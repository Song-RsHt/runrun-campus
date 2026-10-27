-- M01 rollback. Run only after later migrations are absent and after checking the
-- target is the isolated runrun_v2 schema. RESTRICT is intentional.
BEGIN;
DO $$
DECLARE object_count integer;
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'runrun_v2') THEN
    RAISE EXCEPTION 'M01 rollback requires runrun_v2';
  END IF;
  SELECT count(*) INTO object_count
  FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = 'runrun_v2'
    AND c.relkind IN ('r','p','v','m','f')
    AND c.relname NOT IN ('app_users','wechat_identities','user_sessions','courier_certifications','courier_certification_events','admin_audit_logs','idempotency_requests','notification_outbox');
  IF object_count <> 0 THEN
    RAISE EXCEPTION 'Refusing M01 rollback: later runrun_v2 objects exist (%)', object_count;
  END IF;
END
$$;
DROP TABLE IF EXISTS runrun_v2.notification_outbox;
DROP TABLE IF EXISTS runrun_v2.idempotency_requests;
DROP TABLE IF EXISTS runrun_v2.admin_audit_logs;
DROP TABLE IF EXISTS runrun_v2.courier_certification_events;
DROP TABLE IF EXISTS runrun_v2.courier_certifications;
DROP TABLE IF EXISTS runrun_v2.user_sessions;
DROP TABLE IF EXISTS runrun_v2.wechat_identities;
DROP TABLE IF EXISTS runrun_v2.app_users;
COMMIT;
