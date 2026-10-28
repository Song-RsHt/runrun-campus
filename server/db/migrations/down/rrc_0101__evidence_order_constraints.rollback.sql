BEGIN;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM runrun_v2.admin_audit_logs WHERE action IN ('EVIDENCE_HOLD_CREATED','EVIDENCE_HOLD_RELEASED') OR target_type='EVIDENCE_HOLD') THEN
   RAISE EXCEPTION 'M07 constraint rollback refuses evidence hold audit';
 END IF;
END $$;
ALTER TABLE runrun_v2.admin_audit_logs DROP CONSTRAINT admin_audit_action_ck;
ALTER TABLE runrun_v2.admin_audit_logs ADD CONSTRAINT admin_audit_action_ck CHECK (action IN ('V2_ADMIN_ACCESS_CHANGED','V2_ADMIN_CREATED','V2_DEPLOYER_CREATED','SERVICE_CONFIG_CHANGED','COURIER_CERTIFICATION_SUBMITTED','COURIER_CERTIFICATION_PHOTO_UPLOADED','COURIER_CERTIFICATION_PHOTO_READ','COURIER_CERTIFICATION_APPROVED','COURIER_CERTIFICATION_REJECTED'));
ALTER TABLE runrun_v2.admin_audit_logs DROP CONSTRAINT admin_audit_target_ck;
ALTER TABLE runrun_v2.admin_audit_logs ADD CONSTRAINT admin_audit_target_ck CHECK (target_type IN ('APP_USER','COURIER_CERTIFICATION','SERVICE_CONFIG'));
DROP INDEX runrun_v2.evidence_deletion_resource_idx;
DROP INDEX runrun_v2.evidence_holds_order_idx;
ALTER TABLE runrun_v2.evidence_deletion_audits DROP CONSTRAINT evidence_deletion_order_fk;
ALTER TABLE runrun_v2.evidence_holds DROP CONSTRAINT evidence_holds_order_fk;
COMMIT;
