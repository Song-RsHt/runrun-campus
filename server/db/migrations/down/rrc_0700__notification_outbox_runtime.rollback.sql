BEGIN;
DO $$
DECLARE n BIGINT;
BEGIN
  IF to_regprocedure('runrun_v2.claim_notification_outbox(text,integer)') IS NULL THEN
    RAISE EXCEPTION 'M08 claim function is absent';
  END IF;
  SELECT count(*) INTO n FROM runrun_v2.notification_outbox
   WHERE claimed_at IS NOT NULL OR claimed_by IS NOT NULL OR sent_at IS NOT NULL OR last_error_code IS NOT NULL;
  IF n <> 0 THEN RAISE EXCEPTION 'Refusing M08 rollback: outbox runtime data exists'; END IF;
END $$;
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'rrc_notification_worker') THEN
    EXECUTE 'REVOKE ALL ON TABLE runrun_v2.notification_outbox FROM rrc_notification_worker';
  END IF;
END;
$$;
DROP FUNCTION runrun_v2.claim_notification_outbox(TEXT, INTEGER);
DROP INDEX runrun_v2.notification_outbox_claim_idx;
ALTER TABLE runrun_v2.notification_outbox
  DROP COLUMN last_error_code,
  DROP COLUMN sent_at,
  DROP COLUMN claimed_by,
  DROP COLUMN claimed_at;
COMMIT;
