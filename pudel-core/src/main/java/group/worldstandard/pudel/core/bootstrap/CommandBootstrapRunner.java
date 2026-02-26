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

import group.worldstandard.pudel.core.command.CommandRegistry;
import group.worldstandard.pudel.core.command.builtin.AICommandHandler;
import group.worldstandard.pudel.core.command.builtin.HelpCommandHandler;
import group.worldstandard.pudel.core.command.builtin.PingCommandHandler;
import group.worldstandard.pudel.core.command.builtin.SettingsCommandHandler;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.core.service.GuildInitializationService;

/**
 * Bootstrap runner for initializing built-in commands and guild discovery on startup.
 * <p>
 * Note: Many standalone settings commands have been migrated to slash commands.
 * The following text commands are now deprecated and redirect to slash commands:
 * - prefix, verbosity, cooldown, logchannel, botchannel → /settings
 * - enable, disable → /command
 * - ignore, listen → /channel
 * <p>
 * Remaining text commands:
 * - help - Show help information
 * - ping - Check bot latency
 * - settings - Show overview + wizard (setup)
 * - ai - Show AI settings + wizard (setup) + multi-line text settings
 */
@Component
public class CommandBootstrapRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(CommandBootstrapRunner.class);

    private final CommandRegistry commandRegistry;
    private final SettingsCommandHandler settingsCommandHandler;
    private final HelpCommandHandler helpCommandHandler;
    private final PingCommandHandler pingCommandHandler;
    private final AICommandHandler aiCommandHandler;
    private final GuildInitializationService guildInitializationService;
    private final JDA jda;

    public CommandBootstrapRunner(CommandRegistry commandRegistry,
                                 SettingsCommandHandler settingsCommandHandler,
                                 HelpCommandHandler helpCommandHandler,
                                 PingCommandHandler pingCommandHandler,
                                 AICommandHandler aiCommandHandler,
                                 GuildInitializationService guildInitializationService,
                                 JDA jda) {
        this.commandRegistry = commandRegistry;
        this.settingsCommandHandler = settingsCommandHandler;
        this.helpCommandHandler = helpCommandHandler;
        this.pingCommandHandler = pingCommandHandler;
        this.aiCommandHandler = aiCommandHandler;
        this.guildInitializationService = guildInitializationService;
        this.jda = jda;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Initializing built-in commands...");

        // Register core text commands that remain useful
        // Wizards and multi-line text settings still work better as text commands
        commandRegistry.registerCommand("help", helpCommandHandler);
        commandRegistry.registerCommand("ping", pingCommandHandler);
        commandRegistry.registerCommand("settings", settingsCommandHandler);
        commandRegistry.registerCommand("ai", aiCommandHandler);

        // Note: The following standalone commands have been migrated to slash commands:
        // /settings - prefix, verbosity, cooldown, logchannel, botchannel
        // /ai - enable, disable, nickname, language, length, formality, emotes, agent, tables, memories
        // /channel - ignore, unignore, listen, list, clear
        // /command - enable, disable, list

        logger.info("Registered {} built-in text commands", commandRegistry.getCommandCount());
        logger.info("Note: Additional functionality available via slash commands (/settings, /ai, /channel, /command)");

        // Initialize all existing guilds
        logger.info("Initializing {} guilds...", jda.getGuilds().size());
        jda.getGuilds().forEach(guild -> {
            try {
                guildInitializationService.initializeGuild(guild);
            } catch (Exception e) {
                logger.error("Error initializing guild {}: {}", guild.getId(), e.getMessage());
            }
        });

        logger.info("Command bootstrap completed");
    }
}
