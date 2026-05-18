-- ============================================
-- Pudel Brain Rework - Database Migration
-- Version: V10
--
-- Adds columns and tables needed for the reworked PudelBrain:
-- - message_id tracking in passive_context
-- - respond_to tracking in dialogue_history
-- - attachment_urls in both tables
-- - forwarded_messages table
-- - entities JSONB in passive_context
-- ============================================

-- ============================================
-- Helper function: upgrade passive_context table
-- ============================================
CREATE OR REPLACE FUNCTION upgrade_passive_context(schema_name TEXT)
RETURNS void AS $$
BEGIN
    -- Add message_id column (Discord message ID this context came from)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = schema_name AND table_name = 'passive_context' AND column_name = 'message_id'
    ) THEN
        EXECUTE format('ALTER TABLE %I.passive_context ADD COLUMN message_id BIGINT', schema_name);
        RAISE NOTICE 'Added message_id to %.passive_context', schema_name;
    END IF;

    -- Add attachment_urls column (Discord CDN links)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = schema_name AND table_name = 'passive_context' AND column_name = 'attachment_urls'
    ) THEN
        EXECUTE format('ALTER TABLE %I.passive_context ADD COLUMN attachment_urls TEXT[]', schema_name);
        RAISE NOTICE 'Added attachment_urls to %.passive_context', schema_name;
    END IF;

    -- Add forwarded_content column (forwarded message data inline as JSONB)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = schema_name AND table_name = 'passive_context' AND column_name = 'forwarded_content'
    ) THEN
        EXECUTE format('ALTER TABLE %I.passive_context ADD COLUMN forwarded_content JSONB', schema_name);
        RAISE NOTICE 'Added forwarded_content to %.passive_context', schema_name;
    END IF;

    -- Add entities column (extracted entities as JSONB)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = schema_name AND table_name = 'passive_context' AND column_name = 'entities'
    ) THEN
        EXECUTE format('ALTER TABLE %I.passive_context ADD COLUMN entities JSONB', schema_name);
        RAISE NOTICE 'Added entities to %.passive_context', schema_name;
    END IF;

    -- Create index on message_id for fast lookups
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = schema_name AND indexname = 'idx_passive_context_message_id'
    ) THEN
        EXECUTE format('CREATE INDEX idx_passive_context_message_id ON %I.passive_context(message_id)', schema_name);
        RAISE NOTICE 'Created index idx_passive_context_message_id on %.passive_context', schema_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- Helper function: upgrade dialogue_history table
-- ============================================
CREATE OR REPLACE FUNCTION upgrade_dialogue_history(schema_name TEXT)
RETURNS void AS $$
BEGIN
    -- Add respond_to column (message ID the bot responded to)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = schema_name AND table_name = 'dialogue_history' AND column_name = 'respond_to'
    ) THEN
        EXECUTE format('ALTER TABLE %I.dialogue_history ADD COLUMN respond_to BIGINT', schema_name);
        RAISE NOTICE 'Added respond_to to %.dialogue_history', schema_name;
    END IF;

    -- Add attachment_urls column (Discord CDN links)
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = schema_name AND table_name = 'dialogue_history' AND column_name = 'attachment_urls'
    ) THEN
        EXECUTE format('ALTER TABLE %I.dialogue_history ADD COLUMN attachment_urls TEXT[]', schema_name);
        RAISE NOTICE 'Added attachment_urls to %.dialogue_history', schema_name;
    END IF;

    -- Create index on respond_to for fast lookups
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE schemaname = schema_name AND indexname = 'idx_dialogue_history_respond_to'
    ) THEN
        EXECUTE format('CREATE INDEX idx_dialogue_history_respond_to ON %I.dialogue_history(respond_to)', schema_name);
        RAISE NOTICE 'Created index idx_dialogue_history_respond_to on %.dialogue_history', schema_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- Helper function: create forwarded_messages table
-- ============================================
CREATE OR REPLACE FUNCTION create_forwarded_messages(schema_name TEXT)
RETURNS void AS $$
BEGIN
    -- Create forwarded_messages table if it doesn't exist
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = schema_name AND table_name = 'forwarded_messages'
    ) THEN
        EXECUTE format('
            CREATE TABLE %I.forwarded_messages (
                id BIGSERIAL PRIMARY KEY,
                passive_context_id BIGINT REFERENCES %I.passive_context(id) ON DELETE CASCADE,
                message_id BIGINT NOT NULL,
                author_id BIGINT,
                author_name VARCHAR(255),
                content TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        ', schema_name, schema_name);

        -- Create indexes
        EXECUTE format('CREATE INDEX idx_fwd_msgs_passive_ctx ON %I.forwarded_messages(passive_context_id)', schema_name);
        EXECUTE format('CREATE INDEX idx_fwd_msgs_message_id ON %I.forwarded_messages(message_id)', schema_name);

        RAISE NOTICE 'Created forwarded_messages table in %', schema_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- Master function: upgrade all brain tables in a schema
-- ============================================
CREATE OR REPLACE FUNCTION upgrade_brain_schema(schema_name TEXT)
RETURNS void AS $$
BEGIN
    -- Only upgrade if passive_context exists (indicates brain tables are present)
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = schema_name AND table_name = 'passive_context'
    ) THEN
        PERFORM upgrade_passive_context(schema_name);
    END IF;

    -- Only upgrade if dialogue_history exists
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = schema_name AND table_name = 'dialogue_history'
    ) THEN
        PERFORM upgrade_dialogue_history(schema_name);
    END IF;

    -- Create forwarded_messages if passive_context exists
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = schema_name AND table_name = 'passive_context'
    ) THEN
        PERFORM create_forwarded_messages(schema_name);
    END IF;

    RAISE NOTICE 'Upgraded brain schema: %', schema_name;
END;
$$ LANGUAGE plpgsql;

-- ============================================
-- Upgrade all existing guild schemas
-- ============================================
DO $$
DECLARE
    schema_record RECORD;
BEGIN
    FOR schema_record IN
        SELECT schema_name FROM information_schema.schemata
        WHERE schema_name LIKE 'guild_%' OR schema_name LIKE 'user_%'
    LOOP
        BEGIN
            PERFORM upgrade_brain_schema(schema_record.schema_name);
        EXCEPTION WHEN OTHERS THEN
            RAISE WARNING 'Failed to upgrade schema %: %', schema_record.schema_name, SQLERRM;
        END;
    END LOOP;
END;
$$;

-- ============================================
-- Update per-guild schema template in SchemaManagementService
-- Note: The Java code in SchemaManagementService.createGuildTables() and
-- createUserTables() should be updated to include the new columns.
-- This migration handles existing schemas; new schemas get the columns
-- from the updated Java code.
-- ============================================

COMMENT ON FUNCTION upgrade_brain_schema IS 'Upgrades brain-related tables in a guild/user schema for the reworked PudelBrain';
COMMENT ON FUNCTION upgrade_passive_context IS 'Adds new columns to passive_context table';
COMMENT ON FUNCTION upgrade_dialogue_history IS 'Adds new columns to dialogue_history table';
COMMENT ON FUNCTION create_forwarded_messages IS 'Creates the forwarded_messages table';

COMMIT;

