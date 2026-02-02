/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed with an additional permission known as the
 * "Pudel Plugin Exception".
 *
 * See the LICENSE and PLUGIN_EXCEPTION files in the project root for details.
 */
package group.worldstandard.pudel.core.interaction;

import group.worldstandard.pudel.api.interaction.*;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * JDA Event Listener that dispatches interaction events to registered handlers.
 */
@Component
public class InteractionEventListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(InteractionEventListener.class);

    private final InteractionManagerImpl interactionManager;

    public InteractionEventListener(InteractionManagerImpl interactionManager) {
        this.interactionManager = interactionManager;
    }

    // =====================================================
    // Slash Commands
    // =====================================================

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        SlashCommandHandler handler = interactionManager.getSlashCommand(commandName);

        if (handler == null) {
            logger.debug("No handler found for slash command: /{}", commandName);
            return;
        }

        try {
            logger.debug("Handling slash command: /{} by {}", commandName, event.getUser().getName());
            handler.handle(event);
        } catch (Exception e) {
            logger.error("Error handling slash command /{}: {}", commandName, e.getMessage(), e);
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing this command.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    // =====================================================
    // Context Menus
    // =====================================================

    @Override
    public void onUserContextInteraction(@NotNull UserContextInteractionEvent event) {
        String commandName = event.getName();
        ContextMenuHandler handler = interactionManager.getContextMenu(commandName);

        if (handler == null) {
            logger.debug("No handler found for user context menu: {}", commandName);
            return;
        }

        try {
            logger.debug("Handling user context menu: {} on {}", commandName, event.getTarget().getName());
            handler.handleUserContext(event);
        } catch (Exception e) {
            logger.error("Error handling user context menu {}: {}", commandName, e.getMessage(), e);
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing this action.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    @Override
    public void onMessageContextInteraction(@NotNull MessageContextInteractionEvent event) {
        String commandName = event.getName();
        ContextMenuHandler handler = interactionManager.getContextMenu(commandName);

        if (handler == null) {
            logger.debug("No handler found for message context menu: {}", commandName);
            return;
        }

        try {
            logger.debug("Handling message context menu: {} on message {}", commandName, event.getTarget().getId());
            handler.handleMessageContext(event);
        } catch (Exception e) {
            logger.error("Error handling message context menu {}: {}", commandName, e.getMessage(), e);
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing this action.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    // =====================================================
    // Buttons
    // =====================================================

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        ButtonHandler handler = interactionManager.getButtonHandler(buttonId);

        if (handler == null) {
            logger.debug("No handler found for button: {}", buttonId);
            return;
        }

        try {
            logger.debug("Handling button interaction: {}", buttonId);
            handler.handle(event);
        } catch (Exception e) {
            logger.error("Error handling button {}: {}", buttonId, e.getMessage(), e);
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing this button.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    // =====================================================
    // Select Menus
    // =====================================================

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        String menuId = event.getComponentId();
        SelectMenuHandler handler = interactionManager.getSelectMenuHandler(menuId);

        if (handler == null) {
            logger.debug("No handler found for string select menu: {}", menuId);
            return;
        }

        try {
            logger.debug("Handling string select interaction: {}", menuId);
            handler.handleStringSelect(event);
        } catch (Exception e) {
            logger.error("Error handling string select {}: {}", menuId, e.getMessage(), e);
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing this selection.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    @Override
    public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event) {
        String menuId = event.getComponentId();
        SelectMenuHandler handler = interactionManager.getSelectMenuHandler(menuId);

        if (handler == null) {
            logger.debug("No handler found for entity select menu: {}", menuId);
            return;
        }

        try {
            logger.debug("Handling entity select interaction: {}", menuId);
            handler.handleEntitySelect(event);
        } catch (Exception e) {
            logger.error("Error handling entity select {}: {}", menuId, e.getMessage(), e);
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing this selection.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    // =====================================================
    // Modals
    // =====================================================

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        String modalId = event.getModalId();
        ModalHandler handler = interactionManager.getModalHandler(modalId);

        if (handler == null) {
            logger.debug("No handler found for modal: {}", modalId);
            return;
        }

        try {
            logger.debug("Handling modal submission: {}", modalId);
            handler.handle(event);
        } catch (Exception e) {
            logger.error("Error handling modal {}: {}", modalId, e.getMessage(), e);
            if (!event.isAcknowledged()) {
                event.reply("An error occurred while processing your submission.")
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    // =====================================================
    // Autocomplete
    // =====================================================

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        String commandName = event.getName();
        String optionName = event.getFocusedOption().getName();

        AutoCompleteHandler handler = interactionManager.getAutoCompleteHandler(commandName, optionName);

        if (handler == null) {
            logger.debug("No autocomplete handler for {}.{}", commandName, optionName);
            return;
        }

        try {
            logger.debug("Handling autocomplete: {}.{} = '{}'",
                    commandName, optionName, event.getFocusedOption().getValue());
            handler.handle(event);
        } catch (Exception e) {
            logger.error("Error handling autocomplete {}.{}: {}", commandName, optionName, e.getMessage(), e);
            // Autocomplete errors are silent - just don't provide suggestions
            event.replyChoices().queue();
        }
    }
}
