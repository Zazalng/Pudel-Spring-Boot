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
package group.worldstandard.pudel.core.config.brain;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for chatbot embedding behavior.
 * Controls vector embedding settings for semantic memory search.
 */
@ConfigurationProperties(prefix = "pudel.chatbot")
@Component
public class ChatbotConfig {
    private Embedding embedding = new Embedding();

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
    }

    /**
     * Embedding configuration for semantic memory search.
     */
    public static class Embedding {
        private boolean enabled = true;
        private int dimension = 384;
        private int ivfProbes = 10;
        private int ivfLists = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDimension() {
            return dimension;
        }

        public void setDimension(int dimension) {
            this.dimension = dimension;
        }

        public int getIvfProbes() {
            return ivfProbes;
        }

        public void setIvfProbes(int ivfProbes) {
            this.ivfProbes = ivfProbes;
        }

        public int getIvfLists() {
            return ivfLists;
        }

        public void setIvfLists(int ivfLists) {
            this.ivfLists = ivfLists;
        }
    }
}