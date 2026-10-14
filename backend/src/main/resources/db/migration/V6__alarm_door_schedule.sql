-- Lich bao ve rieng tung cua (14/10). Thay lich chung trong alarm_schedule va
-- quiet-hours trong application.yml: moi cua mot khung gio, vua la gio coi keu
-- vua la "ngoai gio thuong le" cua tin Telegram.
--
-- Khong sua V5 da co the da ap dung (Flyway kiem checksum).
ALTER TABLE alarm_door_rule
    ADD COLUMN start_time     TIME        NOT NULL DEFAULT '00:00',
    ADD COLUMN end_time       TIME        NOT NULL DEFAULT '07:00',
    ADD COLUMN days_of_week   VARCHAR(20) NOT NULL DEFAULT '1,2,3,4,5,6,7',
    ADD COLUMN override_mode  VARCHAR(10) NOT NULL DEFAULT 'NONE',
    ADD COLUMN override_until TIMESTAMPTZ,
    ADD CONSTRAINT alarm_door_rule_override CHECK (override_mode IN ('NONE', 'ARMED', 'DISARMED'));

-- Giu nguyen hanh vi dang chay: moi cua da co nhan khung gio cua lich chung cu,
-- va bat/tat coi theo cong tac chung cu neu cua do chua tung duoc cau hinh rieng.
UPDATE alarm_door_rule r
SET start_time   = s.start_time,
    end_time     = s.end_time,
    days_of_week = s.days_of_week
FROM alarm_schedule s
WHERE s.id = 1;

INSERT INTO alarm_door_rule (device_id, siren_enabled, start_time, end_time, days_of_week)
SELECT d.id, s.enabled, s.start_time, s.end_time, s.days_of_week
FROM device d
CROSS JOIN alarm_schedule s
WHERE s.id = 1
  AND NOT EXISTS (SELECT 1 FROM alarm_door_rule r WHERE r.device_id = d.id);

-- Tat coi tu Telegram la mot nguon dung rieng, de bang 5.11 phan biet duoc.
ALTER TABLE alarm_event DROP CONSTRAINT chk_alarm_stop_source;
ALTER TABLE alarm_event
    ADD CONSTRAINT chk_alarm_stop_source
        CHECK (stop_source IS NULL OR stop_source IN ('TIMEOUT', 'REMOTE', 'OVERRIDE', 'TELEGRAM'));

-- alarm_schedule tu nay chi con siren_seconds la co hieu luc. Khong xoa cot cu de
-- con quay lai duoc.
COMMENT ON TABLE alarm_schedule IS 'Tu V6 chi dung siren_seconds. Khung gio va thao tac tay nam o alarm_door_rule.';
