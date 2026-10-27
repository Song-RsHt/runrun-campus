-- M03: isolated payment runtime, after M02 rrc_0100. No public/legacy access.
BEGIN;
-- Approval projection is written only by a trusted in-transaction adjustment service,
-- never by a client amount. M05 owns review/evidence, not this financial projection.
CREATE TABLE runrun_v2.payment_adjustments (
 id BIGINT PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES runrun_v2.v2_orders(id),
 delta_cents BIGINT NOT NULL CHECK(delta_cents<>0 AND delta_cents<>('-9223372036854775808')::bigint),
 status TEXT NOT NULL CHECK(status IN ('PENDING_SUPPLEMENT','PENDING_REFUND','COMPLETED','CANCELLED','TRANSFERRED_TO_DEBT')),
 approved_by BIGINT NOT NULL REFERENCES runrun_v2.app_users(id), reason TEXT NOT NULL CHECK(length(trim(reason))>0),
 approved_at TIMESTAMPTZ NOT NULL, resolved_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 0,
 UNIQUE(id,order_id)
);
CREATE UNIQUE INDEX payment_adjustments_open_uq ON runrun_v2.payment_adjustments(order_id) WHERE status IN ('PENDING_SUPPLEMENT','PENDING_REFUND');
CREATE TABLE runrun_v2.payment_orders (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 order_id BIGINT NOT NULL REFERENCES runrun_v2.v2_orders(id), adjustment_id BIGINT,
 purpose TEXT NOT NULL CHECK(purpose IN ('ORIGINAL','SUPPLEMENT')),
 amount_cents BIGINT NOT NULL CHECK(amount_cents>0),
 status TEXT NOT NULL DEFAULT 'UNPAID' CHECK(status IN ('UNPAID','PROCESSING','UNKNOWN','FAILED','PAID','CLOSED')),
 deadline TIMESTAMPTZ, paid_at TIMESTAMPTZ, credited_attempt_id BIGINT,
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK((purpose='ORIGINAL' AND adjustment_id IS NULL AND deadline IS NOT NULL) OR (purpose='SUPPLEMENT' AND adjustment_id IS NOT NULL)),
 FOREIGN KEY(adjustment_id,order_id) REFERENCES runrun_v2.payment_adjustments(id,order_id), UNIQUE(id,order_id)
);
CREATE UNIQUE INDEX payment_original_uq ON runrun_v2.payment_orders(order_id) WHERE purpose='ORIGINAL';
CREATE UNIQUE INDEX payment_supplement_uq ON runrun_v2.payment_orders(adjustment_id) WHERE purpose='SUPPLEMENT';
CREATE FUNCTION runrun_v2.guard_payment_admission() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.status IN ('PENDING_ASSIGNMENT','ACCEPTED','PICKED_UP','DELIVERED_PENDING_CONFIRMATION','COMPLETED')
 AND NOT EXISTS(SELECT 1 FROM runrun_v2.payment_orders WHERE order_id=NEW.id AND purpose='ORIGINAL' AND status='PAID') THEN
  RAISE EXCEPTION 'paid original required for fulfillment';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER v2_orders_payment_admission BEFORE INSERT OR UPDATE ON runrun_v2.v2_orders FOR EACH ROW EXECUTE FUNCTION runrun_v2.guard_payment_admission();
CREATE TABLE runrun_v2.payment_attempts (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, payment_order_id BIGINT NOT NULL REFERENCES runrun_v2.payment_orders(id),
 attempt_no INTEGER NOT NULL CHECK(attempt_no>0), status TEXT NOT NULL CHECK(status IN ('PROCESSING','UNKNOWN','FAILED','SUCCESS')),
 channel_reference TEXT NOT NULL UNIQUE, result_received_at TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(payment_order_id,attempt_no), UNIQUE(id,payment_order_id)
);
CREATE UNIQUE INDEX payment_attempt_active_uq ON runrun_v2.payment_attempts(payment_order_id) WHERE status IN ('PROCESSING','UNKNOWN');
ALTER TABLE runrun_v2.payment_orders ADD CONSTRAINT payment_credited_attempt_fk FOREIGN KEY(credited_attempt_id,id) REFERENCES runrun_v2.payment_attempts(id,payment_order_id);
CREATE TABLE runrun_v2.refund_orders (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 order_id BIGINT NOT NULL, payment_order_id BIGINT NOT NULL, source_attempt_id BIGINT NOT NULL,
 adjustment_id BIGINT, amount_cents BIGINT NOT NULL CHECK(amount_cents>0),
 purpose TEXT NOT NULL CHECK(purpose IN ('CANCELLATION','DOWNGRADE','LATE_PAYMENT','EXTRA_RECEIPT')),
 status TEXT NOT NULL DEFAULT 'REQUESTED' CHECK(status IN ('REQUESTED','PROCESSING','UNKNOWN','FAILED','SUCCEEDED')),
 reason TEXT NOT NULL CHECK(length(trim(reason))>0), business_key TEXT NOT NULL UNIQUE,
 channel_reference TEXT NOT NULL UNIQUE, succeeded_at TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 FOREIGN KEY(payment_order_id,order_id) REFERENCES runrun_v2.payment_orders(id,order_id),
 FOREIGN KEY(source_attempt_id,payment_order_id) REFERENCES runrun_v2.payment_attempts(id,payment_order_id),
 FOREIGN KEY(adjustment_id,order_id) REFERENCES runrun_v2.payment_adjustments(id,order_id)
);
CREATE INDEX refund_balance_idx ON runrun_v2.refund_orders(source_attempt_id,status);
-- Persistent simulator channel state survives process restarts and lost callbacks.
CREATE TABLE runrun_v2.mock_channel_results (
 resource_type TEXT NOT NULL CHECK(resource_type IN ('PAYMENT','REFUND')), resource_id BIGINT NOT NULL,
 result TEXT NOT NULL CHECK(result IN ('SUCCESS','PROCESSING','FAILED','UNKNOWN')),
 version BIGINT NOT NULL DEFAULT 0, PRIMARY KEY(resource_type,resource_id)
);
CREATE TABLE runrun_v2.payment_result_events (
 event_id TEXT PRIMARY KEY, resource_type TEXT NOT NULL, resource_id BIGINT NOT NULL,
 result TEXT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Append-only accounting integration events: M06 consumes these once, rather than
-- guessing success from a client or overwriting original 30/70 snapshots.
CREATE TABLE runrun_v2.payment_financial_events (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, order_id BIGINT NOT NULL REFERENCES runrun_v2.v2_orders(id),
 event_key TEXT NOT NULL UNIQUE, kind TEXT NOT NULL,
 amount_cents BIGINT NOT NULL CHECK(amount_cents>0), platform_cents BIGINT NOT NULL, courier_cents BIGINT NOT NULL,
 CHECK(platform_cents+courier_cents=amount_cents), payload JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE runrun_v2.payment_admin_audit (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, actor_id BIGINT NOT NULL REFERENCES runrun_v2.app_users(id),
 action TEXT NOT NULL, resource_id BIGINT NOT NULL, request_id TEXT NOT NULL, reason TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(actor_id,action,request_id)
);
CREATE FUNCTION runrun_v2.payment_schema_signature(target OID) RETURNS JSONB LANGUAGE SQL STABLE AS $$
 SELECT jsonb_build_object(
  'columns',(SELECT jsonb_agg(jsonb_build_array(a.attname,a.atttypid,a.atttypmod,a.attnotnull,pg_get_expr(d.adbin,d.adrelid)) ORDER BY a.attnum)
    FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum WHERE a.attrelid=target AND a.attnum>0 AND NOT a.attisdropped),
  'constraints',(SELECT jsonb_agg(jsonb_build_array(conname,pg_get_constraintdef(oid)) ORDER BY conname) FROM pg_constraint WHERE conrelid=target),
  'indexes',(SELECT jsonb_agg(pg_get_indexdef(indexrelid) ORDER BY indexrelid::regclass::text) FROM pg_index WHERE indrelid=target),
  'triggers',(SELECT jsonb_agg(pg_get_triggerdef(oid) ORDER BY tgname) FROM pg_trigger WHERE tgrelid=target AND NOT tgisinternal))
$$;
-- Rollback fingerprints prevent deleting later modules' attached columns/indexes,
-- which PostgreSQL DROP TABLE RESTRICT alone would silently remove.
CREATE TABLE runrun_v2.payment_schema_manifest(relation_name TEXT PRIMARY KEY, signature JSONB NOT NULL);
INSERT INTO runrun_v2.payment_schema_manifest
 SELECT name,runrun_v2.payment_schema_signature(('runrun_v2.'||name)::regclass) FROM unnest(ARRAY[
 'payment_adjustments','payment_orders','payment_attempts','refund_orders','mock_channel_results','payment_result_events',
 'payment_financial_events','payment_admin_audit','payment_schema_manifest','v2_orders']) AS t(name);
COMMIT;
