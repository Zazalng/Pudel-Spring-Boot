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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an AI Agent Tool.
 * <p>
 * Methods annotated with @AgentTool will be available for the AI agent to use
 * when processing user requests. The agent will call these tools based on
 * the user's intent and the tool description.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * public class MyPluginTools implements AgentToolProvider {
 *
 *     @AgentTool(
 *         name = "get_weather",
 *         description = "Get the current weather for a location",
 *         keywords = {"weather", "temperature", "forecast"}
 *     )
 *     public String getWeather(AgentToolContext context, String location) {
 *         return "The weather in " + location + " is sunny, 25°C";
 *     }
 * }
 * }
 * </pre>
 * <p>
 * Tool methods must:
 * <ui>
 *     <li>Have {@link AgentToolContext} as the first parameter</li>
 *     <li>Return a String (the tool's response to the agent)</li>
 *     <li>Only use primitive types, String, or simple objects for other parameters</li>
 * </ui>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AgentTool {

    /**
     * The unique name of the tool.
     * If not specified, the method name will be used.
     * @return tool name
     */
    String name() default "";

    /**
     * Description of what the tool does.
     * This is shown to the AI agent to help it decide when to use the tool.
     * Should be clear and concise.
     * @return tool description
     */
    String description();

    /**
     * Keywords that help identify when this tool should be used.
     * The agent may use these to better match user intent.
     * @return array of keywords
     */
    String[] keywords() default {};

    /**
     * Whether this tool requires guild context.
     * If true, the tool will only be available in guild channels.
     * @return true if guild-only
     */
    boolean guildOnly() default false;

    /**
     * Whether this tool requires DM context.
     * If true, the tool will only be available in direct messages.
     * @return true if DM-only
     */
    boolean dmOnly() default false;

    /**
     * Permission level required to use this tool.
     * Can be used for access control.
     * @return required permission level
     */
    ToolPermission permission() default ToolPermission.EVERYONE;

    /**
     * Priority for tool selection when multiple tools match.
     * Higher priority tools are preferred.
     * @return priority (default 0)
     */
    int priority() default 0;

    /**
     * Permission levels for agent tools.
     */
    enum ToolPermission {
        /** Anyone can use this tool */
        EVERYONE,
        /** Only guild members with manage permissions */
        GUILD_MANAGER,
        /** Only guild administrators */
        GUILD_ADMIN,
        /** Only the guild owner */
        GUILD_OWNER,
        /** Only bot administrators */
        BOT_ADMIN
    }
}
