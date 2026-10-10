#include <Arduino.h>

#define REED_PIN 4
#define LED_PIN 8
#define DEBOUNCE_MS 50 // dat 0 de xem rung tiep diem (anh cho muc 5.4)

int raw, stable;
uint32_t lastChange = 0, edges = 0;

const char *ten(int v) { return v == LOW ? "DONG" : "MO"; }

void setup()
{
  Serial.begin(115200);
  delay(1500);
  pinMode(REED_PIN, INPUT); // keo len bang dien tro ngoai 1 MOhm
  pinMode(LED_PIN, OUTPUT);
  raw = stable = digitalRead(REED_PIN);
  Serial.printf("Bat dau: %s, DEBOUNCE_MS = %d\n", ten(stable), DEBOUNCE_MS);
}

void loop()
{
  int r = digitalRead(REED_PIN);

  if (r != raw)
  { // co canh tho
    raw = r;
    lastChange = millis();
    edges++;
    if (DEBOUNCE_MS == 0)
    {
      stable = r;
      Serial.printf("%10lu us  canh -> %s\n", micros(), ten(r));
    }
  }

  if (DEBOUNCE_MS > 0 && raw != stable && millis() - lastChange >= DEBOUNCE_MS)
  {
    stable = raw;
    Serial.printf("%8lu ms  %s   (%lu canh tho)\n", millis(), ten(stable), edges);
    edges = 0;
  }

  digitalWrite(LED_PIN, stable == LOW ? LOW : HIGH); // den sang = cua dong
}