/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed with an additional permission known as the
 * "Pudel Plugin Exception".
 *
 * See the LICENSE and PLUGIN_EXCEPTION files in the project root for details.
 */
package group.worldstandard.pudel.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import net.dv8tion.jda.api.JDA;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.repository.GuildSettingsRepository;

import java.util.Optional;

/**
 * Service for guild settings management.
 */
@Service
public class GuildSettingsService extends BaseService {
    private static final Logger log = LoggerFactory.getLogger(GuildSettingsService.class);

    private final GuildSettingsRepository guildSettingsRepository;
    @SuppressWarnings("deprecation")
    private final PluginService pluginService;

    @SuppressWarnings("deprecation")
    public GuildSettingsService(@Lazy JDA jda, GuildSettingsRepository guildSettingsRepository,
                                @Lazy PluginService pluginService) {
        super(jda);
        this.guildSettingsRepository = guildSettingsRepository;
        this.pluginService = pluginService;
    }

    /**
     * Get guild settings by guild ID.
     */
    public Optional<GuildSettings> getGuildSettings(String guildId) {
        return guildSettingsRepository.findByGuildId(guildId);
    }

    /**
     * Get or create default guild settings.
     */
    public GuildSettings getOrCreateGuildSettings(String guildId) {
        return guildSettingsRepository.findByGuildId(guildId)
                .orElseGet(() -> {
                    GuildSettings settings = new GuildSettings(guildId);
                    return guildSettingsRepository.save(settings);
                });
    }

    /**
     * Update guild settings.
     */
    public GuildSettings updateGuildSettings(String guildId, GuildSettings updatedSettings) {
        try {
            GuildSettings settings = getOrCreateGuildSettings(guildId);

            if (updatedSettings.getPrefix() != null) {
                settings.setPrefix(updatedSettings.getPrefix());
            }
            if (updatedSettings.getVerbosity() != null) {
                settings.setVerbosity(updatedSettings.getVerbosity());
            }
            if (updatedSettings.getCooldown() != null) {
                settings.setCooldown(updatedSettings.getCooldown());
            }
            if (updatedSettings.getLogChannel() != null) {
                settings.setLogChannel(updatedSettings.getLogChannel());
            }
            if (updatedSettings.getBotChannel() != null) {
                settings.setBotChannel(updatedSettings.getBotChannel());
            }
            if (updatedSettings.getBiography() != null) {
                settings.setBiography(updatedSettings.getBiography());
            }
            if (updatedSettings.getPersonality() != null) {
                settings.setPersonality(updatedSettings.getPersonality());
            }
            if (updatedSettings.getPreferences() != null) {
                settings.setPreferences(updatedSettings.getPreferences());
            }
            if (updatedSettings.getDialogueStyle() != null) {
                settings.setDialogueStyle(updatedSettings.getDialogueStyle());
            }
            if (updatedSettings.getDisabledPlugins() != null) {
                settings.setDisabledPlugins(updatedSettings.getDisabledPlugins());
            }

            GuildSettings saved = guildSettingsRepository.save(settings);
            log.info("Updated settings for guild: {}", guildId);
            return saved;
        } catch (Exception e) {
            log.error("Error updating guild settings", e);
            throw new RuntimeException("Failed to update guild settings", e);
        }
    }

    /**
     * Validate guild settings.
     */
    public boolean validateSettings(GuildSettings settings) {
        // Validate prefix length
        if (settings.getPrefix() != null && (settings.getPrefix().isEmpty() || settings.getPrefix().length() > 5)) {
            return false;
        }

        // Validate verbosity level
        if (settings.getVerbosity() != null && (settings.getVerbosity() < 1 || settings.getVerbosity() > 3)) {
            return false;
        }

        // Validate cooldown
        return settings.getCooldown() == null || settings.getCooldown() >= 0;
    }

    /**
     * Delete guild settings.
     */
    public void deleteGuildSettings(String guildId) {
        try {
            guildSettingsRepository.findByGuildId(guildId)
                    .ifPresent(guildSettingsRepository::delete);
            log.info("Deleted settings for guild: {}", guildId);
        } catch (Exception e) {
            log.error("Error deleting guild settings", e);
            throw new RuntimeException("Failed to delete guild settings", e);
        }
    }

    // =====================================================
    // Guild-Level Plugin Control
    // =====================================================

    /**
     * Check if a plugin is enabled for a specific guild.
     * <p>
     * A plugin is considered enabled for a guild if:
     * 1. The plugin is globally enabled by the admin (checked via PluginService)
     * 2. The guild has NOT disabled it in their guild settings
     * <p>
     * If the plugin is globally disabled, it is always unavailable regardless of guild settings.
     * If the guild has no settings yet, all globally-enabled plugins are available.
     *
     * @param guildId    the Discord guild ID
     * @param pluginName the plugin name to check
     * @return true if the plugin is usable in this guild
     */
    public boolean isPluginEnabledForGuild(String guildId, String pluginName) {
        // 1. Check global enable status — if disabled globally, not available anywhere
        if (!pluginService.isPluginEnabled(pluginName)) {
            return false;
        }

        // 2. Check guild-level disabled list
        Optional<GuildSettings> settings = guildSettingsRepository.findByGuildId(guildId);
        // No guild settings = all globally-enabled plugins are available
        return settings.map(guildSettings -> !guildSettings.isPluginDisabled(pluginName)).orElse(true);
    }

    /**
     * Disable a plugin for a specific guild.
     *
     * @param guildId    the Discord guild ID
     * @param pluginName the plugin name to disable
     */
    public void disablePluginForGuild(String guildId, String pluginName) {
        GuildSettings settings = getOrCreateGuildSettings(guildId);
        if (!settings.isPluginDisabled(pluginName)) {
            String current = settings.getDisabledPlugins();
            String updated = (current == null || current.isBlank())
                    ? pluginName
                    : current + "," + pluginName;
            settings.setDisabledPlugins(updated);
            guildSettingsRepository.save(settings);
            log.info("Plugin '{}' disabled for guild {}", pluginName, guildId);
        }
    }

    /**
     * Enable a plugin for a specific guild (remove from disabled list).
     *
     * @param guildId    the Discord guild ID
     * @param pluginName the plugin name to enable
     */
    public void enablePluginForGuild(String guildId, String pluginName) {
        GuildSettings settings = getOrCreateGuildSettings(guildId);
        if (settings.isPluginDisabled(pluginName)) {
            java.util.List<String> disabled = new java.util.ArrayList<>(settings.getDisabledPluginsList());
            disabled.remove(pluginName);
            settings.setDisabledPlugins(String.join(",", disabled));
            guildSettingsRepository.save(settings);
            log.info("Plugin '{}' enabled for guild {}", pluginName, guildId);
        }
    }
}

