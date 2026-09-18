/*
 * Wi-Fi va MQTT, chua doc cam bien.
 */

#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include "../src/config.h"

static WiFiClient wifiClient;
static PubSubClient mqtt(wifiClient);
static uint32_t seq = 0;

static void scanNetworks()
{
  Serial.println("Dang quet cac mang xung quanh...");
  int n = WiFi.scanNetworks();
  bool found = false;

  for (int i = 0; i < n; i++)
  {
    bool isMine = WiFi.SSID(i) == WIFI_SSID;
    if (isMine)
      found = true;
    Serial.printf("  %s%-28s %d dBm %s\n",
                  isMine ? "-> " : "   ",
                  WiFi.SSID(i).c_str(),
                  WiFi.RSSI(i),
                  isMine ? "<- mang cua ban" : "");
  }

  if (!found)
  {
    Serial.printf("\nKHONG THAY mang '%s'.\n", WIFI_SSID);
  }
  Serial.println();
}

void setup()
{
  Serial.begin(115200);
  delay(500);
  Serial.println();
  Serial.println("=== Wi-Fi va MQTT ===");

  WiFi.mode(WIFI_STA);
  scanNetworks();

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.printf("Dang noi '%s'", WIFI_SSID);

  unsigned long start = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - start < 20000)
  {
    delay(300);
    Serial.print('.');
  }
  Serial.println();

  if (WiFi.status() != WL_CONNECTED)
  {
    Serial.println("That bai!");
    return;
  }

  Serial.printf("Da noi. IP cua ESP32: %s\n", WiFi.localIP().toString().c_str());
  Serial.printf("Cuong do song: %d dBm ", WiFi.RSSI());
  if (WiFi.RSSI() > -60)
    Serial.println("(tot)");
  else if (WiFi.RSSI() > -75)
    Serial.println("(du dung)");
  else
    Serial.println("(yeu, nen dat gan router hon)");

  Serial.printf("Dang noi MQTT %s:%d ... ", MQTT_HOST, MQTT_PORT);
  mqtt.setServer(MQTT_HOST, MQTT_PORT);

  if (mqtt.connect(DEVICE_ID "-test"))
  {
    Serial.println("thanh cong.");
  }
  else
  {
    Serial.printf("that bai, ma loi %d.\n", mqtt.state());
    Serial.println("Kiem tra: docker compose ps, va IP trong config.h co dung khong.");
    Serial.println("Thu tu may tinh:  mosquitto_pub -h localhost -t test -m hello");
  }
}

void loop()
{
  if (!mqtt.connected())
  {
    Serial.println("Mat ket noi MQTT, thu lai sau 3 giay...");
    delay(3000);
    mqtt.connect(DEVICE_ID "-test");
    return;
  }
  mqtt.loop();

  char payload[128];
  snprintf(payload, sizeof(payload),
           "{\"device_id\":\"%s\",\"test\":true,\"seq\":%u,\"rssi\":%d}",
           DEVICE_ID, ++seq, WiFi.RSSI());

  bool ok = mqtt.publish("home/door/" DEVICE_ID "/test", payload);
  Serial.printf("%s %s\n", ok ? "GUI " : "LOI ", payload);

  delay(5000);
}
