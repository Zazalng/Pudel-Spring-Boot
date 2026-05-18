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

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import group.worldstandard.pudel.api.database.QueryBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of QueryBuilder for complex queries.
 */
public class QueryBuilderImpl<T> implements QueryBuilder<T> {
    private final PluginRepositoryImpl<T> repository;
    private final String fullTableName;
    private final Class<T> entityClass;
    private final JdbcTemplate jdbcTemplate;
    private final BeanPropertyRowMapper<T> rowMapper;

    private final List<WhereClause> whereClauses = new ArrayList<>();
    private final List<OrderByClause> orderByClauses = new ArrayList<>();
    private Integer limit;
    private Integer offset;

    public QueryBuilderImpl(PluginRepositoryImpl<T> repository, String fullTableName,
                           Class<T> entityClass, JdbcTemplate jdbcTemplate,
                           BeanPropertyRowMapper<T> rowMapper) {
        this.repository = repository;
        this.fullTableName = fullTableName;
        this.entityClass = entityClass;
        this.jdbcTemplate = jdbcTemplate;
        this.rowMapper = rowMapper;
    }

    @Override
    public QueryBuilder<T> where(String column, Object value) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, "=", value));
        return this;
    }

    @Override
    public QueryBuilder<T> whereNot(String column, Object value) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, "<>", value));
        return this;
    }

    @Override
    public QueryBuilder<T> whereGreaterThan(String column, Object value) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, ">", value));
        return this;
    }

    @Override
    public QueryBuilder<T> whereGreaterThanOrEqual(String column, Object value) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, ">=", value));
        return this;
    }

    @Override
    public QueryBuilder<T> whereLessThan(String column, Object value) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, "<", value));
        return this;
    }

    @Override
    public QueryBuilder<T> whereLessThanOrEqual(String column, Object value) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, "<=", value));
        return this;
    }

    @Override
    public QueryBuilder<T> whereLike(String column, String pattern) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, "LIKE", pattern));
        return this;
    }

    @Override
    public QueryBuilder<T> whereILike(String column, String pattern) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, "ILIKE", pattern));
        return this;
    }

    @Override
    public QueryBuilder<T> whereIn(String column, List<?> values) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, "IN", values));
        return this;
    }

    @Override
    public QueryBuilder<T> whereNotIn(String column, List<?> values) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, "NOT IN", values));
        return this;
    }

    @Override
    public QueryBuilder<T> whereNull(String column) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, "IS NULL", null));
        return this;
    }

    @Override
    public QueryBuilder<T> whereNotNull(String column) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, "IS NOT NULL", null));
        return this;
    }

    @Override
    public QueryBuilder<T> whereBetween(String column, Object start, Object end) {
        validateColumn(column);
        whereClauses.add(new WhereClause(column, "BETWEEN", new Object[]{start, end}));
        return this;
    }

    @Override
    public QueryBuilder<T> orderBy(String column, boolean ascending) {
        validateColumn(column);
        orderByClauses.add(new OrderByClause(column, ascending));
        return this;
    }

    @Override
    public QueryBuilder<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    @Override
    public QueryBuilder<T> offset(int offset) {
        this.offset = offset;
        return this;
    }

    @Override
    public List<T> list() {
        return executeQuery(false);
    }

    @Override
    public Optional<T> findOne() {
        List<T> results = executeQuery(true);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public T findOneOrThrow() {
        return findOne().orElseThrow(() -> new NoSuchElementException("No entity found"));
    }

    @Override
    public long count() {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(fullTableName);
        List<Object> params = new ArrayList<>();
        appendWhere(sql, params);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return count != null ? count : 0;
    }

    @Override
    public boolean exists() {
        return count() > 0;
    }

    @Override
    public int delete() {
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(fullTableName);
        List<Object> params = new ArrayList<>();
        appendWhere(sql, params);
        return jdbcTemplate.update(sql.toString(), params.toArray());
    }

    private List<T> executeQuery(boolean limitOne) {
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(fullTableName);
        List<Object> params = new ArrayList<>();

        appendWhere(sql, params);
        appendOrderBy(sql);

        if (limitOne) {
            sql.append(" LIMIT 1");
        } else if (limit != null) {
            sql.append(" LIMIT ?");
            params.add(limit);
        }

        if (offset != null) {
            sql.append(" OFFSET ?");
            params.add(offset);
        }

        return jdbcTemplate.query(sql.toString(), rowMapper, params.toArray());
    }

    private void appendWhere(StringBuilder sql, List<Object> params) {
        if (whereClauses.isEmpty()) {
            return;
        }

        sql.append(" WHERE ");
        List<String> clauses = new ArrayList<>();

        for (WhereClause clause : whereClauses) {
            String op = clause.operator;

            switch (op) {
                case "IS NULL", "IS NOT NULL" -> clauses.add(clause.column + " " + op);
                case "IN", "NOT IN" -> {
                    List<?> values = (List<?>) clause.value;
                    String placeholders = values.stream().map(v -> "?").collect(Collectors.joining(", "));
                    clauses.add(clause.column + " " + op + " (" + placeholders + ")");
                    params.addAll(values);
                }
                case "BETWEEN" -> {
                    Object[] range = (Object[]) clause.value;
                    clauses.add(clause.column + " BETWEEN ? AND ?");
                    params.add(range[0]);
                    params.add(range[1]);
                }
                default -> {
                    clauses.add(clause.column + " " + op + " ?");
                    params.add(clause.value);
                }
            }
        }

        sql.append(String.join(" AND ", clauses));
    }

    private void appendOrderBy(StringBuilder sql) {
        if (orderByClauses.isEmpty()) {
            sql.append(" ORDER BY id");
            return;
        }

        sql.append(" ORDER BY ");
        List<String> orders = orderByClauses.stream()
                .map(o -> o.column + (o.ascending ? " ASC" : " DESC"))
                .collect(Collectors.toList());
        sql.append(String.join(", ", orders));
    }

    private void validateColumn(String column) {
        if (column == null || !column.matches("^[a-z][a-z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid column name: " + column);
        }
    }

    private record WhereClause(String column, String operator, Object value) {}
    private record OrderByClause(String column, boolean ascending) {}
}