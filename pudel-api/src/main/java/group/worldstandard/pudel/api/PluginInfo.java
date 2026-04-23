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
package group.worldstandard.pudel.api;

/**
 * Contains metadata about a Pudel plugin.
 * <p>
 * Plugin metadata is automatically populated from the
 * {@link group.worldstandard.pudel.api.annotation.Plugin @Plugin} annotation
 * on your plugin class:
 * <pre>
 * {@code @Plugin(name = "My Plugin", version = "1.0.0", author = "Author",
 *         description = "A cool plugin")
 * public class MyPlugin {
 *     // ...
 * }
 * }
 * </pre>
 * <p>
 * Access at runtime via {@link PluginContext#getInfo()}:
 * <pre>
 * {@code @OnEnable
 * public void onEnable(PluginContext context) {
 *     PluginInfo info = context.getInfo();
 *     context.log("info", "Loaded " + info.getName() + " v" + info.getVersion());
 * }
 * }
 * </pre>
 */
public class PluginInfo {
    private final String name;
    private final String version;
    private final String author;
    private final String description;
    private final String[] dependencies;

    /**
     * Creates plugin info without dependencies.
     *
     * @param name        the plugin name
     * @param version     the plugin version
     * @param author      the plugin author
     * @param description the plugin description
     */
    public PluginInfo(String name, String version, String author, String description) {
        this(name, version, author, description, new String[0]);
    }

    /**
     * Creates plugin info with all fields including dependencies.
     *
     * @param name         the plugin name
     * @param version      the plugin version
     * @param author       the plugin author
     * @param description  the plugin description
     * @param dependencies array of required plugin names this plugin depends on
     */
    public PluginInfo(String name, String version, String author, String description, String[] dependencies) {
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.dependencies = dependencies != null ? dependencies : new String[0];
    }

    /**
     * Gets the plugin name as defined in {@code @Plugin(name = "...")}.
     *
     * @return the plugin name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the plugin version as defined in {@code @Plugin(version = "...")}.
     *
     * @return the version string
     */
    public String getVersion() {
        return version;
    }

    /**
     * Gets the plugin author as defined in {@code @Plugin(author = "...")}.
     *
     * @return the author name
     */
    public String getAuthor() {
        return author;
    }

    /**
     * Gets the plugin description as defined in {@code @Plugin(description = "...")}.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the names of plugins this plugin depends on, as defined in
     * {@code @Plugin(dependencies = {"dep1", "dep2"})}.
     *
     * @return array of dependency plugin names (never null)
     */
    public String[] getDependencies() {
        return dependencies;
    }
}

