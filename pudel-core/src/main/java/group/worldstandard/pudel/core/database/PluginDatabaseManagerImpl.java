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
    private final String prefix;
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
     * @param prefix the database table prefix for this plugin
     * @param registry the plugin database registry entry containing metadata
     * @param registryRepository the repository for accessing plugin database registry data
     * @param jdbcTemplate the JDBC template for executing database operations
     */
    public PluginDatabaseManagerImpl(String pluginId, String prefix, PluginDatabaseRegistry registry,
                                     PluginDatabaseRegistryRepository registryRepository,
                                     JdbcTemplate jdbcTemplate) {
        this.pluginId = pluginId;
        this.prefix = prefix;
        this.registry = registry;
        this.registryRepository = registryRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns the database table prefix associated with this plugin.
     *
     * @return the prefix string used for database tables managed by this plugin
     */
    @Override
    public String getPrefix() {
        return prefix;
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
     * Creates a new database table based on the provided schema definition.
     * The table name will be prefixed with the plugin-specific prefix.
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

    /**
     * Checks if a table with the specified name exists in the database.
     * The table name is prefixed with the plugin-specific prefix before checking.
     *
     * @param tableName the name of the table to check for existence
     * @return true if the table exists, false otherwise
     */
    @Override
    public boolean tableExists(String tableName) {
        return tableExistsInternal(prefix + tableName);
    }

    /**
     * Checks if a table with the specified full table name exists in the database.
     * This method performs a case-insensitive check by converting the table name to lowercase.
     *
     * @param fullTableName the fully qualified name of the table to check for existence
     * @return true if the table exists, false otherwise
     */
    private boolean tableExistsInternal(String fullTableName) {
        String sql = "SELECT EXISTS (SELECT FROM pg_tables WHERE tablename = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, fullTableName.toLowerCase());
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Drops the specified database table if it exists.
     * The table name will be prefixed with the plugin-specific prefix before dropping.
     * If the table does not exist, this method returns false.
     * If the table exists and is successfully dropped, this method returns true.
     *
     * @param tableName the name of the table to drop (without prefix)
     * @return true if the table was successfully dropped, false if the table did not exist
     */
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

    /**
     * Retrieves a repository for the specified table name and entity class.
     * If a repository for the given table name already exists, it is returned.
     * Otherwise, a new repository is created and stored for future use.
     *
     * @param tableName the name of the database table associated with the repository
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
            // Ensure KV table exists
            ensureKeyValueTable();
            keyValueStore = new PluginKeyValueStoreImpl(this, jdbcTemplate);
        }
        return keyValueStore;
    }

    /**
     * Ensures that the key-value store table exists in the database.
     * The table name is constructed by appending "kv_store" to the plugin-specific prefix.
     * If the table does not exist, it creates a new table with the following columns:
     * - key: VARCHAR(500) PRIMARY KEY
     * - value: TEXT
     * - created_at: TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
     * - updated_at: TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
     * This method is typically called during initialization to guarantee the presence of the key-value storage table.
     */
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

    /**
     * Retrieves a list of all tables in the database that match the plugin's table prefix.
     * The returned table names are stripped of the plugin-specific prefix.
     *
     * @return a list of table names without the plugin prefix
     */
    @Override
    public List<String> listTables() {
        String sql = "SELECT tablename FROM pg_tables WHERE tablename LIKE ?";
        List<String> tables = jdbcTemplate.queryForList(sql, String.class, prefix + "%");
        return tables.stream()
                .map(t -> t.substring(prefix.length()))
                .collect(Collectors.toList());
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
     * This includes the plugin ID, table prefix, number of tables,
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
     * Returns the full table name by prepending the plugin-specific prefix to the given table name.
     *
     * @param tableName the name of the table to which the prefix will be added
     * @return the full table name including the plugin-specific prefix
     */
    String getFullTableName(String tableName) {
        return prefix + tableName;
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
