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
package group.worldstandard.pudel.core.agent.builtin;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import group.worldstandard.pudel.core.brain.PudelBrain;
import group.worldstandard.pudel.core.brain.context.PassiveContextEntry;
import group.worldstandard.pudel.core.brain.context.PassiveContextProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Built-in MCP tools for the reworked PudelBrain.
 * <p>
 * These tools are exposed to the LLM via LangChain4j's @Tool annotation
 * and provide access to:
 * <ul>
 *   <li>Passive context retrieval (recent messages in a channel)</li>
 *   <li>Dialogue history retrieval (conversation with a specific user)</li>
 *   <li>Specific message lookup by ID</li>
 *   <li>Forwarded message retrieval</li>
 * </ul>
 * <p>
 * All tools are read-only and scoped to the current guild/user context.
 * They are registered as MCP tools through the AgentToolRegistry.
 */
@Component
public class BuiltinMcpTools {

    private static final Logger logger = LoggerFactory.getLogger(BuiltinMcpTools.class);

    private final PudelBrain brain;

    public BuiltinMcpTools(PudelBrain brain) {
        this.brain = brain;
    }

    /**
     * Get recent passive context from the current channel.
     * <p>
     * Returns recent messages that Pudel has observed in the channel,
     * including extracted entities (mentions, emojis, URLs, attachments).
     *
     * @param channelId the channel ID to get context for
     * @param limit     maximum number of context entries to return (default 10)
     * @return formatted context string
     */
    @Tool("Get recent passive context from a channel. Returns observed messages with extracted entities. " +
          "Use this to understand what's been happening in the conversation.")
    public String getPassiveContext(
            @P("The channel ID to get context for") long channelId,
            @P("Maximum number of entries to return (default 10)") int limit) {

        try {
            // Determine if this is a guild or DM context from the channel
            boolean isGuild = true; // Default to guild; the brain will handle DM fallback
            long targetId = channelId; // Use channel as target for context lookup

            String context = brain.getPassiveContext(channelId, isGuild, targetId, limit);
            logger.debug("MCP tool getPassiveContext: channel={}, limit={}, result_length={}",
                    channelId, limit, context.length());
            return context;

        } catch (Exception e) {
            logger.error("Error in getPassiveContext tool: {}", e.getMessage());
            return "Error retrieving context: " + e.getMessage();
        }
    }

    /**
     * Get passive context with default limit.
     */
    @Tool("Get recent passive context from a channel with default limit of 10 entries.")
    public String getPassiveContext(
            @P("The channel ID to get context for") long channelId) {
        return getPassiveContext(channelId, 10);
    }

    /**
     * Get dialogue history with a specific user.
     * <p>
     * Returns the conversation history between the bot and a specific user,
     * including which messages the bot was responding to.
     *
     * @param userId  the user ID to get history for
     * @param isGuild whether this is a guild context (true) or DM (false)
     * @param targetId the guild ID or user ID for schema lookup
     * @param limit   maximum number of conversation turns to return (default 10)
     * @return formatted history string
     */
    @Tool("Get dialogue history with a specific user. Returns previous conversation turns " +
          "including which messages the bot was responding to. " +
          "Use this to understand the conversation flow with a user.")
    public String getDialogueHistory(
            @P("The user ID to get history for") long userId,
            @P("Whether this is a guild context (true) or DM (false)") boolean isGuild,
            @P("The guild ID or user ID for schema lookup") long targetId,
            @P("Maximum number of turns to return (default 10)") int limit) {

        try {
            String history = brain.getDialogueHistory(userId, isGuild, targetId, limit);
            logger.debug("MCP tool getDialogueHistory: user={}, isGuild={}, target={}, limit={}",
                    userId, isGuild, targetId, limit);
            return history;

        } catch (Exception e) {
            logger.error("Error in getDialogueHistory tool: {}", e.getMessage());
            return "Error retrieving history: " + e.getMessage();
        }
    }

    /**
     * Get dialogue history with default limit.
     */
    @Tool("Get dialogue history with a specific user (default 10 turns).")
    public String getDialogueHistory(
            @P("The user ID to get history for") long userId,
            @P("Whether this is a guild context (true) or DM (false)") boolean isGuild,
            @P("The guild ID or user ID for schema lookup") long targetId) {
        return getDialogueHistory(userId, isGuild, targetId, 10);
    }

    /**
     * Fetch a specific message by its ID from passive context.
     * <p>
     * Used when the LLM needs to reference a specific message that was
     * mentioned in the conversation.
     *
     * @param messageId the message ID to fetch
     * @param channelId the channel ID where the message was sent
     * @param isGuild   whether this is a guild context
     * @param targetId  the guild ID or user ID
     * @return the message content and metadata, or a not-found message
     */
    @Tool("Fetch a specific message from passive context by its message ID. " +
          "Use this when you need to reference a specific message mentioned in conversation.")
    public String getMessageById(
            @P("The message ID to fetch") long messageId,
            @P("The channel ID where the message was sent") long channelId,
            @P("Whether this is a guild context") boolean isGuild,
            @P("The guild ID or user ID") long targetId) {

        try {
            PassiveContextEntry entry = brain.fetchContextByMessageId(
                    messageId, channelId, isGuild, targetId);

            if (entry == null) {
                return "Message " + messageId + " not found in context. " +
                       "It may have expired or been too old.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Message ").append(entry.messageId()).append(":\n");
            sb.append("Author: <@").append(entry.userId()).append(">\n");
            sb.append("Content: ").append(entry.content()).append("\n");

            if (entry.replyToMessageId() != null) {
                sb.append("Reply to: ").append(entry.replyToMessageId()).append("\n");
            }

            if (!entry.attachmentUrls().isEmpty()) {
                sb.append("Attachments: ").append(String.join(", ", entry.attachmentUrls())).append("\n");
            }

            if (!entry.entities().isEmpty()) {
                sb.append("Entities: ").append(entry.entities()).append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            logger.error("Error in getMessageById tool: {}", e.getMessage());
            return "Error fetching message: " + e.getMessage();
        }
    }

    /**
     * Get forwarded message data from passive context.
     * <p>
     * Retrieves forwarded message references that were collected
     * when a message containing forwards was observed.
     *
     * @param messageId the message ID that contains forwarded content
     * @param channelId the channel ID
     * @param isGuild   whether this is a guild context
     * @param targetId  the guild ID or user ID
     * @return formatted forwarded message data
     */
    @Tool("Get forwarded message data from a specific message. " +
          "Use this to retrieve content from messages that were forwarded/quoted.")
    public String getForwardedMessages(
            @P("The message ID containing forwarded content") long messageId,
            @P("The channel ID") long channelId,
            @P("Whether this is a guild context") boolean isGuild,
            @P("The guild ID or user ID") long targetId) {

        try {
            PassiveContextEntry entry = brain.fetchContextByMessageId(
                    messageId, channelId, isGuild, targetId);

            if (entry == null) {
                return "Message " + messageId + " not found in context.";
            }

            List<PassiveContextEntry.ForwardedMessageRef> forwarded = entry.forwardedMessages();
            if (forwarded == null || forwarded.isEmpty()) {
                return "No forwarded messages found in message " + messageId;
            }

            StringBuilder sb = new StringBuilder("Forwarded messages:\n");
            for (PassiveContextEntry.ForwardedMessageRef fwd : forwarded) {
                sb.append("- ").append(fwd.content()).append("\n");
            }
            return sb.toString();

        } catch (Exception e) {
            logger.error("Error in getForwardedMessages tool: {}", e.getMessage());
            return "Error fetching forwarded messages: " + e.getMessage();
        }
    }

    /**
     * Get brain status information.
     * <p>
     * Returns the current status of the brain including Ollama availability
     * and passive context queue statistics.
     *
     * @return formatted status string
     */
    @Tool("Get the current status of PudelBrain including Ollama availability and context queue stats.")
    public String getBrainStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("PudelBrain v2 Status:\n");
        sb.append("- Ollama server: ").append(brain.isOllamaAvailable() ? "reachable" : "unreachable").append("\n");
        sb.append("- Model available: ").append(brain.isModelAvailable() ? "yes" : "no").append("\n");

        PassiveContextProcessor.QueueStats stats = brain.getPassiveContextStats();
        sb.append("- Context queue size: ").append(stats.queueSize()).append("\n");
        sb.append("- Total submitted: ").append(stats.totalSubmitted()).append("\n");
        sb.append("- Total processed: ").append(stats.totalProcessed()).append("\n");

        return sb.toString();
    }
}
