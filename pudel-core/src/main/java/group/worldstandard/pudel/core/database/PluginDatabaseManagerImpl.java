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

/**
 * Implementation of {@link PluginDatabaseManager} that provides database management
 * capabilities for plugins including table creation, schema migration, and data access.
 * <p>
 * This class manages plugin-specific database operations such as creating and dropping tables,
 * managing schema versions, and providing repository access for entities. It uses a prefix-based
 * naming strategy to isolate plugin data and ensures thread-safe operations through concurrent
 * data structures.
 * <p>
 * The manager also provides a key-value store implementation for simple data storage needs
 * and maintains statistics about database usage and schema state.
 */
public class PluginDatabaseManagerImpl implements PluginDatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(PluginDatabaseManagerImpl.class);

    private final String pluginId;
    private final String schemaName;
    private final PluginDatabaseRegistry registry;
    private final PluginDatabaseRegistryRepository registryRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * A thread-safe cache mapping table names to their corresponding repository implementations.
     * This map stores repositories that have been created for specific tables, allowing for efficient
     * retrieval and reuse of repository instances. The key is the table name string, and the value
     * is the PluginRepositoryImpl instance associated with that table.
     */
    private final Map<String, PluginRepositoryImpl<?>> repositories = new ConcurrentHashMap<>();

    /**
     * Instance of the key-value store used by this database manager.
     * Provides methods to store, retrieve, and manage key-value pairs
     * within a dedicated table in the plugin's database schema.
     */
    private PluginKeyValueStoreImpl keyValueStore;

    /**
     * Constructs a new PluginDatabaseManagerImpl with the specified configuration.
     *
     * @param pluginId the unique identifier of the plugin
     * @param schemaName the database schema name for this plugin (e.g., "plugin_myplugin")
     * @param registry the plugin database registry entry containing metadata
     * @param registryRepository the repository for accessing plugin database registry data
     * @param jdbcTemplate the JDBC template for executing database operations
     */
    public PluginDatabaseManagerImpl(String pluginId, String schemaName, PluginDatabaseRegistry registry,
                                     PluginDatabaseRegistryRepository registryRepository,
                                     JdbcTemplate jdbcTemplate) {
        this.pluginId = pluginId;
        this.schemaName = schemaName;
        this.registry = registry;
        this.registryRepository = registryRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the unique identifier of the plugin.
     *
     * @return the plugin ID as a String
     */
    @Override
    public String getPluginId() {
        return pluginId;
    }

    /**
     * Migrates existing tables from public schema to this plugin's schema.
     * This handles the migration from the old prefix-based system (tables in public schema)
     * to the new schema-based system.
     *
     * @return number of tables migrated
     */
    public int migrateTablesFromPublicSchema() {
        int migratedCount = 0;

        try {
            // Ensure plugin schema exists
            ensureSchemaExists();

            // Find tables in public schema that belong to this plugin
            // Old pattern was: p_{uuid}_{tableName} in public schema
            String findTablesSql = """
                SELECT tablename FROM pg_tables
                WHERE schemaname = 'public'
                AND tablename LIKE ?
                """;

            // The old prefix was something like "p_xxxx_" - we need to find tables that might belong to this plugin
            // Since we don't have the old prefix readily available, we'll check the registry
            String oldPrefix = registry.getDbPrefix();
            if (oldPrefix == null || oldPrefix.isEmpty()) {
                logger.debug("No old prefix found for plugin {}, skipping migration", pluginId);
                return 0;
            }

            List<String> oldTables = jdbcTemplate.queryForList(
                findTablesSql, String.class, oldPrefix + "%");

            for (String oldTableName : oldTables) {
                // Extract the actual table name (remove the prefix)
                String newTableName = oldTableName.substring(oldPrefix.length());
                String newFullTableName = getFullTableName(newTableName);

                try {
                    // Move table from public schema to plugin schema with new name
                    String renameSql = String.format(
                        "ALTER TABLE public.%s RENAME TO %s",
                        oldTableName, newFullTableName
                    );
                    jdbcTemplate.execute(renameSql);
                    migratedCount++;
                    logger.info("Migrated table public.{} to {}", oldTableName, newFullTableName);
                } catch (Exception e) {
                    logger.error("Failed to migrate table {}: {}", oldTableName, e.getMessage());
                }
            }

            if (migratedCount > 0) {
                logger.info("Successfully migrated {} tables to schema {} for plugin {}",
                    migratedCount, schemaName, pluginId);
            }

        } catch (Exception e) {
            logger.error("Error during table migration for plugin {}: {}", pluginId, e.getMessage(), e);
        }

        return migratedCount;
    }

    /**
     * Ensures the plugin's database schema exists.
     * If the schema doesn't exist, it will be created.
     */
    private void ensureSchemaExists() {
        try {
            // Check if schema exists
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Integer.class,
                schemaName
            );

            if (exists != null && exists == 0) {
                jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
                logger.info("Created schema: {}", schemaName);
            }
        } catch (Exception e) {
            logger.error("Error ensuring schema {} exists: {}", schemaName, e.getMessage(), e);
        }
    }

    /**
     * Returns the database schema name associated with this plugin.
     *
     * @return the schema name (e.g., "plugin_myplugin")
     */
    @Override
    public String getSchemaName() {
        return schemaName;
    }


    /**
     * Creates a new database table based on the provided schema definition.
     * The table will be created in the plugin's schema (e.g., "plugin_myplugin.tablename").
     * The table will include an auto-incrementing primary key column named 'id',
     * and additional columns as defined in the schema.
     * Timestamp columns 'created_at' and 'updated_at' are automatically added.
     * Any indexes defined in the schema will also be created.
     *
     * @param schema the TableSchema object defining the structure of the table to be created
     * @return true if the table was successfully created, false if the table already exists
     */
    @Override
    @Transactional
    public boolean createTable(TableSchema schema) {
        // Ensure plugin schema exists before creating table
        ensureSchemaExists();

        String fullTableName = getFullTableName(schema.getTableName());

        // Check if table already exists in the plugin's schema
        if (tableExistsInternal(schema.getTableName())) {
            logger.debug("Table {} already exists for plugin {} in schema {}", schema.getTableName(), pluginId, schemaName);
            return false;
        }

        // Build CREATE TABLE SQL with schema qualification
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
        logger.info("Created table {} for plugin {} in schema {}", schema.getTableName(), pluginId, schemaName);

        // Create indexes
        for (TableSchema.IndexDefinition idx : schema.getIndexes()) {
            createIndexInternal(fullTableName, idx.unique(), idx.columns().toArray(new String[0]));
        }

        return true;
    }

    /**
     * Checks if a table with the specified name exists in the plugin's schema.
     *
     * @param tableName the name of the table to check for existence (without schema prefix)
     * @return true if the table exists, false otherwise
     */
    @Override
    public boolean tableExists(String tableName) {
        return tableExistsInternal(tableName);
    }

    /**
     * Checks if a table with the specified name exists in the plugin's schema.
     * This method performs a case-insensitive check by converting the table name to lowercase.
     *
     * @param tableName the name of the table to check for existence (without schema prefix)
     * @return true if the table exists, false otherwise
     */
    private boolean tableExistsInternal(String tableName) {
        String sql = "SELECT EXISTS (SELECT FROM pg_tables WHERE schemaname = ? AND tablename = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, schemaName, tableName.toLowerCase());
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Drops the specified database table if it exists in the plugin's schema.
     * If the table does not exist, this method returns false.
     * If the table exists and is successfully dropped, this method returns true.
     *
     * @param tableName the name of the table to drop (without schema prefix)
     * @return true if the table was successfully dropped, false if the table did not exist
     */
    @Override
    @Transactional
    public boolean dropTable(String tableName) {
        String fullTableName = getFullTableName(tableName);

        if (!tableExistsInternal(tableName)) {
            return false;
        }

        jdbcTemplate.execute("DROP TABLE IF EXISTS " + fullTableName + " CASCADE");
        repositories.remove(tableName);
        logger.info("Dropped table {} for plugin {} in schema {}", tableName, pluginId, schemaName);
        return true;
    }

    /**
     * Retrieves a repository for the specified table name and entity class.
     * If a repository for the given table name already exists, it is returned.
     * Otherwise, a new repository is created and stored for future use.
     *
     * @param tableName the name of the database table associated with the repository (without schema prefix)
     * @param entityClass the class type of the entities managed by the repository
     * @return a typed PluginRepository instance for the specified table and entity class
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> PluginRepository<T> getRepository(String tableName, Class<T> entityClass) {
        return (PluginRepository<T>) repositories.computeIfAbsent(tableName,
                name -> new PluginRepositoryImpl<>(this, name, entityClass, jdbcTemplate));
    }

    /**
     * Returns the key-value store associated with this plugin database manager.
     * If the key-value store has not been initialized yet, this method ensures
     * that the underlying key-value table exists and then creates a new instance
     * of the key-value store implementation.
     *
     * @return the PluginKeyValueStore instance for this plugin
     */
    @Override
    public PluginKeyValueStore getKeyValueStore() {
        if (keyValueStore == null) {
            // Ensure KV table exists in plugin schema
            ensureKeyValueTable();
            keyValueStore = new PluginKeyValueStoreImpl(this, jdbcTemplate);
        }
        return keyValueStore;
    }

    /**
     * Ensures that the key-value store table exists in the plugin's schema.
     * The table name is "kv_store" and will be created in the plugin's schema.
     * If the table does not exist, it creates a new table with the following columns:
     * - key: VARCHAR(500) PRIMARY KEY
     * - value: TEXT
     * - created_at: TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
     * - updated_at: TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
     * This method is typically called during initialization to guarantee the presence of the key-value storage table.
     */
    private void ensureKeyValueTable() {
        String fullTableName = getFullTableName("kv_store");
        if (!tableExistsInternal("kv_store")) {
            String sql = "CREATE TABLE " + fullTableName + " (\n" +
                    "    key VARCHAR(500) PRIMARY KEY,\n" +
                    "    value TEXT,\n" +
                    "    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                    "    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP\n" +
                    ")";
            jdbcTemplate.execute(sql);
            logger.info("Created key-value store table for plugin {} in schema {}", pluginId, schemaName);
        }
    }

    /**
     * Retrieves a list of all tables in the plugin's schema.
     * The returned table names are without the schema prefix.
     *
     * @return a list of table names without the schema prefix
     */
    @Override
    public List<String> listTables() {
        String sql = "SELECT tablename FROM pg_tables WHERE schemaname = ?";
        return jdbcTemplate.queryForList(sql, String.class, schemaName);
    }

    /**
     * Returns the current schema version of the plugin's database.
     *
     * @return the schema version as an integer
     */
    @Override
    public int getSchemaVersion() {
        return registry.getSchemaVersion();
    }

    /**
     * Sets the schema version for the plugin's database.
     * This method updates the schema version in the plugin database registry and persists the change.
     *
     * @param version the new schema version to set
     */
    @Override
    @Transactional
    public void setSchemaVersion(int version) {
        registry.setSchemaVersion(version);
        registryRepository.save(registry);
        logger.debug("Set schema version to {} for plugin {}", version, pluginId);
    }

    /**
     * Migrates the plugin's database schema to the specified target version.
     * If the current schema version is already equal to or greater than the target version,
     * the migration is skipped and the method returns false.
     * Otherwise, the migration is executed using the provided migration implementation.
     *
     * @param targetVersion the schema version to migrate to
     * @param migration the migration implementation to execute
     * @return true if the migration was executed successfully, false if it was skipped
     * @throws RuntimeException if the migration execution fails
     */
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

    /**
     * Retrieves statistics about the plugin's database usage.
     * This includes the plugin ID, schema name, number of tables,
     * total row count across all tables, and the current schema version.
     *
     * @return a DatabaseStats object containing the database statistics
     */
    @Override
    public DatabaseStats getStats() {
        List<String> tables = listTables();
        long totalRows = 0;

        for (String table : tables) {
            try {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM " + getFullTableName(table), Long.class);
                totalRows += (count != null ? count : 0);
            } catch (Exception e) {
                logger.debug("Could not count rows in {}: {}", table, e.getMessage());
            }
        }

        return new DatabaseStats(
                pluginId,
                schemaName,
                tables.size(),
                totalRows,
                getSchemaVersion()
        );
    }

    /**
     * Returns the full table name by prepending the plugin schema name.
     * Format: "schema_name.table_name" (e.g., "plugin_myplugin.settings")
     *
     * @param tableName the name of the table
     * @return the full table name with schema
     */
    String getFullTableName(String tableName) {
        return schemaName + "." + tableName;
    }

    /**
     * Creates a database index on the specified table with the given columns.
     * The index name is automatically generated based on the table name and column names.
     * If the index already exists, this method does nothing.
     *
     * @param fullTableName the fully qualified name of the table on which to create the index
     * @param unique whether the index should enforce uniqueness constraints
     * @param columns the column names to include in the index
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
