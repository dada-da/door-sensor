/*
 * Buoc bringup cho AHT20: quet bus I2C tren SDA 5, SCL 6.
 * Dung khi AHT20 da han va da noi day. AHT20 phai hien o 0x38.
 *
 * Khong thay gi: kiem tra VIN (3V3, khong phai 5V), GND, va hai day SDA/SCL
 * co bi dao khong. Dao SDA/SCL la loi thuong gap nhat.
 *
 *   pio run -e scan -t upload && pio device monitor -e scan
 */

#include <Arduino.h>
#include <Wire.h>

#define I2C_SDA 5
#define I2C_SCL 6

void setup() {
  Serial.begin(115200);
  delay(1500);
  Wire.begin(I2C_SDA, I2C_SCL);
  Wire.setClock(100000);
  Serial.printf("\n=== Quet I2C, SDA %d, SCL %d ===\n", I2C_SDA, I2C_SCL);
}

void loop() {
  int found = 0;
  for (uint8_t addr = 1; addr < 127; addr++) {
    Wire.beginTransmission(addr);
    if (Wire.endTransmission() == 0) {
      Serial.printf("  Thay thiet bi o 0x%02X%s\n", addr, addr == 0x38 ? "  <- AHT20" : "");
      found++;
    }
  }
  Serial.printf("%d thiet bi. Quet lai sau 3 giay.\n\n", found);
  delay(3000);
}
