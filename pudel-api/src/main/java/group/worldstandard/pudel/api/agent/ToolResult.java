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

/**
 * Result of executing an agent tool.
 * <p>
 * Returned by the agent tool execution pipeline. Use the static factory
 * methods to create results:
 * <pre>
 * // In an @AgentTool method
 * return ToolResult.success("my_tool", "Result data", elapsed);
 * return ToolResult.failure("my_tool", "Something went wrong", elapsed);
 * </pre>
 *
 * @param success         whether the execution was successful
 * @param result          the result message (shown to user via agent)
 * @param error           error message if the execution failed
 * @param toolName        the name of the tool that was executed
 * @param executionTimeMs execution time in milliseconds
 */
public record ToolResult(
        boolean success,
        String result,
        String error,
        String toolName,
        long executionTimeMs
) {
    /**
     * Create a successful result.
     */
    public static ToolResult success(String toolName, String result, long executionTimeMs) {
        return new ToolResult(true, result, null, toolName, executionTimeMs);
    }

    /**
     * Create a failed result.
     */
    public static ToolResult failure(String toolName, String error, long executionTimeMs) {
        return new ToolResult(false, null, error, toolName, executionTimeMs);
    }

    /**
     * Create a "tool not found" result.
     */
    public static ToolResult notFound(String toolName) {
        return new ToolResult(false, null, "Tool not found: " + toolName, toolName, 0);
    }

    /**
     * Create a "not available" result (e.g., guild-only tool in DM).
     */
    public static ToolResult notAvailable(String toolName, String reason) {
        return new ToolResult(false, null, "Tool not available: " + reason, toolName, 0);
    }

    /**
     * Create a "permission denied" result.
     */
    public static ToolResult permissionDenied(String toolName, String reason) {
        return new ToolResult(false, null, "Permission denied: " + reason, toolName, 0);
    }
}
