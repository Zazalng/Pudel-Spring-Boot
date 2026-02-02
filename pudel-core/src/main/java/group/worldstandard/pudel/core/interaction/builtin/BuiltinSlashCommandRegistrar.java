/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
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

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.api.interaction.SlashCommandHandler;
import group.worldstandard.pudel.core.interaction.InteractionManagerImpl;

import java.util.List;

/**
 * Registers all built-in slash commands for Pudel core functionality.
 * These commands replace the deprecated standalone text commands.
 */
@Component
public class BuiltinSlashCommandRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(BuiltinSlashCommandRegistrar.class);
    private static final String BUILTIN_PLUGIN_ID = "pudel-core";

    private final InteractionManagerImpl interactionManager;
    private final List<SlashCommandHandler> slashCommandHandlers;

    public BuiltinSlashCommandRegistrar(InteractionManagerImpl interactionManager,
                                        SettingsSlashCommand settingsSlashCommand,
                                        AISlashCommand aiSlashCommand,
                                        ChannelSlashCommand channelSlashCommand,
                                        CommandManageSlashCommand commandManageSlashCommand) {
        this.interactionManager = interactionManager;
        this.slashCommandHandlers = List.of(
                settingsSlashCommand,
                aiSlashCommand,
                channelSlashCommand,
                commandManageSlashCommand
        );
    }

    @PostConstruct
    public void registerBuiltinCommands() {
        logger.info("Registering built-in slash commands...");

        int registered = 0;
        for (SlashCommandHandler handler : slashCommandHandlers) {
            if (interactionManager.registerSlashCommand(BUILTIN_PLUGIN_ID, handler)) {
                registered++;
                logger.debug("Registered slash command: /{}", handler.getCommandData().getName());
            }
        }

        logger.info("Registered {} built-in slash commands", registered);
    }
}
