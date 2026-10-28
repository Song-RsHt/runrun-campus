BEGIN;
ALTER TABLE runrun_v2.evidence_holds
  ADD CONSTRAINT evidence_holds_order_fk FOREIGN KEY (order_id) REFERENCES runrun_v2.v2_orders(id);
ALTER TABLE runrun_v2.evidence_deletion_audits
  ADD CONSTRAINT evidence_deletion_order_fk FOREIGN KEY (order_id) REFERENCES runrun_v2.v2_orders(id);
CREATE INDEX evidence_holds_order_idx ON runrun_v2.evidence_holds(order_id,released_at);
CREATE INDEX evidence_deletion_resource_idx ON runrun_v2.evidence_deletion_audits(resource_type,resource_id,created_at);
COMMIT;
