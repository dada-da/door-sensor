#pragma once
/*
 * Doc AHT20 qua I2C, tu viet thay vi dung thu vien.
 *
 * Giao thuc chi co ba lenh nen viet tay ngan hon them mot thu vien, va quan trong
 * hon: thieu cam bien (chua han, day long) phai la truong hop binh thuong, khong
 * duoc lam treo node coi. Node coi la co cau chap hanh, AHT20 chi la phan phu.
 *
 * Theo datasheet AHT20 (Aosong):
 *   - Dia chi 0x38.
 *   - Sau khi cap dien cho 40 ms. Doc byte trang thai, bit 3 (CAL) = 0 thi gui
 *     lenh khoi tao 0xBE 0x08 0x00 roi cho 10 ms.
 *   - Do: gui 0xAC 0x33 0x00, cho >= 80 ms, doc 7 byte:
 *       [0] trang thai, bit 7 (BUSY) = 1 la chua do xong
 *       [1..3] do am 20 bit, [3..5] nhiet do 20 bit, [6] CRC-8
 *   - CRC-8: da thuc 0x31, khoi tao 0xFF, tinh tren 6 byte dau.
 *   - RH  = raw / 2^20 * 100 %
 *   - T   = raw / 2^20 * 200 - 50 do C
 */

#include <Arduino.h>
#include <Wire.h>

class Aht20 {
public:
  static constexpr uint8_t ADDR = 0x38;

  /** Khoi tao bus I2C va cam bien. Tra false neu khong thay cam bien. */
  bool begin(int sda, int scl);

  /** Thu khoi tao lai, dung khi cam bien vua duoc cam vao. */
  bool reinit();

  /**
   * Do mot lan, chan khoang 80 ms.
   * Tra false khi khong co cam bien, cam bien ban, hoac sai CRC.
   * Doc sai CRC nghia la nhieu tren day I2C: bo qua lan do, khong gui so rac.
   */
  bool read(float &tempC, float &rhPct);

  bool present() const { return present_; }

  /** Ly do lan doc gan nhat that bai, de in ra Serial. */
  const char *lastError() const { return lastError_; }

private:
  bool present_ = false;
  const char *lastError_ = "";

  static uint8_t crc8(const uint8_t *data, size_t len);
  bool probe();
};
