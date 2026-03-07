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

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;

/**
 * Handler for slash command autocomplete.
 * <p>
 * Provides dynamic suggestions as users type command options.
 * <p>
 * Implement this interface and register via {@link InteractionManager}:
 * <pre>
 * public class CityAutocomplete implements AutoCompleteHandler {
 *     &#064;Override
 *     public String getCommandName() {
 *         return "weather";
 *     }
 *
 *     &#064;Override
 *     public String getOptionName() {
 *         return "city";
 *     }
 *
 *     &#064;Override
 *     public void handle(CommandAutoCompleteInteractionEvent event) {
 *         String input = event.getFocusedOption().getValue();
 *         List&lt;String&gt; cities = findCitiesMatching(input);
 *
 *         List&lt;Command.Choice&gt; choices = cities.stream()
 *             .limit(25)
 *             .map(city -> new Command.Choice(city, city))
 *             .toList();
 *
 *         event.replyChoices(choices).queue();
 *     }
 * }
 * </pre>
 * <p>
 * Register in your {@code @Plugin} class:
 * <pre>
 * {@code @OnEnable}
 * public void onEnable(PluginContext context) {
 *     context.getInteractionManager().registerAutoCompleteHandler("my-plugin", new CityAutocomplete());
 * }
 * </pre>
 */
public interface AutoCompleteHandler {

    /**
     * Get the command name this autocomplete handles.
     *
     * @return the slash command name
     */
    String getCommandName();

    /**
     * Get the option name this autocomplete handles.
     * <p>
     * This is the name of the option that has autocomplete enabled.
     *
     * @return the option name
     */
    String getOptionName();

    /**
     * Handle the autocomplete request.
     * <p>
     * Use {@code event.getFocusedOption().getValue()} to get what the user typed.
     * Reply with up to 25 choices using {@code event.replyChoices(...)}.
     *
     * @param event the autocomplete event
     */
    void handle(CommandAutoCompleteInteractionEvent event);
}
