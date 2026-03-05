/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 World Standard Group
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package group.worldstandard.pudel.api.database;

/**
 * Interface for plugin database migrations.
 * <p>
 * Plugins can define migrations to update their database schema over time.
 * Migrations run in order based on version numbers and only run once.
 * <p>
 * Example:
 * <pre>
 * {@code @OnEnable}
 * public void onEnable(PluginContext context) {
 *     PluginDatabaseManager db = context.getDatabaseManager();
 *
 *     // Migration to version 1: Initial schema
 *     db.migrate(1, manager -> {
 *         manager.createTable(TableSchema.builder("users")
 *             .column("name", ColumnType.STRING, 100, false)
 *             .build());
 *     });
 *
 *     // Migration to version 2: Add email column
 *     db.migrate(2, manager -> {
 *         // Use raw ALTER TABLE via migration helper
 *         manager.addColumn("users", "email", ColumnType.STRING, 255, true);
 *     });
 *
 *     // Migration to version 3: Add index
 *     db.migrate(3, manager -> {
 *         manager.createIndex("users", false, "email");
 *     });
 * }
 * </pre>
 */
@FunctionalInterface
public interface PluginMigration {

    /**
     * Execute the migration.
     * <p>
     * This method is called within a database transaction.
     * If an exception is thrown, the migration is rolled back.
     *
     * @param migrationHelper helper for migration operations
     * @throws Exception if migration fails
     */
    void migrate(MigrationHelper migrationHelper) throws Exception;

    /**
     * Helper interface for migration operations.
     * <p>
     * Provides methods for schema modifications that are typically
     * needed during migrations.
     */
    interface MigrationHelper {

        /**
         * Get the underlying database manager.
         *
         * @return the database manager
         */
        PluginDatabaseManager getDatabaseManager();

        /**
         * Add a column to an existing table.
         *
         * @param tableName the table name (without prefix)
         * @param columnName the new column name
         * @param type the column type
         * @param size size for VARCHAR, etc. (can be null)
         * @param nullable whether the column allows null
         */
        void addColumn(String tableName, String columnName, ColumnType type, Integer size, boolean nullable);

        /**
         * Add a column with a default value.
         *
         * @param tableName the table name
         * @param columnName the new column name
         * @param type the column type
         * @param size size (can be null)
         * @param nullable whether allows null
         * @param defaultValue the default value
         */
        void addColumn(String tableName, String columnName, ColumnType type, Integer size, boolean nullable, String defaultValue);

        /**
         * Drop a column from a table.
         *
         * @param tableName the table name
         * @param columnName the column to drop
         */
        void dropColumn(String tableName, String columnName);

        /**
         * Rename a column.
         *
         * @param tableName the table name
         * @param oldName the current column name
         * @param newName the new column name
         */
        void renameColumn(String tableName, String oldName, String newName);

        /**
         * Change a column's type.
         *
         * @param tableName the table name
         * @param columnName the column name
         * @param newType the new type
         * @param newSize new size (can be null)
         */
        void alterColumnType(String tableName, String columnName, ColumnType newType, Integer newSize);

        /**
         * Create an index.
         *
         * @param tableName the table name
         * @param unique whether the index is unique
         * @param columns the columns to index
         */
        void createIndex(String tableName, boolean unique, String... columns);

        /**
         * Drop an index.
         *
         * @param tableName the table name
         * @param columns the columns of the index
         */
        void dropIndex(String tableName, String... columns);

        /**
         * Rename a table.
         *
         * @param oldName the current table name
         * @param newName the new table name
         */
        void renameTable(String oldName, String newName);

        /**
         * Execute a data migration using the repository.
         * <p>
         * This allows updating data as part of a migration.
         *
         * @param tableName the table name
         * @param entityClass the entity class
         * @param migrator function to process each entity
         * @param <T> the entity type
         */
        <T> void migrateData(String tableName, Class<T> entityClass, DataMigrator<T> migrator);
    }

    /**
     * Functional interface for data migrations.
     *
     * @param <T> the entity type
     */
    @FunctionalInterface
    interface DataMigrator<T> {
        /**
         * Process an entity during migration.
         * <p>
         * Return the modified entity to save it, or null to delete it.
         *
         * @param entity the entity to process
         * @return the modified entity, or null to delete
         */
        T migrate(T entity);
    }
}
