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
package group.worldstandard.pudel.core.config.discord;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import group.worldstandard.pudel.api.PudelProperties;
import jakarta.xml.bind.DatatypeConverter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import group.worldstandard.pudel.core.discord.DiscordEventListener;
import group.worldstandard.pudel.core.discord.GuildEventListener;
import group.worldstandard.pudel.core.interaction.InteractionEventListener;

import java.nio.charset.StandardCharsets;

/**
 * Configuration for JDA Discord bot with DAVE support.
 *
 * <p><b>DAVE Protocol:</b> Starting March 1st, 2026, Discord requires all voice
 * connections to use End-to-End Encryption via the DAVE protocol. JDA supports
 * this through the AudioModuleConfig interface.</p>
 *
 * @see <a href="https://discord.com/developers/docs/topics/voice-connections#dave-protocol">DAVE Protocol</a>
 */
@Configuration
public class JDAConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(JDAConfiguration.class);

    @Value("${pudel.audio.enabled:true}")
    private boolean audioEnabled;

    @Bean
    public JDA jda(DiscordBotProperties properties,
                   DiscordEventListener eventListener,
                   InteractionEventListener interactionEventListener,
                   GuildEventListener guildEventListener,
                   PudelProperties pudelProperties) {
        try {
            String token = properties.getToken();
            if (token == null || token.trim().isEmpty()) {
                logger.error("Discord bot token not configured. Set pudel.discord.token in application.yml");
                throw new IllegalStateException("Discord bot token not configured");
            }

            logger.info("Initializing JDA with token: ");

            JDABuilder builder = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.DIRECT_MESSAGES,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGE_REACTIONS,
                            GatewayIntent.GUILD_VOICE_STATES
                    )
                    .addEventListeners(
                            eventListener,
                            interactionEventListener,
                            guildEventListener
                    )
                    .setRestConfig(
                            new RestConfig().setUserAgentSuffix(
                                    "/ " + pudelProperties.getUserAgent()
                            )
                    );

            // Configure audio settings
            if (audioEnabled) {
                logger.info("Audio support enabled - initializing DAVE protocol");
                configureDAVE(builder);
            } else {
                logger.info("Audio support disabled - voice connections will not be available");
                // Note: In JDA 6, audio is always enabled at the JDA level
                // Individual voice connections can be refused by the application
            }

            JDA jda = builder.build().awaitReady();

            logger.info("JDA initialized successfully");
            logger.info("Bot name: {}", jda.getSelfUser().getName());
            logger.info("Connected to {} guilds", jda.getGuilds().size());

            return jda;
        } catch (Exception e) {
            logger.error("Failed to initialize JDA: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize JDA", e);
        }
    }

    /**
     * Configure DAVE with JDAVE for voice encryption.
     * This is called during JDA initialization to set up voice encryption.
     */
    private void configureDAVE(JDABuilder builder) {
        try {
            // Check Java version first
            int javaVersion = Runtime.version().feature();
            if (javaVersion < 25) {
                logger.error("JDAVE requires Java 25+, running Java {}. Voice connections will fail after March 1st, 2026!", javaVersion);
                return;
            }

            // Configure JDAVE directly - no reflection needed since it's a required dependency
            JDaveSessionFactory sessionFactory = new JDaveSessionFactory();
            AudioModuleConfig audioConfig = new AudioModuleConfig()
                    .withDaveSessionFactory(sessionFactory);

            builder.setAudioModuleConfig(audioConfig);

            logger.info("DAVE configured successfully with JDAVE - voice encryption enabled");
        } catch (UnsatisfiedLinkError e) {
            logger.error("JDAVE native library not found for this platform: {}", e.getMessage());
            logger.error("Ensure jdave-native for your platform is in the classpath");
        } catch (Exception e) {
            logger.error("Failed to configure DAVE: {} - voice encryption will not be available", e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("DAVE configuration error details", e);
            }
        }
    }
}

