-- Cau hinh bao dong. Mot dong duy nhat, rang buoc bang CHECK.
-- Khong dung bang cau hinh tong quat dang khoa gia tri cho mot tinh nang.
CREATE TABLE alarm_schedule (
    id              SMALLINT     PRIMARY KEY DEFAULT 1,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    start_time      TIME         NOT NULL DEFAULT '00:00',
    end_time        TIME         NOT NULL DEFAULT '07:00',
    -- Thoi luong coi keu, nguoi dung chinh duoc. Firmware van cat ve tran cung
    -- 120 giay bat ke gia tri nay, xem muc 3.10.4 cua bao cao.
    siren_seconds   INT          NOT NULL DEFAULT 30,
    -- 1 = thu Hai, theo chuan ISO-8601. Rong nghia la khong ngay nao.
    days_of_week    VARCHAR(20)  NOT NULL DEFAULT '1,2,3,4,5,6,7',
    -- Dieu khien thu cong, de len lich. NONE | ARMED | DISARMED.
    override_mode   VARCHAR(10)  NOT NULL DEFAULT 'NONE',
    -- Thao tac tay het hieu luc o ranh gioi khung gio ke tiep, khong keo dai vo han.
    override_until  TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT alarm_schedule_one_row CHECK (id = 1),
    CONSTRAINT alarm_schedule_siren_range CHECK (siren_seconds BETWEEN 1 AND 120),
    CONSTRAINT alarm_schedule_override CHECK (override_mode IN ('NONE', 'ARMED', 'DISARMED'))
);

INSERT INTO alarm_schedule (id) VALUES (1);

-- Lich su coi keu. Tach rieng khoi door_event vi mot lan cua mo co the khong
-- kich hoat coi, va mot lan coi keu co the dung bang nhieu cach khac nhau.
CREATE TABLE alarm_event (
    id              BIGSERIAL    PRIMARY KEY,
    device_id       VARCHAR(64)  NOT NULL,
    triggered_at    TIMESTAMPTZ  NOT NULL,
    duration_ms     INT          NOT NULL,
    reason          VARCHAR(32)  NOT NULL,
    stopped_at      TIMESTAMPTZ,
    -- TIMEOUT | REMOTE | OVERRIDE. Cot nay la so lieu cho muc 5.6.2.
    stop_source     VARCHAR(16)
);

CREATE INDEX idx_alarm_event_triggered ON alarm_event (triggered_at DESC);
