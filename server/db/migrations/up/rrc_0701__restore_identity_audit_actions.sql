-- M09 integration repair: M07 0101 accidentally removed M01/M02 audit actions.
-- Restore only those established actions; keep explicit whitelists and refuse later schema changes.
BEGIN;
CREATE TEMP TABLE m09_expected_audit (action TEXT CHECK (action IN (
 'V2_ADMIN_ACCESS_CHANGED','COURIER_CERTIFICATION_SUBMITTED','COURIER_CERTIFICATION_PHOTO_UPLOADED',
 'COURIER_CERTIFICATION_PHOTO_READ','COURIER_CERTIFICATION_APPROVED','COURIER_CERTIFICATION_REJECTED',
 'EVIDENCE_HOLD_CREATED','EVIDENCE_HOLD_RELEASED'))) ON COMMIT DROP;
DO $$
DECLARE actual TEXT; expected TEXT;
BEGIN
 IF to_regprocedure('runrun_v2.run_notification_review_timers()') IS NULL THEN
   RAISE EXCEPTION 'M09 audit repair requires complete M00-M08 migrations';
 END IF;
 SELECT pg_get_constraintdef(oid) INTO actual FROM pg_constraint
 WHERE conrelid='runrun_v2.admin_audit_logs'::regclass AND conname='admin_audit_action_ck';
 SELECT pg_get_constraintdef(oid) INTO expected FROM pg_constraint
 WHERE conrelid='m09_expected_audit'::regclass AND contype='c';
 IF actual IS DISTINCT FROM expected OR EXISTS (
   SELECT 1 FROM pg_constraint WHERE conrelid='runrun_v2.admin_audit_logs'::regclass
   AND conname='admin_audit_action_m09_ck') THEN
   RAISE EXCEPTION 'M09 audit repair refuses duplicate execution or an unexpected audit whitelist';
 END IF;
END $$;
ALTER TABLE runrun_v2.admin_audit_logs DROP CONSTRAINT admin_audit_action_ck;
ALTER TABLE runrun_v2.admin_audit_logs ADD CONSTRAINT admin_audit_action_m09_ck CHECK (action IN (
 'V2_ADMIN_ACCESS_CHANGED','COURIER_CERTIFICATION_SUBMITTED','COURIER_CERTIFICATION_PHOTO_UPLOADED',
 'COURIER_CERTIFICATION_PHOTO_READ','COURIER_CERTIFICATION_APPROVED','COURIER_CERTIFICATION_REJECTED',
 'EVIDENCE_HOLD_CREATED','EVIDENCE_HOLD_RELEASED',
 'V2_ADMIN_CREATED','V2_DEPLOYER_CREATED','SERVICE_CONFIG_CHANGED'));
COMMIT;
