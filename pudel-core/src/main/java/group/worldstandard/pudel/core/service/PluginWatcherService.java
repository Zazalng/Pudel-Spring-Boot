/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
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

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import group.worldstandard.pudel.api.PluginInfo;
import group.worldstandard.pudel.core.config.plugins.PluginProperties;
import group.worldstandard.pudel.core.entity.PluginMetadata;
import group.worldstandard.pudel.core.plugin.PluginClassLoader;
import group.worldstandard.pudel.core.repository.PluginMetadataRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service that watches the plugins directory for changes and handles hot-reload.
 * <p>
 * Features:
 * - Auto-detect new plugins (NIO WatchService + polling fallback for Docker)
 * - Hash-based change detection for updates
 * - Loads plugins from temp copies for safe hot-reload
 * - Retry logic for failed plugins with configurable attempts
 * - Pending updates list for enabled plugins
 * - Warning notifications for pending updates
 * - Docker-compatible with bind-mounted plugin directories
 */
@Service
public class PluginWatcherService {

    private static final Logger logger = LoggerFactory.getLogger(PluginWatcherService.class);
    private static final long UPDATE_CHECK_INTERVAL_MS = 60_000; // 1 minute
    private static final long FILE_STABILITY_DELAY_MS = 1000; // Wait for file to finish writing

    private final PluginProperties pluginProperties;
    private final PluginClassLoader pluginClassLoader;
    private final PluginMetadataRepository pluginMetadataRepository;
    private final PluginService pluginService;

    // Track JAR hashes: pluginName -> hash
    private final Map<String, String> jarHashes = new ConcurrentHashMap<>();

    // Track JAR file -> plugin name mapping
    private final Map<String, String> jarToPlugin = new ConcurrentHashMap<>();

    // Track failed JAR files: jarFileName -> FailedJarInfo
    // This allows re-attempting load when the JAR is updated OR after retry interval
    private final Map<String, FailedJarInfo> failedJars = new ConcurrentHashMap<>();

    // Pending updates for enabled plugins: pluginName -> PendingUpdate
    private final Map<String, PendingUpdate> pendingUpdates = new ConcurrentHashMap<>();

    // Track files being written (to avoid loading incomplete uploads)
    private final Map<String, FileWriteTracker> filesBeingWritten = new ConcurrentHashMap<>();

    // Temp directory for loaded plugin copies
    private Path tempPluginDir;

    // Thread-safe shutdown flags
    private final AtomicBoolean shutdownInitiated = new AtomicBoolean(false);
    private final AtomicBoolean cleanupCompleted = new AtomicBoolean(false);

    private volatile boolean watcherRunning = false;

    // NIO WatchService for real-time file watching
    private WatchService watchService;
    private ExecutorService watchExecutor;

    // Shutdown hook reference
    private Thread shutdownHook;

    // Scheduled executor for retry tasks
    private ScheduledExecutorService retryScheduler;

    public PluginWatcherService(PluginProperties pluginProperties,
                                 PluginClassLoader pluginClassLoader,
                                 PluginMetadataRepository pluginMetadataRepository,
                                 PluginService pluginService) {
        this.pluginProperties = pluginProperties;
        this.pluginClassLoader = pluginClassLoader;
        this.pluginMetadataRepository = pluginMetadataRepository;
        this.pluginService = pluginService;
    }

    @PostConstruct
    public void init() {
        try {
            // Create temp directory for plugin copies
            tempPluginDir = Files.createTempDirectory("pudel-plugins-");
            logger.info("Plugin temp directory: {}", tempPluginDir);

            // Initialize retry scheduler
            retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "PluginRetryScheduler");
                t.setDaemon(true);
                return t;
            });

            // Register shutdown hook
            shutdownHook = new Thread(() -> {
                if (shutdownInitiated.compareAndSet(false, true)) {
                    logger.info("Shutdown hook triggered - cleaning up plugin resources");
                    performCleanup();
                }
            }, "PluginWatcher-ShutdownHook");
            Runtime.getRuntime().addShutdownHook(shutdownHook);

            watcherRunning = true;

            // Initial scan
            scanPluginsDirectory();

            // Start NIO WatchService for real-time detection
            startWatchService();

        } catch (IOException e) {
            logger.error("Failed to initialize PluginWatcherService: {}", e.getMessage(), e);
        }
    }

    /**
     * Start the NIO WatchService for real-time file change detection.
     * Falls back to polling if WatchService isn't supported (e.g., some Docker setups).
     */
    private void startWatchService() {
        try {
            Path pluginsPath = pluginClassLoader.getPluginsDirectory().toPath();

            if (!Files.exists(pluginsPath)) {
                Files.createDirectories(pluginsPath);
            }

            watchService = FileSystems.getDefault().newWatchService();
            pluginsPath.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            watchExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "PluginWatchService");
                t.setDaemon(true);
                return t;
            });

            watchExecutor.submit(this::watchLoop);
            logger.info("NIO WatchService started for plugins directory: {}", pluginsPath);

        } catch (IOException e) {
            logger.warn("Could not start NIO WatchService (falling back to polling): {}", e.getMessage());
            // Polling fallback is handled by @Scheduled method
        }
    }

    /**
     * Watch loop for NIO WatchService.
     */
    private void watchLoop() {
        while (watcherRunning && !Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.poll(5, TimeUnit.SECONDS);
                if (key == null) {
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        // Too many events, do a full scan
                        scanPluginsDirectory();
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();
                    String fileName = filename.toString();

                    if (!fileName.endsWith(".jar")) {
                        continue;
                    }

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE ||
                        kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        // Track file write and schedule processing after stability delay
                        handleFileChange(fileName);
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        handleFileDelete(fileName);
                    }
                }

                if (!key.reset()) {
                    logger.warn("WatchKey is no longer valid, stopping watch service");
                    break;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                logger.debug("WatchService closed");
                break;
            } catch (Exception e) {
                logger.error("Error in watch loop: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Handle file create/modify events with stability delay.
     * Waits for file to finish writing before processing.
     */
    private void handleFileChange(String fileName) {
        FileWriteTracker tracker = filesBeingWritten.compute(fileName, (key, v) -> {
            if (v == null) {
                v = new FileWriteTracker();
            }
            v.lastModified = Instant.now();
            return v;
        });

        // Cancel any previous scheduled task
        if (tracker.scheduledTask != null && !tracker.scheduledTask.isDone()) {
            tracker.scheduledTask.cancel(false);
        }

        // Schedule processing after stability delay
        tracker.scheduledTask = retryScheduler.schedule(() -> {
            filesBeingWritten.remove(fileName);
            File jarFile = new File(pluginClassLoader.getPluginsDirectory(), fileName);
            if (jarFile.exists()) {
                logger.debug("File stable, processing: {}", fileName);
                processJarFile(jarFile);
            }
        }, FILE_STABILITY_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Handle file delete events.
     */
    private void handleFileDelete(String fileName) {
        filesBeingWritten.remove(fileName);
        String pluginName = jarToPlugin.remove(fileName);
        if (pluginName != null) {
            logger.info("Plugin JAR removed: {} ({})", fileName, pluginName);
            jarHashes.remove(pluginName);
        }
        failedJars.remove(fileName);
    }

    @PreDestroy
    public void shutdown() {
        logger.info("PluginWatcherService shutting down...");
        watcherRunning = false;

        // Mark shutdown as initiated
        if (shutdownInitiated.compareAndSet(false, true)) {
            performCleanup();

            // Try to remove shutdown hook
            try {
                if (shutdownHook != null) {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                }
            } catch (IllegalStateException e) {
                logger.debug("Could not remove shutdown hook - JVM already shutting down");
            }
        }

        logger.info("PluginWatcherService shutdown complete");
    }

    /**
     * Performs the actual cleanup of resources.
     * Thread-safe - will only run once.
     */
    private void performCleanup() {
        if (cleanupCompleted.compareAndSet(false, true)) {
            logger.info("Performing plugin cleanup...");

            // 1. Stop watch service first
            stopWatchService();

            // 2. Stop retry scheduler
            if (retryScheduler != null) {
                retryScheduler.shutdownNow();
                try {
                    retryScheduler.awaitTermination(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 3. Shutdown all plugins to release classloader resources
            try {
                pluginService.shutdownAllPlugins();
            } catch (Exception e) {
                logger.error("Error shutting down plugins: {}", e.getMessage(), e);
            }

            // 4. Give time for classloaders to release file handles
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 5. Request GC to help release file handles
            System.gc();

            // 6. Small delay for GC to complete
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 7. Now cleanup temp directory
            cleanupTempDir();

            // 8. Clear all tracking maps
            jarHashes.clear();
            jarToPlugin.clear();
            failedJars.clear();
            pendingUpdates.clear();
            filesBeingWritten.clear();

            logger.info("Plugin cleanup completed");
        }
    }

    /**
     * Stop the NIO WatchService.
     */
    private void stopWatchService() {
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.debug("Error closing watch service: {}", e.getMessage());
            }
        }

        if (watchExecutor != null) {
            watchExecutor.shutdownNow();
            try {
                watchExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Scheduled task to check for plugin updates (polling fallback for Docker).
     * Also handles retry logic for failed plugins.
     */
    @Scheduled(fixedRate = UPDATE_CHECK_INTERVAL_MS)
    public void checkForUpdates() {
        if (!watcherRunning || !pluginProperties.isEnableAutoDiscovery()) {
            return;
        }

        scanPluginsDirectory();
        retryFailedPlugins();
        warnPendingUpdates();
    }

    /**
     * Scan the plugins directory for new/updated plugins.
     */
    public void scanPluginsDirectory() {
        if (!watcherRunning) {
            return;
        }

        File pluginsDir = pluginClassLoader.getPluginsDirectory();
        File[] jarFiles = pluginsDir.listFiles((dir, name) -> name.endsWith(".jar"));

        if (jarFiles == null) {
            return;
        }

        Set<String> currentJars = new HashSet<>();

        for (File jarFile : jarFiles) {
            currentJars.add(jarFile.getName());
            processJarFile(jarFile);
        }

        // Check for removed JARs
        Set<String> removedJars = new HashSet<>(jarToPlugin.keySet());
        removedJars.removeAll(currentJars);

        for (String removedJar : removedJars) {
            String pluginName = jarToPlugin.remove(removedJar);
            if (pluginName != null) {
                logger.info("Plugin JAR removed: {} ({})", removedJar, pluginName);
                jarHashes.remove(pluginName);
            }
        }

        // Also clean up failed jars that no longer exist
        failedJars.keySet().removeIf(jarName -> !currentJars.contains(jarName));
    }

    /**
     * Process a single JAR file.
     */
    private void processJarFile(File jarFile) {
        if (!watcherRunning) {
            return;
        }

        try {
            String currentHash = computeFileHash(jarFile);
            String jarName = jarFile.getName();

            // Check if this was a previously failed JAR
            FailedJarInfo failedInfo = failedJars.get(jarName);
            if (failedInfo != null) {
                // Check if the JAR has been updated since last failure
                if (!failedInfo.hash.equals(currentHash)) {
                    logger.info("Previously failed JAR '{}' has been updated, retrying load...", jarName);
                    failedJars.remove(jarName);
                    loadNewPlugin(jarFile, currentHash);
                }
                // Don't return here - let retry logic handle unchanged failed JARs
                return;
            }

            // Check if this is a known plugin
            String existingPluginName = jarToPlugin.get(jarName);

            if (existingPluginName != null) {
                // Check if hash changed (update detected)
                String storedHash = jarHashes.get(existingPluginName);

                if (storedHash != null && !storedHash.equals(currentHash)) {
                    handlePluginUpdate(existingPluginName, jarFile, currentHash);
                }
            } else {
                // New plugin - try to load it
                loadNewPlugin(jarFile, currentHash);
            }

        } catch (Exception e) {
            logger.error("Error processing JAR file {}: {}", jarFile.getName(), e.getMessage());
        }
    }

    /**
     * Retry loading failed plugins based on retry interval and max attempts.
     */
    private void retryFailedPlugins() {
        if (failedJars.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        long retryIntervalMs = pluginProperties.getRetryIntervalMs();
        int maxRetries = pluginProperties.getMaxRetryAttempts();

        for (Map.Entry<String, FailedJarInfo> entry : failedJars.entrySet()) {
            String jarName = entry.getKey();
            FailedJarInfo info = entry.getValue();

            // Check if max retries exceeded
            if (info.attemptCount.get() >= maxRetries) {
                continue; // Don't retry anymore, wait for JAR update
            }

            // Check if enough time has passed since last attempt
            Duration sinceLastAttempt = Duration.between(info.lastAttemptTime, now);
            if (sinceLastAttempt.toMillis() < retryIntervalMs) {
                continue;
            }

            // Retry loading
            File jarFile = new File(pluginClassLoader.getPluginsDirectory(), jarName);
            if (jarFile.exists()) {
                try {
                    String currentHash = computeFileHash(jarFile);

                    // Check if file was modified
                    if (!currentHash.equals(info.hash)) {
                        logger.info("Failed JAR '{}' was modified, resetting retry count", jarName);
                        info.hash = currentHash;
                        info.attemptCount.set(0);
                    }

                    info.attemptCount.incrementAndGet();
                    info.lastAttemptTime = now;

                    logger.info("Retrying failed plugin '{}' (attempt {}/{})",
                            jarName, info.attemptCount.get(), maxRetries);

                    // Remove from failed list temporarily
                    failedJars.remove(jarName);

                    // Try to load
                    loadNewPlugin(jarFile, currentHash);

                } catch (Exception e) {
                    logger.debug("Retry failed for {}: {}", jarName, e.getMessage());
                }
            } else {
                // JAR no longer exists
                failedJars.remove(jarName);
            }
        }
    }

    /**
     * Handle a plugin update.
     */
    private void handlePluginUpdate(String pluginName, File newJarFile, String newHash) {
        Optional<PluginMetadata> metadataOpt = pluginMetadataRepository.findByPluginName(pluginName);

        if (metadataOpt.isEmpty()) {
            return;
        }

        PluginMetadata metadata = metadataOpt.get();

        if (metadata.isEnabled()) {
            // Plugin is enabled - queue update and warn
            pendingUpdates.put(pluginName, new PendingUpdate(newJarFile.getAbsolutePath(), newHash, Instant.now()));
            logger.warn("Update detected for enabled plugin '{}'. Restart or disable plugin to apply update.", pluginName);
        } else {
            // Plugin is disabled - apply update immediately
            applyPluginUpdate(pluginName, newJarFile, newHash);
        }
    }

    /**
     * Apply a plugin update (for disabled plugins).
     */
    private void applyPluginUpdate(String pluginName, File newJarFile, String newHash) {
        logger.info("Applying update for plugin: {}", pluginName);

        try {
            // Unload old plugin
            pluginService.unloadPlugin(pluginName);

            // Wait a moment for classloader to release
            Thread.sleep(100);

            // Delete old temp copy
            deleteOldTempCopies(pluginName);

            // Copy new JAR to temp
            String tempName = newJarFile.getName().replace(".jar", "-" + newHash.substring(0, 8) + ".jar");
            Path newTempCopy = tempPluginDir.resolve(tempName);
            Files.copy(newJarFile.toPath(), newTempCopy, StandardCopyOption.REPLACE_EXISTING);

            // Load from temp copy
            PluginInfo pluginInfo = pluginClassLoader.loadPlugin(newTempCopy.toFile());

            if (pluginInfo != null) {
                jarHashes.put(pluginName, newHash);
                jarToPlugin.put(newJarFile.getName(), pluginName);
                pendingUpdates.remove(pluginName);
                logger.info("Plugin '{}' updated successfully", pluginName);
            }

        } catch (Exception e) {
            logger.error("Failed to apply update for plugin {}: {}", pluginName, e.getMessage(), e);
        }
    }

    /**
     * Delete old temp copies for a plugin.
     */
    private void deleteOldTempCopies(String pluginName) {
        try {
            if (tempPluginDir != null && Files.exists(tempPluginDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempPluginDir, "*.jar")) {
                    for (Path path : stream) {
                        String fileName = path.getFileName().toString();
                        // Match plugin name pattern (e.g., pudel-music-1.0.0-abcd1234.jar)
                        if (fileName.startsWith(pluginName) ||
                            fileName.contains(pluginName.toLowerCase())) {
                            try {
                                Files.deleteIfExists(path);
                                logger.debug("Deleted old temp copy: {}", path);
                            } catch (IOException e) {
                                logger.debug("Could not delete temp file: {}", path);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.debug("Error cleaning old temp copies: {}", e.getMessage());
        }
    }

    /**
     * Load a new plugin.
     */
    private void loadNewPlugin(File jarFile, String hash) {
        if (!watcherRunning) {
            return;
        }

        Path tempCopy = null;
        try {
            // Copy to temp directory
            String tempName = jarFile.getName().replace(".jar", "-" + hash.substring(0, 8) + ".jar");
            tempCopy = tempPluginDir.resolve(tempName);
            Files.copy(jarFile.toPath(), tempCopy, StandardCopyOption.REPLACE_EXISTING);

            // Load from temp copy
            PluginInfo pluginInfo = pluginClassLoader.loadPlugin(tempCopy.toFile());

            if (pluginInfo != null) {
                String pluginName = pluginInfo.getName();
                jarHashes.put(pluginName, hash);
                jarToPlugin.put(jarFile.getName(), pluginName);

                // Remove from failed list if it was there
                failedJars.remove(jarFile.getName());

                logger.info("New plugin discovered: {} v{}", pluginName, pluginInfo.getVersion());
            } else {
                // Plugin load returned null - track as failed
                trackFailedJar(jarFile.getName(), hash, "Plugin load returned null");
                cleanupTempFile(tempCopy);
            }

        } catch (Exception e) {
            logger.error("Failed to load new plugin from {}: {}", jarFile.getName(), e.getMessage(), e);
            trackFailedJar(jarFile.getName(), hash, e.getMessage());
            cleanupTempFile(tempCopy);
        }
    }

    /**
     * Clean up a temp file safely.
     */
    private void cleanupTempFile(Path tempCopy) {
        if (tempCopy != null) {
            try {
                Files.deleteIfExists(tempCopy);
            } catch (IOException e) {
                logger.debug("Could not delete temp file: {}", tempCopy);
            }
        }
    }

    /**
     * Track a failed JAR file for retry.
     */
    private void trackFailedJar(String jarFileName, String hash, String reason) {
        FailedJarInfo existing = failedJars.get(jarFileName);
        if (existing != null && existing.hash.equals(hash)) {
            // Same hash, just update time and increment counter
            existing.lastAttemptTime = Instant.now();
            existing.reason = reason;
        } else {
            // New failure or hash changed
            failedJars.put(jarFileName, new FailedJarInfo(hash, Instant.now(), reason));
        }

        FailedJarInfo info = failedJars.get(jarFileName);
        int maxRetries = pluginProperties.getMaxRetryAttempts();

        if (info.attemptCount.get() < maxRetries) {
            logger.warn("JAR '{}' failed to load: {}. Will retry ({}/{} attempts).",
                    jarFileName, reason, info.attemptCount.get(), maxRetries);
        } else {
            logger.warn("JAR '{}' failed to load: {}. Max retries ({}) reached, waiting for JAR update.",
                    jarFileName, reason, maxRetries);
        }
    }

    /**
     * Warn about pending updates for enabled plugins.
     */
    private void warnPendingUpdates() {
        for (Map.Entry<String, PendingUpdate> entry : pendingUpdates.entrySet()) {
            String pluginName = entry.getKey();
            PendingUpdate update = entry.getValue();

            long minutesAgo = Duration.between(update.detectedTime, Instant.now()).toMinutes();

            logger.warn("[HOT-RELOAD] Plugin '{}' has pending update (detected {} min ago). " +
                       "Disable plugin or restart bot to apply.", pluginName, minutesAgo);
        }
    }

    /**
     * Force apply all pending updates (for use during shutdown/restart).
     */
    public void applyAllPendingUpdates() {
        for (Map.Entry<String, PendingUpdate> entry : pendingUpdates.entrySet()) {
            String pluginName = entry.getKey();
            PendingUpdate update = entry.getValue();

            File jarFile = new File(update.jarPath);
            if (jarFile.exists()) {
                applyPluginUpdate(pluginName, jarFile, update.newHash);
            }
        }
    }

    /**
     * Get list of pending updates.
     */
    public Map<String, PendingUpdate> getPendingUpdates() {
        return new HashMap<>(pendingUpdates);
    }

    /**
     * Check if a plugin has pending update.
     */
    public boolean hasPendingUpdate(String pluginName) {
        return pendingUpdates.containsKey(pluginName);
    }

    /**
     * Get list of failed plugins.
     */
    public Map<String, FailedJarInfo> getFailedPlugins() {
        return new HashMap<>(failedJars);
    }

    /**
     * Manually trigger retry for a specific failed plugin.
     */
    public boolean retryFailedPlugin(String jarFileName) {
        FailedJarInfo info = failedJars.get(jarFileName);
        if (info == null) {
            return false;
        }

        // Reset attempt count to allow retry
        info.attemptCount.set(0);
        retryFailedPlugins();
        return true;
    }

    /**
     * Compute SHA-256 hash of a file.
     */
    private String computeFileHash(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream is = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Cleanup temp directory with retry for locked files.
     */
    private void cleanupTempDir() {
        if (tempPluginDir == null || !Files.exists(tempPluginDir)) {
            return;
        }

        logger.info("Cleaning up temp directory: {}", tempPluginDir);

        int maxRetries = 5;
        int retryDelayMs = 200;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                List<Path> pathsToDelete;
                try (var pathStream = Files.walk(tempPluginDir)) {
                    pathsToDelete = pathStream
                        .sorted(Comparator.reverseOrder())
                        .toList();
                }

                List<Path> failedPaths = new ArrayList<>();

                for (Path path : pathsToDelete) {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        failedPaths.add(path);
                        logger.debug("Failed to delete temp file (attempt {}): {}", attempt, path);
                    }
                }

                if (failedPaths.isEmpty()) {
                    logger.info("Temp directory cleaned up successfully");
                    return;
                }

                if (attempt < maxRetries) {
                    logger.debug("Retrying cleanup in {}ms... ({} files remaining)", retryDelayMs, failedPaths.size());
                    Thread.sleep(retryDelayMs);
                    retryDelayMs *= 2; // Exponential backoff
                } else {
                    // Mark files for deletion on exit as last resort
                    for (Path path : failedPaths) {
                        path.toFile().deleteOnExit();
                    }
                    logger.warn("Could not delete {} temp files, marked for deletion on exit", failedPaths.size());
                }

            } catch (IOException e) {
                logger.debug("Failed to walk temp directory: {}", e.getMessage());
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.debug("Cleanup interrupted");
                break;
            }
        }
    }

    /**
     * Pending update record.
     */
    public static class PendingUpdate {
        public final String jarPath;
        public final String newHash;
        public final Instant detectedTime;

        public PendingUpdate(String jarPath, String newHash, Instant detectedTime) {
            this.jarPath = jarPath;
            this.newHash = newHash;
            this.detectedTime = detectedTime;
        }
    }

    /**
     * Failed JAR tracking record with retry support.
     */
    public static class FailedJarInfo {
        public volatile String hash;
        public volatile Instant lastAttemptTime;
        public volatile String reason;
        public final AtomicInteger attemptCount;

        public FailedJarInfo(String hash, Instant lastAttemptTime, String reason) {
            this.hash = hash;
            this.lastAttemptTime = lastAttemptTime;
            this.reason = reason;
            this.attemptCount = new AtomicInteger(1);
        }

        public int getAttemptCount() {
            return attemptCount.get();
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Tracks files that are being written (to detect when upload is complete).
     */
    private static class FileWriteTracker {
        Instant lastModified;
        ScheduledFuture<?> scheduledTask;
    }
}
