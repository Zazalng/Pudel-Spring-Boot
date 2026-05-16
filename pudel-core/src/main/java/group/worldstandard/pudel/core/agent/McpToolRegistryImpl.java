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

import group.worldstandard.pudel.api.agent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of McpToolRegistry for managing MCP (Model Context Protocol) tools.
 * <p>
 * This registry manages MCP-compatible tools alongside the legacy AgentTool system.
 * MCP tools use JSON Schema for parameter definitions and follow the Model Context Protocol.
 * <p>
 * Built-in MCP tools (like BuiltinMcpTools) are registered at startup, and plugins
 * can register additional MCP tools at runtime.
 */
@Component
public class McpToolRegistryImpl implements McpToolRegistry {
    private static final Logger logger = LoggerFactory.getLogger(McpToolRegistryImpl.class);

    // Tool storage: toolName -> McpToolDefinition
    private final Map<String, McpToolDefinition> tools = new ConcurrentHashMap<>();

    // Track which plugin owns which tools: pluginId -> Set<toolName>
    private final Map<String, Set<String>> pluginTools = new ConcurrentHashMap<>();

    @Override
    public boolean registerTool(String pluginId, McpToolDefinition definition) {
        Objects.requireNonNull(pluginId, "Plugin ID cannot be null");
        Objects.requireNonNull(definition, "Definition cannot be null");

        String toolName = definition.getName();

        if (tools.containsKey(toolName)) {
            logger.warn("MCP tool '{}' already registered", toolName);
            return false;
        }

        tools.put(toolName, definition);
        pluginTools.computeIfAbsent(pluginId, k -> ConcurrentHashMap.newKeySet()).add(toolName);

        logger.info("Registered MCP tool: {} (plugin: {})", toolName, pluginId);
        return true;
    }

    @Override
    public boolean unregisterTool(String toolName) {
        McpToolDefinition removed = tools.remove(toolName);
        if (removed == null) {
            return false;
        }

        pluginTools.values().forEach(set -> set.remove(toolName));
        logger.info("Unregistered MCP tool: {}", toolName);
        return true;
    }

    @Override
    public int unregisterAll(String pluginId) {
        Set<String> toolNames = pluginTools.remove(pluginId);
        if (toolNames == null || toolNames.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String toolName : toolNames) {
            if (tools.remove(toolName) != null) {
                count++;
            }
        }

        logger.info("Unregistered {} MCP tools from plugin: {}", count, pluginId);
        return count;
    }

    @Override
    public Optional<McpToolDefinition> getTool(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    @Override
    public Collection<McpToolDefinition> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    @Override
    public Collection<McpToolDefinition> getToolsByPlugin(String pluginId) {
        Set<String> names = pluginTools.get(pluginId);
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        return names.stream()
                .map(tools::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<McpToolDefinition> searchTools(String keyword) {
        String lower = keyword.toLowerCase();
        return tools.values().stream()
                .filter(tool ->
                        tool.getName().toLowerCase().contains(lower) ||
                        tool.getDescription().toLowerCase().contains(lower) ||
                        tool.getKeywords().stream().anyMatch(k -> k.toLowerCase().contains(lower)))
                .collect(Collectors.toList());
    }

    @Override
    public boolean hasTool(String toolName) {
        return tools.containsKey(toolName);
    }

    @Override
    public int getToolCount() {
        return tools.size();
    }

    @Override
    public ToolResult executeTool(String toolName, AgentToolContext context, Map<String, Object> parameters) {
        long startTime = System.currentTimeMillis();

        McpToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            return ToolResult.notFound(toolName);
        }

        // Check availability (guild/DM restrictions)
        if (!tool.isAvailableIn(context)) {
            String reason = tool.isGuildOnly() ? "This tool is only available in servers" :
                       tool.isDmOnly() ? "This tool is only available in DMs" : "Unknown";
            return ToolResult.notAvailable(toolName, reason);
        }

        try {
            String result = tool.execute(context, parameters);
            long duration = System.currentTimeMillis() - startTime;
            return ToolResult.success(toolName, result, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Error executing MCP tool {}: {}", toolName, e.getMessage(), e);
            return ToolResult.failure(toolName, e.getMessage(), duration);
        }
    }
}

