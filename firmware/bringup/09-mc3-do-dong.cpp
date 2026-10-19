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
  delay(1500); // cho may tinh kip nhan lai cong USB de doc duoc dong in duoi
  Serial.printf("Pha A: noi Wi-Fi \"%s\"\n", WIFI_SSID);
  WiFi.persistent(false);
  WiFi.mode(WIFI_STA);
  bool ok = false;
  // Thu 3 lan, moi lan toi da 15 s. Lan dau sau khi cap dien C3 hay that bai.
  for (int attempt = 1; attempt <= 3 && !ok; attempt++)
  {
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    WiFi.setTxPower(WIFI_POWER_8_5dBm); // giong firmware that (bao cao muc 6.5)
    uint32_t t = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - t < PHASE_A_WIFI_TIMEOUT_MS)
      delay(10);
    ok = WiFi.status() == WL_CONNECTED;
    Serial.printf("  lan %d: %s (ma trang thai %d) sau %lu ms\n", attempt, ok ? "OK" : "THAT BAI",
                  (int)WiFi.status(), (unsigned long)(millis() - t));
    if (!ok)
    {
      WiFi.disconnect(true);
      delay(500);
      WiFi.mode(WIFI_STA);
    }
  }
  if (ok)
  {
    WiFi.setSleep(false);
    Serial.printf("Pha A: Wi-Fi OK, RSSI %d dBm, do dong trong %lu s\n", WiFi.RSSI(),
                  (unsigned long)(PHASE_A_MS / 1000));
    delay(PHASE_A_MS);
  }
  else
  {
    // Quet de biet mang co nhin thay khong: thay thi sai mat khau, khong thay thi sai ten
    // hoac song yeu. Ma trang thai: 1 = khong thay SSID, 4 = sai mat khau/that bai, 6 = mat ket noi.
    int n = WiFi.scanNetworks();
    bool seen = false;
    for (int i = 0; i < n; i++)
    {
      if (WiFi.SSID(i) == WIFI_SSID)
      {
        seen = true;
        Serial.printf("  Thay mang \"%s\", RSSI %d dBm, kenh %d -> kiem tra mat khau\n",
                      WIFI_SSID, WiFi.RSSI(i), WiFi.channel(i));
      }
    }
    if (!seen)
      Serial.printf("  KHONG thay mang \"%s\" trong %d mang quet duoc -> kiem tra ten, chi ho tro 2,4 GHz\n",
                    WIFI_SSID, n);
    blink(5); // so do pha A vo nghia
  }
  WiFi.disconnect(true);
  WiFi.mode(WIFI_OFF);

  // ---- Pha B: CPU ban, Wi-Fi tat ----
  blink(2);
  Serial.println("Pha B: CPU chay, Wi-Fi tat");
  volatile uint32_t x = 0;
  uint32_t t = millis();
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
