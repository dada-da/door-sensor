#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <time.h>

#include "config.h"

static WiFiClient wifiClient;
static PubSubClient mqtt(wifiClient);

static const char *TOPIC_STATE = "home/door/" DEVICE_ID "/state";
static const char *TOPIC_STATUS = "home/door/" DEVICE_ID "/status";

RTC_DATA_ATTR static uint32_t bootCount = 0;

static int stableState = HIGH; // trang thai da khu rung
static int lastRawRead = HIGH; // lan doc tho gan nhat
static unsigned long lastChangeMs = 0;
static unsigned long lastHeartbeat = 0;
static unsigned long lastHealthyMs = 0;

// ---------------------------------------------------------------- ket noi

static void connectWifi()
{
  WiFi.mode(WIFI_STA);

  // Tat tiet kiem dien Wi-Fi.
  //
  // Mac dinh ESP32 ngu giua cac nhip beacon cua AP, nen goi tin den co the bi
  // tre hang tram mili giay. Voi thiet bi cam dien lien tuc thi khong duoc loi
  // gi, ma lai lam goi ping MQTT den muon, broker tuong thiet bi chet va phat
  // Last Will. Chi bat lai khi chuyen sang chay pin.
  WiFi.setSleep(false);

  WiFi.setAutoReconnect(true);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  Serial.print("Dang noi Wi-Fi");
  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 20000)
  {
    delay(300);
    Serial.print('.');
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED)
  {
    Serial.printf("Da noi. IP %s, RSSI %d dBm\n",
                  WiFi.localIP().toString().c_str(), WiFi.RSSI());
  }
  else
  {
    Serial.println("Khong noi duoc Wi-Fi, se thu lai trong loop.");
  }
}

static void syncClock()
{
  configTime(7 * 3600, 0, "pool.ntp.org", "time.google.com");

  Serial.print("Dang dong bo dong ho");
  time_t now = 0;
  int tries = 0;
  while (now < 1700000000 && tries < 20)
  {
    delay(500);
    Serial.print('.');
    time(&now);
    tries++;
  }
  Serial.println();
  Serial.printf(now < 1700000000 ? "Chua dong bo duoc, backend se tu dat moc.\n"
                                 : "Dong ho da dong bo.\n");
}

static void connectMqtt()
{
  mqtt.setServer(MQTT_HOST, MQTT_PORT);

  mqtt.setKeepAlive(60);
  mqtt.setSocketTimeout(30);

  while (!mqtt.connected() && WiFi.status() == WL_CONNECTED)
  {
    Serial.print("Dang noi MQTT broker... ");

    bool ok = mqtt.connect(
        DEVICE_ID,
        NULL, NULL,
        TOPIC_STATUS,
        1,
        true,
        "offline");

    if (ok)
    {
      Serial.println("thanh cong.");
      mqtt.publish(TOPIC_STATUS, "online", true);
    }
    else
    {
      Serial.printf("that bai, ma loi %d. Thu lai sau 3 giay.\n", mqtt.state());
      delay(3000);
    }
  }
}

static void publishState(int level)
{
  JsonDocument doc;
  doc["device_id"] = DEVICE_ID;
  doc["label"] = DEVICE_LABEL;
  doc["state"] = (level == HIGH) ? "OPEN" : "CLOSED";
  doc["rssi"] = WiFi.RSSI();
  doc["boot_count"] = bootCount;

  time_t now = time(nullptr);
  if (now > 1700000000)
  {
    doc["ts"] = (uint32_t)now;
  }

  char payload[256];
  size_t n = serializeJson(doc, payload, sizeof(payload));

  bool ok = mqtt.publish(TOPIC_STATE, (const uint8_t *)payload, n, false);
  Serial.printf("%s -> %s\n", ok ? "GUI" : "LOI", payload);
}

void setup()
{
  Serial.begin(115200);
  delay(200);
  bootCount++;

  pinMode(REED_PIN, INPUT_PULLUP);

  stableState = digitalRead(REED_PIN);
  lastRawRead = stableState;
  lastChangeMs = millis();

  Serial.printf("\n=== Cam bien cua [%s] khoi dong lan %u ===\n", DEVICE_ID, bootCount);
  Serial.printf("Trang thai ban dau: %s\n", stableState == HIGH ? "OPEN" : "CLOSED");

  connectWifi();
  syncClock();
  connectMqtt();

  publishState(stableState);
  lastHeartbeat = millis();
  lastHealthyMs = millis();
}

void loop()
{
  if (WiFi.status() != WL_CONNECTED)
  {
    connectWifi();
  }
  if (!mqtt.connected())
  {
    connectMqtt();
  }
  mqtt.loop();

  int raw = digitalRead(REED_PIN);

  if (raw != lastRawRead)
  {
    lastRawRead = raw;
    lastChangeMs = millis();
  }

  if (raw != stableState && (millis() - lastChangeMs) >= DEBOUNCE_MS)
  {
    stableState = raw;
    Serial.printf("Cua %s\n", stableState == HIGH ? "MO" : "DONG");
    publishState(stableState);
    lastHeartbeat = millis();
  }

  if (millis() - lastHeartbeat >= HEARTBEAT_MS)
  {
    publishState(stableState);
    lastHeartbeat = millis();
  }

  if (mqtt.connected() && WiFi.status() == WL_CONNECTED)
  {
    lastHealthyMs = millis();
  }
  else if (millis() - lastHealthyMs > RECOVERY_TIMEOUT_MS)
  {
    Serial.println("Mat ket noi qua lau, khoi dong lai.");
    delay(100);
    ESP.restart();
  }

  delay(10);
}