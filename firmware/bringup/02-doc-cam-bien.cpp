/*
 * Doc cong tac tu, CHUA khu rung.
 */

#include <Arduino.h>

#define REED_PIN 4

static int lastLevel;
static uint32_t transitions = 0;
static uint32_t lastPrintMs = 0;

void setup()
{
  Serial.begin(115200);
  delay(500);

  pinMode(REED_PIN, INPUT_PULLUP);
  lastLevel = digitalRead(REED_PIN);

  Serial.println();
  Serial.println("=== Doc cam bien, chua khu rung ===");
  Serial.printf("Trang thai ban dau: %s\n",
                lastLevel == HIGH ? "HIGH -> cua MO (nam cham xa)"
                                  : "LOW  -> cua DONG (nam cham gan)");
  Serial.println("Dua nam cham ra vao 5 lan roi xem so lan chuyen muc.");
  Serial.println();
}

void loop()
{
  // Khong co delay trong vong lap: phai doc that nhanh moi bat duoc rung.
  int level = digitalRead(REED_PIN);

  if (level != lastLevel)
  {
    transitions++;
    Serial.printf("[%8lu ms] %-4s  (lan chuyen muc thu %u)\n",
                  millis(), level == HIGH ? "HIGH" : "LOW", transitions);
    lastLevel = level;
  }

  // Cu 5 giay bao mot lan de con biet chuong trinh van song.
  if (millis() - lastPrintMs > 5000)
  {
    lastPrintMs = millis();
    Serial.printf("--- Tong cong %u lan chuyen muc. Trang thai hien tai: %s ---\n",
                  transitions, lastLevel == HIGH ? "MO" : "DONG");
  }
}
