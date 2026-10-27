BEGIN;
CREATE TABLE runrun_v2.fulfillment_tasks (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 order_id BIGINT NOT NULL REFERENCES runrun_v2.v2_orders(id), courier_id BIGINT NOT NULL REFERENCES runrun_v2.app_users(id),
 status TEXT NOT NULL CHECK(status IN ('OFFERED','ACTIVE','RELEASED','HANDED_OVER','DELIVERED','CANCELLED')),
 source_task_id BIGINT, superseded_at TIMESTAMPTZ,
 accepted_at TIMESTAMPTZ, picked_up_at TIMESTAMPTZ, delivered_at TIMESTAMPTZ, ended_at TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0), created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(id,order_id), UNIQUE(id,order_id,courier_id),
 FOREIGN KEY(source_task_id,order_id) REFERENCES runrun_v2.fulfillment_tasks(id,order_id),
 CHECK((status IN ('RELEASED','HANDED_OVER','DELIVERED','CANCELLED'))=(ended_at IS NOT NULL))
);
CREATE UNIQUE INDEX fulfillment_active_order_uq ON runrun_v2.fulfillment_tasks(order_id) WHERE status='ACTIVE';
CREATE INDEX fulfillment_courier_idx ON runrun_v2.fulfillment_tasks(courier_id,status,created_at DESC,id DESC);
CREATE INDEX fulfillment_source_idx ON runrun_v2.fulfillment_tasks(source_task_id);
ALTER TABLE runrun_v2.v2_order_events ADD CONSTRAINT order_event_task_fk FOREIGN KEY(task_id,order_id) REFERENCES runrun_v2.fulfillment_tasks(id,order_id);
CREATE TABLE runrun_v2.v2_order_proofs (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, order_id BIGINT NOT NULL, task_id BIGINT NOT NULL, uploaded_by BIGINT NOT NULL,
 kind TEXT NOT NULL CHECK(kind IN ('PICKUP','SPECIFICATION','DELIVERY')), storage_key TEXT NOT NULL UNIQUE,
 storage_status TEXT NOT NULL CHECK(storage_status IN ('UPLOADED','AVAILABLE','DELETING','DELETED')),
 delete_after TIMESTAMPTZ, deleted_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 UNIQUE(id,task_id,order_id), FOREIGN KEY(task_id,order_id,uploaded_by) REFERENCES runrun_v2.fulfillment_tasks(id,order_id,courier_id)
);
CREATE INDEX fulfillment_proof_idx ON runrun_v2.v2_order_proofs(order_id,task_id,kind);
CREATE INDEX fulfillment_proof_delete_idx ON runrun_v2.v2_order_proofs(delete_after);
CREATE UNIQUE INDEX fulfillment_single_stage_proof_uq ON runrun_v2.v2_order_proofs(task_id,kind) WHERE kind IN ('PICKUP','DELIVERY');
CREATE TABLE runrun_v2.location_events (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY, order_id BIGINT NOT NULL, task_id BIGINT NOT NULL, courier_id BIGINT NOT NULL,
 event_type TEXT NOT NULL CHECK(event_type IN ('ARRIVAL','DELIVERY')), latitude NUMERIC(10,7), longitude NUMERIC(10,7),
 coordinate_system TEXT NOT NULL CHECK(coordinate_system='GCJ02'), received_at TIMESTAMPTZ NOT NULL,
 request_id TEXT NOT NULL, accepted BOOLEAN NOT NULL, decision_reason TEXT,
 delete_after TIMESTAMPTZ, deleted_at TIMESTAMPTZ, UNIQUE(task_id,event_type,request_id), UNIQUE(id,task_id,order_id),
 FOREIGN KEY(task_id,order_id,courier_id) REFERENCES runrun_v2.fulfillment_tasks(id,order_id,courier_id),
 CHECK((deleted_at IS NULL AND latitude IS NOT NULL AND longitude IS NOT NULL AND latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
 OR (deleted_at IS NOT NULL AND latitude IS NULL AND longitude IS NULL))
);
CREATE INDEX fulfillment_location_idx ON runrun_v2.location_events(order_id,event_type,received_at);
CREATE UNIQUE INDEX fulfillment_delivery_location_uq ON runrun_v2.location_events(task_id) WHERE event_type='DELIVERY' AND accepted;
-- This is a completion gate, not an adjustment/after-sales entity. M05 owns those
-- entities and must update this projection in its own order-locked transaction.
CREATE TABLE runrun_v2.fulfillment_completion_blocks (
 order_id BIGINT NOT NULL REFERENCES runrun_v2.v2_orders(id), source_type TEXT NOT NULL CHECK(source_type IN ('ADJUSTMENT','AFTER_SALES')),
 source_id TEXT NOT NULL, PRIMARY KEY(order_id,source_type,source_id)
);
-- Independent cancellation paths (M03) end tasks in the same order transaction;
-- a stale task can never continue holding capacity or writing evidence afterward.
CREATE FUNCTION runrun_v2.fulfillment_order_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.delivered_at IS NOT NULL AND NEW.delivered_at IS DISTINCT FROM OLD.delivered_at THEN RAISE EXCEPTION 'immutable actual delivery'; END IF;
 IF NEW.status='CANCELLED' THEN
  UPDATE runrun_v2.fulfillment_tasks SET status='CANCELLED',ended_at=coalesce(NEW.cancelled_at,now()),version=version+1 WHERE order_id=NEW.id AND status IN ('ACTIVE','OFFERED');
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER fulfillment_order_guard BEFORE UPDATE ON runrun_v2.v2_orders FOR EACH ROW EXECUTE FUNCTION runrun_v2.fulfillment_order_guard();
CREATE TABLE runrun_v2.fulfillment_schema_manifest(relation_name TEXT PRIMARY KEY,signature JSONB NOT NULL);
INSERT INTO runrun_v2.fulfillment_schema_manifest SELECT name,runrun_v2.payment_schema_signature(('runrun_v2.'||name)::regclass)
 FROM unnest(ARRAY['fulfillment_tasks','v2_order_proofs','location_events','fulfillment_completion_blocks','fulfillment_schema_manifest','v2_orders','v2_order_events']) AS t(name);
COMMIT;
