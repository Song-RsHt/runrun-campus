BEGIN;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM runrun_v2.m05_order_events) OR EXISTS (SELECT 1 FROM runrun_v2.order_adjustments WHERE status IN ('PENDING_PAYMENT','PENDING_REFUND','COMPLETED')) OR EXISTS (SELECT 1 FROM runrun_v2.after_sales_cases WHERE status<>'RESOLVED') THEN RAISE EXCEPTION 'M05 rollback refused: business evidence or unresolved records exist'; END IF;
 IF EXISTS (SELECT 1 FROM runrun_v2.fulfillment_completion_blocks WHERE source_type IN ('ADJUSTMENT','AFTER_SALES')) THEN RAISE EXCEPTION 'M05 rollback refused: completion blocks exist'; END IF;
END $$;
DROP FUNCTION runrun_v2.proof_access_allowed(BIGINT,TEXT);
DROP FUNCTION runrun_v2.complete_order(BIGINT,BIGINT,TEXT);
DROP FUNCTION runrun_v2.resolve_after_sales(BIGINT,BIGINT,JSONB);
DROP FUNCTION runrun_v2.open_after_sales(BIGINT,BIGINT,TEXT,TEXT,BIGINT);
DROP FUNCTION runrun_v2.resolve_adjustment(BIGINT,BOOLEAN);
DROP FUNCTION runrun_v2.decide_adjustment(BIGINT,BIGINT,TEXT,TEXT);
DROP FUNCTION runrun_v2.request_adjustment(BIGINT,BIGINT,TEXT,BIGINT,TEXT,BIGINT);
DROP TABLE runrun_v2.m05_order_events;
DROP TABLE runrun_v2.debts;
DROP TABLE runrun_v2.after_sales_cases;
DROP INDEX runrun_v2.order_adjustments_one_open;
DROP TABLE runrun_v2.order_adjustments;
COMMIT;
