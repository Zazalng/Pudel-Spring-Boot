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
        // Check cache first
        PluginDatabaseManagerImpl cached = managerCache.get(pluginId);
        if (cached != null) {
            return cached;
        }

        // Get or create registry entry
        PluginDatabaseRegistry registry = registryRepository.findByPluginId(pluginId)
                .orElseGet(() -> createRegistryEntry(pluginId, pluginVersion));

        // Update version if changed
        if (!Objects.equals(registry.getCurrentVersion(), pluginVersion)) {
            registry.setCurrentVersion(pluginVersion);
            registryRepository.save(registry);
            logger.info("Updated plugin {} version to {}", pluginId, pluginVersion);
        }

        // Create manager instance
        PluginDatabaseManagerImpl manager = new PluginDatabaseManagerImpl(
                pluginId,
                registry.getDbPrefix(),
                registry,
                registryRepository,
                jdbcTemplate
        );

        managerCache.put(pluginId, manager);
        logger.debug("Created database manager for plugin {} with prefix {}", pluginId, registry.getDbPrefix());

        return manager;
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
     */
    public void removeManager(String pluginId) {
        managerCache.remove(pluginId);
        logger.debug("Removed database manager from cache for plugin {}", pluginId);
    }

    /**
     * Get registry information for a plugin.
     */
    public Optional<PluginDatabaseRegistry> getRegistry(String pluginId) {
        return registryRepository.findByPluginId(pluginId);
    }

    /**
     * List all registered plugins.
     */
    public List<PluginDatabaseRegistry> getAllRegistries() {
        return registryRepository.findAll();
    }

    /**
     * Get the JdbcTemplate for direct operations.
     * Used internally by manager implementations.
     */
    JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
}
