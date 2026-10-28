-- M08 notification runtime. M01 owns notification_outbox; this migration
-- extends it for a durable claim/send worker and does not recreate the table.
BEGIN;
DO $$
BEGIN
  IF to_regclass('runrun_v2.notification_outbox') IS NULL
     OR to_regclass('runrun_v2.user_notifications') IS NULL
  THEN RAISE EXCEPTION 'M08 requires M01 notification_outbox and M07 user_notifications'; END IF;
END $$;

ALTER TABLE runrun_v2.notification_outbox
  ADD COLUMN claimed_at TIMESTAMPTZ,
  ADD COLUMN claimed_by TEXT,
  ADD COLUMN sent_at TIMESTAMPTZ,
  ADD COLUMN last_error_code TEXT;

CREATE INDEX notification_outbox_claim_idx
  ON runrun_v2.notification_outbox(status, next_attempt_at, claimed_at, created_at, id);

CREATE FUNCTION runrun_v2.claim_notification_outbox(p_worker TEXT, p_limit INTEGER DEFAULT 50)
RETURNS SETOF runrun_v2.notification_outbox
LANGUAGE plpgsql
AS $$
BEGIN
  IF p_worker IS NULL OR btrim(p_worker) = '' OR p_limit IS NULL OR p_limit < 1 THEN
    RAISE EXCEPTION 'NOTIFICATION_CLAIM_ARGUMENT_INVALID';
  END IF;
  RETURN QUERY
  WITH picked AS (
    SELECT id
    FROM runrun_v2.notification_outbox
    WHERE status IN ('PENDING','FAILED')
      AND (next_attempt_at IS NULL OR next_attempt_at <= now())
      AND claimed_at IS NULL
    ORDER BY created_at, id
    FOR UPDATE SKIP LOCKED
    LIMIT p_limit
  )
  UPDATE runrun_v2.notification_outbox o
     SET status='SENDING', claimed_at=now(), claimed_by=p_worker,
         attempt_count=o.attempt_count+1, updated_at=now()
    FROM picked
   WHERE o.id=picked.id
  RETURNING o.*;
END;
$$;
COMMIT;
