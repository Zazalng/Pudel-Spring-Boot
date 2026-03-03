-- ============================================
-- V9: Add guild-level plugin control
-- ============================================
-- Adds disabled_plugins column to guild_settings table.
-- This allows each guild to independently disable specific
-- globally-enabled plugins for their server.
-- Stored as comma-separated plugin names (e.g. "MusicPlayer,WeatherTools").
-- ============================================

ALTER TABLE guild_settings
    ADD COLUMN IF NOT EXISTS disabled_plugins TEXT;

COMMENT ON COLUMN guild_settings.disabled_plugins IS 'Comma-separated list of plugin names disabled for this guild';

