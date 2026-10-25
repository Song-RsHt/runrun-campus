CREATE TABLE credit_profiles (
    user_id BIGINT PRIMARY KEY REFERENCES app_users(id),
    score INTEGER NOT NULL DEFAULT 100 CHECK (score BETWEEN 0 AND 100),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO credit_profiles (user_id)
SELECT id FROM app_users WHERE role <> 'ADMIN' AND deleted_at IS NULL;

CREATE FUNCTION create_default_credit_profile() RETURNS trigger AS $$
BEGIN
    IF NEW.role <> 'ADMIN' THEN
        INSERT INTO credit_profiles (user_id) VALUES (NEW.id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_app_users_credit_profile
AFTER INSERT ON app_users
FOR EACH ROW EXECUTE FUNCTION create_default_credit_profile();

CREATE TABLE risk_signals (
    id BIGSERIAL PRIMARY KEY,
    subject_user_id BIGINT NOT NULL REFERENCES app_users(id),
    signal_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    source_key VARCHAR(120) NOT NULL UNIQUE,
    order_id BIGINT REFERENCES campus_orders(id),
    details VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by_id BIGINT REFERENCES app_users(id),
    review_note VARCHAR(300),
    CONSTRAINT chk_risk_signal_status CHECK (status IN ('PENDING', 'CONFIRMED', 'DISMISSED')),
    CONSTRAINT chk_risk_signal_review CHECK (
        (status = 'PENDING' AND reviewed_at IS NULL AND reviewed_by_id IS NULL)
        OR (status <> 'PENDING' AND reviewed_at IS NOT NULL AND reviewed_by_id IS NOT NULL)
    )
);

CREATE INDEX idx_risk_signals_status_created ON risk_signals (status, created_at DESC);
CREATE INDEX idx_risk_signals_subject ON risk_signals (subject_user_id, created_at DESC);

CREATE TABLE credit_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_users(id),
    operator_id BIGINT NOT NULL REFERENCES app_users(id),
    event_type VARCHAR(24) NOT NULL,
    delta INTEGER NOT NULL CHECK (delta BETWEEN -100 AND 100),
    balance_after INTEGER NOT NULL CHECK (balance_after BETWEEN 0 AND 100),
    reason VARCHAR(300) NOT NULL,
    risk_signal_id BIGINT REFERENCES risk_signals(id),
    original_event_id BIGINT REFERENCES credit_events(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_credit_event_type CHECK (event_type IN ('ADMIN_ADJUSTMENT', 'RISK_CONFIRMED', 'REVERSAL'))
);

CREATE UNIQUE INDEX uq_credit_events_reversal ON credit_events (original_event_id)
WHERE original_event_id IS NOT NULL;
CREATE INDEX idx_credit_events_user_created ON credit_events (user_id, created_at DESC);

ALTER TABLE admin_audit_logs DROP CONSTRAINT admin_audit_logs_action_check;
ALTER TABLE admin_audit_logs ADD CONSTRAINT admin_audit_logs_action_check CHECK (action IN (
    'COURIER_CERTIFICATION_APPROVED', 'COURIER_CERTIFICATION_REJECTED', 'USER_SUSPENDED', 'USER_RESTORED',
    'ORDER_ISSUE_CLAIMED', 'ORDER_ISSUE_RESOLVED', 'FEEDBACK_CLAIMED', 'FEEDBACK_RESOLVED',
    'ADMIN_CREATED', 'ADMIN_ROLE_CHANGED', 'ADMIN_SUSPENDED', 'ADMIN_RESTORED',
    'ADMIN_PASSWORD_RESET', 'ADMIN_PASSWORD_CHANGED', 'USER_LOGIN_UNLOCKED', 'ADMIN_LOGIN_UNLOCKED',
    'ADMIN_MFA_LOGIN_FAILED', 'ADMIN_MFA_LOGIN_SUCCEEDED', 'ADMIN_MFA_ENROLLED',
    'ADMIN_MFA_REPLACED', 'ADMIN_MFA_RECOVERY_USED', 'ADMIN_MFA_RESET',
    'ORDER_DISPATCHED', 'ORDER_REASSIGNED', 'PLATFORM_SETTINGS_UPDATED', 'COUPON_ISSUED',
    'CREDIT_ADJUSTED', 'CREDIT_EVENT_REVERSED', 'RISK_SIGNAL_CONFIRMED', 'RISK_SIGNAL_DISMISSED'
));

ALTER TABLE admin_audit_logs DROP CONSTRAINT admin_audit_logs_target_type_check;
ALTER TABLE admin_audit_logs ADD CONSTRAINT admin_audit_logs_target_type_check CHECK (target_type IN (
    'COURIER_CERTIFICATION', 'USER', 'ORDER_ISSUE', 'FEEDBACK', 'ADMIN_ACCOUNT',
    'ORDER', 'PLATFORM_SETTINGS', 'CREDIT_EVENT', 'RISK_SIGNAL'
));
