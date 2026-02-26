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
package group.worldstandard.pudel.core.command.builtin;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.api.command.CommandContext;
import group.worldstandard.pudel.api.command.TextCommandHandler;
import group.worldstandard.pudel.core.command.CommandContextImpl;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.service.GuildInitializationService;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Handler for the !settings command.
 * Manages guild-level settings through text-based wizard.
 * <p>
 * Standalone settings (prefix, verbosity, cooldown, etc.) have been migrated to
 * slash commands (/settings). This handler now focuses on:
 * - !settings - Show settings overview and redirect to slash commands
 * - !settings setup/wizard - Start interactive configuration wizard (requires sequential text input)
 * <p>
 * AI personality settings are handled by AICommandHandler (!ai command).
 */
@Component
public class SettingsCommandHandler extends ListenerAdapter implements TextCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(SettingsCommandHandler.class);

    private final GuildInitializationService guildInitializationService;

    // Track active wizard sessions: guildId_userId -> WizardSession
    private final Map<String, SettingsWizardSession> activeWizards = new ConcurrentHashMap<>();

    public SettingsCommandHandler(GuildInitializationService guildInitializationService) {
        this.guildInitializationService = guildInitializationService;
    }

    @Override
    public void handle(CommandContext context) {
        if (!context.isFromGuild()) {
            context.getChannel().sendMessage("❌ This command only works in guilds!").queue();
            return;
        }

        // Check for admin permission
        if (!context.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            context.getChannel().sendMessage("❌ You need ADMINISTRATOR permission to use this command!").queue();
            return;
        }

        String guildId = context.getGuild().getId();
        GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(guildId);

        if (context.getArgs().length == 0) {
            // Show current settings overview
            showSettings(context, settings);
            return;
        }

        String subcommand = context.getArgs()[0].toLowerCase();

        switch (subcommand) {
            // Wizard - keep as text command (requires sequential input)
            case "setup", "wizard" -> startWizard(context, settings);

            // Redirect to slash commands for individual settings
            case "prefix", "verbosity", "cooldown", "logchannel", "botchannel" ->
                    redirectToSlashCommand(context, subcommand);

            default -> showHelp(context);
        }
    }

    private void redirectToSlashCommand(CommandContext context, String subcommand) {
        String slashCommand = switch (subcommand) {
            case "prefix" -> "`/settings prefix`";
            case "verbosity" -> "`/settings verbosity`";
            case "cooldown" -> "`/settings cooldown`";
            case "logchannel" -> "`/settings logchannel`";
            case "botchannel" -> "`/settings botchannel`";
            default -> "`/settings`";
        };

        context.getChannel().sendMessage(
                "💡 This setting has been migrated to slash commands for better experience.\n" +
                "Use " + slashCommand + " instead!"
        ).queue();
    }

    private void showSettings(CommandContext context, GuildSettings settings) {
        boolean aiEnabled = settings.getAiEnabled() != null ? settings.getAiEnabled() : true;
        String ignoredCount = "None";
        if (settings.getIgnoredChannels() != null && !settings.getIgnoredChannels().isEmpty()) {
            ignoredCount = settings.getIgnoredChannels().split(",").length + " channel(s)";
        }

        String nickname = settings.getNickname() != null ? settings.getNickname() : "Pudel";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚙️ Guild Settings")
                .setColor(new Color(114, 137, 218))
                .setDescription("""
                        Use `!settings setup` for first-time configuration wizard.
                        Use `/settings` slash commands for individual settings.
                        Use `!ai` to configure AI personality settings.""")
                .addField("Prefix", "`" + settings.getPrefix() + "`", true)
                .addField("Verbosity", getVerbosityDescription(settings.getVerbosity()), true)
                .addField("Cooldown", settings.getCooldown() + "s", true)
                .addField("Log Channel", settings.getLogChannel() != null ? "<#" + settings.getLogChannel() + ">" : "None", true)
                .addField("Bot Channel", settings.getBotChannel() != null ? "<#" + settings.getBotChannel() + ">" : "All channels", true)
                .addField("Ignored Channels", ignoredCount, true)
                .addField("AI Status", aiEnabled ? "✅ Enabled" : "❌ Disabled", true)
                .addField("Bot Name", nickname, true)
                .setFooter("!settings setup for wizard | /settings for quick changes | /channel for channel management");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private String getVerbosityDescription(int level) {
        return switch (level) {
            case 1 -> "Level 1 (Delete all)";
            case 2 -> "Level 2 (Keep pings)";
            case 3 -> "Level 3 (Keep all)";
            default -> "Level " + level;
        };
    }

    private void showHelp(CommandContext context) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚙️ Settings Command Help")
                .setColor(new Color(114, 137, 218))
                .setDescription("Configure guild-level settings for Pudel.")
                .addField("First-Time Setup", "`!settings setup` - Interactive configuration wizard", false)
                .addField("Quick Settings (Slash Commands)", """
                        `/settings view` - View current settings
                        `/settings prefix <prefix>` - Set command prefix
                        `/settings verbosity <1|2|3>` - Message cleanup level
                        `/settings cooldown <seconds>` - Command cooldown
                        `/settings logchannel [#channel]` - Command log channel
                        `/settings botchannel [#channel]` - Restrict to channel
                        """, false)
                .addField("Channel Management (Slash Commands)", """
                        `/channel ignore <#channel>` - Ignore a channel
                        `/channel unignore <#channel>` - Un-ignore a channel
                        `/channel listen [#channel]` - Set listen channel
                        `/channel list` - View channel config
                        `/channel clear` - Clear all restrictions
                        """, false)
                .addField("Command Management (Slash Commands)", """
                        `/command enable <name>` - Enable a command
                        `/command disable <name>` - Disable a command
                        `/command list` - View all commands
                        """, false)
                .addField("Related Commands", """
                        `!ai` - Configure AI personality settings
                        `!ai setup` - AI personality wizard
                        """, false)
                .setFooter("AI settings (biography, personality, etc.) are under !ai command");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    // ==================== WIZARD FUNCTIONALITY ====================

    private void startWizard(CommandContext context, GuildSettings settings) {
        String sessionKey = context.getGuild().getId() + "_" + context.getUser().getId();

        // Cancel any existing wizard
        activeWizards.remove(sessionKey);

        SettingsWizardSession session = new SettingsWizardSession(
                context.getGuild().getId(),
                context.getUser().getId(),
                context.getChannel().getId(),
                settings
        );
        activeWizards.put(sessionKey, session);

        // Welcome message
        EmbedBuilder welcome = new EmbedBuilder()
                .setTitle("⚙️ Pudel Setup Wizard")
                .setColor(new Color(114, 137, 218))
                .setDescription("""
                        Welcome to the Pudel setup wizard!
                        
                        I'll guide you through configuring the basic guild settings. \
                        For AI personality settings (biography, personality, etc.), use `!ai setup` after this wizard.
                        
                        Type your response or `skip` to keep current values. Type `cancel` to exit.""");

        context.getChannel().sendMessageEmbeds(welcome.build()).queue(msg -> {
            // Send first step
            sendWizardStep(context, session);
        });

        // Auto-cleanup after 10 minutes
        context.getChannel().sendMessage("").queueAfter(10, TimeUnit.MINUTES, msg -> activeWizards.remove(sessionKey), error -> {});
    }

    private void sendWizardStep(CommandContext context, SettingsWizardSession session) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(114, 137, 218))
                .setFooter("Type 'skip' to keep current | 'cancel' to exit | Step " + (session.currentStep + 1) + "/5");

        switch (session.currentStep) {
            case 0 -> embed.setTitle("📝 Step 1: Command Prefix")
                    .setDescription("What prefix should trigger my commands?\n\n" +
                            "**Current:** `" + session.settings.getPrefix() + "`\n\n" +
                            "Examples: `!`, `?`, `p!`, `>>`, `.`\n\nType your preferred prefix:");
            case 1 -> embed.setTitle("🔊 Step 2: Verbosity Level")
                    .setDescription("How should I handle command messages after execution?\n\n" +
                            "**Current:** Level " + session.settings.getVerbosity() + "\n\n" +
                            "**Options:**\n" +
                            "• `1` - Delete ALL command messages after execution\n" +
                            "• `2` - Delete commands UNLESS they ping someone (prevents ghost pings)\n" +
                            "• `3` - Keep all command messages (default)\n\nEnter 1, 2, or 3:");
            case 2 -> embed.setTitle("⏱️ Step 3: Command Cooldown")
                    .setDescription("How long should users wait between commands?\n\n" +
                            "**Current:** " + session.settings.getCooldown() + " seconds\n\n" +
                            "Enter seconds (0 to disable, e.g., `5` for 5 seconds).\n" +
                            "Staff with Manage Messages permission bypass cooldowns.");
            case 3 -> embed.setTitle("📋 Step 4: Log Channel")
                    .setDescription("Where should I log command usage?\n\n" +
                            "**Current:** " + (session.settings.getLogChannel() != null ? "<#" + session.settings.getLogChannel() + ">" : "Not set") +
                            "\n\nMention a channel (e.g., `#bot-logs`) or type `none` to disable:");
            case 4 -> embed.setTitle("🎯 Step 5: Bot Channel")
                    .setDescription("Should I only respond in a specific channel?\n\n" +
                            "**Current:** " + (session.settings.getBotChannel() != null ? "<#" + session.settings.getBotChannel() + ">" : "All channels") +
                            "\n\nMention a channel to restrict, or type `all` to respond everywhere:");
            default -> {
                // Wizard complete
                completeWizard(context, session);
                return;
            }
        }

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void completeWizard(CommandContext context, SettingsWizardSession session) {
        String sessionKey = session.guildId + "_" + session.userId;
        activeWizards.remove(sessionKey);

        // Save all settings
        guildInitializationService.updateGuildSettings(session.guildId, session.settings);

        boolean aiEnabled = session.settings.getAiEnabled() != null ? session.settings.getAiEnabled() : true;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ Setup Complete!")
                .setColor(new Color(67, 181, 129))
                .setDescription("""
                        Guild settings have been configured!
                        
                        **Next Steps:**
                        • Use `!ai setup` to configure my AI personality
                        • Use `!help` to see all available commands
                        • Use `/settings` to adjust settings quickly""")
                .addField("Prefix", "`" + session.settings.getPrefix() + "`", true)
                .addField("Verbosity", "Level " + session.settings.getVerbosity(), true)
                .addField("Cooldown", session.settings.getCooldown() + "s", true)
                .addField("Log Channel", session.settings.getLogChannel() != null ? "<#" + session.settings.getLogChannel() + ">" : "None", true)
                .addField("Bot Channel", session.settings.getBotChannel() != null ? "<#" + session.settings.getBotChannel() + ">" : "All", true)
                .addField("AI Status", aiEnabled ? "✅ Enabled" : "❌ Disabled", true);

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) return;

        String sessionKey = event.getGuild().getId() + "_" + event.getAuthor().getId();
        SettingsWizardSession session = activeWizards.get(sessionKey);

        if (session == null || !session.channelId.equals(event.getChannel().getId())) {
            return;
        }

        String input = event.getMessage().getContentRaw().trim();

        // Handle cancel
        if (input.equalsIgnoreCase("cancel")) {
            activeWizards.remove(sessionKey);
            event.getChannel().sendMessage("❌ Setup wizard cancelled. No changes were saved.").queue();
            return;
        }

        // Handle skip
        boolean skipped = input.equalsIgnoreCase("skip");

        if (!skipped) {
            // Process the input for current step
            switch (session.currentStep) {
                case 0 -> { // Prefix
                    if (input.length() > 5) {
                        event.getChannel().sendMessage("❌ Prefix must be 5 characters or less. Try again:").queue();
                        return;
                    }
                    session.settings.setPrefix(input);
                }
                case 1 -> { // Verbosity
                    try {
                        int level = Integer.parseInt(input);
                        if (level < 1 || level > 3) {
                            event.getChannel().sendMessage("❌ Enter 1, 2, or 3. Try again:").queue();
                            return;
                        }
                        session.settings.setVerbosity(level);
                    } catch (NumberFormatException e) {
                        event.getChannel().sendMessage("❌ Enter a number (1, 2, or 3). Try again:").queue();
                        return;
                    }
                }
                case 2 -> { // Cooldown
                    try {
                        float cooldown = Float.parseFloat(input);
                        if (cooldown < 0) {
                            event.getChannel().sendMessage("❌ Cooldown cannot be negative. Try again:").queue();
                            return;
                        }
                        session.settings.setCooldown(cooldown);
                    } catch (NumberFormatException e) {
                        event.getChannel().sendMessage("❌ Enter a number (e.g., 5 or 0). Try again:").queue();
                        return;
                    }
                }
                case 3 -> { // Log Channel
                    if (input.equalsIgnoreCase("none")) {
                        session.settings.setLogChannel(null);
                    } else if (!event.getMessage().getMentions().getChannels().isEmpty()) {
                        GuildChannel channel = event.getMessage().getMentions().getChannels().getFirst();
                        session.settings.setLogChannel(channel.getId());
                    } else {
                        event.getChannel().sendMessage("❌ Please mention a channel (e.g., #bot-logs) or type 'none'. Try again:").queue();
                        return;
                    }
                }
                case 4 -> { // Bot Channel
                    if (input.equalsIgnoreCase("all")) {
                        session.settings.setBotChannel(null);
                    } else if (!event.getMessage().getMentions().getChannels().isEmpty()) {
                        GuildChannel channel = event.getMessage().getMentions().getChannels().getFirst();
                        session.settings.setBotChannel(channel.getId());
                    } else {
                        event.getChannel().sendMessage("❌ Please mention a channel or type 'all'. Try again:").queue();
                        return;
                    }
                }
            }
        }

        // Move to next step
        session.currentStep++;

        // Create a context for sending the next step
        CommandContext mockContext = new CommandContextImpl(event, "settings", new String[0], "");

        sendWizardStep(mockContext, session);
    }

    // ==================== WIZARD SESSION CLASS ====================

    private static class SettingsWizardSession {
        final String guildId;
        final String userId;
        final String channelId;
        final GuildSettings settings;
        int currentStep = 0;

        SettingsWizardSession(String guildId, String userId, String channelId, GuildSettings settings) {
            this.guildId = guildId;
            this.userId = userId;
            this.channelId = channelId;
            this.settings = settings;
        }
    }
}
