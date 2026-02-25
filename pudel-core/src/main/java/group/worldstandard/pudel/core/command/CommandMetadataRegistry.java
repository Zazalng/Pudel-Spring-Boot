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
package group.worldstandard.pudel.core.command;

import net.dv8tion.jda.api.Permission;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for command metadata (descriptions, usage, permissions).
 * Used by help commands to display dynamic command information.
 */
@Component
public class CommandMetadataRegistry {

    // Text command metadata: commandName -> metadata
    private final Map<String, CommandMetadata> textCommands = new ConcurrentHashMap<>();

    // Slash command metadata: commandName -> metadata
    private final Map<String, CommandMetadata> slashCommands = new ConcurrentHashMap<>();

    // Track which plugin registered which commands
    private final Map<String, Set<String>> pluginTextCommands = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> pluginSlashCommands = new ConcurrentHashMap<>();

    /**
     * Register metadata for a text command.
     */
    public void registerTextCommand(String pluginId, String commandName, String description,
                                    String usage, Permission[] permissions) {
        CommandMetadata metadata = new CommandMetadata(
                commandName, description, usage, permissions, pluginId, CommandType.TEXT
        );
        textCommands.put(commandName.toLowerCase(), metadata);
        pluginTextCommands.computeIfAbsent(pluginId, k -> ConcurrentHashMap.newKeySet())
                .add(commandName.toLowerCase());
    }

    /**
     * Register metadata for a slash command.
     */
    public void registerSlashCommand(String pluginId, String commandName, String description,
                                     Permission[] permissions) {
        CommandMetadata metadata = new CommandMetadata(
                commandName, description, "", permissions, pluginId, CommandType.SLASH
        );
        slashCommands.put(commandName.toLowerCase(), metadata);
        pluginSlashCommands.computeIfAbsent(pluginId, k -> ConcurrentHashMap.newKeySet())
                .add(commandName.toLowerCase());
    }

    /**
     * Unregister all commands for a plugin.
     */
    public void unregisterPluginCommands(String pluginId) {
        Set<String> textCmds = pluginTextCommands.remove(pluginId);
        if (textCmds != null) {
            textCmds.forEach(textCommands::remove);
        }

        Set<String> slashCmds = pluginSlashCommands.remove(pluginId);
        if (slashCmds != null) {
            slashCmds.forEach(slashCommands::remove);
        }
    }

    /**
     * Get metadata for a text command.
     */
    public Optional<CommandMetadata> getTextCommandMetadata(String commandName) {
        return Optional.ofNullable(textCommands.get(commandName.toLowerCase()));
    }

    /**
     * Get metadata for a slash command.
     */
    public Optional<CommandMetadata> getSlashCommandMetadata(String commandName) {
        return Optional.ofNullable(slashCommands.get(commandName.toLowerCase()));
    }

    /**
     * Get all text command metadata.
     */
    public Collection<CommandMetadata> getAllTextCommands() {
        return Collections.unmodifiableCollection(textCommands.values());
    }

    /**
     * Get all slash command metadata.
     */
    public Collection<CommandMetadata> getAllSlashCommands() {
        return Collections.unmodifiableCollection(slashCommands.values());
    }

    /**
     * Get description for a text command (with fallback).
     */
    public String getTextCommandDescription(String commandName) {
        CommandMetadata meta = textCommands.get(commandName.toLowerCase());
        return meta != null ? meta.description() : "No description available";
    }

    /**
     * Get description for a slash command (with fallback).
     */
    public String getSlashCommandDescription(String commandName) {
        CommandMetadata meta = slashCommands.get(commandName.toLowerCase());
        return meta != null ? meta.description() : "No description available";
    }

    /**
     * Check if a text command is from a plugin (not built-in).
     */
    public boolean isPluginTextCommand(String commandName) {
        CommandMetadata meta = textCommands.get(commandName.toLowerCase());
        return meta != null && !"core".equals(meta.pluginId());
    }

    /**
     * Check if a slash command is from a plugin (not built-in).
     */
    public boolean isPluginSlashCommand(String commandName) {
        CommandMetadata meta = slashCommands.get(commandName.toLowerCase());
        return meta != null && !"core".equals(meta.pluginId());
    }

    /**
     * Command types.
     */
    public enum CommandType {
        TEXT, SLASH
    }

    /**
     * Metadata record for a command.
     */
    public record CommandMetadata(
            String name,
            String description,
            String usage,
            Permission[] permissions,
            String pluginId,
            CommandType type
    ) {
        /**
         * Get a short description (first line or truncated).
         */
        public String shortDescription() {
            if (description == null || description.isEmpty()) {
                return "No description";
            }
            // Get first line
            int newline = description.indexOf('\n');
            String firstLine = newline > 0 ? description.substring(0, newline) : description;
            // Truncate if too long
            return firstLine.length() > 50 ? firstLine.substring(0, 47) + "..." : firstLine;
        }

        /**
         * Check if this is a built-in (core) command.
         */
        public boolean isBuiltIn() {
            return "core".equals(pluginId);
        }
    }
}

