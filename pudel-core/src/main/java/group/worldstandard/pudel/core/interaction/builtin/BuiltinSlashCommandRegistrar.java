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
package group.worldstandard.pudel.core.interaction.builtin;

import group.worldstandard.pudel.core.plugin.PluginAnnotationProcessor;
import group.worldstandard.pudel.core.plugin.PluginContextFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registers all built-in slash commands for Pudel core functionality.
 * <p>
 * Uses the same annotation processor as plugins, demonstrating that
 * the core follows the same patterns as the plugin API.
 */
@Component
public class BuiltinSlashCommandRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(BuiltinSlashCommandRegistrar.class);
    private static final String BUILTIN_PLUGIN_ID = "pudel-core";

    private final PluginAnnotationProcessor annotationProcessor;
    private final PluginContextFactory pluginContextFactory;
    private final BuiltinCommands builtinCommands;

    public BuiltinSlashCommandRegistrar(PluginAnnotationProcessor annotationProcessor,
                                        PluginContextFactory pluginContextFactory,
                                        BuiltinCommands builtinCommands) {
        this.annotationProcessor = annotationProcessor;
        this.pluginContextFactory = pluginContextFactory;
        this.builtinCommands = builtinCommands;
    }

    @PostConstruct
    public void registerBuiltinCommands() {
        logger.info("Registering built-in slash commands using annotation processor...");

        // Use the same annotation processor as plugins
        int registered = annotationProcessor.processAndRegister(
                BUILTIN_PLUGIN_ID,
                builtinCommands,
                pluginContextFactory.getContext(BUILTIN_PLUGIN_ID)
        );

        logger.info("Registered {} built-in commands via annotations", registered);

        // Sync to Discord
        annotationProcessor.syncCommands();
    }
}
