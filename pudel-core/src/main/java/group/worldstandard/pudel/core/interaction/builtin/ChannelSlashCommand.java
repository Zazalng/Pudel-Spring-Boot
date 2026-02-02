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
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.api.interaction.SlashCommandHandler;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.service.GuildInitializationService;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Slash command handler for channel management (ignore/listen).
 * Replaces standalone text commands: ignore, listen
 * <p>
 * Usage:
 * /channel ignore <channel> - Add channel to ignore list
 * /channel unignore <channel> - Remove channel from ignore list
 * /channel listen [channel] - Set or clear listen channel
 * /channel list - List all ignored and listen channels
 * /channel clear - Clear all channel restrictions
 */
@Component
public class ChannelSlashCommand implements SlashCommandHandler {

    private final GuildInitializationService guildInitializationService;

    public ChannelSlashCommand(GuildInitializationService guildInitializationService) {
        this.guildInitializationService = guildInitializationService;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("channel", "Manage channel restrictions for Pudel")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addSubcommands(
                        new SubcommandData("ignore", "Add channel to ignore list")
                                .addOption(OptionType.CHANNEL, "channel", "Channel to ignore", true),
                        new SubcommandData("unignore", "Remove channel from ignore list")
                                .addOption(OptionType.CHANNEL, "channel", "Channel to unignore", true),
                        new SubcommandData("listen", "Restrict bot to specific channel")
                                .addOption(OptionType.CHANNEL, "channel", "Channel to listen in (leave empty for all)", false),
                        new SubcommandData("list", "View channel configuration"),
                        new SubcommandData("clear", "Clear all channel restrictions")
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
            case "ignore" -> handleIgnore(event, settings, guildId);
            case "unignore" -> handleUnignore(event, settings, guildId);
            case "listen" -> handleListen(event, settings, guildId);
            case "list" -> handleList(event, settings);
            case "clear" -> handleClear(event, settings, guildId);
            default -> event.reply("❌ Unknown subcommand").setEphemeral(true).queue();
        }
    }

    private void handleIgnore(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        GuildChannel channel = event.getOption("channel", OptionMapping::getAsChannel);

        if (channel == null) {
            event.reply("❌ Please specify a channel!").setEphemeral(true).queue();
            return;
        }

        Set<String> ignoredChannels = getIgnoredChannelSet(settings);

        if (ignoredChannels.contains(channel.getId())) {
            event.reply("ℹ️ <#" + channel.getId() + "> is already in the ignore list.").setEphemeral(true).queue();
            return;
        }

        ignoredChannels.add(channel.getId());
        settings.setIgnoredChannels(String.join(",", ignoredChannels));
        guildInitializationService.updateGuildSettings(guildId, settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔇 Channel Ignored")
                .setColor(new Color(240, 71, 71))
                .setDescription("<#" + channel.getId() + "> has been added to the ignore list.\n\n" +
                        "Pudel will now **completely ignore** this channel:\n" +
                        "• No AI responses\n" +
                        "• No command execution\n" +
                        "• No passive context tracking");

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleUnignore(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        GuildChannel channel = event.getOption("channel", OptionMapping::getAsChannel);

        if (channel == null) {
            event.reply("❌ Please specify a channel!").setEphemeral(true).queue();
            return;
        }

        Set<String> ignoredChannels = getIgnoredChannelSet(settings);

        if (!ignoredChannels.contains(channel.getId())) {
            event.reply("ℹ️ <#" + channel.getId() + "> is not in the ignore list.").setEphemeral(true).queue();
            return;
        }

        ignoredChannels.remove(channel.getId());
        settings.setIgnoredChannels(ignoredChannels.isEmpty() ? null : String.join(",", ignoredChannels));
        guildInitializationService.updateGuildSettings(guildId, settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔊 Channel Un-ignored")
                .setColor(new Color(67, 181, 129))
                .setDescription("<#" + channel.getId() + "> has been removed from the ignore list.\n\n" +
                        "Pudel will now respond normally in this channel.");

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleListen(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        GuildChannel channel = event.getOption("channel", OptionMapping::getAsChannel);

        if (channel == null) {
            // Clear listen restriction
            if (settings.getBotChannel() == null || settings.getBotChannel().isEmpty()) {
                event.reply("ℹ️ Pudel is already listening in all channels.").setEphemeral(true).queue();
                return;
            }

            settings.setBotChannel(null);
            guildInitializationService.updateGuildSettings(guildId, settings);

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("👂 Listen Restriction Removed")
                    .setColor(new Color(67, 181, 129))
                    .setDescription("Pudel will now respond in **all channels** (except ignored ones).");

            event.replyEmbeds(embed.build()).queue();
        } else {
            settings.setBotChannel(channel.getId());
            guildInitializationService.updateGuildSettings(guildId, settings);

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("👂 Listen Channel Set")
                    .setColor(new Color(67, 181, 129))
                    .setDescription("Pudel will now **only** respond in <#" + channel.getId() + ">.\n\n" +
                            "**Note:** Direct `@Pudel` mentions will still work in other channels.");

            event.replyEmbeds(embed.build()).queue();
        }
    }

    private void handleList(SlashCommandInteractionEvent event, GuildSettings settings) {
        Set<String> ignoredChannels = getIgnoredChannelSet(settings);
        String botChannel = settings.getBotChannel();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 Channel Configuration")
                .setColor(new Color(114, 137, 218));

        // Listen channel
        if (botChannel != null && !botChannel.isEmpty()) {
            embed.addField("👂 Listen Channel", "<#" + botChannel + ">", false);
        } else {
            embed.addField("👂 Listen Channel", "All channels", false);
        }

        // Ignored channels
        if (ignoredChannels.isEmpty()) {
            embed.addField("🔇 Ignored Channels", "None", false);
        } else {
            StringBuilder channelList = new StringBuilder();
            for (String channelId : ignoredChannels) {
                GuildChannel channel = event.getGuild().getGuildChannelById(channelId);
                if (channel != null) {
                    channelList.append("<#").append(channelId).append(">\n");
                } else {
                    channelList.append("Unknown (`").append(channelId).append("`)\n");
                }
            }
            embed.addField("🔇 Ignored Channels (" + ignoredChannels.size() + ")", channelList.toString(), false);
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleClear(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        Set<String> ignoredChannels = getIgnoredChannelSet(settings);
        boolean hadBotChannel = settings.getBotChannel() != null && !settings.getBotChannel().isEmpty();
        boolean hadIgnored = !ignoredChannels.isEmpty();

        if (!hadBotChannel && !hadIgnored) {
            event.reply("ℹ️ No channel restrictions are currently set.").setEphemeral(true).queue();
            return;
        }

        settings.setBotChannel(null);
        settings.setIgnoredChannels(null);
        guildInitializationService.updateGuildSettings(guildId, settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🧹 Channel Restrictions Cleared")
                .setColor(new Color(67, 181, 129))
                .setDescription("""
                        All channel restrictions have been removed.
                        
                        Pudel will now respond in **all channels**.""");

        if (hadBotChannel) {
            embed.addField("Removed", "Listen channel restriction", true);
        }
        if (hadIgnored) {
            embed.addField("Cleared", ignoredChannels.size() + " ignored channel(s)", true);
        }

        event.replyEmbeds(embed.build()).queue();
    }

    private Set<String> getIgnoredChannelSet(GuildSettings settings) {
        if (settings.getIgnoredChannels() == null || settings.getIgnoredChannels().isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(settings.getIgnoredChannels().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
