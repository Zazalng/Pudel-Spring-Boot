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

import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;

/**
 * Handler for select menu (dropdown) interactions.
 * <p>
 * Plugins implement this interface to handle select menu selections.
 * <p>
 * Example:
 * <pre>
 * public class MySelectHandler implements SelectMenuHandler {
 *     &#064;Override
 *     public String getSelectMenuIdPrefix() {
 *         return "myplugin:select:";
 *     }
 *
 *     &#064;Override
 *     public void handleStringSelect(StringSelectInteractionEvent event) {
 *         List&lt;String&gt; values = event.getValues();
 *         event.reply("You selected: " + String.join(", ", values)).queue();
 *     }
 * }
 * </pre>
 */
public interface SelectMenuHandler {

    /**
     * Get the select menu ID prefix this handler responds to.
     *
     * @return the select menu ID prefix
     */
    String getSelectMenuIdPrefix();

    /**
     * Handle string select menu interaction.
     * <p>
     * Called when a user selects options from a string select menu
     * (dropdown with predefined string options).
     *
     * @param event the string select interaction event
     */
    default void handleStringSelect(StringSelectInteractionEvent event) {
        // Default no-op - override to handle
    }

    /**
     * Handle entity select menu interaction.
     * <p>
     * Called when a user selects entities (users, roles, channels, etc.)
     * from an entity select menu.
     *
     * @param event the entity select interaction event
     */
    default void handleEntitySelect(EntitySelectInteractionEvent event) {
        // Default no-op - override to handle
    }
}
