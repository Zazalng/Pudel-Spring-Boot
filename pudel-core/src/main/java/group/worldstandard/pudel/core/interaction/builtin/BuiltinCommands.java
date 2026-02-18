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

import group.worldstandard.pudel.api.annotation.*;
import group.worldstandard.pudel.api.interaction.InteractionManager;
import group.worldstandard.pudel.api.interaction.SlashCommandHandler;
import group.worldstandard.pudel.core.command.CommandMetadataRegistry;
import group.worldstandard.pudel.core.command.CommandRegistry;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.service.GuildInitializationService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Built-in Pudel commands using the annotation-based system.
 * <p>
 * This demonstrates that even core commands follow the same pattern as plugins.
 * The core uses Spring's @Component for DI, but command registration uses @SlashCommand.
 * <p>
 * Note: For built-in commands, we use @Component instead of @Plugin because:
 * 1. They need Spring dependency injection
 * 2. They are not loaded from external JARs
 * 3. They are always enabled (no enable/disable lifecycle)
 */
@Component
@Plugin(
    name = "pudel-core",
    version = "2.0.0",
    author = "Pudel Team",
    description = "Built-in Pudel commands"
)
public class BuiltinCommands {

    private final GuildInitializationService guildInitializationService;
    private final CommandMetadataRegistry metadataRegistry;
    private final CommandRegistry commandRegistry;
    private final InteractionManager interactionManager;

    public BuiltinCommands(GuildInitializationService guildInitializationService,
                          CommandMetadataRegistry metadataRegistry,
                          CommandRegistry commandRegistry,
                          InteractionManager interactionManager) {
        this.guildInitializationService = guildInitializationService;
        this.metadataRegistry = metadataRegistry;
        this.commandRegistry = commandRegistry;
        this.interactionManager = interactionManager;
    }

    // =====================================================
    // Settings Commands
    // =====================================================

    @SlashCommand(
        name = "settings",
        description = "Configure Pudel's guild settings",
        permissions = {Permission.ADMINISTRATOR},
        subcommands = {
            @Subcommand(name = "view", description = "View current guild settings"),
            @Subcommand(
                name = "prefix",
                description = "Set the command prefix",
                options = @CommandOption(name = "prefix", description = "New prefix (max 5 characters)", required = true)
            ),
            @Subcommand(
                name = "verbosity",
                description = "Set message cleanup level",
                options = @CommandOption(
                    name = "level",
                    description = "Verbosity level",
                    type = "INTEGER",
                    required = true,
                    choices = {
                        @Choice(name = "Delete all command messages", value = "1"),
                        @Choice(name = "Keep messages with pings", value = "2"),
                        @Choice(name = "Keep all messages", value = "3")
                    }
                )
            ),
            @Subcommand(
                name = "cooldown",
                description = "Set command cooldown",
                options = @CommandOption(name = "seconds", description = "Cooldown in seconds (0 to disable)", type = "NUMBER", required = true)
            ),
            @Subcommand(
                name = "logchannel",
                description = "Set command log channel",
                options = @CommandOption(name = "channel", description = "Log channel (leave empty to disable)", type = "CHANNEL")
            ),
            @Subcommand(
                name = "botchannel",
                description = "Restrict bot to a specific channel",
                options = @CommandOption(name = "channel", description = "Bot channel (leave empty for all)", type = "CHANNEL")
            )
        }
    )
    public void handleSettings(SlashCommandInteractionEvent event) {
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
            case "view" -> handleSettingsView(event, settings);
            case "prefix" -> handleSettingsPrefix(event, settings, guildId);
            case "verbosity" -> handleSettingsVerbosity(event, settings, guildId);
            case "cooldown" -> handleSettingsCooldown(event, settings, guildId);
            case "logchannel" -> handleSettingsLogChannel(event, settings, guildId);
            case "botchannel" -> handleSettingsBotChannel(event, settings, guildId);
            default -> event.reply("❌ Unknown subcommand").setEphemeral(true).queue();
        }
    }

    private void handleSettingsView(SlashCommandInteractionEvent event, GuildSettings settings) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚙️ Guild Settings")
                .setColor(Color.BLUE)
                .addField("Prefix", "`" + settings.getPrefix() + "`", true)
                .addField("Verbosity", String.valueOf(settings.getVerbosity()), true)
                .addField("Cooldown", settings.getCooldown() + "s", true)
                .addField("Log Channel", settings.getLogChannel() != null ? "<#" + settings.getLogChannel() + ">" : "None", true)
                .addField("Bot Channel", settings.getBotChannel() != null ? "<#" + settings.getBotChannel() + ">" : "All channels", true)
                .addField("AI Enabled", Boolean.TRUE.equals(settings.getAiEnabled()) ? "✅ Yes" : "❌ No", true);

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleSettingsPrefix(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        OptionMapping opt = event.getOption("prefix");
        if (opt == null) {
            event.reply("❌ Prefix is required").setEphemeral(true).queue();
            return;
        }

        String newPrefix = opt.getAsString();
        if (newPrefix.length() > 5) {
            event.reply("❌ Prefix must be 5 characters or less").setEphemeral(true).queue();
            return;
        }

        settings.setPrefix(newPrefix);
        guildInitializationService.updateGuildSettings(guildId, settings);
        event.reply("✅ Command prefix set to `" + newPrefix + "`").queue();
    }

    private void handleSettingsVerbosity(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        OptionMapping opt = event.getOption("level");
        if (opt == null) {
            event.reply("❌ Level is required").setEphemeral(true).queue();
            return;
        }

        int level = opt.getAsInt();
        settings.setVerbosity(level);
        guildInitializationService.updateGuildSettings(guildId, settings);

        String levelDesc = switch (level) {
            case 1 -> "Delete all command messages";
            case 2 -> "Keep messages with pings";
            case 3 -> "Keep all messages";
            default -> "Unknown";
        };
        event.reply("✅ Verbosity set to **" + level + "** (" + levelDesc + ")").queue();
    }

    private void handleSettingsCooldown(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        OptionMapping opt = event.getOption("seconds");
        if (opt == null) {
            event.reply("❌ Seconds is required").setEphemeral(true).queue();
            return;
        }

        double seconds = opt.getAsDouble();
        settings.setCooldown((float) seconds);
        guildInitializationService.updateGuildSettings(guildId, settings);

        if (seconds == 0) {
            event.reply("✅ Command cooldown disabled").queue();
        } else {
            event.reply("✅ Command cooldown set to **" + seconds + "** seconds").queue();
        }
    }

    private void handleSettingsLogChannel(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        OptionMapping channelOption = event.getOption("channel");
        if (channelOption == null) {
            settings.setLogChannel(null);
            guildInitializationService.updateGuildSettings(guildId, settings);
            event.reply("✅ Log channel disabled").setEphemeral(true).queue();
        } else {
            GuildChannel channel = channelOption.getAsChannel();
            settings.setLogChannel(channel.getId());
            guildInitializationService.updateGuildSettings(guildId, settings);
            event.reply("✅ Log channel set to " + channel.getAsMention()).setEphemeral(true).queue();
        }
    }

    private void handleSettingsBotChannel(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        OptionMapping channelOption = event.getOption("channel");
        if (channelOption == null) {
            settings.setBotChannel(null);
            guildInitializationService.updateGuildSettings(guildId, settings);
            event.reply("✅ Bot can now respond in all channels").setEphemeral(true).queue();
        } else {
            GuildChannel channel = channelOption.getAsChannel();
            settings.setBotChannel(channel.getId());
            guildInitializationService.updateGuildSettings(guildId, settings);
            event.reply("✅ Bot restricted to " + channel.getAsMention()).setEphemeral(true).queue();
        }
    }

    // =====================================================
    // AI Commands
    // =====================================================

    @SlashCommand(
        name = "ai",
        description = "Configure Pudel's AI behavior",
        permissions = {Permission.ADMINISTRATOR},
        subcommands = {
            @Subcommand(name = "status", description = "View AI configuration"),
            @Subcommand(
                name = "toggle",
                description = "Enable or disable AI",
                options = @CommandOption(
                    name = "enabled",
                    description = "Enable AI?",
                    type = "BOOLEAN",
                    required = true
                )
            ),
            @Subcommand(
                name = "personality",
                description = "Set AI personality",
                options = @CommandOption(name = "personality", description = "Personality traits", required = true)
            ),
            @Subcommand(
                name = "biography",
                description = "Set AI biography/backstory",
                options = @CommandOption(name = "biography", description = "Biography text", required = true)
            ),
            @Subcommand(
                name = "nickname",
                description = "Set AI nickname",
                options = @CommandOption(name = "nickname", description = "Bot nickname in responses", required = true)
            )
        }
    )
    public void handleAI(SlashCommandInteractionEvent event) {
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
            case "status" -> handleAIStatus(event, settings);
            case "toggle" -> handleAIToggle(event, settings, guildId);
            case "personality" -> handleAIPersonality(event, settings, guildId);
            case "biography" -> handleAIBiography(event, settings, guildId);
            case "nickname" -> handleAINickname(event, settings, guildId);
            default -> event.reply("❌ Unknown subcommand").setEphemeral(true).queue();
        }
    }

    private void handleAIStatus(SlashCommandInteractionEvent event, GuildSettings settings) {
        boolean aiEnabled = Boolean.TRUE.equals(settings.getAiEnabled());
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤖 AI Configuration")
                .setColor(aiEnabled ? Color.GREEN : Color.RED)
                .addField("Status", aiEnabled ? "✅ Enabled" : "❌ Disabled", true)
                .addField("Nickname", settings.getNickname() != null ? settings.getNickname() : "Pudel", true)
                .addField("Language", settings.getLanguage() != null ? settings.getLanguage() : "en", true)
                .addField("Personality", settings.getPersonality() != null ? settings.getPersonality() : "Not set", false)
                .addField("Biography", settings.getBiography() != null ?
                    (settings.getBiography().length() > 100 ? settings.getBiography().substring(0, 100) + "..." : settings.getBiography())
                    : "Not set", false);

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleAIToggle(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        OptionMapping opt = event.getOption("enabled");
        if (opt == null) {
            event.reply("❌ Enabled flag is required").setEphemeral(true).queue();
            return;
        }

        boolean enabled = opt.getAsBoolean();
        settings.setAiEnabled(enabled);
        guildInitializationService.updateGuildSettings(guildId, settings);
        event.reply(enabled ? "✅ AI is now **enabled**" : "❌ AI is now **disabled**").setEphemeral(true).queue();
    }

    private void handleAIPersonality(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        OptionMapping opt = event.getOption("personality");
        if (opt == null) {
            event.reply("❌ Personality is required").setEphemeral(true).queue();
            return;
        }

        String personality = opt.getAsString();
        settings.setPersonality(personality);
        guildInitializationService.updateGuildSettings(guildId, settings);
        event.reply("✅ AI personality updated").setEphemeral(true).queue();
    }

    private void handleAIBiography(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        OptionMapping opt = event.getOption("biography");
        if (opt == null) {
            event.reply("❌ Biography is required").setEphemeral(true).queue();
            return;
        }

        String biography = opt.getAsString();
        settings.setBiography(biography);
        guildInitializationService.updateGuildSettings(guildId, settings);
        event.reply("✅ AI biography updated").setEphemeral(true).queue();
    }

    private void handleAINickname(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        OptionMapping opt = event.getOption("nickname");
        if (opt == null) {
            event.reply("❌ Nickname is required").setEphemeral(true).queue();
            return;
        }

        String nickname = opt.getAsString();
        settings.setNickname(nickname);
        guildInitializationService.updateGuildSettings(guildId, settings);
        event.reply("✅ Bot nickname set to **" + nickname + "**").queue();
    }

    // =====================================================
    // Channel Commands
    // =====================================================

    @SlashCommand(
        name = "channel",
        description = "Manage channel settings",
        permissions = {Permission.ADMINISTRATOR},
        subcommands = {
            @Subcommand(
                name = "ignore",
                description = "Add channel to ignore list",
                options = @CommandOption(name = "channel", description = "Channel to ignore", type = "CHANNEL", required = true)
            ),
            @Subcommand(
                name = "unignore",
                description = "Remove channel from ignore list",
                options = @CommandOption(name = "channel", description = "Channel to unignore", type = "CHANNEL", required = true)
            ),
            @Subcommand(name = "list", description = "List ignored channels")
        }
    )
    public void handleChannel(SlashCommandInteractionEvent event) {
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
            case "ignore" -> {
                OptionMapping opt = event.getOption("channel");
                if (opt == null) {
                    event.reply("❌ Channel is required").setEphemeral(true).queue();
                    return;
                }
                GuildChannel channel = opt.getAsChannel();
                Set<String> ignored = getIgnoredChannelSet(settings);
                ignored.add(channel.getId());
                settings.setIgnoredChannels(String.join(",", ignored));
                guildInitializationService.updateGuildSettings(guildId, settings);
                event.reply("✅ Now ignoring " + channel.getAsMention()).queue();
            }
            case "unignore" -> {
                OptionMapping opt = event.getOption("channel");
                if (opt == null) {
                    event.reply("❌ Channel is required").setEphemeral(true).queue();
                    return;
                }
                GuildChannel channel = opt.getAsChannel();
                Set<String> ignored = getIgnoredChannelSet(settings);
                ignored.remove(channel.getId());
                settings.setIgnoredChannels(ignored.isEmpty() ? null : String.join(",", ignored));
                guildInitializationService.updateGuildSettings(guildId, settings);
                event.reply("✅ No longer ignoring " + channel.getAsMention()).queue();
            }
            case "list" -> {
                Set<String> ignored = getIgnoredChannelSet(settings);
                if (ignored.isEmpty()) {
                    event.reply("📋 No channels are being ignored").setEphemeral(true).queue();
                } else {
                    StringBuilder sb = new StringBuilder("📋 **Ignored Channels:**\n");
                    for (String channelId : ignored) {
                        sb.append("• <#").append(channelId).append(">\n");
                    }
                    event.reply(sb.toString()).setEphemeral(true).queue();
                }
            }
            default -> event.reply("❌ Unknown subcommand").setEphemeral(true).queue();
        }
    }

    private Set<String> getIgnoredChannelSet(GuildSettings settings) {
        String ignored = settings.getIgnoredChannels();
        if (ignored == null || ignored.isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(ignored.split(","))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    // =====================================================
    // Command Management
    // =====================================================

    @SlashCommand(
        name = "command",
        description = "Manage text commands",
        permissions = {Permission.ADMINISTRATOR},
        subcommands = {
            @Subcommand(
                name = "disable",
                description = "Disable a text command",
                options = @CommandOption(name = "command", description = "Command name to disable", required = true)
            ),
            @Subcommand(
                name = "enable",
                description = "Enable a text command",
                options = @CommandOption(name = "command", description = "Command name to enable", required = true)
            ),
            @Subcommand(name = "list", description = "List disabled commands")
        }
    )
    public void handleCommand(SlashCommandInteractionEvent event) {
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
            case "disable" -> {
                OptionMapping opt = event.getOption("command");
                if (opt == null) {
                    event.reply("❌ Command name is required").setEphemeral(true).queue();
                    return;
                }
                String cmd = opt.getAsString().toLowerCase();
                Set<String> disabled = getDisabledCommandSet(settings);
                disabled.add(cmd);
                settings.setDisabledCommands(String.join(",", disabled));
                guildInitializationService.updateGuildSettings(guildId, settings);
                event.reply("✅ Command `" + cmd + "` has been disabled").queue();
            }
            case "enable" -> {
                OptionMapping opt = event.getOption("command");
                if (opt == null) {
                    event.reply("❌ Command name is required").setEphemeral(true).queue();
                    return;
                }
                String cmd = opt.getAsString().toLowerCase();
                Set<String> disabled = getDisabledCommandSet(settings);
                disabled.remove(cmd);
                settings.setDisabledCommands(disabled.isEmpty() ? null : String.join(",", disabled));
                guildInitializationService.updateGuildSettings(guildId, settings);
                event.reply("✅ Command `" + cmd + "` has been enabled").queue();
            }
            case "list" -> {
                Set<String> disabled = getDisabledCommandSet(settings);
                if (disabled.isEmpty()) {
                    event.reply("📋 No commands are disabled").setEphemeral(true).queue();
                } else {
                    StringBuilder sb = new StringBuilder("📋 **Disabled Commands:**\n");
                    for (String cmd : disabled) {
                        sb.append("• `").append(cmd).append("`\n");
                    }
                    event.reply(sb.toString()).setEphemeral(true).queue();
                }
            }
            default -> event.reply("❌ Unknown subcommand").setEphemeral(true).queue();
        }
    }

    private Set<String> getDisabledCommandSet(GuildSettings settings) {
        String disabled = settings.getDisabledCommands();
        if (disabled == null || disabled.isEmpty()) {
            return new HashSet<>();
        }
        return Arrays.stream(disabled.split(","))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    // =====================================================
    // Simple Commands
    // =====================================================

    @SlashCommand(name = "ping", description = "Check bot latency")
    public void handlePing(SlashCommandInteractionEvent event) {
        long gatewayPing = event.getJDA().getGatewayPing();
        event.reply("🏓 Pong! Gateway: **" + gatewayPing + "ms**").setEphemeral(true).queue();
    }

    @SlashCommand(name = "help", description = "Show available commands")
    public void handleHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📚 Pudel Commands")
                .setColor(Color.CYAN)
                .setDescription("Use `/command` to see options for each slash command.\n" +
                        "Use `!help [command]` for detailed text command info.");

        // Built-in slash commands section
        StringBuilder builtInSlash = new StringBuilder();
        StringBuilder pluginSlash = new StringBuilder();

        for (SlashCommandHandler handler : interactionManager.getAllSlashCommands()) {
            String name = handler.getCommandData().getName();
            String desc = handler.getCommandData().getDescription();
            // Truncate long descriptions
            if (desc.length() > 40) {
                desc = desc.substring(0, 37) + "...";
            }

            String line = "`/" + name + "` - " + desc + "\n";

            // Check if it's a plugin command
            var metadata = metadataRegistry.getSlashCommandMetadata(name);
            if (metadata.isPresent() && !metadata.get().isBuiltIn()) {
                pluginSlash.append(line);
            } else {
                builtInSlash.append(line);
            }
        }

        if (!builtInSlash.isEmpty()) {
            embed.addField("⚙️ Built-in Slash Commands", builtInSlash.toString(), false);
        }

        if (!pluginSlash.isEmpty()) {
            embed.addField("🔌 Plugin Slash Commands", pluginSlash.toString(), false);
        }

        // Text commands summary
        int textCmdCount = commandRegistry.getCommandCount();
        if (textCmdCount > 0) {
            String prefix = "!";
            if (event.isFromGuild() && event.getGuild() != null) {
                GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(event.getGuild().getId());
                prefix = settings.getPrefix() != null ? settings.getPrefix() : "!";
            }
            embed.addField("📝 Text Commands",
                    "**" + textCmdCount + "** text commands available.\n" +
                    "Use `" + prefix + "help` for the full list with pagination.", false);
        }

        embed.setFooter("Pudel v2.0.0 | Use !help for detailed command list");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
