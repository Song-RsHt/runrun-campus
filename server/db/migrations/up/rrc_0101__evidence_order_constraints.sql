BEGIN;
ALTER TABLE runrun_v2.admin_audit_logs DROP CONSTRAINT admin_audit_action_ck;
ALTER TABLE runrun_v2.admin_audit_logs ADD CONSTRAINT admin_audit_action_ck CHECK (action IN ('V2_ADMIN_ACCESS_CHANGED','COURIER_CERTIFICATION_SUBMITTED','COURIER_CERTIFICATION_PHOTO_UPLOADED','COURIER_CERTIFICATION_PHOTO_READ','COURIER_CERTIFICATION_APPROVED','COURIER_CERTIFICATION_REJECTED','EVIDENCE_HOLD_CREATED','EVIDENCE_HOLD_RELEASED'));
ALTER TABLE runrun_v2.admin_audit_logs DROP CONSTRAINT admin_audit_target_ck;
ALTER TABLE runrun_v2.admin_audit_logs ADD CONSTRAINT admin_audit_target_ck CHECK (target_type IN ('APP_USER','COURIER_CERTIFICATION','SERVICE_CONFIG','EVIDENCE_HOLD'));
ALTER TABLE runrun_v2.evidence_holds
  ADD CONSTRAINT evidence_holds_order_fk FOREIGN KEY (order_id) REFERENCES runrun_v2.v2_orders(id);
ALTER TABLE runrun_v2.evidence_deletion_audits
  ADD CONSTRAINT evidence_deletion_order_fk FOREIGN KEY (order_id) REFERENCES runrun_v2.v2_orders(id);
CREATE INDEX evidence_holds_order_idx ON runrun_v2.evidence_holds(order_id,released_at);
CREATE INDEX evidence_deletion_resource_idx ON runrun_v2.evidence_deletion_audits(resource_type,resource_id,created_at);
COMMIT;
