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
package group.worldstandard.pudel.core.database;

import group.worldstandard.pudel.api.database.PluginDatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import group.worldstandard.pudel.core.entity.PluginDatabaseRegistry;
import group.worldstandard.pudel.core.repository.PluginDatabaseRegistryRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing plugin database namespaces.
 * <p>
 * Each plugin gets a unique prefix assigned on first registration.
 * This service handles:
 * - Prefix assignment and tracking
 * - Creating PluginDatabaseManager instances for plugins
 * - Schema version management
 */
@Service
public class PluginDatabaseService {

    private static final Logger logger = LoggerFactory.getLogger(PluginDatabaseService.class);

    private final PluginDatabaseRegistryRepository registryRepository;
    private final JdbcTemplate jdbcTemplate;

    // Cache of plugin managers: pluginId -> manager
    private final Map<String, PluginDatabaseManagerImpl> managerCache = new ConcurrentHashMap<>();

    public PluginDatabaseService(PluginDatabaseRegistryRepository registryRepository, JdbcTemplate jdbcTemplate) {
        this.registryRepository = registryRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Get or create a database manager for a plugin.
     *
     * @param pluginId the plugin identifier
     * @param pluginVersion the plugin version
     * @return the database manager
     */
    @Transactional
    public PluginDatabaseManager getManagerForPlugin(String pluginId, String pluginVersion) {
        // Normalize plugin ID to prevent case/whitespace issues causing duplicate registrations
        String normalizedId = normalizePluginId(pluginId);

        // Check cache first
        PluginDatabaseManagerImpl cached = managerCache.get(normalizedId);
        if (cached != null) {
            logger.debug("Returning cached database manager for plugin {}", normalizedId);
            return cached;
        }

        // Get or create registry entry
        PluginDatabaseRegistry registry = registryRepository.findByPluginId(normalizedId)
                .orElseGet(() -> createRegistryEntry(normalizedId, pluginVersion));

        // Log the prefix being used - helps debug issues
        logger.info("Plugin {} using database prefix: {}", normalizedId, registry.getDbPrefix());

        // Update version if changed
        if (!Objects.equals(registry.getCurrentVersion(), pluginVersion)) {
            registry.setCurrentVersion(pluginVersion);
            registryRepository.save(registry);
            logger.info("Updated plugin {} version to {}", normalizedId, pluginVersion);
        }

        // Create manager instance
        PluginDatabaseManagerImpl manager = new PluginDatabaseManagerImpl(
                normalizedId,
                registry.getDbPrefix(),
                registry,
                registryRepository,
                jdbcTemplate
        );

        managerCache.put(normalizedId, manager);
        logger.debug("Created database manager for plugin {} with prefix {}", normalizedId, registry.getDbPrefix());

        return manager;
    }

    /**
     * Normalize plugin ID to ensure consistent lookups.
     * <p>
     * This prevents issues where plugins with names like "My Plugin" vs "my plugin"
     * or "Plugin's Name" vs "Plugin's Name" (different apostrophe chars) would get
     * different database prefixes.
     *
     * @param pluginId the raw plugin ID
     * @return normalized plugin ID
     */
    private String normalizePluginId(String pluginId) {
        if (pluginId == null) {
            return "unknown";
        }

        // Convert to lowercase
        String normalized = pluginId.toLowerCase();

        // Replace common problematic characters with standard versions
        // Using Unicode escapes to avoid encoding issues
        normalized = normalized
                .replace("\u2018", "'")  // Left single curly quote to straight
                .replace("\u2019", "'")  // Right single curly quote (apostrophe) to straight
                .replace("\u201C", "\"") // Left double curly quote to straight
                .replace("\u201D", "\""); // Right double curly quote to straight

        // Trim whitespace
        normalized = normalized.trim();

        // Replace multiple spaces with single space
        normalized = normalized.replaceAll("\\s+", " ");

        return normalized;
    }

    /**
     * Create a new registry entry for a plugin.
     */
    private PluginDatabaseRegistry createRegistryEntry(String pluginId, String pluginVersion) {
        // Generate unique prefix
        String prefix = generateUniquePrefix(pluginId);

        PluginDatabaseRegistry registry = new PluginDatabaseRegistry();
        registry.setPluginId(pluginId);
        registry.setDbPrefix(prefix);
        registry.setInitialVersion(pluginVersion);
        registry.setCurrentVersion(pluginVersion);
        registry.setSchemaVersion(0);
        registry.setEnabled(true);

        registry = registryRepository.save(registry);
        logger.info("Registered new plugin database: {} with prefix {}", pluginId, prefix);

        return registry;
    }

    /**
     * Generate a unique database prefix for a plugin.
     * Format: "p_{shortId}_" where shortId is 8 chars
     */
    private String generateUniquePrefix(String pluginId) {
        // Generate a short unique ID
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String shortId = uuid.substring(0, 8);
        String prefix = "p_" + shortId + "_";

        // Ensure uniqueness (very unlikely to collide, but be safe)
        int attempts = 0;
        while (registryRepository.existsByDbPrefix(prefix) && attempts < 10) {
            uuid = UUID.randomUUID().toString().replace("-", "");
            shortId = uuid.substring(0, 8);
            prefix = "p_" + shortId + "_";
            attempts++;
        }

        if (attempts >= 10) {
            throw new IllegalStateException("Failed to generate unique prefix for plugin: " + pluginId);
        }

        return prefix;
    }

    /**
     * Remove a manager from cache when plugin is unloaded.
     * The database tables and registry entry are preserved for data persistence.
     */
    public void removeManager(String pluginId) {
        String normalizedId = normalizePluginId(pluginId);
        managerCache.remove(normalizedId);
        logger.debug("Removed database manager from cache for plugin {}", normalizedId);
    }

    /**
     * Get registry information for a plugin.
     */
    public Optional<PluginDatabaseRegistry> getRegistry(String pluginId) {
        return registryRepository.findByPluginId(normalizePluginId(pluginId));
    }

    /**
     * List all registered plugins.
     */
    public List<PluginDatabaseRegistry> getAllRegistries() {
        return registryRepository.findAll();
    }

    /**
     * Migrate existing plugin database registrations to use normalized IDs.
     * <p>
     * This should be called once during startup to fix any existing registrations
     * that were created before normalization was implemented.
     * <p>
     * Also attempts to find and merge duplicate registrations for the same plugin
     * (e.g., "My Plugin" and "my plugin" would be merged, keeping the older one).
     */
    @Transactional
    public void migrateToNormalizedIds() {
        logger.info("Checking for plugin database registrations that need migration...");

        List<PluginDatabaseRegistry> allRegistries = registryRepository.findAll();
        Map<String, PluginDatabaseRegistry> normalizedMap = new HashMap<>();
        List<PluginDatabaseRegistry> toDelete = new ArrayList<>();

        for (PluginDatabaseRegistry registry : allRegistries) {
            String originalId = registry.getPluginId();
            String normalizedId = normalizePluginId(originalId);

            if (!originalId.equals(normalizedId)) {
                // This registration needs normalization
                logger.info("Found registration needing normalization: '{}' -> '{}'", originalId, normalizedId);

                // Check if a normalized version already exists
                if (normalizedMap.containsKey(normalizedId)) {
                    // Duplicate found - keep the older one (lower ID = created first = has data)
                    PluginDatabaseRegistry existing = normalizedMap.get(normalizedId);
                    if (registry.getId() < existing.getId()) {
                        // Current one is older, update its ID and mark existing for deletion
                        toDelete.add(existing);
                        registry.setPluginId(normalizedId);
                        registryRepository.save(registry);
                        normalizedMap.put(normalizedId, registry);
                        logger.info("Migrated registration and will delete duplicate: {} (prefix: {})",
                                existing.getPluginId(), existing.getDbPrefix());
                    } else {
                        // Existing one is older, mark current for deletion
                        toDelete.add(registry);
                        logger.info("Will delete duplicate registration: {} (prefix: {})",
                                originalId, registry.getDbPrefix());
                    }
                } else {
                    // No duplicate, just normalize the ID
                    registry.setPluginId(normalizedId);
                    registryRepository.save(registry);
                    normalizedMap.put(normalizedId, registry);
                    logger.info("Normalized plugin ID: '{}' -> '{}' (prefix: {})",
                            originalId, normalizedId, registry.getDbPrefix());
                }
            } else {
                // Already normalized
                normalizedMap.put(normalizedId, registry);
            }
        }

        // Delete duplicates
        for (PluginDatabaseRegistry duplicate : toDelete) {
            logger.warn("Deleting duplicate plugin database registration: {} (prefix: {}). " +
                    "Note: Database tables with this prefix will become orphaned.",
                    duplicate.getPluginId(), duplicate.getDbPrefix());
            registryRepository.delete(duplicate);
        }

        if (toDelete.isEmpty()) {
            logger.info("No plugin database migrations needed");
        } else {
            logger.info("Migrated {} plugin database registrations", toDelete.size());
        }
    }

    /**
     * Get the JdbcTemplate for direct operations.
     * Used internally by manager implementations.
     */
    JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
}
