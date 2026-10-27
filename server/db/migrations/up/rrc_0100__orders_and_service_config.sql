-- M02 owns configuration and orders; payment tables remain M03-owned.
BEGIN;
CREATE TABLE runrun_v2.delivery_zones (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 code TEXT NOT NULL UNIQUE, name TEXT NOT NULL,
 package_surcharge_cents BIGINT NOT NULL CHECK (package_surcharge_cents BETWEEN 0 AND 9223372036854775007),
 enabled BOOLEAN NOT NULL DEFAULT TRUE, config_version BIGINT NOT NULL DEFAULT 0 CHECK(config_version>=0),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO runrun_v2.delivery_zones(code,name,package_surcharge_cents) VALUES ('STANDARD','其他区域',0),('MEIYUAN','梅园',200);
CREATE TABLE runrun_v2.service_points (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 code TEXT NOT NULL UNIQUE, name TEXT NOT NULL,
 latitude NUMERIC(10,7), longitude NUMERIC(10,7), coordinate_system TEXT NOT NULL DEFAULT 'GCJ02' CHECK(coordinate_system='GCJ02'),
 enabled BOOLEAN NOT NULL DEFAULT TRUE, config_version BIGINT NOT NULL DEFAULT 0 CHECK(config_version>=0),
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK ((latitude IS NULL) = (longitude IS NULL)),
 CHECK (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
);
-- Actual pickup locations must be verified by operations; do not seed invented coordinates.
CREATE TABLE runrun_v2.platform_settings (
 id SMALLINT PRIMARY KEY DEFAULT 1 CHECK(id=1),
 new_order_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 business_timezone TEXT NOT NULL DEFAULT 'Asia/Shanghai' CHECK(business_timezone='Asia/Shanghai'),
 business_open TIME NOT NULL DEFAULT '09:00' CHECK(business_open='09:00'),
 business_close TIME NOT NULL DEFAULT '23:00' CHECK(business_close='23:00'),
 review_owner_id BIGINT REFERENCES runrun_v2.app_users(id),
 pending_payment_minutes INTEGER NOT NULL DEFAULT 30 CHECK(pending_payment_minutes=30),
 delivery_confirm_minutes INTEGER NOT NULL DEFAULT 10 CHECK(delivery_confirm_minutes=10),
 arrival_radius_m INTEGER NOT NULL DEFAULT 100 CHECK(arrival_radius_m=100),
 arrival_window_minutes INTEGER NOT NULL DEFAULT 30 CHECK(arrival_window_minutes=30),
 arrival_subsidy_cents BIGINT NOT NULL DEFAULT 100 CHECK(arrival_subsidy_cents=100),
 adjust_pay_timeout_minutes INTEGER NOT NULL DEFAULT 15 CHECK(adjust_pay_timeout_minutes=15),
 adjustment_review_remind_minutes INTEGER NOT NULL DEFAULT 120 CHECK(adjustment_review_remind_minutes=120),
 adjustment_review_escalate_minutes INTEGER NOT NULL DEFAULT 1440 CHECK(adjustment_review_escalate_minutes=1440),
 overdue_debt_closure_days INTEGER NOT NULL DEFAULT 30 CHECK(overdue_debt_closure_days=30),
 platform_share_percent INTEGER NOT NULL DEFAULT 30 CHECK(platform_share_percent=30),
 config_version BIGINT NOT NULL DEFAULT 0 CHECK(config_version>=0), version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO runrun_v2.platform_settings(id) VALUES(1);
CREATE INDEX platform_settings_review_owner_idx ON runrun_v2.platform_settings(review_owner_id);
CREATE TABLE runrun_v2.v2_orders (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 order_no TEXT NOT NULL UNIQUE,
 order_flow_version SMALLINT NOT NULL DEFAULT 2 CHECK(order_flow_version=2),
 user_id BIGINT NOT NULL REFERENCES runrun_v2.app_users(id),
 status TEXT NOT NULL DEFAULT 'PENDING_PAYMENT' CHECK(status IN ('PENDING_PAYMENT','PENDING_ASSIGNMENT','ACCEPTED','PICKED_UP','DELIVERED_PENDING_CONFIRMATION','EXCEPTION','COMPLETED','CANCELLED')),
 service_type TEXT NOT NULL CHECK(service_type IN ('LOBBY','DOOR','EXPRESS','PACKAGE_SMALL','PACKAGE_MEDIUM','PACKAGE_MEDIUM_LARGE','PACKAGE_LARGE','PACKAGE_EXTRA_LARGE','PACKAGE_XL')),
 zone_id BIGINT NOT NULL REFERENCES runrun_v2.delivery_zones(id),
 service_point_id BIGINT NOT NULL REFERENCES runrun_v2.service_points(id),
 address_snapshot JSONB NOT NULL CHECK(jsonb_typeof(address_snapshot)='object'),
 pricing_snapshot JSONB NOT NULL CHECK(jsonb_typeof(pricing_snapshot)='object'),
 policy_snapshot JSONB NOT NULL CHECK(jsonb_typeof(policy_snapshot)='object'),
 original_fee_cents BIGINT NOT NULL CHECK(original_fee_cents>=0),
 effective_fee_cents BIGINT NOT NULL CHECK(effective_fee_cents>=0),
 pending_payment_deadline TIMESTAMPTZ NOT NULL,
 delivered_at TIMESTAMPTZ, confirm_started_at TIMESTAMPTZ, confirm_deadline TIMESTAMPTZ,
 completed_at TIMESTAMPTZ, cancelled_at TIMESTAMPTZ,
 version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX v2_orders_user_created_idx ON runrun_v2.v2_orders(user_id,created_at,id);
CREATE INDEX v2_orders_zone_idx ON runrun_v2.v2_orders(zone_id);
CREATE INDEX v2_orders_service_point_idx ON runrun_v2.v2_orders(service_point_id);
CREATE INDEX v2_orders_pending_idx ON runrun_v2.v2_orders(status,pending_payment_deadline);
CREATE INDEX v2_orders_confirm_idx ON runrun_v2.v2_orders(status,confirm_deadline);
-- Guard every future module's writes as well as this module: fixed flow version,
-- immutable original snapshots, and exactly one concurrency-version increment.
CREATE FUNCTION runrun_v2.guard_order_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='INSERT' THEN
  IF NEW.version<>0 THEN RAISE EXCEPTION 'order version must start at zero'; END IF;
 ELSE
  IF NEW.order_flow_version IS DISTINCT FROM OLD.order_flow_version THEN RAISE EXCEPTION 'immutable order flow version'; END IF;
  IF NEW.version<>OLD.version+1 THEN RAISE EXCEPTION 'order version must increment once'; END IF;
  IF NEW.address_snapshot IS DISTINCT FROM OLD.address_snapshot OR NEW.pricing_snapshot IS DISTINCT FROM OLD.pricing_snapshot
   OR NEW.policy_snapshot IS DISTINCT FROM OLD.policy_snapshot OR NEW.original_fee_cents<>OLD.original_fee_cents
   OR NEW.pending_payment_deadline<>OLD.pending_payment_deadline OR NEW.user_id<>OLD.user_id
   OR NEW.zone_id<>OLD.zone_id OR NEW.service_point_id<>OLD.service_point_id OR NEW.order_no<>OLD.order_no
   OR NEW.created_at<>OLD.created_at THEN RAISE EXCEPTION 'immutable original order facts'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER v2_orders_guard BEFORE INSERT OR UPDATE ON runrun_v2.v2_orders FOR EACH ROW EXECUTE FUNCTION runrun_v2.guard_order_update();
CREATE TABLE runrun_v2.v2_order_events (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 order_id BIGINT NOT NULL REFERENCES runrun_v2.v2_orders(id),
 -- M04 adds the task FK after fulfillment_tasks exists; M02 writes only NULL.
 task_id BIGINT, actor_id BIGINT REFERENCES runrun_v2.app_users(id),
 event_type TEXT NOT NULL, request_id TEXT NOT NULL, payload JSONB NOT NULL CHECK(jsonb_typeof(payload)='object'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(order_id,event_type,request_id)
);
CREATE INDEX v2_order_events_order_idx ON runrun_v2.v2_order_events(order_id,created_at,id);
CREATE INDEX v2_order_events_actor_idx ON runrun_v2.v2_order_events(actor_id);
CREATE INDEX v2_order_events_task_idx ON runrun_v2.v2_order_events(task_id);
ALTER TABLE runrun_v2.admin_audit_logs DROP CONSTRAINT admin_audit_action_ck;
ALTER TABLE runrun_v2.admin_audit_logs ADD CONSTRAINT admin_audit_action_ck CHECK(action IN (
 'V2_ADMIN_ACCESS_CHANGED','V2_ADMIN_CREATED','V2_DEPLOYER_CREATED','SERVICE_CONFIG_CHANGED',
 'COURIER_CERTIFICATION_SUBMITTED','COURIER_CERTIFICATION_PHOTO_UPLOADED','COURIER_CERTIFICATION_PHOTO_READ','COURIER_CERTIFICATION_APPROVED','COURIER_CERTIFICATION_REJECTED'));
ALTER TABLE runrun_v2.admin_audit_logs DROP CONSTRAINT admin_audit_target_ck;
ALTER TABLE runrun_v2.admin_audit_logs ADD CONSTRAINT admin_audit_target_ck CHECK(target_type IN ('APP_USER','COURIER_CERTIFICATION','SERVICE_CONFIG'));
COMMIT;
