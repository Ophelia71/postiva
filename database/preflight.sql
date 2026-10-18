-- Read-only inventory before baselining an existing database at V1.
-- Run with psql -X -v ON_ERROR_STOP=1 -f database/preflight.sql.
-- This checks known prerequisites, NOT every possible schema difference.
BEGIN TRANSACTION READ ONLY;

SELECT current_database() AS database_name, current_user AS connected_role,
       current_setting('server_version') AS postgres_version,
       to_regclass('public.flyway_schema_history') AS flyway_history;

DO $$
DECLARE missing_tables TEXT;
BEGIN
    SELECT string_agg(expected.name, ', ' ORDER BY expected.name)
    INTO missing_tables
    FROM unnest(ARRAY[
        'users', 'pending_registrations', 'business_profiles', 'industry_templates',
        'product_briefs', 'generated_posts', 'post_scores', 'content_calendars',
        'calendar_items', 'uploaded_files', 'social_accounts', 'scheduled_posts',
        'publish_logs', 'ai_generation_logs', 'social_data_deletion_requests'
    ]) AS expected(name)
    WHERE to_regclass('public.' || expected.name) IS NULL;

    IF missing_tables IS NOT NULL THEN
        RAISE EXCEPTION 'Not ready for V1 baseline; missing tables: %', missing_tables;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'post_scores'
          AND column_name = 'score' AND udt_name = 'int4'
    ) THEN
        RAISE EXCEPTION 'post_scores.score differs from the current INTEGER entity mapping; inspect before baselining';
    END IF;
END;
$$;

SELECT table_name, column_name, data_type, character_maximum_length,
       is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
ORDER BY table_name, ordinal_position;

SELECT tablename, indexname, indexdef
FROM pg_indexes WHERE schemaname = 'public'
ORDER BY tablename, indexname;

SELECT conrelid::regclass AS table_name, conname, pg_get_constraintdef(oid) AS definition
FROM pg_constraint WHERE connamespace = 'public'::regnamespace
ORDER BY conrelid::regclass::text, conname;

SELECT 'users' AS table_name, count(*) AS row_count FROM public.users
UNION ALL SELECT 'generated_posts', count(*) FROM public.generated_posts
UNION ALL SELECT 'social_accounts', count(*) FROM public.social_accounts
UNION ALL SELECT 'scheduled_posts', count(*) FROM public.scheduled_posts;

COMMIT;
