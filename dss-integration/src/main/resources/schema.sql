-- Grain horaire DSS : 1 ligne = 1 caméra + 1 créneau [hour_start, hour_end)
-- Sert le rapport personnalisé : période + tranche horaire + caméra + groupement jour

CREATE TABLE IF NOT EXISTS camera (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_id    VARCHAR(64)  NOT NULL,
    name          VARCHAR(255) NOT NULL,
    site          VARCHAR(128) NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    manual        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_camera_channel UNIQUE (channel_id)
);

CREATE TABLE IF NOT EXISTS people_counting_hourly (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    camera_id     BIGINT       NOT NULL,
    slot_date     DATE         NOT NULL,
    hour_start    TIME         NOT NULL,
    hour_end      TIME         NOT NULL,
    entries       INT          NOT NULL DEFAULT 0,
    exits         INT          NOT NULL DEFAULT 0,
    occupancy     INT          NOT NULL DEFAULT 0,
    synced_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_counting_slot UNIQUE (camera_id, slot_date, hour_start),
    CONSTRAINT fk_counting_camera FOREIGN KEY (camera_id) REFERENCES camera(id)
);

-- Add columns for change-detection and finalization (safe if already present)
ALTER TABLE people_counting_hourly ADD COLUMN IF NOT EXISTS row_hash VARCHAR(128);
ALTER TABLE people_counting_hourly ADD COLUMN IF NOT EXISTS last_source_timestamp TIMESTAMP NULL;
ALTER TABLE people_counting_hourly ADD COLUMN IF NOT EXISTS finalized BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_counting_date ON people_counting_hourly (slot_date);
CREATE INDEX IF NOT EXISTS idx_counting_date_hour ON people_counting_hourly (slot_date, hour_start);

CREATE TABLE IF NOT EXISTS app_user (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(64)  NOT NULL,
    password_hash  VARCHAR(120) NOT NULL,
    role           VARCHAR(20)  NOT NULL DEFAULT 'USER',
    enabled        BOOLEAN      NOT NULL DEFAULT TRUE,
    is_system      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_user_username UNIQUE (username)
);
