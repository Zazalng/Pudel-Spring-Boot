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
package group.worldstandard.pudel.core.brain.memory;

import group.worldstandard.pudel.core.config.brain.PudelBrainConfig;
import group.worldstandard.pudel.core.service.GuildDataService;
import group.worldstandard.pudel.core.service.UserDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Manages dialogue history storage and retrieval for the reworked PudelBrain.
 * <p>
 * Stores conversation turns (user message + bot response) with:
 * - respond_to tracking (which message ID the bot responded to)
 * - attachment URLs (Discord CDN links from both user and bot messages)
 * - Proper schema isolation (guild vs user schemas)
 * <p>
 * Retrieval supports:
 * - Per-user history within a guild
 * - Per-channel history within a guild
 * - Per-user history in DMs
 * - Chronological ordering for LLM context
 */
@Component
public class DialogueHistoryManager {

    private static final Logger logger = LoggerFactory.getLogger(DialogueHistoryManager.class);

    private final PudelBrainConfig brainConfig;
    private final GuildDataService guildDataService;
    private final UserDataService userDataService;

    public DialogueHistoryManager(PudelBrainConfig brainConfig,
                                   GuildDataService guildDataService,
                                   UserDataService userDataService) {
        this.brainConfig = brainConfig;
        this.guildDataService = guildDataService;
        this.userDataService = userDataService;
    }

    /**
     * Store a dialogue exchange in the database.
     * <p>
     * Records the user's message, bot's response, and the message ID
     * that the bot was responding to (if any).
     *
     * @param userMessage   the user's message content
     * @param botResponse   the bot's response content
     * @param intent        the detected intent (optional)
     * @param userId        the user's Discord ID
     * @param channelId     the channel's Discord ID
     * @param isGuild       whether this is a guild message
     * @param targetId      guild ID or user ID
     * @param respondToMessageId the message ID the bot responded to (nullable)
     * @param userAttachmentUrls  attachment URLs from the user's message
     * @param botAttachmentUrls   attachment URLs from the bot's response
     * @return true if stored successfully
     */
    public boolean storeDialogue(String userMessage, String botResponse, String intent,
                                  long userId, long channelId, boolean isGuild, long targetId,
                                  Long respondToMessageId,
                                  List<String> userAttachmentUrls,
                                  List<String> botAttachmentUrls) {
        if (!brainConfig.getDialogueHistory().isEnabled()) {
            return false;
        }

        try {
            // Merge attachment URLs
            List<String> allAttachmentUrls = new ArrayList<>();
            if (userAttachmentUrls != null) {
                allAttachmentUrls.addAll(userAttachmentUrls);
            }
            if (botAttachmentUrls != null) {
                allAttachmentUrls.addAll(botAttachmentUrls);
            }

            if (isGuild) {
                storeGuildDialogue(targetId, userId, channelId, userMessage, botResponse,
                        intent, respondToMessageId, allAttachmentUrls);
            } else {
                storeUserDialogue(targetId, userMessage, botResponse, intent,
                        respondToMessageId, allAttachmentUrls);
            }

            logger.debug("Stored dialogue for {} {} (respond_to={})",
                    isGuild ? "guild" : "user", targetId, respondToMessageId);
            return true;

        } catch (Exception e) {
            logger.error("Error storing dialogue: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Store a simplified dialogue exchange (without respond_to or attachments).
     */
    public boolean storeDialogue(String userMessage, String botResponse, String intent,
                                  long userId, long channelId, boolean isGuild, long targetId) {
        return storeDialogue(userMessage, botResponse, intent, userId, channelId, isGuild, targetId,
                null, List.of(), List.of());
    }

    /**
     * Get recent dialogue history for a user, formatted for LLM context.
     * <p>
     * Returns the conversation history in chronological order (oldest first),
     * limited by the configured maximum.
     *
     * @param userId  the user's Discord ID
     * @param isGuild whether this is a guild context
     * @param targetId guild ID or user ID
     * @param limit   maximum number of turns to return
     * @return list of conversation turns in chronological order
     */
    public List<Map<String, Object>> getRecentHistory(long userId, boolean isGuild, long targetId, int limit) {
        if (!brainConfig.getDialogueHistory().isEnabled()) {
            return List.of();
        }

        try {
            List<Map<String, Object>> rawHistory;
            if (isGuild) {
                rawHistory = guildDataService.getRecentDialogue(targetId, userId, limit);
            } else {
                rawHistory = userDataService.getRecentDialogue(userId, limit);
            }

            // Reverse to chronological order (oldest first) for LLM context
            List<Map<String, Object>> chronological = new ArrayList<>(rawHistory);
            Collections.reverse(chronological);

            return chronological;

        } catch (Exception e) {
            logger.error("Error getting dialogue history: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get recent dialogue history for a channel.
     *
     * @param guildId   the guild ID
     * @param channelId the channel ID
     * @param limit     maximum number of entries
     * @return list of dialogue entries in chronological order
     */
    public List<Map<String, Object>> getChannelHistory(long guildId, long channelId, int limit) {
        if (!brainConfig.getDialogueHistory().isEnabled()) {
            return List.of();
        }

        try {
            List<Map<String, Object>> rawHistory = guildDataService.getChannelDialogue(guildId, channelId, limit);
            List<Map<String, Object>> chronological = new ArrayList<>(rawHistory);
            Collections.reverse(chronological);
            return chronological;

        } catch (Exception e) {
            logger.error("Error getting channel dialogue history: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get the maximum number of history entries to include in LLM context.
     */
    public int getMaxContextMessages() {
        return brainConfig.getCompletion().getMaxContextMessages();
    }

    // ===============================
    // Private Methods
    // ===============================

    private void storeGuildDialogue(long guildId, long userId, long channelId,
                                     String userMessage, String botResponse, String intent,
                                     Long respondToMessageId, List<String> attachmentUrls) {
        guildDataService.storeDialogue(guildId, userId, channelId, userMessage, botResponse,
                intent, respondToMessageId, attachmentUrls);
    }

    private void storeUserDialogue(long userId, String userMessage, String botResponse,
                                    String intent, Long respondToMessageId,
                                    List<String> attachmentUrls) {
        userDataService.storeDialogue(userId, userMessage, botResponse, intent,
                respondToMessageId, attachmentUrls);
    }
}
