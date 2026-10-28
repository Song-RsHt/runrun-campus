BEGIN;
DROP INDEX runrun_v2.evidence_deletion_resource_idx;
DROP INDEX runrun_v2.evidence_holds_order_idx;
ALTER TABLE runrun_v2.evidence_deletion_audits DROP CONSTRAINT evidence_deletion_order_fk;
ALTER TABLE runrun_v2.evidence_holds DROP CONSTRAINT evidence_holds_order_fk;
COMMIT;
