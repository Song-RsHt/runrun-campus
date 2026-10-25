ALTER TABLE campus_orders DROP CONSTRAINT campus_orders_payment_method_check;
ALTER TABLE campus_orders ADD CONSTRAINT campus_orders_payment_method_check
    CHECK (payment_method IN ('WECHAT_APP', 'CASH_ON_DELIVERY', 'PERSONAL_QR_MANUAL'));

ALTER TABLE campus_orders
    ADD COLUMN manual_payment_status VARCHAR(24) NOT NULL DEFAULT 'NOT_REQUESTED',
    ADD COLUMN payer_reference VARCHAR(64),
    ADD COLUMN payment_requested_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN payment_submitted_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN payment_reviewed_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN payment_review_note VARCHAR(200),
    ADD COLUMN payment_reviewer_id BIGINT REFERENCES app_users(id),
    ADD COLUMN courier_settlement_status VARCHAR(24) NOT NULL DEFAULT 'NOT_APPLICABLE',
    ADD COLUMN courier_settled_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN courier_settlement_reviewer_id BIGINT REFERENCES app_users(id),
    ADD CONSTRAINT campus_orders_manual_payment_status_check CHECK (
        manual_payment_status IN ('NOT_REQUESTED', 'AWAITING_PAYMENT', 'SUBMITTED', 'CONFIRMED', 'REJECTED')
    ),
    ADD CONSTRAINT campus_orders_courier_settlement_status_check CHECK (
        courier_settlement_status IN ('NOT_APPLICABLE', 'PENDING', 'SETTLED')
    );

CREATE INDEX idx_campus_orders_manual_payment_review
    ON campus_orders (manual_payment_status, payment_submitted_at DESC)
    WHERE payment_method = 'PERSONAL_QR_MANUAL';

CREATE INDEX idx_campus_orders_courier_settlement
    ON campus_orders (courier_settlement_status, updated_at DESC)
    WHERE courier_settlement_status = 'PENDING';

ALTER TABLE admin_audit_logs DROP CONSTRAINT admin_audit_logs_action_check;
ALTER TABLE admin_audit_logs ADD CONSTRAINT admin_audit_logs_action_check CHECK (action IN (
    'COURIER_CERTIFICATION_APPROVED', 'COURIER_CERTIFICATION_REJECTED', 'USER_SUSPENDED', 'USER_RESTORED',
    'ORDER_ISSUE_CLAIMED', 'ORDER_ISSUE_RESOLVED', 'FEEDBACK_CLAIMED', 'FEEDBACK_RESOLVED',
    'ADMIN_CREATED', 'ADMIN_ROLE_CHANGED', 'ADMIN_SUSPENDED', 'ADMIN_RESTORED',
    'ADMIN_PASSWORD_RESET', 'ADMIN_PASSWORD_CHANGED', 'USER_LOGIN_UNLOCKED', 'ADMIN_LOGIN_UNLOCKED',
    'ADMIN_MFA_LOGIN_FAILED', 'ADMIN_MFA_LOGIN_SUCCEEDED', 'ADMIN_MFA_ENROLLED',
    'ADMIN_MFA_REPLACED', 'ADMIN_MFA_RECOVERY_USED', 'ADMIN_MFA_RESET',
    'ORDER_DISPATCHED', 'ORDER_REASSIGNED', 'PLATFORM_SETTINGS_UPDATED', 'COUPON_ISSUED',
    'CREDIT_ADJUSTED', 'CREDIT_EVENT_REVERSED', 'RISK_SIGNAL_CONFIRMED', 'RISK_SIGNAL_DISMISSED',
    'MANUAL_PAYMENT_CONFIRMED', 'MANUAL_PAYMENT_REJECTED', 'COURIER_SETTLEMENT_CONFIRMED'
));
