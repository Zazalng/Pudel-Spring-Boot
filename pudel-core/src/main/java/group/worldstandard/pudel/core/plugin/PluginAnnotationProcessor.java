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
package group.worldstandard.pudel.core.plugin;

import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.PluginInfo;
import group.worldstandard.pudel.api.annotation.*;
import group.worldstandard.pudel.api.command.CommandContext;
import group.worldstandard.pudel.api.command.TextCommandHandler;
import group.worldstandard.pudel.api.interaction.InteractionManager;
import group.worldstandard.pudel.api.interaction.SlashCommandHandler;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Processes plugin annotations and handles automatic command registration.
 * <p>
 * This processor scans plugin classes for annotations like @SlashCommand, @TextCommand,
 * &#064;ButtonHandler,  etc., and automatically:
 * <ul>
 *   <li>Registers commands when plugin is enabled</li>
 *   <li>Unregisters commands when plugin is disabled</li>
 *   <li>Syncs slash commands to Discord</li>
 * </ul>
 */
@Component
public class PluginAnnotationProcessor {

    private static final Logger logger = LoggerFactory.getLogger(PluginAnnotationProcessor.class);

    private final InteractionManager interactionManager;

    // Track registered handlers per plugin for cleanup
    private final Map<String, Set<String>> pluginSlashCommands = new HashMap<>();
    private final Map<String, Set<String>> pluginTextCommands = new HashMap<>();
    private final Map<String, Set<String>> pluginButtonHandlers = new HashMap<>();
    private final Map<String, Set<String>> pluginModalHandlers = new HashMap<>();
    private final Map<String, Set<String>> pluginSelectMenuHandlers = new HashMap<>();

    public PluginAnnotationProcessor(InteractionManager interactionManager) {
        this.interactionManager = interactionManager;
    }

    /**
     * Extract plugin info from @Plugin annotation.
     *
     * @param pluginClass the plugin class
     * @return PluginInfo or null if not annotated
     */
    public PluginInfo extractPluginInfo(Class<?> pluginClass) {
        Plugin annotation = pluginClass.getAnnotation(Plugin.class);
        if (annotation == null) {
            return null;
        }

        return new PluginInfo(
                annotation.name(),
                annotation.version(),
                annotation.author(),
                annotation.description()
        );
    }

    /**
     * Check if a class is annotated with @Plugin.
     */
    public boolean isAnnotatedPlugin(Class<?> pluginClass) {
        return pluginClass.isAnnotationPresent(Plugin.class);
    }

    /**
     * Process all annotations and register handlers for a plugin.
     *
     * @param pluginId      the plugin identifier
     * @param pluginInstance the plugin instance
     * @param context       the plugin context
     * @return number of handlers registered
     */
    public int processAndRegister(String pluginId, Object pluginInstance, PluginContext context) {
        Class<?> pluginClass = pluginInstance.getClass();
        int registered = 0;

        // Process @SlashCommand methods
        registered += processSlashCommands(pluginId, pluginInstance, pluginClass);

        // Process @TextCommand methods
        registered += processTextCommands(pluginId, pluginInstance, pluginClass, context);

        // Process @ButtonHandler methods
        registered += processButtonHandlers(pluginId, pluginInstance, pluginClass);

        // Process @ModalHandler methods
        registered += processModalHandlers(pluginId, pluginInstance, pluginClass);

        // Process @SelectMenuHandler methods
        registered += processSelectMenuHandlers(pluginId, pluginInstance, pluginClass);

        // Call @OnEnable methods
        invokeLifecycleMethods(pluginInstance, pluginClass, OnEnable.class, context);

        logger.info("[{}] Registered {} handlers via annotations", pluginId, registered);
        return registered;
    }

    /**
     * Unregister all handlers for a plugin and sync commands.
     *
     * @param pluginId      the plugin identifier
     * @param pluginInstance the plugin instance (for @OnDisable)
     * @param context       the plugin context
     */
    public void unregisterAll(String pluginId, Object pluginInstance, PluginContext context) {
        // Call @OnDisable methods first
        if (pluginInstance != null) {
            invokeLifecycleMethods(pluginInstance, pluginInstance.getClass(), OnDisable.class, context);
        }

        // Unregister slash commands
        Set<String> slashCmds = pluginSlashCommands.remove(pluginId);
        if (slashCmds != null) {
            for (String cmd : slashCmds) {
                interactionManager.unregisterSlashCommand(cmd);
            }
        }

        // Unregister text commands
        Set<String> textCmds = pluginTextCommands.remove(pluginId);
        if (textCmds != null && context != null) {
            for (String cmd : textCmds) {
                context.unregisterCommand(cmd);
            }
        }

        // Unregister button handlers
        Set<String> buttons = pluginButtonHandlers.remove(pluginId);
        if (buttons != null) {
            for (String prefix : buttons) {
                interactionManager.unregisterButtonHandler(prefix);
            }
        }

        // Unregister modal handlers
        Set<String> modals = pluginModalHandlers.remove(pluginId);
        if (modals != null) {
            for (String prefix : modals) {
                interactionManager.unregisterModalHandler(prefix);
            }
        }

        // Unregister select menu handlers
        Set<String> selects = pluginSelectMenuHandlers.remove(pluginId);
        if (selects != null) {
            for (String prefix : selects) {
                interactionManager.unregisterSelectMenuHandler(prefix);
            }
        }

        logger.info("[{}] Unregistered all annotation-based handlers", pluginId);
    }

    /**
     * Sync all slash commands to Discord.
     * Called by core after plugin enable/disable.
     */
    public void syncCommands() {
        interactionManager.syncCommands()
                .thenRun(() -> logger.info("Synced slash commands to Discord"))
                .exceptionally(e -> {
                    logger.error("Failed to sync slash commands: {}", e.getMessage());
                    return null;
                });
    }

    /**
     * Invoke @OnShutdown method on a plugin.
     * <p>
     * The shutdown method can return boolean:
     * <ul>
     *   <li>true - shutdown successful, proceed with unload</li>
     *   <li>false - shutdown failed, core should force-kill</li>
     * </ul>
     *
     * @param pluginId      the plugin identifier
     * @param pluginInstance the plugin instance
     * @param context       the plugin context (may be null if plugin wasn't fully initialized)
     * @return true if shutdown was successful (or no shutdown method exists),
     *         false if shutdown failed and force-kill is needed
     */
    public boolean invokeShutdown(String pluginId, Object pluginInstance, PluginContext context) {
        if (pluginInstance == null) {
            return true;
        }

        Class<?> pluginClass = pluginInstance.getClass();
        boolean shutdownSuccess = true;

        for (Method method : pluginClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(OnShutdown.class)) continue;

            try {
                method.setAccessible(true);
                Class<?>[] params = method.getParameterTypes();
                Class<?> returnType = method.getReturnType();

                Object result;
                if (params.length == 0) {
                    result = method.invoke(pluginInstance);
                } else if (params.length == 1 && PluginContext.class.isAssignableFrom(params[0])) {
                    // If context is null, skip methods that require it
                    if (context == null) {
                        logger.warn("[{}] @OnShutdown method {} requires context but context is null (plugin not fully initialized), skipping",
                                   pluginId, method.getName());
                        continue;
                    }
                    result = method.invoke(pluginInstance, context);
                } else {
                    logger.warn("[{}] @OnShutdown method {} has invalid parameters", pluginId, method.getName());
                    continue;
                }

                // Check return value
                if (returnType == boolean.class || returnType == Boolean.class) {
                    if (result != null && !(Boolean) result) {
                        logger.warn("[{}] @OnShutdown method {} returned false, will force-kill",
                                   pluginId, method.getName());
                        shutdownSuccess = false;
                    }
                }
                // void return is treated as success

                logger.debug("[{}] @OnShutdown method {} completed", pluginId, method.getName());

            } catch (Exception e) {
                logger.error("[{}] Error invoking @OnShutdown method {}: {}",
                        pluginId, method.getName(), e.getMessage(), e);
                shutdownSuccess = false;
            }
        }

        return shutdownSuccess;
    }

    // =====================================================
    // Slash Command Processing
    // =====================================================

    private int processSlashCommands(String pluginId, Object instance, Class<?> pluginClass) {
        int count = 0;

        for (Method method : pluginClass.getDeclaredMethods()) {
            SlashCommand annotation = method.getAnnotation(SlashCommand.class);
            if (annotation == null) continue;

            // Validate method signature
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !SlashCommandInteractionEvent.class.isAssignableFrom(params[0])) {
                logger.warn("[{}] @SlashCommand method {} must accept SlashCommandInteractionEvent",
                        pluginId, method.getName());
                continue;
            }

            // Build command data
            SlashCommandData commandData = buildSlashCommandData(annotation);

            // Create handler
            SlashCommandHandler handler = new AnnotatedSlashCommandHandler(
                    instance, method, commandData, annotation.global(), annotation.guildIds()
            );

            // Register
            if (interactionManager.registerSlashCommand(pluginId, handler)) {
                pluginSlashCommands.computeIfAbsent(pluginId, k -> new HashSet<>())
                        .add(annotation.name());
                count++;
            }
        }

        return count;
    }

    private SlashCommandData buildSlashCommandData(SlashCommand annotation) {
        SlashCommandData data = Commands.slash(annotation.name(), annotation.description());

        // Add permissions
        if (annotation.permissions().length > 0) {
            List<Permission> perms = new ArrayList<>();
            for (String permName : annotation.permissions()) {
                try {
                    perms.add(Permission.valueOf(permName.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    logger.warn("Unknown permission: {}", permName);
                }
            }
            if (!perms.isEmpty()) {
                data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(perms));
            }
        }

        // Add subcommands
        if (annotation.subcommands().length > 0) {
            for (Subcommand sub : annotation.subcommands()) {
                SubcommandData subData = new SubcommandData(sub.name(), sub.description());
                for (CommandOption opt : sub.options()) {
                    subData.addOptions(buildOptionData(opt));
                }
                data.addSubcommands(subData);
            }
        } else {
            // Add options (only if no subcommands)
            for (CommandOption opt : annotation.options()) {
                data.addOptions(buildOptionData(opt));
            }
        }

        return data;
    }

    private OptionData buildOptionData(CommandOption opt) {
        OptionType type;
        try {
            type = OptionType.valueOf(opt.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            type = OptionType.STRING;
        }

        OptionData data = new OptionData(type, opt.name(), opt.description(), opt.required());

        // Add choices
        for (Choice choice : opt.choices()) {
            if (type == OptionType.INTEGER) {
                try {
                    data.addChoice(choice.name(), Long.parseLong(choice.value()));
                } catch (NumberFormatException e) {
                    data.addChoice(choice.name(), choice.value());
                }
            } else if (type == OptionType.NUMBER) {
                try {
                    data.addChoice(choice.name(), Double.parseDouble(choice.value()));
                } catch (NumberFormatException e) {
                    data.addChoice(choice.name(), choice.value());
                }
            } else {
                data.addChoice(choice.name(), choice.value());
            }
        }

        // Set min/max for numeric types
        if (opt.min() != Double.MIN_VALUE) {
            if (type == OptionType.INTEGER) {
                data.setMinValue((long) opt.min());
            } else if (type == OptionType.NUMBER) {
                data.setMinValue(opt.min());
            }
            // Ignore min for non-numeric types
        }
        if (opt.max() != Double.MAX_VALUE) {
            if (type == OptionType.INTEGER) {
                data.setMaxValue((long) opt.max());
            } else if (type == OptionType.NUMBER) {
                data.setMaxValue(opt.max());
            }
            // Ignore max for non-numeric types
        }

        data.setAutoComplete(opt.autocomplete());

        return data;
    }

    // =====================================================
    // Text Command Processing
    // =====================================================

    private int processTextCommands(String pluginId, Object instance, Class<?> pluginClass, PluginContext context) {
        int count = 0;

        for (Method method : pluginClass.getDeclaredMethods()) {
            TextCommand annotation = method.getAnnotation(TextCommand.class);
            if (annotation == null) continue;

            // Validate method signature
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !CommandContext.class.isAssignableFrom(params[0])) {
                logger.warn("[{}] @TextCommand method {} must accept CommandContext",
                        pluginId, method.getName());
                continue;
            }

            // Create wrapper handler
            method.setAccessible(true);
            Method finalMethod = method;
            TextCommandHandler handler = ctx -> {
                try {
                    finalMethod.invoke(instance, ctx);
                } catch (Exception e) {
                    logger.error("[{}] Error in text command {}: {}",
                            pluginId, annotation.value(), e.getMessage(), e);
                    ctx.reply("❌ An error occurred executing this command.");
                }
            };

            // Register main command
            context.registerCommand(annotation.value(), handler);
            pluginTextCommands.computeIfAbsent(pluginId, k -> new HashSet<>())
                    .add(annotation.value());
            count++;

            // Register aliases
            for (String alias : annotation.aliases()) {
                context.registerCommand(alias, handler);
                pluginTextCommands.get(pluginId).add(alias);
                count++;
            }
        }

        return count;
    }

    // =====================================================
    // Button Handler Processing
    // =====================================================

    private int processButtonHandlers(String pluginId, Object instance, Class<?> pluginClass) {
        int count = 0;

        for (Method method : pluginClass.getDeclaredMethods()) {
            group.worldstandard.pudel.api.annotation.ButtonHandler annotation =
                    method.getAnnotation(group.worldstandard.pudel.api.annotation.ButtonHandler.class);
            if (annotation == null) continue;

            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !ButtonInteractionEvent.class.isAssignableFrom(params[0])) {
                logger.warn("[{}] @ButtonHandler method {} must accept ButtonInteractionEvent",
                        pluginId, method.getName());
                continue;
            }

            method.setAccessible(true);
            Method finalMethod = method;
            String prefix = annotation.value();

            group.worldstandard.pudel.api.interaction.ButtonHandler handler =
                    new group.worldstandard.pudel.api.interaction.ButtonHandler() {
                        @Override
                        public String getButtonIdPrefix() {
                            return prefix;
                        }

                        @Override
                        public void handle(ButtonInteractionEvent event) {
                            try {
                                finalMethod.invoke(instance, event);
                            } catch (Exception e) {
                                logger.error("[{}] Error in button handler: {}", pluginId, e.getMessage(), e);
                                event.reply("❌ An error occurred.").setEphemeral(true).queue();
                            }
                        }
                    };

            if (interactionManager.registerButtonHandler(pluginId, handler)) {
                pluginButtonHandlers.computeIfAbsent(pluginId, k -> new HashSet<>()).add(prefix);
                count++;
            }
        }

        return count;
    }

    // =====================================================
    // Modal Handler Processing
    // =====================================================

    private int processModalHandlers(String pluginId, Object instance, Class<?> pluginClass) {
        int count = 0;

        for (Method method : pluginClass.getDeclaredMethods()) {
            group.worldstandard.pudel.api.annotation.ModalHandler annotation =
                    method.getAnnotation(group.worldstandard.pudel.api.annotation.ModalHandler.class);
            if (annotation == null) continue;

            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !ModalInteractionEvent.class.isAssignableFrom(params[0])) {
                logger.warn("[{}] @ModalHandler method {} must accept ModalInteractionEvent",
                        pluginId, method.getName());
                continue;
            }

            method.setAccessible(true);
            Method finalMethod = method;
            String prefix = annotation.value();

            group.worldstandard.pudel.api.interaction.ModalHandler handler =
                    new group.worldstandard.pudel.api.interaction.ModalHandler() {
                        @Override
                        public String getModalIdPrefix() {
                            return prefix;
                        }

                        @Override
                        public void handle(ModalInteractionEvent event) {
                            try {
                                finalMethod.invoke(instance, event);
                            } catch (Exception e) {
                                logger.error("[{}] Error in modal handler: {}", pluginId, e.getMessage(), e);
                                event.reply("❌ An error occurred.").setEphemeral(true).queue();
                            }
                        }
                    };

            if (interactionManager.registerModalHandler(pluginId, handler)) {
                pluginModalHandlers.computeIfAbsent(pluginId, k -> new HashSet<>()).add(prefix);
                count++;
            }
        }

        return count;
    }

    // =====================================================
    // Select Menu Handler Processing
    // =====================================================

    private int processSelectMenuHandlers(String pluginId, Object instance, Class<?> pluginClass) {
        int count = 0;

        for (Method method : pluginClass.getDeclaredMethods()) {
            SelectMenuHandler annotation = method.getAnnotation(SelectMenuHandler.class);
            if (annotation == null) continue;

            Class<?>[] params = method.getParameterTypes();
            // Accept various select menu event types
            if (params.length != 1) {
                logger.warn("[{}] @SelectMenuHandler method {} must accept a select interaction event",
                        pluginId, method.getName());
                continue;
            }

            method.setAccessible(true);
            Method finalMethod = method;
            String prefix = annotation.value();

            group.worldstandard.pudel.api.interaction.SelectMenuHandler handler =
                    new group.worldstandard.pudel.api.interaction.SelectMenuHandler() {
                        @Override
                        public String getSelectMenuIdPrefix() {
                            return prefix;
                        }

                        @Override
                        public void handleStringSelect(StringSelectInteractionEvent event) {
                            try {
                                finalMethod.invoke(instance, event);
                            } catch (Exception e) {
                                logger.error("[{}] Error in select menu handler: {}", pluginId, e.getMessage(), e);
                                event.reply("❌ An error occurred.").setEphemeral(true).queue();
                            }
                        }
                    };

            if (interactionManager.registerSelectMenuHandler(pluginId, handler)) {
                pluginSelectMenuHandlers.computeIfAbsent(pluginId, k -> new HashSet<>()).add(prefix);
                count++;
            }
        }

        return count;
    }

    // =====================================================
    // Lifecycle Method Processing
    // =====================================================

    private void invokeLifecycleMethods(Object instance, Class<?> pluginClass,
                                        Class<? extends java.lang.annotation.Annotation> annotation,
                                        PluginContext context) {
        for (Method method : pluginClass.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(annotation)) continue;

            try {
                method.setAccessible(true);
                Class<?>[] params = method.getParameterTypes();

                if (params.length == 0) {
                    method.invoke(instance);
                } else if (params.length == 1 && PluginContext.class.isAssignableFrom(params[0])) {
                    method.invoke(instance, context);
                } else {
                    logger.warn("@{} method {} has invalid parameters",
                            annotation.getSimpleName(), method.getName());
                }
            } catch (Exception e) {
                logger.error("Error invoking @{} method {}: {}",
                        annotation.getSimpleName(), method.getName(), e.getMessage(), e);
            }
        }
    }

    // =====================================================
    // Inner Classes
    // =====================================================

    /**
     * SlashCommandHandler implementation that wraps an annotated method.
     */
    private static class AnnotatedSlashCommandHandler implements SlashCommandHandler {
        private final Object instance;
        private final Method method;
        private final SlashCommandData commandData;
        private final boolean global;
        private final long[] guildIds;

        public AnnotatedSlashCommandHandler(Object instance, Method method,
                                            SlashCommandData commandData,
                                            boolean global, long[] guildIds) {
            this.instance = instance;
            this.method = method;
            this.commandData = commandData;
            this.global = global;
            this.guildIds = guildIds;
            method.setAccessible(true);
        }

        @Override
        public SlashCommandData getCommandData() {
            return commandData;
        }

        @Override
        public void handle(SlashCommandInteractionEvent event) {
            try {
                method.invoke(instance, event);
            } catch (Exception e) {
                LoggerFactory.getLogger(AnnotatedSlashCommandHandler.class)
                        .error("Error handling slash command /{}: {}", commandData.getName(), e.getMessage(), e);
                if (!event.isAcknowledged()) {
                    event.reply("❌ An error occurred executing this command.").setEphemeral(true).queue();
                }
            }
        }

        @Override
        public boolean isGlobal() {
            return global;
        }

        @Override
        public long[] getGuildIds() {
            return guildIds.length > 0 ? guildIds : null;
        }
    }
}
