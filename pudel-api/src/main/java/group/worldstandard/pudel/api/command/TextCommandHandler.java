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
 * Functional interface for handling text (prefix-based) commands.
 * <p>
 * <b>Preferred approach:</b> Use the {@code @TextCommand} annotation directly on methods
 * in your {@code @Plugin} class:
 * <pre>
 * {@code @Plugin(name = "MyPlugin", version = "1.0.0", author = "Author")}
 * public class MyPlugin {
 *
 *     {@code @TextCommand(name = "greet", description = "Greet a user",
 *             usage = "greet <user>", aliases = {"hi", "hello"})}
 *     public void greet(CommandContext context) {
 *         context.reply("Hello, " + context.getArgsString() + "!");
 *     }
 * }
 * </pre>
 * <p>
 * <b>Alternative:</b> Implement this interface and register via
 * {@link group.worldstandard.pudel.api.PluginContext#registerCommand(String, TextCommandHandler)}
 * for dynamic command registration:
 * <pre>
 * {@code @OnEnable}
 * public void onEnable(PluginContext context) {
 *     context.registerCommand("greet", ctx -&gt;
 *         ctx.reply("Hello, " + ctx.getArgsString() + "!"));
 * }
 * </pre>
 */
@FunctionalInterface
public interface TextCommandHandler {

    /**
     * Handles a text command invocation.
     *
     * @param context the command context containing the event, arguments, and convenience methods
     */
    void handle(CommandContext context);
}

