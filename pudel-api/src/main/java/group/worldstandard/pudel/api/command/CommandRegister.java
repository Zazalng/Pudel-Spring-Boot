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
package group.worldstandard.pudel.api.command;

/**
 * Interface for programmatic text command registration and unregistration.
 * <p>
 * <b>Preferred approach:</b> Use the {@code @TextCommand} annotation on methods in
 * your {@code @Plugin} class. Commands defined via annotations are automatically
 * registered on enable and unregistered on disable.
 * <p>
 * This interface is used internally by the core and is available for plugins
 * that need dynamic command registration at runtime.
 */
public interface CommandRegister {

    /**
     * Registers a text command handler.
     *
     * @param commandName the command name (without prefix)
     * @param handler the command handler
     */
    void register(String commandName, TextCommandHandler handler);

    /**
     * Unregisters a text command handler.
     *
     * @param commandName the command name to unregister
     */
    void unregister(String commandName);
}


