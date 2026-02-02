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
package group.worldstandard.pudel.api.interaction;

import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

/**
 * Handler for context menu commands.
 * <p>
 * Context menus appear when right-clicking on users or messages.
 * <p>
 * Example (User Context Menu):
 * <pre>
 * public class UserInfoContextMenu implements ContextMenuHandler {
 *     &#064;Override
 *     public CommandData getCommandData() {
 *         return Commands.user("Get User Info");
 *     }
 *
 *     &#064;Override
 *     public void handleUserContext(UserContextInteractionEvent event) {
 *         User target = event.getTarget();
 *         event.reply("User: " + target.getAsTag() + "\nID: " + target.getId()).queue();
 *     }
 * }
 * </pre>
 * <p>
 * Example (Message Context Menu):
 * <pre>
 * public class BookmarkContextMenu implements ContextMenuHandler {
 *     &#064;Override
 *     public CommandData getCommandData() {
 *         return Commands.message("Bookmark Message");
 *     }
 *
 *     &#064;Override
 *     public void handleMessageContext(MessageContextInteractionEvent event) {
 *         Message message = event.getTarget();
 *         // Save bookmark...
 *         event.reply("Message bookmarked!").setEphemeral(true).queue();
 *     }
 * }
 * </pre>
 */
public interface ContextMenuHandler {

    /**
     * Get the context menu command data.
     * <p>
     * Use {@link net.dv8tion.jda.api.interactions.commands.build.Commands#user(String)}
     * for user context menus, or
     * {@link net.dv8tion.jda.api.interactions.commands.build.Commands#message(String)}
     * for message context menus.
     *
     * @return the command data
     */
    CommandData getCommandData();

    /**
     * Handle user context menu interaction.
     * <p>
     * Called when this command is used on a user (right-click → Apps → Your Command).
     *
     * @param event the user context interaction event
     */
    default void handleUserContext(UserContextInteractionEvent event) {
        event.reply("This context menu is not implemented for users.").setEphemeral(true).queue();
    }

    /**
     * Handle message context menu interaction.
     * <p>
     * Called when this command is used on a message (right-click → Apps → Your Command).
     *
     * @param event the message context interaction event
     */
    default void handleMessageContext(MessageContextInteractionEvent event) {
        event.reply("This context menu is not implemented for messages.").setEphemeral(true).queue();
    }

    /**
     * Whether this command should be registered globally.
     *
     * @return true for global registration
     */
    default boolean isGlobal() {
        return true;
    }

    /**
     * Get guild IDs for guild-specific registration.
     *
     * @return array of guild IDs, or null for all guilds
     */
    default long[] getGuildIds() {
        return null;
    }
}
