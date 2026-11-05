-- M08 rollback. Refuse removal while durable notification evidence remains.
BEGIN;
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='runrun_v2' AND c.relkind IN ('r','p','v','m','S','f')
      AND NOT EXISTS (SELECT 1 FROM runrun_v2.m08_rollback_baseline b WHERE b.object_name=c.relname)
      AND c.relname NOT IN ('user_notifications','notification_templates','m08_rollback_baseline')
      -- M08 itself creates this identity sequence. Accept only the sequence
      -- actually owned by user_notifications.id; unrelated later objects still block rollback.
      AND NOT (c.relkind='S' AND c.relname='user_notifications_id_seq'
               AND pg_get_serial_sequence('runrun_v2.user_notifications','id') = format('%I.%I',n.nspname,c.relname))
  ) THEN
    RAISE EXCEPTION 'M08 rollback refused: later runrun_v2 objects exist';
  END IF;
  IF EXISTS (SELECT 1 FROM runrun_v2.user_notifications)
     OR EXISTS (SELECT 1 FROM runrun_v2.notification_outbox WHERE status <> 'SENT') THEN
    RAISE EXCEPTION 'M08 rollback refused: notification records or pending deliveries exist';
  END IF;
END $$;
DROP FUNCTION runrun_v2.complete_notification_outbox(BIGINT,BOOLEAN,TEXT);
DROP FUNCTION runrun_v2.claim_notification_outbox(INTEGER);
DROP FUNCTION runrun_v2.enqueue_notification(BIGINT,BIGINT,TEXT,TEXT,JSONB,JSONB);
DROP TRIGGER m08_v2_orders_notify ON runrun_v2.v2_orders;
DROP TRIGGER m08_payment_orders_notify ON runrun_v2.payment_orders;
DROP TRIGGER m08_refund_orders_notify ON runrun_v2.refund_orders;
DROP TRIGGER m08_after_sales_notify ON runrun_v2.after_sales_cases;
DROP TRIGGER m08_adjustments_notify ON runrun_v2.order_adjustments;
DROP TRIGGER m08_certifications_notify ON runrun_v2.courier_certifications;
DROP FUNCTION runrun_v2.m08_business_notification_trigger();
DROP FUNCTION runrun_v2.run_notification_review_timers();
ALTER TABLE runrun_v2.order_adjustments DROP COLUMN reminder_notified_at, DROP COLUMN escalation_notified_at;
DROP TABLE runrun_v2.notification_templates;
DROP INDEX runrun_v2.notification_outbox_due_idx;
DROP INDEX runrun_v2.user_notifications_event_uq;
DROP TABLE runrun_v2.user_notifications;
ALTER TABLE runrun_v2.notification_outbox DROP COLUMN IF EXISTS locked_until, DROP COLUMN IF EXISTS sent_at, DROP COLUMN IF EXISTS failed_at;
DROP TABLE runrun_v2.m08_rollback_baseline;
COMMIT;




