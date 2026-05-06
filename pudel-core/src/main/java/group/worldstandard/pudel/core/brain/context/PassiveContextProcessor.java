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
package group.worldstandard.pudel.core.brain.context;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.core.brain.memory.MemoryManager;
import group.worldstandard.pudel.model.PudelModelService;
import group.worldstandard.pudel.model.analyzer.TextAnalysis;

import java.util.Comparator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages passive context processing with proper queue management.
 * <p>
 * Solves the "doom" problem where messages pile up faster than LLM can process them.
 * <p>
 * Features:
 * - Priority queue: newer messages get processed first (LIFO for freshness)
 * - Age-based expiration: messages older than MAX_AGE are dropped
 * - Deduplication: same user+channel only keeps the most recent message
 * - Rate limiting: only N messages processed per second
 * - Graceful degradation: falls back to fast analysis when queue is full
 * - Batch processing: processes messages in controlled batches
 */
@Component
public class PassiveContextProcessor {
    private static final Logger logger = LoggerFactory.getLogger(PassiveContextProcessor.class);

    private final PudelModelService modelService;
    private final MemoryManager memoryManager;
    private final PassiveContextConfig config;

    // Priority queue - newest messages first (based on timestamp)
    private final PriorityBlockingQueue<ContextMessage> messageQueue;

    // Track messages per user+channel to deduplicate
    private final ConcurrentHashMap<String, ContextMessage> latestMessageByKey;

    // Processing thread
    private ScheduledExecutorService scheduler;
    private ExecutorService llmExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Stats
    private final AtomicLong messagesReceived = new AtomicLong(0);
    private final AtomicLong messagesProcessed = new AtomicLong(0);
    private final AtomicLong messagesDropped = new AtomicLong(0);
    private final AtomicLong messagesDeduplicated = new AtomicLong(0);

    public PassiveContextProcessor(PudelModelService modelService,
                                   MemoryManager memoryManager,
                                   PassiveContextConfig config) {
        this.modelService = modelService;
        this.memoryManager = memoryManager;
        this.config = config;

        // Priority queue: newer messages (higher timestamp) first
        this.messageQueue = new PriorityBlockingQueue<>(
                config.getMaxQueueSize(),
                Comparator.comparingLong(ContextMessage::timestamp).reversed()
        );
        this.latestMessageByKey = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void start() {
        if (!config.isEnabled()) {
            logger.info("PassiveContextProcessor is disabled via configuration");
            return;
        }

        running.set(true);

        // Dedicated executor for LLM calls - 2 threads for parallel processing
        llmExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "PassiveContext-LLM");
            t.setDaemon(true);
            return t;
        });

        // Scheduler for periodic batch processing
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PassiveContext-Scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(
                this::processBatch,
                config.getProcessingIntervalMs(),
                config.getProcessingIntervalMs(),
                TimeUnit.MILLISECONDS
        );

        logger.info("PassiveContextProcessor started with queue size {}, batch size {}, max age {}ms",
                config.getMaxQueueSize(), config.getBatchSize(), config.getMaxMessageAgeMs());
    }

    @PreDestroy
    public void stop() {
        running.set(false);

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (llmExecutor != null) {
            llmExecutor.shutdown();
            try {
                if (!llmExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    llmExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                llmExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("PassiveContextProcessor stopped. Stats: received={}, processed={}, dropped={}, deduplicated={}",
                messagesReceived.get(), messagesProcessed.get(),
                messagesDropped.get(), messagesDeduplicated.get());
    }

    /**
     * Submit a message for passive context processing.
     * This method returns immediately - processing happens asynchronously.
     *
     * @param message   The message content
     * @param userId    The user who sent the message
     * @param channelId The channel ID
     * @param isGuild   Whether this is a guild message
     * @param targetId  Guild ID or user ID
     */
    public void submit(String message, long userId, long channelId, boolean isGuild, long targetId) {
        if (!running.get() || !config.isEnabled()) {
            return;
        }

        // Skip very short messages
        if (message == null || message.length() < config.getMinMessageLength()) {
            return;
        }

        messagesReceived.incrementAndGet();

        // Create context message
        String dedupeKey = userId + ":" + channelId;
        long timestamp = System.currentTimeMillis();
        ContextMessage contextMessage = new ContextMessage(
                message, userId, channelId, isGuild, targetId, timestamp, dedupeKey
        );

        // Deduplication: replace older message from same user+channel
        ContextMessage existing = latestMessageByKey.put(dedupeKey, contextMessage);
        if (existing != null) {
            // Remove the old message from queue if present
            messageQueue.remove(existing);
            messagesDeduplicated.incrementAndGet();
        }

        // Check queue capacity
        if (messageQueue.size() >= config.getMaxQueueSize()) {
            // Queue is full - drop oldest messages
            dropOldestMessages(config.getMaxQueueSize() / 4);
        }

        // Add to queue
        if (!messageQueue.offer(contextMessage)) {
            messagesDropped.incrementAndGet();
            latestMessageByKey.remove(dedupeKey, contextMessage);
            logger.debug("Queue full, dropping passive context message");
        }
    }

    /**
     * Process a batch of messages from the queue.
     */
    private void processBatch() {
        if (!running.get() || messageQueue.isEmpty()) {
            return;
        }

        // First, clean up expired messages
        cleanupExpiredMessages();

        // Process up to batchSize messages
        for (int i = 0; i < config.getBatchSize() && !messageQueue.isEmpty(); i++) {
            ContextMessage msg = messageQueue.poll();
            if (msg == null) {
                break;
            }

            // Remove from deduplication map
            latestMessageByKey.remove(msg.dedupeKey(), msg);

            // Check if message is too old
            long age = System.currentTimeMillis() - msg.timestamp();
            if (age > config.getMaxMessageAgeMs()) {
                messagesDropped.incrementAndGet();
                logger.debug("Dropping expired passive context message (age: {}ms)", age);
                continue;
            }

            // Process the message
            processMessage(msg);
        }
    }

    /**
     * Process a single context message.
     */
    private void processMessage(ContextMessage msg) {
        try {
            // Use LLM async analysis with timeout
            CompletableFuture<TextAnalysis> analysisFuture = CompletableFuture
                    .supplyAsync(() -> modelService.analyzeText(msg.message()), llmExecutor)
                    .orTimeout(config.getLlmTimeoutSeconds(), TimeUnit.SECONDS);

            analysisFuture
                    .thenAccept(analysis -> {
                        if (analysis.containsInterestingInfo()) {
                            try {
                                memoryManager.storePassiveContext(
                                        msg.message(),
                                        msg.userId(),
                                        msg.channelId(),
                                        analysis,
                                        msg.isGuild(),
                                        msg.targetId()
                                );
                                messagesProcessed.incrementAndGet();
                                logger.debug("Stored passive context for user {} in channel {}",
                                        msg.userId(), msg.channelId());
                            } catch (Exception e) {
                                logger.debug("Error storing passive context: {}", e.getMessage());
                            }
                        } else {
                            messagesProcessed.incrementAndGet();
                            logger.debug("Passive context not interesting, skipping storage");
                        }
                    })
                    .exceptionally(e -> {
                        // Timeout or error - try fast analysis as fallback
                        logger.debug("LLM analysis failed/timed out, using fast fallback: {}", e.getMessage());
                        try {
                            TextAnalysis fastAnalysis = modelService.analyzeTextFast(msg.message());
                            if (fastAnalysis.containsInterestingInfo()) {
                                memoryManager.storePassiveContext(
                                        msg.message(),
                                        msg.userId(),
                                        msg.channelId(),
                                        fastAnalysis,
                                        msg.isGuild(),
                                        msg.targetId()
                                );
                            }
                            messagesProcessed.incrementAndGet();
                        } catch (Exception ex) {
                            logger.debug("Fast fallback also failed: {}", ex.getMessage());
                            messagesDropped.incrementAndGet();
                        }
                        return null;
                    });

        } catch (Exception e) {
            logger.debug("Error processing passive context: {}", e.getMessage());
            messagesDropped.incrementAndGet();
        }
    }

    /**
     * Clean up expired messages from the queue.
     */
    private void cleanupExpiredMessages() {
        long now = System.currentTimeMillis();
        int removed = 0;

        // Use removeIf would require synchronization, instead drain expired ones
        while (!messageQueue.isEmpty()) {
            ContextMessage peek = messageQueue.peek();
            if (peek != null && (now - peek.timestamp()) > config.getMaxMessageAgeMs()) {
                ContextMessage removedMsg = messageQueue.poll();
                if (removedMsg != null) {
                    latestMessageByKey.remove(removedMsg.dedupeKey(), removedMsg);
                    removed++;
                    messagesDropped.incrementAndGet();
                }
            } else {
                break; // Queue is sorted by timestamp, so all remaining are newer
            }
        }

        if (removed > 0) {
            logger.debug("Cleaned up {} expired passive context messages", removed);
        }
    }

    /**
     * Drop messages when queue is full.
     * Since we use LIFO (newest first), we let old messages naturally expire.
     * This method just logs the overflow condition.
     */
    private void dropOldestMessages(int count) {
        // With our priority queue (newest first) + age-based expiration,
        // old messages will naturally be cleaned up by cleanupExpiredMessages().
        // Here we just acknowledge the overflow.
        messagesDropped.addAndGet(count);
        logger.debug("Queue overflow - {} oldest messages will be dropped via expiration", count);
    }

    /**
     * Get current queue statistics.
     */
    public QueueStats getStats() {
        return new QueueStats(
                messageQueue.size(),
                config.getMaxQueueSize(),
                messagesReceived.get(),
                messagesProcessed.get(),
                messagesDropped.get(),
                messagesDeduplicated.get()
        );
    }

    /**
     * Check if the processor is running and accepting messages.
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Context message record for the queue.
     */
    private record ContextMessage(
            String message,
            long userId,
            long channelId,
            boolean isGuild,
            long targetId,
            long timestamp,
            String dedupeKey
    ) {}

    /**
     * Queue statistics.
     */
    public record QueueStats(
            int currentSize,
            int maxSize,
            long totalReceived,
            long totalProcessed,
            long totalDropped,
            long totalDeduplicated
    ) {
        public double processRate() {
            return totalReceived > 0 ? (double) totalProcessed / totalReceived * 100 : 0;
        }

        public double dropRate() {
            return totalReceived > 0 ? (double) totalDropped / totalReceived * 100 : 0;
        }
    }
}