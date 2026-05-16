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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR,
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package group.worldstandard.pudel.api.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an MCP (Model Context Protocol) tool.
 * <p>
 * MCP tools are exposed to the LLM and can be called during completion.
 * They replace the legacy {@link AgentTool} annotation with a more
 * structured approach compatible with the Model Context Protocol.
 * <p>
 * Example:
 * <pre>
 * {@code
 *    @McpTool(
 *        name = "get_weather",
 *        description = "Get current weather for a location",
 *        parameters = "{\"type\":\"object\",\"properties\":{\"location\":{\"type\":\"string\"}}}"
 *    )
 *    public String getWeather(AgentToolContext ctx, String location) {
 *        return "Weather in " + location + ": Sunny, 25°C";
 *    }
 * }
 * </pre>
 *
 * Use this instead of the legacy @AgentTool for new tools.
 * The old @AgentTool is kept for backward compatibility.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpTool {
    /** The tool name used by the LLM to invoke it. */
    String name();

    /** Human-readable description of what the tool does. */
    String description();

    /**
     * JSON Schema describing the tool's parameters.
     * Should be a valid JSON Schema object string.
     */
    String parameters() default "";

    /** Keywords for tool discovery. */
    String[] keywords() default {};

    /** Whether this tool only works in guild channels. */
    boolean guildOnly() default false;

    /** Whether this tool only works in DMs. */
    boolean dmOnly() default false;

    /** Required permission level. */
    AgentTool.ToolPermission permission() default AgentTool.ToolPermission.EVERYONE;

    /** Tool priority (higher = more important). */
    int priority() default 0;
}

