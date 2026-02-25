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
package group.worldstandard.pudel.core.bootstrap;

import group.worldstandard.pudel.api.interaction.InteractionManager;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Bootstrap runner that syncs all registered slash commands to Discord after JDA is ready.
 * <p>
 * This runner executes after:
 * - SchemaBootstrapRunner (Order 10)
 * - PluginBootstrapRunner (default order)
 * - CommandBootstrapRunner (default order)
 * <p>
 * This ensures all built-in and plugin commands are registered before syncing.
 * <p>
 * Note: Global commands can take up to 1 hour to propagate in Discord.
 * Guild-specific commands are instant.
 */
@Component
@Order(100) // Run last after all commands are registered
public class SlashCommandSyncRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(SlashCommandSyncRunner.class);

    private final JDA jda;
    private final InteractionManager interactionManager;

    public SlashCommandSyncRunner(JDA jda, InteractionManager interactionManager) {
        this.jda = jda;
        this.interactionManager = interactionManager;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Starting slash command sync to Discord...");

        try {
            // Wait for JDA to be fully ready
            jda.awaitReady();
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for JDA to be ready", e);
            Thread.currentThread().interrupt();
            return;
        }

        // Log current registration stats
        InteractionManager.InteractionStats stats = interactionManager.getStats();
        logger.info("Registered interactions - Slash: {}, Context: {}, Button: {}, Select: {}, Modal: {}, AutoComplete: {}",
                stats.slashCommandCount(),
                stats.contextMenuCount(),
                stats.buttonHandlerCount(),
                stats.selectMenuHandlerCount(),
                stats.modalHandlerCount(),
                stats.autoCompleteHandlerCount());

        if (stats.slashCommandCount() == 0 && stats.contextMenuCount() == 0) {
            logger.info("No slash commands or context menus to sync");
            return;
        }

        // Sync all commands to Discord
        interactionManager.syncCommands()
                .thenRun(() -> {
                    logger.info("Successfully synced slash commands to Discord");
                    logger.info("Note: Global commands may take up to 1 hour to appear in Discord");
                })
                .exceptionally(e -> {
                    logger.error("Failed to sync slash commands to Discord: {}", e.getMessage(), e);
                    return null;
                });
    }
}
