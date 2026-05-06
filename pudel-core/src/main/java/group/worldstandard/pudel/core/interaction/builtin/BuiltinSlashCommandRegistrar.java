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
package group.worldstandard.pudel.core.interaction.builtin;

import group.worldstandard.pudel.api.PluginInfo;
import group.worldstandard.pudel.api.agent.AgentToolRegistry;
import group.worldstandard.pudel.core.plugin.PluginAnnotationProcessor;
import group.worldstandard.pudel.core.plugin.PluginContextFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registers all built-in commands (slash, text, and agent tools) for Pudel core functionality.
 * <p>
 * Uses the same annotation processor and agent tool registry as plugins,
 * ensuring the core follows the same patterns as the plugin API.
 */
@Component
public class BuiltinSlashCommandRegistrar {
    private static final Logger logger = LoggerFactory.getLogger(BuiltinSlashCommandRegistrar.class);

    private final PluginAnnotationProcessor annotationProcessor;
    private final PluginContextFactory pluginContextFactory;
    private final BuiltinCommands builtinCommands;
    private final BuiltinTextCommands builtinTextCommands;
    private final BuiltinAgentTools builtinAgentTools;
    private final AgentToolRegistry agentToolRegistry;

    public BuiltinSlashCommandRegistrar(PluginAnnotationProcessor annotationProcessor,
                                        PluginContextFactory pluginContextFactory,
                                        BuiltinCommands builtinCommands,
                                        BuiltinTextCommands builtinTextCommands,
                                        BuiltinAgentTools builtinAgentTools,
                                        AgentToolRegistry agentToolRegistry) {
        this.annotationProcessor = annotationProcessor;
        this.pluginContextFactory = pluginContextFactory;
        this.builtinCommands = builtinCommands;
        this.builtinTextCommands = builtinTextCommands;
        this.builtinAgentTools = builtinAgentTools;
        this.agentToolRegistry = agentToolRegistry;
    }

    @PostConstruct
    public void registerBuiltinCommands() {
        logger.info("Registering built-in commands using annotation processor...");

        // Extract PluginInfo from @Plugin annotations on built-in classes
        PluginInfo slashInfo = annotationProcessor.extractPluginInfo(BuiltinCommands.class);
        PluginInfo textInfo = annotationProcessor.extractPluginInfo(BuiltinTextCommands.class);

        // Register slash commands (settings, ping, help)
        // Built-in commands use empty dbPrefix — they are core commands
        // and do not need database-based collision namespacing.
        int registered = annotationProcessor.processAndRegister(
                slashInfo.getName(),
                builtinCommands,
                pluginContextFactory.getContext(slashInfo),
                ""
        );

        logger.info("Registered {} built-in slash commands via annotations", registered);

        // Register text commands (ping, help)
        int textRegistered = annotationProcessor.processAndRegister(
                textInfo.getName(),
                builtinTextCommands,
                pluginContextFactory.getContext(textInfo),
                ""
        );

        logger.info("Registered {} built-in text commands via annotations", textRegistered);

        // Register built-in agent tools (data management, memory, etc.)
        int toolsRegistered = agentToolRegistry.registerProvider(
                BuiltinAgentTools.PLUGIN_ID,
                builtinAgentTools
        );

        logger.info("Registered {} built-in agent tools via AgentToolRegistry", toolsRegistered);

        // Sync slash commands to Discord
        annotationProcessor.syncCommands();
    }
}