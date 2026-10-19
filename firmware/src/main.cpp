/*
 * Node cam bien cua chay pin (door-02), ESP32-C3 SuperMini.
 *
 * Moi lan thuc la mot chu ky tron ven trong setup():
 *   thuc -> doc cua -> noi Wi-Fi -> (NTP neu can) -> noi MQTT -> gui
 *   -> doc lai cua -> ngat ket noi tu te -> hen thuc -> ngu sau
 *
 * Hop dong MQTT giong het node tang 1 (src/main.cpp cua door-01):
 *   home/door/door-02/state   QoS 0, khong giu lai, JSON
 *   home/door/door-02/status  di chuc "offline", "online" khi noi, giu lai
 * Truong them (seq, wake, work_ms, wifi_ms, mqtt_ms, last_rtt_ms) backend bo qua
 * nho @JsonIgnoreProperties(ignoreUnknown = true) trong DevicePayload.
 */

#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <time.h>
#include "esp_sleep.h"
#include "esp_sntp.h"
#include "driver/gpio.h"
#include "config.h"

static const char *TOPIC_STATE = "home/door/" DEVICE_ID "/state";
static const char *TOPIC_STATUS = "home/door/" DEVICE_ID "/status";

// ---- song qua giac ngu (bo nho RTC) ----
RTC_DATA_ATTR static uint32_t bootCount = 0;
RTC_DATA_ATTR static time_t lastNtpSync = 0;
RTC_DATA_ATTR static int32_t lastRttMs = -1;
// Diem phat va kenh cua lan noi thanh cong gan nhat: lan sau noi thang, bo buoc quet
RTC_DATA_ATTR static uint8_t apBssid[6] = {0};
RTC_DATA_ATTR static int32_t apChannel = 0;

static WiFiClient wifiClient;
static PubSubClient mqtt(wifiClient);

// ---- dien ap pin (buoc 8 phan pin, CN-07) ----
// Cau chia 1 MOhm + 1 MOhm tu duong pin xuong GND, diem giua vao BATT_PIN (ADC1),
// tu 100 nF tu diem giua xuong GND. Chua co BATT_PIN trong config.h thi khong do,
// firmware chay y nhu truoc.
#ifdef BATT_PIN
#ifndef BATT_DIVIDER
#define BATT_DIVIDER 2.0f // (R1 + R2) / R2, sua theo hieu chuan
#endif
#ifndef BATT_SAMPLES
#define BATT_SAMPLES 32
#endif
#ifndef BATT_CUTOFF_MV
#define BATT_CUTOFF_MV 3300 // duoi muc nay 3 lan lien tiep thi ngat mem
#endif
static int32_t battAdcMv = -1; // dien ap tai chan ADC, gui kem de hieu chuan
static int32_t battMv = -1;    // dien ap pin suy ra
RTC_DATA_ATTR static uint8_t lowBattCount = 0;

/*
 * Ngat mem khi pin can. Khong co buoc nay, HT7333 sut ap, C3 khoi dong lai lien tuc
 * do brownout va van an vai mA, keo pin xuong toi nguong bao ve 2,4-2,5 V cua mach
 * TP4056 roi nam do hang tuan (thu nghiem buoc 9 chay trong luc vang nha). Ngu sau
 * khong hen thuc chi an vai chuc uA, pin dung yen quanh 3,3 V. Doc 3 lan lien tiep
 * de mot lan doc sai khong lam tat thiet bi. Danh thuc lai: nhan RESET.
 */
static bool batteryCutoff()
{
  if (battMv < 0)
    return false;
  if (battMv < BATT_CUTOFF_MV)
  {
    if (lowBattCount < 255)
      lowBattCount++;
  }
  else
  {
    lowBattCount = 0;
  }
  return lowBattCount >= 3;
}

// Do ngay khi thuc, truoc khi bat Wi-Fi: luc nay board chi an vai mA nen dien ap
// pin gan voi dien ap ho mach, khong bi sut theo xung phat Wi-Fi.
static void readBattery()
{
  uint32_t sum = 0;
  for (int i = 0; i < BATT_SAMPLES; i++)
  {
    // analogReadMilliVolts ap hieu chuan eFuse cua chip; analogRead tho sai vai tram mV
    sum += analogReadMilliVolts(BATT_PIN);
  }
  battAdcMv = (int32_t)(sum / BATT_SAMPLES);
  battMv = (int32_t)(battAdcMv * BATT_DIVIDER + 0.5f);
}
#endif

static uint32_t tStart = 0; // moc bat dau do, sau phan cho USB cua che do phat trien
static uint32_t wifiMs = 0, mqttMs = 0;
static uint32_t sendIndex = 0;

// do khu hoi: tu dang ky chinh chu de state cua minh, do thoi gian broker gui lai
static uint32_t echoSeq = 0;
static uint32_t publishAt = 0;
static int32_t rttMs = -1;

#if DEBUG_SERIAL
#define LOG(...) Serial.printf(__VA_ARGS__)
#else
#define LOG(...)
#endif

static inline gpio_num_t reedGpio() { return (gpio_num_t)REED_PIN; }

static const char *wakeName(esp_sleep_wakeup_cause_t c)
{
  switch (c)
  {
  case ESP_SLEEP_WAKEUP_TIMER:
    return "timer";
  case ESP_SLEEP_WAKEUP_GPIO:
    return "door";
  default:
    return "boot";
  }
}

// Doc cua da khu rung: hai lan doc lien tiep cach DEBOUNCE_MS phai giong nhau.
static int readDoor()
{
  int prev = digitalRead(REED_PIN);
  for (int i = 0; i < 6; i++)
  {
    delay(DEBOUNCE_MS);
    int now = digitalRead(REED_PIN);
    if (now == prev)
      return now;
    prev = now;
  }
  return prev;
}

static const char *stateName(int level) { return level == HIGH ? "OPEN" : "CLOSED"; }

// ---------------------------------------------------------------- mang

#ifndef WIFI_FAST_MS
#define WIFI_FAST_MS 2500UL // noi nhanh theo diem phat da nho, qua moc nay thi quet lai
#endif

static bool waitWifi(uint32_t t0, uint32_t limitMs)
{
  while (WiFi.status() != WL_CONNECTED && millis() - t0 < limitMs)
  {
    delay(10);
  }
  return WiFi.status() == WL_CONNECTED;
}

static bool connectWifi()
{
  uint32_t t = millis();
  WiFi.persistent(false); // khong ghi cau hinh Wi-Fi vao flash moi lan thuc
  WiFi.mode(WIFI_STA);
#if defined(STATIC_IP) && defined(STATIC_GATEWAY) && defined(STATIC_SUBNET)
  // IP tinh: bo buoc xin DHCP moi lan thuc (vai tram ms toi vai giay)
  WiFi.config(IPAddress(STATIC_IP), IPAddress(STATIC_GATEWAY), IPAddress(STATIC_SUBNET),
              IPAddress(STATIC_GATEWAY));
#endif
  bool ok = false;
  bool fast = apChannel > 0;
  if (fast)
  {
    // Noi thang diem phat va kenh cu: khong quet ca 13 kenh
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD, apChannel, apBssid, true);
    WiFi.setTxPower(WIFI_POWER_8_5dBm);
    ok = waitWifi(t, WIFI_FAST_MS);
    if (!ok)
    {
      // Diem phat cu tat hoac doi kenh: quen no, quet lai tu dau
      LOG("Noi nhanh that bai sau %lu ms, quet lai\n", (unsigned long)(millis() - t));
      apChannel = 0;
      WiFi.disconnect();
    }
  }
  if (!ok)
  {
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    WiFi.setTxPower(WIFI_POWER_8_5dBm);
    ok = waitWifi(t, WIFI_TIMEOUT_MS);
  }
  wifiMs = millis() - t;
  if (ok)
  {
    memcpy(apBssid, WiFi.BSSID(), 6);
    apChannel = WiFi.channel();
  }
  LOG("Wi-Fi %s (%s) sau %lu ms, RSSI %d, kenh %ld\n", ok ? "OK" : "THAT BAI",
      fast ? "nhanh" : "quet", (unsigned long)wifiMs, WiFi.RSSI(), (long)apChannel);
  return ok;
}

static bool clockValid() { return time(nullptr) > 1700000000; }

#ifndef NTP_RETRY_SEC
#define NTP_RETRY_SEC 3600UL // NTP that bai ma dong ho van dung: 1 gio sau moi thu lai
#endif

static void syncClockIfNeeded()
{
  time_t now = time(nullptr);
  if (clockValid() && lastNtpSync > 0 && (now - lastNtpSync) < (time_t)NTP_RESYNC_SEC)
  {
    return; // dong ho RTC van dung, bo qua NTP de tiet kiem thoi gian thuc
  }
  uint32_t t = millis();
  sntp_set_sync_status(SNTP_SYNC_STATUS_RESET);
  configTime(0, 0, "pool.ntp.org", "time.google.com"); // ts gui di la epoch UTC
  // sntp_get_sync_status() tra COMPLETED dung MOT lan roi tu xoa ve RESET, nen chi
  // duoc doc mot lan va giu ket qua. Ban truoc doc hai lan: luon coi la that bai,
  // lan thuc nao cung chay lai NTP, mat toi 3 giay (thu nghiem buoc 9, 20/10).
  bool done = false;
  while (millis() - t < NTP_TIMEOUT_MS)
  {
    if (sntp_get_sync_status() == SNTP_SYNC_STATUS_COMPLETED)
    {
      done = true;
      break;
    }
    delay(20);
  }
  if (done)
  {
    lastNtpSync = time(nullptr);
    LOG("NTP OK sau %lu ms\n", (unsigned long)(millis() - t));
  }
  else if (clockValid())
  {
    // Gia nhu vua dong bo cach day (RESYNC - RETRY) giay: lan thu sau la sau RETRY giay
    lastNtpSync = time(nullptr) - (time_t)NTP_RESYNC_SEC + (time_t)NTP_RETRY_SEC;
    LOG("NTP that bai, dung dong ho RTC, thu lai sau %lu s\n", (unsigned long)NTP_RETRY_SEC);
  }
  else
  {
    LOG("NTP that bai, khong gui ts\n");
  }
}

static void onMessage(char *topic, byte *payload, unsigned int len)
{
  if (rttMs >= 0 || len >= 512)
    return;
  char buf[512];
  memcpy(buf, payload, len);
  buf[len] = '\0';
  char key[32];
  snprintf(key, sizeof(key), "\"seq\":%lu,", (unsigned long)echoSeq);
  if (strstr(buf, key))
    rttMs = (int32_t)(millis() - publishAt);
}

static bool connectMqtt()
{
  uint32_t t = millis();
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setBufferSize(512);
  mqtt.setKeepAlive(60);
  mqtt.setSocketTimeout(3);
  mqtt.setCallback(onMessage);

  bool ok = false;
  while (!ok && millis() - t < MQTT_TIMEOUT_MS)
  {
    ok = mqtt.connect(DEVICE_ID, NULL, NULL, TOPIC_STATUS, 1, true, "offline");
    if (!ok)
      delay(200);
  }
  mqttMs = millis() - t;
  if (!ok)
  {
    LOG("MQTT THAT BAI sau %lu ms, ma loi %d\n", (unsigned long)mqttMs, mqtt.state());
    return false;
  }
  mqtt.publish(TOPIC_STATUS, "online", true);
  mqtt.subscribe(TOPIC_STATE, 0);
  LOG("MQTT OK sau %lu ms\n", (unsigned long)mqttMs);
  return true;
}

// Gui trang thai. measureRtt: cho broker gui lai chinh tin nay de do khu hoi.
static bool publishState(int level, const char *wake, bool measureRtt)
{
  uint32_t seq = bootCount * 10 + (++sendIndex);

  JsonDocument doc;
  doc["device_id"] = DEVICE_ID;
  doc["label"] = DEVICE_LABEL;
  doc["state"] = stateName(level);
  doc["seq"] = seq; // dat ngay sau "state" de chuoi tim kiem "seq":N, luon co dau phay
  doc["rssi"] = WiFi.RSSI();
  doc["boot_count"] = bootCount;
  if (clockValid())
    doc["ts"] = (uint32_t)time(nullptr);
  doc["wake"] = wake;
  doc["work_ms"] = millis() - tStart;
  doc["wifi_ms"] = wifiMs;
  doc["mqtt_ms"] = mqttMs;
  if (lastRttMs >= 0)
    doc["last_rtt_ms"] = lastRttMs;
#ifdef BATT_PIN
  if (battMv >= 0)
  {
    doc["battery_mv"] = battMv;     // backend da doc truong nay (canh bao pin yeu)
    doc["batt_adc_mv"] = battAdcMv; // so tho tai chan ADC, chi de hieu chuan
  }
#endif

  char payload[384];
  size_t n = serializeJson(doc, payload, sizeof(payload));

  echoSeq = seq;
  rttMs = -1;
  publishAt = millis();
  bool ok = mqtt.publish(TOPIC_STATE, (const uint8_t *)payload, n, false);
  LOG("%s -> %s\n", ok ? "GUI" : "LOI", payload);

  if (ok && measureRtt)
  {
    while (rttMs < 0 && millis() - publishAt < ECHO_TIMEOUT_MS)
    {
      mqtt.loop();
      delay(2);
    }
    LOG("Khu hoi broker: %ld ms\n", (long)rttMs);
  }
  return ok;
}

// ---------------------------------------------------------------- ngu

static void goToSleep(int wakeOnOppositeOf, bool sendFailed)
{
  uint64_t sec = sendFailed ? RETRY_SEC : HEARTBEAT_SEC;
  esp_sleep_enable_timer_wakeup(sec * 1000000ULL);
  esp_deep_sleep_enable_gpio_wakeup(BIT(REED_PIN),
                                    wakeOnOppositeOf == LOW ? ESP_GPIO_WAKEUP_GPIO_HIGH : ESP_GPIO_WAKEUP_GPIO_LOW);

  // Chan ESP-IDF tu bat dien tro keo noi theo muc danh thuc (bao cao muc 6.3)
  gpio_pullup_dis(reedGpio());
  gpio_pulldown_dis(reedGpio());
  gpio_hold_en(reedGpio());

  LOG("Ngu %llu s, thuc khi cua khac %s. Tong thoi gian thuc %lu ms\n",
      sec, stateName(wakeOnOppositeOf), (unsigned long)(millis() - tStart));
#if DEBUG_SERIAL
  Serial.flush();
  digitalWrite(LED_PIN, HIGH);
#endif
  esp_deep_sleep_start();
}

// ---------------------------------------------------------------- chu ky

void setup()
{
  gpio_hold_dis(reedGpio()); // nha khoa con lai tu giac ngu truoc
  pinMode(REED_PIN, INPUT);  // dien tro keo len ngoai 1 MOhm
  bootCount++;

#if DEBUG_SERIAL
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  pinMode(BOOT_PIN, INPUT_PULLUP);
  Serial.begin(115200);
  delay(1500); // chi de may tinh kip nhan cong USB, khong tinh vao work_ms
  if (digitalRead(BOOT_PIN) == LOW)
  {
    Serial.println("Dang giu BOOT -> khong ngu. Nap code moi duoc.");
    while (true)
    {
      digitalWrite(LED_PIN, !digitalRead(LED_PIN));
      delay(100);
    }
  }
#endif

  tStart = millis();
  esp_sleep_wakeup_cause_t cause = esp_sleep_get_wakeup_cause();
  const char *wake = wakeName(cause);

  int door = readDoor(); // co cho khu rung, vi co the thuc dung luc tiep diem dang nay
  LOG("\n#%lu thuc vi %s, cua %s\n", (unsigned long)bootCount, wake, stateName(door));
#ifdef BATT_PIN
  readBattery();
  LOG("Pin %ld mV (chan ADC %ld mV, he so %.3f)\n", (long)battMv, (long)battAdcMv, (double)BATT_DIVIDER);
  bool cutoff = batteryCutoff();
  if (cutoff)
  {
    wake = "lowbatt"; // tin cuoi cung, may chu thay trong device_telemetry
    LOG("Pin duoi %d mV 3 lan lien tiep -> ngat mem\n", BATT_CUTOFF_MV);
  }
#endif

  bool sent = false;
  int sentState = door;

  if (connectWifi())
  {
    syncClockIfNeeded();
    if (connectMqtt())
    {
      sent = publishState(door, wake, true);

      // Cua co the doi trang thai trong 1-3 giay dang noi mang
      int again = readDoor();
      if (sent && again != door)
      {
        LOG("Cua doi trong luc dang gui, gui them\n");
        if (publishState(again, "recheck", false))
          sentState = again;
      }

      mqtt.loop();
      mqtt.disconnect(); // ngat tu te: broker KHONG phat di chuc
      delay(200);
    }
  }
  WiFi.disconnect(true);
  WiFi.mode(WIFI_OFF);

  lastRttMs = rttMs;

  // Hen thuc o muc nguoc voi trang thai DA GUI thanh cong. Neu cua doi sau lan gui
  // cuoi, dieu kien danh thuc dung ngay, board thuc lai va gui thay doi do.
  // Gui that bai thi theo trang thai hien tai, va hen gio thuc som de gui lai.
  int wakeRef = sent ? sentState : digitalRead(REED_PIN);
#ifdef BATT_PIN
  if (cutoff)
  {
    // Khong bat nguon danh thuc nao: ngu toi khi nhan RESET hoac cap dien lai.
#if DEBUG_SERIAL
    Serial.flush();
    digitalWrite(LED_PIN, HIGH);
#endif
    esp_deep_sleep_start();
  }
#endif
  goToSleep(wakeRef, !sent);
}

void loop() {}