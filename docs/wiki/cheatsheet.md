# Cheat Sheet

Quick reference for Pudel v2.3.1

---

## Plugin Development

### Minimal Plugin

```java
@Plugin(name = "MyPlugin", version = "1.0.0")
public class MyPlugin {

    @SlashCommand(name = "ping", description = "Pong!")
    public void ping(SlashCommandInteractionEvent event) {
        event.reply("Pong!").queue();
    }
}
```

### All Annotations

| Annotation           | Purpose                            |
|----------------------|------------------------------------|
| `@Plugin`            | Marks class as plugin (required)   |
| `@SlashCommand`      | Slash command handler              |
| `@ContextMenu`       | Context Menu command handler       |
| `@TextCommand`       | Text command handler               |
| `@ButtonHandler`     | Button click handler               |
| `@ModalHandler`      | Modal submission handler           |
| `@SelectMenuHandler` | Select menu handler                |
| `@OnEnable`          | Called when enabled                |
| `@OnDisable`         | Called when disabled               |
| `@OnShutdown`        | Called on unload (returns boolean) |

### Slash Command with Options

```java
@SlashCommand(
    name = "greet",
    description = "Greet someone",
    options = {
        @CommandOption(name = "user", type = "USER", required = true),
        @CommandOption(name = "message", type = "STRING")
    }
)
public void greet(SlashCommandInteractionEvent event) {
    User user = event.getOption("user").getAsUser();
    event.reply("Hello, " + user.getAsMention() + "!").queue();
}
```

### Slash Command with Subcommands

```java
@SlashCommand(
    name = "config",
    description = "Configuration",
    subcommands = {
        @Subcommand(name = "view", description = "View config"),
        @Subcommand(name = "set", description = "Set value",
            options = @CommandOption(name = "key", required = true))
    }
)
public void config(SlashCommandInteractionEvent event) {
    switch (event.getSubcommandName()) {
        case "view" -> event.reply("...").queue();
        case "set" -> event.reply("...").queue();
    }
}
```

### Lifecycle Hooks

```java
@OnEnable
public void onEnable(PluginContext ctx) {
    ctx.log("info", "Enabled!");
}

@OnDisable
public void onDisable(PluginContext ctx) {
    ctx.log("info", "Disabled!");
}

@OnShutdown
public boolean shutdown(PluginContext ctx) {
    // Cleanup resources
    return true; // false = force-kill
}
```

---

## Built-in Commands

### Slash Commands
```
/settings     # Opens Components V2 interactive panel
              # Views: General, AI, Channels, Commands, Plugins
              # AI view: toggle, nickname, language, personality,
              #          biography, response length, formality, emote usage
```

### Text Commands
```
!ping         # Bot latency (rich embed)
!help         # Paginated command listing (⏮ ◀ ▶ ⏭ buttons)
!help 2       # Jump to page 2
!help ping    # Detailed help for a command
!h / !?       # Aliases for !help
```

### AI Agent (Natural Language)
```
"Create a table called notes"
"Save this: important info"
"Find notes about meeting"
"Remember the password is xyz"
"What do you remember?"
```

### MCP Tools (Brain Context)
```
get_passive_context     — Get recent observed messages in a channel
get_dialogue_history    — Get conversation history with a user
get_message_by_id       — Fetch a specific message from context
get_forwarded_messages  — Get forwarded message data
get_brain_status        — Check Ollama + context queue status
```

### Plugin MCP Tools
Plugins can register MCP tools via `McpToolRegistry.registerTool()`:
```java
mcpRegistry.registerTool("my-plugin",
    McpToolDefinition.builder()
        .name("my_tool")
        .description("What this tool does")
        .inputSchema("{...JSON Schema...}")
        .executor(this::executeTool)
        .build());
```

---

## Docker Quick Start

```yaml
# docker-compose.yml
services:
  pudel:
    build: .
    volumes:
      - ./plugins:/app/plugins  # Hot-reload!
      - ./keys:/app/keys
    environment:
      - DISCORD_BOT_TOKEN=${DISCORD_BOT_TOKEN}
    depends_on:
      - postgres
```

```bash
# Deploy
docker-compose up -d

# Hot-reload plugin
cp my-plugin.jar ./plugins/
# Pudel auto-detects and loads!
```

---

## Environment Variables

```env
# Required
DISCORD_BOT_TOKEN=your_token
POSTGRES_HOST=localhost
POSTGRES_DB=pudel
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password

# Optional
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=qwen3:8b
JWT_PRIVATE_KEY_PATH=./keys/pv.key
JWT_PUBLIC_KEY_PATH=./keys/pb.key
```

---

## Maven Dependency

```xml
<dependency>
    <groupId>group.worldstandard</groupId>
    <artifactId>pudel-api</artifactId>
    <version>2.3.1</version>
    <scope>provided</scope>
</dependency>
```

---

## Migration from v1.x

| Before (v1.x) | After (v2.x.x)                  |
|---------------|---------------------------------|
| `implements PudelPlugin` | `@Plugin` annotation            |
| `getPluginInfo()` | `@Plugin(name, version)`        |
| `onEnable(ctx)` method | `@OnEnable` annotation          |
| `shutdown(ctx)` method | `@OnShutdown` (returns boolean) |
| `manager.registerSlashCommand(...)` | `@SlashCommand` annotation      |
| `manager.syncCommands()` | Automatic (no call needed)      |

---

*Pudel v2.3.1 — Annotation-Based Plugin System*
