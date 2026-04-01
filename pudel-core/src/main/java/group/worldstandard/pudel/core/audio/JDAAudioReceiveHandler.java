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
package group.worldstandard.pudel.core.audio;

import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.audio.UserAudio;
import group.worldstandard.pudel.api.audio.AudioReceiver;
import net.dv8tion.jda.api.entities.User;
import org.jspecify.annotations.NonNull;

/**
 * Adapter that wraps a plugin's AudioReceiver for JDA's AudioReceiveHandler interface.
 */
public class JDAAudioReceiveHandler implements AudioReceiveHandler {

    private final AudioReceiver receiver;

    public JDAAudioReceiveHandler(AudioReceiver receiver) {
        this.receiver = receiver;
    }

    @Override
    public boolean canReceiveCombined() {
        return true;
    }

    @Override
    public boolean canReceiveUser() {
        return true;
    }

    @Override
    public void handleCombinedAudio(@NonNull CombinedAudio combinedAudio) {
        byte[] audioData;
        audioData = combinedAudio.getAudioData(1.0);
        receiver.handleCombinedAudio(audioData);
    }

    @Override
    public void handleUserAudio(UserAudio userAudio) {
        long userId = userAudio.getUser().getIdLong();
        byte[] audioData;
        audioData = userAudio.getAudioData(1.0);
        receiver.handleAudio(userId, audioData);
    }

    @Override
    public boolean includeUserInCombinedAudio(User user) {
        return true;
    }
}

