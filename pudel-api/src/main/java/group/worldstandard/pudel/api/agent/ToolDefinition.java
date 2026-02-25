/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 World Standard.group
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

import java.util.*;
import java.util.function.BiFunction;

/**
 * Definition of an agent tool.
 * <p>
 * A tool definition contains all metadata about a tool and its executor.
 * Tools can be created either from @AgentTool annotated methods or
 * programmatically using the builder.
 * <p>
 * Example using builder:
 * <pre>
 * ToolDefinition tool = ToolDefinition.builder()
 *     .name("search_web")
 *     .description("Search the web for information")
 *     .parameter("query", String.class, "The search query", true)
 *     .parameter("limit", Integer.class, "Max results", false)
 *     .keyword("search")
 *     .keyword("find")
 *     .keyword("lookup")
 *     .executor((context, params) -> {
 *         String query = (String) params.get("query");
 *         int limit = (Integer) params.getOrDefault("limit", 5);
 *         return "Results for: " + query;
 *     })
 *     .build();
 * </pre>
 */
public final class ToolDefinition {

    private final String name;
    private final String description;
    private final String pluginId;
    private final List<ToolParameter> parameters;
    private final Set<String> keywords;
    private final boolean guildOnly;
    private final boolean dmOnly;
    private final AgentTool.ToolPermission permission;
    private final int priority;
    private final BiFunction<AgentToolContext, Map<String, Object>, String> executor;

    private ToolDefinition(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.pluginId = builder.pluginId;
        this.parameters = List.copyOf(builder.parameters);
        this.keywords = Set.copyOf(builder.keywords);
        this.guildOnly = builder.guildOnly;
        this.dmOnly = builder.dmOnly;
        this.permission = builder.permission;
        this.priority = builder.priority;
        this.executor = builder.executor;
    }

    // Getters

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getPluginId() {
        return pluginId;
    }

    public List<ToolParameter> getParameters() {
        return parameters;
    }

    public Set<String> getKeywords() {
        return keywords;
    }

    public boolean isGuildOnly() {
        return guildOnly;
    }

    public boolean isDmOnly() {
        return dmOnly;
    }

    public AgentTool.ToolPermission getPermission() {
        return permission;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Execute this tool with the given context and parameters.
     *
     * @param context the execution context
     * @param parameters the tool parameters
     * @return the tool result as a string
     */
    public String execute(AgentToolContext context, Map<String, Object> parameters) {
        if (executor == null) {
            throw new IllegalStateException("Tool '" + name + "' has no executor");
        }
        return executor.apply(context, parameters);
    }

    /**
     * Check if this tool is available in the given context.
     */
    public boolean isAvailableIn(AgentToolContext context) {
        if (guildOnly && !context.isGuild()) {
            return false;
        }
        if (dmOnly && context.isGuild()) {
            return false;
        }
        return true;
    }

    /**
     * Create a new builder for tool definitions.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for ToolDefinition.
     */
    public static class Builder {
        private String name;
        private String description;
        private String pluginId = "core";
        private final List<ToolParameter> parameters = new ArrayList<>();
        private final Set<String> keywords = new HashSet<>();
        private boolean guildOnly = false;
        private boolean dmOnly = false;
        private AgentTool.ToolPermission permission = AgentTool.ToolPermission.EVERYONE;
        private int priority = 0;
        private BiFunction<AgentToolContext, Map<String, Object>, String> executor;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder pluginId(String pluginId) {
            this.pluginId = pluginId;
            return this;
        }

        public Builder parameter(String name, Class<?> type, String description, boolean required) {
            this.parameters.add(new ToolParameter(name, type, description, required, null));
            return this;
        }

        public Builder parameter(String name, Class<?> type, String description, boolean required, Object defaultValue) {
            this.parameters.add(new ToolParameter(name, type, description, required, defaultValue));
            return this;
        }

        public Builder parameter(ToolParameter parameter) {
            this.parameters.add(parameter);
            return this;
        }

        public Builder keyword(String keyword) {
            this.keywords.add(keyword.toLowerCase());
            return this;
        }

        public Builder keywords(String... keywords) {
            for (String kw : keywords) {
                this.keywords.add(kw.toLowerCase());
            }
            return this;
        }

        public Builder keywords(Collection<String> keywords) {
            keywords.forEach(kw -> this.keywords.add(kw.toLowerCase()));
            return this;
        }

        public Builder guildOnly(boolean guildOnly) {
            this.guildOnly = guildOnly;
            return this;
        }

        public Builder dmOnly(boolean dmOnly) {
            this.dmOnly = dmOnly;
            return this;
        }

        public Builder permission(AgentTool.ToolPermission permission) {
            this.permission = permission;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder executor(BiFunction<AgentToolContext, Map<String, Object>, String> executor) {
            this.executor = executor;
            return this;
        }

        public ToolDefinition build() {
            Objects.requireNonNull(name, "Tool name is required");
            Objects.requireNonNull(description, "Tool description is required");
            Objects.requireNonNull(executor, "Tool executor is required");
            return new ToolDefinition(this);
        }
    }

    /**
     * Definition of a tool parameter.
     */
    public record ToolParameter(
            String name,
            Class<?> type,
            String description,
            boolean required,
            Object defaultValue
    ) {
        /**
         * Get the type name for LLM description.
         */
        public String getTypeName() {
            if (type == String.class) return "string";
            if (type == Integer.class || type == int.class) return "integer";
            if (type == Long.class || type == long.class) return "integer";
            if (type == Double.class || type == double.class) return "number";
            if (type == Float.class || type == float.class) return "number";
            if (type == Boolean.class || type == boolean.class) return "boolean";
            return "string";
        }
    }

    @Override
    public String toString() {
        return "ToolDefinition{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", pluginId='" + pluginId + '\'' +
                ", parameters=" + parameters.size() +
                '}';
    }
}
