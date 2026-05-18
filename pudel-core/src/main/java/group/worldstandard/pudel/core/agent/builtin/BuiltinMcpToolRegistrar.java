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

import group.worldstandard.pudel.api.agent.AgentTool;
import group.worldstandard.pudel.api.agent.AgentToolContext;
import group.worldstandard.pudel.api.agent.McpToolDefinition;
import group.worldstandard.pudel.api.agent.McpToolRegistry;
import group.worldstandard.pudel.core.brain.PudelBrain;
import group.worldstandard.pudel.core.brain.context.PassiveContextEntry;
import group.worldstandard.pudel.core.brain.context.PassiveContextProcessor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Registers BuiltinMcpTools as MCP tool definitions in the McpToolRegistry.
 * <p>
 * This bridges the LangChain4j @Tool annotated methods in BuiltinMcpTools
 * with the MCP tool registry, allowing the LLM to discover and invoke them
 * through the MCP protocol.
 * <p>
 * Each built-in tool is registered with:
 * - JSON Schema inputSchema for structured parameter definitions
 * - An executor that delegates to the appropriate BuiltinMcpTools method
 * - Proper metadata (name, description, keywords, permissions)
 */
@Component
public class BuiltinMcpToolRegistrar {

    private static final Logger logger = LoggerFactory.getLogger(BuiltinMcpToolRegistrar.class);

    private final McpToolRegistry mcpToolRegistry;
    private final PudelBrain brain;

    public BuiltinMcpToolRegistrar(McpToolRegistry mcpToolRegistry, PudelBrain brain) {
        this.mcpToolRegistry = mcpToolRegistry;
        this.brain = brain;
    }

    @PostConstruct
    public void registerBuiltinTools() {
        registerGetPassiveContext();
        registerGetDialogueHistory();
        registerGetMessageById();
        registerGetForwardedMessages();
        registerGetBrainStatus();

        logger.info("Registered {} built-in MCP tools", mcpToolRegistry.getToolCount());
    }

    /**
     * Register get_passive_context tool.
     * Retrieves recent passive context from a channel.
     */
    private void registerGetPassiveContext() {
        String schema = """
                {
                    "type": "object",
                    "properties": {
                        "channel_id": {
                            "type": "number",
                            "description": "The channel ID to get context for"
                        },
                        "is_guild": {
                            "type": "boolean",
                            "description": "Whether this is a guild context (true) or DM (false)",
                            "default": true
                        },
                        "target_id": {
                            "type": "number",
                            "description": "The guild ID or user ID for schema lookup"
                        },
                        "limit": {
                            "type": "integer",
                            "description": "Maximum number of entries to return",
                            "default": 10
                        }
                    },
                    "required": ["channel_id"]
                }
                """;

        mcpToolRegistry.registerTool("builtin", McpToolDefinition.builder()
                .name("get_passive_context")
                .description("Get recent passive context from a channel. Returns observed messages with extracted entities. " +
                        "Use this to understand what's been happening in the conversation.")
                .inputSchema(schema)
                .pluginId("builtin")
                .keywords(List.of("context", "history", "channel", "conversation", "recent"))
                .guildOnly(false)
                .dmOnly(false)
                .permission(group.worldstandard.pudel.api.agent.AgentTool.ToolPermission.EVERYONE)
                .priority(100)
                .executor(this::executeGetPassiveContext)
                .build());
    }

    /**
     * Register get_dialogue_history tool.
     * Retrieves conversation history with a specific user.
     */
    private void registerGetDialogueHistory() {
        String schema = """
                {
                    "type": "object",
                    "properties": {
                        "user_id": {
                            "type": "number",
                            "description": "The user ID to get history for"
                        },
                        "is_guild": {
                            "type": "boolean",
                            "description": "Whether this is a guild context (true) or DM (false)"
                        },
                        "target_id": {
                            "type": "number",
                            "description": "The guild ID or user ID for schema lookup"
                        },
                        "limit": {
                            "type": "integer",
                            "description": "Maximum number of conversation turns to return",
                            "default": 10
                        }
                    },
                    "required": ["user_id", "is_guild", "target_id"]
                }
                """;

        mcpToolRegistry.registerTool("builtin", McpToolDefinition.builder()
                .name("get_dialogue_history")
                .description("Get dialogue history with a specific user. Returns previous conversation turns " +
                        "including which messages the bot was responding to. " +
                        "Use this to understand the conversation flow with a user.")
                .inputSchema(schema)
                .pluginId("builtin")
                .keywords(List.of("history", "conversation", "dialogue", "past", "previous"))
                .guildOnly(false)
                .dmOnly(false)
                .permission(group.worldstandard.pudel.api.agent.AgentTool.ToolPermission.EVERYONE)
                .priority(90)
                .executor(this::executeGetDialogueHistory)
                .build());
    }

    /**
     * Register get_message_by_id tool.
     * Fetches a specific message from passive context by its ID.
     */
    private void registerGetMessageById() {
        String schema = """
                {
                    "type": "object",
                    "properties": {
                        "message_id": {
                            "type": "number",
                            "description": "The message ID to fetch"
                        },
                        "channel_id": {
                            "type": "number",
                            "description": "The channel ID where the message was sent"
                        },
                        "is_guild": {
                            "type": "boolean",
                            "description": "Whether this is a guild context"
                        },
                        "target_id": {
                            "type": "number",
                            "description": "The guild ID or user ID"
                        }
                    },
                    "required": ["message_id", "channel_id"]
                }
                """;

        mcpToolRegistry.registerTool("builtin", McpToolDefinition.builder()
                .name("get_message_by_id")
                .description("Fetch a specific message from passive context by its message ID. " +
                        "Use this when you need to reference a specific message mentioned in conversation.")
                .inputSchema(schema)
                .pluginId("builtin")
                .keywords(List.of("message", "fetch", "lookup", "reference", "specific"))
                .guildOnly(false)
                .dmOnly(false)
                .permission(group.worldstandard.pudel.api.agent.AgentTool.ToolPermission.EVERYONE)
                .priority(80)
                .executor(this::executeGetMessageById)
                .build());
    }

    /**
     * Register get_forwarded_messages tool.
     * Retrieves forwarded message data from a specific message.
     */
    private void registerGetForwardedMessages() {
        String schema = """
                {
                    "type": "object",
                    "properties": {
                        "message_id": {
                            "type": "number",
                            "description": "The message ID containing forwarded content"
                        },
                        "channel_id": {
                            "type": "number",
                            "description": "The channel ID"
                        },
                        "is_guild": {
                            "type": "boolean",
                            "description": "Whether this is a guild context"
                        },
                        "target_id": {
                            "type": "number",
                            "description": "The guild ID or user ID"
                        }
                    },
                    "required": ["message_id", "channel_id"]
                }
                """;

        mcpToolRegistry.registerTool("builtin", McpToolDefinition.builder()
                .name("get_forwarded_messages")
                .description("Get forwarded message data from a specific message. " +
                        "Use this to retrieve content from messages that were forwarded/quoted.")
                .inputSchema(schema)
                .pluginId("builtin")
                .keywords(List.of("forwarded", "quoted", "reference", "embed"))
                .guildOnly(false)
                .dmOnly(false)
                .permission(AgentTool.ToolPermission.EVERYONE)
                .priority(70)
                .executor(this::executeGetForwardedMessages)
                .build());
    }

    /**
     * Register get_brain_status tool.
     * Returns the current status of PudelBrain.
     */
    private void registerGetBrainStatus() {
        String schema = """
                {
                    "type": "object",
                    "properties": {},
                    "required": []
                }
                """;

        mcpToolRegistry.registerTool("builtin", McpToolDefinition.builder()
                .name("get_brain_status")
                .description("Get the current status of PudelBrain including Ollama availability and context queue stats.")
                .inputSchema(schema)
                .pluginId("builtin")
                .keywords(List.of("status", "health", "diagnostics", "ollama", "queue"))
                .guildOnly(false)
                .dmOnly(false)
                .permission(AgentTool.ToolPermission.EVERYONE)
                .priority(10)
                .executor(this::executeGetBrainStatus)
                .build());
    }

    // ===============================
    // Tool Executors
    // ===============================

    private String executeGetPassiveContext(AgentToolContext context, Map<String, Object> parameters) {
        try {
            long channelId = extractLong(parameters, "channel_id");
            boolean isGuild = extractBoolean(parameters, "is_guild", true);
            long targetId = extractLong(parameters, "target_id", channelId);
            int limit = extractInt(parameters, "limit", 10);

            return brain.getPassiveContext(channelId, isGuild, targetId, limit);
        } catch (Exception e) {
            logger.error("Error executing get_passive_context: {}", e.getMessage());
            return "Error retrieving context: " + e.getMessage();
        }
    }

    private String executeGetDialogueHistory(AgentToolContext context, Map<String, Object> parameters) {
        try {
            long userId = extractLong(parameters, "user_id");
            boolean isGuild = extractBoolean(parameters, "is_guild", true);
            long targetId = extractLong(parameters, "target_id");
            int limit = extractInt(parameters, "limit", 10);

            return brain.getDialogueHistory(userId, isGuild, targetId, limit);
        } catch (Exception e) {
            logger.error("Error executing get_dialogue_history: {}", e.getMessage());
            return "Error retrieving history: " + e.getMessage();
        }
    }

    private String executeGetMessageById(AgentToolContext context, Map<String, Object> parameters) {
        try {
            long messageId = extractLong(parameters, "message_id");
            long channelId = extractLong(parameters, "channel_id");
            boolean isGuild = extractBoolean(parameters, "is_guild", true);
            long targetId = extractLong(parameters, "target_id", channelId);

            PassiveContextEntry entry = brain.fetchContextByMessageId(messageId, channelId, isGuild, targetId);
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
                sb.append("Entities: ").append(entry.entities().toString()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Error executing get_message_by_id: {}", e.getMessage());
            return "Error fetching message: " + e.getMessage();
        }
    }

    private String executeGetForwardedMessages(AgentToolContext context, Map<String, Object> parameters) {
        try {
            long messageId = extractLong(parameters, "message_id");
            long channelId = extractLong(parameters, "channel_id");
            boolean isGuild = extractBoolean(parameters, "is_guild", true);
            long targetId = extractLong(parameters, "target_id", channelId);

            PassiveContextEntry entry = brain.fetchContextByMessageId(messageId, channelId, isGuild, targetId);
            if (entry == null) {
                return "Message " + messageId + " not found in context.";
            }

            List<PassiveContextEntry.ForwardedMessageRef> forwarded = entry.forwardedMessages();
            if (forwarded == null || forwarded.isEmpty()) {
                return "No forwarded messages found in message " + messageId;
            }

            StringBuilder sb = new StringBuilder("Forwarded messages:\n");
            for (PassiveContextEntry.ForwardedMessageRef fwd : forwarded) {
                sb.append("- From ").append(fwd.authorName())
                        .append(": ")
                        .append(fwd.content()).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Error executing get_forwarded_messages: {}", e.getMessage());
            return "Error fetching forwarded messages: " + e.getMessage();
        }
    }

    private String executeGetBrainStatus(AgentToolContext context, Map<String, Object> parameters) {
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

    // ===============================
    // Parameter Extraction Helpers
    // ===============================

    private long extractLong(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    private long extractLong(Map<String, Object> params, String key, long defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private int extractInt(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean extractBoolean(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }
}
