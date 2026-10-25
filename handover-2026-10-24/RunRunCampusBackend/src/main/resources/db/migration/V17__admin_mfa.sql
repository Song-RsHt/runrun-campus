ALTER TABLE app_users ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE user_sessions ADD COLUMN admin_mfa_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_sessions ADD COLUMN security_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE admin_mfa_credentials (
    user_id BIGINT PRIMARY KEY REFERENCES app_users(id),
    secret_ciphertext TEXT,
    last_used_step BIGINT NOT NULL DEFAULT -1,
    recovery_hashes TEXT NOT NULL DEFAULT '',
    reset_grant_hash VARCHAR(64),
    reset_grant_expires_at TIMESTAMP WITH TIME ZONE,
    challenge_hash VARCHAR(64) UNIQUE,
    challenge_expires_at TIMESTAMP WITH TIME ZONE,
    challenge_version BIGINT NOT NULL DEFAULT 0,
    challenge_attempts INTEGER NOT NULL DEFAULT 0,
    challenge_kind VARCHAR(16),
    pending_secret_ciphertext TEXT
);

DELETE FROM user_sessions WHERE user_id IN (SELECT id FROM app_users WHERE role = 'ADMIN');

ALTER TABLE admin_audit_logs DROP CONSTRAINT admin_audit_logs_action_check;
ALTER TABLE admin_audit_logs ADD CONSTRAINT admin_audit_logs_action_check CHECK (action IN (
    'COURIER_CERTIFICATION_APPROVED', 'COURIER_CERTIFICATION_REJECTED', 'USER_SUSPENDED', 'USER_RESTORED',
    'ORDER_ISSUE_CLAIMED', 'ORDER_ISSUE_RESOLVED', 'FEEDBACK_CLAIMED', 'FEEDBACK_RESOLVED',
    'ADMIN_CREATED', 'ADMIN_ROLE_CHANGED', 'ADMIN_SUSPENDED', 'ADMIN_RESTORED',
    'ADMIN_PASSWORD_RESET', 'ADMIN_PASSWORD_CHANGED', 'USER_LOGIN_UNLOCKED', 'ADMIN_LOGIN_UNLOCKED',
    'ADMIN_MFA_LOGIN_FAILED', 'ADMIN_MFA_LOGIN_SUCCEEDED', 'ADMIN_MFA_ENROLLED',
    'ADMIN_MFA_REPLACED', 'ADMIN_MFA_RECOVERY_USED', 'ADMIN_MFA_RESET'
));
