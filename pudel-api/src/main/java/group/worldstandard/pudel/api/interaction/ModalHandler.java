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

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;

/**
 * Handler for modal (popup form) interactions.
 * <p>
 * Modals are popup forms that can collect text input from users.
 * <p>
 * <b>Preferred approach:</b> Use the {@code @ModalHandler} annotation directly on methods
 * in your {@code @Plugin} class:
 * <pre>
 * {@code @Plugin(name = "MyPlugin", version = "1.0.0", author = "Author")}
 * public class MyPlugin {
 *
 *     {@code @ModalHandler("myplugin:feedback")}
 *     public void handleFeedback(ModalInteractionEvent event) {
 *         String feedback = event.getValue("feedback-input").getAsString();
 *         event.reply("Thanks for your feedback: " + feedback).setEphemeral(true).queue();
 *     }
 * }
 * </pre>
 * <p>
 * <b>Alternative:</b> Implement this interface and register via {@link InteractionManager}:
 * <pre>
 * public class FeedbackModal implements ModalHandler {
 *     &#064;Override
 *     public String getModalIdPrefix() {
 *         return "myplugin:feedback";
 *     }
 *
 *     &#064;Override
 *     public void handle(ModalInteractionEvent event) {
 *         String feedback = event.getValue("feedback-input").getAsString();
 *         event.reply("Thanks for your feedback: " + feedback).setEphemeral(true).queue();
 *     }
 * }
 * </pre>
 */
public interface ModalHandler {

    /**
     * Get the modal ID prefix this handler responds to.
     *
     * @return the modal ID prefix
     */
    String getModalIdPrefix();

    /**
     * Handle the modal submission.
     * <p>
     * Called when a user submits a modal form.
     * Use {@code event.getValue("input-id")} to get input values.
     *
     * @param event the modal interaction event
     */
    void handle(ModalInteractionEvent event);
}
