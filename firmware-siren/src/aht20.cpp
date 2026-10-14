#include "aht20.h"

bool Aht20::begin(int sda, int scl) {
  Wire.begin(sda, scl);
  Wire.setClock(100000); // 100 kHz: day dupont dai 10-20 cm, khong can nhanh
  // Thieu cam bien thi Wire cho het thoi gian mac dinh (1 giay) moi lan doc.
  // Ha xuong de vong lap chinh khong bi chan lau.
  Wire.setTimeOut(50);
  delay(40); // cho cam bien san sang sau khi cap dien
  return reinit();
}

bool Aht20::probe() {
  Wire.beginTransmission(ADDR);
  return Wire.endTransmission() == 0;
}

bool Aht20::reinit() {
  present_ = false;
  if (!probe()) {
    lastError_ = "khong thay thiet bi o 0x38";
    return false;
  }
  if (Wire.requestFrom(ADDR, (uint8_t)1) != 1) {
    lastError_ = "khong doc duoc trang thai";
    return false;
  }
  uint8_t status = Wire.read();
  if ((status & 0x08) == 0) {
    Wire.beginTransmission(ADDR);
    Wire.write(0xBE);
    Wire.write(0x08);
    Wire.write(0x00);
    if (Wire.endTransmission() != 0) {
      lastError_ = "gui lenh khoi tao that bai";
      return false;
    }
    delay(10);
  }
  present_ = true;
  lastError_ = "";
  return true;
}

uint8_t Aht20::crc8(const uint8_t *data, size_t len) {
  uint8_t crc = 0xFF;
  for (size_t i = 0; i < len; i++) {
    crc ^= data[i];
    for (int b = 0; b < 8; b++) {
      crc = (crc & 0x80) ? (uint8_t)((crc << 1) ^ 0x31) : (uint8_t)(crc << 1);
    }
  }
  return crc;
}

bool Aht20::read(float &tempC, float &rhPct) {
  if (!present_) {
    lastError_ = "chua khoi tao";
    return false;
  }

  Wire.beginTransmission(ADDR);
  Wire.write(0xAC);
  Wire.write(0x33);
  Wire.write(0x00);
  if (Wire.endTransmission() != 0) {
    present_ = false; // day long giua chung: lan sau thu khoi tao lai
    lastError_ = "mat ket noi I2C";
    return false;
  }

  uint8_t buf[7];
  // Datasheet: >= 80 ms. Thu lai toi da 3 lan neu cam bien bao con ban.
  for (int attempt = 0; attempt < 3; attempt++) {
    delay(attempt == 0 ? 80 : 20);
    if (Wire.requestFrom(ADDR, (uint8_t)7) != 7) {
      lastError_ = "doc thieu byte";
      return false;
    }
    for (int i = 0; i < 7; i++) buf[i] = Wire.read();
    if ((buf[0] & 0x80) == 0) break;
  }
  if (buf[0] & 0x80) {
    lastError_ = "cam bien van ban";
    return false;
  }
  if (crc8(buf, 6) != buf[6]) {
    lastError_ = "sai CRC";
    return false;
  }

  uint32_t rawRh = ((uint32_t)buf[1] << 12) | ((uint32_t)buf[2] << 4) | (buf[3] >> 4);
  uint32_t rawT = (((uint32_t)buf[3] & 0x0F) << 16) | ((uint32_t)buf[4] << 8) | buf[5];

  rhPct = rawRh * 100.0f / 1048576.0f;
  tempC = rawT * 200.0f / 1048576.0f - 50.0f;
  return true;
}
