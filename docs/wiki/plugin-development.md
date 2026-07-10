# Plugin Development Guide

Complete guide for creating Pudel plugins using the annotation-based Plugin Development Kit (PDK).

## Overview

Pudel plugins use a **Spring Boot-like annotation system** where the core handles all lifecycle management automatically.

**Key features:**
- **@Plugin** - Marks a class as a plugin (like Spring's @Controller)
- **@SlashCommand** - Slash command handler (like @GetMapping)
- **@TextCommand** - Text command handler
- **@ButtonHandler**, **@ModalHandler**, **@SelectMenuHandler** - Interaction handlers
- **@OnEnable**, **@OnDisable**, **@OnShutdown** - Lifecycle hooks

**No manual registration needed!** The core automatically:
- Discovers and registers all annotated methods
- Syncs slash commands to Discord
- Unregisters everything on disable
- Handles graceful shutdown
- Many default given check javadocs carefully

---

## Quick Start

### 1. Create Maven Project

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project>
    <modelVersion>4.0.0</modelVersion>
    
    <groupId>com.yourname</groupId>
    <artifactId>my-pudel-plugin</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    
    <properties>
        <maven.compiler.release>25</maven.compiler.release>
    </properties>
    
    <dependencies>
        <dependency>
            <groupId>group.worldstandard</groupId>
            <artifactId>pudel-api</artifactId>
            <version>2.3.1</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <configuration>
                    <archive>
                        <manifestEntries>
                            <Plugin-Main>com.yourname.MyPlugin</Plugin-Main>
                        </manifestEntries>
                    </archive>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 2. Create Plugin Class

```java
package com.yourname;

import group.worldstandard.pudel.api.PluginContext;
import group.worldstandard.pudel.api.annotation.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

@Plugin(
    name = "MyPlugin",
    version = "1.0.0",
    author = "Your Name",
    description = "My awesome plugin"
)
public class MyPlugin {

    // Slash command - registered automatically!
    @SlashCommand(name = "hello", description = "Say hello")
    public void hello(SlashCommandInteractionEvent event) {
        event.reply("Hello, " + event.getUser().getName() + "! 👋").queue();
    }

    // Lifecycle hooks (optional)
    @OnEnable
    public void onEnable(PluginContext ctx) {
        ctx.log("info", "Plugin enabled!");
    }

    @OnDisable
    public void onDisable(PluginContext ctx) {
        ctx.log("info", "Plugin disabled!");
    }

    @OnShutdown
    public boolean shutdown(PluginContext ctx) {
        // Clean up resources
        ctx.log("info", "Plugin shutting down...");
        return true; // Return false to force-kill
    }
}
```

### 3. Build and Deploy

```bash
mvn clean package
cp target/my-plugin-1.0.0.jar /path/to/pudel/plugins/
```

That's it! The plugin is automatically discovered and loaded.

---

## Annotations Reference

### @Plugin (Required)

Marks a class as a plugin. This is the only required annotation.

```java
@Plugin(
        name = "MyPlugin",           // Plugin name (required)
        version = "1.0.0",           // Version (default: "1.0.0")
        author = "Developer",        // Author name
        description = "Description"  // Plugin description
)
public class MyPlugin {
    // ...
}
```

### @SlashCommand

Marks a method as a slash command handler.

```java
@SlashCommand(
        name = "greet",                              // Command name (required)
        description = "Greet someone",               // Description (required)
        options = {...},                             // Command options (default {})
        subcommands = {...},                         // Subcommands (default {})
        nsfw = false,                                // Set command as NSFW command (default true = NSFW command)
        global = false,                              // Set Command to be Global scope? (default true = Command can use anywhere even bot does not exist)
        /* If Global is true these attribute below will be ignored / unused */
        guildIds = {},                               // Specific guilds (default {} = every guild)
        permissions = {Permission.ADMINISTRATOR},    // Required permissions (default {} = everyone)
        integrationTo = {...},                       // Integration to User or Guild Install (default IntegrationType.GUILD_INSTALL)
        integrationContext = {...}                   // Integration Context Scope to (default InteractionContextType.GUILD)
)
public void greet(SlashCommandInteractionEvent event) {
    // Handle command
}
```

#### With Options

```java
@SlashCommand(
    name = "greet",
    description = "Greet someone",
    options = {
        @CommandOption(
            name = "user",
            description = "User to greet",
            type = OptionType.USER,
            required = true
        ),
        @CommandOption(
            name = "message",
            description = "Custom message",
            type = OptionType.STRING
        )
    }
)
public void greet(SlashCommandInteractionEvent event) {
    User user = event.getOption("user").getAsUser();
    String message = event.getOption("message") != null 
        ? event.getOption("message").getAsString() 
        : "Hello!";
    event.reply(message + " " + user.getAsMention()).queue();
}
```

#### With Subcommands

```java
@SlashCommand(
    name = "config",
    description = "Plugin configuration",
    permissions = {Permission.ADMINISTRATOR},
    subcommands = {
        @Subcommand(name = "view", description = "View settings"),
        @Subcommand(
            name = "set", 
            description = "Change setting",
            options = {
                @CommandOption(name = "key", description = "Setting name", required = true),
                @CommandOption(name = "value", description = "New value", required = true)
            }
        ),
        @Subcommand(name = "reset", description = "Reset to defaults")
    }
)
public void config(SlashCommandInteractionEvent event) {
    switch (event.getSubcommandName()) {
        case "view" -> event.reply("Settings: ...").setEphemeral(true).queue();
        case "set" -> {
            String key = event.getOption("key").getAsString();
            String value = event.getOption("value").getAsString();
            event.reply("✅ Set " + key + " = " + value).queue();
        }
        case "reset" -> event.reply("✅ Settings reset").queue();
    }
}
```

#### With Choices

```java
@SlashCommand(
    name = "language",
    description = "Set language",
    options = {
        @CommandOption(
            name = "lang",
            description = "Language",
            required = true,
            choices = {
                @Choice(name = "English", value = "en"),
                @Choice(name = "Thai (ภาษาไทย)", value = "th"),
                @Choice(name = "Japanese (日本語)", value = "ja")
            }
        )
    }
)
public void language(SlashCommandInteractionEvent event) {
    String lang = event.getOption("lang").getAsString();
    event.reply("Language set to: " + lang).queue();
}
```

### @TextCommand

Marks a method as a text command handler (e.g., `!hello`).

```java
@TextCommand(
    value = "hello",              // Command name (required)
    aliases = {"hi", "hey"},      // Command aliases
    description = "Say hello",    // Description for help
    usage = "hello [name]"        // Usage example
)
public void hello(CommandContext ctx) {
    String name = ctx.getArg(0).orElse("friend");
    ctx.reply("Hello, " + name + "!");
}
```

### @ButtonHandler

Handles button click interactions.

> ⚠️ **Component ID Namespacing:** The core **automatically prepends** the plugin's unique database prefix (e.g. `p_48f2391a_`) to the annotation value. When creating Discord components, you **must** also prepend the same prefix so that the IDs match. See [Component ID Namespacing](#component-id-namespacing) for details.

```java
@ButtonHandler("confirm:")  // Registered as "p_48f2391a_confirm:" (prefix auto-added)
public void handleConfirm(ButtonInteractionEvent event) {
    event.reply("Confirmed!").setEphemeral(true).queue();
}

@ButtonHandler("cancel:")  // Registered as "p_48f2391a_cancel:"
public void handleCancel(ButtonInteractionEvent event) {
    event.reply("Cancelled").setEphemeral(true).queue();
}
```

### @ModalHandler

Handles modal submission.

> ⚠️ **Component ID Namespacing:** Same as `@ButtonHandler` — the database prefix is auto-prepended. Use the prefix when creating modals too.

```java
@ModalHandler("feedback:")  // Registered as "p_48f2391a_feedback:"
public void handleFeedback(ModalInteractionEvent event) {
    String feedback = event.getValue("feedback-input").getAsString();
    event.reply("Thanks for your feedback!").setEphemeral(true).queue();
}
```

### @SelectMenuHandler

Handles select menu interactions.

> ⚠️ **Component ID Namespacing:** Same as `@ButtonHandler` — the database prefix is auto-prepended.

```java
@SelectMenuHandler("role-select:")  // Registered as "p_48f2391a_role-select:"
public void handleRoleSelect(StringSelectInteractionEvent event) {
    List<String> selected = event.getValues();
    event.reply("You selected: " + String.join(", ", selected)).queue();
}
```

### Component ID Namespacing

When you annotate a handler with `@ButtonHandler("confirm:")`, the core **automatically prepends** the plugin's unique database prefix (e.g. `p_48f2391a_`) so the actual registered ID prefix becomes `p_48f2391a_confirm:`.

This means you **must also prepend the same prefix** when creating Discord components, otherwise the IDs won't match and your handler will never fire.

Use `context.getDatabaseManager().getPrefix()` to obtain your prefix:

```java
@Plugin(name = "MyPlugin", version = "1.0.0", author = "Author")
public class MyPlugin {

    private PluginContext context;

    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
    }

    @SlashCommand(name = "panel", description = "Show panel")
    public void panel(SlashCommandInteractionEvent event) {
        // Get the plugin's unique prefix — e.g. "p_48f2391a_"
        String prefix = context.getDatabaseManager().getPrefix();

        event.reply(
                new MessageCreateBuilder()
                        .useComponentsV2(true)
                        .setComponents(Container.of(
                                TextDisplay.of("### My Panel"),
                                ActionRow.of(
                                        // ✅ CORRECT — prefix + annotation value must match
                                        Button.primary(prefix + "confirm:" + someId, "✅ Confirm"),
                                        Button.danger(prefix + "cancel:" + someId, "❌ Cancel")
                                )
                        ))
                        .build()
        ).setEphemeral(true).queue();
    }

    @ButtonHandler("confirm:")  // Core registers as "p_48f2391a_confirm:"
    public void handleConfirm(ButtonInteractionEvent event) {
        event.reply("Confirmed!").setEphemeral(true).queue();
    }

    @ButtonHandler("cancel:")   // Core registers as "p_48f2391a_cancel:"
    public void handleCancel(ButtonInteractionEvent event) {
        event.reply("Cancelled").setEphemeral(true).queue();
    }
}
```

> **Why?** The database prefix (`p_48f2391a_`) is a short, unique, deterministic identifier assigned to each plugin. Using it instead of the full plugin name avoids collisions between plugins that might share similar handler names, and keeps Discord component IDs short.

| ❌ Wrong | ✅ Correct |
|----------|-----------|
| `Button.primary("confirm:" + id, "OK")` | `Button.primary(prefix + "confirm:" + id, "OK")` |
| Hardcoded `"p_48f2391a_confirm:"` | `context.getDatabaseManager().getPrefix() + "confirm:"` |

The prefix is **stable** — the same plugin always gets the same prefix across restarts and reloads.

### @OnEnable

Called when the plugin is enabled. Runs **after** all handlers and commands have been registered.

> ℹ️ **Transaction Isolation:** `@OnEnable` runs in its own isolated database transaction. If your `@OnEnable` throws an exception (e.g., a failed `CREATE TABLE`), only the `@OnEnable` work is rolled back — the plugin's handlers and commands remain registered and functional.

```java
@OnEnable
public void onEnable(PluginContext ctx) {
    ctx.log("info", "Plugin enabled!");
    // Initialize resources, create tables, etc.
}
```

### @OnDisable

Called when the plugin is disabled.

```java
@OnDisable
public void onDisable(PluginContext ctx) {
    ctx.log("info", "Plugin disabled!");
    // Save state, pause services
}
```

### @OnShutdown

Called when the plugin is being unloaded. **Returns boolean** to indicate success.

```java
@OnShutdown
public boolean shutdown(PluginContext ctx) {
    try {
        // Close database connections
        database.close();
        // Stop executor services
        executor.shutdownNow();
        // Clear caches
        cache.clear();
        
        ctx.log("info", "Shutdown complete");
        return true;  // Success - proceed with unload
    } catch (Exception e) {
        ctx.log("error", "Shutdown failed: " + e.getMessage());
        return false; // Failed - core will force-kill
    }
}
```

---

## Plugin Lifecycle

```
┌─────────────────────────────────────────────────────────────────────┐
│  JAR loaded → Plugin discovered → handlers/commands registered      │
│                                   → slash commands synced to Discord│
│                                   → metadata updated                │
│                                   → @OnEnable called (isolated tx)  │
│                                   → [ENABLED]                       │
│                                                          ↓          │
│  Disable request → @OnDisable called → handlers unregistered        │
│                                        → commands synced (removed)  │
│                                                          ↓          │
│  Unload request → @OnShutdown called (returns boolean)              │
│                   → true: graceful unload                           │
│                   → false: force-kill plugin                        │
└─────────────────────────────────────────────────────────────────────┘
```

> **Note:** `@OnEnable` runs in an isolated transaction *after* handlers are registered.
> If `@OnEnable` fails, the plugin still has its slash commands and interaction handlers
> active — only the `@OnEnable` work (e.g., table creation) is rolled back.

---

## Hot-Reload

Plugins support automatic hot-reload when their JAR file is updated:

1. **Disabled plugins**: Updates are applied immediately
2. **Enabled plugins**: Automatically disabled → reloaded → re-enabled
3. **Removed JARs**: Plugin is automatically unloaded

No manual restart required!

---

## Plugin Database

Each plugin gets isolated database storage:

```java
@OnEnable
public void onEnable(PluginContext ctx) {
    PluginDatabaseManager db = ctx.getDatabaseManager();
    
    // Define schema
    TableSchema schema = TableSchema.builder("user_data")
        .column("user_id", ColumnType.BIGINT, false)
        .column("points", ColumnType.INTEGER, false, "0")
        .index("user_id")
        .build();
    
    // Create table
    db.createTable(schema);
    
    // Get repository
    PluginRepository<UserData> repo = db.getRepository("user_data", UserData.class);
    
    // CRUD operations
    repo.save(new UserData(12345L, 100));
    List<UserData> topUsers = repo.query()
        .whereGreaterThan("points", 50)
        .orderByDesc("points")
        .limit(10)
        .list();
}
```

### Key-Value Store

```java
PluginKeyValueStore kv = db.getKeyValueStore();
kv.set("feature_enabled", true);
kv.set("max_daily_uses", 100);

boolean enabled = kv.getBoolean("feature_enabled", false);
```

---

## Agent Tools

Extend Pudel's AI capabilities with custom tools:

```java
public class WeatherTools implements AgentToolProvider {
    
    @AgentTool(
        name = "get_weather",
        description = "Get current weather for a city",
        keywords = {"weather", "temperature"}
    )
    public String getWeather(AgentToolContext ctx, String city) {
        return "Weather in " + city + ": 22°C, Sunny";
    }
}

// Register in @OnEnable
@OnEnable
public void onEnable(PluginContext ctx) {
    ctx.getAgentToolRegistry().registerProvider("my-plugin", new WeatherTools());
}
```

---

## MCP Tools (Model Context Protocol)

In addition to Agent Tools, Pudel v2.3.1 supports **MCP Tools** — a separate tool registry that follows the Model Context Protocol standard. MCP tools use JSON Schema for parameter definitions and are registered through the `McpToolRegistry` instead of the `AgentToolRegistry`.

### Key Differences: Agent Tools vs MCP Tools

| Aspect | Agent Tools | MCP Tools |
|--------|-------------|-----------|
| **Registry** | `AgentToolRegistry` | `McpToolRegistry` |
| **Registration** | `registerProvider()` with `@AgentTool` annotations | `registerTool()` with `McpToolDefinition` |
| **Parameter Definition** | Java method signatures (compile-time) | JSON Schema (runtime) |
| **Execution** | Method invocation via reflection | `McpToolExecutor` functional interface |
| **Use Case** | Data management (tables, memories, search) | Brain context access (passive context, dialogue history) |
| **Built-in Examples** | `BuiltinAgentTools` (14+ tools) | `BuiltinMcpTools` (5 tools) |

### Creating MCP Tools as a Plugin

Third-party plugins can register MCP tools to extend Pudel's brain context access:

```java
import group.worldstandard.pudel.api.agent.*;
import group.worldstandard.pudel.api.PluginContext;

@Plugin(name = "MyMcpPlugin", version = "1.0.0", author = "Developer")
public class MyMcpPlugin {

    @OnEnable
    public void onEnable(PluginContext ctx) {
        McpToolRegistry mcpRegistry = ctx.getMcpToolRegistry();

        // Register an MCP tool with JSON Schema parameters
        String inputSchema = """
            {
                "type": "object",
                "properties": {
                    "channel_id": {
                        "type": "number",
                        "description": "The channel ID to get info for"
                    },
                    "limit": {
                        "type": "integer",
                        "description": "Maximum entries to return",
                        "default": 10
                    }
                },
                "required": ["channel_id"]
            }
            """;

        mcpRegistry.registerTool("my-plugin",
            McpToolDefinition.builder()
                .name("get_channel_stats")
                .description("Get statistics for a Discord channel including message count and active users.")
                .inputSchema(inputSchema)
                .pluginId("my-plugin")
                .keywords(List.of("stats", "channel", "analytics", "info"))
                .guildOnly(true)
                .dmOnly(false)
                .permission(AgentTool.ToolPermission.EVERYONE)
                .priority(50)
                .executor(this::executeGetChannelStats)
                .build()
        );
    }

    private String executeGetChannelStats(AgentToolContext context, Map<String, Object> parameters) {
        long channelId = extractLong(parameters, "channel_id");
        int limit = extractInt(parameters, "limit", 10);

        // Your implementation here
        return "Channel " + channelId + ": " + limit + " entries analyzed.";
    }

    private long extractLong(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(value.toString());
    }

    private int extractInt(Map<String, Object> params, String key, int defaultVal) {
        Object value = params.get(key);
        if (value == null) return defaultVal;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}
```

### MCP Tool Definition Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `name` | String | ✅ | Unique tool name (used by LLM to call it) |
| `description` | String | ✅ | What the tool does (shown to LLM) |
| `inputSchema` | String | ✅ | JSON Schema string defining parameters |
| `pluginId` | String | ✅ | Your plugin's ID |
| `keywords` | List<String> | No | Search keywords for tool discovery |
| `guildOnly` | boolean | No | Only available in guilds (default: false) |
| `dmOnly` | boolean | No | Only available in DMs (default: false) |
| `permission` | ToolPermission | No | Required permission level |
| `priority` | int | No | Registration priority (higher = first) |
| `executor` | McpToolExecutor | ✅ | Function that executes the tool |

### How MCP Tools Are Called

When the LLM needs to use an MCP tool, it generates a response like:

```
<TOOL_CALL>{"name": "get_channel_stats", "arguments": {"channel_id": 123456789, "limit": 5}}</TOOL_CALL>
```

The `PudelAgentService.processWithTools()` method:
1. Parses the tool call from the LLM response
2. Looks up the tool in `McpToolRegistry` first, then `AgentToolRegistry`
3. Executes the tool's `McpToolExecutor` with the provided parameters
4. Appends the result to the conversation history
5. Re-generates until a final response is produced or max iterations reached

### Best Practices for MCP Tools

1. **Descriptive names**: Use clear, specific names like `get_channel_stats` not `stats`
2. **Detailed descriptions**: The LLM uses the description to decide when to call the tool
3. **Validate parameters**: Always validate and sanitize input parameters
4. **Error handling**: Return meaningful error messages, don't throw exceptions
5. **Keep it focused**: Each tool should do one thing well
6. **Use JSON Schema**: Define types, required fields, defaults, and constraints

### Accessing Pudel's Built-in MCP Tools

Your plugin can also **use** the built-in MCP tools through the `PudelBrain` API:

```java
@OnEnable
public void onEnable(PluginContext ctx) {
    PudelBrain brain = ctx.getBrain();

    // Get passive context for a channel
    String context = brain.getPassiveContext(channelId, true, guildId, 10);

    // Get dialogue history with a user
    String history = brain.getDialogueHistory(userId, true, guildId, 20);

    // Fetch a specific message
    PassiveContextEntry msg = brain.fetchContextByMessageId(messageId, channelId, true, guildId);
}
```

---

## Components v2

Discord's **Components v2** system (JDA 6+) replaces the old pattern of `replyEmbeds()` + `addComponents(ActionRow.of(...))` with a unified component tree. Instead of embeds with separate action rows, you build entire rich UIs using `Container`, `TextDisplay`, `Section`, `Separator`, `MediaGallery`, and `Thumbnail`.

### Available Components

| Component | Description | Factory Method |
|-----------|-------------|----------------|
| `Container` | Top-level wrapper with accent color | `Container.of(children...)` |
| `TextDisplay` | Renders markdown text | `TextDisplay.of("**bold** text")` |
| `Section` | Pairs content with an accessory | `Section.of(accessory, content...)` |
| `Separator` | Visual divider line | `Separator.create(isDivider, spacing)` |
| `Thumbnail` | Small image (Section accessory) | `Thumbnail.fromUrl(url)` |
| `MediaGallery` | Image display | `MediaGallery.of(items...)` |
| `MediaGalleryItem` | Single image in a gallery | `MediaGalleryItem.fromUrl(url)` |
| `ActionRow` | Row of buttons/selects (unchanged) | `ActionRow.of(components...)` |
| `CheckboxGroup` | Group of checkboxes (Modal only) | `CheckboxGroup.create("id")` |

### Basic Example — Sending Components v2

```java
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

@SlashCommand(name = "panel", description = "Show a v2 control panel")
public void panel(SlashCommandInteractionEvent event) {
    Container panel = Container.of(
            TextDisplay.of("# 🎛️ Control Panel"),
            Separator.create(true, Separator.Spacing.SMALL),
            TextDisplay.of("Use the buttons below to manage settings."),
            ActionRow.of(
                    Button.primary("panel:settings", "⚙️ Settings"),
                    Button.danger("panel:reset", "🗑️ Reset")
            )
    ).withAccentColor(Color.BLUE);

    event.reply(
            new MessageCreateBuilder()
                    .useComponentsV2(true)
                    .setComponents(panel)
                    .build()
    ).setEphemeral(true).queue();
}
```

> ⚠️ In a real plugin, the button IDs above (`"panel:settings"`, `"panel:reset"`) must be prefixed with `context.getDatabaseManager().getPrefix()` so they match your `@ButtonHandler` registrations. See [Component ID Namespacing](#component-id-namespacing).

### Sending & Editing Messages

Components v2 messages **must** opt in via `.useComponentsV2(true)` on the builder, then use `.setComponents()`:

```java
// ✅ Sending — reply() accepts MessageCreateData (call .build())
event.reply(
        new MessageCreateBuilder()
                .useComponentsV2(true)
                .setComponents(container1, container2)
                .build()
).setEphemeral(true).queue();

// ✅ Editing from interaction — editMessage() accepts MessageEditData
event.editMessage(
        new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(container1, container2)
                .build()
).queue();

// ✅ Editing a stored Message reference
message.editMessage(
        new MessageEditBuilder()
                .useComponentsV2(true)
                .setComponents(container1, container2)
                .build()
).queue();
```

> ⚠️ **Gotcha: `useComponentsV2(true)` is REQUIRED.** Without it, JDA defaults to Components v1 mode and throws:
> `IllegalStateException: Cannot build message with components other than ActionRow while using components V1`
>
> You **must** call `.useComponentsV2(true)` on every `MessageCreateBuilder` / `MessageEditBuilder` that contains v2 components (`Container`, `TextDisplay`, `Section`, `Separator`, etc.).

```java
// ❌ WRONG — missing useComponentsV2, throws IllegalStateException at runtime
event.reply(new MessageCreateBuilder().setComponents(container).build());

// ❌ WRONG — missing .build(), won't compile
event.reply(new MessageCreateBuilder().useComponentsV2(true).setComponents(container));

// ✅ CORRECT
event.reply(new MessageCreateBuilder().useComponentsV2(true).setComponents(container).build());
```

### Container

`Container` is the top-level v2 component. It holds child components and supports an accent color (colored left-border, like embeds).

```java
Container container = Container.of(
        TextDisplay.of("# Title"),
        Separator.create(true, Separator.Spacing.SMALL),
        TextDisplay.of("Description content here"),
        ActionRow.of(Button.primary("id", "Click Me"))
).withAccentColor(Color.RED);
```

A `Container` can hold: `TextDisplay`, `Section`, `Separator`, `MediaGallery`, `ActionRow`, and `FileDisplay`.

### Separator

Creates a visual divider. Use `Separator.create()`, **not** `Separator.of()`.

```java
// ✅ Visible divider line
Separator.create(true, Separator.Spacing.SMALL)

// ✅ Invisible spacing only
Separator.create(false, Separator.Spacing.LARGE)

// ✅ Shorthand alternatives
Separator.createDivider(Separator.Spacing.SMALL)    // isDivider = true
Separator.createInvisible(Separator.Spacing.SMALL)   // isDivider = false
```

> ⚠️ **Gotcha:** There is no `Separator.of()` method. Use `Separator.create(boolean, Spacing)`.

### Section (with Thumbnail)

`Section` pairs content components (`TextDisplay`) with an accessory (`Thumbnail` or `Button`).

```java
// ✅ Correct — accessory FIRST, then content
Section.of(
        Thumbnail.fromUrl("https://example.com/image.png"),
        TextDisplay.of("### Title"),
        TextDisplay.of("Some description")
)
```

> ⚠️ **Gotcha:** The **accessory comes first** in `Section.of()`, then the content components.
> The signature is: `Section.of(SectionAccessoryComponent, SectionContentComponent, SectionContentComponent...)`

```java
// ❌ WRONG — TextDisplay is not a SectionAccessoryComponent
Section.of(TextDisplay.of("Title"), Thumbnail.fromUrl(url))

// ✅ CORRECT — Thumbnail (accessory) first, then TextDisplay (content)
Section.of(Thumbnail.fromUrl(url), TextDisplay.of("Title"))
```

### MediaGallery

Displays images in a gallery format:

```java
// Single image
MediaGallery.of(MediaGalleryItem.fromUrl("https://example.com/photo.png"))

// Multiple images
MediaGallery.of(
        MediaGalleryItem.fromUrl("https://example.com/img1.png"),
        MediaGalleryItem.fromUrl("https://example.com/img2.png")
)
```

### TextDisplay Markdown

`TextDisplay` renders Discord markdown. Use heading syntax for structure:

```java
TextDisplay.of("# Heading 1")          // Large header
TextDisplay.of("### Heading 3")        // Medium header
TextDisplay.of("**Bold** _Italic_")    // Inline formatting
TextDisplay.of("-# Small text")        // Subtext / small text
TextDisplay.of("[Link](https://...)")  // Hyperlink
TextDisplay.of("\u200B")               // Zero-width space (empty placeholder)
```

### Multiple Containers

You can send multiple top-level containers in one message. This is useful for separating a control panel from a preview:

```java
Container controls = Container.of(
        TextDisplay.of("# Controls"),
        ActionRow.of(Button.primary("edit", "✏️ Edit"))
);

Container preview = Container.of(
        TextDisplay.of("### Preview"),
        TextDisplay.of("Content goes here...")
).withAccentColor(Color.GREEN);

event.reply(
        new MessageCreateBuilder()
                .useComponentsV2(true)
                .setComponents(controls, preview)
                .build()
).setEphemeral(true).queue();
```

### Select Menus Inside Containers

Select menus can be placed inside a `Container` via `ActionRow` for a richer presentation:

```java
StringSelectMenu menu = StringSelectMenu.create("color:select")
        .addOption("🔴 Red", "red")
        .addOption("🔵 Blue", "blue")
        .build();

event.reply(
        new MessageCreateBuilder()
                .useComponentsV2(true)
                .setComponents(Container.of(
                        TextDisplay.of("### 🎨 Choose a Color"),
                        ActionRow.of(menu)
                ))
                .build()
).setEphemeral(true).queue();
```

### CheckboxGroup (Modal Only)

`CheckboxGroup` provides a set of checkboxes that can be placed **inside a Modal** via `Label.of()`. It's useful for boolean toggles or acknowledgement confirmations. Because JDA requires checkboxes to be in a `CheckboxGroup` object for setMinValues().

```java
// ✅ Single checkbox — optional (e.g. opt-in toggle)
CheckboxGroup controlGroup = CheckboxGroup.create("control")
        .addOption("Yes, control this category via Pudel", "control_pudel")
        .build();

// ✅ Single checkbox — required acknowledgement
CheckboxGroup ackGroup = CheckboxGroup.create("acknowledged")
        .addOption("I understand this action will sync permissions", "ack")
        .setMinValues(1)  // This group have to check at least 1 (for 1 option mean force)
        .build();
```

Place the `CheckboxGroup` inside a `Modal` using `Label.of()`, just like `EntitySelectMenu`:

```java
event.replyModal(Modal.create("my_modal", "My Modal Title")
        .addComponents(
                Label.of("Category Name *", nameInput),
                Label.of("Control via Pudel", controlGroup)
        ).build()
).queue();
```

**Reading checkbox values** from a `ModalInteractionEvent`:

```java
@ModalHandler("my_modal")
public void handleModal(ModalInteractionEvent event) {
    // Use getAsStringList() — checkboxes return selected option values as a list
    var value = event.getValue("control");
    List<String> selected = (value != null) ? value.getAsStringList() : List.of();
    boolean isChecked = selected.contains("control_pudel");
}
```

> ⚠️ **Gotcha:** `CheckboxGroup` can only be used inside a **Modal** (via `Label.of()`), not directly in a message `Container` or `ActionRow`.
> Use `getAsStringList()` to read selected values — `getAsString()` will not work correctly for checkbox/select components.

### Complete Example — see [`PudelCategorizing.java`](https://github.com/World-Standard-Group/Basic-Pudel/blob/main/pudel-categorizement/src/main/java/group/worldstandard/pudel/plugin/PudelCategorizing.java)

The `pudel-categorizement` module demonstrates `CheckboxGroup` usage with:
- Optional checkbox for opting in to Pudel tracking on category creation
- Required acknowledgement checkbox on category import (validated in handler code)
- `EntitySelectMenu` for User, Role, and Channel selection in the same Modal
- Reading checkbox state via `getAsStringList()` and `.contains()` check

### Common Pitfalls

| Pitfall | Solution |
|---------|----------|
| Missing `useComponentsV2(true)` → runtime `IllegalStateException` | **Always** call `.useComponentsV2(true)` on every builder using v2 components |
| Button/modal/select-menu handler never fires | The core auto-prepends the database prefix to handler IDs. Use `context.getDatabaseManager().getPrefix() + "yourPrefix:"` when creating component IDs — see [Component ID Namespacing](#component-id-namespacing) |
| Hardcoding the database prefix (e.g. `"p_48f2391a_"`) | Always use `context.getDatabaseManager().getPrefix()` — hardcoded prefixes break if the plugin is reinstalled |
| `Separator.of(...)` doesn't exist | Use `Separator.create(boolean, Spacing)` |
| `Section.of(TextDisplay, Thumbnail)` wrong order | Accessory first: `Section.of(Thumbnail, TextDisplay)` |
| `reply(MessageCreateBuilder)` doesn't compile | Call `.build()`: `reply(builder.build())` |
| `editMessage(MessageEditBuilder)` doesn't compile | Call `.build()`: `editMessage(builder.build())` |
| Putting `TextDisplay` as Section accessory | Only `Thumbnail` or `Button` can be an accessory |
| Embed + Components v2 in same message | Don't mix — use either embeds OR v2 containers |
| `@SelectMenuHandler` with `EntitySelectInteractionEvent` | **Not supported** — `@SelectMenuHandler` only dispatches to `StringSelectInteractionEvent`. Place `EntitySelectMenu` inside a `Label` in a **Modal** instead, and handle via `@ModalHandler` |
| `ModalMapping.getAsString()` on a select menu inside a modal | Select menus return `STRING_SELECT` / `CHANNEL_SELECT` types — use `getAsStringList()` instead of `getAsString()` |
| `EntitySelectMenu` in a message for channel selection | Place it inside a Modal via `Label.of("label", channelMenu)` — the response comes as a `ModalInteractionEvent` with `getAsStringList()` returning entity IDs |
| `CheckboxGroup` in a message `Container` or `ActionRow` | `CheckboxGroup` can **only** be used inside a **Modal** via `Label.of("label", checkboxGroup)` |
| `getAsString()` on a `CheckboxGroup` value | Use `getAsStringList()` and check `.contains("optionValue")` — checkboxes return a list of selected option values |

### Complete Example — see [`PudelMessagePlugin.java`](https://github.com/World-Standard-Group/Basic-Pudel/blob/main/pudel-message/src/main/java/group/worldstandard/pudel/plugin/PudelMessagePlugin.java)

The `pudel-message` module demonstrates a full Components v2 embed builder with:
- Two containers (builder controls + live preview) in one message
- `Container.withAccentColor()` to mimic embed color
- `Section` + `Thumbnail` for author icons and thumbnails
- `MediaGallery` for images
- `Separator` for visual dividers
- `TextDisplay` with markdown for all text content
- `MessageCreateBuilder` / `MessageEditBuilder` for sending/editing
- Classic `MessageEmbed` still used for the final posted embed

---

## Best Practices

### 1. Always Use Annotations

```java
// ✅ Correct - Core handles everything
@Plugin(name = "MyPlugin", version = "1.0.0")
public class MyPlugin {
    @SlashCommand(name = "ping", description = "Pong!")
    public void ping(SlashCommandInteractionEvent event) {
        event.reply("Pong!").queue();
    }
}
```

### 2. Use Guild Commands During Development

```java
@SlashCommand(
    name = "test",
    description = "Test command",
    global = false  // ← Instant registration
)
```

> Global commands take up to 1 hour to propagate. Guild commands are instant.

### 3. Return Boolean from @OnShutdown

```java
@OnShutdown
public boolean shutdown(PluginContext ctx) {
    try {
        // Cleanup
        return true;  // Success
    } catch (Exception e) {
        return false; // Force-kill needed
    }
}
```

### 4. Handle Errors Gracefully

```java
@SlashCommand(name = "risky", description = "Risky operation")
public void risky(SlashCommandInteractionEvent event) {
    try {
        // Risky operation
        event.reply("Success!").queue();
    } catch (Exception e) {
        event.reply("❌ An error occurred").setEphemeral(true).queue();
    }
}
```

---

## Migration from Legacy API

If you have plugins using the deprecated `SimplePlugin` class or `PudelPlugin` interface, follow this guide to migrate to the new annotation-based API.

### Key Changes

| Old API | New API |
|---------|---------|
| `extends SimplePlugin` | `@Plugin` annotation on class |
| `implements PudelPlugin` | `@Plugin` annotation on class |
| `setup()` method | `@OnEnable` method |
| `command("name", handler)` | `@TextCommand("name")` |
| `manager.registerSlashCommand(...)` | `@SlashCommand` annotation |
| `manager.syncCommands()` | **Automatic** - no longer needed! |
| `listener(this)` | `@EventHandler` methods |
| `implements Listener` | Just add `@EventHandler` methods |
| Manual unregister in onDisable | **Automatic** - core handles it |

### Before (SimplePlugin - Deprecated)

```java
public class MyMusicPlugin extends SimplePlugin {
    
    public MyMusicPlugin() {
        super("MyMusic", "1.0.0", "Author", "Music plugin");
    }
    
    @Override
    protected void setup() {
        // Manual command registration
        command("nowplaying", this::handleNowPlaying);
        command("np", this::handleNowPlaying);
        
        // Manual slash command registration
        InteractionManager manager = getContext().getInteractionManager();
        manager.registerSlashCommand(PLUGIN_ID, new SlashCommandHandler() {
            @Override
            public SlashCommandData getCommandData() {
                return Commands.slash("play", "Play music")
                    .addOption(OptionType.STRING, "query", "Song to play", true);
            }
            
            @Override
            public void handle(SlashCommandInteractionEvent event) {
                handlePlay(event);
            }
        });
        
        // Don't forget to sync! (easy to miss)
        manager.syncCommands();
        
        // Manual listener registration
        listener(this);
        
        log("info", "Plugin enabled");
    }
    
    private void handleNowPlaying(CommandContext ctx) {
        ctx.reply("Now playing...");
    }
    
    private void handlePlay(SlashCommandInteractionEvent event) {
        event.reply("Playing...").queue();
    }
}
```

### After (Annotation-based - Recommended)

```java
@Plugin(
    name = "MyMusic",
    version = "2.0.0",
    author = "Author",
    description = "Music plugin"
)
public class MyMusicPlugin {

    private PluginContext context;
    
    // Lifecycle hook - replaces setup()
    @OnEnable
    public void onEnable(PluginContext ctx) {
        this.context = ctx;
        ctx.log("info", "Plugin enabled");
    }
    
    // Shutdown hook - return true for clean shutdown
    @OnShutdown
    public boolean onShutdown() {
        // Cleanup resources
        return true;
    }
    
    // Text command with aliases - replaces command("np", ...)
    @TextCommand(value = "nowplaying", aliases = {"np"})
    public void nowPlaying(CommandContext ctx) {
        ctx.reply("Now playing...");
    }
    
    // Slash command - replaces manager.registerSlashCommand(...)
    @SlashCommand(
        name = "play",
        description = "Play music",
        options = {
            @CommandOption(
                name = "query",
                description = "Song to play",
                type = OptionType.STRING,
                required = true
            )
        }
    )
    public void play(SlashCommandInteractionEvent event) {
        event.reply("Playing...").queue();
    }
    
    // Button handler - replaces implements Listener + onButtonInteraction
    @ButtonHandler("music:")
    public void handleMusicButtons(ButtonInteractionEvent event) {
        // Handle button clicks
    }
    
    // Event handler - replaces listener(this) + @EventHandler
    @EventHandler
    public void onReaction(MessageReactionAddEvent event) {
        // Handle reactions
    }
}
```

### Migration Checklist

- [ ] Replace `extends SimplePlugin` with `@Plugin` annotation
- [ ] Move constructor parameters to `@Plugin` annotation attributes
- [ ] Rename `setup()` to a method with `@OnEnable`
- [ ] Replace `command("name", handler)` with `@TextCommand("name")`
- [ ] Replace manual `registerSlashCommand()` with `@SlashCommand`
- [ ] **Remove all `syncCommands()` calls** - now automatic!
- [ ] Replace `listener(this)` with `@EventHandler` on methods
- [ ] Add `@OnShutdown` method that returns `boolean`
- [ ] Replace `getContext()` calls - context is passed to `@OnEnable`
- [ ] Update `log()` calls to use `context.log()`
- [ ] Update component IDs — use `context.getDatabaseManager().getPrefix()` + handler prefix when creating buttons/modals/select menus (see [Component ID Namespacing](#component-id-namespacing))

---

## Licensing

| License | Requirements |
|---------|--------------|
| **Open Source** | Publish source on marketplace |
| **Proprietary** | Keep source private |
| **Commercial** | Sell plugins, no source required |

The **Plugin Exception** allows proprietary plugins even though pudel-core is AGPL.

---

## Deployment

1. Build JAR: `mvn clean package`
2. Copy to `plugins/` directory
3. Plugin is automatically discovered and loaded

For Docker deployments, mount the plugins directory as a volume.

---

## Examples

See [Basic Pudel's project](https://github.com/World-Standard-Group/Basic-Pudel) for example & reference

---

*Happy plugin development!* 🔌
