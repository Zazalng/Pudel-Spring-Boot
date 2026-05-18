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

import java.util.Map;

/**
 * Functional interface for MCP tool execution.
 * <p>
 * MCP tools execute with a context and parameters, returning a string result
 * that is fed back to the LLM.
 *
 * @see McpToolDefinition
 */
@FunctionalInterface
public interface McpToolExecutor {
    /**
     * Execute the tool.
     *
     * @param context    the tool execution context (guild, user, channel info)
     * @param parameters the tool parameters provided by the LLM
     * @return the tool's result as a string
     */
    String execute(AgentToolContext context, Map<String, Object> parameters);
}

