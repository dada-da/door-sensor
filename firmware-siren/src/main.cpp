/*
 * Node coi bao dong (siren-01), ESP32-C3 SuperMini, cam dien USB.
 *
 * Co cau chap hanh "ngu" (bao cao muc 3.10.1): khong biet gio, khong biet lich,
 * khong biet cua nao. Chi nhan lenh keu bao lau roi keu, va tu im khi het gio.
 *
 * Hop dong MQTT (handoff.md):
 *   home/alarm/siren-01/cmd        nhan, QoS 1   {"action":"BEEP","duration_ms":N} | STOP | TEST
 *   home/alarm/siren-01/state      gui, giu lai  {"state":"ON"|"OFF", ...}, khi doi va moi 60 s
 *   home/alarm/siren-01/status     di chuc       "offline" giu lai, "online" khi noi
 *   home/alarm/armed               nhan, QoS 1   "ARMED" | "DISARMED", chi de bat den
 *   home/env/siren-01/telemetry    gui           {"temp_c":..,"rh":..} moi 60 s
 *
 * Den GPIO 8:
 *   nhay nhanh 5 Hz  coi dang keu
 *   nhay cham 1 Hz   chua noi duoc broker
 *   sang lien tuc    da noi, dang bao ve
 *   tat              da noi, khong bao ve
 *
 * An toan khi hong (bang 3.5):
 *   - Khoi dong: im. Chan coi duoc dat muc tat TRUOC khi chuyen sang dau ra.
 *   - Moi lenh BEEP mang thoi luong, cat tran MAX_SIREN_MS ngay tren node.
 *   - Tu tat bang esp_timer chu khong bang vong lap: vong lap co bi chan (noi lai
 *     MQTT mat toi 3 giay, doc I2C) thi coi van tat dung han.
 *   - Mat ket noi trong luc dang keu: keu tiep toi het han roi tu im. Khong dung
 *     ngay, vi lam vay thi ke dot nhap chi can gay nhieu Wi-Fi la tat duoc coi.
 *     Khong bao gio tu bat keu khi khong co lenh.
 *   - Lenh sai dinh dang: bo qua, ghi log.
 */

#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include "esp_timer.h"
#include "aht20.h"
#include "config.h"

static const char *TOPIC_CMD = "home/alarm/" DEVICE_ID "/cmd";
static const char *TOPIC_STATE = "home/alarm/" DEVICE_ID "/state";
static const char *TOPIC_STATUS = "home/alarm/" DEVICE_ID "/status";
static const char *TOPIC_ARMED = "home/alarm/armed";
static const char *TOPIC_ENV = "home/env/" DEVICE_ID "/telemetry";

static const int SIREN_OFF_LEVEL = SIREN_ACTIVE_LEVEL ? LOW : HIGH;

static WiFiClient wifiClient;
static PubSubClient mqtt(wifiClient);
static Aht20 aht;

// ---- trang thai coi. Ghi tu vong lap va tu callback cua esp_timer.
static esp_timer_handle_t sirenTimer;
static volatile bool sounding = false;
static volatile bool pendingOffReport = false;
static uint32_t soundStart = 0;
static uint32_t soundLength = 0;
static char soundReason[24] = "";

static int armed = -1; // -1 chua biet, 0 tat, 1 bat

static uint32_t lastHeartbeat = 0;
static uint32_t lastEnv = 0;
static uint32_t lastMqttAttempt = 0;
static uint32_t wifiLostSince = 0;

#define LOG(...) Serial.printf(__VA_ARGS__)

// ================================================================ coi

static void IRAM_ATTR sirenTimeout(void *) {
  digitalWrite(SIREN_PIN, SIREN_OFF_LEVEL);
  sounding = false;
  pendingOffReport = true;
}

static void sirenOn(uint32_t ms, const char *reason) {
  if (ms > MAX_SIREN_MS) {
    LOG("Cat thoi luong %lu ms ve tran %lu ms\n", (unsigned long)ms, (unsigned long)MAX_SIREN_MS);
    ms = MAX_SIREN_MS;
  }
  esp_timer_stop(sirenTimer); // dang keu thi dat lai han, khong chong luot
  strlcpy(soundReason, reason, sizeof(soundReason));
  soundStart = millis();
  soundLength = ms;
  digitalWrite(SIREN_PIN, SIREN_ACTIVE_LEVEL);
  sounding = true;
  pendingOffReport = false;
  esp_timer_start_once(sirenTimer, (uint64_t)ms * 1000ULL);
}

static void sirenOff() {
  esp_timer_stop(sirenTimer);
  digitalWrite(SIREN_PIN, SIREN_OFF_LEVEL);
  sounding = false;
  pendingOffReport = false;
}

static uint32_t remainingMs() {
  if (!sounding) return 0;
  uint32_t elapsed = millis() - soundStart;
  return elapsed >= soundLength ? 0 : soundLength - elapsed;
}

// ================================================================ MQTT gui

static void publishState() {
  if (!mqtt.connected()) return;
  JsonDocument doc;
  bool on = sounding;
  doc["state"] = on ? "ON" : "OFF";
  if (on) {
    doc["remaining_ms"] = remainingMs();
    doc["reason"] = soundReason;
  }
  doc["rssi"] = WiFi.RSSI();
  doc["uptime_s"] = millis() / 1000;
  doc["aht20"] = aht.present();

  char payload[256];
  size_t n = serializeJson(doc, payload, sizeof(payload));
  // Giu lai: giao dien hoi broker la biet coi dang the nao ngay ca khi vua mo.
  bool ok = mqtt.publish(TOPIC_STATE, (const uint8_t *)payload, n, true);
  LOG("%s state -> %s\n", ok ? "GUI" : "LOI", payload);
  lastHeartbeat = millis();
}

static void publishEnv() {
  lastEnv = millis();
  if (!aht.present() && !aht.reinit()) {
    LOG("AHT20: %s\n", aht.lastError()); // binh thuong khi chua han, khong phai loi
    return;
  }
  float t, rh;
  if (!aht.read(t, rh)) {
    LOG("AHT20 doc loi: %s, bo qua lan nay\n", aht.lastError());
    return;
  }
  if (!mqtt.connected()) return;
  char payload[64];
  snprintf(payload, sizeof(payload), "{\"temp_c\":%.2f,\"rh\":%.1f}", t, rh);
  bool ok = mqtt.publish(TOPIC_ENV, payload, false);
  LOG("%s env -> %s\n", ok ? "GUI" : "LOI", payload);
}

// ================================================================ MQTT nhan

static void handleCommand(const char *json) {
  JsonDocument doc;
  if (deserializeJson(doc, json)) {
    LOG("Bo qua lenh khong phai JSON: %s\n", json);
    return;
  }
  const char *action = doc["action"] | "";
  const char *reason = doc["reason"] | "";

  if (strcmp(action, "BEEP") == 0) {
    uint32_t ms = doc["duration_ms"] | 0UL;
    if (ms == 0) {
      LOG("Bo qua BEEP thieu duration_ms. Moi lenh keu phai mang thoi luong.\n");
      return;
    }
    LOG("BEEP %lu ms (%s)%s\n", (unsigned long)ms, reason, sounding ? ", gia han" : "");
    sirenOn(ms, reason[0] ? reason : "BEEP");
  } else if (strcmp(action, "TEST") == 0) {
    // Khong tu choi theo armed (bao cao 3.10.1): thu coi ban ngay phai chay duoc.
    // Nhung khong cat ngang mot lan bao dong that.
    if (sounding) {
      LOG("Bo qua TEST: coi dang keu that\n");
      return;
    }
    LOG("TEST %lu ms\n", (unsigned long)TEST_MS);
    sirenOn(TEST_MS, "TEST");
  } else if (strcmp(action, "STOP") == 0) {
    LOG("STOP\n");
    sirenOff();
  } else {
    LOG("Bo qua lenh la: %s\n", json);
    return;
  }
  // Xac nhan ngay: backend dung tin ON nay de ghi acked_at (do tre bang 5.10).
  publishState();
}

static void onMessage(char *topic, byte *payload, unsigned int len) {
  char buf[256];
  if (len >= sizeof(buf)) {
    LOG("Bo qua tin qua dai (%u byte) tren %s\n", len, topic);
    return;
  }
  memcpy(buf, payload, len);
  buf[len] = '\0';

  if (strcmp(topic, TOPIC_CMD) == 0) {
    handleCommand(buf);
  } else if (strcmp(topic, TOPIC_ARMED) == 0) {
    int prev = armed;
    armed = strncmp(buf, "ARMED", 5) == 0 ? 1 : 0;
    if (armed != prev) LOG("Che do bao ve: %s\n", armed ? "BAT" : "TAT");
  }
}

// ================================================================ ket noi

static void connectWifi() {
  WiFi.persistent(false);
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  // C3 SuperMini khong noi duoc o cong suat mac dinh (bao cao muc 6.5)
  WiFi.setTxPower(WIFI_POWER_8_5dBm);
  // Node cam dien: tat tiet kiem dien de goi ping MQTT khong tre (bao cao muc 3.5)
  WiFi.setSleep(false);
}

static void tryConnectMqtt() {
  lastMqttAttempt = millis();
  LOG("Noi MQTT %s:%d... ", MQTT_HOST, MQTT_PORT);
  if (!mqtt.connect(DEVICE_ID, NULL, NULL, TOPIC_STATUS, 1, true, "offline")) {
    LOG("that bai, ma loi %d\n", mqtt.state());
    return;
  }
  LOG("OK\n");
  mqtt.publish(TOPIC_STATUS, "online", true);
  mqtt.subscribe(TOPIC_CMD, 1);
  mqtt.subscribe(TOPIC_ARMED, 1);
  publishState();
}

// ================================================================ den

static void updateLed() {
  uint32_t now = millis();
  bool on;
  if (sounding) {
    on = (now / 100) % 2;
  } else if (!mqtt.connected()) {
    on = (now / 500) % 2;
  } else {
    on = armed == 1;
  }
  digitalWrite(LED_PIN, on ? LOW : HIGH); // den sang khi ghi LOW
}

// ================================================================ chuong trinh

void setup() {
  // Dat muc tat truoc, roi moi chuyen sang dau ra: khong keu "chip" luc khoi dong.
  digitalWrite(SIREN_PIN, SIREN_OFF_LEVEL);
  pinMode(SIREN_PIN, OUTPUT);
  digitalWrite(SIREN_PIN, SIREN_OFF_LEVEL);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, HIGH);

  Serial.begin(115200);
  delay(1500);
  LOG("\n=== Node coi %s ===\n", DEVICE_ID);
  LOG("Chan coi GPIO %d, keu o muc %s, tran %lu ms\n",
      SIREN_PIN, SIREN_ACTIVE_LEVEL ? "CAO" : "THAP", (unsigned long)MAX_SIREN_MS);

  esp_timer_create_args_t args = {};
  args.callback = &sirenTimeout;
  args.name = "siren";
  esp_timer_create(&args, &sirenTimer);

  if (aht.begin(I2C_SDA, I2C_SCL)) {
    LOG("AHT20 OK tai 0x38\n");
  } else {
    LOG("AHT20 khong co (%s). Van chay binh thuong, se thu lai moi %lu s.\n",
        aht.lastError(), (unsigned long)(ENV_PERIOD_MS / 1000));
  }

  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setCallback(onMessage);
  mqtt.setBufferSize(512);
  mqtt.setKeepAlive(60);
  mqtt.setSocketTimeout(3);

  connectWifi();
  lastEnv = millis() - ENV_PERIOD_MS + 5000; // lan do dau sau 5 giay
}

void loop() {
  uint32_t now = millis();

  if (WiFi.status() == WL_CONNECTED) {
    if (wifiLostSince) {
      LOG("Wi-Fi OK, RSSI %d, IP %s\n", WiFi.RSSI(), WiFi.localIP().toString().c_str());
      wifiLostSince = 0;
    }
    // lastMqttAttempt == 0: chua thu lan nao, noi ngay khong cho 5 giay
    if (!mqtt.connected() && (lastMqttAttempt == 0 || now - lastMqttAttempt >= 5000)) {
      tryConnectMqtt();
    }
  } else {
    if (!wifiLostSince) {
      wifiLostSince = now ? now : 1;
      LOG("Mat Wi-Fi\n");
    }
    // Mot so trang thai loi cua Wi-Fi khong tu phuc hoi, khoi dong lai la chac nhat.
    // Khong khoi dong lai khi coi dang keu: cho no tu im theo han da dat.
    if (now - wifiLostSince > RECOVERY_TIMEOUT_MS && !sounding) {
      LOG("Mat Wi-Fi qua %lu phut, khoi dong lai\n", (unsigned long)(RECOVERY_TIMEOUT_MS / 60000));
      Serial.flush();
      ESP.restart();
    }
  }

  mqtt.loop();

  if (pendingOffReport) {
    pendingOffReport = false;
    LOG("Het thoi luong, coi tu im\n");
    publishState();
  }
  if (now - lastHeartbeat >= HEARTBEAT_MS) {
    publishState();
  }
  if (now - lastEnv >= ENV_PERIOD_MS) {
    publishEnv();
  }

  updateLed();
  delay(10);
}
