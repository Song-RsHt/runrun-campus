ALTER TABLE app_users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE admin_audit_logs DROP CONSTRAINT admin_audit_logs_action_check;
ALTER TABLE admin_audit_logs ADD CONSTRAINT admin_audit_logs_action_check CHECK (
    action IN ('COURIER_CERTIFICATION_APPROVED', 'COURIER_CERTIFICATION_REJECTED', 'USER_SUSPENDED', 'USER_RESTORED')
);

ALTER TABLE admin_audit_logs DROP CONSTRAINT admin_audit_logs_target_type_check;
ALTER TABLE admin_audit_logs ADD CONSTRAINT admin_audit_logs_target_type_check CHECK (
    target_type IN ('COURIER_CERTIFICATION', 'USER')
);
