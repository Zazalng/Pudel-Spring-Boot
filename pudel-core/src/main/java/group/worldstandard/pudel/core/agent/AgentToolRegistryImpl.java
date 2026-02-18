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
package group.worldstandard.pudel.core.agent;

import group.worldstandard.pudel.api.agent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of AgentToolRegistry for managing plugin-provided agent tools.
 * <p>
 * This registry:
 * - Stores tool definitions from plugins
 * - Scans @AgentTool annotated methods from providers
 * - Executes tools with proper context
 * - Integrates with the LangChain4j agent system
 */
@Component
public class AgentToolRegistryImpl implements AgentToolRegistry {

    private static final Logger logger = LoggerFactory.getLogger(AgentToolRegistryImpl.class);

    // Tool storage: toolName -> ToolDefinition
    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    // Track which plugin owns which tools: pluginId -> Set<toolName>
    private final Map<String, Set<String>> pluginTools = new ConcurrentHashMap<>();

    // Provider instances for execution: toolName -> provider instance
    private final Map<String, Object> toolProviders = new ConcurrentHashMap<>();

    // Method references for execution: toolName -> Method
    private final Map<String, Method> toolMethods = new ConcurrentHashMap<>();

    @Override
    public int registerProvider(String pluginId, AgentToolProvider provider) {
        Objects.requireNonNull(pluginId, "Plugin ID cannot be null");
        Objects.requireNonNull(provider, "Provider cannot be null");

        int registered = 0;
        Class<?> providerClass = provider.getClass();

        // Scan for @AgentTool annotated methods
        for (Method method : providerClass.getDeclaredMethods()) {
            AgentTool annotation = method.getAnnotation(AgentTool.class);
            if (annotation == null) {
                continue;
            }

            try {
                // Validate method signature
                validateToolMethod(method);

                // Build tool definition
                String toolName = annotation.name().isEmpty() ? method.getName() : annotation.name();

                // Check for duplicates
                if (tools.containsKey(toolName)) {
                    logger.warn("Tool '{}' already registered, skipping from {}", toolName, providerClass.getName());
                    continue;
                }

                // Build parameters from method (skip first AgentToolContext parameter)
                List<ToolDefinition.ToolParameter> params = new ArrayList<>();
                Parameter[] methodParams = method.getParameters();
                for (int i = 1; i < methodParams.length; i++) {
                    Parameter param = methodParams[i];
                    params.add(new ToolDefinition.ToolParameter(
                            param.getName(),
                            param.getType(),
                            "Parameter " + param.getName(),
                            true,
                            null
                    ));
                }

                // Create tool definition with method executor
                ToolDefinition definition = ToolDefinition.builder()
                        .name(toolName)
                        .description(annotation.description())
                        .pluginId(pluginId)
                        .keywords(Arrays.asList(annotation.keywords()))
                        .guildOnly(annotation.guildOnly())
                        .dmOnly(annotation.dmOnly())
                        .permission(annotation.permission())
                        .priority(annotation.priority())
                        .executor((context, parameters) -> invokeToolMethod(toolName, context, parameters))
                        .build();


                // Register
                tools.put(toolName, definition);
                toolProviders.put(toolName, provider);
                toolMethods.put(toolName, method);
                pluginTools.computeIfAbsent(pluginId, k -> ConcurrentHashMap.newKeySet()).add(toolName);

                method.setAccessible(true);
                registered++;

                logger.debug("Registered agent tool: {} from {} ({})", toolName, providerClass.getSimpleName(), pluginId);

            } catch (Exception e) {
                logger.error("Failed to register tool from method {}: {}", method.getName(), e.getMessage());
            }
        }

        if (registered > 0) {
            provider.onRegister();
            logger.info("Registered {} agent tools from {} (plugin: {})",
                    registered, provider.getProviderName(), pluginId);
        }

        return registered;
    }

    /**
     * Validate that a method has the correct signature for an agent tool.
     */
    private void validateToolMethod(Method method) {
        // Must return String
        if (method.getReturnType() != String.class) {
            throw new IllegalArgumentException("Tool method must return String");
        }

        // First parameter must be AgentToolContext
        Parameter[] params = method.getParameters();
        if (params.length == 0 || !AgentToolContext.class.isAssignableFrom(params[0].getType())) {
            throw new IllegalArgumentException("Tool method first parameter must be AgentToolContext");
        }

        // Other parameters must be simple types
        for (int i = 1; i < params.length; i++) {
            Class<?> type = params[i].getType();
            if (!isSimpleType(type)) {
                throw new IllegalArgumentException("Tool method parameter " + params[i].getName() +
                        " has unsupported type: " + type.getName());
            }
        }
    }

    /**
     * Check if a type is a simple type supported for tool parameters.
     */
    private boolean isSimpleType(Class<?> type) {
        return type == String.class ||
                type == int.class || type == Integer.class ||
                type == long.class || type == Long.class ||
                type == double.class || type == Double.class ||
                type == float.class || type == Float.class ||
                type == boolean.class || type == Boolean.class;
    }

    /**
     * Invoke a tool method via reflection.
     */
    private String invokeToolMethod(String toolName, AgentToolContext context, Map<String, Object> parameters) {
        Method method = toolMethods.get(toolName);
        Object provider = toolProviders.get(toolName);

        if (method == null || provider == null) {
            return "Error: Tool not found";
        }

        try {
            // Build argument array
            Parameter[] methodParams = method.getParameters();
            Object[] args = new Object[methodParams.length];
            args[0] = context;

            for (int i = 1; i < methodParams.length; i++) {
                String paramName = methodParams[i].getName();
                Class<?> paramType = methodParams[i].getType();
                Object value = parameters.get(paramName);

                // Convert value to expected type
                args[i] = convertValue(value, paramType);
            }

            return (String) method.invoke(provider, args);

        } catch (Exception e) {
            logger.error("Error invoking tool {}: {}", toolName, e.getMessage(), e);
            return "Error executing tool: " + e.getMessage();
        }
    }

    /**
     * Convert a value to the expected parameter type.
     */
    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            if (targetType.isPrimitive()) {
                // Return default primitive values
                if (targetType == int.class) return 0;
                if (targetType == long.class) return 0L;
                if (targetType == double.class) return 0.0;
                if (targetType == float.class) return 0.0f;
                if (targetType == boolean.class) return false;
            }
            return null;
        }

        if (targetType.isInstance(value)) {
            return value;
        }

        // String conversions
        String strValue = value.toString();

        if (targetType == String.class) {
            return strValue;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(strValue);
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(strValue);
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(strValue);
        }
        if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(strValue);
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(strValue);
        }

        return value;
    }

    @Override
    public boolean registerTool(String pluginId, ToolDefinition definition) {
        Objects.requireNonNull(pluginId, "Plugin ID cannot be null");
        Objects.requireNonNull(definition, "Definition cannot be null");

        String toolName = definition.getName();

        if (tools.containsKey(toolName)) {
            logger.warn("Tool '{}' already registered", toolName);
            return false;
        }

        tools.put(toolName, definition);
        pluginTools.computeIfAbsent(pluginId, k -> ConcurrentHashMap.newKeySet()).add(toolName);

        logger.info("Registered agent tool: {} (plugin: {})", toolName, pluginId);
        return true;
    }

    @Override
    public boolean unregisterTool(String toolName) {
        ToolDefinition removed = tools.remove(toolName);
        if (removed == null) {
            return false;
        }

        toolProviders.remove(toolName);
        toolMethods.remove(toolName);

        // Remove from plugin tracking
        pluginTools.values().forEach(set -> set.remove(toolName));

        logger.info("Unregistered agent tool: {}", toolName);
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
            ToolDefinition def = tools.remove(toolName);
            if (def != null) {
                Object provider = toolProviders.remove(toolName);
                toolMethods.remove(toolName);
                count++;

                // Call onUnregister if this was the last tool from the provider
                if (provider instanceof AgentToolProvider atp) {
                    boolean hasOtherTools = toolProviders.containsValue(provider);
                    if (!hasOtherTools) {
                        try {
                            atp.onUnregister();
                        } catch (Exception e) {
                            logger.debug("Error in onUnregister: {}", e.getMessage());
                        }
                    }
                }
            }
        }

        logger.info("Unregistered {} agent tools from plugin: {}", count, pluginId);
        return count;
    }

    @Override
    public Optional<ToolDefinition> getTool(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    @Override
    public Collection<ToolDefinition> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    @Override
    public Collection<ToolDefinition> getToolsByPlugin(String pluginId) {
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
    public Collection<ToolDefinition> searchTools(String keyword) {
        String lower = keyword.toLowerCase();
        return tools.values().stream()
                .filter(tool ->
                        tool.getName().toLowerCase().contains(lower) ||
                        tool.getDescription().toLowerCase().contains(lower) ||
                        tool.getKeywords().stream().anyMatch(k -> k.contains(lower)))
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

        ToolDefinition tool = tools.get(toolName);
        if (tool == null) {
            return ToolResult.notFound(toolName);
        }

        // Check availability (guild/DM restrictions)
        if (!tool.isAvailableIn(context)) {
            String reason = tool.isGuildOnly() ? "This tool is only available in servers" :
                           tool.isDmOnly() ? "This tool is only available in DMs" : "Unknown";
            return ToolResult.notAvailable(toolName, reason);
        }

        // Check permission level
        if (!checkPermission(tool.getPermission(), context)) {
            String permissionName = getPermissionName(tool.getPermission());
            logger.warn("User {} attempted to use tool {} without {} permission",
                    context.getRequestingUserId(), toolName, permissionName);
            return ToolResult.permissionDenied(toolName,
                    "You need " + permissionName + " permission to use this tool.");
        }

        try {
            String result = tool.execute(context, parameters);
            long duration = System.currentTimeMillis() - startTime;
            return ToolResult.success(toolName, result, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            logger.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
            return ToolResult.failure(toolName, e.getMessage(), duration);
        }
    }

    /**
     * Check if the user has the required permission level.
     */
    private boolean checkPermission(AgentTool.ToolPermission required, AgentToolContext context) {
        if (required == null || required == AgentTool.ToolPermission.EVERYONE) {
            return true;
        }

        // In DM context, only EVERYONE tools are allowed for guild-related permissions
        if (!context.isGuild()) {
            // BOT_ADMIN might still work in DMs if we add that check
            return required == AgentTool.ToolPermission.BOT_ADMIN && isBotAdmin(context.getRequestingUserId());
        }

        return switch (required) {
            case GUILD_MANAGER -> context.canManageGuild() || context.isAdmin() || context.isGuildOwner();
            case GUILD_ADMIN -> context.isAdmin() || context.isGuildOwner();
            case GUILD_OWNER -> context.isGuildOwner();
            case BOT_ADMIN -> isBotAdmin(context.getRequestingUserId());
            case EVERYONE -> true;
        };
    }

    /**
     * Check if a user is a bot admin.
     * This should be integrated with your admin whitelist system.
     */
    private boolean isBotAdmin(long userId) {
        // TODO: Integrate with AdminWhitelistRepository
        // For now, this is a placeholder - you can connect to your existing admin system
        return false;
    }

    /**
     * Get human-readable permission name.
     */
    private String getPermissionName(AgentTool.ToolPermission permission) {
        return switch (permission) {
            case EVERYONE -> "Everyone";
            case GUILD_MANAGER -> "Manage Server";
            case GUILD_ADMIN -> "Administrator";
            case GUILD_OWNER -> "Server Owner";
            case BOT_ADMIN -> "Bot Administrator";
        };
    }

    /**
     * Get all tools as a list for LangChain4j integration.
     * Used by PudelAgentService to provide tools to the agent.
     */
    public List<Object> getToolsForAgent(AgentToolContext context) {
        return tools.values().stream()
                .filter(tool -> tool.isAvailableIn(context))
                .map(tool -> toolProviders.get(tool.getName()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Build a tool description string for the agent prompt.
     */
    public String buildToolDescriptions(AgentToolContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Available tools:\n\n");

        tools.values().stream()
                .filter(tool -> tool.isAvailableIn(context))
                .sorted(Comparator.comparingInt(ToolDefinition::getPriority).reversed())
                .forEach(tool -> {
                    sb.append("- **").append(tool.getName()).append("**: ");
                    sb.append(tool.getDescription()).append("\n");
                    if (!tool.getParameters().isEmpty()) {
                        sb.append("  Parameters: ");
                        tool.getParameters().forEach(p ->
                                sb.append(p.name()).append(" (").append(p.getTypeName()).append("), "));
                        sb.setLength(sb.length() - 2); // Remove trailing ", "
                        sb.append("\n");
                    }
                });

        return sb.toString();
    }
}
