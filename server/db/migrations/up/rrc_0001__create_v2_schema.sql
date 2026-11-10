-- RunRunCampus v2 independent database initialization.
-- M00 creates only the namespace; business tables belong to later modules.
-- Refuse any existing user schema/object so a legacy database cannot be mistaken for an empty v2 database.
BEGIN;
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM pg_namespace n
    WHERE n.nspname NOT IN ('public', 'information_schema')
      AND n.nspname !~ '^pg_'
  ) OR EXISTS (
    SELECT 1 FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
  ) OR EXISTS (
    SELECT 1 FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
  ) OR EXISTS (
    SELECT 1 FROM pg_type t
    JOIN pg_namespace n ON n.oid = t.typnamespace
    WHERE n.nspname = 'public'
  ) THEN
    RAISE EXCEPTION 'M00 requires an empty isolated database; existing user objects or schemas found';
  END IF;
END
$$;

CREATE SCHEMA runrun_v2;
COMMENT ON SCHEMA runrun_v2 IS
  'RunRunCampus stage-one v2 namespace; initialized from an empty isolated database; legacy V1-V43 excluded.';
COMMIT;
