#pragma once
// Chep thanh config.h roi dien thong tin that. config.h nam trong .gitignore.

// ---- mang: giong node tang 1 ----
#define WIFI_SSID "IoT-2G"
#define WIFI_PASSWORD "doi-thanh-mat-khau-that"

#define MQTT_HOST "192.168.2.212"
#define MQTT_PORT 1883

// ---- thiet bi: PHAI khop app.alarm.siren-id cua backend ----
#define DEVICE_ID "siren-01"

// ---- chan (bao cao muc 3.2: tranh GPIO 2, 8, 9 la chan cau hinh khoi dong) ----
#define SIREN_PIN 3 // chan S cua module coi
#define LED_PIN 8   // den tren board, sang khi ghi LOW
#define I2C_SDA 5   // AHT20
#define I2C_SCL 6

// Muc logic lam coi KEU. Module dung transistor NPN (S8050, 2N2222): 1.
// Module dung transistor PNP (S8550, 9012): 0, coi keu khi chan S xuong thap.
// Chua chac thi de 1, nap thu: coi keu ngay khi khoi dong la nguoc, doi thanh 0.
#define SIREN_ACTIVE_LEVEL 1

// ---- chu ky ----
// Backend coi coi la chet sau 300 giay khong co tin song (app.alarm.siren-offline-after-seconds).
#define HEARTBEAT_MS 60000UL
#define ENV_PERIOD_MS 60000UL

// Tran cung thoi luong coi, doc lap voi may chu (bao cao muc 3.10.4, PCN-09).
#define MAX_SIREN_MS 120000UL
#define TEST_MS 200UL

// Mat Wi-Fi qua lau thi khoi dong lai, giong node tang 1.
#define RECOVERY_TIMEOUT_MS (10UL * 60UL * 1000UL)
