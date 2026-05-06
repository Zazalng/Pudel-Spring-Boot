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

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a slash command handler.
 * The method must accept a single parameter of type {@code SlashCommandInteractionEvent}.
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * @SlashCommand(name = "ping", description = "Check bot latency")
 * public void ping(SlashCommandInteractionEvent event) {
 *     long ping = event.getJDA().getGatewayPing();
 *     event.reply("Pong! " + ping + "ms").queue();
 * }
 * }</pre>
 *
 * <h2>With Options:</h2>
 * <pre>{@code
 * @SlashCommand(
 *     name = "greet",
 *     description = "Greet a user",
 *     options = {
 *         @CommandOption(name = "user", description = "User to greet", type = OptionType.USER, required = true)
 *     }
 * )
 * public void greet(SlashCommandInteractionEvent event) {
 *     User user = event.getOption("user").getAsUser();
 *     event.reply("Hello, " + user.getAsMention() + "!").queue();
 * }
 * }</pre>
 *
 * <p>The core automatically:</p>
 * <ul>
 *   <li>Registers the command when plugin is enabled</li>
 *   <li>Syncs to Discord (globally by default)</li>
 *   <li>Unregisters and re-syncs when plugin is disabled</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SlashCommand {

    /**
     * Command name (required).
     * Must be lowercase, 1-32 characters, no spaces.
     */
    String name();

    /**
     * Command description (required).
     * 1-100 characters.
     */
    String description();

    /**
     * Whether this is an NSFW command.
     * NSFW commands can only be used in channels designated as NSFW.
     * <p>
     * Defaults to {@code true}. Set this to {@code false} to mark the command as non-NSFW.
     */
    boolean nsfw() default true;

    /**
     * Command options.
     */
    CommandOption[] options() default {};

    /**
     * Subcommands.
     */
    Subcommand[] subcommands() default {};

    /**
     * Whether this is a global command.
     * <p>
     * Global commands take up to 1 hour to propagate.
     * Guild commands are instant but only work in guilds the bot has been added to.
     * <p>
     * Defaults to {@code true}. Plugin commands are registered globally so they
     * are available everywhere the bot is installed. Use {@link #integrationTo()}
     * and {@link #integrationContext()} to control where the command can be used.
     * <p>
     * Set to {@code false} if you need guild-specific registration (e.g., with
     * {@link #guildIds()}) for testing or restricted access.
     */
    boolean global() default true;

    /**
     * Guild IDs where this command should be registered.
     * Only used if {@link #global()} is set to {@code false}.
     * Empty array = all guilds the bot is in.
     */
    long[] guildIds() default {};

    /**
     * Required permissions for the command.
     * Uses Discord permission names.
     */
    Permission[] permissions() default {};

    /**
     * The installation scopes where this command is available.
     * <p>
     * Defaults to {@link IntegrationType#GUILD_INSTALL}.
     * Use this to determine if the command is installed to a server or a user's account.
     *
     * @see IntegrationType
     */
    IntegrationType[] integrationTo() default {IntegrationType.GUILD_INSTALL};

    /**
     * The context scopes where this command is available.
     * <p>
     * Defaults to {@link InteractionContextType#GUILD}.
     * Use this to determine if the command should be interacted with context scopes.
     *
     * @see InteractionContextType
     */
    InteractionContextType[] integrationContext() default {InteractionContextType.GUILD};
}