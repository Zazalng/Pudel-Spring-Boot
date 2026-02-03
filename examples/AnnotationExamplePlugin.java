package com.example;

import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.annotation.*;
import group.worldstandard.pudel.api.command.CommandContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;

/**
 * Example plugin demonstrating the annotation-based plugin system.
 * <p>
 * This is similar to Spring Boot's controller pattern:
 * - @Plugin is like @Controller/@Service
 * - @SlashCommand is like @GetMapping/@PostMapping
 * - @TextCommand is like @RequestMapping
 * <p>
 * The core handles ALL registration, sync, and cleanup automatically.
 * No need to call registerSlashCommand() or syncCommands() yourself!
 */
@Plugin(
    name = "ExamplePlugin",
    version = "1.0.0",
    author = "Pudel Team",
    description = "Demonstrates annotation-based plugin development"
)
public class AnnotationExamplePlugin {

    // =====================================================
    // Slash Commands - Registered automatically!
    // =====================================================

    /**
     * Simple slash command with no options.
     * The core will:
     * 1. Register this command when plugin is enabled
     * 2. Sync to Discord (instant for guild commands)
     * 3. Unregister and re-sync when plugin is disabled
     */
    @SlashCommand(name = "ping", description = "Check bot latency")
    public void pingCommand(SlashCommandInteractionEvent event) {
        long ping = event.getJDA().getGatewayPing();
        event.reply("🏓 Pong! " + ping + "ms").queue();
    }

    /**
     * Slash command with options.
     */
    @SlashCommand(
        name = "greet",
        description = "Greet someone",
        options = {
            @CommandOption(name = "user", description = "User to greet", type = "USER", required = true),
            @CommandOption(name = "message", description = "Custom message", type = "STRING", required = false)
        }
    )
    public void greetCommand(SlashCommandInteractionEvent event) {
        var user = event.getOption("user").getAsUser();
        var message = event.getOption("message");
        String greeting = message != null ? message.getAsString() : "Hello!";

        event.reply(greeting + " " + user.getAsMention()).queue();
    }

    /**
     * Slash command with subcommands (like /settings view, /settings prefix).
     */
    @SlashCommand(
        name = "config",
        description = "Plugin configuration",
        permissions = {"ADMINISTRATOR"},
        subcommands = {
            @Subcommand(name = "view", description = "View current settings"),
            @Subcommand(
                name = "set",
                description = "Change a setting",
                options = {
                    @CommandOption(name = "key", description = "Setting name", required = true),
                    @CommandOption(name = "value", description = "New value", required = true)
                }
            ),
            @Subcommand(name = "reset", description = "Reset to defaults")
        }
    )
    public void configCommand(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();

        switch (subcommand) {
            case "view" -> {
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("⚙️ Plugin Settings")
                    .setColor(Color.BLUE)
                    .addField("Feature A", "Enabled", true)
                    .addField("Feature B", "Disabled", true);
                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            }
            case "set" -> {
                String key = event.getOption("key").getAsString();
                String value = event.getOption("value").getAsString();
                event.reply("✅ Set `" + key + "` = `" + value + "`").queue();
            }
            case "reset" -> {
                event.reply("✅ Settings reset to defaults").queue();
            }
        }
    }

    /**
     * Slash command with choices.
     */
    @SlashCommand(
        name = "language",
        description = "Set response language",
        options = {
            @CommandOption(
                name = "lang",
                description = "Language to use",
                required = true,
                choices = {
                    @Choice(name = "English", value = "en"),
                    @Choice(name = "Thai (ภาษาไทย)", value = "th"),
                    @Choice(name = "Japanese (日本語)", value = "ja")
                }
            )
        }
    )
    public void languageCommand(SlashCommandInteractionEvent event) {
        String lang = event.getOption("lang").getAsString();
        event.reply("✅ Language set to: " + lang).setEphemeral(true).queue();
    }

    // =====================================================
    // Text Commands - Also registered automatically!
    // =====================================================

    /**
     * Simple text command (!hello).
     */
    @TextCommand(value = "hello", description = "Say hello")
    public void helloCommand(CommandContext ctx) {
        ctx.reply("Hello, " + ctx.getUser().getName() + "! 👋");
    }

    /**
     * Text command with aliases (!help, !h, !?).
     */
    @TextCommand(value = "help", aliases = {"h", "?"}, description = "Show help")
    public void helpCommand(CommandContext ctx) {
        ctx.reply("**Available Commands:**\n" +
                  "• `!hello` - Say hello\n" +
                  "• `!help` - Show this help\n" +
                  "• `/ping` - Check latency\n" +
                  "• `/greet` - Greet someone");
    }

    // =====================================================
    // Button Handlers - Also automatic!
    // =====================================================

    /**
     * Handle buttons with prefix "example:confirm".
     */
    @ButtonHandler("example:confirm")
    public void handleConfirmButton(ButtonInteractionEvent event) {
        event.reply("✅ Confirmed!").setEphemeral(true).queue();
    }

    @ButtonHandler("example:cancel")
    public void handleCancelButton(ButtonInteractionEvent event) {
        event.reply("❌ Cancelled").setEphemeral(true).queue();
    }

    /**
     * Example command that sends buttons.
     */
    @SlashCommand(name = "ask", description = "Ask for confirmation")
    public void askCommand(SlashCommandInteractionEvent event) {
        event.reply("Do you want to proceed?")
            .addActionRow(
                Button.success("example:confirm", "Yes"),
                Button.danger("example:cancel", "No")
            )
            .queue();
    }

    // =====================================================
    // Lifecycle Hooks - Optional
    // =====================================================

    /**
     * Called when plugin is enabled.
     * Use for any custom initialization.
     */
    @OnEnable
    public void onEnable(PluginContext context) {
        context.log("info", "ExamplePlugin enabled!");
        // Any custom setup here
    }

    /**
     * Called when plugin is disabled.
     * Use for any custom cleanup.
     */
    @OnDisable
    public void onDisable(PluginContext context) {
        context.log("info", "ExamplePlugin disabled!");
        // Any custom cleanup here
    }
}
