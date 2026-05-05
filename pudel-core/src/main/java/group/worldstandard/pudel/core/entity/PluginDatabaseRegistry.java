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
package group.worldstandard.pudel.core.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity for tracking plugin database registrations.
 * <p>
 * Each plugin gets a unique UUID-based prefix assigned on first installation.
 * This ensures isolation between plugins and allows version tracking.
 * The schema name is derived from the dbPrefix (format: "plugin_{shortUuid}").
 */
@jakarta.persistence.Entity
@Table(name = "plugin_database_registry", indexes = {
        @Index(name = "idx_plugin_db_registry_plugin_id", columnList = "plugin_id", unique = true)
})
public class PluginDatabaseRegistry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The plugin identifier (from PluginInfo.name).
     */
    @Column(name = "plugin_id", nullable = false, unique = true, length = 100)
    private String pluginId;

    /**
     * The unique database prefix assigned to this plugin.
     * Format: "p_{shortUuid}_" (e.g., "p_a1b2c3d4_")
     * The schema name is derived from this prefix.
     */
    @Column(name = "db_prefix", nullable = false, unique = true, length = 50)
    private String dbPrefix;

    /**
     * The plugin version when first registered.
     */
    @Column(name = "initial_version", length = 50)
    private String initialVersion;

    /**
     * The current plugin version.
     */
    @Column(name = "current_version", length = 50)
    private String currentVersion;

    /**
     * Current database schema version (for migrations).
     */
    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion = 0;

    /**
     * When the plugin was first registered.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * When the registry was last updated.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Whether the plugin is currently enabled.
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Derives the schema name from the dbPrefix.
     * dbPrefix format: "p_{shortUuid}_" -> schema: "plugin_{shortUuid}"
     *
     * @return the schema name for this plugin
     */
    public String deriveSchemaName() {
        if (dbPrefix == null || dbPrefix.isEmpty()) {
            return "plugin_unknown";
        }
        String shortUuid = dbPrefix.replaceAll("^p_", "").replaceAll("_$", "");
        return "plugin_" + shortUuid;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getDbPrefix() {
        return dbPrefix;
    }

    public void setDbPrefix(String dbPrefix) {
        this.dbPrefix = dbPrefix;
    }

    public String getInitialVersion() {
        return initialVersion;
    }

    public void setInitialVersion(String initialVersion) {
        this.initialVersion = initialVersion;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
