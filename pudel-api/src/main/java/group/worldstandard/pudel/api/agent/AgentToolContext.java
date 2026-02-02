/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 Napapon Kamanee
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

import java.util.Map;

/**
 * Context passed to agent tools when executed.
 * Provides information about the current execution environment.
 */
public interface AgentToolContext {

    /**
     * Get the ID of the guild (if in guild context) or user (if in DM).
     * @return the target ID
     */
    long getTargetId();

    /**
     * Check if this is a guild context.
     * @return true if guild, false if DM
     */
    boolean isGuild();

    /**
     * Get the ID of the user who triggered the agent.
     * @return the requesting user's ID
     */
    long getRequestingUserId();

    /**
     * Get the guild ID (returns 0 if not in guild).
     * @return guild ID or 0
     */
    default long getGuildId() {
        return isGuild() ? getTargetId() : 0L;
    }

    /**
     * Get the user ID for DM context.
     * @return user ID if in DM, or the requesting user ID
     */
    default long getUserId() {
        return isGuild() ? getRequestingUserId() : getTargetId();
    }

    /**
     * Get additional context data provided by the agent system.
     * May include things like channel ID, message ID, etc.
     * @return context data map (may be empty, never null)
     */
    Map<String, Object> getContextData();

    /**
     * Get a specific context value.
     * @param key the context key
     * @return the value or null if not present
     */
    default Object getContextValue(String key) {
        return getContextData().get(key);
    }

    /**
     * Get a typed context value.
     * @param key the context key
     * @param type the expected type
     * @return the value cast to the type, or null
     * @param <T> the type
     */
    @SuppressWarnings("unchecked")
    default <T> T getContextValue(String key, Class<T> type) {
        Object value = getContextData().get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
}
