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
package group.worldstandard.pudel.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import group.worldstandard.pudel.core.entity.PluginDatabaseRegistry;

import java.util.Optional;

/**
 * Repository for plugin database registry entries.
 */
@Repository
public interface PluginDatabaseRegistryRepository extends JpaRepository<PluginDatabaseRegistry, Long> {

    /**
     * Find a registry entry by plugin ID.
     *
     * @param pluginId the plugin identifier
     * @return the registry entry if found
     */
    Optional<PluginDatabaseRegistry> findByPluginId(String pluginId);

    /**
     * Find a registry entry by database prefix.
     *
     * @param dbPrefix the database prefix
     * @return the registry entry if found
     */
    Optional<PluginDatabaseRegistry> findByDbPrefix(String dbPrefix);

    /**
     * Check if a plugin ID is already registered.
     *
     * @param pluginId the plugin identifier
     * @return true if registered
     */
    boolean existsByPluginId(String pluginId);

    /**
     * Check if a database prefix is already in use.
     *
     * @param dbPrefix the database prefix
     * @return true if in use
     */
    boolean existsByDbPrefix(String dbPrefix);
}
