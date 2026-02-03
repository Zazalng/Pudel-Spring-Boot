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
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.api.interaction.SlashCommandHandler;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.service.GuildInitializationService;

import java.awt.Color;

/**
 * Slash command handler for guild settings.
 * Replaces standalone text commands: prefix, verbosity, cooldown, logchannel, botchannel
 * <p>
 * Usage:
 * /settings view - Show current settings
 * /settings prefix <prefix> - Set command prefix
 * /settings verbosity <level> - Set verbosity level
 * /settings cooldown <seconds> - Set command cooldown
 * /settings logchannel [channel] - Set or clear log channel
 * /settings botchannel [channel] - Set or clear bot channel restriction
 *
 * @deprecated Use {@link BuiltinCommands} instead. This class will be removed in the next version.
 */
@Deprecated(since = "2.0.0", forRemoval = true)
@Component
public class SettingsSlashCommand implements SlashCommandHandler {

    private final GuildInitializationService guildInitializationService;

    public SettingsSlashCommand(GuildInitializationService guildInitializationService) {
        this.guildInitializationService = guildInitializationService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("settings", "Configure Pudel's guild settings")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addSubcommands(
                        new SubcommandData("view", "View current guild settings"),
                        new SubcommandData("prefix", "Set the command prefix")
                                .addOption(OptionType.STRING, "prefix", "New prefix (max 5 characters)", true),
                        new SubcommandData("verbosity", "Set message cleanup level")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "level", "Verbosity level", true)
                                                .addChoice("Delete all command messages", 1)
                                                .addChoice("Keep messages with pings", 2)
                                                .addChoice("Keep all messages", 3)
                                ),
                        new SubcommandData("cooldown", "Set command cooldown")
                                .addOption(OptionType.NUMBER, "seconds", "Cooldown in seconds (0 to disable)", true),
                        new SubcommandData("logchannel", "Set command log channel")
                                .addOption(OptionType.CHANNEL, "channel", "Log channel (leave empty to disable)", false),
                        new SubcommandData("botchannel", "Restrict bot to a specific channel")
                                .addOption(OptionType.CHANNEL, "channel", "Bot channel (leave empty for all channels)", false)
                );
    }

    @Override
    public boolean isGlobal() {
        // Guild-specific commands register instantly, global commands take up to 1 hour
        return false;
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
            case "view" -> handleView(event, settings);
            case "prefix" -> handlePrefix(event, settings, guildId);
            case "verbosity" -> handleVerbosity(event, settings, guildId);
            case "cooldown" -> handleCooldown(event, settings, guildId);
            case "logchannel" -> handleLogChannel(event, settings, guildId);
            case "botchannel" -> handleBotChannel(event, settings, guildId);
            default -> event.reply("❌ Unknown subcommand").setEphemeral(true).queue();
        }
    }

    private void handleView(SlashCommandInteractionEvent event, GuildSettings settings) {
        boolean aiEnabled = settings.getAiEnabled() != null ? settings.getAiEnabled() : true;
        String ignoredCount = "None";
        if (settings.getIgnoredChannels() != null && !settings.getIgnoredChannels().isEmpty()) {
            ignoredCount = settings.getIgnoredChannels().split(",").length + " channel(s)";
        }

        String nickname = settings.getNickname() != null ? settings.getNickname() : "Pudel";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚙️ Guild Settings")
                .setColor(new Color(114, 137, 218))
                .setDescription("Use `/settings <option>` to configure settings.\n" +
                        "Use `/ai` to configure AI personality settings.")
                .addField("Prefix", "`" + settings.getPrefix() + "`", true)
                .addField("Verbosity", getVerbosityDescription(settings.getVerbosity()), true)
                .addField("Cooldown", settings.getCooldown() + "s", true)
                .addField("Log Channel", settings.getLogChannel() != null ? "<#" + settings.getLogChannel() + ">" : "None", true)
                .addField("Bot Channel", settings.getBotChannel() != null ? "<#" + settings.getBotChannel() + ">" : "All channels", true)
                .addField("Ignored Channels", ignoredCount, true)
                .addField("AI Status", aiEnabled ? "✅ Enabled" : "❌ Disabled", true)
                .addField("Bot Name", nickname, true)
                .setFooter("Use text command !settings setup for setup wizard");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private String getVerbosityDescription(int level) {
        return switch (level) {
            case 1 -> "Level 1 (Delete all)";
            case 2 -> "Level 2 (Keep pings)";
            case 3 -> "Level 3 (Keep all)";
            default -> "Level " + level;
        };
    }

    private void handlePrefix(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        String newPrefix = event.getOption("prefix", OptionMapping::getAsString);

        if (newPrefix == null || newPrefix.isEmpty()) {
            event.reply("❌ Prefix cannot be empty!").setEphemeral(true).queue();
            return;
        }

        if (newPrefix.length() > 5) {
            event.reply("❌ Prefix must be 5 characters or less!").setEphemeral(true).queue();
            return;
        }

        settings.setPrefix(newPrefix);
        guildInitializationService.updateGuildSettings(guildId, settings);

        event.reply("✅ Prefix changed to: `" + newPrefix + "`").queue();
    }

    private void handleVerbosity(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        int level = event.getOption("level", 3, OptionMapping::getAsInt);

        settings.setVerbosity(level);
        guildInitializationService.updateGuildSettings(guildId, settings);

        String description = switch (level) {
            case 1 -> "Delete all command prompts after execution";
            case 2 -> "Delete prompts unless they ping someone";
            case 3 -> "Keep all command prompts (default)";
            default -> "";
        };

        event.reply("✅ Verbosity set to level " + level + ": " + description).queue();
    }

    private void handleCooldown(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        double cooldown = event.getOption("seconds", 0.0, OptionMapping::getAsDouble);

        if (cooldown < 0) {
            event.reply("❌ Cooldown cannot be negative!").setEphemeral(true).queue();
            return;
        }

        settings.setCooldown((float) cooldown);
        guildInitializationService.updateGuildSettings(guildId, settings);

        String message = cooldown == 0 ? "disabled" : cooldown + " seconds";
        event.reply("✅ Command cooldown set to: " + message +
                "\n*Note: Staff with MANAGE_GUILD permission can bypass cooldown*").queue();
    }

    private void handleLogChannel(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        GuildChannel channel = event.getOption("channel", OptionMapping::getAsChannel);

        if (channel == null) {
            settings.setLogChannel(null);
            guildInitializationService.updateGuildSettings(guildId, settings);
            event.reply("✅ Log channel disabled").queue();
        } else {
            settings.setLogChannel(channel.getId());
            guildInitializationService.updateGuildSettings(guildId, settings);
            event.reply("✅ Log channel set to: <#" + channel.getId() + ">").queue();
        }
    }

    private void handleBotChannel(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        GuildChannel channel = event.getOption("channel", OptionMapping::getAsChannel);

        if (channel == null) {
            settings.setBotChannel(null);
            guildInitializationService.updateGuildSettings(guildId, settings);
            event.reply("✅ Bot will respond in all channels").queue();
        } else {
            settings.setBotChannel(channel.getId());
            guildInitializationService.updateGuildSettings(guildId, settings);
            event.reply("✅ Bot channel set to: <#" + channel.getId() + ">").queue();
        }
    }

}
