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
package group.worldstandard.pudel.api.event;

/**
 * Marker interface for plugin event listener classes.
 * <p>
 * Implement this interface and annotate methods with
 * {@link EventHandler @EventHandler} to receive JDA events.
 * The method parameter type determines which event is handled.
 * <p>
 * Example:
 * <pre>
 * {@code
 * public class MyListener implements Listener {
 *
 *     {@code @EventHandler(priority = EventPriority.NORMAL)}
 *     public void onMessage(MessageReceivedEvent event) {
 *         // Handle message received
 *     }
 *
 *     {@code @EventHandler}
 *     public void onReaction(MessageReactionAddEvent event) {
 *         // Handle reaction added
 *     }
 * }
 * }
 * </pre>
 *
 * @see EventHandler
 * @see EventPriority
 */
public interface Listener {
    // Marker interface - no methods required
}