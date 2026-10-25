ALTER TABLE order_issues ADD COLUMN handling_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE order_issues ADD COLUMN assignee_id BIGINT REFERENCES app_users(id);
ALTER TABLE order_issues ADD COLUMN resolution VARCHAR(500);
ALTER TABLE order_issues ADD COLUMN handled_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE order_issues ADD COLUMN updated_at TIMESTAMP(6) WITH TIME ZONE;
UPDATE order_issues SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE order_issues ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE order_issues ADD CONSTRAINT order_issues_handling_status_check CHECK (
    handling_status IN ('PENDING', 'PROCESSING', 'RESOLVED')
);
CREATE INDEX idx_order_issues_handling_created ON order_issues (handling_status, created_at DESC);
CREATE INDEX idx_order_issues_assignee_updated ON order_issues (assignee_id, updated_at DESC);

ALTER TABLE admin_audit_logs DROP CONSTRAINT admin_audit_logs_action_check;
ALTER TABLE admin_audit_logs ADD CONSTRAINT admin_audit_logs_action_check CHECK (
    action IN (
        'COURIER_CERTIFICATION_APPROVED', 'COURIER_CERTIFICATION_REJECTED',
        'USER_SUSPENDED', 'USER_RESTORED', 'ORDER_ISSUE_CLAIMED', 'ORDER_ISSUE_RESOLVED'
    )
);

ALTER TABLE admin_audit_logs DROP CONSTRAINT admin_audit_logs_target_type_check;
ALTER TABLE admin_audit_logs ADD CONSTRAINT admin_audit_logs_target_type_check CHECK (
    target_type IN ('COURIER_CERTIFICATION', 'USER', 'ORDER_ISSUE')
);
