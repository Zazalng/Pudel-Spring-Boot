-- ===========================================
-- V12: Fix Plugin Schema Migration
-- ===========================================
-- This migration creates plugin schemas and moves tables from public schema
-- to the correct plugin schemas based on plugin_database_registry.
--
-- The old prefix format was: p_{uuid}_{tableName}
-- The new schema format is: plugin_{uuid}.{tableName}
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
    schema_name TEXT;
    old_prefix TEXT;
    table_record RECORD;
    new_table_name TEXT;
    total_migrated INTEGER := 0;
    plugin_migrated INTEGER;
BEGIN
    -- Iterate over all registered plugins
    FOR plugin_record IN
        SELECT plugin_id, db_prefix FROM plugin_database_registry WHERE enabled = true
    LOOP
        old_prefix := plugin_record.db_prefix;
        -- Derive schema name from db_prefix (p_{uuid}_ -> plugin_{uuid})
        -- Remove 'p_' prefix and trailing '_'
        schema_name := 'plugin_' || substring(old_prefix FROM 3 FOR length(old_prefix) - 3);

        -- Create the plugin schema if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.schemata WHERE schema_name = schema_name
        ) THEN
            EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', schema_name);
            RAISE NOTICE 'Created schema: %', schema_name;
        END IF;

        plugin_migrated := 0;

        -- Find and move tables from public schema that match the old prefix
        FOR table_record IN
            SELECT tablename
            FROM pg_tables
            WHERE schemaname = 'public'
            AND tablename LIKE old_prefix || '%'
        LOOP
            -- Extract the actual table name (remove the prefix)
            new_table_name := substring(table_record.tablename FROM length(old_prefix) + 1);

            -- Check if table already exists in plugin schema
            IF NOT EXISTS (
                SELECT 1 FROM pg_tables
                WHERE schemaname = schema_name AND tablename = new_table_name
            ) THEN
                BEGIN
                    -- Move table from public schema to plugin schema
                    EXECUTE format(
                        'ALTER TABLE public.%I SET SCHEMA %I',
                        table_record.tablename, schema_name
                    );
                    plugin_migrated := plugin_migrated + 1;
                    total_migrated := total_migrated + 1;
                    RAISE NOTICE 'Moved table public.% to %.%', table_record.tablename, schema_name, new_table_name;
                EXCEPTION WHEN OTHERS THEN
                    RAISE WARNING 'Failed to move table %: %', table_record.tablename, SQLERRM;
                END;
            ELSE
                RAISE NOTICE 'Table % already exists in schema %, skipping', new_table_name, schema_name;
            END IF;
        END LOOP;

        RAISE NOTICE 'Plugin %: schema %, migrated % tables', plugin_record.plugin_id, schema_name, plugin_migrated;
    END LOOP;

    RAISE NOTICE 'Total tables migrated: %', total_migrated;
END $$;

-- Verify the migration
SELECT 'Plugin schemas:' AS info;
SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'plugin_%';

SELECT 'Tables in plugin schemas:' AS info;
SELECT schemaname, tablename FROM pg_tables WHERE schemaname LIKE 'plugin_%' ORDER BY schemaname, tablename;
