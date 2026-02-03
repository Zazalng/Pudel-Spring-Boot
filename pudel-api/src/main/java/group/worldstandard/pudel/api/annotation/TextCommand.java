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
 * Marks a method as a text command handler.
 * The method must accept a single parameter of type {@code CommandContext}.
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * @TextCommand("hello")
 * public void hello(CommandContext ctx) {
 *     ctx.reply("Hello, " + ctx.getUser().getName() + "!");
 * }
 * }</pre>
 *
 * <h2>With Aliases:</h2>
 * <pre>{@code
 * @TextCommand(value = "help", aliases = {"h", "?"})
 * public void help(CommandContext ctx) {
 *     ctx.reply("Available commands: ...");
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TextCommand {

    /**
     * Command name (required).
     */
    String value();

    /**
     * Command aliases.
     */
    String[] aliases() default {};

    /**
     * Command description for help.
     */
    String description() default "";

    /**
     * Usage example.
     */
    String usage() default "";
}
