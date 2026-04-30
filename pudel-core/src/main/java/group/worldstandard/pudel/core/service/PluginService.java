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
package group.worldstandard.pudel.core.service;

import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.PluginInfo;
import group.worldstandard.pudel.core.database.PluginDatabaseService;
import group.worldstandard.pudel.core.entity.PluginMetadata;
import group.worldstandard.pudel.core.plugin.PluginAnnotationProcessor;
import group.worldstandard.pudel.core.plugin.PluginClassLoader;
import group.worldstandard.pudel.core.plugin.PluginContextFactory;
import group.worldstandard.pudel.core.repository.PluginMetadataRepository;
import net.dv8tion.jda.api.JDA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing plugin lifecycle.
 * <p>
 * Handles the complete plugin lifecycle using the annotation-based approach:
 * <ul>
 *   <li>Discovery and loading of JAR files</li>
 *   <li>Automatic command registration via annotations (@SlashCommand, @TextCommand, etc.)</li>
 *   <li>Automatic sync of slash commands to Discord</li>
 *   <li>Clean unregistration on disable</li>
 *   <li>Graceful shutdown with @OnShutdown (returns boolean for force-kill control)</li>
 *   <li>Hot-reload support via PluginWatcherService</li>
 * </ul>
 */
@Service
@Transactional
public class PluginService extends BaseService {
    private static final Logger logger = LoggerFactory.getLogger(PluginService.class);

    private final PluginMetadataRepository pluginMetadataRepository;
    private final PluginClassLoader pluginClassLoader;
    private final PluginContextFactory pluginContextFactory;
    private final PluginAnnotationProcessor annotationProcessor;
    private final PluginDatabaseService databaseService;
    private final PlatformTransactionManager transactionManager;

    // Plugin contexts: pluginName -> PluginContext
    private final Map<String, PluginContext> pluginContexts = new ConcurrentHashMap<>();

    // Track enabled state: pluginName -> enabled
    private final Map<String, Boolean> enabledPlugins = new ConcurrentHashMap<>();

    private final String whitelistRegex = "[^\\h\\w!'@#$%&*()\\-/+<>\":|\\[{\\].`~=]";

    public PluginService(@Lazy JDA jda,
                         PluginMetadataRepository pluginMetadataRepository,
                         PluginClassLoader pluginClassLoader,
                         PluginContextFactory pluginContextFactory,
                         PluginAnnotationProcessor annotationProcessor,
                         PluginDatabaseService databaseService,
                         PlatformTransactionManager transactionManager) {
        super(jda);
        this.pluginMetadataRepository = pluginMetadataRepository;
        this.pluginClassLoader = pluginClassLoader;
        this.pluginContextFactory = pluginContextFactory;
        this.annotationProcessor = annotationProcessor;
        this.databaseService = databaseService;
        this.transactionManager = transactionManager;
    }

    // =====================================================
    // Plugin Discovery & Loading
    // =====================================================

    /**
     * Scan for and load all plugins in the plugins directory.
     * <p>
     * Uses a deduplicated discovery sequence to prevent infinite load/unload loops
     * when multiple JAR files declare the same {@code @Plugin(name=...)}:
     * <ol>
     *   <li>Peek at each JAR to read the plugin name from {@code @Plugin} annotation</li>
     *   <li>Query database for existing metadata (by plugin name) to find old JAR filename</li>
     *   <li>If metadata exists with a different JAR name, treat as replacement: save old name, update metadata</li>
     *   <li>Delete the stale old JAR file to prevent duplicate processing</li>
     *   <li>Skip any JAR whose plugin name was already processed in this cycle</li>
     * </ol>
     */
    public void discoverPlugins() {
        // Reset all plugin states in database on startup
        // Since we're starting fresh, no plugins are enabled in memory
        resetPluginStatesOnStartup();

        File pluginsDir = pluginClassLoader.getPluginsDirectory();
        File[] files = pluginsDir.listFiles((_, name) -> name.endsWith(".jar"));

        if (files == null || files.length == 0) {
            logger.info("No plugins found in directory: {}", pluginsDir.getAbsolutePath());
        } else {
            logger.info("Found {} JAR files in plugins directory", files.length);

            // Sort by lastModified descending so the newest JAR wins when duplicates exist
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            // Track which plugin names have already been processed to skip duplicates
            Set<String> processedPluginNames = new HashSet<>();

            for (File jarFile : files) {
                // Step 1: Peek at the JAR to read the @Plugin annotation name
                String pluginName = pluginClassLoader.peekPluginName(jarFile);

                if (pluginName == null) {
                    logger.warn("Could not read plugin name from JAR: {}, skipping", jarFile.getName().replaceAll(whitelistRegex, ""));
                    continue;
                }

                // Step 2: Skip if we already processed a JAR with this plugin name (newest wins)
                if (processedPluginNames.contains(pluginName)) {
                    logger.warn("Duplicate plugin name '{}' found in JAR '{}'. " +
                                "A newer JAR with the same plugin name was already loaded. Deleting stale JAR.",
                            pluginName.replaceAll(whitelistRegex, ""), jarFile.getName().replaceAll(whitelistRegex, ""));
                    if (!jarFile.delete()) {
                        logger.warn("Could not delete stale duplicate JAR: {}", jarFile.getName().replaceAll(whitelistRegex, ""));
                    }
                    continue;
                }

                // Step 3: Check database for existing metadata with this plugin name
                Optional<PluginMetadata> existingMetadata = pluginMetadataRepository.findByPluginName(pluginName);
                if (existingMetadata.isPresent()) {
                    String oldJarFileName = existingMetadata.get().getJarFileName();
                    String newJarFileName = jarFile.getName();

                    // If the JAR filename changed, this is a replacement
                    if (oldJarFileName != null && !oldJarFileName.equals(newJarFileName)) {
                        logger.info("Plugin '{}' JAR changed from '{}' to '{}', treating as replacement",
                                pluginName.replaceAll(whitelistRegex, ""), oldJarFileName.replaceAll(whitelistRegex, ""), newJarFileName.replaceAll(whitelistRegex, ""));

                        // Step 4: Delete the old JAR file
                        // Validate old filename to prevent path traversal
                        String sanitizedOldName = java.nio.file.Path.of(oldJarFileName).getFileName().toString();
                        if (sanitizedOldName.equals(oldJarFileName) && !oldJarFileName.contains("..")) {
                            File oldJarFile = new File(pluginsDir, sanitizedOldName);
                            if (oldJarFile.exists() && !oldJarFile.equals(jarFile)) {
                                if (oldJarFile.delete()) {
                                    logger.info("Deleted old JAR file: {}", oldJarFileName.replaceAll(whitelistRegex, ""));
                                } else {
                                    logger.warn("Could not delete old JAR file: {}", oldJarFileName.replaceAll(whitelistRegex, ""));
                                }
                            }
                        }

                        // Update the JAR filename in database metadata
                        existingMetadata.get().setJarFileName(newJarFileName);
                        pluginMetadataRepository.save(existingMetadata.get());
                    }
                }

                // Mark this plugin name as processed
                processedPluginNames.add(pluginName);

                // Step 5: Load and initialize the plugin
                loadAndInitialize(jarFile);
            }
        }

        // NOTE: File watching is handled by PluginWatcherService (NIO WatchService + polling).
    }

    /**
     * Reset plugin states in database on startup.
     * <p>
     * This ensures that the database enabled/loaded states are synchronized
     * with the actual in-memory state after a reboot. On startup:
     * <ul>
     *   <li>All plugins should be marked as not loaded (they will be loaded during discovery)</li>
     *   <li>All plugins should be marked as not enabled (admins need to re-enable them)</li>
     * </ul>
     */
    private void resetPluginStatesOnStartup() {
        logger.info("Resetting plugin states in database on startup...");

        List<PluginMetadata> allPlugins = pluginMetadataRepository.findAll();
        int resetCount = 0;

        for (PluginMetadata plugin : allPlugins) {
            boolean needsUpdate = false;

            if (plugin.isEnabled()) {
                logger.info("Plugin {} was marked enabled in database, resetting to disabled", plugin.getPluginName().replaceAll(whitelistRegex, ""));
                plugin.setEnabled(false);
                needsUpdate = true;
            }

            if (plugin.isLoaded()) {
                plugin.setLoaded(false);
                needsUpdate = true;
            }

            if (needsUpdate) {
                pluginMetadataRepository.save(plugin);
                resetCount++;
            }
        }

        if (resetCount > 0) {
            logger.info("Reset {} plugin states in database", resetCount);
        }
    }

    /**
     * Load and initialize a plugin from JAR file.
     */
    private void loadAndInitialize(File jarFile) {
        try {
            PluginInfo info = pluginClassLoader.loadPlugin(jarFile);
            if (info != null) {
                String pluginName = info.getName();
                String pluginVersion = info.getVersion();

                // Register metadata in database
                registerPluginMetadata(info, jarFile.getName());

                // Create plugin context with actual version for proper database registry
                PluginContext context = pluginContextFactory.getContext(info);
                pluginContexts.put(pluginName, context);

                logger.info("Plugin loaded: {} v{}", pluginName.replaceAll(whitelistRegex, ""), pluginVersion);
            }
        } catch (Exception e) {
            logger.error("Error loading plugin {}: {}", jarFile.getName().replaceAll(whitelistRegex, ""), e.getMessage(), e);
        }
    }

    /**
     * Register plugin metadata in the database.
     * <p>
     * This is used during initial discovery and by the file watcher
     * when new plugins are discovered at runtime.
     *
     * @param info        the plugin info
     * @param jarFileName the JAR file name (not the temp copy)
     */
    public void registerPluginMetadata(PluginInfo info, String jarFileName) {
        String pluginName = info.getName();

        Optional<PluginMetadata> existing = pluginMetadataRepository.findByPluginName(pluginName);

        PluginMetadata metadata;
        if (existing.isPresent()) {
            metadata = existing.get();
            metadata.setPluginVersion(info.getVersion());
            metadata.setPluginAuthor(info.getAuthor());
            metadata.setPluginDescription(info.getDescription());
            metadata.setJarFileName(jarFileName);
            metadata.setLoaded(true);
            metadata.setLoadError(null);
        } else {
            metadata = new PluginMetadata(
                    pluginName,
                    info.getVersion(),
                    jarFileName,
                    "" // Main class tracked internally
            );
            metadata.setPluginAuthor(info.getAuthor());
            metadata.setPluginDescription(info.getDescription());
            metadata.setLoaded(true);
        }

        pluginMetadataRepository.save(metadata);
    }

    // =====================================================
    // Plugin Enable/Disable
    // =====================================================

    /**
     * Enable a plugin.
     * <p>
     * This method:
     * <ol>
     *   <li>Creates plugin context</li>
     *   <li>Processes annotations (@SlashCommand, @TextCommand, etc.)</li>
     *   <li>Calls @OnEnable methods</li>
     *   <li>Automatically syncs slash commands to Discord</li>
     * </ol>
     *
     * @param pluginName the plugin name
     */
    public void enablePlugin(String pluginName) {
        if (!pluginClassLoader.isPluginLoaded(pluginName)) {
            logger.warn("Plugin not found: {}", pluginName.replaceAll(whitelistRegex, ""));
            return;
        }

        // Check both in-memory state AND database state
        boolean inMemoryEnabled = enabledPlugins.getOrDefault(pluginName, false);
        boolean dbEnabled = pluginMetadataRepository.findByPluginName(pluginName)
                .map(PluginMetadata::isEnabled)
                .orElse(false);

        if (inMemoryEnabled) {
            logger.warn("Plugin {} is already enabled in memory", pluginName.replaceAll(whitelistRegex, ""));
            return;
        }

        // If database shows enabled but memory doesn't, reset database state first
        if (dbEnabled) {
            logger.info("Plugin {} was marked enabled in database but not in memory, resetting state", pluginName.replaceAll(whitelistRegex, ""));
            pluginMetadataRepository.findByPluginName(pluginName).ifPresent(m -> {
                m.setEnabled(false);
                pluginMetadataRepository.save(m);
            });
        }

        try {
            Object instance = pluginClassLoader.getPluginInstance(pluginName);
            PluginContext context = pluginContexts.computeIfAbsent(pluginName,
                    name -> {
                        PluginInfo info = pluginClassLoader.getPluginInfo(name);
                        if (info == null) {
                            throw new IllegalStateException("No PluginInfo found for plugin: " + name);
                        }
                        return pluginContextFactory.getContext(info);
                    });

            // Phase 1: Obtain (or create) the plugin's unique database prefix
            //          in an isolated REQUIRES_NEW transaction so that a failure
            //          here does not poison the outer transaction.
            PluginInfo info = pluginClassLoader.getPluginInfo(pluginName);
            String pluginVersion = info != null ? info.getVersion() : "0.0.0";
            String dbPrefix = databaseService.getOrCreatePrefix(pluginName, pluginVersion);

            // Phase 2: Process annotations and register handlers.
            //          Handler IDs are namespaced with the database prefix
            //          (e.g. "p_48f2391a_") to avoid collisions between plugins.
            //          @OnEnable is NOT called here.
            int registered = annotationProcessor.processAndRegister(pluginName, instance, context, dbPrefix);

            // Phase 3: Auto-sync slash commands to Discord
            if (registered > 0) {
                annotationProcessor.syncCommands();
            }

            // Phase 4: Mark as enabled (in the current transaction)
            enabledPlugins.put(pluginName, true);

            // Update database
            pluginMetadataRepository.findByPluginName(pluginName).ifPresent(m -> {
                m.setEnabled(true);
                pluginMetadataRepository.save(m);
            });

            // Phase 5: Call @OnEnable in an isolated REQUIRES_NEW transaction.
            //          If the plugin's @OnEnable triggers a failing SQL statement
            //          (e.g. table creation error), only the inner transaction is
            //          poisoned/rolled back — the outer transaction that just
            //          persisted the metadata remains healthy.
            TransactionTemplate onEnableTx = new TransactionTemplate(transactionManager);
            onEnableTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            try {
                onEnableTx.execute(status -> {
                    try {
                        annotationProcessor.invokeOnEnable(instance, context);
                    } catch (Exception e) {
                        logger.error("Plugin {} @OnEnable failed: {}", pluginName.replaceAll(whitelistRegex, ""), e.getMessage(), e);
                        status.setRollbackOnly();
                    }
                    return null;
                });
            } catch (Exception e) {
                logger.warn("Plugin {} @OnEnable transaction rolled back: {}", pluginName.replaceAll(whitelistRegex, ""), e.getMessage());
            }

            logger.info("Plugin enabled: {} ({} handlers registered)", pluginName.replaceAll(whitelistRegex, ""), registered);

        } catch (Exception e) {
            logger.error("Error enabling plugin {}: {}", pluginName.replaceAll(whitelistRegex, ""), e.getMessage(), e);
        }
    }

    /**
     * Disable a plugin.
     * <p>
     * This method:
     * <ol>
     *   <li>Calls @OnDisable methods</li>
     *   <li>Unregisters all annotation-based handlers</li>
     *   <li>Automatically syncs to remove slash commands from Discord</li>
     * </ol>
     *
     * @param pluginName the plugin name
     */
    public void disablePlugin(String pluginName) {
        if (!pluginClassLoader.isPluginLoaded(pluginName)) {
            logger.warn("Plugin not found: {}", pluginName.replaceAll(whitelistRegex, ""));
            return;
        }

        // Check both in-memory state AND database state to handle reboot scenarios
        boolean inMemoryEnabled = enabledPlugins.getOrDefault(pluginName, false);
        boolean dbEnabled = pluginMetadataRepository.findByPluginName(pluginName)
                .map(PluginMetadata::isEnabled)
                .orElse(false);

        if (!inMemoryEnabled && !dbEnabled) {
            logger.warn("Plugin {} is not enabled (memory={}, db={})", pluginName.replaceAll(whitelistRegex, ""), inMemoryEnabled, dbEnabled);
            return;
        }

        // Sync in-memory state if database shows enabled but memory doesn't
        if (!inMemoryEnabled) {
            logger.info("Plugin {} was enabled in database but not in memory, syncing state", pluginName.replaceAll(whitelistRegex, ""));
        }

        try {
            Object instance = pluginClassLoader.getPluginInstance(pluginName);
            PluginContext context = pluginContexts.get(pluginName);

            // Unregister all annotation-based handlers and call @OnDisable
            annotationProcessor.unregisterAll(pluginName, instance, context);

            // Unregister all event listeners
            pluginContextFactory.getEventManager().unregisterListeners(pluginName);

            // Auto-sync to remove slash commands from Discord
            annotationProcessor.syncCommands();

            // Mark as disabled
            enabledPlugins.put(pluginName, false);

            // Update database
            pluginMetadataRepository.findByPluginName(pluginName).ifPresent(m -> {
                m.setEnabled(false);
                pluginMetadataRepository.save(m);
            });

            logger.info("Plugin disabled: {}", pluginName.replaceAll(whitelistRegex, ""));

        } catch (Exception e) {
            logger.error("Error disabling plugin {}: {}", pluginName.replaceAll(whitelistRegex, ""), e.getMessage(), e);
        }
    }

    // =====================================================
    // Plugin Unload & Shutdown
    // =====================================================

    /**
     * Unload a plugin completely.
     * <p>
     * This method:
     * <ol>
     *   <li>Disables the plugin if enabled</li>
     *   <li>Calls @OnShutdown method (expects boolean return)</li>
     *   <li>If @OnShutdown returns false, force-kills the plugin</li>
     *   <li>Releases class loader and resources</li>
     * </ol>
     *
     * @param pluginName the plugin name
     */
    public void unloadPlugin(String pluginName) {
        if (!pluginClassLoader.isPluginLoaded(pluginName)) {
            logger.warn("Plugin not found for unloading: {}", pluginName.replaceAll(whitelistRegex, ""));
            return;
        }

        try {
            // Disable first if enabled
            if (enabledPlugins.getOrDefault(pluginName, false)) {
                disablePlugin(pluginName);
            }

            Object instance = pluginClassLoader.getPluginInstance(pluginName);
            PluginContext context = pluginContexts.get(pluginName);

            // Call @OnShutdown and check result
            boolean shutdownSuccess = annotationProcessor.invokeShutdown(pluginName, instance, context);

            // Remove context
            pluginContexts.remove(pluginName);
            pluginContextFactory.removeContext(pluginName);
            enabledPlugins.remove(pluginName);

            // Unload based on shutdown result
            if (shutdownSuccess) {
                pluginClassLoader.unloadPlugin(pluginName);
            } else {
                logger.warn("[{}] Shutdown returned false, force-killing plugin", pluginName.replaceAll(whitelistRegex, ""));
                pluginClassLoader.forceUnloadPlugin(pluginName);
            }

            // Update database
            pluginMetadataRepository.findByPluginName(pluginName).ifPresent(m -> {
                m.setLoaded(false);
                m.setEnabled(false);
                pluginMetadataRepository.save(m);
            });

            logger.info("Plugin unloaded: {} (force={})", pluginName.replaceAll(whitelistRegex, ""), !shutdownSuccess);

        } catch (Exception e) {
            logger.error("Error unloading plugin {}: {}", pluginName.replaceAll(whitelistRegex, ""), e.getMessage(), e);
            // Force unload on error
            pluginClassLoader.forceUnloadPlugin(pluginName);
        }
    }

    /**
     * Shutdown all plugins.
     * Called when the bot is shutting down.
     */
    public void shutdownAllPlugins() {
        logger.info("Shutting down all plugins...");

        for (String pluginName : pluginClassLoader.getAllPlugins().keySet()) {
            try {
                unloadPlugin(pluginName);
            } catch (Exception e) {
                logger.error("Error shutting down plugin {}: {}", pluginName.replaceAll(whitelistRegex, ""), e.getMessage(), e);
            }
        }

        // Final cleanup
        pluginContexts.clear();
        enabledPlugins.clear();
        pluginClassLoader.closeAllClassLoaders();

        logger.info("All plugins shut down");
    }

    /**
     * Remove a plugin completely.
     * <p>
     * This method:
     * <ol>
     *   <li>Unloads the plugin</li>
     *   <li>Deletes the plugin metadata from the database</li>
     * </ol>
     * <p>
     * Note: This does NOT delete the JAR file. Use {@code AdminController}
     * for the complete removal including JAR file deletion.
     *
     * @param pluginName the plugin name
     */
    public void removePlugin(String pluginName) {
        // First unload the plugin
        unloadPlugin(pluginName);

        // Then delete the metadata from database
        pluginMetadataRepository.findByPluginName(pluginName).ifPresent(m -> {
            pluginMetadataRepository.delete(m);
            logger.info("Plugin metadata removed from database: {}", pluginName.replaceAll(whitelistRegex, ""));
        });
    }

    // =====================================================
    // Plugin Reload
    // =====================================================

    /**
     * Reload a plugin.
     * <p>
     * This method:
     * <ol>
     *   <li>Remembers if plugin was enabled</li>
     *   <li>Unloads the plugin</li>
     *   <li>Reloads the plugin from its JAR file</li>
     *   <li>Re-enables the plugin if it was enabled before</li>
     * </ol>
     *
     * @param pluginName the plugin name
     * @return true if reload was successful
     */
    public boolean reloadPlugin(String pluginName) {
        if (!pluginClassLoader.isPluginLoaded(pluginName)) {
            logger.warn("Plugin not found for reloading: {}", pluginName.replaceAll(whitelistRegex, ""));
            return false;
        }

        try {
            // Get JAR file name before unloading
            Optional<PluginMetadata> metadata = pluginMetadataRepository.findByPluginName(pluginName);
            if (metadata.isEmpty() || metadata.get().getJarFileName() == null) {
                logger.error("Cannot reload plugin {}: JAR file information not found", pluginName.replaceAll(whitelistRegex, ""));
                return false;
            }

            String jarFileName = metadata.get().getJarFileName();

            // Validate filename to prevent path traversal
            String sanitizedFileName = java.nio.file.Path.of(jarFileName).getFileName().toString();
            if (!sanitizedFileName.equals(jarFileName) || jarFileName.contains("..")) {
                logger.error("Cannot reload plugin {}: suspicious JAR filename: {}", pluginName.replaceAll(whitelistRegex, ""), jarFileName);
                return false;
            }

            File jarFile = new File(pluginClassLoader.getPluginsDirectory(), sanitizedFileName);

            if (!jarFile.exists()) {
                logger.error("Cannot reload plugin {}: JAR file does not exist: {}", pluginName.replaceAll(whitelistRegex, ""), jarFile.getAbsolutePath());
                return false;
            }

            // Remember enabled state - check both memory and database
            boolean wasEnabled = enabledPlugins.getOrDefault(pluginName, false);
            if (!wasEnabled) {
                wasEnabled = metadata.get().isEnabled();
            }

            // Unload old version
            logger.info("Reloading plugin {}: unloading...", pluginName.replaceAll(whitelistRegex, ""));
            unloadPlugin(pluginName);

            // Load new version
            logger.info("Reloading plugin {}: loading from {}...", pluginName.replaceAll(whitelistRegex, ""), jarFileName.replaceAll(whitelistRegex, ""));
            loadAndInitialize(jarFile);

            // Re-enable if was enabled
            if (wasEnabled) {
                logger.info("Reloading plugin {}: re-enabling...", pluginName.replaceAll(whitelistRegex, ""));
                enablePlugin(pluginName);
            }

            logger.info("Plugin {} reloaded successfully", pluginName.replaceAll(whitelistRegex, ""));
            return true;

        } catch (Exception e) {
            logger.error("Error reloading plugin {}: {}", pluginName.replaceAll(whitelistRegex, ""), e.getMessage(), e);
            return false;
        }
    }


    // =====================================================
    // Query Methods
    // =====================================================

    /**
     * Get all loaded plugins.
     */
    public List<PluginMetadata> getAllPlugins() {
        return pluginMetadataRepository.findAll();
    }

    /**
     * Get a plugin by name.
     */
    public Optional<PluginMetadata> getPlugin(String pluginName) {
        return pluginMetadataRepository.findByPluginName(pluginName);
    }

    /**
     * Get all enabled plugins.
     */
    public List<PluginMetadata> getEnabledPlugins() {
        return pluginMetadataRepository.findByEnabled(true);
    }

    /**
     * Check if a plugin is enabled.
     */
    public boolean isPluginEnabled(String pluginName) {
        return enabledPlugins.getOrDefault(pluginName, false);
    }
}
