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
  IF object_count <> 0 OR EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'runrun_v2' AND c.relkind = 'S'
      AND NOT EXISTS (
        SELECT 1 FROM pg_depend d JOIN pg_class owner_table ON owner_table.oid = d.refobjid
        WHERE d.objid = c.oid AND d.classid = 'pg_class'::regclass
          AND d.refclassid = 'pg_class'::regclass AND d.deptype IN ('a','i')
          AND owner_table.relnamespace = n.oid
          AND owner_table.relname IN ('app_users','wechat_identities','user_sessions','courier_certifications','courier_certification_events','admin_audit_logs','idempotency_requests','notification_outbox')
      )
  ) OR EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'runrun_v2' AND c.relkind IN ('i','I')
      AND NOT EXISTS (
        SELECT 1 FROM pg_index i JOIN pg_class owner_table ON owner_table.oid = i.indrelid
        -- A later module can index an M01 table. Table ownership alone therefore
        -- cannot authorize dropping that index with the table (M01 R3 review).
        JOIN (VALUES
          ('app_users', 'app_users_pkey'),
          ('app_users', 'app_users_phone_uq'),
          ('wechat_identities', 'wechat_identities_pkey'),
          ('wechat_identities', 'wechat_identities_app_openid_uq'),
          ('wechat_identities', 'wechat_identities_app_user_uq'),
          ('wechat_identities', 'wechat_identities_user_idx'),
          ('user_sessions', 'user_sessions_pkey'),
          ('user_sessions', 'user_sessions_token_hash_key'),
          ('user_sessions', 'user_sessions_user_active_idx'),
          ('courier_certifications', 'courier_certifications_pkey'),
          ('courier_certifications', 'courier_certifications_courier_uq'),
          ('courier_certifications', 'courier_certifications_submitted_resource_uq'),
          ('courier_certifications', 'courier_certifications_approved_resource_uq'),
          ('courier_certifications', 'courier_certifications_status_idx'),
          ('courier_certification_events', 'courier_certification_events_pkey'),
          ('courier_certification_events', 'certification_events_request_uq'),
          ('courier_certification_events', 'certification_events_cert_created_idx'),
          ('courier_certification_events', 'certification_events_actor_idx'),
          ('admin_audit_logs', 'admin_audit_logs_pkey'),
          ('admin_audit_logs', 'admin_audit_request_uq'),
          ('admin_audit_logs', 'admin_audit_administrator_idx'),
          ('admin_audit_logs', 'admin_audit_target_idx'),
          ('idempotency_requests', 'idempotency_requests_pkey'),
          ('idempotency_requests', 'idempotency_requests_uq'),
          ('idempotency_requests', 'idempotency_requests_resource_idx'),
          ('notification_outbox', 'notification_outbox_pkey'),
          ('notification_outbox', 'notification_outbox_event_uq'),
          ('notification_outbox', 'notification_outbox_recipient_idx'),
          ('notification_outbox', 'notification_outbox_pending_idx')
        ) AS m01_index(table_name, index_name)
          ON owner_table.relname = m01_index.table_name AND c.relname = m01_index.index_name
        WHERE i.indexrelid = c.oid AND owner_table.relnamespace = n.oid
      )
  ) OR EXISTS (
    SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = 'runrun_v2'
  ) OR EXISTS (
    SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace
    WHERE n.nspname = 'runrun_v2' AND (t.typtype IN ('d','e') OR (t.typtype = 'c' AND t.typrelid <> 0 AND NOT EXISTS (SELECT 1 FROM pg_class owner_table WHERE owner_table.oid = t.typrelid AND owner_table.relname IN ('app_users','wechat_identities','user_sessions','courier_certifications','courier_certification_events','admin_audit_logs','idempotency_requests','notification_outbox'))))
  ) THEN
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
