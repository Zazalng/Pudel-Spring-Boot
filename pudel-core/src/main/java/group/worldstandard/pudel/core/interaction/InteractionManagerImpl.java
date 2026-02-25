/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard.group
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
import group.worldstandard.pudel.core.service.GuildSettingsService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of InteractionManager for managing Discord interactions.
 */
@Component
public class InteractionManagerImpl implements InteractionManager {

    private static final Logger logger = LoggerFactory.getLogger(InteractionManagerImpl.class);

    private final JDA jda;
    private final GuildSettingsService guildSettingsService;

    // Slash commands: commandName -> handler
    private final Map<String, SlashCommandHandler> slashCommands = new ConcurrentHashMap<>();

    // Context menus: commandName -> handler
    private final Map<String, ContextMenuHandler> contextMenus = new ConcurrentHashMap<>();

    // Buttons: prefix -> handler
    private final Map<String, ButtonHandler> buttonHandlers = new ConcurrentHashMap<>();

    // Select menus: prefix -> handler
    private final Map<String, SelectMenuHandler> selectMenuHandlers = new ConcurrentHashMap<>();

    // Modals: prefix -> handler
    private final Map<String, ModalHandler> modalHandlers = new ConcurrentHashMap<>();

    // Autocomplete: "command:option" -> handler
    private final Map<String, AutoCompleteHandler> autoCompleteHandlers = new ConcurrentHashMap<>();

    // Track plugin ownership: pluginId -> Set<handlerKey>
    private final Map<String, Set<String>> pluginHandlers = new ConcurrentHashMap<>();

    public InteractionManagerImpl(@Lazy JDA jda,
                                  @Lazy GuildSettingsService guildSettingsService) {
        this.jda = jda;
        this.guildSettingsService = guildSettingsService;
    }

    // =====================================================
    // Slash Commands
    // =====================================================

    @Override
    public boolean registerSlashCommand(String pluginId, SlashCommandHandler handler) {
        String name = handler.getCommandData().getName();

        if (slashCommands.containsKey(name)) {
            logger.warn("Slash command '{}' already registered", name);
            return false;
        }

        slashCommands.put(name, handler);
        trackHandler(pluginId, "slash:" + name);
        logger.info("[{}] Registered slash command: /{}", pluginId, name);
        return true;
    }

    @Override
    public boolean unregisterSlashCommand(String commandName) {
        SlashCommandHandler removed = slashCommands.remove(commandName);
        if (removed != null) {
            untrackHandler("slash:" + commandName);
            logger.info("Unregistered slash command: /{}", commandName);
            return true;
        }
        return false;
    }

    @Override
    public SlashCommandHandler getSlashCommand(String commandName) {
        return slashCommands.get(commandName);
    }

    @Override
    public Collection<SlashCommandHandler> getAllSlashCommands() {
        return Collections.unmodifiableCollection(slashCommands.values());
    }

    // =====================================================
    // Context Menus
    // =====================================================

    @Override
    public boolean registerContextMenu(String pluginId, ContextMenuHandler handler) {
        String name = handler.getCommandData().getName();

        if (contextMenus.containsKey(name)) {
            logger.warn("Context menu '{}' already registered", name);
            return false;
        }

        contextMenus.put(name, handler);
        trackHandler(pluginId, "context:" + name);
        logger.info("[{}] Registered context menu: {}", pluginId, name);
        return true;
    }

    @Override
    public boolean unregisterContextMenu(String commandName) {
        ContextMenuHandler removed = contextMenus.remove(commandName);
        if (removed != null) {
            untrackHandler("context:" + commandName);
            logger.info("Unregistered context menu: {}", commandName);
            return true;
        }
        return false;
    }

    @Override
    public ContextMenuHandler getContextMenu(String commandName) {
        return contextMenus.get(commandName);
    }

    // =====================================================
    // Buttons
    // =====================================================

    @Override
    public boolean registerButtonHandler(String pluginId, ButtonHandler handler) {
        String prefix = handler.getButtonIdPrefix();

        if (buttonHandlers.containsKey(prefix)) {
            logger.warn("Button handler with prefix '{}' already registered", prefix);
            return false;
        }

        buttonHandlers.put(prefix, handler);
        trackHandler(pluginId, "button:" + prefix);
        logger.info("[{}] Registered button handler: {}", pluginId, prefix);
        return true;
    }

    @Override
    public boolean unregisterButtonHandler(String buttonIdPrefix) {
        ButtonHandler removed = buttonHandlers.remove(buttonIdPrefix);
        if (removed != null) {
            untrackHandler("button:" + buttonIdPrefix);
            logger.info("Unregistered button handler: {}", buttonIdPrefix);
            return true;
        }
        return false;
    }

    @Override
    public ButtonHandler getButtonHandler(String buttonId) {
        // Find handler by prefix match
        for (Map.Entry<String, ButtonHandler> entry : buttonHandlers.entrySet()) {
            if (buttonId.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // =====================================================
    // Select Menus
    // =====================================================

    @Override
    public boolean registerSelectMenuHandler(String pluginId, SelectMenuHandler handler) {
        String prefix = handler.getSelectMenuIdPrefix();

        if (selectMenuHandlers.containsKey(prefix)) {
            logger.warn("Select menu handler with prefix '{}' already registered", prefix);
            return false;
        }

        selectMenuHandlers.put(prefix, handler);
        trackHandler(pluginId, "select:" + prefix);
        logger.info("[{}] Registered select menu handler: {}", pluginId, prefix);
        return true;
    }

    @Override
    public boolean unregisterSelectMenuHandler(String selectMenuIdPrefix) {
        SelectMenuHandler removed = selectMenuHandlers.remove(selectMenuIdPrefix);
        if (removed != null) {
            untrackHandler("select:" + selectMenuIdPrefix);
            logger.info("Unregistered select menu handler: {}", selectMenuIdPrefix);
            return true;
        }
        return false;
    }

    @Override
    public SelectMenuHandler getSelectMenuHandler(String selectMenuId) {
        for (Map.Entry<String, SelectMenuHandler> entry : selectMenuHandlers.entrySet()) {
            if (selectMenuId.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // =====================================================
    // Modals
    // =====================================================

    @Override
    public boolean registerModalHandler(String pluginId, ModalHandler handler) {
        String prefix = handler.getModalIdPrefix();

        if (modalHandlers.containsKey(prefix)) {
            logger.warn("Modal handler with prefix '{}' already registered", prefix);
            return false;
        }

        modalHandlers.put(prefix, handler);
        trackHandler(pluginId, "modal:" + prefix);
        logger.info("[{}] Registered modal handler: {}", pluginId, prefix);
        return true;
    }

    @Override
    public boolean unregisterModalHandler(String modalIdPrefix) {
        ModalHandler removed = modalHandlers.remove(modalIdPrefix);
        if (removed != null) {
            untrackHandler("modal:" + modalIdPrefix);
            logger.info("Unregistered modal handler: {}", modalIdPrefix);
            return true;
        }
        return false;
    }

    @Override
    public ModalHandler getModalHandler(String modalId) {
        for (Map.Entry<String, ModalHandler> entry : modalHandlers.entrySet()) {
            if (modalId.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // =====================================================
    // Autocomplete
    // =====================================================

    @Override
    public boolean registerAutoCompleteHandler(String pluginId, AutoCompleteHandler handler) {
        String key = handler.getCommandName() + ":" + handler.getOptionName();

        if (autoCompleteHandlers.containsKey(key)) {
            logger.warn("Autocomplete handler for '{}.{}' already registered",
                    handler.getCommandName(), handler.getOptionName());
            return false;
        }

        autoCompleteHandlers.put(key, handler);
        trackHandler(pluginId, "autocomplete:" + key);
        logger.info("[{}] Registered autocomplete handler: {}.{}",
                pluginId, handler.getCommandName(), handler.getOptionName());
        return true;
    }

    @Override
    public boolean unregisterAutoCompleteHandler(String commandName, String optionName) {
        String key = commandName + ":" + optionName;
        AutoCompleteHandler removed = autoCompleteHandlers.remove(key);
        if (removed != null) {
            untrackHandler("autocomplete:" + key);
            logger.info("Unregistered autocomplete handler: {}.{}", commandName, optionName);
            return true;
        }
        return false;
    }

    @Override
    public AutoCompleteHandler getAutoCompleteHandler(String commandName, String optionName) {
        return autoCompleteHandlers.get(commandName + ":" + optionName);
    }

    // =====================================================
    // Bulk Operations
    // =====================================================

    @Override
    public int unregisterAll(String pluginId) {
        Set<String> handlers = pluginHandlers.remove(pluginId);
        if (handlers == null || handlers.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (String key : handlers) {
            if (key.startsWith("slash:")) {
                if (slashCommands.remove(key.substring(6)) != null) count++;
            } else if (key.startsWith("context:")) {
                if (contextMenus.remove(key.substring(8)) != null) count++;
            } else if (key.startsWith("button:")) {
                if (buttonHandlers.remove(key.substring(7)) != null) count++;
            } else if (key.startsWith("select:")) {
                if (selectMenuHandlers.remove(key.substring(7)) != null) count++;
            } else if (key.startsWith("modal:")) {
                if (modalHandlers.remove(key.substring(6)) != null) count++;
            } else if (key.startsWith("autocomplete:")) {
                if (autoCompleteHandlers.remove(key.substring(13)) != null) count++;
            }
        }

        logger.info("Unregistered {} interaction handlers from plugin: {}", count, pluginId);
        return count;
    }

    @Override
    public CompletableFuture<Void> syncCommands() {
        if (jda == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("JDA not available"));
        }

        // Separate core (built-in) commands from plugin commands.
        // Core commands are registered globally — always visible everywhere.
        // Plugin commands are registered per-guild so we can filter by guild settings.
        List<CommandData> coreGlobalCommands = new ArrayList<>();
        List<SlashCommandHandler> pluginSlashHandlers = new ArrayList<>();
        List<ContextMenuHandler> pluginContextHandlers = new ArrayList<>();

        for (SlashCommandHandler handler : slashCommands.values()) {
            String cmdName = handler.getCommandData().getName();
            String pluginId = getPluginIdForCommand("slash:" + cmdName);
            if ("core".equals(pluginId)) {
                if (handler.isGlobal()) {
                    coreGlobalCommands.add(handler.getCommandData());
                }
            } else {
                pluginSlashHandlers.add(handler);
            }
        }

        for (ContextMenuHandler handler : contextMenus.values()) {
            String cmdName = handler.getCommandData().getName();
            String pluginId = getPluginIdForCommand("context:" + cmdName);
            if ("core".equals(pluginId)) {
                if (handler.isGlobal()) {
                    coreGlobalCommands.add(handler.getCommandData());
                }
            } else {
                pluginContextHandlers.add(handler);
            }
        }

        // Register core commands globally
        CompletableFuture<Void> globalFuture;
        if (!coreGlobalCommands.isEmpty()) {
            logger.info("Syncing {} core global commands to Discord...", coreGlobalCommands.size());
            globalFuture = jda.updateCommands()
                    .addCommands(coreGlobalCommands)
                    .submit()
                    .thenAccept(commands ->
                            logger.info("Synced {} core global commands", commands.size()))
                    .exceptionally(e -> {
                        logger.error("Failed to sync core global commands: {}", e.getMessage());
                        return null;
                    });
        } else {
            globalFuture = CompletableFuture.completedFuture(null);
        }

        // Register plugin commands per-guild, filtering out disabled plugins
        List<CompletableFuture<Void>> guildFutures = new ArrayList<>();
        for (Guild guild : jda.getGuilds()) {
            long guildId = guild.getIdLong();
            String guildIdStr = guild.getId();

            // Get disabled plugins for this guild
            Set<String> disabledPlugins = getDisabledPluginsForGuild(guildIdStr);

            // Build command list for this guild
            List<CommandData> guildCmds = new ArrayList<>();

            for (SlashCommandHandler handler : pluginSlashHandlers) {
                String cmdName = handler.getCommandData().getName();
                String pluginId = getPluginIdForCommand("slash:" + cmdName);

                // Skip if this plugin is disabled for this guild
                if (pluginId != null && disabledPlugins.contains(pluginId)) {
                    continue;
                }

                // For guild-specific handlers, check if they target this guild
                if (!handler.isGlobal()) {
                    long[] guildIds = handler.getGuildIds();
                    if (guildIds != null && guildIds.length > 0 && !contains(guildIds, guildId)) {
                        continue;
                    }
                }

                guildCmds.add(handler.getCommandData());
            }

            for (ContextMenuHandler handler : pluginContextHandlers) {
                String cmdName = handler.getCommandData().getName();
                String pluginId = getPluginIdForCommand("context:" + cmdName);

                if (pluginId != null && disabledPlugins.contains(pluginId)) {
                    continue;
                }

                if (!handler.isGlobal()) {
                    long[] guildIds = handler.getGuildIds();
                    if (guildIds != null && guildIds.length > 0 && !contains(guildIds, guildId)) {
                        continue;
                    }
                }

                guildCmds.add(handler.getCommandData());
            }

            if (!guildCmds.isEmpty()) {
                CompletableFuture<Void> future = guild.updateCommands()
                        .addCommands(guildCmds)
                        .submit()
                        .thenAccept(commands ->
                                logger.debug("Synced {} plugin commands to guild {}", commands.size(), guild.getName()))
                        .exceptionally(e -> {
                            logger.error("Failed to sync plugin commands to guild {}: {}", guildId, e.getMessage());
                            return null;
                        });
                guildFutures.add(future);
            } else {
                // Clear any previously registered guild commands if no plugin commands remain
                CompletableFuture<Void> future = guild.updateCommands()
                        .submit()
                        .thenAccept(commands ->
                                logger.debug("Cleared guild commands for {} (no active plugin commands)", guild.getName()))
                        .exceptionally(e -> {
                            logger.error("Failed to clear guild commands for {}: {}", guildId, e.getMessage());
                            return null;
                        });
                guildFutures.add(future);
            }
        }

        return CompletableFuture.allOf(
                globalFuture,
                CompletableFuture.allOf(guildFutures.toArray(new CompletableFuture[0]))
        );
    }

    @Override
    public CompletableFuture<Void> syncGuildCommands(long guildId) {
        if (jda == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("JDA not available"));
        }

        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Guild not found: " + guildId));
        }

        String guildIdStr = String.valueOf(guildId);
        Set<String> disabledPlugins = getDisabledPluginsForGuild(guildIdStr);

        List<CommandData> commands = new ArrayList<>();

        // Collect plugin slash commands for this guild, filtering disabled plugins
        for (SlashCommandHandler handler : slashCommands.values()) {
            String cmdName = handler.getCommandData().getName();
            String pluginId = getPluginIdForCommand("slash:" + cmdName);

            // Skip core commands (they're registered globally)
            if ("core".equals(pluginId)) {
                continue;
            }

            // Skip if this plugin is disabled for this guild
            if (pluginId != null && disabledPlugins.contains(pluginId)) {
                continue;
            }

            // For guild-specific handlers, check targeting
            if (!handler.isGlobal()) {
                long[] guildIds = handler.getGuildIds();
                if (guildIds != null && guildIds.length > 0 && !contains(guildIds, guildId)) {
                    continue;
                }
            }

            commands.add(handler.getCommandData());
        }

        // Collect plugin context menus for this guild
        for (ContextMenuHandler handler : contextMenus.values()) {
            String cmdName = handler.getCommandData().getName();
            String pluginId = getPluginIdForCommand("context:" + cmdName);

            if ("core".equals(pluginId)) {
                continue;
            }

            if (pluginId != null && disabledPlugins.contains(pluginId)) {
                continue;
            }

            if (!handler.isGlobal()) {
                long[] guildIds = handler.getGuildIds();
                if (guildIds != null && guildIds.length > 0 && !contains(guildIds, guildId)) {
                    continue;
                }
            }

            commands.add(handler.getCommandData());
        }

        logger.info("Syncing {} plugin commands to guild {} (disabled plugins: {})...",
                commands.size(), guild.getName(), disabledPlugins);

        return guild.updateCommands()
                .addCommands(commands)
                .submit()
                .thenAccept(cmds -> logger.info("Synced {} plugin commands to guild {}", cmds.size(), guild.getName()))
                .exceptionally(e -> {
                    logger.error("Failed to sync commands to guild {}: {}", guildId, e.getMessage());
                    return null;
                });
    }

    @Override
    public InteractionStats getStats() {
        return new InteractionStats(
                slashCommands.size(),
                contextMenus.size(),
                buttonHandlers.size(),
                selectMenuHandlers.size(),
                modalHandlers.size(),
                autoCompleteHandlers.size()
        );
    }

    // =====================================================
    // Helper Methods
    // =====================================================

    private void trackHandler(String pluginId, String handlerKey) {
        pluginHandlers.computeIfAbsent(pluginId, k -> ConcurrentHashMap.newKeySet()).add(handlerKey);
    }

    private void untrackHandler(String handlerKey) {
        pluginHandlers.values().forEach(set -> set.remove(handlerKey));
    }

    /**
     * Get the plugin ID that owns a given handler key (e.g., "slash:music" or "context:Ban User").
     */
    private String getPluginIdForCommand(String handlerKey) {
        for (Map.Entry<String, Set<String>> entry : pluginHandlers.entrySet()) {
            if (entry.getValue().contains(handlerKey)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Get the set of plugin names disabled for a given guild.
     */
    private Set<String> getDisabledPluginsForGuild(String guildId) {
        try {
            var settings = guildSettingsService.getGuildSettings(guildId);
            if (settings.isPresent()) {
                List<String> disabled = settings.get().getDisabledPluginsList();
                return new HashSet<>(disabled);
            }
        } catch (Exception e) {
            logger.warn("Failed to get disabled plugins for guild {}: {}", guildId, e.getMessage());
        }
        return Collections.emptySet();
    }

    private boolean contains(long[] array, long value) {
        for (long l : array) {
            if (l == value) return true;
        }
        return false;
    }
}
