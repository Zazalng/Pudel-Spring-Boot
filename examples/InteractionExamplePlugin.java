/*
 * Pudel - A Moderate Discord Chat Bot
 * Example Plugin: Slash Commands & Interactions
 *
 * This example demonstrates how to create slash commands, buttons,
 * modals, and other Discord interactions using Pudel's Plugin API.
 *
 * Note: This is an EXAMPLE file for reference.
 */
package com.example.interactionplugin;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import worldstandard.group.pudel.api.PluginContext;
import worldstandard.group.pudel.api.PluginInfo;
import worldstandard.group.pudel.api.SimplePlugin;

import java.awt.Color;

/**
 * Example plugin demonstrating Discord interactions.
 */
public class InteractionExamplePlugin extends SimplePlugin {

    @Override
    public PluginInfo getPluginInfo() {
        return PluginInfo.builder()
                .name("InteractionExample")
                .version("1.0.0")
                .author("Pudel Team")
                .description("Example plugin demonstrating slash commands and interactions")
                .build();
    }

    @Override
    public void onEnable(PluginContext context) {
        InteractionManager manager = context.getInteractionManager();

        // Register slash commands
        manager.registerSlashCommand("example", new PingSlashCommand());
        manager.registerSlashCommand("example", new GreetSlashCommand());
        manager.registerSlashCommand("example", new FeedbackSlashCommand());

        // Register button handlers
        manager.registerButtonHandler("example", new ExampleButtonHandler());

        // Register modal handlers
        manager.registerModalHandler("example", new FeedbackModalHandler());

        // Sync commands to Discord
        manager.syncCommands().thenRun(() ->
            context.log("info", "Slash commands synced to Discord")
        );

        context.log("info", "Interaction Example Plugin enabled!");
    }

    @Override
    public void onDisable(PluginContext context) {
        context.getInteractionManager().unregisterAll("example");
        context.log("info", "Interaction Example Plugin disabled");
    }

    // =========================================================
    // Slash Command: /ping
    // =========================================================

    public static class PingSlashCommand implements SlashCommandHandler {
        @Override
        public SlashCommandData getCommandData() {
            return Commands.slash("ping", "Check bot latency and response time");
        }

        @Override
        public void handle(SlashCommandInteractionEvent event) {
            long gatewayPing = event.getJDA().getGatewayPing();

            event.reply("🏓 Pong!")
                    .addEmbeds(new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle("Latency Check")
                            .addField("Gateway Ping", gatewayPing + "ms", true)
                            .addField("API Latency", "Measuring...", true)
                            .build())
                    .setEphemeral(false)
                    .queue(hook -> {
                        long apiLatency = System.currentTimeMillis() - event.getTimeCreated().toInstant().toEpochMilli();
                        hook.editOriginalEmbeds(new EmbedBuilder()
                                .setColor(Color.GREEN)
                                .setTitle("Latency Check")
                                .addField("Gateway Ping", gatewayPing + "ms", true)
                                .addField("API Latency", apiLatency + "ms", true)
                                .build()).queue();
                    });
        }
    }

    // =========================================================
    // Slash Command: /greet (with options)
    // =========================================================

    public static class GreetSlashCommand implements SlashCommandHandler {
        @Override
        public SlashCommandData getCommandData() {
            return Commands.slash("greet", "Send a greeting to someone")
                    .addOption(OptionType.USER, "user", "The user to greet", true)
                    .addOption(OptionType.STRING, "message", "Custom greeting message", false);
        }

        @Override
        public void handle(SlashCommandInteractionEvent event) {
            var targetUser = event.getOption("user").getAsUser();
            var customMessage = event.getOption("message");

            String greeting = customMessage != null
                    ? customMessage.getAsString()
                    : "Hello, " + targetUser.getAsMention() + "! 👋";

            // Reply with buttons for interaction
            event.reply(greeting)
                    .addActionRow(
                            Button.primary("example:wave:" + targetUser.getId(), "👋 Wave Back"),
                            Button.secondary("example:thanks", "🙏 Say Thanks")
                    )
                    .queue();
        }
    }

    // =========================================================
    // Slash Command: /feedback (opens a modal)
    // =========================================================

    public static class FeedbackSlashCommand implements SlashCommandHandler {
        @Override
        public SlashCommandData getCommandData() {
            return Commands.slash("feedback", "Submit feedback about the bot");
        }

        @Override
        public void handle(SlashCommandInteractionEvent event) {
            // Create and show a modal
            Modal modal = Modal.create("example:feedback", "Submit Feedback")
                    .addActionRow(
                            TextInput.create("title", "Feedback Title", TextInputStyle.SHORT)
                                    .setPlaceholder("Brief summary of your feedback")
                                    .setRequired(true)
                                    .setMinLength(5)
                                    .setMaxLength(100)
                                    .build()
                    )
                    .addActionRow(
                            TextInput.create("description", "Description", TextInputStyle.PARAGRAPH)
                                    .setPlaceholder("Detailed feedback, suggestions, or bug reports...")
                                    .setRequired(true)
                                    .setMinLength(20)
                                    .setMaxLength(2000)
                                    .build()
                    )
                    .build();

            event.replyModal(modal).queue();
        }
    }

    // =========================================================
    // Button Handler
    // =========================================================

    public static class ExampleButtonHandler implements ButtonHandler {
        @Override
        public String getButtonIdPrefix() {
            return "example:";
        }

        @Override
        public void handle(ButtonInteractionEvent event) {
            String buttonId = event.getComponentId();

            if (buttonId.startsWith("example:wave:")) {
                String targetUserId = buttonId.substring("example:wave:".length());
                event.reply("👋 " + event.getUser().getAsMention() + " waved back at <@" + targetUserId + ">!")
                        .queue();
            } else if (buttonId.equals("example:thanks")) {
                event.reply("You're welcome! 😊")
                        .setEphemeral(true)
                        .queue();
            } else {
                event.reply("Unknown button: " + buttonId)
                        .setEphemeral(true)
                        .queue();
            }
        }
    }

    // =========================================================
    // Modal Handler
    // =========================================================

    public static class FeedbackModalHandler implements ModalHandler {
        @Override
        public String getModalIdPrefix() {
            return "example:feedback";
        }

        @Override
        public void handle(ModalInteractionEvent event) {
            String title = event.getValue("title").getAsString();
            String description = event.getValue("description").getAsString();

            // Process the feedback (in real plugin, save to database)
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.BLUE)
                    .setTitle("📝 Feedback Received")
                    .addField("Title", title, false)
                    .addField("Description", description, false)
                    .addField("From", event.getUser().getAsTag(), true)
                    .setTimestamp(java.time.Instant.now());

            event.reply("Thank you for your feedback!")
                    .addEmbeds(embed.build())
                    .setEphemeral(true)
                    .queue();

            // Could also send to a feedback channel
            // event.getGuild().getTextChannelById(FEEDBACK_CHANNEL_ID)
            //     .sendMessageEmbeds(embed.build()).queue();
        }
    }
}
