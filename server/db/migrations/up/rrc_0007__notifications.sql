-- M08 reliable in-app notifications and best-effort WeChat subscription delivery.
-- M08 extends M01 outbox; business transactions call enqueue_notification and external delivery is never inline.
BEGIN;
DO $$
BEGIN
  IF to_regclass('runrun_v2.app_users') IS NULL OR to_regclass('runrun_v2.notification_outbox') IS NULL
     OR to_regclass('runrun_v2.v2_orders') IS NULL OR to_regclass('runrun_v2.payment_orders') IS NULL
     OR to_regclass('runrun_v2.refund_orders') IS NULL OR to_regclass('runrun_v2.after_sales_cases') IS NULL
     OR to_regclass('runrun_v2.order_adjustments') IS NULL OR to_regclass('runrun_v2.courier_certifications') IS NULL THEN
    RAISE EXCEPTION 'M08 requires M01 and M02-M07 foundations';
  END IF;
  IF to_regclass('runrun_v2.user_notifications') IS NOT NULL
     OR to_regclass('runrun_v2.notification_templates') IS NOT NULL
     OR to_regclass('runrun_v2.m08_rollback_baseline') IS NOT NULL
     OR to_regclass('runrun_v2.notification_outbox_due_idx') IS NOT NULL
     OR EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema='runrun_v2' AND table_name='notification_outbox'
                 AND column_name IN ('locked_until','sent_at','failed_at'))
     OR EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
               WHERE n.nspname='runrun_v2'
                 AND p.proname IN ('enqueue_notification','claim_notification_outbox','complete_notification_outbox')) THEN
    RAISE EXCEPTION 'M08 already applied; refusing duplicate migration';
  END IF;
END $$;

CREATE TABLE runrun_v2.m08_rollback_baseline (object_name TEXT PRIMARY KEY);
INSERT INTO runrun_v2.m08_rollback_baseline(object_name)
SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
WHERE n.nspname='runrun_v2' AND c.relkind IN ('r','p','v','m','S','f');

CREATE TABLE runrun_v2.user_notifications (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES runrun_v2.app_users(id),
  order_id BIGINT REFERENCES runrun_v2.v2_orders(id),
  type TEXT NOT NULL CHECK (type IN (
    'PAYMENT_SUCCEEDED','DELIVERY_COMPLETED','SUPPLEMENT_REQUIRED','REFUND_UPDATED',
    'AFTER_SALES_UPDATED','REVIEW_REMINDER','REVIEW_ESCALATED','CERTIFICATION_UPDATED')),
  content JSONB NOT NULL CHECK (jsonb_typeof(content) = 'object'),
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX user_notifications_event_uq ON runrun_v2.user_notifications(user_id, type, ((content->>'eventKey')));
CREATE INDEX user_notifications_user_created_idx ON runrun_v2.user_notifications(user_id, created_at DESC, id DESC);
CREATE INDEX user_notifications_order_idx ON runrun_v2.user_notifications(order_id, created_at DESC, id DESC);
ALTER TABLE runrun_v2.notification_outbox
  ADD COLUMN locked_until TIMESTAMPTZ,
  ADD COLUMN sent_at TIMESTAMPTZ,
  ADD COLUMN failed_at TIMESTAMPTZ;
CREATE INDEX notification_outbox_due_idx ON runrun_v2.notification_outbox(status, next_attempt_at, locked_until, id);

CREATE TABLE runrun_v2.notification_templates (
  type TEXT PRIMARY KEY,
  wechat_template_id TEXT,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO runrun_v2.notification_templates(type) VALUES
 ('PAYMENT_SUCCEEDED'),('DELIVERY_COMPLETED'),('SUPPLEMENT_REQUIRED'),('REFUND_UPDATED'),
 ('AFTER_SALES_UPDATED'),('REVIEW_REMINDER'),('REVIEW_ESCALATED'),('CERTIFICATION_UPDATED')
;

-- One idempotent call creates the durable in-app row and both delivery intents.
-- The caller's order/payment transaction owns this transaction boundary.
CREATE OR REPLACE FUNCTION runrun_v2.enqueue_notification(
  p_recipient BIGINT, p_order BIGINT, p_type TEXT, p_event_key TEXT,
  p_content JSONB, p_wechat_payload JSONB DEFAULT '{}'::jsonb
) RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE n BIGINT;
BEGIN
  IF p_event_key IS NULL OR btrim(p_event_key) = '' OR jsonb_typeof(p_content) <> 'object' THEN
    RAISE EXCEPTION 'NOTIFICATION_INVALID';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM runrun_v2.notification_templates WHERE type=p_type AND enabled) THEN
    RAISE EXCEPTION 'NOTIFICATION_TYPE_DISABLED';
  END IF;
  PERFORM pg_advisory_xact_lock(hashtextextended(p_recipient::text || ':' || p_type || ':' || p_event_key, 0));
  SELECT id INTO n FROM runrun_v2.user_notifications
    WHERE user_id=p_recipient AND type=p_type AND content->>'eventKey'=p_event_key;
  IF n IS NULL THEN
    INSERT INTO runrun_v2.user_notifications(user_id,order_id,type,content)
      VALUES (p_recipient,p_order,p_type,p_content || jsonb_build_object('eventKey',p_event_key))
      RETURNING id INTO n;
    INSERT INTO runrun_v2.notification_outbox(recipient_id,event_key,channel,payload,status,next_attempt_at)
      VALUES (p_recipient,p_event_key,'IN_APP',p_content,'SENT',NULL);
    INSERT INTO runrun_v2.notification_outbox(recipient_id,event_key,channel,payload,status,next_attempt_at)
      VALUES (p_recipient,p_event_key,'WECHAT',p_wechat_payload,'PENDING',now());
  END IF;
  RETURN n;
END $$;

-- Claim is safe for multiple workers; an expired SENDING lease is retryable.
CREATE OR REPLACE FUNCTION runrun_v2.claim_notification_outbox(p_limit INTEGER DEFAULT 50)
RETURNS TABLE(id BIGINT,recipient_id BIGINT,event_key TEXT,channel TEXT,payload JSONB,attempt_count INTEGER)
LANGUAGE sql AS $$
  WITH picked AS (
    SELECT o.id FROM runrun_v2.notification_outbox o
    WHERE ((o.status IN ('PENDING','FAILED') AND (o.next_attempt_at IS NULL OR o.next_attempt_at<=now()))
       OR (o.status='SENDING' AND o.locked_until<now()))
    ORDER BY o.created_at,o.id FOR UPDATE SKIP LOCKED LIMIT greatest(1,least(p_limit,500))
  )
  UPDATE runrun_v2.notification_outbox o
  SET status='SENDING', attempt_count=o.attempt_count+1,
      locked_until=now()+interval '2 minutes', updated_at=now()
  FROM picked WHERE o.id=picked.id
  RETURNING o.id,o.recipient_id,o.event_key,o.channel,o.payload,o.attempt_count;
$$;

CREATE OR REPLACE FUNCTION runrun_v2.complete_notification_outbox(p_id BIGINT,p_success BOOLEAN,p_error TEXT DEFAULT NULL)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE tries INTEGER;
BEGIN
  SELECT attempt_count INTO tries FROM runrun_v2.notification_outbox WHERE id=p_id FOR UPDATE;
  IF NOT FOUND THEN RETURN; END IF;
  IF p_success THEN
    UPDATE runrun_v2.notification_outbox SET status='SENT',sent_at=now(),locked_until=NULL,last_error=NULL,next_attempt_at=NULL,updated_at=now() WHERE id=p_id;
  ELSE
    UPDATE runrun_v2.notification_outbox SET status='FAILED',failed_at=now(),locked_until=NULL,
      last_error=left(coalesce(p_error,'delivery failed'),500),
      next_attempt_at=now()+least(interval '6 hours', interval '15 seconds' * power(2,greatest(0,tries-1))),updated_at=now() WHERE id=p_id;
  END IF;
END $$;

-- M08 business wiring: these AFTER triggers run in the same transaction as the
-- payment, fulfillment, after-sales, review, and certification state change.
CREATE OR REPLACE FUNCTION runrun_v2.m08_business_notification_trigger() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE j JSONB := to_jsonb(NEW); oldj JSONB := CASE WHEN TG_OP='INSERT' THEN '{}'::jsonb ELSE to_jsonb(OLD) END;
        recipient BIGINT; event_type TEXT; event_key TEXT; payload JSONB;
BEGIN
  IF TG_TABLE_NAME='v2_orders' AND j->>'status'='DELIVERED_PENDING_CONFIRMATION' AND oldj->>'status' IS DISTINCT FROM j->>'status' THEN
    recipient := (j->>'user_id')::bigint; event_type := 'DELIVERY_COMPLETED'; event_key := 'delivery:'||(j->>'id');
  ELSIF TG_TABLE_NAME='payment_orders' AND j->>'status'='PAID' AND oldj->>'status' IS DISTINCT FROM j->>'status' THEN
    SELECT user_id INTO recipient FROM runrun_v2.v2_orders WHERE id=(j->>'order_id')::bigint;
    event_type := CASE WHEN j->>'purpose'='SUPPLEMENT' THEN 'SUPPLEMENT_REQUIRED' ELSE 'PAYMENT_SUCCEEDED' END;
    event_key := 'payment:'||(j->>'id');
  ELSIF TG_TABLE_NAME='refund_orders' AND j->>'status'='SUCCEEDED' AND oldj->>'status' IS DISTINCT FROM j->>'status' THEN
    SELECT user_id INTO recipient FROM runrun_v2.v2_orders WHERE id=(j->>'order_id')::bigint;
    event_type := 'REFUND_UPDATED'; event_key := 'refund:'||(j->>'id');
  ELSIF TG_TABLE_NAME='after_sales_cases' AND (TG_OP='INSERT' OR (j->>'status'='RESOLVED' AND oldj->>'status' IS DISTINCT FROM j->>'status')) THEN
    recipient := (j->>'opened_by')::bigint; event_type := 'AFTER_SALES_UPDATED'; event_key := 'after-sales:'||(j->>'id')||':'||(j->>'status');
  ELSIF TG_TABLE_NAME='order_adjustments' AND j->>'remind_at' IS NOT NULL AND oldj->>'remind_at' IS DISTINCT FROM j->>'remind_at' THEN
    recipient := COALESCE((j->>'assigned_admin_id')::bigint,(SELECT review_owner_id FROM runrun_v2.platform_settings WHERE id=1));
    event_type := 'REVIEW_REMINDER'; event_key := 'review-reminder:'||(j->>'id');
  ELSIF TG_TABLE_NAME='order_adjustments' AND j->>'escalate_at' IS NOT NULL AND oldj->>'escalate_at' IS DISTINCT FROM j->>'escalate_at' THEN
    recipient := (SELECT review_owner_id FROM runrun_v2.platform_settings WHERE id=1);
    event_type := 'REVIEW_ESCALATED'; event_key := 'review-escalated:'||(j->>'id');
  ELSIF TG_TABLE_NAME='order_adjustments' AND j->>'status' IN ('PENDING_PAYMENT','COMPLETED','REJECTED','CLOSED_ORIGINAL_PRICE') AND oldj->>'status' IS DISTINCT FROM j->>'status' THEN
    recipient := (j->>'requested_by')::bigint;
    event_type := CASE WHEN j->>'status'='PENDING_PAYMENT' THEN 'SUPPLEMENT_REQUIRED' ELSE 'AFTER_SALES_UPDATED' END;
    event_key := 'adjustment:'||(j->>'id')||':'||(j->>'status');
  ELSIF TG_TABLE_NAME='courier_certifications' AND (TG_OP='INSERT' OR oldj->>'status' IS DISTINCT FROM j->>'status') THEN
    recipient := (j->>'courier_id')::bigint; event_type := 'CERTIFICATION_UPDATED'; event_key := 'certification:'||(j->>'id')||':'||(j->>'version');
  ELSE RETURN NEW;
  END IF;
  IF recipient IS NOT NULL THEN
    payload := jsonb_build_object('eventKey',event_key,'entityId',j->>'id','status',j->>'status');
    PERFORM runrun_v2.enqueue_notification(recipient, NULL, event_type, event_key, payload, payload);
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER m08_v2_orders_notify AFTER INSERT OR UPDATE OF status ON runrun_v2.v2_orders
  FOR EACH ROW EXECUTE FUNCTION runrun_v2.m08_business_notification_trigger();
CREATE TRIGGER m08_payment_orders_notify AFTER INSERT OR UPDATE OF status ON runrun_v2.payment_orders
  FOR EACH ROW EXECUTE FUNCTION runrun_v2.m08_business_notification_trigger();
CREATE TRIGGER m08_refund_orders_notify AFTER INSERT OR UPDATE OF status ON runrun_v2.refund_orders
  FOR EACH ROW EXECUTE FUNCTION runrun_v2.m08_business_notification_trigger();
CREATE TRIGGER m08_after_sales_notify AFTER INSERT OR UPDATE OF status ON runrun_v2.after_sales_cases
  FOR EACH ROW EXECUTE FUNCTION runrun_v2.m08_business_notification_trigger();
CREATE TRIGGER m08_adjustments_notify AFTER INSERT OR UPDATE OF status ON runrun_v2.order_adjustments
  FOR EACH ROW EXECUTE FUNCTION runrun_v2.m08_business_notification_trigger();
CREATE TRIGGER m08_certifications_notify AFTER INSERT OR UPDATE OF status ON runrun_v2.courier_certifications
  FOR EACH ROW EXECUTE FUNCTION runrun_v2.m08_business_notification_trigger();
COMMIT;





