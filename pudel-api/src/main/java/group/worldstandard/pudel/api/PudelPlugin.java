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
package group.worldstandard.pudel.api;

import group.worldstandard.pudel.api.annotation.Plugin;

/**
 * @deprecated This interface is deprecated and will be removed in the next major version.
 * <p>
 * Use the annotation-based approach instead:
 * <pre>{@code
 * @Plugin(
 *     name = "MyPlugin",
 *     version = "1.0.0",
 *     author = "Author",
 *     description = "Description"
 * )
 * public class MyPlugin {
 *
 *     @SlashCommand(name = "ping", description = "Pong!")
 *     public void ping(SlashCommandInteractionEvent event) {
 *         event.reply("Pong!").queue();
 *     }
 *
 *     @OnEnable
 *     public void onEnable(PluginContext ctx) {
 *         ctx.log("info", "Enabled!");
 *     }
 *
 *     @OnDisable
 *     public void onDisable(PluginContext ctx) {
 *         ctx.log("info", "Disabled!");
 *     }
 *
 *     @OnShutdown
 *     public boolean shutdown(PluginContext ctx) {
 *         // Cleanup resources
 *         return true; // or false to force-kill
 *     }
 * }
 * }</pre>
 *
 * @see Plugin The new annotation-based plugin marker
 */
@Deprecated(since = "2.0.0", forRemoval = true)
public interface PudelPlugin {

    /**
     * @deprecated Use {@link Plugin#name()} annotation instead.
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    PluginInfo getPluginInfo();

    /**
     * @deprecated This method is no longer called. Use {@code @OnEnable} annotation instead.
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    default void initialize(PluginContext context) {
    }

    /**
     * @deprecated Use {@code @OnEnable} annotation instead.
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    default void onEnable(PluginContext context) {
    }

    /**
     * @deprecated Use {@code @OnDisable} annotation instead.
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    default void onDisable(PluginContext context) {
    }

    /**
     * @deprecated Use {@code @OnShutdown} annotation instead.
     * The new annotation supports returning boolean to indicate success.
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    default void shutdown(PluginContext context) {
    }
}
