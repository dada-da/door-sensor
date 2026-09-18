/*
 * Khu rung tiep diem, va dem de so sanh.
 */

#include <Arduino.h>

#define REED_PIN 4
#define DEBOUNCE_MS 50

static int stableLevel;  // trang thai da qua bo loc
static int lastRawLevel; // lan doc tho gan nhat
static unsigned long lastRawChangeMs = 0;

static uint32_t rawTransitions = 0;
static uint32_t cleanEvents = 0;
static uint32_t lastReportMs = 0;

void setup()
{
  Serial.begin(115200);
  delay(500);

  pinMode(REED_PIN, INPUT_PULLUP);
  stableLevel = digitalRead(REED_PIN);
  lastRawLevel = stableLevel;
  lastRawChangeMs = millis();

  Serial.println();
  Serial.println("=== Khu rung ===");
  Serial.printf("Cua so loc: %d ms\n", DEBOUNCE_MS);
  Serial.printf("Trang thai ban dau: %s\n", stableLevel == HIGH ? "MO" : "DONG");
  Serial.println();
}

void loop()
{
  int raw = digitalRead(REED_PIN);

  // Dem moi lan chuyen muc tho, ke ca tin hieu gia do rung.
  if (raw != lastRawLevel)
  {
    rawTransitions++;
    lastRawLevel = raw;
    lastRawChangeMs = millis(); // moi thay doi deu khoi dong lai dong ho
  }

  if (raw != stableLevel && (millis() - lastRawChangeMs) >= DEBOUNCE_MS)
  {
    stableLevel = raw;
    cleanEvents++;
    Serial.printf(">>> SU KIEN %u: cua %s\n",
                  cleanEvents, stableLevel == HIGH ? "MO" : "DONG");
  }

  if (millis() - lastReportMs > 5000)
  {
    lastReportMs = millis();
    if (rawTransitions > 0)
    {
      float noise = 100.0f * (rawTransitions - cleanEvents) / rawTransitions;
      Serial.printf("--- Tho: %u | Sau loc: %u | Loai bo %.1f%% tin hieu gia ---\n",
                    rawTransitions, cleanEvents, noise);
    }
  }
}
