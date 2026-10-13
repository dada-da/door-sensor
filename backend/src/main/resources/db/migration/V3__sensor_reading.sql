-- So do moi truong (AHT20 tren node coi). Chuoi so do lien tuc, tach khoi
-- door_event vi khac ban chat: door_event la su kien roi rac, bang nay la mau
-- lay deu theo thoi gian.
--
-- Khong rang buoc khoa ngoai toi device: bang device mang trang thai cua
-- (last_state, pin) va bi DeviceWatchdog quet. Dua node moi truong vao do se
-- lam giao dien hien them mot "cua" va watchdog bao mat lien lac nham.
CREATE TABLE sensor_reading (
    id             BIGSERIAL        PRIMARY KEY,
    device_id      VARCHAR(64)      NOT NULL,
    measured_at    TIMESTAMPTZ      NOT NULL,
    received_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    temperature_c  DOUBLE PRECISION,
    humidity_pct   DOUBLE PRECISION,
    CONSTRAINT chk_reading_has_value CHECK (temperature_c IS NOT NULL OR humidity_pct IS NOT NULL),
    CONSTRAINT chk_reading_temp CHECK (temperature_c IS NULL OR temperature_c BETWEEN -40 AND 85),
    CONSTRAINT chk_reading_rh   CHECK (humidity_pct  IS NULL OR humidity_pct  BETWEEN 0 AND 100)
);

-- Truy van thong ke luon loc theo thiet bi va khoang thoi gian.
CREATE INDEX idx_sensor_reading_device_time ON sensor_reading (device_id, measured_at DESC);
