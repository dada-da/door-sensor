-- Canh bao nhiet do, do am (AHT20 tren node coi).
--
-- env_alert_rule: mot dong duy nhat (id = 1), chinh tren giao dien. Nguong NULL
-- nghia la khong canh bao chieu do. hold_minutes: gia tri phai vuot nguong lien
-- tuc chung nay phut moi bao, de mot mau nhieu (mo cua so, hoi nong tu bep) khong
-- thanh mot tin Telegram.
CREATE TABLE env_alert_rule (
    id            SMALLINT         PRIMARY KEY DEFAULT 1,
    enabled       BOOLEAN          NOT NULL DEFAULT TRUE,
    temp_high     DOUBLE PRECISION,
    temp_low      DOUBLE PRECISION,
    rh_high       DOUBLE PRECISION,
    rh_low        DOUBLE PRECISION,
    hold_minutes  INT              NOT NULL DEFAULT 5,
    updated_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT chk_env_alert_single CHECK (id = 1),
    CONSTRAINT chk_env_alert_hold   CHECK (hold_minutes BETWEEN 0 AND 120),
    CONSTRAINT chk_env_alert_temp   CHECK (temp_high IS NULL OR temp_low IS NULL OR temp_low < temp_high),
    CONSTRAINT chk_env_alert_rh     CHECK (rh_high IS NULL OR rh_low IS NULL OR rh_low < rh_high)
);

-- Mac dinh cho phong o mien Bac: tren 35 do la nong bat thuong trong nha, duoi
-- 12 do chi gap khi ret dam. Do am tren 85 % keo dai (nom) lam moc va ri mach,
-- duoi 30 % hiem gap o Viet Nam nen gan nhu khong bao.
INSERT INTO env_alert_rule (id, enabled, temp_high, temp_low, rh_high, rh_low, hold_minutes)
VALUES (1, TRUE, 35, 12, 85, 30, 5);

-- Nhat ky canh bao: moi lan vuot nguong (RAISED) va moi lan tro lai binh thuong
-- (CLEARED). Dung de khoi phuc trang thai sau khi khoi dong lai (khong bao lai
-- mot canh bao dang mo) va de ve len giao dien, bao cao.
CREATE TABLE env_alert_event (
    id           BIGSERIAL        PRIMARY KEY,
    device_id    VARCHAR(64)      NOT NULL,
    metric       VARCHAR(8)       NOT NULL,
    direction    VARCHAR(8)       NOT NULL,
    kind         VARCHAR(8)       NOT NULL,
    value        DOUBLE PRECISION NOT NULL,
    threshold    DOUBLE PRECISION,
    occurred_at  TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT chk_env_alert_metric    CHECK (metric IN ('TEMP', 'RH')),
    CONSTRAINT chk_env_alert_direction CHECK (direction IN ('HIGH', 'LOW')),
    CONSTRAINT chk_env_alert_kind      CHECK (kind IN ('RAISED', 'CLEARED'))
);

CREATE INDEX idx_env_alert_event_time ON env_alert_event (occurred_at DESC);
CREATE INDEX idx_env_alert_event_key  ON env_alert_event (device_id, metric, occurred_at DESC);
