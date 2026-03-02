/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
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
package group.worldstandard.pudel.core.interaction.builtin;

import group.worldstandard.pudel.api.annotation.ButtonHandler;
import group.worldstandard.pudel.api.annotation.Plugin;
import group.worldstandard.pudel.api.annotation.TextCommand;
import group.worldstandard.pudel.api.command.CommandContext;
import group.worldstandard.pudel.api.interaction.InteractionManager;
import group.worldstandard.pudel.api.interaction.SlashCommandHandler;
import group.worldstandard.pudel.core.command.CommandMetadataRegistry;
import group.worldstandard.pudel.core.config.PudelPropertiesImpl;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.service.GuildInitializationService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Built-in Pudel text commands (prefix-based).
 * <p>
 * Provides {@code !ping} and {@code !help} as text-based counterparts
 * to the slash commands in {@link BuiltinCommands}. Registered through
 * the same annotation processor pipeline as plugins.
 */
@Component
@Plugin(
    name = "pudel-core-text",
    version = "2.1.1",
    author = "World Standard Group",
    description = "Built-in Pudel text commands"
)
public class BuiltinTextCommands {

    private static final Color ACCENT = new Color(0x5865F2);
    private static final String BTN_PREFIX = "help:";
    private static final int ITEMS_PER_PAGE = 8;
    private static final long SESSION_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes

    private final GuildInitializationService guildInitializationService;
    private final CommandMetadataRegistry metadataRegistry;
    private final InteractionManager interactionManager;
    private final PudelPropertiesImpl pudelProperties;

    /** Active help sessions keyed by message ID. */
    private final Map<Long, HelpSession> activeSessions = new ConcurrentHashMap<>();

    private final ScheduledExecutorService sessionCleaner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "help-session-cleaner");
        t.setDaemon(true);
        return t;
    });

    public BuiltinTextCommands(GuildInitializationService guildInitializationService,
                               CommandMetadataRegistry metadataRegistry,
                               InteractionManager interactionManager,
                               PudelPropertiesImpl pudelProperties) {
        this.guildInitializationService = guildInitializationService;
        this.metadataRegistry = metadataRegistry;
        this.interactionManager = interactionManager;
        this.pudelProperties = pudelProperties;

        // Periodically clean up expired sessions
        sessionCleaner.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            activeSessions.entrySet().removeIf(e -> now - e.getValue().createdAt > SESSION_TIMEOUT_MS);
        }, 1, 1, TimeUnit.MINUTES);
    }

    // =====================================================
    // !ping
    // =====================================================

    @TextCommand(value = "ping", description = "Check bot latency", usage = "ping")
    public void handlePing(CommandContext ctx) {
        long gatewayPing = ctx.getEvent().getJDA().getGatewayPing();
        long start = System.currentTimeMillis();

        ctx.getChannel().sendMessage("🏓 Pinging...").queue(msg -> {
            long roundTrip = System.currentTimeMillis() - start;

            MessageEmbed embed = new EmbedBuilder()
                    .setTitle("🏓 Pong!")
                    .setColor(ACCENT)
                    .addField("Gateway", gatewayPing + "ms", true)
                    .addField("Round-trip", roundTrip + "ms", true)
                    .setFooter(pudelProperties.getName() + " v" + pudelProperties.getVersion())
                    .build();

            msg.editMessage("").setEmbeds(embed).queue();
        });
    }

    // =====================================================
    // !help [command] [page]
    // =====================================================

    @TextCommand(value = "help", aliases = {"h", "?"}, description = "Show available commands", usage = "help [command | page]")
    public void handleHelp(CommandContext ctx) {
        String prefix = "!";
        if (ctx.isFromGuild() && ctx.getGuild() != null) {
            GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(ctx.getGuild().getId());
            prefix = settings.getPrefix() != null ? settings.getPrefix() : "!";
        }

        // If a specific command is requested (not a number), show detailed help
        if (ctx.hasArgs()) {
            String firstArg = ctx.getArg(0);

            // Check if it's a page number
            try {
                int requestedPage = Integer.parseInt(firstArg);
                sendHelpPage(ctx, prefix, Math.max(1, requestedPage));
                return;
            } catch (NumberFormatException ignored) {
                // Not a number, treat as command name
            }

            showCommandHelp(ctx, firstArg.toLowerCase(), prefix);
            return;
        }

        // Show first page
        sendHelpPage(ctx, prefix, 1);
    }

    // =====================================================
    // Button Handler for Help Paging
    // =====================================================

    @ButtonHandler(BTN_PREFIX)
    public void onHelpButton(ButtonInteractionEvent event) {
        long messageId = event.getMessageIdLong();
        HelpSession session = activeSessions.get(messageId);

        if (session == null) {
            event.reply("❌ This help session has expired. Use the help command again.").setEphemeral(true).queue();
            return;
        }

        // Only the original user can navigate
        if (event.getUser().getIdLong() != session.userId) {
            event.reply("❌ Only the person who used the command can navigate.").setEphemeral(true).queue();
            return;
        }

        String id = event.getComponentId().substring(BTN_PREFIX.length());

        switch (id) {
            case "prev" -> {
                session.page = Math.max(0, session.page - 1);
                event.editMessage(buildHelpEdit(session)).queue();
            }
            case "next" -> {
                session.page = Math.min(session.totalPages - 1, session.page + 1);
                event.editMessage(buildHelpEdit(session)).queue();
            }
            case "first" -> {
                session.page = 0;
                event.editMessage(buildHelpEdit(session)).queue();
            }
            case "last" -> {
                session.page = session.totalPages - 1;
                event.editMessage(buildHelpEdit(session)).queue();
            }
            default -> event.deferEdit().queue();
        }
    }

    // =====================================================
    // Help Page Builder
    // =====================================================

    private void sendHelpPage(CommandContext ctx, String prefix, int requestedPage) {
        List<HelpEntry> entries = collectAllCommands(prefix);
        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / ITEMS_PER_PAGE));
        int page = Math.min(requestedPage - 1, totalPages - 1); // 0-based internally

        MessageEmbed embed = buildHelpEmbed(entries, page, prefix, totalPages);

        if (totalPages <= 1) {
            // No paging needed — single page, no buttons
            ctx.getChannel().sendMessageEmbeds(embed).queue();
        } else {
            // Send with navigation buttons and track session
            MessageCreateBuilder msgBuilder = new MessageCreateBuilder()
                    .setEmbeds(embed)
                    .setComponents(buildHelpButtons(page, totalPages));

            ctx.getChannel().sendMessage(msgBuilder.build()).queue(msg -> {
                HelpSession session = new HelpSession(
                        ctx.getUser().getIdLong(), prefix, entries, totalPages, page
                );
                activeSessions.put(msg.getIdLong(), session);
            });
        }
    }

    private MessageEditData buildHelpEdit(HelpSession session) {
        return new MessageEditBuilder()
                .setEmbeds(buildHelpEmbed(session.entries, session.page, session.prefix, session.totalPages))
                .setComponents(buildHelpButtons(session.page, session.totalPages))
                .build();
    }

    private MessageEmbed buildHelpEmbed(List<HelpEntry> entries, int page, String prefix, int totalPages) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📚 " + pudelProperties.getName() + " Commands")
                .setColor(ACCENT)
                .setDescription("Use `" + prefix + "help <command>` for details on a specific command."
                        + (totalPages > 1 ? "\nUse `" + prefix + "help <page>` to jump to a page." : ""));

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, entries.size());

        // Group entries on this page by category
        Map<String, StringBuilder> categories = new LinkedHashMap<>();
        for (int i = start; i < end; i++) {
            HelpEntry entry = entries.get(i);
            categories.computeIfAbsent(entry.category, k -> new StringBuilder())
                    .append(entry.line)
                    .append("\n");
        }

        for (Map.Entry<String, StringBuilder> cat : categories.entrySet()) {
            String fieldValue = cat.getValue().toString().trim();
            // Discord field value limit is 1024, truncate if needed
            if (fieldValue.length() > 1024) {
                fieldValue = fieldValue.substring(0, 1021) + "...";
            }
            embed.addField(cat.getKey(), fieldValue, false);
        }

        embed.setFooter("Page " + (page + 1) + "/" + totalPages
                + " • " + entries.size() + " commands"
                + " • " + pudelProperties.getName() + " v" + pudelProperties.getVersion());

        return embed.build();
    }

    private ActionRow buildHelpButtons(int page, int totalPages) {
        return ActionRow.of(
                Button.secondary(BTN_PREFIX + "first", Emoji.fromUnicode("⏮"))
                        .withDisabled(page == 0),
                Button.primary(BTN_PREFIX + "prev", Emoji.fromUnicode("◀"))
                        .withDisabled(page == 0),
                Button.secondary("help:page_indicator", "Page " + (page + 1) + "/" + totalPages)
                        .asDisabled(),
                Button.primary(BTN_PREFIX + "next", Emoji.fromUnicode("▶"))
                        .withDisabled(page >= totalPages - 1),
                Button.secondary(BTN_PREFIX + "last", Emoji.fromUnicode("⏭"))
                        .withDisabled(page >= totalPages - 1)
        );
    }

    // =====================================================
    // Command Collector
    // =====================================================

    private List<HelpEntry> collectAllCommands(String prefix) {
        List<HelpEntry> entries = new ArrayList<>();

        // ---- Built-in Slash Commands ----
        for (SlashCommandHandler handler : interactionManager.getAllSlashCommands()) {
            String name = handler.getCommandData().getName();
            String desc = handler.getCommandData().getDescription();
            if (desc.length() > 50) desc = desc.substring(0, 47) + "...";

            var metadata = metadataRegistry.getSlashCommandMetadata(name);
            boolean builtIn = metadata.isEmpty() || metadata.get().isBuiltIn();

            String category = builtIn ? "⚙️ Slash Commands (Built-in)" : "🧩 Slash Commands (Plugins)";
            entries.add(new HelpEntry(category, "`/" + name + "` — " + desc, builtIn ? 0 : 2, name));
        }

        // ---- Text Commands ----
        Collection<CommandMetadataRegistry.CommandMetadata> textMetas = metadataRegistry.getAllTextCommands();
        List<CommandMetadataRegistry.CommandMetadata> sorted = new ArrayList<>(textMetas);
        sorted.sort(Comparator.comparing(CommandMetadataRegistry.CommandMetadata::name));

        for (CommandMetadataRegistry.CommandMetadata meta : sorted) {
            String desc = meta.shortDescription();
            boolean builtIn = meta.isBuiltIn();

            String category = builtIn ? "📝 Text Commands (Built-in)" : "🧩 Text Commands (Plugins)";
            entries.add(new HelpEntry(category, "`" + prefix + meta.name() + "` — " + desc, builtIn ? 1 : 3, meta.name()));
        }

        // Sort: built-in slash → built-in text → plugin slash → plugin text
        entries.sort(Comparator.comparingInt((HelpEntry e) -> e.sortOrder)
                .thenComparing(e -> e.name));

        return entries;
    }

    // =====================================================
    // Single Command Help
    // =====================================================

    private void showCommandHelp(CommandContext ctx, String cmdName, String prefix) {
        // Check text commands first
        Optional<CommandMetadataRegistry.CommandMetadata> textMeta = metadataRegistry.getTextCommandMetadata(cmdName);
        if (textMeta.isPresent()) {
            CommandMetadataRegistry.CommandMetadata meta = textMeta.get();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📝 Command: " + prefix + meta.name())
                    .setColor(ACCENT)
                    .addField("Description", meta.description().isEmpty() ? "No description" : meta.description(), false);

            if (!meta.usage().isEmpty()) {
                embed.addField("Usage", "`" + prefix + meta.usage() + "`", false);
            }
            if (meta.permissions().length > 0) {
                StringBuilder perms = new StringBuilder();
                for (var perm : meta.permissions()) {
                    perms.append("`").append(perm.getName()).append("` ");
                }
                embed.addField("Permissions", perms.toString().trim(), false);
            }
            embed.addField("Source", meta.isBuiltIn() ? "Built-in" : "Plugin: " + meta.pluginId(), true);

            ctx.getChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        // Check slash commands
        Optional<CommandMetadataRegistry.CommandMetadata> slashMeta = metadataRegistry.getSlashCommandMetadata(cmdName);
        if (slashMeta.isPresent()) {
            CommandMetadataRegistry.CommandMetadata meta = slashMeta.get();

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("⚡ Slash Command: /" + meta.name())
                    .setColor(ACCENT)
                    .addField("Description", meta.description().isEmpty() ? "No description" : meta.description(), false);

            if (meta.permissions().length > 0) {
                StringBuilder perms = new StringBuilder();
                for (var perm : meta.permissions()) {
                    perms.append("`").append(perm.getName()).append("` ");
                }
                embed.addField("Permissions", perms.toString().trim(), false);
            }
            embed.addField("Source", meta.isBuiltIn() ? "Built-in" : "Plugin: " + meta.pluginId(), true);

            ctx.getChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        // Command not found
        ctx.reply("❌ Unknown command: `" + cmdName + "`. Use `" + prefix + "help` to see all commands.");
    }

    // =====================================================
    // Inner Types
    // =====================================================

    private record HelpEntry(String category, String line, int sortOrder, String name) {}

    private static class HelpSession {
        final long userId;
        final String prefix;
        final List<HelpEntry> entries;
        final int totalPages;
        final long createdAt;
        int page;

        HelpSession(long userId, String prefix, List<HelpEntry> entries, int totalPages, int page) {
            this.userId = userId;
            this.prefix = prefix;
            this.entries = entries;
            this.totalPages = totalPages;
            this.page = page;
            this.createdAt = System.currentTimeMillis();
        }
    }
}

