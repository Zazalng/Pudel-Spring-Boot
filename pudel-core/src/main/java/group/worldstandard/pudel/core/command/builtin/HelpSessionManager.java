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
package group.worldstandard.pudel.core.command.builtin;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.core.command.CommandMetadataRegistry;
import group.worldstandard.pudel.core.command.CommandRegistry;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages interactive help sessions with reaction-based pagination.
 * Each session tracks a help message, the user who invoked it, and current page state.
 */
@Component
public class HelpSessionManager {

    // Emoji constants for navigation
    public static final String EMOJI_FIRST = "⏮️";
    public static final String EMOJI_PREV = "◀️";
    public static final String EMOJI_NEXT = "▶️";
    public static final String EMOJI_LAST = "⏭️";
    public static final String EMOJI_CLOSE = "❌";
    public static final String[] PAGE_EMOJIS = {"1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟"};

    private static final int COMMANDS_PER_PAGE = 10;
    private static final long SESSION_TIMEOUT_MINUTES = 5;

    private final Map<Long, HelpSession> activeSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    private final CommandMetadataRegistry metadataRegistry;

    public HelpSessionManager(CommandMetadataRegistry metadataRegistry) {
        this.metadataRegistry = metadataRegistry;
        // Schedule cleanup of expired sessions
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredSessions, 1, 1, TimeUnit.MINUTES);
    }

    /**
     * Create a new help session for a message.
     */
    public void createSession(long messageId, long userId, String prefix, List<String> commandNames,
                              Set<String> disabledCommands, int totalPages) {
        HelpSession session = new HelpSession(userId, prefix, commandNames, disabledCommands, totalPages);
        activeSessions.put(messageId, session);
    }

    /**
     * Get an active session by message ID.
     */
    public HelpSession getSession(long messageId) {
        return activeSessions.get(messageId);
    }

    /**
     * Check if a message has an active session.
     */
    public boolean hasSession(long messageId) {
        return activeSessions.containsKey(messageId);
    }

    /**
     * Remove a session.
     */
    public void removeSession(long messageId) {
        activeSessions.remove(messageId);
    }

    /**
     * Clean up expired sessions.
     */
    private void cleanupExpiredSessions() {
        Instant cutoff = Instant.now().minusSeconds(SESSION_TIMEOUT_MINUTES * 60);
        activeSessions.entrySet().removeIf(entry -> entry.getValue().getCreatedAt().isBefore(cutoff));
    }

    /**
     * Build a help page embed.
     */
    public MessageEmbed buildHelpEmbed(HelpSession session, int page, CommandRegistry commandRegistry) {
        List<String> commandNames = session.getCommandNames();
        int totalCommands = commandNames.size();
        int totalPages = session.getTotalPages();
        String prefix = session.getPrefix();
        Set<String> disabledCommands = session.getDisabledCommands();

        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;
        session.setCurrentPage(page);

        int startIndex = (page - 1) * COMMANDS_PER_PAGE;
        int endIndex = Math.min(startIndex + COMMANDS_PER_PAGE, totalCommands);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📚 Pudel Commands")
                .setColor(new Color(114, 137, 218))
                .setDescription("React to navigate pages • `" + prefix + "help [command]` for details\n\n" +
                        "**Syntax:** `()` Optional | `[]` Required | `{}` Conditional");

        StringBuilder builtInCommands = new StringBuilder();
        StringBuilder pluginCommands = new StringBuilder();

        for (int i = startIndex; i < endIndex; i++) {
            String cmdName = commandNames.get(i);
            boolean isDisabled = disabledCommands.contains(cmdName.toLowerCase());
            String status = isDisabled ? " ❌" : "";
            String shortDesc = getShortDescription(cmdName);

            String line = "`" + prefix + cmdName + "`" + status + " - " + shortDesc + "\n";

            if (isBuiltInCommand(cmdName)) {
                builtInCommands.append(line);
            } else {
                pluginCommands.append(line);
            }
        }

        if (!builtInCommands.isEmpty()) {
            embed.addField("🔧 Built-in Text Commands", builtInCommands.toString(), false);
        }

        if (!pluginCommands.isEmpty()) {
            embed.addField("🔌 Plugin Text Commands", pluginCommands.toString(), false);
        }

        // Add slash commands section (only on first page for overview)
        if (page == 1) {
            StringBuilder slashCommands = new StringBuilder();
            var allSlashCommands = metadataRegistry.getAllSlashCommands();
            int slashCount = 0;
            for (var slashCmd : allSlashCommands) {
                if (slashCount >= 5) {
                    slashCommands.append("*... and ").append(allSlashCommands.size() - 5).append(" more*\n");
                    break;
                }
                slashCommands.append("`/").append(slashCmd.name()).append("` - ").append(slashCmd.shortDescription()).append("\n");
                slashCount++;
            }
            if (!slashCommands.isEmpty()) {
                embed.addField("⚡ Slash Commands", slashCommands.toString(), false);
            }
        }

        // Navigation hints
        int totalSlashCommands = metadataRegistry.getAllSlashCommands().size();
        String footer = "Page " + page + "/" + totalPages +
                " • Text: " + totalCommands + " | Slash: " + totalSlashCommands +
                " • ❌ = disabled";

        embed.setFooter(footer);

        // Add page number buttons info
        if (totalPages > 1) {
            embed.addField("📍 Navigation",
                    EMOJI_FIRST + " First • " + EMOJI_PREV + " Previous • " + EMOJI_NEXT + " Next • " + EMOJI_LAST + " Last • " + EMOJI_CLOSE + " Close",
                    false);
        }

        return embed.build();
    }

    /**
     * Add navigation reactions to a message.
     */
    public void addNavigationReactions(Message message, int totalPages) {
        if (totalPages <= 1) {
            message.addReaction(Emoji.fromUnicode(EMOJI_CLOSE)).queue();
            return;
        }

        message.addReaction(Emoji.fromUnicode(EMOJI_FIRST)).queue();
        message.addReaction(Emoji.fromUnicode(EMOJI_PREV)).queue();
        message.addReaction(Emoji.fromUnicode(EMOJI_NEXT)).queue();
        message.addReaction(Emoji.fromUnicode(EMOJI_LAST)).queue();
        message.addReaction(Emoji.fromUnicode(EMOJI_CLOSE)).queue();
    }

    /**
     * Handle a navigation reaction.
     * @return the new page number, or -1 to close
     */
    public int handleNavigation(String emoji, HelpSession session) {
        int currentPage = session.getCurrentPage();
        int totalPages = session.getTotalPages();

        return switch (emoji) {
            case EMOJI_FIRST -> 1;
            case EMOJI_PREV -> Math.max(1, currentPage - 1);
            case EMOJI_NEXT -> Math.min(totalPages, currentPage + 1);
            case EMOJI_LAST -> totalPages;
            case EMOJI_CLOSE -> -1;
            default -> {
                // Check for number emoji (direct page jump)
                for (int i = 0; i < PAGE_EMOJIS.length; i++) {
                    if (PAGE_EMOJIS[i].equals(emoji)) {
                        int targetPage = i + 1;
                        yield targetPage <= totalPages ? targetPage : currentPage;
                    }
                }
                yield currentPage;
            }
        };
    }

    private boolean isBuiltInCommand(String commandName) {
        // Check metadata registry first
        var metadata = metadataRegistry.getTextCommandMetadata(commandName);
        return metadata.map(CommandMetadataRegistry.CommandMetadata::isBuiltIn).orElseGet(() -> switch (commandName.toLowerCase()) {
            case "help", "enable", "disable", "ping", "settings", "prefix", "verbosity", "cooldown",
                 "logchannel", "botchannel", "listen", "ignore", "ai",
                 "biography", "personality", "preferences", "dialoguestyle" -> true;
            default -> false;
        });
    }

    private String getShortDescription(String commandName) {
        // Try to get description from metadata registry first
        var metadata = metadataRegistry.getTextCommandMetadata(commandName);
        if (metadata.isPresent() && metadata.get().description() != null && !metadata.get().description().isEmpty()) {
            return metadata.get().shortDescription();
        }

        // Fallback to hardcoded descriptions for built-in commands
        return switch (commandName.toLowerCase()) {
            case "help" -> "Show command list";
            case "enable" -> "Enable a command";
            case "disable" -> "Disable a command";
            case "ping" -> "Check bot latency";
            case "settings" -> "Configuration wizard";
            case "prefix" -> "Set command prefix";
            case "verbosity" -> "Message cleanup level";
            case "cooldown" -> "Command cooldown";
            case "logchannel" -> "Set log channel";
            case "botchannel" -> "Restrict to channel";
            case "listen" -> "Set listen channel";
            case "ignore" -> "Ignore channels";
            case "ai" -> "Toggle AI features";
            case "biography" -> "Set bot biography";
            case "personality" -> "Set bot personality";
            case "preferences" -> "Set bot preferences";
            case "dialoguestyle" -> "Set dialogue style";
            default -> "Plugin command";
        };
    }

    public static int getCommandsPerPage() {
        return COMMANDS_PER_PAGE;
    }

    /**
     * Represents an active help session.
     */
    public static class HelpSession {
        private final long userId;
        private final String prefix;
        private final List<String> commandNames;
        private final Set<String> disabledCommands;
        private final int totalPages;
        private final Instant createdAt;
        private int currentPage = 1;

        public HelpSession(long userId, String prefix, List<String> commandNames,
                          Set<String> disabledCommands, int totalPages) {
            this.userId = userId;
            this.prefix = prefix;
            this.commandNames = new ArrayList<>(commandNames);
            this.disabledCommands = disabledCommands;
            this.totalPages = totalPages;
            this.createdAt = Instant.now();
        }

        public long getUserId() {
            return userId;
        }

        public String getPrefix() {
            return prefix;
        }

        public List<String> getCommandNames() {
            return commandNames;
        }

        public Set<String> getDisabledCommands() {
            return disabledCommands;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public int getCurrentPage() {
            return currentPage;
        }

        public void setCurrentPage(int currentPage) {
            this.currentPage = currentPage;
        }
    }
}

