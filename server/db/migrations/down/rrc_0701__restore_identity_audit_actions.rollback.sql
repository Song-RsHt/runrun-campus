-- Retain audit evidence: never narrow the whitelist while repaired actions have records.
BEGIN;
CREATE TEMP TABLE m09_expected_audit (action TEXT CHECK (action IN (
 'V2_ADMIN_ACCESS_CHANGED','COURIER_CERTIFICATION_SUBMITTED','COURIER_CERTIFICATION_PHOTO_UPLOADED',
 'COURIER_CERTIFICATION_PHOTO_READ','COURIER_CERTIFICATION_APPROVED','COURIER_CERTIFICATION_REJECTED',
 'EVIDENCE_HOLD_CREATED','EVIDENCE_HOLD_RELEASED',
 'V2_ADMIN_CREATED','V2_DEPLOYER_CREATED','SERVICE_CONFIG_CHANGED'))) ON COMMIT DROP;
DO $$
DECLARE actual TEXT; expected TEXT;
BEGIN
 SELECT pg_get_constraintdef(oid) INTO actual FROM pg_constraint
 WHERE conrelid='runrun_v2.admin_audit_logs'::regclass AND conname='admin_audit_action_m09_ck';
 SELECT pg_get_constraintdef(oid) INTO expected FROM pg_constraint
 WHERE conrelid='m09_expected_audit'::regclass AND contype='c';
 IF actual IS DISTINCT FROM expected OR EXISTS (
   SELECT 1 FROM pg_constraint WHERE conrelid='runrun_v2.admin_audit_logs'::regclass
   AND conname='admin_audit_action_ck') THEN
   RAISE EXCEPTION 'M09 audit rollback refuses missing or later whitelist changes';
 END IF;
 IF EXISTS (SELECT 1 FROM runrun_v2.admin_audit_logs
            WHERE action IN ('V2_ADMIN_CREATED','V2_DEPLOYER_CREATED','SERVICE_CONFIG_CHANGED')) THEN
   RAISE EXCEPTION 'M09 audit rollback refused: retained repaired-action audit records exist';
 END IF;
END $$;
ALTER TABLE runrun_v2.admin_audit_logs DROP CONSTRAINT admin_audit_action_m09_ck;
ALTER TABLE runrun_v2.admin_audit_logs ADD CONSTRAINT admin_audit_action_ck CHECK (action IN (
 'V2_ADMIN_ACCESS_CHANGED','COURIER_CERTIFICATION_SUBMITTED','COURIER_CERTIFICATION_PHOTO_UPLOADED',
 'COURIER_CERTIFICATION_PHOTO_READ','COURIER_CERTIFICATION_APPROVED','COURIER_CERTIFICATION_REJECTED',
 'EVIDENCE_HOLD_CREATED','EVIDENCE_HOLD_RELEASED'));
COMMIT;
