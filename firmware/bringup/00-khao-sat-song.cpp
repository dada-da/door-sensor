/*
 * KHAO SAT SONG
 * BANG TRA NHIP NHAY:
 *
 *   sang gan nhu lien tuc   ->  manh hon -60 dBm  ->  rat tot
 *   nhay nhanh (4 lan/giay) ->  -60 den -70       ->  tot
 *   nhay vua  (2 lan/giay)  ->  -70 den -78       ->  dung duoc
 *   nhay cham (1 lan/giay)  ->  -78 den -85       ->  chap chon, se rot mang
 *   nhay doi roi nghi dai   ->  yeu hon -85       ->  khong dung duoc
 *   tat han                 ->  khong thay mang   ->  ngoai vung phu song
 */

#include <Arduino.h>
#include <WiFi.h>
#include "config.h"

#define LED_PIN 2
#define SAMPLES 8 // so mau lay trung binh
#define SCAN_GAP_MS 1500

static int samples[SAMPLES];
static int sampleCount = 0;
static int sampleIndex = 0;

// Nhip nhay hien tai, tinh bang mili giay cho moi nua chu ky.
// 0 nghia la sang lien tuc, -1 nghia la khong thay mang.
static volatile int blinkHalfPeriod = -1;

static unsigned long lastScanMs = 0;
static unsigned long lastBlinkMs = 0;
static bool ledOn = false;

static int averageRssi()
{
  if (sampleCount == 0)
    return 0;
  long sum = 0;
  for (int i = 0; i < sampleCount; i++)
    sum += samples[i];
  return (int)(sum / sampleCount);
}

static const char *verdict(int rssi)
{
  if (rssi >= -60)
    return "rat tot";
  if (rssi >= -70)
    return "tot";
  if (rssi >= -78)
    return "dung duoc";
  if (rssi >= -85)
    return "chap chon, se rot mang";
  return "khong dung duoc";
}

static int halfPeriodFor(int rssi)
{
  if (rssi >= -60)
    return 0; // sang lien tuc
  if (rssi >= -70)
    return 125; // 4 lan/giay
  if (rssi >= -78)
    return 250; // 2 lan/giay
  if (rssi >= -85)
    return 500; // 1 lan/giay
  return 1200;  // rat cham
}

void setup()
{
  Serial.begin(115200);
  delay(500);
  pinMode(LED_PIN, OUTPUT);

  WiFi.mode(WIFI_STA);
  WiFi.disconnect();

  Serial.println();
  Serial.println("=== Khao sat song Wi-Fi ===");
  Serial.printf("Dang tim mang: %s\n", WIFI_SSID);
  Serial.println();
}

void loop()
{
  // --- Quet dinh ky ---
  if (millis() - lastScanMs >= SCAN_GAP_MS)
  {
    lastScanMs = millis();

    int n = WiFi.scanNetworks(false, true);
    int best = 0;
    bool found = false;

    for (int i = 0; i < n; i++)
    {
      if (WiFi.SSID(i) == WIFI_SSID)
      {
        if (!found || WiFi.RSSI(i) > best)
        {
          best = WiFi.RSSI(i);
          found = true;
        }
      }
    }
    WiFi.scanDelete();

    if (!found)
    {
      blinkHalfPeriod = -1;
      sampleCount = 0;
      sampleIndex = 0;
      Serial.println("Khong thay mang. Ngoai vung phu song, hoac sai ten SSID.");
      return;
    }

    samples[sampleIndex] = best;
    sampleIndex = (sampleIndex + 1) % SAMPLES;
    if (sampleCount < SAMPLES)
      sampleCount++;

    int avg = averageRssi();
    blinkHalfPeriod = halfPeriodFor(avg);

    // Thanh truc quan de doc nhanh tren Serial.
    int bars = (avg + 100) / 8;
    if (bars < 0)
      bars = 0;
    if (bars > 6)
      bars = 6;
    char gauge[8];
    for (int i = 0; i < 6; i++)
      gauge[i] = (i < bars) ? '#' : '.';
    gauge[6] = '\0';

    Serial.printf("hien tai %4d dBm | trung binh %4d dBm [%s] %s%s\n",
                  best, avg, gauge, verdict(avg),
                  sampleCount < SAMPLES ? "  (dang lay mau...)" : "");
  }

  // --- Nhay den theo nhip da tinh ---
  int half = blinkHalfPeriod;

  if (half == 0)
  {
    digitalWrite(LED_PIN, HIGH);
    return;
  }
  if (half < 0)
  {
    digitalWrite(LED_PIN, LOW);
    return;
  }
  if (millis() - lastBlinkMs >= (unsigned long)half)
  {
    lastBlinkMs = millis();
    ledOn = !ledOn;
    digitalWrite(LED_PIN, ledOn ? HIGH : LOW);
  }
}
