/*
 * Pudel - A Moderate Discord Chat Bot
 * Example Plugin: Database Usage
 *
 * This example demonstrates how to use the Plugin Database API
 * for storing persistent data.
 *
 * Note: This is an EXAMPLE file for reference.
 */
package com.example.databaseplugin;

import worldstandard.group.pudel.api.PluginContext;
import worldstandard.group.pudel.api.PluginInfo;
import worldstandard.group.pudel.api.SimplePlugin;

import java.util.List;
import java.util.Optional;

/**
 * Example plugin demonstrating the Plugin Database API.
 */
public class DatabaseExamplePlugin extends SimplePlugin {

    private PluginDatabaseManager db;
    private PluginRepository<UserNote> notesRepo;
    private PluginKeyValueStore config;

    @Override
    public PluginInfo getPluginInfo() {
        return PluginInfo.builder()
                .name("DatabaseExample")
                .version("1.0.0")
                .author("Pudel Team")
                .description("Example plugin demonstrating database usage")
                .build();
    }

    @Override
    public void onEnable(PluginContext context) {
        // Get the database manager
        db = context.getDatabaseManager();

        context.log("info", "Database prefix: " + db.getPrefix());

        // =====================================================
        // OPTION 1: Define Schema and Use Repository
        // =====================================================

        // Define table schema
        TableSchema notesSchema = TableSchema.builder("user_notes")
                .column("user_id", ColumnType.BIGINT, false)  // Discord user ID
                .column("guild_id", ColumnType.BIGINT, true)  // null for DM notes
                .column("title", ColumnType.STRING, 200, false)
                .column("content", ColumnType.TEXT, true)
                .column("pinned", ColumnType.BOOLEAN, false, "false")
                .index("user_id")
                .index("guild_id")
                .uniqueIndex("user_id", "title")  // One note per title per user
                .build();

        // Create table (idempotent - safe to call every startup)
        boolean created = db.createTable(notesSchema);
        if (created) {
            context.log("info", "Created user_notes table");
        }

        // Get repository for CRUD operations
        notesRepo = db.getRepository("user_notes", UserNote.class);

        // =====================================================
        // OPTION 2: Simple Key-Value Store
        // =====================================================

        config = db.getKeyValueStore();

        // Store configuration values
        config.set("max_notes_per_user", 100);
        config.set("enable_pinning", true);

        // =====================================================
        // OPTION 3: Schema Migrations
        // =====================================================

        // Run migrations (only execute if not already at that version)
        db.migrate(1, helper -> {
            // Migration 1: Already done above with createTable
            context.log("info", "Migration 1: Initial schema");
        });

        db.migrate(2, helper -> {
            // Migration 2: Add a new column
            helper.addColumn("user_notes", "color", ColumnType.STRING, 20, true, "'default'");
            context.log("info", "Migration 2: Added color column");
        });

        context.log("info", "Database Example Plugin enabled! Schema version: " + db.getSchemaVersion());
    }

    @Override
    public void onDisable(PluginContext context) {
        context.log("info", "Database Example Plugin disabled");
        // Note: We don't drop tables on disable - data persists
    }

    // =====================================================
    // Example CRUD Operations
    // =====================================================

    /**
     * Create a new note for a user.
     */
    public UserNote createNote(long userId, Long guildId, String title, String content) {
        UserNote note = new UserNote();
        note.setUserId(userId);
        note.setGuildId(guildId);
        note.setTitle(title);
        note.setContent(content);
        note.setPinned(false);

        return notesRepo.save(note);  // ID is set after save
    }

    /**
     * Get all notes for a user.
     */
    public List<UserNote> getUserNotes(long userId) {
        return notesRepo.findBy("user_id", userId);
    }

    /**
     * Get a specific note by ID.
     */
    public Optional<UserNote> getNote(long noteId) {
        return notesRepo.findById(noteId);
    }

    /**
     * Search notes with complex query.
     */
    public List<UserNote> searchNotes(long userId, String keyword, boolean pinnedOnly) {
        QueryBuilder<UserNote> query = notesRepo.query()
                .where("user_id", userId)
                .whereLike("content", "%" + keyword + "%");

        if (pinnedOnly) {
            query.where("pinned", true);
        }

        return query.orderByDesc("created_at")
                .limit(20)
                .list();
    }

    /**
     * Update a note.
     */
    public UserNote updateNote(UserNote note) {
        return notesRepo.save(note);  // save() handles both insert and update
    }

    /**
     * Delete a note.
     */
    public boolean deleteNote(long noteId) {
        return notesRepo.deleteById(noteId);
    }

    /**
     * Delete all notes for a user.
     */
    public int deleteAllUserNotes(long userId) {
        return notesRepo.deleteBy("user_id", userId);
    }

    /**
     * Get max notes configuration.
     */
    public int getMaxNotesPerUser() {
        return config.getInt("max_notes_per_user", 100);
    }

    // =====================================================
    // Entity Class
    // =====================================================

    /**
     * Entity class for user notes.
     *
     * Field names are automatically converted to snake_case columns.
     * The 'id', 'created_at', and 'updated_at' columns are auto-managed.
     */
    @Entity
    public static class UserNote {
        private Long id;           // Auto-generated
        private Long userId;       // -> user_id column
        private Long guildId;      // -> guild_id column (nullable)
        private String title;
        private String content;
        private Boolean pinned;
        // created_at and updated_at are auto-managed

        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public Long getGuildId() { return guildId; }
        public void setGuildId(Long guildId) { this.guildId = guildId; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public Boolean getPinned() { return pinned; }
        public void setPinned(Boolean pinned) { this.pinned = pinned; }
    }
}
