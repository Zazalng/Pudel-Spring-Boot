-- ===========================================
-- V12: Fix Plugin Schema Migration
-- ===========================================
-- This migration creates plugin schemas and moves tables from public schema
-- to the correct plugin schemas based on plugin_database_registry.
--
-- Plugin tables found in public schema:
--   p_304934d6_prank_container      -> plugin_304934d6.prank_container
--   p_304934d6_prank_collection     -> plugin_304934d6.prank_collection
--   p_c41b38a1_managed_roles        -> plugin_c41b38a1.managed_roles
--   p_c41b38a1_user_assignments     -> plugin_c41b38a1.user_assignments
--   p_2c1134ee_music_queue          -> plugin_2c1134ee.music_queue
--   p_2c1134ee_music_history        -> plugin_2c1134ee.music_history
--   p_dbdb3182_category             -> plugin_dbdb3182.category
--   p_dbdb3182_privilege_role       -> plugin_dbdb3182.privilege_role
--   p_dbdb3182_permission_profile   -> plugin_dbdb3182.permission_profile
-- ============================================

DO $$
DECLARE
    plugin_record RECORD;
    target_schema TEXT;
    src_prefix TEXT;
    table_record RECORD;
    dest_table TEXT;
    total_migrated INTEGER := 0;
    plugin_migrated INTEGER;
    schema_exists BOOLEAN;
BEGIN
    -- Iterate over all registered plugins
    FOR plugin_record IN
        SELECT plugin_id, db_prefix FROM plugin_database_registry WHERE enabled = true
    LOOP
        src_prefix := plugin_record.db_prefix;
        -- Derive schema name from db_prefix (p_{uuid}_ -> plugin_{uuid})
        -- Remove 'p_' prefix and trailing '_'
        target_schema := 'plugin_' || substring(src_prefix FROM 3 FOR length(src_prefix) - 3);

        -- Check if schema exists
        SELECT EXISTS (
            SELECT 1 FROM information_schema.schemata s WHERE s.schema_name = target_schema
        ) INTO schema_exists;

        -- Create the plugin schema if it doesn't exist
        IF NOT schema_exists THEN
            EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', target_schema);
            RAISE NOTICE 'Created schema: %', target_schema;
        END IF;

        plugin_migrated := 0;

        -- Find and move tables from public schema that match the old prefix
        FOR table_record IN
            SELECT t.tablename
            FROM pg_tables t
            WHERE t.schemaname = 'public'
            AND t.tablename LIKE src_prefix || '%'
        LOOP
            -- Extract the actual table name (remove the prefix)
            dest_table := substring(table_record.tablename FROM length(src_prefix) + 1);

            -- Check if table already exists in plugin schema
            IF NOT EXISTS (
                SELECT 1 FROM pg_tables t2
                WHERE t2.schemaname = target_schema AND t2.tablename = dest_table
            ) THEN
                BEGIN
                    -- Move table from public schema to plugin schema
                    EXECUTE format(
                        'ALTER TABLE public.%I SET SCHEMA %I',
                        table_record.tablename, target_schema
                    );
                    plugin_migrated := plugin_migrated + 1;
                    total_migrated := total_migrated + 1;
                    RAISE NOTICE 'Moved table public.% to %.%', table_record.tablename, target_schema, dest_table;
                EXCEPTION WHEN OTHERS THEN
                    RAISE WARNING 'Failed to move table %: %', table_record.tablename, SQLERRM;
                END;
            ELSE
                RAISE NOTICE 'Table % already exists in schema %, skipping', dest_table, target_schema;
            END IF;
        END LOOP;

        RAISE NOTICE 'Plugin %: schema %, migrated % tables', plugin_record.plugin_id, target_schema, plugin_migrated;
    END LOOP;

    RAISE NOTICE 'Total tables migrated: %', total_migrated;
END $$;

-- Verify the migration
SELECT 'Plugin schemas:' AS info;
SELECT s.schema_name FROM information_schema.schemata s WHERE s.schema_name LIKE 'plugin_%';

SELECT 'Tables in plugin schemas:' AS info;
SELECT t.schemaname, t.tablename FROM pg_tables t WHERE t.schemaname LIKE 'plugin_%' ORDER BY t.schemaname, t.tablename;
