BEGIN;
DO $$
DECLARE n BIGINT;
BEGIN
  IF to_regclass('runrun_v2.user_notifications') IS NULL THEN
    RAISE EXCEPTION 'M07 notification table is absent';
  END IF;
  SELECT count(*) INTO n FROM runrun_v2.user_notifications;
  IF n <> 0 THEN RAISE EXCEPTION 'Refusing M07 rollback: user notification data exists'; END IF;
END $$;
DROP TABLE runrun_v2.user_notifications;
COMMIT;
