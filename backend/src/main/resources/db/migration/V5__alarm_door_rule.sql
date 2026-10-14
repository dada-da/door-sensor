-- Cua nao kich hoat coi. Mot dong cho moi cua da duoc cau hinh.
--
-- Khong co dong nao cho mot cua nghia la cua do KICH HOAT coi (mac dinh an toan:
-- cua moi tu dang ky van duoc bao ve, khong phai nho bat tay). Chi khi nguoi dung
-- tat rieng mot cua thi moi co dong siren_enabled = FALSE.
--
-- Bang rieng, khong them cot vao device: bang device thuoc duong nhan su kien
-- tinh tu T0, khong sua.
CREATE TABLE alarm_door_rule (
    device_id      VARCHAR(64)  PRIMARY KEY,
    siren_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
