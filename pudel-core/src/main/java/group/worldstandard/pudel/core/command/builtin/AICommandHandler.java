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
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.api.command.CommandContext;
import group.worldstandard.pudel.api.command.TextCommandHandler;
import group.worldstandard.pudel.core.command.CommandContextImpl;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.service.GuildInitializationService;

import java.awt.Color;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Handler for the !ai command.
 * Controls Pudel's AI/chatbot personality configuration through text-based wizard.
 * <p>
 * Standalone settings (on/off, nickname, language, etc.) have been migrated to
 * slash commands (/ai). This handler now focuses on:
 * - !ai - Show status overview and redirect to slash commands
 * - !ai setup/wizard - Start interactive personality wizard (requires sequential text input)
 * <p>
 * Text-based settings are still available for complex multi-line inputs that
 * don't work well with slash commands (biography, personality, preferences, etc.)
 */
@Component
public class AICommandHandler extends ListenerAdapter implements TextCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(AICommandHandler.class);

    private final GuildInitializationService guildInitializationService;

    // Track active wizard sessions: guildId_userId -> WizardSession
    private final Map<String, WizardSession> activeWizards = new ConcurrentHashMap<>();

    private static final Set<String> VALID_LANGUAGES = Set.of(
            "en", "th", "ja", "ko", "zh", "de", "fr", "es", "pt", "ru", "it", "nl", "pl", "vi", "id", "auto"
    );

    public AICommandHandler(GuildInitializationService guildInitializationService) {
        this.guildInitializationService = guildInitializationService;
    }

    @Override
    public void handle(CommandContext context) {
        if (!context.isFromGuild()) {
            context.getChannel().sendMessage("❌ This command only works in guilds!").queue();
            return;
        }

        // Check for admin permission
        if (!context.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            context.getChannel().sendMessage("❌ You need ADMINISTRATOR permission to use this command!").queue();
            return;
        }

        String guildId = context.getGuild().getId();
        GuildSettings settings = guildInitializationService.getOrCreateGuildSettings(guildId);

        if (context.getArgs().length == 0) {
            // Show current status and redirect to slash commands
            showAIStatus(context, settings);
            return;
        }

        String action = context.getArgs()[0].toLowerCase();

        switch (action) {
            // Wizard - keep as text command (requires sequential input)
            case "setup", "wizard" -> startWizard(context, settings);

            // Text-based settings for complex multi-line inputs
            case "biography", "bio" -> handleBiography(context, settings);
            case "personality" -> handlePersonality(context, settings);
            case "preferences", "prefs" -> handlePreferences(context, settings);
            case "dialoguestyle", "dialogue", "style" -> handleDialogueStyle(context, settings);
            case "quirks" -> handleQuirks(context, settings);
            case "interests", "topics" -> handleTopicsInterest(context, settings);
            case "avoid" -> handleTopicsAvoid(context, settings);
            case "systemprompt", "system" -> handleSystemPrompt(context, settings);

            // Redirect to slash commands for simple settings
            case "on", "enable", "off", "disable", "nickname", "name", "language", "lang",
                 "responselength", "length", "formality", "emotes", "emoji",
                 "agent", "secretary", "maid", "tables", "memory", "memories" ->
                    redirectToSlashCommand(context, action);

            default -> showHelp(context);
        }
    }

    private void redirectToSlashCommand(CommandContext context, String action) {
        String slashCommand = switch (action) {
            case "on", "enable" -> "`/ai enable`";
            case "off", "disable" -> "`/ai disable`";
            case "nickname", "name" -> "`/ai nickname`";
            case "language", "lang" -> "`/ai language`";
            case "responselength", "length" -> "`/ai length`";
            case "formality" -> "`/ai formality`";
            case "emotes", "emoji" -> "`/ai emotes`";
            case "agent", "secretary", "maid" -> "`/ai agent`";
            case "tables" -> "`/ai tables`";
            case "memory", "memories" -> "`/ai memories`";
            default -> "`/ai`";
        };

        context.getChannel().sendMessage(
                "💡 This setting has been migrated to slash commands for better experience.\n" +
                "Use " + slashCommand + " instead!"
        ).queue();
    }

    private void showAIStatus(CommandContext context, GuildSettings settings) {
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
                .addField("Quirks", truncate(settings.getQuirks(), 100, "Not set"), true)
                .addField("Topics of Interest", truncate(settings.getTopicsInterest(), 100, "Not set"), true)
                .addField("Topics to Avoid", truncate(settings.getTopicsAvoid(), 100, "Not set"), true)
                .addField("System Prompt", truncate(settings.getSystemPromptPrefix(), 150, "Default"), false)
                .setFooter("Use !ai setup for wizard | /ai for quick settings");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
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

    private void showHelp(CommandContext context) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🤖 AI Command Help")
                .setColor(new Color(114, 137, 218))
                .setDescription("Configure Pudel's AI personality and behavior.")
                .addField("Setup Wizard", "`!ai setup` - Interactive personality configuration wizard", false)
                .addField("Quick Settings (Slash Commands)", """
                        `/ai enable` / `/ai disable` - Toggle AI
                        `/ai nickname` - Set custom name
                        `/ai language` - Set response language
                        `/ai length` - Set response length
                        `/ai formality` - Set formality level
                        `/ai emotes` - Set emote usage
                        `/ai agent` - View agent info
                        `/ai tables` - View data tables
                        `/ai memories` - View memories
                        """, false)
                .addField("Text Settings (Multi-line)", """
                        `!ai biography <text>` - Set backstory
                        `!ai personality <text>` - Set character traits
                        `!ai preferences <text>` - Set likes/dislikes
                        `!ai dialoguestyle <text>` - Set speech patterns
                        `!ai quirks <text>` - Set speech quirks
                        `!ai interests <topics>` - Topics to engage with
                        `!ai avoid <topics>` - Topics to steer away from
                        `!ai systemprompt <text>` - Custom system prefix
                        """, false)
                .setFooter("Use 'clear' as value to reset any setting");

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    // ==================== WIZARD FUNCTIONALITY ====================

    private void startWizard(CommandContext context, GuildSettings settings) {
        String sessionKey = context.getGuild().getId() + "_" + context.getUser().getId();

        // Cancel any existing wizard
        activeWizards.remove(sessionKey);

        WizardSession session = new WizardSession(
                context.getGuild().getId(),
                context.getUser().getId(),
                context.getChannel().getId(),
                settings
        );
        activeWizards.put(sessionKey, session);

        // Send first step
        sendWizardStep(context, session);

        // Auto-cleanup after 10 minutes
        context.getChannel().sendMessage("").queueAfter(10, TimeUnit.MINUTES,
                msg -> activeWizards.remove(sessionKey),
                error -> {});
    }

    private void sendWizardStep(CommandContext context, WizardSession session) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(114, 137, 218))
                .setFooter("Type 'skip' to keep current | 'cancel' to exit wizard | Step " + (session.currentStep + 1) + "/8");

        switch (session.currentStep) {
            case 0 -> embed.setTitle("📝 Step 1: Nickname")
                    .setDescription("What should I be called in this server?\n\n" +
                            "**Current:** " + (session.settings.getNickname() != null ? session.settings.getNickname() : "Pudel") +
                            "\n\nType a new nickname or 'skip' to keep current:");
            case 1 -> embed.setTitle("📚 Step 2: Biography")
                    .setDescription("What's my backstory?\n\n" +
                            "**Current:** " + truncate(session.settings.getBiography(), 500, "Not set") +
                            "\n\nType my biography (e.g., 'I am the loyal maid of this guild...'):");
            case 2 -> embed.setTitle("✨ Step 3: Personality")
                    .setDescription("What are my character traits?\n\n" +
                            "**Current:** " + truncate(session.settings.getPersonality(), 500, "Not set") +
                            "\n\nType my personality (e.g., 'Cheerful, helpful, with a dry sense of humor'):");
            case 3 -> embed.setTitle("❤️ Step 4: Preferences")
                    .setDescription("What do I like and dislike?\n\n" +
                            "**Current:** " + truncate(session.settings.getPreferences(), 500, "Not set") +
                            "\n\nType my preferences (e.g., 'Likes gaming discussions, dislikes spam'):");
            case 4 -> embed.setTitle("💬 Step 5: Dialogue Style")
                    .setDescription("How should I speak?\n\n" +
                            "**Current:** " + truncate(session.settings.getDialogueStyle(), 500, "Not set") +
                            "\n\nType my dialogue style (e.g., 'Formal Victorian English' or 'Casual modern slang'):");
            case 5 -> embed.setTitle("🌐 Step 6: Language")
                    .setDescription("What language should I respond in?\n\n" +
                            "**Current:** " + (session.settings.getLanguage() != null ? session.settings.getLanguage().toUpperCase() : "EN") +
                            "\n\n**Options:** en, th, ja, ko, zh, de, fr, es, pt, ru, it, nl, auto\n\nType a language code:");
            case 6 -> embed.setTitle("📏 Step 7: Response Length")
                    .setDescription("How detailed should my responses be?\n\n" +
                            "**Current:** " + (session.settings.getResponseLength() != null ? session.settings.getResponseLength() : "medium") +
                            "\n\n**Options:**\n" +
                            "• `short` - Brief 1-2 sentences\n" +
                            "• `medium` - Balanced 2-4 sentences\n" +
                            "• `detailed` - Comprehensive responses\n\nChoose one:");
            case 7 -> embed.setTitle("🎭 Step 8: Formality")
                    .setDescription("How formal should I be?\n\n" +
                            "**Current:** " + (session.settings.getFormality() != null ? session.settings.getFormality() : "balanced") +
                            "\n\n**Options:**\n" +
                            "• `casual` - Friendly, relaxed, uses emojis\n" +
                            "• `balanced` - Mix of friendly and professional\n" +
                            "• `formal` - Professional, polite\n\nChoose one:");
            default -> {
                // Wizard complete
                completeWizard(context, session);
                return;
            }
        }

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void completeWizard(CommandContext context, WizardSession session) {
        String sessionKey = session.guildId + "_" + session.userId;
        activeWizards.remove(sessionKey);

        // Save all settings
        guildInitializationService.updateGuildSettings(session.guildId, session.settings);

        String nickname = session.settings.getNickname() != null ? session.settings.getNickname() : "Pudel";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ AI Setup Complete!")
                .setColor(new Color(67, 181, 129))
                .setDescription(nickname + " has been configured!\n\nUse `!ai` to view all settings or `/ai` for quick adjustments.")
                .addField("Nickname", nickname, true)
                .addField("Language", session.settings.getLanguage() != null ? session.settings.getLanguage().toUpperCase() : "EN", true)
                .addField("Response Length", session.settings.getResponseLength() != null ? session.settings.getResponseLength() : "medium", true);

        context.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || !event.isFromGuild()) return;

        String sessionKey = event.getGuild().getId() + "_" + event.getAuthor().getId();
        WizardSession session = activeWizards.get(sessionKey);

        if (session == null || !session.channelId.equals(event.getChannel().getId())) {
            return;
        }

        String input = event.getMessage().getContentRaw().trim();

        // Handle cancel
        if (input.equalsIgnoreCase("cancel")) {
            activeWizards.remove(sessionKey);
            event.getChannel().sendMessage("❌ Wizard cancelled. No changes were saved.").queue();
            return;
        }

        // Handle skip
        boolean skipped = input.equalsIgnoreCase("skip");

        if (!skipped) {
            // Process the input for current step
            switch (session.currentStep) {
                case 0 -> {
                    if (input.length() > 32) {
                        event.getChannel().sendMessage("❌ Nickname must be 32 characters or less. Try again:").queue();
                        return;
                    }
                    session.settings.setNickname(input);
                }
                case 1 -> session.settings.setBiography(input);
                case 2 -> session.settings.setPersonality(input);
                case 3 -> session.settings.setPreferences(input);
                case 4 -> session.settings.setDialogueStyle(input);
                case 5 -> {
                    String lang = input.toLowerCase();
                    if (!VALID_LANGUAGES.contains(lang)) {
                        event.getChannel().sendMessage("❌ Invalid language code. Use one of: " + String.join(", ", VALID_LANGUAGES)).queue();
                        return;
                    }
                    session.settings.setLanguage(lang);
                }
                case 6 -> {
                    String length = input.toLowerCase();
                    if (!Set.of("short", "medium", "detailed").contains(length)) {
                        event.getChannel().sendMessage("❌ Invalid option. Use: short, medium, or detailed").queue();
                        return;
                    }
                    session.settings.setResponseLength(length);
                }
                case 7 -> {
                    String formality = input.toLowerCase();
                    if (!Set.of("casual", "balanced", "formal").contains(formality)) {
                        event.getChannel().sendMessage("❌ Invalid option. Use: casual, balanced, or formal").queue();
                        return;
                    }
                    session.settings.setFormality(formality);
                }
            }
        }

        // Move to next step
        session.currentStep++;

        // Create a context for sending the next step
        CommandContext mockContext = new CommandContextImpl(event, "ai", new String[0], "");

        sendWizardStep(mockContext, session);
    }

    // ==================== TEXT-BASED SETTING HANDLERS (for multi-line inputs) ====================

    private void handleBiography(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Biography:** " +
                    truncate(settings.getBiography(), 2000, "Not set") +
                    "\n\n*Usage:* `!ai biography <text>` or `!ai biography clear`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setBiography(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Biography cleared!").queue();
            return;
        }

        settings.setBiography(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Biography set!").queue();
    }

    private void handlePersonality(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Personality:** " +
                    truncate(settings.getPersonality(), 2000, "Not set") +
                    "\n\n*Usage:* `!ai personality <text>` or `!ai personality clear`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setPersonality(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Personality cleared!").queue();
            return;
        }

        settings.setPersonality(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Personality set!").queue();
    }

    private void handlePreferences(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Preferences:** " +
                    truncate(settings.getPreferences(), 2000, "Not set") +
                    "\n\n*Usage:* `!ai preferences <text>` or `!ai preferences clear`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setPreferences(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Preferences cleared!").queue();
            return;
        }

        settings.setPreferences(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Preferences set!").queue();
    }

    private void handleDialogueStyle(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Dialogue Style:** " +
                    truncate(settings.getDialogueStyle(), 2000, "Not set") +
                    "\n\n*Usage:* `!ai dialoguestyle <text>` or `!ai dialoguestyle clear`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setDialogueStyle(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Dialogue style cleared!").queue();
            return;
        }

        settings.setDialogueStyle(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Dialogue style set!").queue();
    }

    private void handleQuirks(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Quirks:** " +
                    truncate(settings.getQuirks(), 500, "Not set") +
                    "\n\n*Usage:* `!ai quirks <text>` or `!ai quirks clear`\n" +
                    "*Example:* `!ai quirks Ends sentences with 'nya~', uses cat puns`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setQuirks(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Quirks cleared!").queue();
            return;
        }

        settings.setQuirks(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Quirks set! I'll incorporate these speech patterns.").queue();
    }

    private void handleTopicsInterest(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Topics of Interest:** " +
                    truncate(settings.getTopicsInterest(), 500, "Not set") +
                    "\n\n*Usage:* `!ai interests <topics>` or `!ai interests clear`\n" +
                    "*Example:* `!ai interests gaming, anime, technology, music`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setTopicsInterest(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Topics of interest cleared!").queue();
            return;
        }

        settings.setTopicsInterest(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Topics of interest set! I'll be more engaged on these topics.").queue();
    }

    private void handleTopicsAvoid(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current Topics to Avoid:** " +
                    truncate(settings.getTopicsAvoid(), 500, "Not set") +
                    "\n\n*Usage:* `!ai avoid <topics>` or `!ai avoid clear`\n" +
                    "*Example:* `!ai avoid politics, religion, controversial topics`").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear")) {
            settings.setTopicsAvoid(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ Topics to avoid cleared!").queue();
            return;
        }

        settings.setTopicsAvoid(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ Topics to avoid set! I'll steer away from these topics.").queue();
    }

    private void handleSystemPrompt(CommandContext context, GuildSettings settings) {
        if (context.getArgs().length < 2) {
            context.getChannel().sendMessage("**Current System Prompt Prefix:** " +
                    truncate(settings.getSystemPromptPrefix(), 500, "Using default system prompt") +
                    "\n\n*Usage:* `!ai systemprompt <text>` or `!ai systemprompt clear`\n" +
                    "*This is prepended to the LLM system prompt for advanced customization.*").queue();
            return;
        }

        String text = String.join(" ", Arrays.copyOfRange(context.getArgs(), 1, context.getArgs().length));

        if (text.equalsIgnoreCase("clear") || text.equalsIgnoreCase("reset")) {
            settings.setSystemPromptPrefix(null);
            guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
            context.getChannel().sendMessage("✅ System prompt prefix cleared! Using default.").queue();
            return;
        }

        settings.setSystemPromptPrefix(text);
        guildInitializationService.updateGuildSettings(context.getGuild().getId(), settings);
        context.getChannel().sendMessage("✅ System prompt prefix set!").queue();
    }


    // ==================== WIZARD SESSION CLASS ====================

    private static class WizardSession {
        final String guildId;
        final String userId;
        final String channelId;
        final GuildSettings settings;
        int currentStep = 0;

        WizardSession(String guildId, String userId, String channelId, GuildSettings settings) {
            this.guildId = guildId;
            this.userId = userId;
            this.channelId = channelId;
            this.settings = settings;
        }
    }
}
