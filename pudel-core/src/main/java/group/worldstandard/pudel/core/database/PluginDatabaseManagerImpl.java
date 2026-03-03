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
package group.worldstandard.pudel.core.database;

import group.worldstandard.pudel.api.database.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import group.worldstandard.pudel.core.entity.PluginDatabaseRegistry;
import group.worldstandard.pudel.core.repository.PluginDatabaseRegistryRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementation of PluginDatabaseManager.
 * <p>
 * Provides isolated database operations for a specific plugin.
 */
public class PluginDatabaseManagerImpl implements PluginDatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(PluginDatabaseManagerImpl.class);

    private final String pluginId;
    private final String prefix;
    private final PluginDatabaseRegistry registry;
    private final PluginDatabaseRegistryRepository registryRepository;
    private final JdbcTemplate jdbcTemplate;

    // Cache repositories: tableName -> repository
    private final Map<String, PluginRepositoryImpl<?>> repositories = new ConcurrentHashMap<>();

    // Key-value store instance
    private PluginKeyValueStoreImpl keyValueStore;

    public PluginDatabaseManagerImpl(String pluginId, String prefix, PluginDatabaseRegistry registry,
                                     PluginDatabaseRegistryRepository registryRepository,
                                     JdbcTemplate jdbcTemplate) {
        this.pluginId = pluginId;
        this.prefix = prefix;
        this.registry = registry;
        this.registryRepository = registryRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    @Transactional
    public boolean createTable(TableSchema schema) {
        String fullTableName = prefix + schema.getTableName();

        // Check if table already exists
        if (tableExistsInternal(fullTableName)) {
            logger.debug("Table {} already exists for plugin {}", fullTableName, pluginId);
            return false;
        }

        // Build CREATE TABLE SQL
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(fullTableName).append(" (\n");
        sql.append("    id BIGSERIAL PRIMARY KEY,\n");

        // Add user-defined columns
        for (TableSchema.ColumnDefinition col : schema.getColumns()) {
            sql.append("    ").append(col.name()).append(" ");
            sql.append(col.type().getSqlType(col.size()));
            if (!col.nullable()) {
                sql.append(" NOT NULL");
            }
            if (col.defaultValue() != null) {
                sql.append(" DEFAULT ").append(col.defaultValue());
            }
            sql.append(",\n");
        }

        // Add timestamp columns
        sql.append("    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,\n");
        sql.append("    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP\n");
        sql.append(")");

        jdbcTemplate.execute(sql.toString());
        logger.info("Created table {} for plugin {}", fullTableName, pluginId);

        // Create indexes
        for (TableSchema.IndexDefinition idx : schema.getIndexes()) {
            createIndexInternal(fullTableName, idx.unique(), idx.columns().toArray(new String[0]));
        }

        return true;
    }

    @Override
    public boolean tableExists(String tableName) {
        return tableExistsInternal(prefix + tableName);
    }

    private boolean tableExistsInternal(String fullTableName) {
        String sql = "SELECT EXISTS (SELECT FROM pg_tables WHERE tablename = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, fullTableName.toLowerCase());
        return Boolean.TRUE.equals(exists);
    }

    @Override
    @Transactional
    public boolean dropTable(String tableName) {
        String fullTableName = prefix + tableName;

        if (!tableExistsInternal(fullTableName)) {
            return false;
        }

        jdbcTemplate.execute("DROP TABLE IF EXISTS " + fullTableName + " CASCADE");
        repositories.remove(tableName);
        logger.info("Dropped table {} for plugin {}", fullTableName, pluginId);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> PluginRepository<T> getRepository(String tableName, Class<T> entityClass) {
        return (PluginRepository<T>) repositories.computeIfAbsent(tableName,
                name -> new PluginRepositoryImpl<>(this, name, entityClass, jdbcTemplate));
    }

    @Override
    public PluginKeyValueStore getKeyValueStore() {
        if (keyValueStore == null) {
            // Ensure KV table exists
            ensureKeyValueTable();
            keyValueStore = new PluginKeyValueStoreImpl(this, jdbcTemplate);
        }
        return keyValueStore;
    }

    private void ensureKeyValueTable() {
        String fullTableName = prefix + "kv_store";
        if (!tableExistsInternal(fullTableName)) {
            String sql = "CREATE TABLE " + fullTableName + " (\n" +
                    "    key VARCHAR(500) PRIMARY KEY,\n" +
                    "    value TEXT,\n" +
                    "    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                    "    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP\n" +
                    ")";
            jdbcTemplate.execute(sql);
            logger.info("Created key-value store table for plugin {}", pluginId);
        }
    }

    @Override
    public List<String> listTables() {
        String sql = "SELECT tablename FROM pg_tables WHERE tablename LIKE ?";
        List<String> tables = jdbcTemplate.queryForList(sql, String.class, prefix + "%");
        return tables.stream()
                .map(t -> t.substring(prefix.length()))
                .collect(Collectors.toList());
    }

    @Override
    public int getSchemaVersion() {
        return registry.getSchemaVersion();
    }

    @Override
    @Transactional
    public void setSchemaVersion(int version) {
        registry.setSchemaVersion(version);
        registryRepository.save(registry);
        logger.debug("Set schema version to {} for plugin {}", version, pluginId);
    }

    @Override
    @Transactional
    public boolean migrate(int targetVersion, PluginMigration migration) {
        int currentVersion = getSchemaVersion();
        if (currentVersion >= targetVersion) {
            logger.debug("Plugin {} already at schema version {}, skipping migration to {}",
                    pluginId, currentVersion, targetVersion);
            return false;
        }

        logger.info("Running migration for plugin {} from version {} to {}",
                pluginId, currentVersion, targetVersion);

        try {
            MigrationHelperImpl helper = new MigrationHelperImpl(this, jdbcTemplate);
            migration.migrate(helper);
            setSchemaVersion(targetVersion);
            logger.info("Migration completed for plugin {} to version {}", pluginId, targetVersion);
            return true;
        } catch (Exception e) {
            logger.error("Migration failed for plugin {}: {}", pluginId, e.getMessage(), e);
            throw new RuntimeException("Migration failed for plugin " + pluginId, e);
        }
    }

    @Override
    public DatabaseStats getStats() {
        List<String> tables = listTables();
        long totalRows = 0;

        for (String table : tables) {
            try {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + prefix + table, Long.class);
                totalRows += (count != null ? count : 0);
            } catch (Exception e) {
                logger.debug("Could not count rows in {}: {}", table, e.getMessage());
            }
        }

        return new DatabaseStats(
                pluginId,
                prefix,
                tables.size(),
                totalRows,
                getSchemaVersion()
        );
    }

    /**
     * Get the full table name with prefix.
     */
    String getFullTableName(String tableName) {
        return prefix + tableName;
    }

    /**
     * Create an index on a table.
     */
    void createIndexInternal(String fullTableName, boolean unique, String... columns) {
        String indexName = "idx_" + fullTableName + "_" + String.join("_", columns);
        String sql = String.format("CREATE %sINDEX IF NOT EXISTS %s ON %s (%s)",
                unique ? "UNIQUE " : "",
                indexName,
                fullTableName,
                String.join(", ", columns));
        jdbcTemplate.execute(sql);
        logger.debug("Created index {} on {}", indexName, fullTableName);
    }
}
