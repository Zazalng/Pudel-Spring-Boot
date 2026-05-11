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

import net.dv8tion.jda.api.interactions.commands.OptionType;

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
     * @return name of option.
     */
    String name();

    /**
     * @return description of option.
     */
    String description();

    /**
     * Integrated type of Option used for auto-complete when performed.
     * <p>
     * Use JDA's {@link OptionType} enum, for example:
     * STRING, INTEGER, BOOLEAN, USER, CHANNEL, ROLE, MENTIONABLE, NUMBER, ATTACHMENT
     *
     * @return {@link OptionType} passed to the handler. Default {@link OptionType#STRING}
     */
    OptionType type() default OptionType.STRING;

    /**
     * @return Whether this option is required. Default {@link Boolean#FALSE}
     */
    boolean required() default false;

    /**
     * @return Array of predefined choices for this option. Default empty array.
     */
    Choice[] choices() default {};

    /**
     * @return Minimum value (for INTEGER and NUMBER types). Default {@link Double#MIN_VALUE}
     */
    double min() default Double.MIN_VALUE;

    /**
     * @return Maximum value (for INTEGER and NUMBER types). Default {@link Double#MAX_VALUE}
     */
    double max() default Double.MAX_VALUE;

    /**
     * @return Whether this option supports auto-completion. Default {@link Boolean#FALSE}
     */
    boolean autocomplete() default false;
}