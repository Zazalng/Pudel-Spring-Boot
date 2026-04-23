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

import java.util.List;
import java.util.Optional;

/**
 * Query builder for complex database queries.
 * <p>
 * Provides a fluent interface for building queries with multiple conditions,
 * ordering, and pagination.
 * <p>
 * Example:
 * <pre>
 * {@code
 * List<UserSetting> results = repository.query()
 *     .where("user_id", 12345L)
 *     .where("enabled", true)
 *     .whereLike("setting_name", "theme%")
 *     .orderBy("created_at", false)  // descending
 *     .limit(10)
 *     .offset(0)
 *     .list();
 *
 * // Or get a single result
 * Optional<UserSetting> result = repository.query()
 *     .where("user_id", 12345L)
 *     .where("setting_name", "theme")
 *     .findOne();
 *
 * // Count
 * long count = repository.query()
 *     .where("enabled", true)
 *     .count();
 * }
 * </pre>
 *
 * @param <T> the entity type
 */
public interface QueryBuilder<T> {

    /**
     * Add an equality condition (column = value).
     *
     * @param column the column name
     * @param value the value
     * @return this builder
     */
    QueryBuilder<T> where(String column, Object value);

    /**
     * Add a not-equal condition (column != value).
     *
     * @param column the column name
     * @param value the value
     * @return this builder
     */
    QueryBuilder<T> whereNot(String column, Object value);

    /**
     * Add a greater-than condition (column > value).
     *
     * @param column the column name
     * @param value the value
     * @return this builder
     */
    QueryBuilder<T> whereGreaterThan(String column, Object value);

    /**
     * Add a greater-than-or-equal condition (column >= value).
     *
     * @param column the column name
     * @param value the value
     * @return this builder
     */
    QueryBuilder<T> whereGreaterThanOrEqual(String column, Object value);

    /**
     * Add a less-than condition (column &lt; value).
     *
     * @param column the column name
     * @param value the value
     * @return this builder
     */
    QueryBuilder<T> whereLessThan(String column, Object value);

    /**
     * Add a less-than-or-equal condition (column &lt;= value).
     *
     * @param column the column name
     * @param value the value
     * @return this builder
     */
    QueryBuilder<T> whereLessThanOrEqual(String column, Object value);

    /**
     * Add a LIKE condition for pattern matching.
     * <p>
     * Use % for wildcard matching:
     * - "abc%" matches strings starting with "abc"
     * - "%abc" matches strings ending with "abc"
     * - "%abc%" matches strings containing "abc"
     *
     * @param column the column name
     * @param pattern the pattern with % wildcards
     * @return this builder
     */
    QueryBuilder<T> whereLike(String column, String pattern);

    /**
     * Add a case-insensitive LIKE condition.
     *
     * @param column the column name
     * @param pattern the pattern with % wildcards
     * @return this builder
     */
    QueryBuilder<T> whereILike(String column, String pattern);

    /**
     * Add an IN condition (column IN (values)).
     *
     * @param column the column name
     * @param values the values to match
     * @return this builder
     */
    QueryBuilder<T> whereIn(String column, List<?> values);

    /**
     * Add a NOT IN condition.
     *
     * @param column the column name
     * @param values the values to exclude
     * @return this builder
     */
    QueryBuilder<T> whereNotIn(String column, List<?> values);

    /**
     * Add an IS NULL condition.
     *
     * @param column the column name
     * @return this builder
     */
    QueryBuilder<T> whereNull(String column);

    /**
     * Add an IS NOT NULL condition.
     *
     * @param column the column name
     * @return this builder
     */
    QueryBuilder<T> whereNotNull(String column);

    /**
     * Add a BETWEEN condition (column BETWEEN start AND end).
     *
     * @param column the column name
     * @param start the start value (inclusive)
     * @param end the end value (inclusive)
     * @return this builder
     */
    QueryBuilder<T> whereBetween(String column, Object start, Object end);

    /**
     * Add ordering.
     *
     * @param column the column to order by
     * @param ascending true for ASC, false for DESC
     * @return this builder
     */
    QueryBuilder<T> orderBy(String column, boolean ascending);

    /**
     * Add ascending ordering.
     *
     * @param column the column to order by
     * @return this builder
     */
    default QueryBuilder<T> orderByAsc(String column) {
        return orderBy(column, true);
    }

    /**
     * Add descending ordering.
     *
     * @param column the column to order by
     * @return this builder
     */
    default QueryBuilder<T> orderByDesc(String column) {
        return orderBy(column, false);
    }

    /**
     * Limit the number of results.
     *
     * @param limit maximum number of results
     * @return this builder
     */
    QueryBuilder<T> limit(int limit);

    /**
     * Skip a number of results.
     *
     * @param offset number of results to skip
     * @return this builder
     */
    QueryBuilder<T> offset(int offset);

    /**
     * Execute the query and return all matching entities.
     *
     * @return list of matching entities
     */
    List<T> list();

    /**
     * Execute the query and return the first matching entity.
     *
     * @return the first matching entity
     */
    Optional<T> findOne();

    /**
     * Execute the query and return the first matching entity, or throw if not found.
     *
     * @return the first matching entity
     * @throws java.util.NoSuchElementException if no match found
     */
    T findOneOrThrow();

    /**
     * Count matching entities.
     *
     * @return count of matching entities
     */
    long count();

    /**
     * Check if any matching entities exist.
     *
     * @return true if at least one match exists
     */
    boolean exists();

    /**
     * Delete all matching entities.
     *
     * @return number of entities deleted
     */
    int delete();
}
