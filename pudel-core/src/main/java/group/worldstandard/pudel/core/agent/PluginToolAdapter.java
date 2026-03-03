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

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import group.worldstandard.pudel.api.agent.AgentToolContext;
import group.worldstandard.pudel.api.agent.ToolDefinition;
import group.worldstandard.pudel.api.agent.ToolResult;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that exposes plugin tools to LangChain4j's agent system.
 * <p>
 * This class is instantiated per-request with the appropriate context,
 * and provides @Tool annotated methods that delegate to the plugin registry.
 * <p>
 * Since LangChain4j requires @Tool annotations at compile time but plugins
 * register tools dynamically, this adapter provides a bridge by having
 * a generic "execute_plugin_tool" method that can call any registered tool.
 */
public class PluginToolAdapter {

    private static final Logger logger = LoggerFactory.getLogger(PluginToolAdapter.class);

    private final AgentToolRegistryImpl registry;
    private final AgentToolContext context;

    public PluginToolAdapter(AgentToolRegistryImpl registry, AgentToolContext context) {
        this.registry = registry;
        this.context = context;
    }

    /**
     * Execute any registered plugin tool by name.
     * <p>
     * This is the main entry point for the LLM to call plugin tools.
     * The LLM provides the tool name and parameters as a JSON-like string.
     *
     * @param toolName the name of the tool to execute
     * @param parametersJson JSON-like string of parameters (e.g., "location=Tokyo, days=5")
     * @return the tool's result
     */
    @Tool("Execute a plugin tool by name. Use 'list_plugin_tools' first to see available tools. " +
          "Parameters should be provided as 'key=value' pairs separated by commas.")
    public String executePluginTool(String toolName, String parametersJson) {
        if (toolName == null || toolName.isBlank()) {
            return "Error: Tool name is required";
        }

        // Parse parameters
        Map<String, Object> params = parseParameters(parametersJson);

        // Execute the tool
        ToolResult result = registry.executeTool(toolName, context, params);

        if (result.success()) {
            return result.result();
        } else {
            return "Error: " + result.error();
        }
    }

    /**
     * List all available plugin tools.
     * <p>
     * The LLM can call this to discover what tools are available
     * before deciding which one to use.
     *
     * @return list of available tools with descriptions
     */
    @Tool("List all available plugin tools with their descriptions and parameters")
    public String listPluginTools() {
        return registry.buildToolDescriptions(context);
    }

    /**
     * Get detailed information about a specific tool.
     *
     * @param toolName the tool name
     * @return detailed tool information
     */
    @Tool("Get detailed information about a specific plugin tool including its parameters")
    public String getToolInfo(String toolName) {
        return registry.getTool(toolName)
                .map(this::formatToolInfo)
                .orElse("Tool not found: " + toolName);
    }

    /**
     * Format detailed tool information.
     */
    private String formatToolInfo(ToolDefinition tool) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Tool: ").append(tool.getName()).append("**\n");
        sb.append("Description: ").append(tool.getDescription()).append("\n");
        sb.append("Plugin: ").append(tool.getPluginId()).append("\n");

        if (!tool.getParameters().isEmpty()) {
            sb.append("\nParameters:\n");
            for (ToolDefinition.ToolParameter param : tool.getParameters()) {
                sb.append("- ").append(param.name())
                        .append(" (").append(param.getTypeName()).append(")")
                        .append(param.required() ? " [required]" : " [optional]")
                        .append(": ").append(param.description())
                        .append("\n");
            }
        }

        if (!tool.getKeywords().isEmpty()) {
            sb.append("\nKeywords: ").append(String.join(", ", tool.getKeywords()));
        }

        return sb.toString();
    }

    /**
     * Parse parameter string into a map.
     * Supports formats like: "key1=value1, key2=value2"
     */
    private Map<String, Object> parseParameters(String parametersJson) {
        Map<String, Object> params = new HashMap<>();

        if (parametersJson == null || parametersJson.isBlank()) {
            return params;
        }

        // Simple key=value parsing
        String[] pairs = parametersJson.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim();

                // Try to parse as number or boolean
                params.put(key, parseValue(value));
            }
        }

        return params;
    }

    /**
     * Parse a string value to the appropriate type.
     */
    private Object parseValue(String value) {
        // Try boolean
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;

        // Try integer
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {}

        // Try double
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {}

        // Return as string
        return value;
    }
}
