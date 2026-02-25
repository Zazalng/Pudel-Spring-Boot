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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for customizing field-to-column mapping.
 * <p>
 * By default, field names are converted to snake_case.
 * Use this annotation to specify a custom column name or mark a field as transient.
 * <p>
 * Example:
 * <pre>
 * {@code @Entity}
 * public class UserData {
 *     private Long id;
 *
 *     {@code @Column(name = "discord_user_id")}
 *     private Long userId;
 *
 *     {@code @Column(ignore = true)}
 *     private transient String cachedValue;  // Not persisted
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Column {

    /**
     * Custom column name.
     * <p>
     * If empty, the field name is converted to snake_case.
     *
     * @return the column name
     */
    String name() default "";

    /**
     * Whether this field should be ignored during persistence.
     *
     * @return true to ignore this field
     */
    boolean ignore() default false;
}
