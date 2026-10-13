-- Bo sung cho phan bao dong chay that. Khong sua V2 da ap dung: Flyway kiem tra
-- checksum, sua file cu la loi "checksum mismatch" da gap mot lan.

-- Ba moc thoi gian de tach do tre bao dong thanh chang (bang 5.10):
--   door_occurred_at  node cua ghi nhan cua mo (dong ho node)
--   triggered_at      may chu phat lenh (da co tu V2)
--   acked_at          node coi bao da keu (dong ho may chu)
-- command_ok: lenh co duoc broker xac nhan (PUBACK) khong. FALSE nghia la coi
-- gan nhu chac chan khong keu, va da co tin Telegram rieng cho truong hop nay.
ALTER TABLE alarm_event
    ADD COLUMN siren_id          VARCHAR(64),
    ADD COLUMN door_occurred_at  TIMESTAMPTZ,
    ADD COLUMN command_ok        BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN acked_at          TIMESTAMPTZ;

ALTER TABLE alarm_event
    ADD CONSTRAINT chk_alarm_stop_source
        CHECK (stop_source IS NULL OR stop_source IN ('TIMEOUT', 'REMOTE', 'OVERRIDE'));

-- Lich su len xuong mang cua node coi. Khong dung connectivity_event vi bang do
-- co khoa ngoai toi device, va dua node coi vao device se lam giao dien hien them
-- mot "cua" cung DeviceWatchdog quet nham.
CREATE TABLE siren_connectivity_event (
    id          BIGSERIAL    PRIMARY KEY,
    siren_id    VARCHAR(64)  NOT NULL,
    online      BOOLEAN      NOT NULL,
    source      VARCHAR(16)  NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_siren_conn_source CHECK (source IN ('REPORTED', 'LWT', 'WATCHDOG'))
);

CREATE INDEX idx_siren_conn_time ON siren_connectivity_event (siren_id, received_at DESC);
