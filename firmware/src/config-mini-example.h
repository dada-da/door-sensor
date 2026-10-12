#pragma once

// ---- mang: chep tu config.h cua node tang 1 ----
#define WIFI_SSID "IoT-2G"
#define WIFI_PASSWORD "doi-thanh-mat-khau-that"

#define MQTT_HOST "192.168.2.212"
#define MQTT_PORT 1883

// ---- thiet bi: PHAI khac node tang 1 ----
#define DEVICE_ID "door-02"
#define DEVICE_LABEL "Cua tang 2"

// ---- chan ----
#define REED_PIN 4 // MC-38, keo len ngoai 1 MOhm
#define LED_PIN 8  // sang khi ghi LOW
#define BOOT_PIN 9 // nut BOOT

#define DEBOUNCE_MS 50

// ---- chu ky ----
// Phai NHO HON app.device.offline-after-seconds cua backend (watchdog dung chung
// mot nguong cho moi thiet bi), co chua le cho thoi gian thuc day va noi mang.
#define HEARTBEAT_SEC 300UL
// Gui that bai thi lan sau thuc som hon de gui lai
#define RETRY_SEC 60UL

// ---- gioi han thoi gian, de mat mang khong lam can pin ----
#define WIFI_TIMEOUT_MS 10000UL
#define MQTT_TIMEOUT_MS 5000UL
#define NTP_TIMEOUT_MS 3000UL
#define ECHO_TIMEOUT_MS 500UL
// Dong ho RTC van chay khi ngu sau, chi can dong bo NTP lai sau moi khoang nay
#define NTP_RESYNC_SEC (6UL * 3600UL)

// ---- che do phat trien ----
// 1: bat Serial, cho USB 1,5 giay, den bao, loi thoat bang nut BOOT.
// 0: khi do dong va khi lap that. awake/work_ms chi co nghia o che do 0.
#define DEBUG_SERIAL 1