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
package group.worldstandard.pudel.core.bootstrap;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;
import group.worldstandard.pudel.core.entity.GuildSettings;
import group.worldstandard.pudel.core.repository.GuildSettingsRepository;
import group.worldstandard.pudel.core.service.SchemaManagementService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bootstrap runner that ensures all guilds the bot is in have proper database schemas.
 * Runs on startup after JDA is ready.
 * <p>
 * It also guarantees the base schema (users, guilds, guild_settings, plugin
 * metadata, ...) defined in {@code database/init.sql} exists. In the bundled
 * Postgres setup that file is applied automatically by the database container;
 * in EXTERNAL database mode there is no such container, so this runner applies
 * it lazily on first boot when the database is still empty.
 */
@Component
@Order(10) // Run after other bootstrap tasks
public class SchemaBootstrapRunner implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(SchemaBootstrapRunner.class);

    /**
     * Name of a core base table created by init.sql. Used to detect whether the
     * base schema has already been applied.
     */
    private static final String BASE_TABLE_MARKER = "guild_settings";

    private final JDA jda;
    private final GuildSettingsRepository guildSettingsRepository;
    private final SchemaManagementService schemaManagementService;
    private final JdbcTemplate jdbcTemplate;

    public SchemaBootstrapRunner(@Lazy JDA jda,
                                 GuildSettingsRepository guildSettingsRepository,
                                 SchemaManagementService schemaManagementService,
                                 JdbcTemplate jdbcTemplate) {
        this.jda = jda;
        this.guildSettingsRepository = guildSettingsRepository;
        this.schemaManagementService = schemaManagementService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        logger.info("Starting schema bootstrap process...");

        // Ensure base tables (init.sql) exist before touching per-guild schemas.
        ensureBaseSchema();

        // Wait for JDA to be ready
        try {
            jda.awaitReady();
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting for JDA to be ready", e);
            Thread.currentThread().interrupt();
            return;
        }

        List<Guild> guilds = jda.getGuilds();
        int schemaCreated = 0;
        int settingsCreated = 0;

        logger.info("Checking {} guilds for schema initialization...", guilds.size());

        for (Guild guild : guilds) {
            String guildId = guild.getId();
            long guildIdLong = guild.getIdLong();
            String guildName = guild.getName();

            // Ensure guild settings exist
            GuildSettings settings = guildSettingsRepository.findByGuildId(guildId)
                    .orElseGet(() -> {
                        GuildSettings newSettings = new GuildSettings(guildId);
                        newSettings.setSchemaCreated(false);
                        return guildSettingsRepository.save(newSettings);
                    });

            if (settings.getSchemaCreated() == null || !settings.getSchemaCreated()) {
                settingsCreated++;
            }

            // Ensure schema exists
            if (!schemaManagementService.schemaExists(guildIdLong)) {
                try {
                    schemaManagementService.createGuildSchema(guildIdLong);

                    // Update settings to mark schema as created
                    settings.setSchemaCreated(true);
                    guildSettingsRepository.save(settings);

                    schemaCreated++;
                    logger.info("Created schema for guild: {} ({})", guildName, guildId);
                } catch (Exception e) {
                    logger.error("Failed to create schema for guild {} ({}): {}",
                            guildName, guildId, e.getMessage());
                }
            } else if (settings.getSchemaCreated() == null || !settings.getSchemaCreated()) {
                // Schema exists but not marked in settings - update the flag
                settings.setSchemaCreated(true);
                guildSettingsRepository.save(settings);
            }
        }

        logger.info("Schema bootstrap complete. Created {} new schemas, {} guilds processed.",
                schemaCreated, guilds.size());
    }

    /**
     * Apply {@code database/init.sql} once, only when the base schema is missing.
     * Idempotent: init.sql uses CREATE ... IF NOT EXISTS throughout, so re-running
     * on an already-initialized database is a no-op. The pgvector extension line
     * requires elevated privileges on some external databases; if it fails we
     * retry without it (assuming the extension is pre-installed) so the core
     * tables are still created.
     */
    private void ensureBaseSchema() {
        Boolean exists = jdbcTemplate.execute((Connection conn) -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?")) {
                ps.setString(1, BASE_TABLE_MARKER);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });

        if (Boolean.TRUE.equals(exists)) {
            logger.debug("Base schema already present ({}), skipping init.sql", BASE_TABLE_MARKER);
            return;
        }

        Resource initSql = resolveInitSql();
        if (initSql == null || !initSql.exists()) {
            logger.warn("init.sql not found on classpath or at /app/database/init.sql; "
                    + "cannot initialize base schema automatically");
            return;
        }

        logger.info("Base schema missing - applying {} (external/empty database)",
                initSql.getDescription());
        try {
            runScript(initSql, false);
            logger.info("Base schema applied successfully from {}", initSql.getDescription());
        } catch (Exception e) {
            // Likely the CREATE EXTENSION vector; retry without extension lines.
            logger.warn("Full init.sql failed ({}); retrying without CREATE EXTENSION lines. "
                    + "Ensure the 'vector' extension is pre-installed on the database server.",
                    e.getMessage());
            try {
                runScript(initSql, true);
                logger.info("Base schema applied (without vector extension) from {}",
                        initSql.getDescription());
            } catch (Exception e2) {
                logger.error("Failed to apply base schema from {}: {}",
                        initSql.getDescription(), e2.getMessage(), e2);
            }
        }
    }

    /** Resolve init.sql from the container path first, then the classpath. */
    private Resource resolveInitSql() {
        FileSystemResource fs = new FileSystemResource("/app/database/init.sql");
        if (fs.exists()) {
            return fs;
        }
        ClassPathResource cp = new ClassPathResource("database/init.sql");
        return cp.exists() ? cp : null;
    }

    /** Execute the init.sql script. When {@code skipExtensions} is true, any line */
    private void runScript(Resource resource, boolean skipExtensions) throws Exception {
        if (!skipExtensions) {
            jdbcTemplate.execute((Connection conn) -> {
                ScriptUtils.executeSqlScript(conn, resource);
                return null;
            });
            return;
        }
        // Strip CREATE EXTENSION lines and execute the remainder.
        String sql = readToString(resource);
        String filtered = sql.lines()
                .filter(line -> !line.trim().toUpperCase().startsWith("CREATE EXTENSION"))
                .collect(Collectors.joining(System.lineSeparator()));
        jdbcTemplate.execute((Connection conn) -> {
            ScriptUtils.executeSqlScript(conn, new ByteArrayResource(filtered.getBytes(StandardCharsets.UTF_8)));
            return null;
        });
    }

    private String readToString(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}