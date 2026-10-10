/*
 * Xac nhan bo cong cu chay duoc.
 */

#include <Arduino.h>

#define LED_PIN 8

static uint32_t counter = 0;

void setup()
{
  Serial.begin(115200);
  delay(500);
  pinMode(LED_PIN, OUTPUT);

  Serial.println();
  Serial.println("=== Blink ===");
  Serial.printf("Chip: %s, %d loi, %d MHz\n",
                ESP.getChipModel(), ESP.getChipCores(), getCpuFrequencyMhz());
  Serial.printf("Flash: %u MB\n", ESP.getFlashChipSize() / (1024 * 1024));
  Serial.printf("RAM trong: %u byte\n", ESP.getFreeHeap());
  Serial.println("Neu ban doc duoc dong nay thi cap, driver va cong serial deu on.");
  Serial.println();
}

void loop()
{
  digitalWrite(LED_PIN, HIGH);
  delay(500);
  digitalWrite(LED_PIN, LOW);
  delay(500);

  Serial.printf("Dang chay... %u giay\n", ++counter);
}
