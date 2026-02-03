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
package group.worldstandard.pudel.core.interaction.builtin;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.api.interaction.SlashCommandHandler;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.service.GuildInitializationService;
import group.worldstandard.pudel.model.agent.AgentDataExecutor;

import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * Slash command handler for AI settings.
 * Replaces standalone toggle commands and provides quick access to common AI settings.
 * <p>
 * Usage:
 * /ai view - Show current AI configuration
 * /ai enable - Enable AI brain
 * /ai disable - Disable AI brain
 * /ai nickname <name> - Set bot nickname
 * /ai language <code> - Set response language
 * /ai length <option> - Set response length
 * /ai formality <option> - Set formality level
 * /ai emotes <option> - Set emote usage
 * /ai agent - View agent capabilities info
 * /ai tables - View stored data tables
 * /ai memories - View stored memories
 *
 * @deprecated Use {@link BuiltinCommands} instead. This class will be removed in the next version.
 */
@Deprecated(since = "2.0.0", forRemoval = true)
@Component
public class AISlashCommand implements SlashCommandHandler {

    private final GuildInitializationService guildInitializationService;
    private final AgentDataExecutor agentDataExecutor;


    public AISlashCommand(GuildInitializationService guildInitializationService,
                          AgentDataExecutor agentDataExecutor) {
        this.guildInitializationService = guildInitializationService;
        this.agentDataExecutor = agentDataExecutor;
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash("ai", "Configure Pudel's AI personality and behavior")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addSubcommands(
                        new SubcommandData("view", "View current AI configuration"),
                        new SubcommandData("enable", "Enable AI brain"),
                        new SubcommandData("disable", "Disable AI brain"),
                        new SubcommandData("nickname", "Set bot nickname")
                                .addOption(OptionType.STRING, "name", "New nickname (max 32 characters, or 'reset')", true),
                        new SubcommandData("language", "Set response language")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "code", "Language code", true)
                                                .addChoice("English", "en")
                                                .addChoice("Thai (ภาษาไทย)", "th")
                                                .addChoice("Japanese (日本語)", "ja")
                                                .addChoice("Korean (한국어)", "ko")
                                                .addChoice("Chinese (中文)", "zh")
                                                .addChoice("German (Deutsch)", "de")
                                                .addChoice("French (Français)", "fr")
                                                .addChoice("Spanish (Español)", "es")
                                                .addChoice("Portuguese (Português)", "pt")
                                                .addChoice("Russian (Русский)", "ru")
                                                .addChoice("Auto-detect", "auto")
                                ),
                        new SubcommandData("length", "Set response length")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "option", "Response length", true)
                                                .addChoice("Short (1-2 sentences)", "short")
                                                .addChoice("Medium (2-4 sentences)", "medium")
                                                .addChoice("Detailed (comprehensive)", "detailed")
                                ),
                        new SubcommandData("formality", "Set formality level")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "option", "Formality level", true)
                                                .addChoice("Casual (friendly, relaxed)", "casual")
                                                .addChoice("Balanced (mix)", "balanced")
                                                .addChoice("Formal (professional)", "formal")
                                ),
                        new SubcommandData("emotes", "Set emote usage")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "option", "Emote usage level", true)
                                                .addChoice("None", "none")
                                                .addChoice("Minimal", "minimal")
                                                .addChoice("Moderate", "moderate")
                                                .addChoice("Frequent", "frequent")
                                ),
                        new SubcommandData("agent", "View agent capabilities"),
                        new SubcommandData("tables", "View stored data tables"),
                        new SubcommandData("memories", "View stored memories")
                );
    }

    @Override
    public boolean isGlobal() {
        // Guild-specific commands register instantly, global commands take up to 1 hour
        return false;
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("❌ This command only works in guilds!").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("❌ Invalid subcommand").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(guildId);

        switch (subcommand) {
            case "view" -> handleView(event, settings);
            case "enable" -> handleEnable(event, settings, guildId);
            case "disable" -> handleDisable(event, settings, guildId);
            case "nickname" -> handleNickname(event, settings, guildId);
            case "language" -> handleLanguage(event, settings, guildId);
            case "length" -> handleLength(event, settings, guildId);
            case "formality" -> handleFormality(event, settings, guildId);
            case "emotes" -> handleEmotes(event, settings, guildId);
            case "agent" -> handleAgent(event);
            case "tables" -> handleTables(event);
            case "memories" -> handleMemories(event);
            default -> event.reply("❌ Unknown subcommand").setEphemeral(true).queue();
        }
    }

    private void handleView(SlashCommandInteractionEvent event, GuildSettings settings) {
        boolean aiEnabled = settings.getAiEnabled() != null ? settings.getAiEnabled() : true;
        String nickname = settings.getNickname() != null ? settings.getNickname() : "Pudel";
        String language = settings.getLanguage() != null ? settings.getLanguage() : "en";
        String responseLength = settings.getResponseLength() != null ? settings.getResponseLength() : "medium";
        String formality = settings.getFormality() != null ? settings.getFormality() : "balanced";
        String emoteUsage = settings.getEmoteUsage() != null ? settings.getEmoteUsage() : "moderate";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤖 " + nickname + "'s AI Configuration")
                .setColor(aiEnabled ? new Color(67, 181, 129) : new Color(240, 71, 71))
                .addField("AI Status", aiEnabled ? "✅ Enabled" : "❌ Disabled", true)
                .addField("Nickname", nickname, true)
                .addField("Language", language.toUpperCase(), true)
                .addField("Response Length", responseLength, true)
                .addField("Formality", formality, true)
                .addField("Emote Usage", emoteUsage, true)
                .addField("Biography", truncate(settings.getBiography(), 200, "Not set"), false)
                .addField("Personality", truncate(settings.getPersonality(), 200, "Not set"), false)
                .addField("Preferences", truncate(settings.getPreferences(), 200, "Not set"), false)
                .addField("Dialogue Style", truncate(settings.getDialogueStyle(), 200, "Not set"), false)
                .setFooter("Use !ai setup for full personality wizard");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private String truncate(String text, int maxLength, String defaultValue) {
        if (text == null || text.isEmpty()) {
            return defaultValue;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private void handleEnable(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        settings.setAiEnabled(true);
        guildInitializationService.updateGuildSettings(guildId, settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤖 AI Enabled")
                .setColor(new Color(67, 181, 129))
                .setDescription("""
                        Pudel's AI brain is now **active**!
                        
                        I will now:
                        • Respond to name/nickname mentions
                        • Track conversation context passively
                        • Build memory from conversations
                        • Act according to personality settings
                        
                        Use `!ai setup` to configure my personality!""");

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleDisable(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        settings.setAiEnabled(false);
        guildInitializationService.updateGuildSettings(guildId, settings);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤖 AI Disabled")
                .setColor(new Color(240, 71, 71))
                .setDescription("""
                        Pudel's AI brain is now **disabled**.
                        
                        I will now:
                        • Only respond to direct `@Pudel` mentions
                        • Not record any conversation context
                        • Not build memory
                        
                        **Note:** You can still use `@Pudel [command]` to run commands without the prefix.""");

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleNickname(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        String nickname = event.getOption("name", OptionMapping::getAsString);

        if (nickname == null || nickname.isEmpty()) {
            event.reply("❌ Nickname cannot be empty!").setEphemeral(true).queue();
            return;
        }

        if (nickname.equalsIgnoreCase("reset") || nickname.equalsIgnoreCase("clear")) {
            settings.setNickname(null);
            guildInitializationService.updateGuildSettings(guildId, settings);
            event.reply("✅ Nickname reset to default: **Pudel**").queue();
            return;
        }

        if (nickname.length() > 32) {
            event.reply("❌ Nickname must be 32 characters or less!").setEphemeral(true).queue();
            return;
        }

        settings.setNickname(nickname);
        guildInitializationService.updateGuildSettings(guildId, settings);
        event.reply("✅ Nickname set to: **" + nickname + "**").queue();
    }

    private void handleLanguage(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        String language = event.getOption("code", OptionMapping::getAsString);

        settings.setLanguage(language);
        guildInitializationService.updateGuildSettings(guildId, settings);
        event.reply("✅ Response language set to: **" + getLanguageName(language) + "**").queue();
    }

    private String getLanguageName(String code) {
        if (code == null) return "English (default)";
        return switch (code) {
            case "en" -> "English";
            case "th" -> "Thai (ภาษาไทย)";
            case "ja" -> "Japanese (日本語)";
            case "ko" -> "Korean (한국어)";
            case "zh" -> "Chinese (中文)";
            case "de" -> "German (Deutsch)";
            case "fr" -> "French (Français)";
            case "es" -> "Spanish (Español)";
            case "pt" -> "Portuguese (Português)";
            case "ru" -> "Russian (Русский)";
            case "it" -> "Italian (Italiano)";
            case "nl" -> "Dutch (Nederlands)";
            case "pl" -> "Polish (Polski)";
            case "vi" -> "Vietnamese (Tiếng Việt)";
            case "id" -> "Indonesian (Bahasa Indonesia)";
            case "auto" -> "Auto-detect";
            default -> code.toUpperCase();
        };
    }

    private void handleLength(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        String length = event.getOption("option", OptionMapping::getAsString);

        settings.setResponseLength(length);
        guildInitializationService.updateGuildSettings(guildId, settings);

        String description = switch (length) {
            case "short" -> "Brief 1-2 sentence responses";
            case "medium" -> "Balanced 2-4 sentence responses";
            case "detailed" -> "Comprehensive responses with full explanations";
            default -> "";
        };

        event.reply("✅ Response length set to: **" + length + "** - " + description).queue();
    }

    private void handleFormality(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        String formality = event.getOption("option", OptionMapping::getAsString);

        settings.setFormality(formality);
        guildInitializationService.updateGuildSettings(guildId, settings);

        String description = switch (formality) {
            case "casual" -> "Friendly, relaxed tone with emojis and contractions";
            case "balanced" -> "Mix of friendly and professional";
            case "formal" -> "Professional, polite tone suitable for business";
            default -> "";
        };

        event.reply("✅ Formality set to: **" + formality + "** - " + description).queue();
    }

    private void handleEmotes(SlashCommandInteractionEvent event, GuildSettings settings, String guildId) {
        String usage = event.getOption("option", OptionMapping::getAsString);

        settings.setEmoteUsage(usage);
        guildInitializationService.updateGuildSettings(guildId, settings);

        String description = switch (usage) {
            case "none" -> "No emojis or emoticons in responses";
            case "minimal" -> "Occasional emojis for emphasis";
            case "moderate" -> "Regular use of emojis";
            case "frequent" -> "Heavy emoji usage for expressive responses ✨🎉";
            default -> "";
        };

        event.reply("✅ Emote usage set to: **" + usage + "** - " + description).queue();
    }

    private void handleAgent(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤵 Pudel Agent System (Maid/Secretary AI)")
                .setColor(new Color(114, 137, 218))
                .setDescription("""
                        Pudel can act as your personal maid/secretary, managing data through natural conversation!
                        
                        **What I can do:**
                        • 📊 Create tables to organize information
                        • 📝 Store documents, notes, news
                        • 🔍 Search and retrieve data
                        • 🧠 Remember facts and recall them
                        
                        **How to use:**
                        Just talk to me naturally! For example:
                        • "Pudel, remember the meeting is on Friday"
                        • "Create a notes table for project updates"
                        • "Save this news article in my documents"
                        • "Find all notes about features"
                        
                        **Commands:**
                        • `/ai tables` - View your data tables
                        • `/ai memories` - View stored memories
                        """)
                .setFooter("All data is stored in your guild's private database");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleTables(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();

        List<Map<String, Object>> tables = agentDataExecutor.listCustomTables(guildId, true);

        if (tables.isEmpty()) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📊 Agent Tables")
                    .setColor(new Color(114, 137, 218))
                    .setDescription("""
                            No tables created yet!
                            
                            Talk to me to create tables, for example:
                            "Pudel, create a notes table for team updates\"""");
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Agent Tables")
                .setColor(new Color(67, 181, 129));

        StringBuilder tableList = new StringBuilder();
        for (Map<String, Object> table : tables) {
            String name = (String) table.get("table_name");
            String desc = table.get("description") != null ? (String) table.get("description") : "No description";
            Object rowCount = table.get("row_count");

            tableList.append("• **").append(name.replace("agent_", "")).append("**");
            tableList.append(" (").append(rowCount).append(" entries)\n");
            tableList.append("  ").append(truncate(desc, 50, "")).append("\n\n");
        }

        embed.setDescription(tableList.toString());
        embed.setFooter("Total: " + tables.size() + " tables");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleMemories(SlashCommandInteractionEvent event) {
        long guildId = event.getGuild().getIdLong();

        List<Map<String, Object>> memories = agentDataExecutor.getMemoriesByCategory(guildId, true, "agent_memory");

        if (memories.isEmpty()) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🧠 Agent Memories")
                    .setColor(new Color(114, 137, 218))
                    .setDescription("""
                            No memories stored yet!
                            
                            Ask me to remember things, for example:
                            "Pudel, remember that the deadline is next Friday\"""");
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🧠 Agent Memories")
                .setColor(new Color(67, 181, 129));

        StringBuilder memoryList = new StringBuilder();
        int count = 0;
        for (Map<String, Object> memory : memories) {
            if (count >= 20) {
                memoryList.append("\n*... and ").append(memories.size() - 20).append(" more*");
                break;
            }

            String key = (String) memory.get("key");
            String value = (String) memory.get("value");

            memoryList.append("• **").append(key).append("**: ")
                    .append(truncate(value, 50, "")).append("\n");
            count++;
        }

        embed.setDescription(memoryList.toString());
        embed.setFooter("Total: " + memories.size() + " memories");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}
