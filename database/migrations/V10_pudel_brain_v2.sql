-- ============================================
-- PudelBrain v2 Database Migration
-- ============================================
-- This migration adds the new columns and tables required by the reworked PudelBrain.
-- It is idempotent and safe to run on existing databases.
--
-- Changes:
-- 1. Add respond_to and attachment_urls columns to dialogue_history (guild schemas)
-- 2. Add respond_to and attachment_urls columns to dialogue_history (user schemas)
-- 3. Create passive_context table (guild schemas)
-- 4. Create forwarded_messages table (guild schemas)
-- 5. Add indexes for new columns
-- ============================================

-- Note: This migration uses DO blocks to handle per-schema changes.
-- For each guild/user schema, the new columns and tables are added if they don't exist.

-- ============================================
-- Guild Schema Updates
-- ============================================

DO $$
DECLARE
    schema_record RECORD;
BEGIN
    -- Iterate over all guild schemas
    FOR schema_record IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name LIKE 'guild_%'
    LOOP
        -- Add respond_to column to dialogue_history if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = schema_record.schema_name
              AND table_name = 'dialogue_history'
              AND column_name = 'respond_to'
        ) THEN
            EXECUTE format('ALTER TABLE %I.dialogue_history ADD COLUMN respond_to BIGINT', schema_record.schema_name);
        END IF;

        -- Add attachment_urls column to dialogue_history if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = schema_record.schema_name
              AND table_name = 'dialogue_history'
              AND column_name = 'attachment_urls'
        ) THEN
            EXECUTE format('ALTER TABLE %I.dialogue_history ADD COLUMN attachment_urls TEXT[]', schema_record.schema_name);
        END IF;

        -- Create passive_context table if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = schema_record.schema_name
              AND table_name = 'passive_context'
        ) THEN
            EXECUTE format('
                CREATE TABLE %I.passive_context (
                    id BIGSERIAL PRIMARY KEY,
                    message_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    channel_id BIGINT NOT NULL,
                    content TEXT NOT NULL,
                    entities JSONB,
                    attachment_urls TEXT[],
                    forwarded_content JSONB,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )', schema_record.schema_name);

            EXECUTE format('CREATE INDEX idx_passive_ctx_message_id ON %I.passive_context(message_id)', schema_record.schema_name);
            EXECUTE format('CREATE INDEX idx_passive_ctx_user ON %I.passive_context(user_id)', schema_record.schema_name);
            EXECUTE format('CREATE INDEX idx_passive_ctx_channel ON %I.passive_context(channel_id)', schema_record.schema_name);
        END IF;

        -- Create forwarded_messages table if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.tables
            WHERE table_schema = schema_record.schema_name
              AND table_name = 'forwarded_messages'
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
                )', schema_record.schema_name, schema_record.schema_name);

            EXECUTE format('CREATE INDEX idx_fwd_msgs_passive_ctx ON %I.forwarded_messages(passive_context_id)', schema_record.schema_name);
            EXECUTE format('CREATE INDEX idx_fwd_msgs_message_id ON %I.forwarded_messages(message_id)', schema_record.schema_name);
        END IF;

        -- Add index for respond_to if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 FROM pg_indexes
            WHERE schemaname = schema_record.schema_name
              AND indexname = 'idx_dialogue_respond_to'
        ) THEN
            EXECUTE format('CREATE INDEX idx_dialogue_respond_to ON %I.dialogue_history(respond_to)', schema_record.schema_name);
        END IF;

    END LOOP;
END $$;

-- ============================================
-- User Schema Updates
-- ============================================

DO $$
DECLARE
    schema_record RECORD;
BEGIN
    -- Iterate over all user schemas
    FOR schema_record IN
        SELECT schema_name
        FROM information_schema.schemata
        WHERE schema_name LIKE 'user_%'
    LOOP
        -- Add respond_to column to dialogue_history if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = schema_record.schema_name
              AND table_name = 'dialogue_history'
              AND column_name = 'respond_to'
        ) THEN
            EXECUTE format('ALTER TABLE %I.dialogue_history ADD COLUMN respond_to BIGINT', schema_record.schema_name);
        END IF;

        -- Add attachment_urls column to dialogue_history if it doesn't exist
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_schema = schema_record.schema_name
              AND table_name = 'dialogue_history'
              AND column_name = 'attachment_urls'
        ) THEN
            EXECUTE format('ALTER TABLE %I.dialogue_history ADD COLUMN attachment_urls TEXT[]', schema_record.schema_name);
        END IF;

    END LOOP;
END $$;

