/*
 * Buoc 6-7 phan pin: sketch do dong ba che do tren C3 (door-02), khong can Serial.
 *
 * Nap: pio run -e dodong -t upload   (giu BOOT neu nap khong vao)
 * Nap lai firmware that: pio run -e c3 -t upload
 *
 * Lap mot vong 11 phut, moi pha bao bang so lan nhay den (nhay 50 ms, it anh
 * huong toi so do):
 *
 *   1 nhay  Pha A, 30 s: Wi-Fi da noi, tat tiet kiem nang luong modem.
 *           Gan voi luc firmware that dang noi mang va gui tin.   Shunt 1 Ohm.
 *   2 nhay  Pha B, 30 s: Wi-Fi tat, CPU chay vong lap ban.
 *           Gan voi firmware chay lien tuc kieu door-01.            Shunt 1 Ohm.
 *   3 nhay  Pha C, 600 s: ngu sau, chi hen gio thuc.               Shunt 1 kOhm,
 *           mo cong tac sau khi den tat, DONG LAI truoc khi het 10 phut.
 *
 * Mach (xem huong-dan-phan-pin.md buoc 6): HT7333 -> [1 kOhm // cong tac] -> [1 Ohm]
 * -> chan 3V3 cua C3, tu 1000 uF ngang 3V3-GND sau shunt. RUT cap USB khi do.
 *
 * Giu BOOT luc cap dien: den nhay nhanh, khong chay vong do, de nap code moi.
 */

#include <Arduino.h>
#include <WiFi.h>
#include "esp_sleep.h"
#include "config.h"

#define LED_PIN 8  // sang khi ghi LOW
#define BOOT_PIN 9 // nut BOOT

#define PHASE_A_MS 30000UL
#define PHASE_B_MS 30000UL
#define SLEEP_SEC 600UL
#define PHASE_A_WIFI_TIMEOUT_MS 15000UL

static void blink(int n)
{
  for (int i = 0; i < n; i++)
  {
    digitalWrite(LED_PIN, LOW);
    delay(50);
    digitalWrite(LED_PIN, HIGH);
    delay(400);
  }
}

void setup()
{
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, HIGH);
  pinMode(BOOT_PIN, INPUT_PULLUP);
  Serial.begin(115200); // khong cam USB thi khong ai doc, khong sao
  delay(300);

  if (digitalRead(BOOT_PIN) == LOW)
  {
    Serial.println("Dang giu BOOT -> khong do. Nap code moi duoc.");
    while (true)
    {
      digitalWrite(LED_PIN, !digitalRead(LED_PIN));
      delay(100);
    }
  }

  // ---- Pha A: Wi-Fi noi, khong ngu modem ----
  blink(1);
  WiFi.persistent(false);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  WiFi.setTxPower(WIFI_POWER_8_5dBm); // giong firmware that (bao cao muc 6.5)
  WiFi.setSleep(false);
  uint32_t t = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - t < PHASE_A_WIFI_TIMEOUT_MS)
    delay(10);
  Serial.printf("Pha A: Wi-Fi %s, RSSI %d\n",
                WiFi.status() == WL_CONNECTED ? "OK" : "THAT BAI", WiFi.RSSI());
  if (WiFi.status() != WL_CONNECTED)
  {
    // Khong noi duoc thi so do pha A vo nghia: nhay 5 lan roi bo qua pha A.
    blink(5);
  }
  else
  {
    delay(PHASE_A_MS);
  }
  WiFi.disconnect(true);
  WiFi.mode(WIFI_OFF);

  // ---- Pha B: CPU ban, Wi-Fi tat ----
  blink(2);
  Serial.println("Pha B: CPU chay, Wi-Fi tat");
  volatile uint32_t x = 0;
  t = millis();
  while (millis() - t < PHASE_B_MS)
  {
    x = x * 1664525UL + 1013904223UL; // vong lap ban, khong goi delay
  }

  // ---- Pha C: ngu sau ----
  blink(3);
  Serial.printf("Pha C: ngu %lu s. Mo cong tac sau khi den tat.\n", (unsigned long)SLEEP_SEC);
  Serial.flush();
  esp_sleep_enable_timer_wakeup((uint64_t)SLEEP_SEC * 1000000ULL);
  esp_deep_sleep_start();
}

void loop() {}
