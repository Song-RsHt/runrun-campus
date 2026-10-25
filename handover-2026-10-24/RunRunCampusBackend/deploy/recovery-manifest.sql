-- Only run against the isolated restored database. Never emit row contents.
BEGIN ISOLATION LEVEL REPEATABLE READ;
CREATE TEMP TABLE drill_manifest (name text, rows bigint, fingerprint text) ON COMMIT DROP;
DO $$
DECLARE t record;
BEGIN
    FOR t IN SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename LOOP
        EXECUTE format(
            'INSERT INTO drill_manifest SELECT %L, count(*), md5(COALESCE(string_agg(md5(row_to_json(r)::text), '''' ORDER BY md5(row_to_json(r)::text)), '''')) FROM public.%I r',
            t.tablename, t.tablename);
    END LOOP;
END $$;
SELECT json_build_object(
    'tables', (SELECT json_agg(m ORDER BY name) FROM drill_manifest m),
    'migrations', (SELECT json_agg(m ORDER BY installed_rank) FROM (
        SELECT installed_rank, version, checksum, success FROM flyway_schema_history
    ) m),
    'sequences', (SELECT json_agg(s ORDER BY sequencename) FROM (
        SELECT sequencename, last_value FROM pg_sequences WHERE schemaname = 'public'
    ) s),
    'invalidConstraints', (SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE n.nspname = 'public' AND NOT c.convalidated),
    'schemaHash', (SELECT md5(string_agg(definition, E'\n' ORDER BY definition)) FROM (
        SELECT format('column:%s:%s:%s:%s:%s:%s:%s', table_name, ordinal_position, column_name,
            data_type, udt_name, is_nullable, coalesce(column_default, '')) AS definition
        FROM information_schema.columns WHERE table_schema = 'public'
        UNION ALL
        SELECT format('constraint:%s:%s:%s', c.conrelid::regclass, c.conname, pg_get_constraintdef(c.oid))
        FROM pg_constraint c JOIN pg_namespace n ON n.oid = c.connamespace WHERE n.nspname = 'public'
        UNION ALL
        SELECT format('index:%s:%s', tablename, indexdef) FROM pg_indexes WHERE schemaname = 'public'
    ) definitions)
);
COMMIT;
