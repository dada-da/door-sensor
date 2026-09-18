-- Thiet bi cam bien. Mot ban ghi cho moi ESP32 trong he thong.
CREATE TABLE device (
    id               VARCHAR(64) PRIMARY KEY,
    label            VARCHAR(128) NOT NULL,
    location         VARCHAR(128),
    first_seen       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen        TIMESTAMPTZ,
    online           BOOLEAN      NOT NULL DEFAULT FALSE,
    last_state       VARCHAR(16),
    last_state_at    TIMESTAMPTZ,
    last_battery_mv  INTEGER,
    last_rssi        INTEGER,
    boot_count       INTEGER
);


CREATE TABLE door_event (
    id           BIGSERIAL    PRIMARY KEY,
    device_id    VARCHAR(64)  NOT NULL REFERENCES device (id),
    state        VARCHAR(16)  NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    battery_mv   INTEGER,
    rssi         INTEGER,
    boot_count   INTEGER,
    duration_ms  BIGINT,
    CONSTRAINT chk_door_event_state CHECK (state IN ('OPEN', 'CLOSED'))
);

CREATE TABLE connectivity_event (
    id          BIGSERIAL    PRIMARY KEY,
    device_id   VARCHAR(64)  NOT NULL REFERENCES device (id),
    online      BOOLEAN      NOT NULL,
    source      VARCHAR(16)  NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_conn_source CHECK (source IN ('REPORTED', 'LWT', 'WATCHDOG'))
);

CREATE INDEX idx_door_event_device_time ON door_event (device_id, occurred_at DESC);
CREATE INDEX idx_door_event_time        ON door_event (occurred_at DESC);
CREATE INDEX idx_conn_event_device_time ON connectivity_event (device_id, received_at DESC);
