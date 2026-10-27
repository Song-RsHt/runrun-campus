BEGIN;
DO $$ DECLARE item RECORD; has_rows BOOLEAN; BEGIN
 FOR item IN SELECT * FROM runrun_v2.fulfillment_schema_manifest LOOP
  IF runrun_v2.payment_schema_signature(('runrun_v2.'||item.relation_name)::regclass) IS DISTINCT FROM item.signature THEN RAISE EXCEPTION 'M04 rollback refuses later module objects'; END IF;
  IF item.relation_name NOT IN ('v2_orders','v2_order_events','fulfillment_schema_manifest') THEN
   EXECUTE format('SELECT EXISTS(SELECT 1 FROM runrun_v2.%I)',item.relation_name) INTO has_rows;
   IF has_rows THEN RAISE EXCEPTION 'M04 rollback refuses fulfillment evidence'; END IF;
  END IF;
 END LOOP;
 IF EXISTS(SELECT 1 FROM runrun_v2.idempotency_requests WHERE scope LIKE 'fulfillment:%') OR EXISTS(SELECT 1 FROM runrun_v2.v2_order_events WHERE event_type LIKE 'FULFILLMENT_%') THEN RAISE EXCEPTION 'M04 rollback refuses event evidence'; END IF;
END $$;
DROP TRIGGER fulfillment_order_guard ON runrun_v2.v2_orders;
DROP FUNCTION runrun_v2.fulfillment_order_guard();
ALTER TABLE runrun_v2.v2_order_events DROP CONSTRAINT order_event_task_fk;
DROP TABLE runrun_v2.fulfillment_completion_blocks;
DROP TABLE runrun_v2.location_events;
DROP TABLE runrun_v2.v2_order_proofs;
DROP TABLE runrun_v2.fulfillment_tasks;
DROP TABLE runrun_v2.fulfillment_schema_manifest;
COMMIT;
