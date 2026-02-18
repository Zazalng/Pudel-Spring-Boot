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
package group.worldstandard.pudel.core.plugin;

import group.worldstandard.pudel.api.PluginInfo;
import group.worldstandard.pudel.api.PudelPlugin;
import group.worldstandard.pudel.api.annotation.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Loads and manages plugins from JAR files.
 * <p>
 * Supports both legacy {@link PudelPlugin} interface (deprecated) and
 * new annotation-based plugins using {@link Plugin}.
 * <p>
 * Features:
 * <ul>
 *   <li>Hot-reload detection via file hash monitoring</li>
 *   <li>Annotation-based plugin discovery</li>
 *   <li>Graceful class loader cleanup</li>
 *   <li>File watcher for plugin updates</li>
 * </ul>
 */
@SuppressWarnings({"deprecation", "removal"}) // Supporting legacy PudelPlugin for backward compatibility
public class PluginClassLoader {

    private static final Logger logger = LoggerFactory.getLogger(PluginClassLoader.class);

    private final File pluginsDirectory;

    // Plugin instances: name -> instance
    private final Map<String, Object> loadedPlugins = new ConcurrentHashMap<>();

    // Plugin info: name -> PluginInfo
    private final Map<String, PluginInfo> pluginInfos = new ConcurrentHashMap<>();

    // Class loaders: name -> URLClassLoader
    private final Map<String, URLClassLoader> classLoaders = new ConcurrentHashMap<>();

    // JAR file mapping: name -> File
    private final Map<String, File> jarFiles = new ConcurrentHashMap<>();

    // File hashes for hot-reload detection: name -> hash
    private final Map<String, String> fileHashes = new ConcurrentHashMap<>();

    // File watcher for plugin directory
    private WatchService watchService;
    private Thread watcherThread;
    private volatile boolean watcherRunning = false;

    // Debouncing for file events: filename -> last event timestamp
    private final Map<String, Long> lastEventTime = new ConcurrentHashMap<>();
    // Debounce window in milliseconds (ignore duplicate events within this window)
    private static final long DEBOUNCE_WINDOW_MS = 2000;
    // Track files currently being processed
    private final Set<String> processingFiles = ConcurrentHashMap.newKeySet();

    public PluginClassLoader(File pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory;
        if (!pluginsDirectory.exists()) {
            if (pluginsDirectory.mkdirs()) {
                logger.info("Created plugins directory: {}", pluginsDirectory.getAbsolutePath());
            }
        }
    }

    /**
     * Load a plugin from a JAR file.
     * Supports both @Plugin annotated classes and legacy PudelPlugin interface.
     *
     * @param jarFile the JAR file to load
     * @return the plugin info or null if failed
     */
    public PluginInfo loadPlugin(File jarFile) {
        if (!jarFile.exists() || !jarFile.getName().endsWith(".jar")) {
            logger.warn("Invalid JAR file: {}", jarFile.getAbsolutePath());
            return null;
        }

        URLClassLoader classLoader = null;
        String pluginName = null;

        try {
            // Calculate file hash for hot-reload detection
            String fileHash = calculateFileHash(jarFile);

            // Find main class
            String mainClassName = findMainClass(jarFile);
            if (mainClassName == null) {
                logger.error("Could not find plugin class in {}", jarFile.getName());
                return null;
            }

            // Create ClassLoader
            classLoader = new URLClassLoader(
                    new URL[]{jarFile.toURI().toURL()},
                    Thread.currentThread().getContextClassLoader()
            );

            // Load main class
            Class<?> mainClass = classLoader.loadClass(mainClassName);

            // Extract plugin info and create instance
            PluginInfo info;
            Object instance;

            // Check for @Plugin annotation first (new way)
            Plugin pluginAnnotation = mainClass.getAnnotation(Plugin.class);
            if (pluginAnnotation != null) {
                info = new PluginInfo(
                        pluginAnnotation.name(),
                        pluginAnnotation.version(),
                        pluginAnnotation.author(),
                        pluginAnnotation.description()
                );
                instance = mainClass.getDeclaredConstructor().newInstance();
                pluginName = info.getName();

                logger.info("Loaded annotation-based plugin: {} v{}", pluginName, info.getVersion());
            }
            // Fall back to legacy PudelPlugin interface (deprecated)
            else if (PudelPlugin.class.isAssignableFrom(mainClass)) {
                logger.warn("Plugin {} uses deprecated PudelPlugin interface. " +
                           "Migrate to @Plugin annotation.", jarFile.getName());

                PudelPlugin legacyPlugin = (PudelPlugin) mainClass.getDeclaredConstructor().newInstance();
                info = legacyPlugin.getPluginInfo();
                instance = legacyPlugin;
                pluginName = info.getName();
            }
            else {
                logger.error("Class {} is not a valid plugin (no @Plugin or PudelPlugin)", mainClassName);
                classLoader.close();
                return null;
            }

            // Check if already loaded
            if (loadedPlugins.containsKey(pluginName)) {
                logger.info("Plugin {} is already loaded. Unloading old version before loading new one.", pluginName);
                // Close the new class loader first since we need to unload the old one
                classLoader.close();
                classLoader = null;

                // Unload the old version
                unloadPlugin(pluginName);

                // Reload with fresh class loader
                classLoader = new URLClassLoader(
                        new URL[]{jarFile.toURI().toURL()},
                        Thread.currentThread().getContextClassLoader()
                );
                mainClass = classLoader.loadClass(mainClassName);

                // Re-extract info and create instance for new version
                pluginAnnotation = mainClass.getAnnotation(Plugin.class);
                if (pluginAnnotation != null) {
                    info = new PluginInfo(
                            pluginAnnotation.name(),
                            pluginAnnotation.version(),
                            pluginAnnotation.author(),
                            pluginAnnotation.description()
                    );
                    instance = mainClass.getDeclaredConstructor().newInstance();
                    logger.info("Reloaded annotation-based plugin: {} v{}", pluginName, info.getVersion());
                } else if (PudelPlugin.class.isAssignableFrom(mainClass)) {
                    PudelPlugin legacyPlugin = (PudelPlugin) mainClass.getDeclaredConstructor().newInstance();
                    info = legacyPlugin.getPluginInfo();
                    instance = legacyPlugin;
                } else {
                    logger.error("Class {} is not a valid plugin after reload", mainClassName);
                    classLoader.close();
                    return null;
                }
            }

            // Register plugin
            classLoaders.put(pluginName, classLoader);
            loadedPlugins.put(pluginName, instance);
            pluginInfos.put(pluginName, info);
            jarFiles.put(pluginName, jarFile);
            fileHashes.put(pluginName, fileHash);

            logger.info("Successfully loaded plugin: {} v{} by {}",
                       pluginName, info.getVersion(), info.getAuthor());
            return info;

        } catch (Exception e) {
            logger.error("Failed to load plugin from {}: {}", jarFile.getName(), e.getMessage(), e);

            if (pluginName != null) {
                unloadPlugin(pluginName);
            } else if (classLoader != null) {
                try {
                    classLoader.close();
                } catch (IOException ioEx) {
                    logger.error("Failed to close classloader after failed load", ioEx);
                }
            }
            return null;
        }
    }

    /**
     * Find the main plugin class in a JAR file.
     * Checks: MANIFEST.MF Plugin-Main → plugin.yml main: → @Plugin annotated class
     */
    private String findMainClass(File jarFile) {
        logger.debug("Searching for plugin main class in: {}", jarFile.getName());

        try (JarFile jar = new JarFile(jarFile)) {
            // 1. Check MANIFEST.MF for Plugin-Main
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String pluginMain = manifest.getMainAttributes().getValue("Plugin-Main");
                if (pluginMain != null && !pluginMain.isEmpty()) {
                    logger.debug("Found Plugin-Main in manifest: {}", pluginMain);
                    return pluginMain;
                }
            }

            // 2. Check plugin.yml
            JarEntry pluginYml = jar.getJarEntry("plugin.yml");
            if (pluginYml != null) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(jar.getInputStream(pluginYml)))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("main:")) {
                            String mainClass = line.substring(5).trim();
                            logger.debug("Found main class in plugin.yml: {}", mainClass);
                            return mainClass;
                        }
                    }
                }
            }

            // 3. Scan for @Plugin annotated classes
            URLClassLoader tempLoader = new URLClassLoader(
                    new URL[]{jarFile.toURI().toURL()},
                    Thread.currentThread().getContextClassLoader()
            );

            try {
                for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                    JarEntry entry = entries.nextElement();

                    if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
                        String className = entry.getName()
                                .replace("/", ".")
                                .replace(".class", "");

                        try {
                            Class<?> clazz = tempLoader.loadClass(className);
                            if (clazz.isAnnotationPresent(Plugin.class)) {
                                logger.debug("Found @Plugin annotated class: {}", className);
                                tempLoader.close();
                                return className;
                            }
                        } catch (ClassNotFoundException | NoClassDefFoundError e) {
                            // Skip classes that can't be loaded
                        }
                    }
                }
            } finally {
                tempLoader.close();
            }

            // 4. Fall back to naming patterns
            List<String> candidates = new ArrayList<>();
            for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && !entry.getName().contains("$")) {
                    String className = entry.getName().replace("/", ".").replace(".class", "");
                    String simpleName = className.substring(className.lastIndexOf('.') + 1);

                    if (simpleName.endsWith("Plugin") || simpleName.endsWith("Bundle") ||
                        simpleName.equals("Main") || simpleName.equals("PluginMain")) {
                        candidates.add(className);
                    }
                }
            }

            if (!candidates.isEmpty()) {
                // Prefer Bundle/Default names
                for (String className : candidates) {
                    if (className.contains("Bundle") || className.contains("Default")) {
                        return className;
                    }
                }
                return candidates.getFirst();
            }

            logger.warn("No plugin class found in JAR: {}", jarFile.getName());
        } catch (IOException e) {
            logger.error("Error reading JAR file: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * Calculate SHA-256 hash of a file for hot-reload detection.
     */
    private String calculateFileHash(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(file.toPath());
            byte[] hashBytes = digest.digest(fileBytes);

            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Failed to calculate file hash: {}", e.getMessage());
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Check if a plugin JAR file has been updated (for hot-reload).
     */
    public boolean hasPluginUpdated(String pluginName) {
        File jarFile = jarFiles.get(pluginName);
        String oldHash = fileHashes.get(pluginName);

        if (jarFile == null || oldHash == null || !jarFile.exists()) {
            return false;
        }

        String newHash = calculateFileHash(jarFile);
        return !oldHash.equals(newHash);
    }

    /**
     * Find plugin name by JAR filename.
     *
     * @param jarFileName the JAR file name (e.g., "my-plugin-1.0.jar")
     * @return the plugin name if found, null otherwise
     */
    public String findPluginNameByJarFile(String jarFileName) {
        for (Map.Entry<String, File> entry : jarFiles.entrySet()) {
            if (entry.getValue().getName().equals(jarFileName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Unload a plugin and release resources.
     */
    public void unloadPlugin(String pluginName) {
        loadedPlugins.remove(pluginName);
        pluginInfos.remove(pluginName);
        jarFiles.remove(pluginName);
        fileHashes.remove(pluginName);

        URLClassLoader classLoader = classLoaders.remove(pluginName);
        if (classLoader != null) {
            try {
                classLoader.close();

                // Force garbage collection to release file handles
                System.gc();

                logger.info("Plugin unloaded: {}", pluginName);
            } catch (IOException e) {
                logger.error("Error closing class loader for plugin {}: {}", pluginName, e.getMessage());
            }
        }
    }

    /**
     * Force unload a plugin after shutdown timeout.
     * Used when @OnShutdown returns false or throws an exception.
     */
    public void forceUnloadPlugin(String pluginName) {
        logger.warn("Force unloading plugin: {}", pluginName);
        unloadPlugin(pluginName);

        // Additional cleanup attempts
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Start watching the plugins directory for changes.
     */
    public void startWatcher(PluginUpdateCallback callback) {
        if (watcherRunning) {
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();
            pluginsDirectory.toPath().register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            watcherRunning = true;
            watcherThread = new Thread(() -> {
                logger.info("Plugin directory watcher started");

                while (watcherRunning) {
                    try {
                        WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                        if (key == null) continue;

                        for (WatchEvent<?> event : key.pollEvents()) {
                            Path filename = (Path) event.context();
                            String fileNameStr = filename.toString();

                            if (fileNameStr.endsWith(".jar")) {
                                File jarFile = new File(pluginsDirectory, fileNameStr);
                                long currentTime = System.currentTimeMillis();

                                // Check if we should debounce this event
                                Long lastTime = lastEventTime.get(fileNameStr);
                                if (lastTime != null && (currentTime - lastTime) < DEBOUNCE_WINDOW_MS) {
                                    logger.debug("Debouncing event for {}, too soon after last event", fileNameStr);
                                    continue;
                                }

                                // Check if file is currently being processed
                                if (processingFiles.contains(fileNameStr)) {
                                    logger.debug("File {} is currently being processed, skipping event", fileNameStr);
                                    continue;
                                }

                                // Update last event time
                                lastEventTime.put(fileNameStr, currentTime);

                                // Mark as processing
                                processingFiles.add(fileNameStr);

                                try {
                                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                                        // For CREATE, wait a bit to ensure file is fully written
                                        Thread.sleep(500);
                                        if (jarFile.exists() && jarFile.canRead()) {
                                            logger.info("New plugin detected: {}", fileNameStr);
                                            callback.onPluginAdded(jarFile);
                                        }
                                    } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                                        // Only handle MODIFY if plugin is already loaded (true update)
                                        String pluginName = findPluginNameByJarFile(fileNameStr);
                                        if (pluginName != null) {
                                            logger.info("Plugin modified: {}", fileNameStr);
                                            callback.onPluginModified(jarFile);
                                        } else {
                                            // Plugin not loaded yet, CREATE handler should handle it
                                            logger.debug("Ignoring MODIFY for unloaded plugin: {}", fileNameStr);
                                        }
                                    } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                                        logger.info("Plugin removed: {}", fileNameStr);
                                        callback.onPluginRemoved(fileNameStr);
                                        // Clean up tracking
                                        lastEventTime.remove(fileNameStr);
                                    }
                                } finally {
                                    // Clear processing flag after a delay to prevent immediate re-processing
                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(DEBOUNCE_WINDOW_MS);
                                        } catch (InterruptedException ignored) {
                                            Thread.currentThread().interrupt();
                                        }
                                        processingFiles.remove(fileNameStr);
                                    }).start();
                                }
                            }
                        }
                        key.reset();
                    } catch (ClosedWatchServiceException e) {
                        // Watch service was closed, exit gracefully
                        logger.debug("Watch service closed, stopping watcher");
                        break;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        logger.error("Error in plugin watcher: {}", e.getMessage(), e);
                    }
                }

                logger.info("Plugin directory watcher stopped");
            }, "plugin-watcher");

            watcherThread.setDaemon(true);
            watcherThread.start();

        } catch (IOException e) {
            logger.error("Failed to start plugin watcher: {}", e.getMessage());
        }
    }

    /**
     * Stop watching the plugins directory.
     */
    public void stopWatcher() {
        watcherRunning = false;

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.error("Error closing watch service: {}", e.getMessage());
            }
        }

        if (watcherThread != null) {
            watcherThread.interrupt();
        }
    }

    /**
     * Close all class loaders. Call during shutdown.
     */
    public void closeAllClassLoaders() {
        logger.info("Closing all plugin class loaders...");
        stopWatcher();

        List<String> pluginNames = new ArrayList<>(classLoaders.keySet());
        for (String pluginName : pluginNames) {
            unloadPlugin(pluginName);
        }

        logger.info("All plugin class loaders closed");
    }

    // Getters

    public Object getPluginInstance(String pluginName) {
        return loadedPlugins.get(pluginName);
    }

    public PluginInfo getPluginInfo(String pluginName) {
        return pluginInfos.get(pluginName);
    }

    @Deprecated
    public PudelPlugin getPlugin(String pluginName) {
        Object instance = loadedPlugins.get(pluginName);
        return instance instanceof PudelPlugin ? (PudelPlugin) instance : null;
    }

    public Map<String, Object> getAllPlugins() {
        return new HashMap<>(loadedPlugins);
    }

    public boolean isPluginLoaded(String pluginName) {
        return loadedPlugins.containsKey(pluginName);
    }

    public File getPluginsDirectory() {
        return pluginsDirectory;
    }

    /**
     * Callback interface for plugin directory watcher.
     */
    public interface PluginUpdateCallback {
        void onPluginAdded(File jarFile);
        void onPluginModified(File jarFile);
        void onPluginRemoved(String jarFileName);
    }
}

