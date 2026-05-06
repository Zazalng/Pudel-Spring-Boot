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
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import group.worldstandard.pudel.api.database.Column;
import group.worldstandard.pudel.api.database.PluginRepository;
import group.worldstandard.pudel.api.database.QueryBuilder;

import java.lang.reflect.Field;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of a repository for managing plugin entities in a database.
 * This class provides functionality to perform CRUD operations on entities,
 * including saving, inserting, updating, and mapping entity fields to database columns.
 * It utilizes JDBC templates and reflection to interact with the database and manage entity states.
 * <p>
 * The repository dynamically maps entity fields to database columns, supporting custom column names
 * through annotations and automatic conversion of field names from camelCase to snake_case.
 * It also handles conversion of Java time types to JDBC-compatible types during persistence operations.
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

    /**
     * Maps entity field names to their corresponding database column names.
     * This mapping is used to translate between Java object fields and database columns
     * during persistence operations. The map maintains insertion order to ensure
     * consistent mapping behavior.
     */
    private final Map<String, String> fieldToColumn = new LinkedHashMap<>();
    /**
     * A mapping from database column names to their corresponding {@link Field} objects.
     * This map is used to associate table columns with entity class fields during ORM operations.
     * The map preserves insertion order to maintain consistent field processing sequence.
     */
    private final Map<String, Field> columnToField = new LinkedHashMap<>();

    /**
     * Constructs a PluginRepositoryImpl instance with the specified database manager, table name, entity class, and JDBC template.
     * Initializes internal components including row mappers, field mappings, and named parameter JDBC templates.
     *
     * @param dbManager     the database manager used to manage database interactions and table naming conventions
     * @param tableName     the name of the database table associated with this repository (without schema prefix)
     * @param entityClass   the class type of the entities managed by this repository
     * @param jdbcTemplate  the JDBC template used for executing SQL queries and updates
     */
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
     * Creates and configures a ConversionService instance with custom converters for Timestamp objects.
     * Registers converters to handle conversion from Timestamp to Instant, LocalDateTime, and OffsetDateTime.
     * The OffsetDateTime converter specifically uses UTC as the time zone offset.
     *
     * @return a configured ConversionService instance with registered timestamp-related converters
     */
    private ConversionService createConversionService() {
        DefaultConversionService conversionService =
            new DefaultConversionService();

        // Add Timestamp -> Instant converter
        conversionService.addConverter(Timestamp.class, Instant.class, Timestamp::toInstant);

        // Add Timestamp -> LocalDateTime converter
        conversionService.addConverter(Timestamp.class, LocalDateTime.class, Timestamp::toLocalDateTime);

        // Add Timestamp -> OffsetDateTime converter
        conversionService.addConverter(Timestamp.class, OffsetDateTime.class,
            ts -> ts.toInstant().atOffset(java.time.ZoneOffset.UTC));

        return conversionService;
    }

    /**
     * Builds mappings between entity fields and database columns.
     * <p>
     * This method iterates over all declared fields of the entity class and establishes bidirectional mappings
     * between field names and their corresponding database column names. Fields annotated with {@code @Column}
     * are processed according to the annotation's configuration:
     * <ul>
     *   <li>If the {@code ignore} attribute is set to {@code true}, the field is skipped.</li>
     *   <li>If the {@code name} attribute is provided, it is used as the column name.</li>
     *   <li>Otherwise, the field name is converted from camelCase to snake_case using {@link #toSnakeCase(String)}.</li>
     * </ul>
     * The mappings are stored in {@link #fieldToColumn} and {@link #columnToField} for later use in database operations.
     * Field accessibility is set to true to allow reflection-based access during mapping.
     */
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

    /**
     * Converts a camelCase string to snake_case format.
     * <p>
     * This method takes a string in camelCase notation and transforms it into snake_case
     * by inserting underscores before uppercase letters that follow lowercase letters,
     * then converting the entire string to lowercase.
     *
     * @param camelCase the input string in camelCase format to be converted
     * @return the converted string in snake_case format
     */
    private String toSnakeCase(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Saves the given entity to the database. If the entity's ID is null or zero, it will be inserted as a new record.
     * Otherwise, it will be updated based on its existing ID.
     *
     * @param entity the entity to save; must not be null
     * @return the saved entity with updated state, such as generated ID
     */
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

    /**
     * Inserts the given entity into the database table.
     * <p>
     * This method constructs an SQL INSERT statement dynamically based on the entity's field mappings,
     * excluding auto-generated columns such as 'id', 'created_at', and 'updated_at'.
     * It utilizes reflection to extract field values from the entity and converts them to JDBC-compatible types.
     * The inserted row's generated ID is retrieved and set back to the entity.
     *
     * @param entity the entity to be inserted; must not be null
     * @return the inserted entity with the generated ID assigned
     * @throws Exception if any error occurs during the insertion process, including reflection or SQL exceptions
     */
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

    /**
     * Updates an existing entity in the database by its ID.
     * <p>
     * This method constructs an SQL UPDATE statement dynamically based on the entity's field mappings,
     * excluding immutable columns such as 'id' and 'created_at'. The 'updated_at' column is automatically
     * set to the current timestamp. Reflection is used to extract field values from the entity, which
     * are then converted to JDBC-compatible types. The update operation targets the row identified by
     * the entity's ID.
     *
     * @param entity the entity containing updated data; must not be null and must have a valid ID
     * @return the updated entity instance
     * @throws Exception if any error occurs during the update process, including reflection or SQL exceptions
     */
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

    /**
     * Retrieves the ID of the given entity by accessing its "id" field through reflection.
     * The method supports multiple numeric types for the ID field, converting them to Long.
     * If the ID field is not present or accessible, an exception is thrown.
     *
     * @param entity the entity instance from which to retrieve the ID; must not be null
     * @return the ID of the entity as a Long, or null if the ID field value is null or unsupported
     * @throws Exception if an error occurs during reflection-based field access
     */
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

    /**
     * Sets the ID of the specified entity using reflection.
     * <p>
     * This method accesses the entity's "id" field via reflection and sets its value to the provided ID.
     * The field is made accessible regardless of its visibility modifier to allow modification.
     * If the "id" field does not exist in the entity, no action is taken.
     *
     * @param entity the entity instance whose ID is to be set; must not be null
     * @param id the ID value to assign to the entity's "id" field
     * @throws Exception if an error occurs while accessing or setting the field value via reflection
     */
    private void setEntityId(T entity, long id) throws Exception {
        Field idField = columnToField.get("id");
        if (idField != null) {
            idField.setAccessible(true);
            idField.set(entity, id);
        }
    }

    /**
     * Converts a given value to its corresponding JDBC-compatible value.
     * This method handles specific Java time types by converting them to java.sql.Timestamp
     * for proper storage and retrieval through JDBC.
     * Null values are returned as-is.
     * Values of type Instant are converted to Timestamp using Timestamp.from().
     * Values of type LocalDateTime are converted to Timestamp using Timestamp.valueOf().
     * Values of type OffsetDateTime are converted to Timestamp by first converting to Instant.
     * All other values are returned unchanged.
     *
     * @param value the object to be converted to a JDBC-compatible value
     * @return the JDBC-compatible value, or the original value if no conversion is needed
     */
    private Object convertToJdbcValue(Object value) {
        return switch (value) {
            case null -> null;

            // Convert java.time.Instant to java.sql.Timestamp
            case Instant instant -> Timestamp.from(instant);

            // Convert java.time.LocalDateTime to java.sql.Timestamp
            case LocalDateTime ldt -> Timestamp.valueOf(ldt);

            // Convert java.time.OffsetDateTime to java.sql.Timestamp
            case OffsetDateTime odt -> Timestamp.from(odt.toInstant());
            default -> value;
        };
    }

    /**
     * Sets the value of a parameter in the prepared statement at the specified index.
     * If the provided value is null, sets the parameter to SQL NULL using Types.NULL.
     * Otherwise, sets the parameter to the provided object value.
     *
     * @param ps the PreparedStatement object whose parameter needs to be set
     * @param index the parameter index (1-based) where the value should be set
     * @param value the value to set; if null, the parameter will be set to SQL NULL
     * @throws SQLException if a database access error occurs or the parameter index is invalid
     */
    private void setParameterValue(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NULL);
        } else {
            ps.setObject(index, value);
        }
    }

    /**
     * Saves all given entities and returns a list of the saved entities.
     * Each entity is saved individually using the save method.
     * The order of the returned entities corresponds to the order of the input entities.
     *
     * @param entities the entities to save, must not be null
     * @return a list containing the saved entities, never null
     */
    @Override
    public List<T> saveAll(Iterable<T> entities) {
        List<T> result = new ArrayList<>();
        for (T entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    /**
     * Finds an entity by its unique identifier.
     * @param id the unique identifier of the entity to find
     * @return an Optional containing the found entity, or empty Optional if no entity exists with the given id
     */
    @Override
    public Optional<T> findById(long id) {
        String sql = "SELECT * FROM " + fullTableName + " WHERE id = ?";
        List<T> results = jdbcTemplate.query(sql, rowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /**
     * Retrieves all entities from the database table and returns them as a list.
     * The results are ordered by the id column in ascending order.
     * Uses JDBC template to execute the query and maps the result set to entity objects.
     *
     * @return a list of all entities found in the database table, ordered by id;
     *         returns an empty list if no entities are found
     */
    @Override
    public List<T> findAll() {
        String sql = "SELECT * FROM " + fullTableName + " ORDER BY id";
        return jdbcTemplate.query(sql, rowMapper);
    }

    /**
     * Retrieves a list of entities with pagination support.
     * @param limit the maximum number of entities to retrieve
     * @param offset the number of entities to skip before starting to return results
     * @return a list of entities within the specified limit and offset range
     */
    @Override
    public List<T> findAll(int limit, int offset) {
        String sql = "SELECT * FROM " + fullTableName + " ORDER BY id LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, limit, offset);
    }

    /**
     * Finds all entities where the specified column matches the given value.
     * The results are ordered by the id column in ascending order.
     *
     * @param column the name of the database column to search by
     * @param value the value to match against the specified column
     * @return a list of entities that match the search criteria, or an empty list if no matches are found
     */
    @Override
    public List<T> findBy(String column, Object value) {
        validateColumn(column);
        String sql = "SELECT * FROM " + fullTableName + " WHERE " + column + " = ? ORDER BY id";
        return jdbcTemplate.query(sql, rowMapper, value);
    }

    /**
     * Finds entities by the specified column value with pagination support.
     * @param column the name of the column to search by
     * @param value the value to match in the specified column
     * @param limit the maximum number of results to return
     * @param offset the number of results to skip before starting to return results
     * @return a list of entities matching the column value criteria, limited by the specified count and offset
     */
    @Override
    public List<T> findBy(String column, Object value, int limit, int offset) {
        validateColumn(column);
        String sql = "SELECT * FROM " + fullTableName + " WHERE " + column + " = ? ORDER BY id LIMIT ? OFFSET ?";
        return jdbcTemplate.query(sql, rowMapper, value, limit, offset);
    }

    /**
     * Finds all entities that match the specified conditions.
     * @param conditions a map of column names to values used to filter the results
     * @return a list of entities matching the given conditions, or all entities if conditions is empty
     */
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

    /**
     * Finds a single entity by the specified column and value.
     * This method searches for entities where the given column matches the provided value.
     * If no matching entity is found, an empty Optional is returned.
     * If multiple entities match the criteria, the first one is returned.
     *
     * @param column the name of the column to search by
     * @param value the value to match against the column
     * @return an Optional containing the first matching entity, or empty if no matches found
     */
    @Override
    public Optional<T> findOneBy(String column, Object value) {
        List<T> results = findBy(column, value);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /**
     * Finds a single entity that matches all the given conditions.
     * This method retrieves entities matching all specified conditions and returns the first one found.
     * If no entities match the conditions, an empty Optional is returned.
     *
     * @param conditions a map containing field names as keys and their corresponding values to match against
     * @return an Optional containing the first entity that matches all conditions, or an empty Optional if no matching entity is found
     */
    @Override
    public Optional<T> findOneByAll(Map<String, Object> conditions) {
        List<T> results = findByAll(conditions);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /**
     * Checks if an entity with the specified ID exists in the database.
     *
     * @param id the ID of the entity to check for existence
     * @return true if an entity with the specified ID exists, false otherwise
     */
    @Override
    public boolean existsById(long id) {
        String sql = "SELECT EXISTS(SELECT 1 FROM " + fullTableName + " WHERE id = ?)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, id));
    }

    /**
     * Checks if a record exists in the database table where the specified column matches the given value.
     * This method constructs a SQL query using the EXISTS clause to efficiently determine if any rows
     * match the provided column-value criteria.
     *
     * @param column the name of the database column to check
     * @param value the value to match against the specified column
     * @return true if at least one record exists with the specified column value, false otherwise
     */
    @Override
    public boolean existsBy(String column, Object value) {
        validateColumn(column);
        String sql = "SELECT EXISTS(SELECT 1 FROM " + fullTableName + " WHERE " + column + " = ?)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, value));
    }

    /**
     * Returns the total number of rows in the database table.
     * Executes a SQL COUNT query to determine the number of records present in the table.
     * If the query returns null, this method will return zero instead.
     *
     * @return the total row count as a long value, or zero if no rows exist or query result is null
     */
    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM " + fullTableName;
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }

    /**
     * Counts the number of records in the table where the specified column matches the given value.
     * @param column the name of the column to filter by
     * @param value the value to match against the column
     * @return the number of records that match the specified column and value, or 0 if no records are found
     */
    @Override
    public long countBy(String column, Object value) {
        validateColumn(column);
        String sql = "SELECT COUNT(*) FROM " + fullTableName + " WHERE " + column + " = ?";
        Long count = jdbcTemplate.queryForObject(sql, Long.class, value);
        return count != null ? count : 0;
    }

    /**
     * Deletes a record from the database table by its unique identifier.
     * @param id the unique identifier of the record to be deleted
     * @return true if the record was successfully deleted, false otherwise
     */
    @Override
    public boolean deleteById(long id) {
        String sql = "DELETE FROM " + fullTableName + " WHERE id = ?";
        int rows = jdbcTemplate.update(sql, id);
        return rows > 0;
    }

    /**
     * Deletes the specified entity from the data store.
     * This method first retrieves the entity's ID and then delegates to the deleteById method.
     * If the entity does not have a valid ID or if an error occurs during deletion, the method returns false.
     *
     * @param entity the entity to be deleted, must not be null
     * @return true if the entity was successfully deleted, false otherwise
     */
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

    /**
     * Deletes all entities from the data store that are present in the provided iterable collection.
     * This method iterates through each entity in the collection and attempts to delete it individually.
     * The total count of successfully deleted entities is returned.
     *
     * @param entities the collection of entities to be deleted; must not be null
     * @return the number of entities that were successfully deleted
     */
    @Override
    public int deleteAll(Iterable<T> entities) {
        int count = 0;
        for (T entity : entities) {
            if (delete(entity)) count++;
        }
        return count;
    }

    /**
     * Deletes records from the database table where the specified column matches the given value.
     * @param column the name of the column to match against
     * @param value the value to match in the specified column
     * @return the number of rows deleted from the table
     */
    @Override
    public int deleteBy(String column, Object value) {
        validateColumn(column);
        String sql = "DELETE FROM " + fullTableName + " WHERE " + column + " = ?";
        return jdbcTemplate.update(sql, value);
    }

    /**
     * Deletes all records from the table.
     * <p>
     * This method executes a SQL DELETE statement that removes all rows from the table.
     * The operation affects every record currently stored in the table.
     *
     * @return the number of rows deleted from the table
     */
    @Override
    public int deleteAll() {
        String sql = "DELETE FROM " + fullTableName;
        return jdbcTemplate.update(sql);
    }

    /**
     * Creates and returns a new QueryBuilder instance for constructing database queries.
     * The QueryBuilder is initialized with the current repository's configuration including
     * the full table name, entity class, JDBC template, and row mapper.
     *
     * @return a new QueryBuilder instance configured with this repository's settings
     */
    @Override
    public QueryBuilder<T> query() {
        return new QueryBuilderImpl<>(this, fullTableName, entityClass, jdbcTemplate, rowMapper);
    }

    /**
     * Returns the name of the database table associated with this entity.
     *
     * @return the table name as a String
     */
    @Override
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns the Class object representing the entity type managed by this instance.
     *
     * @return the Class object of the entity type
     */
    @Override
    public Class<T> getEntityClass() {
        return entityClass;
    }

    /**
     * Validates that the specified column name is not null and matches the required format.
     * A valid column name must start with a lowercase letter and can only contain
     * lowercase letters, digits, and underscores.
     *
     * @param column the column name to validate
     * @throws IllegalArgumentException if the column name is null or does not match
     *         the pattern of starting with a lowercase letter followed by zero or more
     *         lowercase letters, digits, or underscores
     */
    private void validateColumn(String column) {
        if (column == null || !column.matches("^[a-z][a-z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid column name: " + column);
        }
    }
}