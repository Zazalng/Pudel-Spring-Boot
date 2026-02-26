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
package group.worldstandard.pudel.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to be called when the plugin is being unloaded/shutdown.
 * <p>
 * Unlike {@link OnDisable}, this is called when the plugin is being completely
 * removed (not just disabled). Use this for final cleanup like closing database
 * connections, stopping threads, clearing caches, etc.
 * <p>
 * The method can optionally return a boolean:
 * <ul>
 *   <li>{@code true} - Shutdown completed successfully, core can proceed with unload</li>
 *   <li>{@code false} - Shutdown had issues, core will force-kill the plugin process</li>
 * </ul>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * @OnShutdown
 * public boolean shutdown(PluginContext context) {
 *     try {
 *         // Close database connections
 *         database.close();
 *         // Stop executor services
 *         executor.shutdownNow();
 *         // Clear caches
 *         cache.clear();
 *
 *         context.log("info", "Plugin shutdown complete");
 *         return true; // Success
 *     } catch (Exception e) {
 *         context.log("error", "Shutdown failed: " + e.getMessage());
 *         return false; // Core will force-kill
 *     }
 * }
 * }</pre>
 *
 * <p>The method can optionally accept a PluginContext parameter.
 * If no return value is specified (void), it's treated as successful.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OnShutdown {
}
