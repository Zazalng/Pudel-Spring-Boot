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
package group.worldstandard.pudel.core.command.builtin;

import net.dv8tion.jda.api.EmbedBuilder;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.api.command.CommandContext;
import group.worldstandard.pudel.api.command.TextCommandHandler;
import group.worldstandard.pudel.core.command.CommandMetadataRegistry;
import group.worldstandard.pudel.core.command.CommandRegistry;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.service.GuildInitializationService;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handler for the help command.
 * Shows information about Pudel commands including plugin commands.
 * Includes both text commands (!command) and slash commands (/command).
 * Uses reaction-based pagination for interactive navigation.
 */
@Component
public class HelpCommandHandler implements TextCommandHandler {

    private final CommandRegistry commandRegistry;
    private final CommandMetadataRegistry commandMetadataRegistry;
    private final GuildInitializationService guildInitializationService;
    private final HelpSessionManager helpSessionManager;

    public HelpCommandHandler(CommandRegistry commandRegistry,
                              CommandMetadataRegistry commandMetadataRegistry,
                              GuildInitializationService guildInitializationService,
                              HelpSessionManager helpSessionManager) {
        this.commandRegistry = commandRegistry;
        this.commandMetadataRegistry = commandMetadataRegistry;
        this.guildInitializationService = guildInitializationService;
        this.helpSessionManager = helpSessionManager;
    }

    @Override
    public void handle(CommandContext context) {
        String prefix = "!";
        Set<String> disabledCommands = new HashSet<>();

        if (context.isFromGuild()) {
            GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(context.getGuild().getId());
            prefix = settings.getPrefix() != null ? settings.getPrefix() : "!";
            if (settings.getDisabledCommands() != null && !settings.getDisabledCommands().isEmpty()) {
                disabledCommands = new HashSet<>(Arrays.asList(settings.getDisabledCommands().toLowerCase().split(",")));
            }
        }

        // Check if user wants help for a specific command or page
        if (context.getArgs().length > 0) {
            String arg = context.getArgs()[0];

            // Check if it's a page number
            if (arg.startsWith("#")) {
                try {
                    int page = Integer.parseInt(arg.substring(1));
                    showInteractiveCommandList(context, prefix, disabledCommands, page);
                    return;
                } catch (NumberFormatException e) {
                    // Not a page number, treat as command name
                }
            }

            // Try to parse as page number directly
            try {
                int page = Integer.parseInt(arg);
                showInteractiveCommandList(context, prefix, disabledCommands, page);
                return;
            } catch (NumberFormatException e) {
                // Not a page number, show help for specific command
            }

            // Show help for specific command
            showCommandHelp(context, arg, prefix, disabledCommands);
            return;
        }

        // Show interactive command list (page 1)
        showInteractiveCommandList(context, prefix, disabledCommands, 1);
    }

    /**
     * Show interactive paginated command list with reactions.
     */
    private void showInteractiveCommandList(CommandContext context, String prefix, Set<String> disabledCommands, int page) {
        Map<String, TextCommandHandler> allCommands = commandRegistry.getAllCommands();
        List<String> commandNames = new ArrayList<>(allCommands.keySet());
        commandNames.sort(String::compareToIgnoreCase);

        int totalCommands = commandNames.size();
        int commandsPerPage = HelpSessionManager.getCommandsPerPage();
        int totalPages = (int) Math.ceil((double) totalCommands / commandsPerPage);

        if (totalPages < 1) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        // Create session for this user
        final int finalTotalPages = totalPages;
        final String finalPrefix = prefix;
        final Set<String> finalDisabledCommands = disabledCommands;
        final List<String> finalCommandNames = commandNames;

        // Create a temporary session to build the embed
        HelpSessionManager.HelpSession tempSession = new HelpSessionManager.HelpSession(
                context.getUser().getIdLong(), prefix, commandNames, disabledCommands, totalPages);
        tempSession.setCurrentPage(page);

        // Build and send embed with reactions
        context.getChannel().sendMessageEmbeds(helpSessionManager.buildHelpEmbed(tempSession, page, commandRegistry))
                .queue(message -> {
                    // Create persistent session
                    helpSessionManager.createSession(
                            message.getIdLong(),
                            context.getUser().getIdLong(),
                            finalPrefix,
                            finalCommandNames,
                            finalDisabledCommands,
                            finalTotalPages
                    );

                    // Add navigation reactions
                    helpSessionManager.addNavigationReactions(message, finalTotalPages);
                });
    }

    /**
     * Show detailed help for a specific command.
     */
    private void showCommandHelp(CommandContext context, String commandName, String prefix, Set<String> disabledCommands) {
        String cmdLower = commandName.toLowerCase();

        // Check if it's a slash command (starts with /)
        if (cmdLower.startsWith("/")) {
            showSlashCommandHelp(context, cmdLower.substring(1));
            return;
        }

        if (!commandRegistry.hasCommand(cmdLower)) {
            // Check if it's a slash command without the /
            var slashMeta = commandMetadataRegistry.getSlashCommandMetadata(cmdLower);
            if (slashMeta.isPresent()) {
                showSlashCommandHelp(context, cmdLower);
                return;
            }
            context.getChannel().sendMessage("❌ Unknown command: `" + commandName + "`\nTry `" + prefix + "help` for a list of commands.").queue();
            return;
        }

        boolean isDisabled = disabledCommands.contains(cmdLower);
        String statusText = isDisabled ? " (❌ Disabled)" : "";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📖 Command: " + prefix + cmdLower + statusText)
                .setColor(isDisabled ? new Color(255, 0, 0) : new Color(114, 137, 218));

        // Try to get description from metadata registry first
        var metadata = commandMetadataRegistry.getTextCommandMetadata(cmdLower);
        String description;
        String usage;
        String permissions;

        if (metadata.isPresent()) {
            var meta = metadata.get();
            description = meta.description() != null && !meta.description().isEmpty()
                    ? meta.description() : getCommandDescription(cmdLower);
            usage = meta.usage() != null && !meta.usage().isEmpty()
                    ? meta.usage() : getCommandUsage(cmdLower, prefix);
            permissions = meta.permissions() != null && meta.permissions().length > 0
                    ? formatPermissions(meta.permissions()) : getRequiredPermissions(cmdLower);
        } else {
            description = getCommandDescription(cmdLower);
            usage = getCommandUsage(cmdLower, prefix);
            permissions = getRequiredPermissions(cmdLower);
        }

        embed.setDescription(description);

        if (!usage.isEmpty()) {
            embed.addField("📝 Usage", usage, false);
        }

        // Add examples for some commands
        String examples = getCommandExamples(cmdLower, prefix);
        if (!examples.isEmpty()) {
            embed.addField("💡 Examples", examples, false);
        }

        // Category and permissions
        if (isBuiltInCommand(cmdLower)) {
            embed.addField("📁 Category", "🔧 Built-in", true);
        } else {
            String pluginName = metadata.map(CommandMetadataRegistry.CommandMetadata::pluginId).orElse("Unknown");
            embed.addField("📁 Category", "🔌 Plugin (" + pluginName + ")", true);
        }

        if (!permissions.isEmpty()) {
            embed.addField("🔒 Permissions", permissions, true);
        }

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Show detailed help for a slash command.
     */
    private void showSlashCommandHelp(CommandContext context, String commandName) {
        var metadata = commandMetadataRegistry.getSlashCommandMetadata(commandName);

        if (metadata.isEmpty()) {
            context.getChannel().sendMessage("❌ Unknown slash command: `/" + commandName + "`").queue();
            return;
        }

        var meta = metadata.get();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📖 Slash Command: /" + commandName)
                .setColor(new Color(87, 242, 135)) // Green for slash commands
                .setDescription(meta.description());

        String pluginName = meta.isBuiltIn() ? "Built-in" : meta.pluginId();
        embed.addField("📁 Category", meta.isBuiltIn() ? "🔧 Built-in" : "🔌 Plugin (" + pluginName + ")", true);

        if (meta.permissions() != null && meta.permissions().length > 0) {
            embed.addField("🔒 Permissions", formatPermissions(meta.permissions()), true);
        }

        embed.setFooter("Use this command by typing /" + commandName + " in Discord");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Format JDA Permission array to readable string.
     */
    private String formatPermissions(net.dv8tion.jda.api.Permission[] permissions) {
        if (permissions == null || permissions.length == 0) {
            return "None";
        }
        return java.util.Arrays.stream(permissions)
                .map(net.dv8tion.jda.api.Permission::getName)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private boolean isBuiltInCommand(String commandName) {
        return switch (commandName.toLowerCase()) {
            case "help", "enable", "disable", "ping", "settings", "prefix", "verbosity", "cooldown",
                 "logchannel", "botchannel", "listen", "ignore", "ai",
                 "biography", "personality", "preferences", "dialoguestyle" -> true;
            default -> false;
        };
    }

    private String getCommandDescription(String commandName) {
        return switch (commandName.toLowerCase()) {
            case "help" -> """
                    Shows information about Pudel commands including plugin commands.
                    
                    • Without arguments: Shows paginated command list with reactions for navigation
                    • With command name: Shows detailed info about that command
                    • With page number: Jumps to that page""";
            case "enable" -> """
                    Re-enable a previously disabled command so people can use it again like normal.
                    
                    Useful when you want to restore a command you previously disabled.""";
            case "disable" -> """
                    Disable a command so nobody can use it in your guild.
                    
                    **Protected commands** (cannot be disabled): help, enable, disable, settings""";
            case "ping" -> """
                    Checks if Pudel is online and displays response latency.
                    
                    Shows both REST API latency and WebSocket gateway latency.""";
            case "settings" -> """
                    Interactive configuration wizard for all Pudel settings.
                    
                    Use without arguments for guided setup, or specify a setting to change directly.""";
            case "prefix" -> """
                    Set a custom prefix for commands in your server.
                    
                    Maximum 5 characters. Common prefixes: `!`, `?`, `p!`, `>>`""";
            case "verbosity" -> """
                    Set how Pudel handles command messages after execution.
                    
                    **Level 1:** Deletes ALL command prompts after execution
                    **Level 2:** Deletes prompts unless they mention users/roles
                    **Level 3:** Keeps all prompts (default)""";
            case "cooldown" -> """
                    Set a cooldown period between commands to prevent spam.
                    
                    Staff with `Manage Messages` permission can bypass cooldowns.""";
            case "logchannel" -> """
                    Set a channel where Pudel logs all command usage.
                    
                    Useful for moderation and tracking command activity.""";
            case "botchannel" -> """
                    Restrict Pudel to only respond in a specific channel.
                    
                    Direct @Pudel mentions still work in other channels.""";
            case "listen" -> """
                    Set the channel where Pudel listens for commands.
                    
                    Alternative to botchannel. Use `-clear` to listen everywhere.""";
            case "ignore" -> """
                    Add channels to ignore list where Pudel won't respond at all.
                    
                    Completely blocks AI, commands, and passive tracking in those channels.""";
            case "ai" -> """
                    Toggle Pudel's AI/chatbot functionality.
                    
                    When disabled:
                    • No passive context tracking
                    • No memory recording
                    • Only @Pudel mentions work""";
            case "biography" -> """
                    Set Pudel's biography/backstory for your server.
                    
                    Affects how Pudel introduces herself and answers questions about herself.""";
            case "personality" -> """
                    Set Pudel's character traits and temperament.
                    
                    Affects emotional responses and general demeanor.""";
            case "preferences" -> """
                    Set Pudel's preferences and likes/dislikes.
                    
                    Affects recommendations and opinions Pudel might share.""";
            case "dialoguestyle" -> """
                    Set how Pudel structures her sentences and communicates.
                    
                    Examples: formal, casual, Victorian English, modern slang""";
            default -> "Plugin command. Check plugin documentation for details.";
        };
    }

    private String getCommandUsage(String commandName, String prefix) {
        return switch (commandName.toLowerCase()) {
            case "help" -> "`" + prefix + "help` - Show all commands\n" +
                    "`" + prefix + "help [command]` - Show specific command info\n" +
                    "`" + prefix + "help #<page>` - Jump to specific page";
            case "enable" -> "`" + prefix + "enable [command]` - Enable a disabled command\n" +
                    "`" + prefix + "enable` - Show currently disabled commands";
            case "disable" -> "`" + prefix + "disable [command]` - Disable a command";
            case "ping" -> "`" + prefix + "ping` - Check bot latency";
            case "settings" -> "`" + prefix + "settings` - Start interactive wizard\n" +
                    "`" + prefix + "settings <option> [value]` - Set specific option";
            case "prefix" -> "`" + prefix + "prefix (prefix)` - Set or view command prefix";
            case "verbosity" -> "`" + prefix + "verbosity 1 | 2 | 3` - Set verbosity level";
            case "cooldown" -> "`" + prefix + "cooldown (seconds)` - Set or view cooldown";
            case "logchannel" -> "`" + prefix + "logchannel (channel)` - Set or disable log channel";
            case "botchannel" -> "`" + prefix + "botchannel (channel)` - Set or remove bot channel";
            case "listen" -> "`" + prefix + "listen (#channel)` - Set listen channel\n" +
                    "`" + prefix + "listen -clear` - Listen everywhere";
            case "ignore" -> "`" + prefix + "ignore` - Show ignored channels\n" +
                    "`" + prefix + "ignore #channel` - Ignore a channel\n" +
                    "`" + prefix + "ignore -remove #channel` - Un-ignore\n" +
                    "`" + prefix + "ignore -clean` - Clear all";
            case "ai" -> "`" + prefix + "ai` - Show AI status\n" +
                    "`" + prefix + "ai {on|off|enable|disable}` - Toggle AI";
            case "biography" -> "`" + prefix + "biography (text)` - Set or view biography";
            case "personality" -> "`" + prefix + "personality (text)` - Set or view personality";
            case "preferences" -> "`" + prefix + "preferences (text)` - Set or view preferences";
            case "dialoguestyle" -> "`" + prefix + "dialoguestyle (text)` - Set or view dialogue style";
            default -> "";
        };
    }

    private String getCommandExamples(String commandName, String prefix) {
        return switch (commandName.toLowerCase()) {
            case "help" -> "`" + prefix + "help ping`\n`" + prefix + "help #2`";
            case "prefix" -> "`" + prefix + "prefix ?`\n`" + prefix + "prefix p!`";
            case "ignore" -> "`" + prefix + "ignore #bot-spam`\n`" + prefix + "ignore -remove #general`";
            case "ai" -> "`" + prefix + "ai off` - Disable chatbot\n`" + prefix + "ai on` - Enable chatbot";
            case "verbosity" -> "`" + prefix + "verbosity 1` - Clean mode\n`" + prefix + "verbosity 3` - Keep all";
            default -> "";
        };
    }

    private String getRequiredPermissions(String commandName) {
        return switch (commandName.toLowerCase()) {
            case "settings", "prefix", "verbosity", "cooldown", "logchannel", "botchannel",
                 "listen", "ignore", "ai", "biography", "personality", "preferences",
                 "dialoguestyle", "enable", "disable" -> "Administrator";
            default -> "None";
        };
    }
}
