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
package group.worldstandard.pudel.core.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.core.config.plugins.PluginProperties;
import group.worldstandard.pudel.core.database.PluginDatabaseService;
import group.worldstandard.pudel.core.service.PluginService;
import group.worldstandard.pudel.core.service.PluginWatcherService;

/**
 * Bootstrap runner for initializing the bot on startup.
 */
@Component
public class PluginBootstrapRunner implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(PluginBootstrapRunner.class);

    private final PluginService pluginService;
    private final PluginProperties pluginProperties;
    private final PluginDatabaseService databaseService;
    private final PluginWatcherService pluginWatcherService;

    public PluginBootstrapRunner(PluginService pluginService, PluginProperties pluginProperties,
                                 PluginDatabaseService databaseService,
                                 PluginWatcherService pluginWatcherService) {
        this.pluginService = pluginService;
        this.pluginProperties = pluginProperties;
        this.databaseService = databaseService;
        this.pluginWatcherService = pluginWatcherService;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Starting Pudel plugin bootstrap...");

        // Run database migration to normalize existing plugin IDs
        // This fixes Issue #8 where plugins got new database prefixes after reboot
        try {
            databaseService.migrateToNormalizedIds();
        } catch (Exception e) {
            logger.error("Failed to migrate plugin database registrations: {}", e.getMessage(), e);
            // Continue anyway - new plugins will work correctly
        }

        if (pluginProperties.isEnableAutoDiscovery()) {
            logger.info("Auto-discovery enabled, scanning for plugins...");
            pluginService.discoverPlugins();

            // Sync the watcher service with already-loaded plugins so it doesn't
            // re-load them as "new" on the next scheduled poll
            pluginWatcherService.syncLoadedPlugins();
        } else {
            logger.info("Auto-discovery disabled");
        }

        logger.info("Plugin bootstrap completed");
    }
}