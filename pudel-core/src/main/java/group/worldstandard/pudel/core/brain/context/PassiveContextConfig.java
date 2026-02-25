/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard.group
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
package group.worldstandard.pudel.core.brain.context;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for passive context processing.
 * Controls queue management to prevent message overload.
 */
@Component
@ConfigurationProperties(prefix = "pudel.chatbot.passive-tracking")
public class PassiveContextConfig {

    /**
     * Whether passive context tracking is enabled.
     */
    private boolean enabled = true;

    /**
     * Maximum number of messages in the processing queue.
     * When full, oldest messages are dropped.
     */
    private int maxQueueSize = 100;

    /**
     * Maximum age of a message (in milliseconds) before it's dropped.
     * Default: 60 seconds.
     */
    private long maxMessageAgeMs = 60_000;

    /**
     * Interval between processing batches (in milliseconds).
     * Default: 500ms.
     */
    private long processingIntervalMs = 500;

    /**
     * Number of messages to process per batch.
     * Higher = faster throughput but more LLM load.
     */
    private int batchSize = 3;

    /**
     * Timeout for LLM analysis per message (in seconds).
     * If exceeded, falls back to fast pattern-based analysis.
     */
    private int llmTimeoutSeconds = 30;

    /**
     * Minimum message length to track.
     * Shorter messages are ignored.
     */
    private int minMessageLength = 5;

    // Getters and setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public long getMaxMessageAgeMs() {
        return maxMessageAgeMs;
    }

    public void setMaxMessageAgeMs(long maxMessageAgeMs) {
        this.maxMessageAgeMs = maxMessageAgeMs;
    }

    public long getProcessingIntervalMs() {
        return processingIntervalMs;
    }

    public void setProcessingIntervalMs(long processingIntervalMs) {
        this.processingIntervalMs = processingIntervalMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getLlmTimeoutSeconds() {
        return llmTimeoutSeconds;
    }

    public void setLlmTimeoutSeconds(int llmTimeoutSeconds) {
        this.llmTimeoutSeconds = llmTimeoutSeconds;
    }

    public int getMinMessageLength() {
        return minMessageLength;
    }

    public void setMinMessageLength(int minMessageLength) {
        this.minMessageLength = minMessageLength;
    }
}
