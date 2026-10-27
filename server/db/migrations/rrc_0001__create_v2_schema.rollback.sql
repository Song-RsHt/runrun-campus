-- Manual rollback for rrc_0001__create_v2_schema.sql.
-- Run only in the isolated database after verifying that no later rrc migrations ran.
-- This script intentionally refuses to remove a non-empty schema.

DO $$
DECLARE
  object_count integer;
BEGIN
  SELECT count(*)
    INTO object_count
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname = 'runrun_v2'
     AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f');

  IF object_count <> 0 THEN
    RAISE EXCEPTION
      'Refusing M00 rollback: runrun_v2 contains % relation(s); restore an isolated database or roll back later migrations first',
      object_count;
  END IF;
END
$$;

DROP SCHEMA IF EXISTS runrun_v2;
