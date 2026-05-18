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
package group.worldstandard.pudel.core.brain.personality;

/**
 * Pudel's personality configuration.
 * <p>
 * Defines all the personality traits, preferences, and behavioral settings
 * that control how Pudel responds in conversations.
 *
 * @param nickname       the bot's nickname in this context
 * @param biography      background information about the bot
 * @param personality    core personality traits description
 * @param preferences    behavioral preferences
 * @param dialogueStyle  how the bot should speak
 * @param language       preferred language code (e.g., "en", "es")
 * @param responseLength preferred response length: "short", "medium", "long"
 * @param formality      formality level: "casual", "moderate", "formal"
 * @param emoteUsage     emote/emoji usage: "none", "minimal", "moderate", "frequent"
 * @param quirks         speech quirks or special behaviors
 * @param topicsInterest topics the bot is interested in
 * @param topicsAvoid    topics the bot should avoid
 */
public record PudelPersonality(
        String nickname,
        String biography,
        String personality,
        String preferences,
        String dialogueStyle,
        String language,
        String responseLength,
        String formality,
        String emoteUsage,
        String quirks,
        String topicsInterest,
        String topicsAvoid
) {}

