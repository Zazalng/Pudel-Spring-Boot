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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Schema definition for a plugin database table.
 * <p>
 * All tables automatically include an 'id' column (BIGSERIAL PRIMARY KEY)
 * and 'created_at', 'updated_at' timestamp columns.
 * <p>
 * Example:
 * <pre>
 * TableSchema schema = TableSchema.builder("user_settings")
 *     .column("user_id", ColumnType.BIGINT, false)
 *     .column("setting_name", ColumnType.STRING, 100, false)
 *     .column("setting_value", ColumnType.TEXT, true)
 *     .column("enabled", ColumnType.BOOLEAN, false, "true")
 *     .index("user_id")
 *     .uniqueIndex("user_id", "setting_name")
 *     .build();
 * </pre>
 */
public final class TableSchema {

    private final String tableName;
    private final List<ColumnDefinition> columns;
    private final List<IndexDefinition> indexes;

    private TableSchema(Builder builder) {
        this.tableName = builder.tableName;
        this.columns = Collections.unmodifiableList(new ArrayList<>(builder.columns));
        this.indexes = Collections.unmodifiableList(new ArrayList<>(builder.indexes));
    }

    public String getTableName() {
        return tableName;
    }

    public List<ColumnDefinition> getColumns() {
        return columns;
    }

    public List<IndexDefinition> getIndexes() {
        return indexes;
    }

    /**
     * Create a new schema builder.
     *
     * @param tableName the table name (will be prefixed automatically)
     * @return a new builder
     */
    public static Builder builder(String tableName) {
        return new Builder(tableName);
    }

    /**
     * Builder for TableSchema.
     */
    public static class Builder {
        private final String tableName;
        private final List<ColumnDefinition> columns = new ArrayList<>();
        private final List<IndexDefinition> indexes = new ArrayList<>();

        private Builder(String tableName) {
            this.tableName = validateTableName(tableName);
        }

        /**
         * Add a column to the schema.
         *
         * @param name column name
         * @param type column type
         * @param nullable whether the column can be null
         * @return this builder
         */
        public Builder column(String name, ColumnType type, boolean nullable) {
            return column(name, type, null, nullable, null);
        }

        /**
         * Add a column with a size constraint.
         *
         * @param name column name
         * @param type column type
         * @param size size/length for VARCHAR, etc.
         * @param nullable whether the column can be null
         * @return this builder
         */
        public Builder column(String name, ColumnType type, Integer size, boolean nullable) {
            return column(name, type, size, nullable, null);
        }

        /**
         * Add a column with a default value.
         *
         * @param name column name
         * @param type column type
         * @param nullable whether the column can be null
         * @param defaultValue SQL default value expression
         * @return this builder
         */
        public Builder column(String name, ColumnType type, boolean nullable, String defaultValue) {
            return column(name, type, null, nullable, defaultValue);
        }

        /**
         * Add a column with all options.
         *
         * @param name column name
         * @param type column type
         * @param size size/length (nullable)
         * @param nullable whether the column can be null
         * @param defaultValue SQL default value expression (nullable)
         * @return this builder
         */
        public Builder column(String name, ColumnType type, Integer size, boolean nullable, String defaultValue) {
            columns.add(new ColumnDefinition(
                    validateColumnName(name),
                    type,
                    size,
                    nullable,
                    defaultValue
            ));
            return this;
        }

        /**
         * Add an index on one or more columns.
         *
         * @param columnNames the columns to index
         * @return this builder
         */
        public Builder index(String... columnNames) {
            indexes.add(new IndexDefinition(false, List.of(columnNames)));
            return this;
        }

        /**
         * Add a unique index on one or more columns.
         *
         * @param columnNames the columns for the unique constraint
         * @return this builder
         */
        public Builder uniqueIndex(String... columnNames) {
            indexes.add(new IndexDefinition(true, List.of(columnNames)));
            return this;
        }

        public TableSchema build() {
            return new TableSchema(this);
        }

        private static String validateTableName(String name) {
            Objects.requireNonNull(name, "Table name cannot be null");
            if (!name.matches("^[a-z][a-z0-9_]*$")) {
                throw new IllegalArgumentException(
                        "Table name must start with lowercase letter and contain only lowercase letters, numbers, and underscores: " + name);
            }
            if (name.length() > 50) {
                throw new IllegalArgumentException("Table name too long (max 50 chars): " + name);
            }
            return name;
        }

        private static String validateColumnName(String name) {
            Objects.requireNonNull(name, "Column name cannot be null");
            if (!name.matches("^[a-z][a-z0-9_]*$")) {
                throw new IllegalArgumentException(
                        "Column name must start with lowercase letter and contain only lowercase letters, numbers, and underscores: " + name);
            }
            // Reserved column names
            if (name.equals("id") || name.equals("created_at") || name.equals("updated_at")) {
                throw new IllegalArgumentException("Column name '" + name + "' is reserved");
            }
            return name;
        }
    }

    /**
     * Column definition.
     */
    public record ColumnDefinition(
            String name,
            ColumnType type,
            Integer size,
            boolean nullable,
            String defaultValue
    ) {}

    /**
     * Index definition.
     */
    public record IndexDefinition(
            boolean unique,
            List<String> columns
    ) {}
}
