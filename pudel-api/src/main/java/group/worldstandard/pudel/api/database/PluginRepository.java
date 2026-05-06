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
import java.util.Map;
import java.util.Optional;

/**
 * Repository interface for CRUD operations on plugin database tables.
 * <p>
 * Provides a JPA-like interface for data access without exposing raw SQL.
 * All operations are automatically scoped to the plugin's namespace.
 * <p>
 * Example:
 * <pre>
 * {@code
 * @Entity
 * public class UserSetting {
 *     private Long id;
 *     private Long userId;
 *     private String settingName;
 *     private String settingValue;
 *     // getters and setters...
 * }
 *
 * // Usage
 * PluginRepository<UserSetting> repo = db.getRepository("user_settings", UserSetting.class);
 *
 * // Save
 * UserSetting setting = new UserSetting();
 * setting.setUserId(12345L);
 * setting.setSettingName("theme");
 * setting.setSettingValue("dark");
 * setting = repo.save(setting);  // id is set after save
 *
 * // Find
 * Optional<UserSetting> found = repo.findById(setting.getId());
 * List<UserSetting> userSettings = repo.findBy("user_id", 12345L);
 *
 * // Update
 * setting.setSettingValue("light");
 * repo.save(setting);
 *
 * // Delete
 * repo.deleteById(setting.getId());
 * }
 * </pre>
 *
 * @param <T> the entity type
 */
public interface PluginRepository<T> {

    /**
     * Save an entity (insert or update).
     * <p>
     * If the entity has a null or zero id, it will be inserted.
     * Otherwise, it will be updated.
     *
     * @param entity the entity to save
     * @return the saved entity with id populated
     */
    T save(T entity);

    /**
     * Save multiple entities in a batch.
     *
     * @param entities the entities to save
     * @return the saved entities
     */
    List<T> saveAll(Iterable<T> entities);

    /**
     * Find an entity by its ID.
     *
     * @param id the entity ID
     * @return the entity if found
     */
    Optional<T> findById(long id);

    /**
     * Find all entities.
     *
     * @return list of all entities
     */
    List<T> findAll();

    /**
     * Find all entities with pagination.
     *
     * @param limit maximum number of results
     * @param offset number of results to skip
     * @return list of entities
     */
    List<T> findAll(int limit, int offset);

    /**
     * Find entities where a column equals a value.
     *
     * @param column the column name
     * @param value the value to match
     * @return matching entities
     */
    List<T> findBy(String column, Object value);

    /**
     * Find entities where a column equals a value, with pagination.
     *
     * @param column the column name
     * @param value the value to match
     * @param limit maximum results
     * @param offset results to skip
     * @return matching entities
     */
    List<T> findBy(String column, Object value, int limit, int offset);

    /**
     * Find entities matching multiple column conditions (AND).
     *
     * @param conditions map of column -> value
     * @return matching entities
     */
    List<T> findByAll(Map<String, Object> conditions);

    /**
     * Find one entity where a column equals a value.
     *
     * @param column the column name
     * @param value the value to match
     * @return the first matching entity
     */
    Optional<T> findOneBy(String column, Object value);

    /**
     * Find one entity matching multiple conditions.
     *
     * @param conditions map of column -> value
     * @return the first matching entity
     */
    Optional<T> findOneByAll(Map<String, Object> conditions);

    /**
     * Check if an entity with the given ID exists.
     *
     * @param id the entity ID
     * @return true if exists
     */
    boolean existsById(long id);

    /**
     * Check if any entity matches the condition.
     *
     * @param column the column name
     * @param value the value to match
     * @return true if any match exists
     */
    boolean existsBy(String column, Object value);

    /**
     * Count all entities.
     *
     * @return total count
     */
    long count();

    /**
     * Count entities matching a condition.
     *
     * @param column the column name
     * @param value the value to match
     * @return matching count
     */
    long countBy(String column, Object value);

    /**
     * Delete an entity by ID.
     *
     * @param id the entity ID
     * @return true if deleted, false if not found
     */
    boolean deleteById(long id);

    /**
     * Delete an entity.
     *
     * @param entity the entity to delete (must have id)
     * @return true if deleted
     */
    boolean delete(T entity);

    /**
     * Delete multiple entities.
     *
     * @param entities the entities to delete
     * @return number of entities deleted
     */
    int deleteAll(Iterable<T> entities);

    /**
     * Delete entities matching a condition.
     *
     * @param column the column name
     * @param value the value to match
     * @return number of entities deleted
     */
    int deleteBy(String column, Object value);

    /**
     * Delete all entities in the table.
     * <p>
     * <b>Warning:</b> This deletes ALL data in the table!
     *
     * @return number of entities deleted
     */
    int deleteAll();

    /**
     * Create a query builder for more complex queries.
     *
     * @return a new query builder
     */
    QueryBuilder<T> query();

    /**
     * Get the table name (without prefix).
     *
     * @return table name
     */
    String getTableName();

    /**
     * Get the entity class.
     *
     * @return entity class
     */
    Class<T> getEntityClass();
}