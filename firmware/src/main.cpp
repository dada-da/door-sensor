
#include "driver/gpio.h"
#include <Arduino.h>
#include "esp_sleep.h"

#define REED_PIN 4
#define LED_PIN 8
#define BOOT_PIN 9 // nut BOOT tren board
#define SLEEP_SEC 10

RTC_DATA_ATTR uint32_t bootCount = 0; // nam trong bo nho RTC, song qua giac ngu

const char *lyDo()
{
  switch (esp_sleep_get_wakeup_cause())
  {
  case ESP_SLEEP_WAKEUP_TIMER:
    return "het gio hen (nhip bao)";
  case ESP_SLEEP_WAKEUP_GPIO:
    return "cua doi trang thai";
  default:
    return "cap dien hoac reset";
  }
}

void setup()
{
  gpio_hold_dis((gpio_num_t)REED_PIN); // nha khoa con lai tu giac ngu truoc
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW); // den sang = dang thuc
  pinMode(REED_PIN, INPUT);   // van dung dien tro keo len ngoai 1 MOhm
  pinMode(BOOT_PIN, INPUT_PULLUP);
  bootCount++;

  Serial.begin(115200);
  delay(2000); // cho may tinh nhan lai cong USB sau khi thuc

  Serial.printf("\n#%lu  ly do: %s\n", bootCount, lyDo());
  Serial.printf("Chan danh thuc: 0x%llx\n", esp_sleep_get_gpio_wakeup_status());

  // Loi thoat: dang giu nut BOOT thi khong ngu, de nap code moi
  if (digitalRead(BOOT_PIN) == LOW)
  {
    Serial.println("Dang giu BOOT -> khong ngu. Nap code moi duoc.");
    while (true)
    {
      digitalWrite(LED_PIN, !digitalRead(LED_PIN));
      delay(100);
    }
  }

  bool dong = (digitalRead(REED_PIN) == LOW);
  Serial.printf("Cua %s -> hen thuc khi chan len %s\n",
                dong ? "DONG" : "MO", dong ? "CAO" : "THAP");

  esp_sleep_enable_timer_wakeup((uint64_t)SLEEP_SEC * 1000000ULL);

  // 1) Chan gia GPIO 5 (khong noi gi): bat che do danh thuc bang GPIO cua IDF.
  //    IDF se bat keo xuong cho chan nay -> no luon o muc THAP, khong bao gio danh thuc nham.
  esp_deep_sleep_enable_gpio_wakeup(BIT(5), ESP_GPIO_WAKEUP_GPIO_HIGH);

  // 2) Chan that GPIO 4: dang ky truc tiep, khong qua IDF -> chi con dien tro 1 MOhm ben ngoai
  gpio_pullup_dis((gpio_num_t)REED_PIN);
  gpio_pulldown_dis((gpio_num_t)REED_PIN);
  gpio_deep_sleep_wakeup_enable((gpio_num_t)REED_PIN,
                                dong ? GPIO_INTR_HIGH_LEVEL : GPIO_INTR_LOW_LEVEL);
  gpio_hold_en((gpio_num_t)REED_PIN); // giu nguyen cau hinh nay suot giac ngu

  Serial.printf("Di ngu %d giay (da thuc %lu ms)\n", SLEEP_SEC, millis());
  Serial.flush();
  digitalWrite(LED_PIN, HIGH);
  esp_deep_sleep_start();
}

void loop() {} // khong bao gio chay toi