/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 World Standard.group
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
package group.worldstandard.pudel.api;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import group.worldstandard.pudel.api.agent.AgentToolRegistry;
import group.worldstandard.pudel.api.audio.VoiceManager;
import group.worldstandard.pudel.api.command.TextCommandHandler;
import group.worldstandard.pudel.api.database.PluginDatabaseManager;
import group.worldstandard.pudel.api.event.EventManager;
import group.worldstandard.pudel.api.event.Listener;
import group.worldstandard.pudel.api.event.PluginEventListener;
import group.worldstandard.pudel.api.interaction.InteractionManager;

/**
 * Context provided to plugins for accessing bot services and Discord API.
 * Plugins should use this context to interact with the bot and access shared services.
 */
public interface PluginContext {

    /**
     * Gets the JDA instance.
     * @return the JDA instance
     */
    JDA getJDA();

    /**
     * Gets the bot's JDA user.
     * @return the bot user
     */
    User getBotUser();

    /**
     * Gets a guild by ID.
     * @param guildId the guild ID
     * @return the guild or null if not found
     */
    Guild getGuild(long guildId);

    /**
     * Registers a command handler.
     * @param commandName the command name
     * @param handler the command handler
     */
    void registerCommand(String commandName, TextCommandHandler handler);

    /**
     * Unregisters a command handler.
     * @param commandName the command name
     */
    void unregisterCommand(String commandName);

    /**
     * Gets a registered command handler.
     * @param commandName the command name
     * @return the handler or null if not found
     */
    TextCommandHandler getCommand(String commandName);

    /**
     * Logs a message.
     * @param level the log level
     * @param message the message
     */
    void log(String level, String message);

    /**
     * Logs a message with an exception.
     * @param level the log level
     * @param message the message
     * @param throwable the exception
     */
    void log(String level, String message, Throwable throwable);

    // ============== Event Management ==============

    /**
     * Gets the event manager for registering event listeners.
     * @return the event manager
     */
    EventManager getEventManager();

    /**
     * Registers a listener object with annotated event handlers.
     * Convenience method for getEventManager().registerListener().
     * @param listener the listener object
     */
    void registerListener(Listener listener);

    /**
     * Registers a typed event listener.
     * Convenience method for getEventManager().registerEventListener().
     * @param listener the event listener
     * @param <T> the event type
     */
    <T extends GenericEvent> void registerEventListener(PluginEventListener<T> listener);

    /**
     * Unregisters a listener object.
     * @param listener the listener to unregister
     */
    void unregisterListener(Listener listener);

    /**
     * Unregisters a typed event listener.
     * @param listener the event listener to unregister
     * @param <T> the event type
     */
    <T extends GenericEvent> void unregisterEventListener(PluginEventListener<T> listener);

    /**
     * Gets the plugin name associated with this context.
     * @return the plugin name
     */
    String getPluginName();

    // ============== Voice/Audio Management ==============

    /**
     * Gets the voice manager for handling voice connections and audio.
     *
     * @return the voice manager
     */
    VoiceManager getVoiceManager();

    // ============== Agent Tools API ==============

    /**
     * Gets the agent tool registry for registering AI agent tools.
     * <p>
     * Plugins can register custom tools that the AI agent can use
     * when processing user requests.
     * <p>
     * Example:
     * <pre>
     * context.getAgentToolRegistry().registerProvider("my-plugin", new MyTools());
     * </pre>
     *
     * @return the agent tool registry
     */
    AgentToolRegistry getAgentToolRegistry();

    // ============== Discord Interactions API ==============

    /**
     * Gets the interaction manager for handling Discord interactions.
     * <p>
     * Plugins use this to register slash commands, buttons, modals,
     * select menus, context menus, and autocomplete handlers.
     * <p>
     * Example:
     * <pre>
     * InteractionManager manager = context.getInteractionManager();
     *
     * // Register slash command
     * manager.registerSlashCommand("my-plugin", new PingCommand());
     *
     * // Register button handler
     * manager.registerButtonHandler("my-plugin", new MyButtonHandler());
     *
     * // Sync commands to Discord
     * manager.syncCommands();
     * </pre>
     *
     * @return the interaction manager
     */
    InteractionManager getInteractionManager();

    // ============== Plugin Database API ==============

    /**
     * Gets the database manager for plugin data persistence.
     * <p>
     * Each plugin gets its own isolated database namespace with tables
     * prefixed by a unique identifier. Plugins interact through a JPA-like
     * repository pattern - no raw SQL is allowed.
     * <p>
     * Example:
     * <pre>
     * PluginDatabaseManager db = context.getDatabaseManager();
     *
     * // Define schema
     * TableSchema schema = TableSchema.builder("settings")
     *     .column("user_id", ColumnType.BIGINT, false)
     *     .column("key", ColumnType.STRING, 100, false)
     *     .column("value", ColumnType.TEXT, true)
     *     .build();
     *
     * // Create table (safe to call every startup)
     * db.createTable(schema);
     *
     * // Get repository for CRUD operations
     * PluginRepository&lt;Setting&gt; repo = db.getRepository("settings", Setting.class);
     *
     * // Or use simple key-value store
     * db.getKeyValueStore().set("config.enabled", true);
     * </pre>
     *
     * @return the plugin database manager
     */
    PluginDatabaseManager getDatabaseManager();
}

