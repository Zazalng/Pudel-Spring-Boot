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
     * Creates a successful tool execution result.
     *
     * @param toolName        the name of the tool that was executed
     * @param result          the result message to be shown to the user via the agent
     * @param executionTimeMs the execution time in milliseconds
     * @return a ToolResult instance representing a successful execution
     */
    public static ToolResult success(String toolName, String result, long executionTimeMs) {
        return new ToolResult(true, result, null, toolName, executionTimeMs);
    }

    /**
     * Creates a ToolResult representing a failed tool execution.
     *
     * @param toolName        the name of the tool that was executed
     * @param error           error message describing the failure
     * @param executionTimeMs execution time in milliseconds
     * @return a ToolResult instance indicating failure with the provided details
     */
    public static ToolResult failure(String toolName, String error, long executionTimeMs) {
        return new ToolResult(false, null, error, toolName, executionTimeMs);
    }

    /**
     * Creates a ToolResult indicating that the specified tool was not found.
     *
     * @param toolName the name of the tool that was not found
     * @return a ToolResult instance representing a tool not found error
     */
    public static ToolResult notFound(String toolName) {
        return new ToolResult(false, null, "Tool not found: " + toolName, toolName, 0);
    }

    /**
     * Creates a ToolResult indicating that a tool is not available for execution.
     * <p>
     * This method is typically used when a tool cannot be executed due to temporary
     * unavailability, maintenance, or other transient issues.
     *
     * @param toolName the name of the tool that is not available
     * @param reason   the reason why the tool is not available
     * @return a ToolResult instance representing the unavailability of the tool
     */
    public static ToolResult notAvailable(String toolName, String reason) {
        return new ToolResult(false, null, "Tool not available: " + reason, toolName, 0);
    }

    /**
     * Creates a ToolResult indicating that the tool execution was denied due to insufficient permissions.
     *
     * @param toolName the name of the tool that was denied
     * @param reason   the reason why permission was denied
     * @return a ToolResult representing a permission denied outcome
     */
    public static ToolResult permissionDenied(String toolName, String reason) {
        return new ToolResult(false, null, "Permission denied: " + reason, toolName, 0);
    }
}