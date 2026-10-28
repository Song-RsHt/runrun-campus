-- M07 notification read model. notification_outbox is owned by M01 and is
-- intentionally consumed here rather than recreated.
BEGIN;
DO $$
BEGIN
  IF to_regclass('runrun_v2.notification_outbox') IS NULL
     OR to_regclass('runrun_v2.app_users') IS NULL
     OR to_regclass('runrun_v2.v2_orders') IS NULL
  THEN RAISE EXCEPTION 'M07 notifications require M01 and M02 objects'; END IF;
END $$;

CREATE TABLE runrun_v2.user_notifications (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES runrun_v2.app_users(id),
  order_id BIGINT REFERENCES runrun_v2.v2_orders(id),
  type TEXT NOT NULL CHECK (type IN ('DELIVERY','SUPPLEMENT','REFUND','AFTER_SALES','CERTIFICATION','ADJUSTMENT_REVIEW')),
  content JSONB NOT NULL,
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX user_notifications_user_created_idx ON runrun_v2.user_notifications(user_id,created_at,id);
CREATE INDEX user_notifications_order_idx ON runrun_v2.user_notifications(order_id,created_at,id);
COMMIT;
