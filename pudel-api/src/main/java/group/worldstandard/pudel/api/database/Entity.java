/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 Napapon Kamanee
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for mapping entity classes to database tables.
 * <p>
 * Entities are simple POJOs that map to database rows.
 * Field names are automatically converted to snake_case column names.
 * <p>
 * Example:
 * <pre>
 * {@code @Entity}
 * public class UserSetting {
 *     private Long id;           // maps to 'id' column (auto)
 *     private Long userId;       // maps to 'user_id' column
 *     private String settingName; // maps to 'setting_name' column
 *     private String settingValue;
 *     private Instant createdAt;  // auto-managed
 *     private Instant updatedAt;  // auto-managed
 *
 *     // Getters and setters...
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Entity {
    // Marker annotation - table name comes from repository creation
}
