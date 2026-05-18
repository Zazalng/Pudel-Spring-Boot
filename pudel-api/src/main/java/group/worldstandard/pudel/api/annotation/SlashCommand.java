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
     * Returns the name of the slash command.
     * This name is used to identify and invoke the command in Discord.
     * Command names must be unique within a guild or globally, depending on registration scope.
     *
     * @return the name of the command as a String; Max 32 characters with no backspace
     */
    String name();

    /**
     * Returns the description of the slash command.
     * This description is displayed to users in the Discord client when they view the command.
     * Command descriptions must be concise and informative, explaining what the command does.
     *
     * @return the description of the command as a String; Max 100 characters
     */
    String description();

    /**
     * Indicates whether the slash command is NSFW (Not Safe For Work).
     * If set to true, the command will only be available in channels marked as NSFW.
     * Defaults to {@code true}.
     *
     * @return {@code true} if the command is NSFW, {@code false} otherwise
     */
    boolean nsfw() default true;

    /**
     * Returns the command options for this slash command.
     * Command options define the parameters that users can provide when invoking the command.
     * Each option includes a name, description, type, and other constraints such as whether it is required,
     * allowed choices, minimum/maximum values, and support for auto-completion.
     *
     * @return an array of {@link CommandOption} annotations defining the command's parameters; defaults to an empty array
     */
    CommandOption[] options() default {};

    /**
     * Returns the subcommands associated with this slash command.
     * Subcommands allow a single slash command to have multiple related actions,
     * each with their own name, description, and options.
     *
     * @return an array of {@link Subcommand} annotations defining the subcommands; defaults to an empty array
     */
    Subcommand[] subcommands() default {};

    /**
     * Indicates whether the slash command is available globally or restricted to specific guilds.
     * If set to {@code true}, the command will be registered globally across all guilds.
     * If set to {@code false}, the command will only be registered in the guilds specified by {@link #guildIds()}.
     * Defaults to {@code true}.
     *
     * @return {@code true} if the command is global, {@code false} if it is guild-specific
     */
    boolean global() default true;

    /**
     * Returns the IDs of the guilds where this slash command is available.
     * If the command is guild-specific (i.e., {@link #global()} returns {@code false}),
     * this method specifies the exact guilds in which the command will be registered.
     * If {@link #global()} returns {@code true}, this method has no effect.
     *
     * @return an array of guild IDs as long values; defaults to an empty array
     */
    long[] guildIds() default {};

    /**
     * Returns the permissions required to execute the slash command.
     * These permissions define the minimum set of privileges that a user must have
     * in the guild or channel to successfully invoke the command.
     * If the command is global, these permissions apply across all contexts where the command is available.
     * If the command is guild-specific, these permissions are enforced within the specified guilds.
     *
     * @return an array of {@link Permission} enums representing the required permissions; defaults to an empty array
     */
    Permission[] permissions() default {};

    /**
     * Returns the integration types for which this slash command is available.
     * Integration types determine how and where the command can be installed and used.
     * By default, the command is integrated as a guild install type.
     *
     * @return an array of {@link IntegrationType} enums indicating the supported integration types; defaults to {@link IntegrationType#GUILD_INSTALL}
     */
    IntegrationType[] integrationTo() default {IntegrationType.GUILD_INSTALL};

    /**
     * Returns the interaction context types in which this slash command is available.
     * Interaction contexts define where the command can be used, such as in guilds, direct messages,
     * or other specific environments. This setting helps control the visibility and accessibility
     * of the command based on the user's current context.
     *
     * @return an array of {@link InteractionContextType} enums indicating the supported interaction contexts;
     *         defaults to {@link InteractionContextType#GUILD}
     */
    InteractionContextType[] integrationContext() default {InteractionContextType.GUILD};
}