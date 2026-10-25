ALTER TABLE app_users ADD COLUMN admin_role VARCHAR(24);
UPDATE app_users SET admin_role = 'SUPER_ADMIN' WHERE role = 'ADMIN';
ALTER TABLE app_users ADD CONSTRAINT app_users_admin_role_check CHECK (
    (role = 'ADMIN' AND admin_role IN ('SUPER_ADMIN', 'OPERATIONS', 'SUPPORT', 'REVIEWER', 'AUDITOR'))
    OR (role <> 'ADMIN' AND admin_role IS NULL)
);
CREATE INDEX idx_app_users_admin_role ON app_users (admin_role) WHERE role = 'ADMIN';

ALTER TABLE admin_audit_logs DROP CONSTRAINT admin_audit_logs_action_check;
ALTER TABLE admin_audit_logs ADD CONSTRAINT admin_audit_logs_action_check CHECK (
    action IN (
        'COURIER_CERTIFICATION_APPROVED', 'COURIER_CERTIFICATION_REJECTED',
        'USER_SUSPENDED', 'USER_RESTORED', 'ORDER_ISSUE_CLAIMED', 'ORDER_ISSUE_RESOLVED',
        'FEEDBACK_CLAIMED', 'FEEDBACK_RESOLVED', 'ADMIN_CREATED', 'ADMIN_ROLE_CHANGED'
    )
);

ALTER TABLE admin_audit_logs DROP CONSTRAINT admin_audit_logs_target_type_check;
ALTER TABLE admin_audit_logs ADD CONSTRAINT admin_audit_logs_target_type_check CHECK (
    target_type IN ('COURIER_CERTIFICATION', 'USER', 'ORDER_ISSUE', 'FEEDBACK', 'ADMIN_ACCOUNT')
);
