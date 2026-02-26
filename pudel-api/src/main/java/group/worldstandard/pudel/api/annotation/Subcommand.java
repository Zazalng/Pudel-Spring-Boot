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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a subcommand for slash commands.
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * @SlashCommand(
 *     name = "config",
 *     description = "Plugin configuration",
 *     subcommands = {
 *         @Subcommand(name = "view", description = "View settings"),
 *         @Subcommand(name = "set", description = "Change setting", options = {
 *             @CommandOption(name = "key", description = "Setting name", required = true),
 *             @CommandOption(name = "value", description = "New value", required = true)
 *         })
 *     }
 * )
 * public void config(SlashCommandInteractionEvent event) {
 *     String sub = event.getSubcommandName();
 *     // ...
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface Subcommand {

    /**
     * Subcommand name (required).
     */
    String name();

    /**
     * Subcommand description (required).
     */
    String description();

    /**
     * Subcommand options.
     */
    CommandOption[] options() default {};
}
