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

// ---- dien ap pin (buoc 8). Xoa ca khoi nay neu chua lap cau chia ----
// GPIO 3 thuoc ADC1 (ADC2 khong dung duoc khi bat Wi-Fi). Cau chia 1 MOhm + 1 MOhm.
#define BATT_PIN 3
// (R1 + R2) / R2. Sau hieu chuan: he_so_moi = he_so_cu * (dong_ho / battery_mv)
#define BATT_DIVIDER 2.000f
#define BATT_SAMPLES 32
// Duoi muc nay 3 lan thuc lien tiep thi gui tin cuoi (wake = lowbatt) roi ngu han
#define BATT_CUTOFF_MV 3300

// ---- chu ky ----
// Phai NHO HON app.device.offline-after-seconds cua backend (watchdog dung chung
// mot nguong cho moi thiet bi), co chua le cho thoi gian thuc day va noi mang.
// Thu nghiem tang toc (buoc 9): dat 10UL va DEBUG_SERIAL 0.
// Doi tu xa khong can nap lai: gui tin GIU LAI len home/door/<DEVICE_ID>/config
//   mosquitto_pub -r -q 1 -t home/door/door-02/config -m '{"heartbeat_sec":300}'
// Chi nhan 10-600 s; 0 la quay ve gia tri duoi day. Tin state co truong "hb".
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