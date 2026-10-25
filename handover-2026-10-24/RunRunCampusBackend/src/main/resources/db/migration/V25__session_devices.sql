ALTER TABLE user_sessions
    ADD COLUMN created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN device_name VARCHAR(80) NOT NULL DEFAULT '旧版设备',
    ADD COLUMN client_type VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE user_sessions
    ADD CONSTRAINT user_sessions_client_type_check
        CHECK (client_type IN ('ANDROID', 'ADMIN_WEB', 'UNKNOWN'));

CREATE INDEX idx_user_sessions_user_created
    ON user_sessions(user_id, created_at DESC);
