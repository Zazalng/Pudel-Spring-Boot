-- ===========================================
-- V11: Fix passive_context UNIQUE constraint on message_id
-- ===========================================
-- The ON CONFLICT (message_id) DO UPDATE clause requires a UNIQUE constraint
-- or primary key on message_id. This migration adds it if missing.

DO $$
DECLARE
    schema_record RECORD;
BEGIN
    FOR schema_record IN
        SELECT schema_name FROM information_schema.schemata
        WHERE schema_name LIKE 'guild_%'
    LOOP
        IF EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = schema_record.schema_name AND table_name = 'passive_context'
        ) THEN
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.table_constraints
                WHERE constraint_schema = schema_record.schema_name
                  AND table_name = 'passive_context'
                  AND constraint_type = 'UNIQUE'
            ) THEN
                EXECUTE format('ALTER TABLE %I.passive_context ADD CONSTRAINT passive_context_message_id_unique UNIQUE (message_id)', schema_record.schema_name);
                RAISE NOTICE 'Added UNIQUE constraint to %.passive_context', schema_record.schema_name;
            END IF;
        END IF;
    END LOOP;
END $$;
