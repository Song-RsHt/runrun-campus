-- Only untouched seed configuration can be rolled back; never discard orders/audit.
BEGIN;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM runrun_v2.v2_orders) OR EXISTS(SELECT 1 FROM runrun_v2.v2_order_events)
 OR EXISTS(SELECT 1 FROM runrun_v2.service_points)
 OR EXISTS(SELECT 1 FROM runrun_v2.platform_settings WHERE config_version<>0 OR version<>0 OR new_order_enabled OR review_owner_id IS NOT NULL)
 OR EXISTS(SELECT 1 FROM runrun_v2.delivery_zones WHERE version<>0 OR config_version<>0 OR NOT enabled
   OR (code,name,package_surcharge_cents) NOT IN (('STANDARD','其他区域',0),('MEIYUAN','梅园',200)))
 OR EXISTS(SELECT 1 FROM runrun_v2.admin_audit_logs WHERE action='SERVICE_CONFIG_CHANGED' OR target_type='SERVICE_CONFIG')
 OR EXISTS(SELECT 1 FROM runrun_v2.idempotency_requests WHERE scope LIKE 'orders:%' OR scope='service-config:patch') THEN
  RAISE EXCEPTION 'M02 rollback refuses business/configuration evidence';
 END IF;
 -- RESTRICT does not protect attached objects owned by a later module: PostgreSQL
 -- considers an index/trigger/constraint/column part of the table being dropped.
 -- Explicitly inspect every M02 relation before any DROP so a later migration's
 -- objects cannot be silently discarded by this rollback.
 IF EXISTS (
   SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
   WHERE n.nspname='runrun_v2' AND c.relkind='i'
     AND c.relname NOT IN (
       'delivery_zones_pkey','delivery_zones_code_key','service_points_pkey','service_points_code_key',
       'platform_settings_pkey','platform_settings_review_owner_idx','v2_orders_pkey','v2_orders_order_no_key',
       'v2_orders_user_created_idx','v2_orders_zone_idx','v2_orders_service_point_idx','v2_orders_pending_idx','v2_orders_confirm_idx',
       'v2_order_events_pkey','v2_order_events_order_id_event_type_request_id_key','v2_order_events_order_idx','v2_order_events_actor_idx','v2_order_events_task_idx'
     )
     AND EXISTS (SELECT 1 FROM pg_index i WHERE i.indexrelid=c.oid AND i.indrelid IN (
       'runrun_v2.delivery_zones'::regclass,'runrun_v2.service_points'::regclass,'runrun_v2.platform_settings'::regclass,
       'runrun_v2.v2_orders'::regclass,'runrun_v2.v2_order_events'::regclass))
 ) OR EXISTS (
   SELECT 1 FROM pg_trigger t
   WHERE t.tgrelid IN ('runrun_v2.delivery_zones'::regclass,'runrun_v2.service_points'::regclass,'runrun_v2.platform_settings'::regclass,
                       'runrun_v2.v2_orders'::regclass,'runrun_v2.v2_order_events'::regclass)
     AND NOT t.tgisinternal AND t.tgname <> 'v2_orders_guard'
 ) OR EXISTS (
   SELECT 1 FROM pg_constraint c
   WHERE c.conrelid IN ('runrun_v2.delivery_zones'::regclass,'runrun_v2.service_points'::regclass,'runrun_v2.platform_settings'::regclass,
                        'runrun_v2.v2_orders'::regclass,'runrun_v2.v2_order_events'::regclass)
     AND c.conname NOT IN (
       'delivery_zones_pkey','delivery_zones_code_key','delivery_zones_package_surcharge_cents_check','delivery_zones_config_version_check','delivery_zones_version_check',
       'service_points_pkey','service_points_code_key','service_points_coordinate_system_check','service_points_config_version_check','service_points_version_check','service_points_check','service_points_check1',
       'platform_settings_pkey','platform_settings_id_check','platform_settings_business_timezone_check','platform_settings_business_open_check','platform_settings_business_close_check','platform_settings_review_owner_id_fkey','platform_settings_pending_payment_minutes_check','platform_settings_delivery_confirm_minutes_check','platform_settings_arrival_radius_m_check','platform_settings_arrival_window_minutes_check','platform_settings_arrival_subsidy_cents_check','platform_settings_adjust_pay_timeout_minutes_check','platform_settings_adjustment_review_remind_minutes_check','platform_settings_adjustment_review_escalate_minutes_check','platform_settings_overdue_debt_closure_days_check','platform_settings_platform_share_percent_check','platform_settings_config_version_check','platform_settings_version_check',
       'v2_orders_pkey','v2_orders_order_no_key','v2_orders_order_flow_version_check','v2_orders_status_check','v2_orders_service_type_check','v2_orders_address_snapshot_check','v2_orders_pricing_snapshot_check','v2_orders_policy_snapshot_check','v2_orders_original_fee_cents_check','v2_orders_effective_fee_cents_check','v2_orders_version_check','v2_orders_user_id_fkey','v2_orders_zone_id_fkey','v2_orders_service_point_id_fkey',
       'v2_order_events_pkey','v2_order_events_payload_check','v2_order_events_order_id_event_type_request_id_key','v2_order_events_order_id_fkey','v2_order_events_actor_id_fkey'
     )
 ) OR EXISTS (
   SELECT 1 FROM information_schema.columns x
   WHERE x.table_schema='runrun_v2' AND x.table_name IN ('delivery_zones','service_points','platform_settings','v2_orders','v2_order_events')
     AND (x.table_name,x.column_name) NOT IN (
       ('delivery_zones','id'),('delivery_zones','code'),('delivery_zones','name'),('delivery_zones','package_surcharge_cents'),('delivery_zones','enabled'),('delivery_zones','config_version'),('delivery_zones','version'),('delivery_zones','created_at'),('delivery_zones','updated_at'),
       ('service_points','id'),('service_points','code'),('service_points','name'),('service_points','latitude'),('service_points','longitude'),('service_points','coordinate_system'),('service_points','enabled'),('service_points','config_version'),('service_points','version'),('service_points','created_at'),('service_points','updated_at'),
       ('platform_settings','id'),('platform_settings','new_order_enabled'),('platform_settings','business_timezone'),('platform_settings','business_open'),('platform_settings','business_close'),('platform_settings','review_owner_id'),('platform_settings','pending_payment_minutes'),('platform_settings','delivery_confirm_minutes'),('platform_settings','arrival_radius_m'),('platform_settings','arrival_window_minutes'),('platform_settings','arrival_subsidy_cents'),('platform_settings','adjust_pay_timeout_minutes'),('platform_settings','adjustment_review_remind_minutes'),('platform_settings','adjustment_review_escalate_minutes'),('platform_settings','overdue_debt_closure_days'),('platform_settings','platform_share_percent'),('platform_settings','config_version'),('platform_settings','version'),('platform_settings','created_at'),('platform_settings','updated_at'),
       ('v2_orders','id'),('v2_orders','order_no'),('v2_orders','order_flow_version'),('v2_orders','user_id'),('v2_orders','status'),('v2_orders','service_type'),('v2_orders','zone_id'),('v2_orders','service_point_id'),('v2_orders','address_snapshot'),('v2_orders','pricing_snapshot'),('v2_orders','policy_snapshot'),('v2_orders','original_fee_cents'),('v2_orders','effective_fee_cents'),('v2_orders','pending_payment_deadline'),('v2_orders','delivered_at'),('v2_orders','confirm_started_at'),('v2_orders','confirm_deadline'),('v2_orders','completed_at'),('v2_orders','cancelled_at'),('v2_orders','version'),('v2_orders','created_at'),('v2_orders','updated_at'),
       ('v2_order_events','id'),('v2_order_events','order_id'),('v2_order_events','task_id'),('v2_order_events','actor_id'),('v2_order_events','event_type'),('v2_order_events','request_id'),('v2_order_events','payload'),('v2_order_events','created_at')
     )
 ) THEN RAISE EXCEPTION 'M02 rollback refuses attached later-module objects';
 END IF;
END $$;
DROP TABLE runrun_v2.v2_order_events;
DROP TABLE runrun_v2.v2_orders;
DROP FUNCTION runrun_v2.guard_order_update();
DROP TABLE runrun_v2.platform_settings;
DROP TABLE runrun_v2.service_points;
DROP TABLE runrun_v2.delivery_zones;
ALTER TABLE runrun_v2.admin_audit_logs DROP CONSTRAINT admin_audit_action_ck;
ALTER TABLE runrun_v2.admin_audit_logs ADD CONSTRAINT admin_audit_action_ck CHECK(action IN (
 'V2_ADMIN_ACCESS_CHANGED','V2_ADMIN_CREATED','V2_DEPLOYER_CREATED',
 'COURIER_CERTIFICATION_SUBMITTED','COURIER_CERTIFICATION_PHOTO_UPLOADED','COURIER_CERTIFICATION_PHOTO_READ','COURIER_CERTIFICATION_APPROVED','COURIER_CERTIFICATION_REJECTED'));
ALTER TABLE runrun_v2.admin_audit_logs DROP CONSTRAINT admin_audit_target_ck;
ALTER TABLE runrun_v2.admin_audit_logs ADD CONSTRAINT admin_audit_target_ck CHECK(target_type IN ('APP_USER','COURIER_CERTIFICATION'));
COMMIT;
