/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard.group
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
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import group.worldstandard.pudel.api.database.Column;
import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.api.database.QueryBuilder;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of PluginRepository using JdbcTemplate.
 */
public class PluginRepositoryImpl<T> implements PluginRepository<T> {

    private static final Logger logger = LoggerFactory.getLogger(PluginRepositoryImpl.class);

    private final PluginDatabaseManagerImpl dbManager;
    private final String tableName;
    private final String fullTableName;
    private final Class<T> entityClass;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final BeanPropertyRowMapper<T> rowMapper;

    // Field mappings: fieldName -> columnName
    private final Map<String, String> fieldToColumn = new LinkedHashMap<>();
    private final Map<String, Field> columnToField = new LinkedHashMap<>();

    public PluginRepositoryImpl(PluginDatabaseManagerImpl dbManager, String tableName,
                                Class<T> entityClass, JdbcTemplate jdbcTemplate) {
        this.dbManager = dbManager;
        this.tableName = tableName;
        this.fullTableName = dbManager.getFullTableName(tableName);
        this.entityClass = entityClass;
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);

        // Create row mapper with custom conversion for java.time types
        BeanPropertyRowMapper<T> mapper = new BeanPropertyRowMapper<>(entityClass);
        mapper.setConversionService(createConversionService());
        this.rowMapper = mapper;

        // Build field mappings
        buildFieldMappings();
    }

    /**
     * Create a conversion service that handles Timestamp to Instant conversion.
     */
    private org.springframework.core.convert.ConversionService createConversionService() {
        org.springframework.core.convert.support.DefaultConversionService conversionService =
            new org.springframework.core.convert.support.DefaultConversionService();

        // Add Timestamp -> Instant converter
        conversionService.addConverter(Timestamp.class, Instant.class, Timestamp::toInstant);

        // Add Timestamp -> LocalDateTime converter
        conversionService.addConverter(Timestamp.class, LocalDateTime.class, Timestamp::toLocalDateTime);

        // Add Timestamp -> OffsetDateTime converter
        conversionService.addConverter(Timestamp.class, OffsetDateTime.class,
            ts -> ts.toInstant().atOffset(java.time.ZoneOffset.UTC));

        return conversionService;
    }

    private void buildFieldMappings() {
        for (Field field : entityClass.getDeclaredFields()) {
            field.setAccessible(true);

            // Check for @Column annotation
            Column columnAnnotation = field.getAnnotation(Column.class);
            if (columnAnnotation != null && columnAnnotation.ignore()) {
                continue; // Skip ignored fields
            }

            String columnName;
            if (columnAnnotation != null && !columnAnnotation.name().isEmpty()) {
                columnName = columnAnnotation.name();
            } else {
                // Convert camelCase to snake_case
                columnName = toSnakeCase(field.getName());
            }

            fieldToColumn.put(field.getName(), columnName);
            columnToField.put(columnName, field);
        }
    }

    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    @Override
    public T save(T entity) {
        try {
            Long id = getEntityId(entity);
            if (id == null || id == 0) {
                return insert(entity);
            } else {
                return update(entity);
            }
        } catch (Exception e) {
            logger.error("Error saving entity: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save entity", e);
        }
    }

    private T insert(T entity) throws Exception {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, String> entry : fieldToColumn.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();

            if (columnName.equals("id") || columnName.equals("created_at") || columnName.equals("updated_at")) {
                continue; // Skip auto-generated columns
            }

            Field field = entityClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(entity);

            columns.add(columnName);
            values.add(convertToJdbcValue(value));
        }

        String sql = "INSERT INTO " + fullTableName + " (" + String.join(", ", columns) + ") " +
                "VALUES (" + columns.stream().map(c -> "?").collect(Collectors.joining(", ")) + ") " +
                "RETURNING id";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.size(); i++) {
                setParameterValue(ps, i + 1, values.get(i));
            }
            return ps;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId != null) {
            setEntityId(entity, generatedId.longValue());
        }

        return entity;
    }

    private T update(T entity) throws Exception {
        Long id = getEntityId(entity);
        List<String> setClauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, String> entry : fieldToColumn.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();

            if (columnName.equals("id") || columnName.equals("created_at")) {
                continue; // Skip id and created_at
            }

            if (columnName.equals("updated_at")) {
                setClauses.add(columnName + " = CURRENT_TIMESTAMP");
                continue;
            }

            Field field = entityClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(entity);

            setClauses.add(columnName + " = ?");
            values.add(convertToJdbcValue(value));
        }

        values.add(id); // For WHERE clause

        String sql = "UPDATE " + fullTableName + " SET " + String.join(", ", setClauses) + " WHERE id = ?";
        jdbcTemplate.update(sql, values.toArray());

        return entity;
    }

    private Long getEntityId(T entity) throws Exception {
        Field idField = columnToField.get("id");
        if (idField == null) {
            throw new IllegalStateException("Entity must have an 'id' field");
        }
        idField.setAccessible(true);
        Object value = idField.get(entity);
        return switch (value) {
            case null -> null;
            case Long l -> l;
            case Number number -> number.longValue();
            default -> null;
        };
    }

    private void setEntityId(T entity, long id) throws Exception {
        Field idField = columnToField.get("id");
        if (idField != null) {
            idField.setAccessible(true);
            idField.set(entity, id);
        }
    }

    /**
     * Convert a Java value to a JDBC-compatible value.
     * Handles java.time types that PostgreSQL JDBC driver doesn't support directly.
     */
    private Object convertToJdbcValue(Object value) {
        if (value == null) {
            return null;
        }
        // Convert java.time.Instant to java.sql.Timestamp
        if (value instanceof Instant instant) {
            return Timestamp.from(instant);
        }
        // Convert java.time.LocalDateTime to java.sql.Timestamp
        if (value instanceof LocalDateTime ldt) {
            return Timestamp.valueOf(ldt);
        }
        // Convert java.time.OffsetDateTime to java.sql.Timestamp
        if (value instanceof OffsetDateTime odt) {
            return Timestamp.from(odt.toInstant());
        }
        return value;
    }

    /**
     * Set a parameter value on a PreparedStatement, handling null values properly.
     */
    private void setParameterValue(PreparedStatement ps, int index, Object value) throws java.sql.SQLException {
        if (value == null) {
            ps.setNull(index, Types.NULL);
        } else {
            ps.setObject(index, value);
        }
    }

    @Override
    public List<T> saveAll(Iterable<T> entities) {
        List<T> result = new ArrayList<>();
        for (T entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    @Override
    public Optional<T> findById(long id) {
        String sql = "SELECT * FROM " + fullTableName + " WHERE id = ?";
        List<T> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public List<T> findAll() {
        String sql = "SELECT * FROM " + fullTableName + " ORDER BY id";
        return jdbcTemplate.query(sql, rowMapper);
    }

    @Override
    public List<T> findAll(int limit, int offset) {
        String sql = "SELECT * FROM " + fullTableName + " ORDER BY id LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, limit, offset);
    }

    @Override
    public List<T> findBy(String column, Object value) {
        validateColumn(column);
        String sql = "SELECT * FROM " + fullTableName + " WHERE " + column + " = ? ORDER BY id";
        return jdbcTemplate.query(sql, rowMapper, value);
    }

    @Override
    public List<T> findBy(String column, Object value, int limit, int offset) {
        validateColumn(column);
        String sql = "SELECT * FROM " + fullTableName + " WHERE " + column + " = ? ORDER BY id LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, value, limit, offset);
    }

    @Override
    public List<T> findByAll(Map<String, Object> conditions) {
        if (conditions.isEmpty()) {
            return findAll();
        }
        conditions.keySet().forEach(this::validateColumn);

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(fullTableName).append(" WHERE ");
        List<String> clauses = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : conditions.entrySet()) {
            clauses.add(entry.getKey() + " = ?");
            values.add(entry.getValue());
        }

        sql.append(String.join(" AND ", clauses)).append(" ORDER BY id");
        return jdbcTemplate.query(sql.toString(), rowMapper, values.toArray());
    }

    @Override
    public Optional<T> findOneBy(String column, Object value) {
        List<T> results = findBy(column, value);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public Optional<T> findOneByAll(Map<String, Object> conditions) {
        List<T> results = findByAll(conditions);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    @Override
    public boolean existsById(long id) {
        String sql = "SELECT EXISTS(SELECT 1 FROM " + fullTableName + " WHERE id = ?)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, id));
    }

    @Override
    public boolean existsBy(String column, Object value) {
        validateColumn(column);
        String sql = "SELECT EXISTS(SELECT 1 FROM " + fullTableName + " WHERE " + column + " = ?)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, value));
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM " + fullTableName;
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    @Override
    public long countBy(String column, Object value) {
        validateColumn(column);
        String sql = "SELECT COUNT(*) FROM " + fullTableName + " WHERE " + column + " = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, value);
        return count != null ? count : 0;
    }

    @Override
    public boolean deleteById(long id) {
        String sql = "DELETE FROM " + fullTableName + " WHERE id = ?";
        int rows = jdbcTemplate.update(sql, id);
        return rows > 0;
    }

    @Override
    public boolean delete(T entity) {
        try {
            Long id = getEntityId(entity);
            if (id == null) return false;
            return deleteById(id);
        } catch (Exception e) {
            logger.error("Error deleting entity: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public int deleteAll(Iterable<T> entities) {
        int count = 0;
        for (T entity : entities) {
            if (delete(entity)) count++;
        }
        return count;
    }

    @Override
    public int deleteBy(String column, Object value) {
        validateColumn(column);
        String sql = "DELETE FROM " + fullTableName + " WHERE " + column + " = ?";
        return jdbcTemplate.update(sql, value);
    }

    @Override
    public int deleteAll() {
        String sql = "DELETE FROM " + fullTableName;
        return jdbcTemplate.update(sql);
    }

    @Override
    public QueryBuilder<T> query() {
        return new QueryBuilderImpl<>(this, fullTableName, entityClass, jdbcTemplate, rowMapper);
    }

    @Override
    public String getTableName() {
        return tableName;
    }

    @Override
    public Class<T> getEntityClass() {
        return entityClass;
    }

    /**
     * Validate that a column name is safe (prevents SQL injection).
     */
    private void validateColumn(String column) {
        if (column == null || !column.matches("^[a-z][a-z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid column name: " + column);
        }
    }
}
