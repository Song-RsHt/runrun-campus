BEGIN;
DO $$ BEGIN
 IF EXISTS (SELECT 1 FROM runrun_v2.payout_registrations)
    OR EXISTS (SELECT 1 FROM runrun_v2.settlement_entries)
    OR EXISTS (SELECT 1 FROM runrun_v2.settlement_freezes)
    OR EXISTS (SELECT 1 FROM runrun_v2.freeze_events)
    OR EXISTS (SELECT 1 FROM runrun_v2.rider_allocations)
    OR EXISTS (SELECT 1 FROM runrun_v2.rider_allocation_lines)
 THEN RAISE EXCEPTION 'M06 rollback refused: financial evidence exists'; END IF;
 IF EXISTS (
   SELECT 1 FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='runrun_v2' AND c.relname IN ('after_sales_cases','settlement_entries','rider_allocations','rider_allocation_lines','settlement_freezes','freeze_events','payout_registrations') AND NOT t.tgisinternal
     AND t.tgname <> 'after_sales_default_settlement_freeze'
 ) THEN RAISE EXCEPTION 'M06 rollback refused: later trigger exists'; END IF;
 IF EXISTS (
   SELECT 1 FROM pg_class i JOIN pg_index x ON x.indexrelid=i.oid JOIN pg_class t ON t.oid=x.indrelid JOIN pg_namespace n ON n.oid=t.relnamespace
   WHERE n.nspname='runrun_v2' AND t.relname IN ('settlement_entries','rider_allocations','rider_allocation_lines','settlement_freezes','freeze_events','payout_registrations')
     AND NOT EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conindid=i.oid)
     AND i.relname NOT IN ('settlement_entries_order_beneficiary','rider_allocations_current','settlement_freezes_active_source','settlement_freezes_order_active','freeze_events_lookup','payout_registrations_order_beneficiary')
 ) THEN RAISE EXCEPTION 'M06 rollback refused: later index exists'; END IF;
 IF EXISTS (
   SELECT 1 FROM pg_constraint k JOIN pg_class t ON t.oid=k.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
   WHERE n.nspname='runrun_v2' AND t.relname IN ('settlement_entries','rider_allocations','rider_allocation_lines','settlement_freezes','freeze_events','payout_registrations')
     AND k.conname NOT IN (
       'settlement_beneficiary_shape','settlement_entries_beneficiary_id_fkey','settlement_entries_beneficiary_type_check','settlement_entries_delta_cents_check','settlement_entries_kind_check','settlement_entries_order_id_fkey','settlement_entries_pkey','settlement_entries_reason_check','settlement_entries_source_type_check','settlement_entries_source_type_source_id_beneficiary_type_b_key','settlement_entries_task_id_fkey',
       'rider_allocations_allocation_version_check','rider_allocations_approved_by_fkey','rider_allocations_order_id_allocation_version_key','rider_allocations_order_id_fkey','rider_allocations_pkey','rider_allocations_reason_check','rider_allocations_status_check',
       'rider_allocation_lines_allocation_id_fkey','rider_allocation_lines_allocation_id_task_id_courier_id_key','rider_allocation_lines_amount_cents_check','rider_allocation_lines_courier_id_fkey','rider_allocation_lines_pkey','rider_allocation_lines_task_id_fkey',
       'settlement_freezes_beneficiary_id_fkey','settlement_freezes_beneficiary_type_check','settlement_freezes_check','settlement_freezes_frozen_cents_check','settlement_freezes_order_id_fkey','settlement_freezes_pkey','settlement_freezes_reason_check',
       'freeze_events_actor_id_fkey','freeze_events_event_type_check','freeze_events_freeze_id_fkey','freeze_events_new_cents_check','freeze_events_pkey','freeze_events_previous_cents_check','freeze_events_reason_check',
       'payout_registrations_amount_cents_check','payout_registrations_beneficiary_id_fkey','payout_registrations_beneficiary_type_check','payout_registrations_check','payout_registrations_mode_check','payout_registrations_order_id_fkey','payout_registrations_pkey','payout_registrations_reason_check','payout_registrations_reference_key','payout_registrations_registered_by_fkey'
     )
 ) THEN RAISE EXCEPTION 'M06 rollback refused: later constraint exists'; END IF;
 IF EXISTS (
   SELECT 1 FROM pg_depend d JOIN pg_proc p ON p.oid=d.objid JOIN pg_namespace pn ON pn.oid=p.pronamespace
   JOIN pg_class r ON r.oid=d.refobjid JOIN pg_namespace rn ON rn.oid=r.relnamespace
   WHERE pn.nspname='runrun_v2' AND rn.nspname='runrun_v2'
     AND r.relname IN ('settlement_entries','rider_allocations','rider_allocation_lines','settlement_freezes','freeze_events','payout_registrations')
     AND p.proname NOT IN ('record_split','record_arrival_subsidy','record_settlement_adjustment','approve_rider_allocation','freeze_after_sales','m06_after_sales_freeze_trigger','register_payout','freeze_settlement','release_settlement_freeze')
 ) THEN RAISE EXCEPTION 'M06 rollback refused: later function exists'; END IF;
END $$;
DROP FUNCTION runrun_v2.release_settlement_freeze(BIGINT,TEXT,BIGINT,BOOLEAN);
DROP TRIGGER after_sales_default_settlement_freeze ON runrun_v2.after_sales_cases;
DROP FUNCTION runrun_v2.m06_after_sales_freeze_trigger();
DROP FUNCTION runrun_v2.freeze_after_sales(BIGINT,BIGINT,BIGINT,TEXT,BOOLEAN);
DROP FUNCTION runrun_v2.freeze_settlement(BIGINT,TEXT,BIGINT,TEXT,TEXT,BIGINT,TEXT,BIGINT,BOOLEAN);
DROP FUNCTION runrun_v2.register_payout(BIGINT,TEXT,BIGINT,BIGINT,TEXT,TEXT,BIGINT,BOOLEAN);
DROP FUNCTION runrun_v2.record_arrival_subsidy(BIGINT,BIGINT,BIGINT,TEXT,BOOLEAN);
DROP FUNCTION runrun_v2.record_settlement_adjustment(BIGINT,TEXT,TEXT,BIGINT,BIGINT,BIGINT,BIGINT,TEXT,BOOLEAN);
DROP FUNCTION runrun_v2.approve_rider_allocation(BIGINT,JSONB,BIGINT,TEXT,BOOLEAN);
DROP FUNCTION runrun_v2.record_split(BIGINT,TEXT,TEXT,BIGINT,BIGINT,BIGINT,TEXT);
DROP TABLE runrun_v2.payout_registrations;
DROP TABLE runrun_v2.freeze_events;
DROP TABLE runrun_v2.settlement_freezes;
DROP TABLE runrun_v2.rider_allocation_lines;
DROP TABLE runrun_v2.rider_allocations;
DROP TABLE runrun_v2.settlement_entries;
COMMIT;
