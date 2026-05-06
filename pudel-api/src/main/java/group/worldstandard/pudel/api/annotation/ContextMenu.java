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

import net.dv8tion.jda.api.interactions.commands.Command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a context menu handler.
 * <p>
 * Context menus appear when right-clicking on users or messages in Discord.
 * The method will be called with either a {@link net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent}
 * or {@link net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent}.
 *
 * <h2>User Context Menu Example:</h2>
 * <pre>{@code
 * @ContextMenu(name = "Get User Info", type = Command.Type.USER)
 * public void getUserInfo(UserContextInteractionEvent event) {
 *     User target = event.getTarget();
 *     event.reply("User: " + target.getAsTag() + "\nID: " + target.getId()).queue();
 * }
 * }</pre>
 *
 * <h2>Message Context Menu Example:</h2>
 * <pre>{@code
 * @ContextMenu(name = "Bookmark Message", type = Command.Type.MESSAGE)
 * public void bookmarkMessage(MessageContextInteractionEvent event) {
 *     Message message = event.getTarget();
 *     // Save bookmark...
 *     event.reply("Message bookmarked!").setEphemeral(true).queue();
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
public @interface ContextMenu {

    /**
     * Context menu name (required).
     * Must be 1-32 characters.
     */
    String name();

    /**
     * Command type: USER or MESSAGE (required).
     * <p>
     * USER context menus are invoked on users (right-click user → Apps → Your Command).
     * MESSAGE context menus are invoked on messages (right-click message → Apps → Your Command).
     */
    Command.Type type();

    /**
     * Whether this is a global context menu.
     * <p>
     * Global context menus take up to 1 hour to propagate.
     * Guild-specific context menus are instant but only work in specified guilds.
     * <p>
     * Defaults to {@code true}.
     */
    boolean global() default true;

    /**
     * Guild IDs where this context menu should be registered.
     * Only used if {@link #global()} is set to {@code false}.
     * Empty array = all guilds the bot is in.
     */
    long[] guildIds() default {};
}