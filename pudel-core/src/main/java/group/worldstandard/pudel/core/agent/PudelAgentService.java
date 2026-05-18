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
package group.worldstandard.pudel.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import group.worldstandard.pudel.api.agent.*;
import group.worldstandard.pudel.core.agent.builtin.BuiltinMcpTools;
import group.worldstandard.pudel.core.brain.PudelBrain;
import group.worldstandard.pudel.core.brain.ollama.OllamaClient.ConversationTurn;
import group.worldstandard.pudel.core.brain.personality.PudelPersonality;
import group.worldstandard.pudel.core.brain.personality.SystemPromptBuilder;
import group.worldstandard.pudel.core.config.brain.PudelBrainConfig;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Pudel Agent Service v2 - Integrates the reworked PudelBrain with the MCP tool system.
 * <p>
 * This service provides:
 * - Agent-mode processing with MCP tool-calling support
 * - Integration between PudelBrain's Ollama completion and the MCP tool registry
 * - Tool discovery and execution for the LLM
 * - Session management for conversation context
 * <p>
 * Unlike the legacy PudelAgentService (in pudel-model module), this version:
 * - Uses the new PudelBrain v2 for all completion
 * - Uses McpToolRegistry for tool management
 * - Supports both built-in and plugin-provided MCP tools
 * - Does not depend on the deprecated pudel-model module
 */
@Service
public class PudelAgentService {

    private static final Logger logger = LoggerFactory.getLogger(PudelAgentService.class);

    private final PudelBrain brain;
    private final PudelBrainConfig brainConfig;
    private final SystemPromptBuilder systemPromptBuilder;
    private final AgentToolRegistryImpl agentToolRegistry;
    private final McpToolRegistry mcpToolRegistry;
    private final BuiltinMcpTools builtinMcpTools;

    public PudelAgentService(PudelBrain brain,
                              PudelBrainConfig brainConfig,
                              SystemPromptBuilder systemPromptBuilder,
                              AgentToolRegistryImpl agentToolRegistry,
                              McpToolRegistry mcpToolRegistry,
                              BuiltinMcpTools builtinMcpTools) {
        this.brain = brain;
        this.brainConfig = brainConfig;
        this.systemPromptBuilder = systemPromptBuilder;
        this.agentToolRegistry = agentToolRegistry;
        this.mcpToolRegistry = mcpToolRegistry;
        this.builtinMcpTools = builtinMcpTools;
    }

    @PostConstruct
    public void init() {
        logger.info("PudelAgentService v2 initialized (MCP tools: {}, Agent tools: {})",
                mcpToolRegistry.getToolCount(), agentToolRegistry.getToolCount());
    }

    /**
     * Process a message with agent capabilities (MCP tool-calling).
     * <p>
     * This method implements a tool-calling iteration loop:
     * 1. Builds the system prompt with tool descriptions
     * 2. Sends the conversation to Ollama for completion
     * 3. If the response contains a tool call, parses and executes it
     * 4. Appends the tool result back to the conversation
     * 5. Re-generates until a final response is produced or max iterations reached
     *
     * @param userMessage   the user's message
     * @param personality   the personality configuration
     * @param context       the agent tool context
     * @param isGuild       whether this is a guild context
     * @param targetId      guild ID or user ID
     * @return the agent's response
     */
    public AgentResponse processWithTools(String userMessage,
                                            PudelPersonality personality,
                                            AgentToolContext context,
                                            boolean isGuild,
                                            long targetId) {
        if (!brainConfig.getMcp().isEnabled()) {
            logger.debug("MCP disabled, falling back to standard brain processing");
            return processStandard(userMessage, personality, context.getUserId(), isGuild, targetId);
        }

        try {
            // Build system prompt with tool information
            String systemPrompt = systemPromptBuilder.buildSystemPrompt(
                    personality, isGuild, brainConfig.getCompletion().isEnableRoleplay());

            // Append MCP tool descriptions in a structured format for Ollama
            String toolDescriptions = buildMcpToolDescriptions();
            if (!toolDescriptions.isBlank()) {
                systemPrompt += "\n## Available MCP Tools\n" + toolDescriptions + "\n";
            }

            // Gather conversation history for context
            List<ConversationTurn> history = gatherConversationHistoryForAgent(
                    context, isGuild, targetId);

            // Build the full conversation including tool call instructions
            List<ConversationTurn> fullHistory = new ArrayList<>(history);

            // Tool-calling iteration loop
            int maxIterations = brainConfig.getMcp().getMaxToolIterations();
            List<String> toolsUsed = new ArrayList<>();
            String response = null;

            for (int i = 0; i < maxIterations; i++) {
                // Build prompt with conversation history
                String prompt = buildAgentPrompt(fullHistory, userMessage, i == 0);

                // Generate response from Ollama
                response = brain.getOllamaClient().generateBlocking(
                        systemPrompt, prompt, List.of());

                if (response == null || response.isBlank()) {
                    return new AgentResponse(null, false, "Empty response from Ollama", toolsUsed);
                }

                // Check if the response contains a tool call
                // Tool call format: <TOOL_CALL>{"name": "...", "arguments": {...}}</TOOL_CALL>
                // or JSON function_call format: {"name": "...", "arguments": {...}}
                ToolCallResult toolCall = tryParseToolCall(response);

                if (toolCall == null) {
                    // No tool call - this is the final response
                    break;
                }

                // Execute the tool
                logger.debug("Executing MCP tool: {} with arguments: {}",
                        toolCall.name(), toolCall.arguments());

                ToolResult toolResult = executeMcpTool(
                        toolCall.name(), context, toolCall.arguments());

                toolsUsed.add(toolCall.name());

                // Append the tool result to conversation history for the next iteration
                String toolResultMessage = "Tool result from '" + toolCall.name() + "': "
                        + toolResult.result();
                fullHistory.add(new ConversationTurn(
                        "Tool call: " + toolCall.name() + " " + toolCall.arguments(),
                        toolResultMessage));

                logger.debug("Tool '{}' returned: {}", toolCall.name(), toolResult.result());
            }

            if (response != null && !response.isBlank()) {
                return new AgentResponse(response, true, null, toolsUsed);
            } else {
                return new AgentResponse(null, false, "No response generated", toolsUsed);
            }

        } catch (Exception e) {
            logger.error("Error in agent processing: {}", e.getMessage(), e);
            return new AgentResponse(null, false, e.getMessage(), List.of());
        }
    }

    /**
     * Attempt to parse a tool call from the LLM response.
     * Supports both XML-style tool call markers and raw JSON.
     */
    private ToolCallResult tryParseToolCall(String response) {
        ObjectMapper mapper = new ObjectMapper();

        // Try XML-style tool call markers first
        // Format: <TOOL_CALL>{"name": "...", "arguments": {...}}</TOOL_CALL>
        int startIdx = response.indexOf("<TOOL_CALL>");
        int endIdx = response.indexOf("</TOOL_CALL>");
        if (startIdx >= 0 && endIdx > startIdx) {
            String jsonStr = response.substring(startIdx + 11, endIdx).trim();
            return parseToolCallJson(jsonStr, mapper);
        }

        // Try to find a JSON object that looks like a tool call
        // Look for patterns like {"name": "...", "arguments": {...}}
        int braceStart = response.indexOf('{');
        int braceEnd = response.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            String jsonStr = response.substring(braceStart, braceEnd + 1).trim();
            ToolCallResult result = parseToolCallJson(jsonStr, mapper);
            if (result != null) {
                return result;
            }
        }

        // No tool call detected
        return null;
    }

    /**
     * Parse JSON string as a tool call.
     * Expected format: {"name": "...", "arguments": {...}}
     */
    private ToolCallResult parseToolCallJson(String jsonStr, ObjectMapper mapper) {
        try {
            JsonNode node = mapper.readTree(jsonStr);
            if (node.has("name") && node.has("arguments")) {
                String name = node.get("name").asText();
                Map<String, Object> arguments = new HashMap<>();
                JsonNode argsNode = node.get("arguments");
                if (argsNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = argsNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        arguments.put(field.getKey(), field.getValue().asText());
                    }
                }
                return new ToolCallResult(name, arguments);
            }
        } catch (Exception e) {
            logger.debug("Failed to parse tool call JSON: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Build the prompt for agent conversation including user message.
     */
    private String buildAgentPrompt(List<ConversationTurn> history,
                                     String userMessage, boolean includeUserMessage) {
        StringBuilder prompt = new StringBuilder();
        for (ConversationTurn turn : history) {
            prompt.append("User: ").append(turn.userMessage()).append("\n");
            prompt.append("Assistant: ").append(turn.botResponse()).append("\n");
        }
        if (includeUserMessage) {
            prompt.append("User: ").append(userMessage).append("\n");
        }
        prompt.append("Assistant:");
        return prompt.toString();
    }

    /**
     * Gather conversation history for agent context.
     */
    private List<ConversationTurn> gatherConversationHistoryForAgent(
            AgentToolContext context, boolean isGuild, long targetId) {
        int limit = brainConfig.getCompletion().getMaxContextMessages();
        List<Map<String, Object>> rawHistory;
        if (isGuild) {
            rawHistory = brain.getDialogueHistoryManager()
                    .getRecentHistory(context.getUserId(), true, targetId, limit);
        } else {
            rawHistory = brain.getDialogueHistoryManager()
                    .getRecentHistory(context.getUserId(), false, targetId, limit);
        }

        List<ConversationTurn> turns = new ArrayList<>();
        for (Map<String, Object> entry : rawHistory) {
            String userMsg = (String) entry.getOrDefault("user_message", "");
            String botMsg = (String) entry.getOrDefault("bot_response", "");
            if (userMsg != null && !userMsg.isBlank()) {
                turns.add(new ConversationTurn(userMsg,
                        botMsg != null ? botMsg : ""));
            }
        }
        return turns;
    }

    /**
     * Process a message using standard brain processing (no tool-calling).
     * <p>
     * This method properly gathers conversation history and passive context
     * before delegating to Ollama for completion, unlike the previous
     * implementation which bypassed the brain's pipeline entirely.
     *
     * @param userMessage the user's message
     * @param personality the personality configuration
     * @param userId      the user's Discord ID (for per-user history lookup)
     * @param isGuild     whether this is a guild context
     * @param targetId    guild ID or user ID
     * @return the agent's response
     */
    public AgentResponse processStandard(String userMessage,
                                           PudelPersonality personality,
                                           long userId,
                                           boolean isGuild,
                                           long targetId) {
        try {
            String systemPrompt = systemPromptBuilder.buildSystemPrompt(
                    personality, isGuild, brainConfig.getCompletion().isEnableRoleplay());

            // Gather conversation history for context
            int limit = brainConfig.getCompletion().getMaxContextMessages();
            List<Map<String, Object>> rawHistory;
            if (isGuild) {
                rawHistory = brain.getDialogueHistoryManager()
                        .getRecentHistory(userId, true, targetId, limit);
            } else {
                rawHistory = brain.getDialogueHistoryManager()
                        .getRecentHistory(userId, false, targetId, limit);
            }

            List<ConversationTurn> history = new ArrayList<>();
            for (Map<String, Object> entry : rawHistory) {
                String userMsg = (String) entry.getOrDefault("user_message", "");
                String botMsg = (String) entry.getOrDefault("bot_response", "");
                if (userMsg != null && !userMsg.isBlank()) {
                    history.add(new ConversationTurn(userMsg,
                            botMsg != null ? botMsg : ""));
                }
            }

            // Enrich with passive context (use targetId as channel approximation)
            String passiveContext = brain.gatherPassiveContext(targetId, isGuild, targetId);
            if (passiveContext != null && !passiveContext.equals("No recent context available.")) {
                systemPrompt += "\n## Recent Context\n" + passiveContext + "\n";
            }

            // Build prompt with conversation history
            StringBuilder promptBuilder = new StringBuilder();
            for (ConversationTurn turn : history) {
                promptBuilder.append("User: ").append(turn.userMessage()).append("\n");
                promptBuilder.append("Assistant: ").append(turn.botResponse()).append("\n");
            }
            promptBuilder.append("User: ").append(userMessage).append("\n");
            promptBuilder.append("Assistant:");

            String response = brain.getOllamaClient().generateBlocking(
                    systemPrompt, promptBuilder.toString(), List.of());

            if (response != null && !response.isBlank()) {
                return new AgentResponse(response, true, null, List.of());
            } else {
                return new AgentResponse(null, false, "Empty response from Ollama", List.of());
            }
        } catch (Exception e) {
            logger.error("Error in standard processing: {}", e.getMessage(), e);
            return new AgentResponse(null, false, e.getMessage(), List.of());
        }
    }

    /**
     * Execute a specific MCP tool by name.
     * <p>
     * This is called when the LLM requests a tool to be executed.
     *
     * @param toolName  the tool name
     * @param context   the execution context
     * @param parameters the tool parameters
     * @return the tool's result
     */
    public ToolResult executeMcpTool(String toolName, AgentToolContext context, Map<String, Object> parameters) {
        // Try MCP tools first, then fall back to legacy agent tools
        if (mcpToolRegistry.hasTool(toolName)) {
            return mcpToolRegistry.executeTool(toolName, context, parameters);
        }
        if (agentToolRegistry.hasTool(toolName)) {
            return agentToolRegistry.executeTool(toolName, context, parameters);
        }
        return ToolResult.notFound(toolName);
    }

    /**
     * Build a description of all available MCP tools for the system prompt.
     */
    private String buildMcpToolDescriptions() {
        StringBuilder sb = new StringBuilder();

        Collection<McpToolDefinition> mcpTools = mcpToolRegistry.getAllTools();
        if (!mcpTools.isEmpty()) {
            sb.append("### MCP Tools\n");
            for (McpToolDefinition tool : mcpTools) {
                sb.append("- **").append(tool.getName()).append("**: ")
                        .append(tool.getDescription()).append("\n");
            }
        }

        // Also include legacy agent tools
        Collection<ToolDefinition> agentTools = agentToolRegistry.getAllTools();
        if (!agentTools.isEmpty()) {
            sb.append("\n### Plugin Tools\n");
            for (ToolDefinition tool : agentTools) {
                sb.append("- **").append(tool.getName()).append("**: ")
                        .append(tool.getDescription()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Get the number of available MCP tools.
     */
    public int getMcpToolCount() {
        return mcpToolRegistry.getToolCount();
    }

    /**
     * Get the number of available legacy agent tools.
     */
    public int getAgentToolCount() {
        return agentToolRegistry.getToolCount();
    }

    /**
     * Check if MCP tools are enabled and available.
     */
    public boolean isMcpAvailable() {
        return brainConfig.getMcp().isEnabled() && mcpToolRegistry.getToolCount() > 0;
    }

    // ===============================
     // Response DTO
     // ===============================

    public record AgentResponse(
            String response,
            boolean success,
            String error,
            List<String> toolsUsed
    ) {}

    /**
     * Represents a parsed tool call from the LLM response.
     *
     * @param name      the tool name to invoke
     * @param arguments the tool arguments as key-value pairs
     */
    public record ToolCallResult(
            String name,
            Map<String, Object> arguments
    ) {}
}

