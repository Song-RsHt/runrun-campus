ALTER TABLE app_users ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE app_users ADD COLUMN login_locked_until TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE app_users ADD CONSTRAINT app_users_failed_login_attempts_check CHECK (failed_login_attempts >= 0);
CREATE INDEX idx_app_users_login_locked_until ON app_users (login_locked_until)
    WHERE login_locked_until IS NOT NULL;
