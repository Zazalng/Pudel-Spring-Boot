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

import org.springframework.jdbc.core.JdbcTemplate;
import group.worldstandard.pudel.api.database.PluginKeyValueStore;

import java.util.*;

/**
 * Implementation of PluginKeyValueStore using a dedicated table.
 */
public class PluginKeyValueStoreImpl implements PluginKeyValueStore {
    private final PluginDatabaseManagerImpl dbManager;
    private final JdbcTemplate jdbcTemplate;
    private final String tableName;

    public PluginKeyValueStoreImpl(PluginDatabaseManagerImpl dbManager, JdbcTemplate jdbcTemplate) {
        this.dbManager = dbManager;
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = dbManager.getFullTableName("kv_store");
    }

    @Override
    public void set(String key, String value) {
        String sql = "INSERT INTO " + tableName + " (key, value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = CURRENT_TIMESTAMP";
        jdbcTemplate.update(sql, key, value);
    }

    @Override
    public void set(String key, int value) {
        set(key, String.valueOf(value));
    }

    @Override
    public void set(String key, long value) {
        set(key, String.valueOf(value));
    }

    @Override
    public void set(String key, boolean value) {
        set(key, String.valueOf(value));
    }

    @Override
    public void set(String key, double value) {
        set(key, String.valueOf(value));
    }

    @Override
    public Optional<String> getString(String key) {
        String sql = "SELECT value FROM " + tableName + " WHERE key = ?";
        List<String> results = jdbcTemplate.queryForList(sql, String.class, key);
        return results.isEmpty() ? Optional.empty() : Optional.ofNullable(results.getFirst());
    }

    @Override
    public Optional<Integer> getInt(String key) {
        return getString(key).map(v -> {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    @Override
    public Optional<Long> getLong(String key) {
        return getString(key).map(v -> {
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    @Override
    public Optional<Boolean> getBoolean(String key) {
        return getString(key).map(v -> {
            if ("true".equalsIgnoreCase(v)) return true;
            if ("false".equalsIgnoreCase(v)) return false;
            return null;
        });
    }

    @Override
    public Optional<Double> getDouble(String key) {
        return getString(key).map(v -> {
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    @Override
    public boolean exists(String key) {
        String sql = "SELECT EXISTS(SELECT 1 FROM " + tableName + " WHERE key = ?)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, key));
    }

    @Override
    public boolean delete(String key) {
        String sql = "DELETE FROM " + tableName + " WHERE key = ?";
        return jdbcTemplate.update(sql, key) > 0;
    }

    @Override
    public int deleteByPrefix(String prefix) {
        String sql = "DELETE FROM " + tableName + " WHERE key LIKE ?";
        return jdbcTemplate.update(sql, prefix + "%");
    }

    @Override
    public Set<String> keys() {
        String sql = "SELECT key FROM " + tableName;
        return new HashSet<>(jdbcTemplate.queryForList(sql, String.class));
    }

    @Override
    public Set<String> keys(String prefix) {
        String sql = "SELECT key FROM " + tableName + " WHERE key LIKE ?";
        return new HashSet<>(jdbcTemplate.queryForList(sql, String.class, prefix + "%"));
    }

    @Override
    public Map<String, String> getAll() {
        String sql = "SELECT key, value FROM " + tableName;
        return jdbcTemplate.query(sql, rs -> {
            Map<String, String> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getString("key"), rs.getString("value"));
            }
            return map;
        });
    }

    @Override
    public Map<String, String> getAll(String prefix) {
        String sql = "SELECT key, value FROM " + tableName + " WHERE key LIKE ?";
        return jdbcTemplate.query(sql, rs -> {
            Map<String, String> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getString("key"), rs.getString("value"));
            }
            return map;
        }, prefix + "%");
    }

    @Override
    public void setAll(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            set(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count != null ? count : 0;
    }
}