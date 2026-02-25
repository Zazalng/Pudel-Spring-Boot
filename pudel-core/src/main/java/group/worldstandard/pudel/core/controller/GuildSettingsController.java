/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard.group
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
package group.worldstandard.pudel.core.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.entity.PluginMetadata;
import group.worldstandard.pudel.core.interaction.InteractionManagerImpl;
import group.worldstandard.pudel.core.service.GuildSettingsService;
import group.worldstandard.pudel.core.service.PluginService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for guild settings management.
 */
@Tag(name = "Guild Settings", description = "Guild configuration and settings management")
@RestController
@RequestMapping("/api/guilds")
@CrossOrigin(origins = "*", maxAge = 3600)
public class GuildSettingsController {
    private static final Logger log = LoggerFactory.getLogger(GuildSettingsController.class);

    private final GuildSettingsService guildSettingsService;
    private final PluginService pluginService;
    private final InteractionManagerImpl interactionManager;

    public GuildSettingsController(GuildSettingsService guildSettingsService,
                                   PluginService pluginService,
                                   InteractionManagerImpl interactionManager) {
        this.guildSettingsService = guildSettingsService;
        this.pluginService = pluginService;
        this.interactionManager = interactionManager;
    }

    /**
     * Get guild settings.
     * GET /api/guilds/{guildId}/settings
     */
    @GetMapping("/{guildId}/settings")
    public ResponseEntity<?> getGuildSettings(@PathVariable String guildId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not authenticated"));
            }

            GuildSettings settings = guildSettingsService.getOrCreateGuildSettings(guildId);
            log.debug("Retrieved settings for guild: {}", guildId);
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            log.error("Error retrieving guild settings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to retrieve guild settings"));
        }
    }

    /**
     * Update guild settings.
     * PATCH /api/guilds/{guildId}/settings
     */
    @PatchMapping("/{guildId}/settings")
    public ResponseEntity<?> updateGuildSettings(
            @PathVariable String guildId,
            @RequestBody GuildSettings updatedSettings) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not authenticated"));
            }

            // Validate settings
            if (!guildSettingsService.validateSettings(updatedSettings)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Invalid settings provided"));
            }

            GuildSettings settings = guildSettingsService.updateGuildSettings(guildId, updatedSettings);
            log.info("Updated settings for guild: {}", guildId);
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            log.error("Error updating guild settings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to update guild settings"));
        }
    }

    /**
     * Create guild settings.
     * POST /api/guilds/{guildId}/settings
     */
    @PostMapping("/{guildId}/settings")
    public ResponseEntity<?> createGuildSettings(@PathVariable String guildId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not authenticated"));
            }

            GuildSettings settings = guildSettingsService.getOrCreateGuildSettings(guildId);
            log.info("Created/verified settings for guild: {}", guildId);
            return ResponseEntity.status(HttpStatus.CREATED).body(settings);
        } catch (Exception e) {
            log.error("Error creating guild settings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to create guild settings"));
        }
    }

    /**
     * Delete guild settings.
     * DELETE /api/guilds/{guildId}/settings
     */
    @DeleteMapping("/{guildId}/settings")
    public ResponseEntity<?> deleteGuildSettings(@PathVariable String guildId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not authenticated"));
            }

            guildSettingsService.deleteGuildSettings(guildId);
            log.info("Deleted settings for guild: {}", guildId);
            return ResponseEntity.ok(new SuccessResponse("Guild settings deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting guild settings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to delete guild settings"));
        }
    }

    // =====================================================
    // Guild Plugin Management
    // =====================================================

    /**
     * Get plugins available for this guild.
     * Returns all globally-enabled plugins with their guild-level enabled/disabled status.
     * GET /api/guilds/{guildId}/plugins
     */
    @GetMapping("/{guildId}/plugins")
    public ResponseEntity<?> getGuildPlugins(@PathVariable String guildId) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not authenticated"));
            }

            // Get all globally-enabled plugins
            List<PluginMetadata> enabledPlugins = pluginService.getEnabledPlugins();
            GuildSettings settings = guildSettingsService.getOrCreateGuildSettings(guildId);
            List<String> disabledForGuild = settings.getDisabledPluginsList();

            // Build response with guild-level status
            List<Map<String, Object>> pluginList = enabledPlugins.stream()
                    .map(plugin -> {
                        Map<String, Object> info = new HashMap<>();
                        info.put("name", plugin.getPluginName());
                        info.put("version", plugin.getPluginVersion());
                        info.put("author", plugin.getPluginAuthor());
                        info.put("description", plugin.getPluginDescription());
                        info.put("enabledForGuild", !disabledForGuild.contains(plugin.getPluginName()));
                        return info;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("plugins", pluginList);
            response.put("total", pluginList.size());
            response.put("guildId", guildId);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting guild plugins", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to get guild plugins"));
        }
    }

    /**
     * Enable a plugin for this guild (remove from disabled list).
     * POST /api/guilds/{guildId}/plugins/{pluginName}/enable
     */
    @PostMapping("/{guildId}/plugins/{pluginName}/enable")
    public ResponseEntity<?> enablePluginForGuild(
            @PathVariable String guildId,
            @PathVariable String pluginName) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not authenticated"));
            }

            // Verify plugin exists and is globally enabled
            if (!pluginService.isPluginEnabled(pluginName)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Plugin '" + pluginName + "' is not globally enabled"));
            }

            guildSettingsService.enablePluginForGuild(guildId, pluginName);

            // Re-sync guild commands so the plugin's slash commands appear in Discord
            syncGuildCommandsAsync(guildId);

            log.info("Plugin '{}' enabled for guild {}", pluginName, guildId);
            return ResponseEntity.ok(new SuccessResponse("Plugin '" + pluginName + "' enabled for this guild"));
        } catch (Exception e) {
            log.error("Error enabling plugin for guild", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to enable plugin for guild"));
        }
    }

    /**
     * Disable a plugin for this guild (add to disabled list).
     * POST /api/guilds/{guildId}/plugins/{pluginName}/disable
     */
    @PostMapping("/{guildId}/plugins/{pluginName}/disable")
    public ResponseEntity<?> disablePluginForGuild(
            @PathVariable String guildId,
            @PathVariable String pluginName) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getPrincipal() == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ErrorResponse("User not authenticated"));
            }

            // Verify plugin exists and is globally enabled
            if (!pluginService.isPluginEnabled(pluginName)) {
                return ResponseEntity.badRequest()
                        .body(new ErrorResponse("Plugin '" + pluginName + "' is not globally enabled"));
            }

            guildSettingsService.disablePluginForGuild(guildId, pluginName);

            // Re-sync guild commands so the plugin's slash commands disappear from Discord
            syncGuildCommandsAsync(guildId);

            log.info("Plugin '{}' disabled for guild {}", pluginName, guildId);
            return ResponseEntity.ok(new SuccessResponse("Plugin '" + pluginName + "' disabled for this guild"));
        } catch (Exception e) {
            log.error("Error disabling plugin for guild", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("Failed to disable plugin for guild"));
        }
    }

    /**
     * Trigger async re-sync of slash commands for a guild.
     * This updates Discord's command list so disabled plugin commands disappear
     * and enabled ones appear — instantly, no waiting for global propagation.
     */
    private void syncGuildCommandsAsync(String guildId) {
        try {
            long guildIdLong = Long.parseLong(guildId);
            interactionManager.syncGuildCommands(guildIdLong)
                    .thenRun(() -> log.debug("Guild commands re-synced for {}", guildId))
                    .exceptionally(e -> {
                        log.warn("Failed to re-sync guild commands for {}: {}", guildId, e.getMessage());
                        return null;
                    });
        } catch (NumberFormatException e) {
            log.warn("Invalid guild ID for command sync: {}", guildId);
        }
    }

    /**
     * Error response DTO.
     */
    public static class ErrorResponse {
        private String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }

    /**
     * Success response DTO.
     */
    public static class SuccessResponse {
        private String message;

        public SuccessResponse(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

