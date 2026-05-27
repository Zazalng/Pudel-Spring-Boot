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
package group.worldstandard.pudel.core.discord;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.messages.MessageSnapshot;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.api.command.TextCommandHandler;
import group.worldstandard.pudel.core.brain.PudelBrain;
import group.worldstandard.pudel.core.brain.context.PassiveContextProcessor;
import group.worldstandard.pudel.core.command.CommandContextImpl;
import group.worldstandard.pudel.core.command.CommandMetadataRegistry;
import group.worldstandard.pudel.core.command.CommandRegistry;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.event.PluginEventManager;
import group.worldstandard.pudel.core.service.CommandExecutionService;
import group.worldstandard.pudel.core.service.GuildInitializationService;
import group.worldstandard.pudel.core.service.GuildSettingsService;
import group.worldstandard.pudel.core.util.DiscordMessageParser;
import group.worldstandard.pudel.core.util.DiscordMessageParser.ParseResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens to Discord events and dispatches commands.
 * Also dispatches events to plugin event handlers.
 */
@Component
public class DiscordEventListener extends ListenerAdapter {
     private static final Logger logger = LoggerFactory.getLogger(DiscordEventListener.class);

     // Forward context cache - stores forwarded message content temporarily
     // Key: "userId:channelId", Value: ForwardContext with content and timestamp
     private static final long FORWARD_CONTEXT_TIMEOUT_MS = 30_000; // 30 seconds
     private final Map<String, ForwardContext> forwardContextCache = new ConcurrentHashMap<>();

     private final CommandRegistry commandRegistry;
     private final GuildInitializationService guildInitializationService;
     private final CommandExecutionService commandExecutionService;
     private final PluginEventManager pluginEventManager;
     private final CommandMetadataRegistry commandMetadataRegistry;
     private final GuildSettingsService guildSettingsService;
    private final PudelBrain pudelBrain;
    private final PassiveContextProcessor passiveContextProcessor;
    private final DiscordMessageParser messageParser;
    private static final String DEFAULT_PREFIX = "!";

    public DiscordEventListener(CommandRegistry commandRegistry,
                                 GuildInitializationService guildInitializationService,
                                 @Lazy CommandExecutionService commandExecutionService,
                                 PluginEventManager pluginEventManager,
                                 @Lazy CommandMetadataRegistry commandMetadataRegistry,
                                 GuildSettingsService guildSettingsService,
                                 PudelBrain pudelBrain,
                                 PassiveContextProcessor passiveContextProcessor,
                                 DiscordMessageParser messageParser) {
        this.commandRegistry = commandRegistry;
        this.guildInitializationService = guildInitializationService;
        this.commandExecutionService = commandExecutionService;
        this.pluginEventManager = pluginEventManager;
        this.commandMetadataRegistry = commandMetadataRegistry;
        this.guildSettingsService = guildSettingsService;
        this.pudelBrain = pudelBrain;
        this.passiveContextProcessor = passiveContextProcessor;
        this.messageParser = messageParser;
    }

    /**
     * Generic event handler that dispatches all events to plugin handlers.
     */
    @Override
    public void onGenericEvent(@NotNull GenericEvent event) {
        // Dispatch to plugin event handlers
        try {
            pluginEventManager.dispatchEvent(event);
        } catch (Exception e) {
            logger.error("Error dispatching event {} to plugins: {}",
                    event.getClass().getSimpleName(), e.getMessage(), e);
        }
    }

    // Guild join/leave events are handled by GuildEventListener
    // which provides more complete logic including command syncing,
    // guild record creation, and welcome messages.

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) {
            return;
        }

        String messageContent = event.getMessage().getContentRaw().trim();
        String selfId = event.getJDA().getSelfUser().getId();

        // Determine the command prefix
        String prefix = DEFAULT_PREFIX;
        GuildSettings settings = null;
        if (event.isFromGuild()) {
            settings = guildInitializationService.getOrCreateGuildSettings(event.getGuild().getId());
            prefix = settings.getPrefix() != null ? settings.getPrefix() : DEFAULT_PREFIX;

            // Check if channel is in ignored list
            if (isChannelIgnored(event.getChannel().getId(), settings)) {
                return; // Completely ignore this channel
            }
        }

        // Check if message starts with prefix (command mode)
        if (messageContent.startsWith(prefix)) {
            handleCommand(event, messageContent, prefix, settings);
            return;
        }

        // Check if message starts with @mention (alternative command mode)
        String mentionPrefix = "<@" + selfId + "> ";
        String mentionPrefixAlt = "<@!" + selfId + "> ";
        if (messageContent.startsWith(mentionPrefix) || messageContent.startsWith(mentionPrefixAlt)) {
            String withoutMention = messageContent.startsWith(mentionPrefix)
                    ? messageContent.substring(mentionPrefix.length()).trim()
                    : messageContent.substring(mentionPrefixAlt.length()).trim();
            if (!withoutMention.isEmpty()) {
                // Check if this looks like a command
                String[] parts = withoutMention.split("\\s+", 2);
                String potentialCommand = parts[0].toLowerCase();
                if (commandRegistry.hasCommand(potentialCommand)) {
                    handleCommand(event, prefix + withoutMention, prefix, settings);
                    return;
                }
            }
        }

        // Non-command message: check if bot is mentioned (by ID or by name/nickname)
        boolean isGuild = event.isFromGuild();
        long targetId = isGuild ? event.getGuild().getIdLong() : event.getAuthor().getIdLong();

        // Use DiscordMessageParser to detect mentions by ID, name, or nickname
        ParseResult parseResult = messageParser.parseMessage(event);
        boolean isMentionedById = parseResult.mentionsBotById();
        boolean isMentionedByName = parseResult.mentionsBotByName();
        boolean isReplyToBot = parseResult.isReplyToBot();

        // Check for @mention by ID (original check, kept for compatibility)
        boolean isMentionedByIdFallback = messageContent.contains("<@" + selfId + ">")
                || messageContent.contains("<@!" + selfId + ">");

        boolean isMentioned = isMentionedById || isMentionedByIdFallback || isMentionedByName || isReplyToBot;

        if (isMentioned) {
            // Bot is mentioned (by ID, name, nickname, or reply) - process with PudelBrain
            // Also include any recent passive context from the same channel (may be forwarded content)
            String recentContext = passiveContextProcessor.getRecentContextForChannel(
                    event.getChannel().getIdLong(), isGuild, targetId, 5);

            // Check for cached forward context from a previous forward message (avoids race condition)
            String cacheKey = event.getAuthor().getId() + ":" + event.getChannel().getId();
            String cachedForwardContext = getCachedForwardContext(cacheKey);

            // Prepend cached forward context to recent context if available
            if (cachedForwardContext != null && !cachedForwardContext.isEmpty()) {
                if (recentContext != null && !recentContext.isEmpty() && !recentContext.equals("No recent context available.")) {
                    recentContext = "[Forwarded message context: \"" + cachedForwardContext + "\"]\n\n" + recentContext;
                } else {
                    recentContext = "[Forwarded message context: \"" + cachedForwardContext + "\"]";
                }
                logger.debug("Included cached forward context for mention message");
            }

            pudelBrain.processMessageAsync(event, null, isGuild, targetId, recentContext);
        } else {
            // Not a command and not a mention - passively track context
            passiveContextProcessor.submit(event, targetId, isGuild);

            // Check if this message has forwarded content and cache it for follow-up mentions
            String forwardContent = extractForwardContent(event);
            if (forwardContent != null && !forwardContent.isEmpty()) {
                String cacheKey = event.getAuthor().getId() + ":" + event.getChannel().getId();
                forwardContextCache.put(cacheKey, new ForwardContext(forwardContent, Instant.now()));
                logger.debug("Cached forward content for user {} in channel {} (waiting for follow-up mention)",
                        event.getAuthor().getId(), event.getChannel().getId());
            }
        }
    }

    /**
     * Check if a channel is in the ignored list.
     */
    private boolean isChannelIgnored(String channelId, GuildSettings settings) {
        if (settings == null || settings.getIgnoredChannels() == null || settings.getIgnoredChannels().isEmpty()) {
            return false;
        }
        String[] ignored = settings.getIgnoredChannels().split(",");
        for (String ignoredId : ignored) {
            if (ignoredId.trim().equals(channelId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle command execution.
     */
    private void handleCommand(MessageReceivedEvent event, String messageContent, String prefix, GuildSettings settings) {
        // Remove prefix and parse command
        String withoutPrefix = messageContent.substring(prefix.length()).trim();

        if (withoutPrefix.isEmpty()) {
            return;
        }

        // Split into command and arguments
        String[] parts = withoutPrefix.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String argsString = parts.length > 1 ? parts[1] : "";
        String[] args = argsString.isEmpty() ? new String[0] : argsString.split("\\s+");

        // Dispatch to command handler
        TextCommandHandler handler = commandRegistry.getCommand(command);
        if (handler == null) {
            // Silently ignore unknown commands
            return;
        }

        // Check if command is disabled for this guild
        if (event.isFromGuild()) {
            GuildSettings guildSettings = settings != null ? settings :
                    guildInitializationService.getOrCreateGuildSettings(event.getGuild().getId());
            if (isCommandDisabled(command, guildSettings)) {
                event.getChannel().sendMessage("❌ The command `" + command + "` is disabled on this server.").queue();
                return;
            }

            // Check if the command's plugin is disabled for this guild
            var textMeta = commandMetadataRegistry.getTextCommandMetadata(command);
            if (textMeta.isPresent() && !textMeta.get().isBuiltIn()) {
                String pluginId = textMeta.get().pluginId();
                if (!guildSettingsService.isPluginEnabledForGuild(event.getGuild().getId(), pluginId)) {
                    event.getChannel().sendMessage("❌ The plugin providing this command is disabled on this server.").queue();
                    return;
                }
            }
        }

        // Check cooldown (skip for DM and for staff in guilds)
        if (event.isFromGuild()) {
            GuildSettings guildSettings = settings != null ? settings :
                    guildInitializationService.getOrCreateGuildSettings(event.getGuild().getId());
            Float cooldownSecs = guildSettings.getCooldown();

            if (cooldownSecs != null && cooldownSecs > 0) {
                // Check if user can bypass cooldown (has MANAGE_GUILD permission)
                if (!commandExecutionService.canBypassCooldown(event.getGuild(), event.getMember())) {
                    float remainingCooldown = commandExecutionService.getRemainingCooldown(
                            event.getGuild().getId(), event.getAuthor().getId());
                    if (remainingCooldown > 0) {
                        event.getChannel().sendMessage(
                                String.format("⏳ Please wait %.2f second(s) before using another command.", remainingCooldown)
                        ).queue();
                        return;
                    }
                }
            }
        }

        try {
            CommandContextImpl context = new CommandContextImpl(event, command, args, argsString);
            handler.handle(context);

            // Apply settings after successful command execution
            if (event.isFromGuild()) {
                GuildSettings guildSettings = settings != null ? settings :
                        guildInitializationService.getOrCreateGuildSettings(event.getGuild().getId());

                // Apply verbosity (handle message deletion)
                commandExecutionService.handleVerbosity(event.getMessage(), event.getGuild().getId(),
                        guildSettings.getVerbosity());

                // Log command
                commandExecutionService.sendCommandLog(event.getGuild(), command, argsString,
                        event.getAuthor().getId(), event.getAuthor().getName(),
                        event.getChannel(), true);

                // Apply cooldown
                Float cooldownSecs = guildSettings.getCooldown();
                if (cooldownSecs != null && cooldownSecs > 0 && !commandExecutionService.canBypassCooldown(event.getGuild(), event.getMember())) {
                    commandExecutionService.applyCooldown(event.getGuild().getId(),
                            event.getAuthor().getId(), cooldownSecs);
                }
            }
        } catch (Exception e) {
            logger.error("Error executing command '{}': {}", command, e.getMessage(), e);
            try {
                event.getChannel().sendMessage("❌ An error occurred while executing the command.").queue();
                // Log failed command
                if (event.isFromGuild()) {
                    commandExecutionService.sendCommandLog(event.getGuild(), command, argsString,
                            event.getAuthor().getId(), event.getAuthor().getName(),
                            event.getChannel(), false);
                }
            } catch (Exception sendError) {
                logger.error("Failed to send error message: {}", sendError.getMessage());
            }
        }
    }

    /**
     * Check if a command is disabled for a guild.
     */
    private boolean isCommandDisabled(String command, GuildSettings settings) {
        if (settings == null || settings.getDisabledCommands() == null || settings.getDisabledCommands().isEmpty()) {
            return false;
        }

        String[] disabled = settings.getDisabledCommands().toLowerCase().split(",");
        for (String disabledCmd : disabled) {
            if (disabledCmd.trim().equals(command.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract forwarded message content from a message event.
     * Uses MessageSnapshot API (JDA 6.x) to access forwarded content.
     *
     * @param event The message event
     * @return The forwarded message content, or null if not a forward
     */
    private String extractForwardContent(MessageReceivedEvent event) {
        Message message = event.getMessage();
        StringBuilder forwardContent = new StringBuilder();

        // Check for message snapshots (Discord's native forward feature)
        try {
            List<MessageSnapshot> snapshots = message.getMessageSnapshots();
            if (snapshots != null && !snapshots.isEmpty()) {
                for (MessageSnapshot snapshot : snapshots) {
                    String content = snapshot.getContentRaw();
                    if (content != null && !content.isEmpty()) {
                        // Truncate very long messages to avoid token bloat
                        if (content.length() > 500) {
                            content = content.substring(0, 500) + "...";
                        }
                        if (!forwardContent.isEmpty()) {
                            forwardContent.append("\n---\n");
                        }
                        forwardContent.append(content);
                    }
                }
                if (!forwardContent.isEmpty()) {
                    logger.debug("Extracted {} forwarded message snapshot(s)", snapshots.size());
                    return forwardContent.toString();
                }
            }
        } catch (Exception e) {
            logger.debug("Error extracting message snapshots: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get cached forward context for a user+channel, if not expired.
     * This avoids the race condition where the mention message is processed
     * before the forwarded message's passive context is stored in the database.
     *
     * @param cacheKey The cache key (userId:channelId)
     * @return The cached forward content, or null if not found or expired
     */
    private String getCachedForwardContext(String cacheKey) {
        ForwardContext cached = forwardContextCache.get(cacheKey);
        if (cached == null) {
            return null;
        }

        // Check if expired
        if (Instant.now().toEpochMilli() - cached.timestamp().toEpochMilli() > FORWARD_CONTEXT_TIMEOUT_MS) {
            forwardContextCache.remove(cacheKey);
            logger.debug("Forward context expired for {}", cacheKey);
            return null;
        }

        // Remove after use (one-time context)
        forwardContextCache.remove(cacheKey);
        logger.debug("Retrieved and consumed cached forward context for {}", cacheKey);
        return cached.content();
    }

    /**
     * Forward context cache entry.
     * Stores the content of a forwarded message along with its timestamp.
     * Used to associate forward content with follow-up questions from the same user.
     */
    private record ForwardContext(
            String content,
            Instant timestamp
    ) {}
}

