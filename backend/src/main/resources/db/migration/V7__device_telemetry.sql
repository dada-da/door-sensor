-- Nhat ky moi lan thuc cua thiet bi cua (moi tin tren home/door/+/state, ke ca
-- nhip bao khong doi trang thai). Phuc vu phan pin: duong xa pin cua thu nghiem
-- tang toc (buoc 9), phan bo thoi gian thuc work_ms cho mo hinh nang luong.
--
-- Tach khoi door_event: door_event chi ghi lan doi trang thai, va thuoc duong nhan
-- tu T0. Bang nay do client MQTT rieng ghi (DeviceTelemetryIngest), khong dung toi
-- DoorService. Moi cot so do deu cho phep NULL: door-01 khong gui pin, firmware cu
-- khong gui work_ms.
CREATE TABLE device_telemetry (
    id           BIGSERIAL    PRIMARY KEY,
    device_id    VARCHAR(64)  NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    device_ts    TIMESTAMPTZ,
    state        VARCHAR(8),
    wake         VARCHAR(16),
    battery_mv   INT,
    batt_adc_mv  INT,
    rssi         INT,
    boot_count   BIGINT,
    work_ms      INT,
    wifi_ms      INT,
    mqtt_ms      INT,
    rtt_ms       INT
);

CREATE INDEX idx_device_telemetry_device_time ON device_telemetry (device_id, received_at DESC);
