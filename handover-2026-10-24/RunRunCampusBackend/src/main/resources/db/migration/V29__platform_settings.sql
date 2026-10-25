CREATE TABLE platform_settings (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    business_hours VARCHAR(80) NOT NULL,
    wait_minutes INTEGER NOT NULL CHECK (wait_minutes BETWEEN 1 AND 1440),
    delivery_minutes INTEGER NOT NULL CHECK (delivery_minutes BETWEEN 1 AND 1440),
    max_active_orders INTEGER NOT NULL CHECK (max_active_orders BETWEEN 1 AND 10),
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
INSERT INTO platform_settings (id, business_hours, wait_minutes, delivery_minutes, max_active_orders, updated_at)
VALUES (1, '09:00–23:00', 10, 30, 2, now());

ALTER TABLE admin_audit_logs DROP CONSTRAINT admin_audit_logs_action_check;
ALTER TABLE admin_audit_logs ADD CONSTRAINT admin_audit_logs_action_check CHECK (action IN (
    'COURIER_CERTIFICATION_APPROVED', 'COURIER_CERTIFICATION_REJECTED', 'USER_SUSPENDED', 'USER_RESTORED',
    'ORDER_ISSUE_CLAIMED', 'ORDER_ISSUE_RESOLVED', 'FEEDBACK_CLAIMED', 'FEEDBACK_RESOLVED',
    'ADMIN_CREATED', 'ADMIN_ROLE_CHANGED', 'ADMIN_SUSPENDED', 'ADMIN_RESTORED',
    'ADMIN_PASSWORD_RESET', 'ADMIN_PASSWORD_CHANGED', 'USER_LOGIN_UNLOCKED', 'ADMIN_LOGIN_UNLOCKED',
    'ADMIN_MFA_LOGIN_FAILED', 'ADMIN_MFA_LOGIN_SUCCEEDED', 'ADMIN_MFA_ENROLLED',
    'ADMIN_MFA_REPLACED', 'ADMIN_MFA_RECOVERY_USED', 'ADMIN_MFA_RESET', 'ORDER_DISPATCHED',
    'ORDER_REASSIGNED', 'COUPON_ISSUED', 'PLATFORM_SETTINGS_UPDATED'
));

ALTER TABLE admin_audit_logs DROP CONSTRAINT admin_audit_logs_target_type_check;
ALTER TABLE admin_audit_logs ADD CONSTRAINT admin_audit_logs_target_type_check CHECK (
    target_type IN ('COURIER_CERTIFICATION', 'USER', 'ORDER_ISSUE', 'FEEDBACK', 'ADMIN_ACCOUNT', 'ORDER',
                    'PLATFORM_SETTINGS')
);
