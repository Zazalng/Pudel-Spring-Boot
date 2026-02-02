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

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.api.interaction.SlashCommandHandler;
import group.worldstandard.pudel.core.command.CommandRegistry;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.service.GuildInitializationService;

import java.awt.Color;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Slash command handler for command management (enable/disable).
 * Replaces standalone text commands: enable, disable
 * <p>
 * Usage:
 * /command enable <command> - Enable a disabled command
 * /command disable <command> - Disable a command
 * /command list - List all commands and their status
 */
@Component
public class CommandManageSlashCommand implements SlashCommandHandler {

    private static final Set<String> PROTECTED_COMMANDS = Set.of("help", "enable", "disable", "settings");

    private final GuildInitializationService guildInitializationService;
    private final CommandRegistry commandRegistry;

    public CommandManageSlashCommand(GuildInitializationService guildInitializationService,
                                     CommandRegistry commandRegistry) {
        this.guildInitializationService = guildInitializationService;
        this.commandRegistry = commandRegistry;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("command", "Manage command availability")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addSubcommands(
                        new SubcommandData("enable", "Enable a disabled command")
                                .addOption(OptionType.STRING, "name", "Command name to enable", true),
                        new SubcommandData("disable", "Disable a command")
                                .addOption(OptionType.STRING, "name", "Command name to disable", true),
                        new SubcommandData("list", "List all commands and their status")
                );
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("❌ This command only works in guilds!").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("❌ Invalid subcommand").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(guildId);

        switch (subcommand) {
            case "enable" -> handleEnable(event, settings, guildId);
            case "disable" -> handleDisable(event, settings, guildId);
            case "list" -> handleList(event, settings);
            default -> event.reply("❌ Unknown subcommand").setEphemeral(true).queue();
        }
    }

    private void handleEnable(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        String commandName = event.getOption("name", OptionMapping::getAsString);

        if (commandName == null || commandName.isEmpty()) {
            event.reply("❌ Please specify a command name!").setEphemeral(true).queue();
            return;
        }

        commandName = commandName.toLowerCase();

        // Check if command exists
        if (!commandRegistry.hasCommand(commandName)) {
            event.reply("❌ Unknown command: `" + commandName + "`").setEphemeral(true).queue();
            return;
        }

        Set<String> disabledCommands = getDisabledCommands(settings);

        if (!disabledCommands.contains(commandName)) {
            event.reply("⚠️ Command `" + commandName + "` is not disabled!").setEphemeral(true).queue();
            return;
        }

        disabledCommands.remove(commandName);
        settings.setDisabledCommands(disabledCommands.isEmpty() ? null : String.join(",", disabledCommands));
        guildInitializationService.updateGuildSettings(guildId, settings);

        event.reply("✅ Command `" + commandName + "` has been re-enabled.\nUsers can now use this command again.").queue();
    }

    private void handleDisable(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        String commandName = event.getOption("name", OptionMapping::getAsString);

        if (commandName == null || commandName.isEmpty()) {
            event.reply("❌ Please specify a command name!").setEphemeral(true).queue();
            return;
        }

        commandName = commandName.toLowerCase();

        // Check if command is protected
        if (PROTECTED_COMMANDS.contains(commandName)) {
            event.reply("❌ Cannot disable protected command: `" + commandName + "`\n" +
                    "Protected commands: " + String.join(", ", PROTECTED_COMMANDS)).setEphemeral(true).queue();
            return;
        }

        // Check if command exists
        if (!commandRegistry.hasCommand(commandName)) {
            event.reply("❌ Unknown command: `" + commandName + "`").setEphemeral(true).queue();
            return;
        }

        Set<String> disabledCommands = getDisabledCommands(settings);

        if (disabledCommands.contains(commandName)) {
            event.reply("⚠️ Command `" + commandName + "` is already disabled!").setEphemeral(true).queue();
            return;
        }

        disabledCommands.add(commandName);
        settings.setDisabledCommands(String.join(",", disabledCommands));
        guildInitializationService.updateGuildSettings(guildId, settings);

        event.reply("✅ Command `" + commandName + "` has been disabled.\n" +
                "Use `/command enable " + commandName + "` to re-enable it.").queue();
    }

    private void handleList(SlashCommandInteractionEvent event, GuildSettings settings) {
        Set<String> disabledCommands = getDisabledCommands(settings);
        Map<String, ?> allCommands = commandRegistry.getAllCommands();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 Command Status")
                .setColor(new Color(114, 137, 218));

        if (allCommands.isEmpty()) {
            embed.setDescription("No commands registered.");
        } else {
            StringBuilder enabled = new StringBuilder();
            StringBuilder disabled = new StringBuilder();
            StringBuilder protected_ = new StringBuilder();

            List<String> sortedCommands = new ArrayList<>(allCommands.keySet());
            Collections.sort(sortedCommands);

            for (String cmd : sortedCommands) {
                if (PROTECTED_COMMANDS.contains(cmd)) {
                    protected_.append("`").append(cmd).append("` ");
                } else if (disabledCommands.contains(cmd)) {
                    disabled.append("`").append(cmd).append("` ");
                } else {
                    enabled.append("`").append(cmd).append("` ");
                }
            }

            if (!protected_.isEmpty()) {
                embed.addField("🔒 Protected", protected_.toString().trim(), false);
            }
            if (!enabled.isEmpty()) {
                embed.addField("✅ Enabled", enabled.toString().trim(), false);
            }
            if (!disabled.isEmpty()) {
                embed.addField("❌ Disabled", disabled.toString().trim(), false);
            } else {
                embed.addField("❌ Disabled", "None", false);
            }
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private Set<String> getDisabledCommands(GuildSettings settings) {
        if (settings.getDisabledCommands() == null || settings.getDisabledCommands().isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(settings.getDisabledCommands().split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
