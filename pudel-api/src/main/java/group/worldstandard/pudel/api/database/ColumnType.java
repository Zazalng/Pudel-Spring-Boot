/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 World Standard.group
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
 * Supported column types for plugin database tables.
 */
public enum ColumnType {
    /**
     * 64-bit integer (BIGINT in PostgreSQL).
     * Use for IDs, large numbers, Discord snowflakes.
     */
    BIGINT("BIGINT"),

    /**
     * 32-bit integer (INTEGER in PostgreSQL).
     * Use for counts, small numbers.
     */
    INTEGER("INTEGER"),

    /**
     * 16-bit integer (SMALLINT in PostgreSQL).
     * Use for very small numbers, enums.
     */
    SMALLINT("SMALLINT"),

    /**
     * Boolean (BOOLEAN in PostgreSQL).
     * Use for true/false flags.
     */
    BOOLEAN("BOOLEAN"),

    /**
     * Variable-length string (VARCHAR in PostgreSQL).
     * Requires size parameter. Use for short strings.
     */
    STRING("VARCHAR"),

    /**
     * Unlimited text (TEXT in PostgreSQL).
     * Use for long content, descriptions.
     */
    TEXT("TEXT"),

    /**
     * Timestamp with timezone (TIMESTAMPTZ in PostgreSQL).
     * Use for dates and times.
     */
    TIMESTAMP("TIMESTAMPTZ"),

    /**
     * Date only (DATE in PostgreSQL).
     */
    DATE("DATE"),

    /**
     * Time only (TIME in PostgreSQL).
     */
    TIME("TIME"),

    /**
     * Decimal number (NUMERIC in PostgreSQL).
     * Use for precise decimal values (money, etc.).
     */
    DECIMAL("NUMERIC"),

    /**
     * Double precision floating point (DOUBLE PRECISION in PostgreSQL).
     * Use for scientific calculations.
     */
    DOUBLE("DOUBLE PRECISION"),

    /**
     * Single precision floating point (REAL in PostgreSQL).
     */
    FLOAT("REAL"),

    /**
     * JSON data (JSONB in PostgreSQL).
     * Use for structured data, configurations.
     */
    JSON("JSONB"),

    /**
     * Binary data (BYTEA in PostgreSQL).
     * Use for small binary blobs.
     */
    BINARY("BYTEA"),

    /**
     * UUID (UUID in PostgreSQL).
     * Use for unique identifiers.
     */
    UUID("UUID");

    private final String sqlType;

    ColumnType(String sqlType) {
        this.sqlType = sqlType;
    }

    /**
     * Get the PostgreSQL SQL type name.
     */
    public String getSqlType() {
        return sqlType;
    }

    /**
     * Get the SQL type with size if applicable.
     *
     * @param size the size/length
     * @return SQL type string
     */
    public String getSqlType(Integer size) {
        if (this == STRING && size != null) {
            return "VARCHAR(" + size + ")";
        }
        if (this == DECIMAL && size != null) {
            return "NUMERIC(" + size + ", 2)"; // Default 2 decimal places
        }
        return sqlType;
    }
}
