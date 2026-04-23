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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Simple key-value store for plugin configuration and small data.
 * <p>
 * This provides a simpler alternative to defining schemas for plugins
 * that just need to store configuration values or small pieces of data.
 * <p>
 * All keys are namespaced to the plugin automatically.
 * <p>
 * Example:
 * <pre>
 * {@code
 * PluginKeyValueStore store = db.getKeyValueStore();
 *
 * // Store values
 * store.set("api_key", "abc123");
 * store.set("max_retries", 5);
 * store.set("features_enabled", true);
 *
 * // Retrieve values
 * String apiKey = store.getString("api_key").orElse("default");
 * int maxRetries = store.getInt("max_retries").orElse(3);
 * boolean enabled = store.getBoolean("features_enabled").orElse(false);
 *
 * // Guild/User scoped values
 * store.set("guild:123456:prefix", "!");
 * store.set("user:789:theme", "dark");
 * }
 * </pre>
 */
public interface PluginKeyValueStore {

    /**
     * Set a string value.
     *
     * @param key the key
     * @param value the value
     */
    void set(String key, String value);

    /**
     * Set an integer value.
     *
     * @param key the key
     * @param value the value
     */
    void set(String key, int value);

    /**
     * Set a long value.
     *
     * @param key the key
     * @param value the value
     */
    void set(String key, long value);

    /**
     * Set a boolean value.
     *
     * @param key the key
     * @param value the value
     */
    void set(String key, boolean value);

    /**
     * Set a double value.
     *
     * @param key the key
     * @param value the value
     */
    void set(String key, double value);

    /**
     * Get a string value.
     *
     * @param key the key
     * @return the value if present
     */
    Optional<String> getString(String key);

    /**
     * Get a string value with a default.
     *
     * @param key the key
     * @param defaultValue default if not present
     * @return the value or default
     */
    default String getString(String key, String defaultValue) {
        return getString(key).orElse(defaultValue);
    }

    /**
     * Get an integer value.
     *
     * @param key the key
     * @return the value if present and valid
     */
    Optional<Integer> getInt(String key);

    /**
     * Get an integer value with a default.
     *
     * @param key the key
     * @param defaultValue default if not present
     * @return the value or default
     */
    default int getInt(String key, int defaultValue) {
        return getInt(key).orElse(defaultValue);
    }

    /**
     * Get a long value.
     *
     * @param key the key
     * @return the value if present and valid
     */
    Optional<Long> getLong(String key);

    /**
     * Get a long value with a default.
     *
     * @param key the key
     * @param defaultValue default if not present
     * @return the value or default
     */
    default long getLong(String key, long defaultValue) {
        return getLong(key).orElse(defaultValue);
    }

    /**
     * Get a boolean value.
     *
     * @param key the key
     * @return the value if present
     */
    Optional<Boolean> getBoolean(String key);

    /**
     * Get a boolean value with a default.
     *
     * @param key the key
     * @param defaultValue default if not present
     * @return the value or default
     */
    default boolean getBoolean(String key, boolean defaultValue) {
        return getBoolean(key).orElse(defaultValue);
    }

    /**
     * Get a double value.
     *
     * @param key the key
     * @return the value if present and valid
     */
    Optional<Double> getDouble(String key);

    /**
     * Get a double value with a default.
     *
     * @param key the key
     * @param defaultValue default if not present
     * @return the value or default
     */
    default double getDouble(String key, double defaultValue) {
        return getDouble(key).orElse(defaultValue);
    }

    /**
     * Check if a key exists.
     *
     * @param key the key
     * @return true if exists
     */
    boolean exists(String key);

    /**
     * Delete a key.
     *
     * @param key the key
     * @return true if deleted, false if didn't exist
     */
    boolean delete(String key);

    /**
     * Delete multiple keys by prefix.
     *
     * @param prefix the key prefix
     * @return number of keys deleted
     */
    int deleteByPrefix(String prefix);

    /**
     * Get all keys.
     *
     * @return set of all keys
     */
    Set<String> keys();

    /**
     * Get all keys matching a prefix.
     *
     * @param prefix the key prefix
     * @return matching keys
     */
    Set<String> keys(String prefix);

    /**
     * Get all key-value pairs.
     *
     * @return map of all key-value pairs
     */
    Map<String, String> getAll();

    /**
     * Get all key-value pairs matching a prefix.
     *
     * @param prefix the key prefix
     * @return matching key-value pairs
     */
    Map<String, String> getAll(String prefix);

    /**
     * Set multiple values at once.
     *
     * @param values map of key -> value
     */
    void setAll(Map<String, String> values);

    /**
     * Count the number of stored key-value pairs.
     *
     * @return count of entries
     */
    long count();
}
