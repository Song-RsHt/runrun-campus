-- Explicitly refuses to discard runtime credentials or audit evidence.
BEGIN;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM runrun_v2.identity_tickets)
 OR EXISTS (SELECT 1 FROM runrun_v2.identity_audit_events)
 OR EXISTS (SELECT 1 FROM runrun_v2.app_users WHERE controlled_identity)
 OR EXISTS (SELECT 1 FROM runrun_v2.admin_audit_logs WHERE action IN ('V2_ADMIN_CREATED','V2_DEPLOYER_CREATED')) THEN
  RAISE EXCEPTION 'M01 runtime rollback requires preserved/exported evidence and empty runtime objects';
 END IF;
END $$;
DROP TABLE runrun_v2.identity_audit_events;
DROP TABLE runrun_v2.identity_tickets;
ALTER TABLE runrun_v2.app_users DROP CONSTRAINT app_users_admin_enabled_ck;
ALTER TABLE runrun_v2.app_users DROP CONSTRAINT app_users_controlled_ck;
ALTER TABLE runrun_v2.app_users DROP COLUMN controlled_identity;
ALTER TABLE runrun_v2.admin_audit_logs DROP CONSTRAINT admin_audit_action_ck;
ALTER TABLE runrun_v2.admin_audit_logs ADD CONSTRAINT admin_audit_action_ck CHECK (action IN (
 'V2_ADMIN_ACCESS_CHANGED','COURIER_CERTIFICATION_SUBMITTED','COURIER_CERTIFICATION_PHOTO_UPLOADED',
 'COURIER_CERTIFICATION_PHOTO_READ','COURIER_CERTIFICATION_APPROVED','COURIER_CERTIFICATION_REJECTED'));
COMMIT;
