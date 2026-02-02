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

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/**
 * Handler for slash command interactions.
 * <p>
 * Plugins implement this interface to handle slash commands.
 * <p>
 * Example:
 * <pre>
 * public class PingCommand implements SlashCommandHandler {
 *     &#064;Override
 *     public SlashCommandData getCommandData() {
 *         return Commands.slash("ping", "Check bot latency");
 *     }
 *
 *     &#064;Override
 *     public void handle(SlashCommandInteractionEvent event) {
 *         long ping = event.getJDA().getGatewayPing();
 *         event.reply("Pong! " + ping + "ms").queue();
 *     }
 * }
 * </pre>
 */
public interface SlashCommandHandler {

    /**
     * Get the slash command data for registration.
     * <p>
     * Use {@link net.dv8tion.jda.api.interactions.commands.build.Commands#slash(String, String)}
     * to create the command data.
     *
     * @return the slash command data
     */
    SlashCommandData getCommandData();

    /**
     * Handle the slash command interaction.
     * <p>
     * You must respond to the interaction within 3 seconds using one of:
     * - {@code event.reply(...)}
     * - {@code event.deferReply()} followed by {@code event.getHook().sendMessage(...)}
     *
     * @param event the slash command event
     */
    void handle(SlashCommandInteractionEvent event);

    /**
     * Whether this command should be registered globally or per-guild.
     * <p>
     * Global commands can take up to 1 hour to propagate.
     * Guild commands are instant but only work in that guild.
     *
     * @return true for global registration, false for guild-only
     */
    default boolean isGlobal() {
        return true;
    }

    /**
     * Get the guild IDs where this command should be registered.
     * <p>
     * Only used if {@link #isGlobal()} returns false.
     * Return null or empty array to register in all guilds the bot is in.
     *
     * @return array of guild IDs, or null for all guilds
     */
    default long[] getGuildIds() {
        return null;
    }
}
