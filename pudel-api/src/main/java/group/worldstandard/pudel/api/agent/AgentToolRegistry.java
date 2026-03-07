/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 World Standard Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package group.worldstandard.pudel.api.agent;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry for managing agent tools from plugins.
 * <p>
 * Plugins use this registry to register their tools that the AI agent can use.
 * The registry handles tool discovery, validation, and lifecycle management.
 * <p>
 * Example usage in a plugin:
 * <pre>
 * {@code @Plugin(name = "MyPlugin", version = "1.0.0", author = "Author")}
 * public class MyPlugin {
 *
 *     {@code @OnEnable}
 *     public void onEnable(PluginContext context) {
 *         AgentToolRegistry registry = context.getAgentToolRegistry();
 *
 *         // Register a tool provider (class with @AgentTool methods)
 *         registry.registerProvider("my-plugin", new MyTools());
 *
 *         // Or register individual tool executors
 *         registry.registerTool("my-plugin", ToolDefinition.builder()
 *             .name("hello")
 *             .description("Say hello to someone")
 *             .executor((ctx, params) -> "Hello, " + params.get("name") + "!")
 *             .build());
 *     }
 *
 *     {@code @OnDisable}
 *     public void onDisable(PluginContext context) {
 *         // Unregister all tools for this plugin
 *         context.getAgentToolRegistry().unregisterAll("my-plugin");
 *     }
 * }
 * </pre>
 */
public interface AgentToolRegistry {

    /**
     * Register a tool provider with annotated @AgentTool methods.
     * The registry will scan the provider for @AgentTool annotations
     * and register each method as a tool.
     *
     * @param pluginId the plugin identifier (for tracking ownership)
     * @param provider the tool provider instance
     * @return number of tools registered
     */
    int registerProvider(String pluginId, AgentToolProvider provider);

    /**
     * Register a single tool with a custom executor.
     *
     * @param pluginId the plugin identifier
     * @param definition the tool definition
     * @return true if registered successfully
     */
    boolean registerTool(String pluginId, ToolDefinition definition);

    /**
     * Unregister a specific tool.
     *
     * @param toolName the tool name
     * @return true if the tool was found and unregistered
     */
    boolean unregisterTool(String toolName);

    /**
     * Unregister all tools from a plugin.
     *
     * @param pluginId the plugin identifier
     * @return number of tools unregistered
     */
    int unregisterAll(String pluginId);

    /**
     * Get a tool definition by name.
     *
     * @param toolName the tool name
     * @return the tool definition, or empty if not found
     */
    Optional<ToolDefinition> getTool(String toolName);

    /**
     * Get all registered tools.
     *
     * @return collection of all tool definitions
     */
    Collection<ToolDefinition> getAllTools();

    /**
     * Get all tools registered by a specific plugin.
     *
     * @param pluginId the plugin identifier
     * @return collection of tool definitions from that plugin
     */
    Collection<ToolDefinition> getToolsByPlugin(String pluginId);

    /**
     * Search for tools by keyword.
     *
     * @param keyword the keyword to search
     * @return matching tool definitions
     */
    Collection<ToolDefinition> searchTools(String keyword);

    /**
     * Check if a tool with the given name exists.
     *
     * @param toolName the tool name
     * @return true if exists
     */
    boolean hasTool(String toolName);

    /**
     * Get the number of registered tools.
     *
     * @return total tool count
     */
    int getToolCount();

    /**
     * Execute a tool by name with the given context and parameters.
     *
     * @param toolName the tool name
     * @param context the execution context
     * @param parameters the tool parameters
     * @return the tool execution result
     */
    ToolResult executeTool(String toolName, AgentToolContext context, java.util.Map<String, Object> parameters);
}
