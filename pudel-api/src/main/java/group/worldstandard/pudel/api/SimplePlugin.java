/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 World Standard.group
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
 * @deprecated This class is deprecated and will be removed in the next major version.
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
 *     @TextCommand("hello")
 *     public void hello(CommandContext ctx) {
 *         ctx.reply("Hello!");
 *     }
 *
 *     @OnEnable
 *     public void onEnable(PluginContext ctx) {
 *         ctx.log("info", "Enabled!");
 *     }
 * }
 * }</pre>
 *
 * @see Plugin The new annotation-based plugin marker
 */
@Deprecated(since = "2.0.0", forRemoval = true)
public abstract class SimplePlugin implements PudelPlugin {

    private final PluginInfo info;

    @Deprecated
    protected SimplePlugin(String name, String version, String author, String description) {
        this.info = new PluginInfo(name, version, author, description);
    }

    @Override
    @Deprecated
    public final PluginInfo getPluginInfo() {
        return info;
    }

    @Override
    @Deprecated
    public void onEnable(PluginContext context) {
        // Deprecated - use @OnEnable annotation
    }

    @Override
    @Deprecated
    public void onDisable(PluginContext context) {
        // Deprecated - use @OnDisable annotation
    }
}
