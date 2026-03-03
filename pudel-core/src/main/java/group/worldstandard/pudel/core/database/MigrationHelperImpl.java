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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import group.worldstandard.pudel.api.database.ColumnType;
import group.worldstandard.pudel.api.database.PluginDatabaseManager;
import group.worldstandard.pudel.api.database.PluginMigration;
import group.worldstandard.pudel.api.database.PluginRepository;

/**
 * Implementation of MigrationHelper for plugin schema migrations.
 */
public class MigrationHelperImpl implements PluginMigration.MigrationHelper {

    private static final Logger logger = LoggerFactory.getLogger(MigrationHelperImpl.class);

    private final PluginDatabaseManagerImpl dbManager;
    private final JdbcTemplate jdbcTemplate;

    public MigrationHelperImpl(PluginDatabaseManagerImpl dbManager, JdbcTemplate jdbcTemplate) {
        this.dbManager = dbManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PluginDatabaseManager getDatabaseManager() {
        return dbManager;
    }

    @Override
    public void addColumn(String tableName, String columnName, ColumnType type, Integer size, boolean nullable) {
        addColumn(tableName, columnName, type, size, nullable, null);
    }

    @Override
    public void addColumn(String tableName, String columnName, ColumnType type, Integer size,
                         boolean nullable, String defaultValue) {
        String fullTableName = dbManager.getFullTableName(tableName);
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE ").append(fullTableName);
        sql.append(" ADD COLUMN IF NOT EXISTS ").append(columnName).append(" ");
        sql.append(type.getSqlType(size));

        if (!nullable) {
            sql.append(" NOT NULL");
        }
        if (defaultValue != null) {
            sql.append(" DEFAULT ").append(defaultValue);
        }

        jdbcTemplate.execute(sql.toString());
        logger.debug("Added column {} to table {}", columnName, fullTableName);
    }

    @Override
    public void dropColumn(String tableName, String columnName) {
        String fullTableName = dbManager.getFullTableName(tableName);
        String sql = "ALTER TABLE " + fullTableName + " DROP COLUMN IF EXISTS " + columnName;
        jdbcTemplate.execute(sql);
        logger.debug("Dropped column {} from table {}", columnName, fullTableName);
    }

    @Override
    public void renameColumn(String tableName, String oldName, String newName) {
        String fullTableName = dbManager.getFullTableName(tableName);
        String sql = "ALTER TABLE " + fullTableName + " RENAME COLUMN " + oldName + " TO " + newName;
        jdbcTemplate.execute(sql);
        logger.debug("Renamed column {} to {} in table {}", oldName, newName, fullTableName);
    }

    @Override
    public void alterColumnType(String tableName, String columnName, ColumnType newType, Integer newSize) {
        String fullTableName = dbManager.getFullTableName(tableName);
        String sql = "ALTER TABLE " + fullTableName + " ALTER COLUMN " + columnName +
                     " TYPE " + newType.getSqlType(newSize);
        jdbcTemplate.execute(sql);
        logger.debug("Changed type of column {} in table {} to {}", columnName, fullTableName, newType);
    }

    @Override
    public void createIndex(String tableName, boolean unique, String... columns) {
        String fullTableName = dbManager.getFullTableName(tableName);
        dbManager.createIndexInternal(fullTableName, unique, columns);
    }

    @Override
    public void dropIndex(String tableName, String... columns) {
        String fullTableName = dbManager.getFullTableName(tableName);
        String indexName = "idx_" + fullTableName + "_" + String.join("_", columns);
        String sql = "DROP INDEX IF EXISTS " + indexName;
        jdbcTemplate.execute(sql);
        logger.debug("Dropped index {} from table {}", indexName, fullTableName);
    }

    @Override
    public void renameTable(String oldName, String newName) {
        String fullOldName = dbManager.getFullTableName(oldName);
        String fullNewName = dbManager.getFullTableName(newName);
        String sql = "ALTER TABLE " + fullOldName + " RENAME TO " + fullNewName;
        jdbcTemplate.execute(sql);
        logger.debug("Renamed table {} to {}", fullOldName, fullNewName);
    }

    @Override
    public <T> void migrateData(String tableName, Class<T> entityClass, PluginMigration.DataMigrator<T> migrator) {
        PluginRepository<T> repo = dbManager.getRepository(tableName, entityClass);

        for (T entity : repo.findAll()) {
            T migrated = migrator.migrate(entity);
            if (migrated == null) {
                repo.delete(entity);
            } else {
                repo.save(migrated);
            }
        }

        logger.debug("Migrated data in table {}", tableName);
    }
}
