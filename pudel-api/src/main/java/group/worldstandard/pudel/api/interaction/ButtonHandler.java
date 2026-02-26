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
package group.worldstandard.pudel.api.interaction;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

/**
 * Handler for button interactions.
 * <p>
 * Plugins implement this interface to handle button clicks.
 * Button IDs should be prefixed with your plugin name to avoid conflicts.
 * <p>
 * Example:
 * <pre>
 * public class MyButtonHandler implements ButtonHandler {
 *     &#064;Override
 *     public String getButtonIdPrefix() {
 *         return "myplugin:";
 *     }
 *
 *     &#064;Override
 *     public void handle(ButtonInteractionEvent event) {
 *         String buttonId = event.getComponentId();
 *         if (buttonId.equals("myplugin:confirm")) {
 *             event.reply("Confirmed!").setEphemeral(true).queue();
 *         }
 *     }
 * }
 * </pre>
 */
public interface ButtonHandler {

    /**
     * Get the button ID prefix this handler responds to.
     * <p>
     * The handler will receive all button interactions where the
     * component ID starts with this prefix.
     * <p>
     * Use a unique prefix like "pluginname:" to avoid conflicts.
     *
     * @return the button ID prefix
     */
    String getButtonIdPrefix();

    /**
     * Handle the button interaction.
     * <p>
     * You must respond to the interaction within 3 seconds using one of:
     * - {@code event.reply(...)}
     * - {@code event.deferReply()}
     * - {@code event.editMessage(...)}
     * - {@code event.deferEdit()}
     *
     * @param event the button interaction event
     */
    void handle(ButtonInteractionEvent event);
}
