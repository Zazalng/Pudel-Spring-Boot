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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a command option for slash commands.
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * @SlashCommand(
 *     name = "ban",
 *     description = "Ban a user",
 *     options = {
 *         @CommandOption(name = "user", description = "User to ban", type = OptionType.USER, required = true),
 *         @CommandOption(name = "reason", description = "Ban reason", type = OptionType.STRING, required = false)
 *     }
 * )
 * public void ban(SlashCommandInteractionEvent event) {
 *     // ...
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface CommandOption {

    /**
     * Option name (required).
     */
    String name();

    /**
     * Option description (required).
     */
    String description();

    /**
     * Option type.
     * Use JDA's OptionType enum values as strings:
     * STRING, INTEGER, BOOLEAN, USER, CHANNEL, ROLE, MENTIONABLE, NUMBER, ATTACHMENT
     */
    String type() default "STRING";

    /**
     * Whether this option is required.
     */
    boolean required() default false;

    /**
     * Predefined choices for this option.
     */
    Choice[] choices() default {};

    /**
     * Minimum value (for INTEGER and NUMBER types).
     */
    double min() default Double.MIN_VALUE;

    /**
     * Maximum value (for INTEGER and NUMBER types).
     */
    double max() default Double.MAX_VALUE;

    /**
     * Whether this option supports autocomplete.
     */
    boolean autocomplete() default false;
}
