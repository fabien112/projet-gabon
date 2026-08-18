-- Initial schema for PostgreSQL (Flyway)
-- Tables: camera, people_counting_hourly, app_user, sync_meta

CREATE TABLE IF NOT EXISTS camera (
    id BIGSERIAL PRIMARY KEY,
    channel_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    site VARCHAR(128),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    manual BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_camera_channel ON camera(channel_id);

CREATE TABLE IF NOT EXISTS people_counting_hourly (
    id BIGSERIAL PRIMARY KEY,
    camera_id BIGINT NOT NULL REFERENCES camera(id),
    slot_date DATE NOT NULL,
    hour_start TIME NOT NULL,
    hour_end TIME NOT NULL,
    entries INT NOT NULL DEFAULT 0,
    exits INT NOT NULL DEFAULT 0,
    occupancy INT NOT NULL DEFAULT 0,
    synced_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    row_hash VARCHAR(128),
    last_source_timestamp TIMESTAMP WITHOUT TIME ZONE,
    finalized BOOLEAN DEFAULT FALSE
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_counting_slot ON people_counting_hourly (camera_id, slot_date, hour_start);
CREATE INDEX IF NOT EXISTS idx_counting_date ON people_counting_hourly (slot_date);
CREATE INDEX IF NOT EXISTS idx_counting_date_hour ON people_counting_hourly (slot_date, hour_start);

CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(120) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_app_user_username ON app_user (username);

CREATE TABLE IF NOT EXISTS sync_meta (
    id                    BIGINT PRIMARY KEY,
    last_started_at       TIMESTAMP WITHOUT TIME ZONE,
    last_finished_at      TIMESTAMP WITHOUT TIME ZONE,
    last_from_date        DATE,
    last_to_date          DATE,
    last_status           VARCHAR(32),
    last_message          VARCHAR(500),
    last_days_processed   INT NOT NULL DEFAULT 0,
    last_rows_upserted    INT NOT NULL DEFAULT 0,
    last_poll_at          TIMESTAMP WITHOUT TIME ZONE,
    last_poll_date        DATE
);

INSERT INTO sync_meta (id) VALUES (1)
ON CONFLICT (id) DO NOTHING;
