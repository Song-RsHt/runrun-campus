-- Only pristine M03 may be removed. Evidence-bearing installations require restore
-- or a forward repair; never delete payment/refund records to make rollback pass.
BEGIN;
DO $$ DECLARE item RECORD; has_rows BOOLEAN; BEGIN
 FOR item IN SELECT * FROM runrun_v2.payment_schema_manifest LOOP
  IF runrun_v2.payment_schema_signature(('runrun_v2.'||item.relation_name)::regclass) IS DISTINCT FROM item.signature THEN
   RAISE EXCEPTION 'M03 rollback refuses changed or later-module attached objects';
  END IF;
  IF item.relation_name NOT IN ('v2_orders','payment_schema_manifest') THEN
   EXECUTE format('SELECT EXISTS(SELECT 1 FROM runrun_v2.%I)',item.relation_name) INTO has_rows;
   IF has_rows THEN RAISE EXCEPTION 'M03 rollback refuses financial evidence'; END IF;
  END IF;
 END LOOP;
 IF EXISTS(SELECT 1 FROM runrun_v2.idempotency_requests WHERE scope LIKE 'payments:%' OR scope LIKE 'refunds:%') THEN
  RAISE EXCEPTION 'M03 rollback refuses idempotency evidence';
 END IF;
END $$;
DROP TRIGGER v2_orders_payment_admission ON runrun_v2.v2_orders;
DROP FUNCTION runrun_v2.guard_payment_admission();
DROP TABLE runrun_v2.refund_orders;
ALTER TABLE runrun_v2.payment_orders DROP CONSTRAINT payment_credited_attempt_fk;
DROP TABLE runrun_v2.payment_attempts;
DROP TABLE runrun_v2.payment_orders;
DROP TABLE runrun_v2.payment_adjustments;
DROP TABLE runrun_v2.mock_channel_results;
DROP TABLE runrun_v2.payment_result_events;
DROP TABLE runrun_v2.payment_financial_events;
DROP TABLE runrun_v2.payment_admin_audit;
DROP TABLE runrun_v2.payment_schema_manifest;
DROP FUNCTION runrun_v2.payment_schema_signature(OID);
COMMIT;
