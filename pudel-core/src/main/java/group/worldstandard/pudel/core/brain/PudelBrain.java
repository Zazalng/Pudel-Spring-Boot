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
package group.worldstandard.pudel.core.brain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.core.brain.context.PassiveContextProcessor;
import group.worldstandard.pudel.core.brain.memory.MemoryManager;
import group.worldstandard.pudel.core.brain.personality.PersonalityEngine;
import group.worldstandard.pudel.core.brain.response.ResponseGenerator;
import group.worldstandard.pudel.core.service.ChatbotService.PudelPersonality;
import group.worldstandard.pudel.model.PudelModelService;
import group.worldstandard.pudel.model.analyzer.TextAnalysis;

import java.util.List;
import java.util.Map;

/**
 * Pudel's Brain - The central intelligence component.
 * <p>
 * This component orchestrates:
 * - Text analysis (intent detection, entity extraction, language detection) via LangChain4j
 * - Memory management (context retrieval, storage with capacity limits)
 * - Personality application (biography, personality traits, dialogue style)
 * - Response generation (LLM + fallback template-based responses)
 * <p>
 * Uses LangChain4j + Ollama for intelligent analysis when available,
 * with pattern-based fallback for offline operation.
 * Memory capacity is controlled by subscription tiers.
 */
@Component
public class PudelBrain {

    private static final Logger logger = LoggerFactory.getLogger(PudelBrain.class);

    private final PudelModelService modelService;
    private final MemoryManager memoryManager;
    private final PersonalityEngine personalityEngine;
    private final ResponseGenerator responseGenerator;
    private final PassiveContextProcessor passiveContextProcessor;

    public PudelBrain(PudelModelService modelService,
                      MemoryManager memoryManager,
                      PersonalityEngine personalityEngine,
                      ResponseGenerator responseGenerator,
                      PassiveContextProcessor passiveContextProcessor) {
        this.modelService = modelService;
        this.memoryManager = memoryManager;
        this.personalityEngine = personalityEngine;
        this.responseGenerator = responseGenerator;
        this.passiveContextProcessor = passiveContextProcessor;
        logger.info("Pudel Brain initialized with LangChain4j text analyzer and PassiveContextProcessor");
    }

    /**
     * Process a message and generate an intelligent response.
     *
     * @param userMessage the user's message
     * @param context the conversation context (history, personality, etc.)
     * @param isGuild whether this is a guild message
     * @param targetId guild ID or user ID
     * @return the generated response
     */
    public BrainResponse processMessage(String userMessage,
                                         ConversationContext context,
                                         boolean isGuild,
                                         long targetId) {
        try {
            // Step 1: Analyze message with LangChain4j TextAnalyzer
            TextAnalysis analysis = modelService.analyzeText(userMessage);
            logger.debug("Text Analysis: intent={}, sentiment={}, language={}",
                    analysis.intent(), analysis.sentiment(), analysis.language());

            // Step 2: Retrieve relevant memories based on context
            List<MemoryManager.MemoryEntry> relevantMemories =
                    memoryManager.retrieveRelevantMemories(userMessage, isGuild, targetId);
            logger.debug("Retrieved {} relevant memories for {} {}",
                    relevantMemories.size(), isGuild ? "guild" : "user", targetId);

            // Log conversation history size for debugging
            logger.debug("Conversation history contains {} entries", context.history().size());

            // Step 3: Build enriched context with memories
            EnrichedContext enrichedContext = new EnrichedContext(
                    context,
                    analysis,
                    relevantMemories
            );

            // Step 4: Apply personality traits to understand response style
            PersonalityEngine.PersonalityProfile profile =
                    personalityEngine.buildProfile(context.personality());

            // Step 5: Generate response based on enriched context and personality
            String response = responseGenerator.generate(
                    userMessage,
                    enrichedContext,
                    profile
            );

            // Step 6: Return brain response with metadata
            return new BrainResponse(
                    response,
                    analysis.intent(),
                    analysis.sentiment(),
                    analysis.confidence(),
                    relevantMemories.size()
            );

        } catch (Exception e) {
            logger.error("Error processing message in brain: {}", e.getMessage(), e);
            return new BrainResponse(
                    personalityEngine.getErrorResponse(context.personality()),
                    "error",
                    "neutral",
                    0.0,
                    0
            );
        }
    }

    /**
     * Passively track context from a message without generating a response.
     * Used for building memory when Pudel isn't directly addressed.
     * <p>
     * Uses PassiveContextProcessor for proper queue management:
     * - Deduplication: only keeps newest message per user+channel
     * - Age expiration: drops messages older than 1 minute
     * - Rate limiting: processes in controlled batches
     * - LLM fallback: uses fast analysis if LLM times out
     *
     * @param message the message content
     * @param userId the user who sent the message
     * @param channelId the channel ID
     * @param isGuild whether this is a guild message
     * @param targetId guild ID or user ID
     */
    public void trackContext(String message, long userId, long channelId,
                             boolean isGuild, long targetId) {
        // Delegate to PassiveContextProcessor for proper queue management
        // This prevents the "doom" scenario where messages pile up faster than
        // LLM can process them, causing timeouts and garbage collection
        passiveContextProcessor.submit(message, userId, channelId, isGuild, targetId);
    }

    /**
     * Get statistics about passive context processing.
     */
    public PassiveContextProcessor.QueueStats getPassiveContextStats() {
        return passiveContextProcessor.getStats();
    }

    /**
     * Store a dialogue exchange in memory.
     */
    public boolean storeDialogue(String userMessage, String botResponse, String intent,
                                 long userId, long channelId, boolean isGuild, long targetId) {
        return memoryManager.storeDialogue(
                userMessage, botResponse, intent, userId, channelId, isGuild, targetId
        );
    }

    /**
     * Get the model service for external use.
     */
    public PudelModelService getModelService() {
        return modelService;
    }

    /**
     * Get the memory manager for external use.
     */
    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    /**
     * Analyze text using the LangChain4j analyzer.
     * @param text the text to analyze
     * @return the analysis result
     */
    public TextAnalysis analyzeText(String text) {
        return modelService.analyzeText(text);
    }

    /**
     * Check if LLM-powered analysis is available.
     */
    public boolean isLLMAnalyzerAvailable() {
        return modelService.isAnalyzerLLMAvailable();
    }

    // ===============================
    // Inner Classes / Records
    // ===============================

    /**
     * Response from the brain with metadata.
     */
    public record BrainResponse(
            String response,
            String intent,
            String sentiment,
            double confidence,
            int memoriesUsed
    ) {}

    /**
     * Conversation context (passed from ChatbotService).
     */
    public record ConversationContext(
            List<Map<String, Object>> history,
            PudelPersonality personality,
            boolean isGuild,
            long targetId,
            long userId
    ) {}

    /**
     * Enriched context with text analysis and memories.
     */
    public record EnrichedContext(
            ConversationContext baseContext,
            TextAnalysis textAnalysis,
            List<MemoryManager.MemoryEntry> relevantMemories
    ) {
        public List<Map<String, Object>> getHistory() {
            return baseContext.history();
        }

        public PudelPersonality getPersonality() {
            return baseContext.personality();
        }
    }
}

