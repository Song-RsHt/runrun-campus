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
