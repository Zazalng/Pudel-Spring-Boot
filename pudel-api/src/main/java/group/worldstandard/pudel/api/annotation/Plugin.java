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
package group.worldstandard.pudel.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Pudel plugin.
 * Similar to Spring's @Controller or @Service annotations.
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * @Plugin(
 *     name = "MyPlugin",
 *     version = "1.0.0",
 *     author = "Developer",
 *     description = "A sample plugin"
 * )
 * public class MyPlugin {
 *
 *     @SlashCommand(name = "ping", description = "Check bot latency")
 *     public void ping(SlashCommandInteractionEvent event) {
 *         event.reply("Pong!").queue();
 *     }
 *
 *     @TextCommand("hello")
 *     public void hello(CommandContext ctx) {
 *         ctx.reply("Hello, " + ctx.getUser().getName() + "!");
 *     }
 * }
 * }</pre>
 *
 * <p>The core will automatically:</p>
 * <ul>
 *   <li>Discover annotated methods</li>
 *   <li>Register commands on enable</li>
 *   <li>Unregister commands on disable</li>
 *   <li>Sync slash commands to Discord</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Plugin {

    /**
     * Plugin name (required).
     */
    String name();

    /**
     * Plugin version.
     */
    String version() default "1.0.0";

    /**
     * Plugin author.
     */
    String author() default "";

    /**
     * Plugin description.
     */
    String description() default "";
}
