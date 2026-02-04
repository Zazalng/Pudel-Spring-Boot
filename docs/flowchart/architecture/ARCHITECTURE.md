# Pudel Architecture v2.0.0

This document describes the complete architecture of Pudel Discord Bot with the new annotation-based plugin system.

---

## Table of Contents

- [System Overview](#system-overview)
- [Module Structure](#module-structure)
- [Plugin System](#plugin-system)
- [Command System](#command-system)
- [Brain Architecture](#brain-architecture)
- [Database Schema](#database-schema)
- [Configuration](#configuration)
- [Authentication Architecture](#authentication-architecture)

---

## System Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          PUDEL DISCORD BOT v2.0.0                            │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────┐      ┌─────────────────────┐      ┌──────────────┐  │
│  │    Vue Frontend     │◄────►│   Spring Boot API   │◄────►│  PostgreSQL  │  │
│  │   (Dashboard/Wiki)  │      │    (pudel-core)     │      │  + pgvector  │  │
│  └─────────────────────┘      └──────────┬──────────┘      └──────────────┘  │
│                                          │                                   │
│                               ┌──────────┴──────────┐                        │
│                               │                     │                        │
│                    ┌──────────▼─────────┐ ┌────────▼────────┐                │
│                    │   Discord (JDA 6)  │ │   Ollama LLM    │                │
│                    │   Gateway + REST   │ │  (Local Model)  │                │
│                    └────────────────────┘ └─────────────────┘                │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │                    Annotation-Based Plugin System                     │   │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────────┐  │   │
│  │  │  @Plugin    │ │  @Plugin    │ │  @Plugin    │ │ BuiltinCommands │  │   │
│  │  │  Music      │ │  Moderation │ │  Custom...  │ │ (pudel-core)    │  │   │
│  │  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────────┘  │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Module Structure

```
pudel/
├── pudel-api/          # Plugin Development Kit (MIT License)
│   ├── annotation/              # 🆕 Annotation-based API (v2.0.0)
│   │   ├── Plugin.java          # @Plugin - marks plugin class
│   │   ├── SlashCommand.java    # @SlashCommand - slash command handler
│   │   ├── TextCommand.java     # @TextCommand - text command handler
│   │   ├── ButtonHandler.java   # @ButtonHandler - button click handler
│   │   ├── ModalHandler.java    # @ModalHandler - modal submission
│   │   ├── SelectMenuHandler.java # @SelectMenuHandler - select menu
│   │   ├── OnEnable.java        # @OnEnable - lifecycle hook
│   │   ├── OnDisable.java       # @OnDisable - lifecycle hook
│   │   ├── OnShutdown.java      # @OnShutdown - returns boolean
│   │   ├── CommandOption.java   # @CommandOption - command options
│   │   ├── Subcommand.java      # @Subcommand - subcommands
│   │   └── Choice.java          # @Choice - option choices
│   │
│   ├── PluginContext.java       # Runtime context for plugins
│   ├── PluginInfo.java          # Plugin metadata
│   ├── PudelPlugin.java         # ⚠️ DEPRECATED - use @Plugin
│   ├── SimplePlugin.java        # ⚠️ DEPRECATED - use @Plugin
│   │
│   ├── command/                 # Text command API
│   ├── interaction/             # Discord interactions API
│   ├── event/                   # Event listener API
│   ├── audio/                   # Voice/Audio API (DAVE support)
│   ├── agent/                   # Agent Tools API
│   └── database/                # Plugin database API
│
├── pudel-model/        # AI/Brain Module (AGPL-3.0)
│   ├── PudelModelService.java
│   ├── ollama/
│   ├── embedding/
│   ├── analyzer/
│   └── agent/
│
├── pudel-core/         # Main Bot Application (AGPL-3.0)
│   ├── Pudel.java               # Entry point
│   ├── plugin/                  # 🆕 Plugin loader/manager (v2.0.0)
│   │   ├── PluginClassLoader.java     # JAR loading + hot-reload
│   │   ├── PluginAnnotationProcessor.java  # Annotation scanning
│   │   ├── PluginContextImpl.java
│   │   └── PluginContextFactory.java
│   ├── service/
│   │   ├── PluginService.java         # 🆕 Lifecycle management
│   │   └── PluginWatcherService.java  # File watcher for hot-reload
│   ├── interaction/
│   │   ├── InteractionManagerImpl.java
│   │   └── builtin/
│   │       ├── BuiltinCommands.java   # 🆕 @Plugin-based built-ins
│   │       └── BuiltinSlashCommandRegistrar.java
│   └── ...
│
└── database/           # SQL Scripts
    └── init.sql
```

---

## Plugin System

### Annotation-Based Architecture (v2.0.0)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ANNOTATION-BASED PLUGIN SYSTEM                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Developer writes:                     Core handles:                        │
│  ─────────────────                     ─────────────                        │
│                                                                             │
│  @Plugin(name="MyPlugin")              PluginClassLoader                    │
│  public class MyPlugin {               ├── JAR discovery                    │
│                                        ├── @Plugin detection                │
│      @SlashCommand(...)       ───────► ├── Instance creation                │
│      public void cmd(event)            │                                    │
│                                        PluginAnnotationProcessor            │
│      @ButtonHandler(...)      ───────► ├── Method scanning                  │
│      public void btn(event)            ├── Handler registration             │
│                                        ├── Auto syncCommands()              │
│      @OnEnable                ───────► │                                    │
│      public void enable(ctx)           PluginService                        │
│                                        ├── Lifecycle orchestration          │
│      @OnShutdown              ───────► ├── Enable/Disable/Unload            │
│      public boolean shutdown(ctx)      └── Force-kill on false return       │
│  }                                                                          │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Plugin Lifecycle

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           PLUGIN LIFECYCLE                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌────────────┐                                                             │
│  │  JAR File  │                                                             │
│  │ in plugins/│                                                             │
│  └─────┬──────┘                                                             │
│        │                                                                    │
│        ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ PluginClassLoader.loadPlugin()                                      │    │
│  │ ├── Read MANIFEST.MF (Plugin-Main) or scan for @Plugin              │    │
│  │ ├── Create URLClassLoader                                           │    │
│  │ ├── Instantiate plugin class                                        │    │
│  │ └── Extract PluginInfo from @Plugin annotation                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│        │                                                                    │
│        ▼                                                                    │
│  ┌─────────────┐                                                            │
│  │   LOADED    │ ◄─── Plugin discovered, instance created                   │
│  └─────┬───────┘                                                            │
│        │ enablePlugin()                                                     │
│        ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ PluginAnnotationProcessor.processAndRegister()                      │    │
│  │ ├── Scan for @SlashCommand → register with InteractionManager       │    │
│  │ ├── Scan for @TextCommand → register with CommandRegistry           │    │
│  │ ├── Scan for @ButtonHandler, @ModalHandler, @SelectMenuHandler      │    │
│  │ ├── Call @OnEnable methods                                          │    │
│  │ └── Auto syncCommands() to Discord                                  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│        │                                                                    │
│        ▼                                                                    │
│  ┌─────────────┐                                                            │
│  │   ENABLED   │ ◄─── Commands registered, ready to handle events           │
│  └─────┬───────┘                                                            │
│        │ disablePlugin()                                                    │
│        ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ PluginAnnotationProcessor.unregisterAll()                           │    │
│  │ ├── Call @OnDisable methods                                         │    │
│  │ ├── Unregister all handlers                                         │    │
│  │ └── Auto syncCommands() to remove from Discord                      │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│        │                                                                    │
│        ▼                                                                    │
│  ┌─────────────┐                                                            │
│  │  DISABLED   │ ◄─── Commands unregistered, plugin still in memory         │
│  └─────┬───────┘                                                            │
│        │ unloadPlugin()                                                     │
│        ▼                                                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │ PluginAnnotationProcessor.invokeShutdown()                          │    │
│  │ ├── Call @OnShutdown method                                         │    │
│  │ ├── Check return value (boolean)                                    │    │
│  │ │   ├── true  → Graceful unload                                     │    │
│  │ │   └── false → Force-kill (GC + cleanup)                           │    │
│  │ └── Close URLClassLoader                                            │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│        │                                                                    │
│        ▼                                                                    │
│  ┌─────────────┐                                                            │
│  │  UNLOADED   │ ◄─── ClassLoader closed, memory released                   │
│  └─────────────┘                                                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Hot-Reload System

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          HOT-RELOAD SYSTEM                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  PluginClassLoader.startWatcher()                                           │
│       │                                                                     │
│       │ WatchService monitors plugins/ directory                            │
│       ▼                                                                     │
│  ┌───────────────────────────────────────────────────────────────────┐      │
│  │                     File System Events                            │      │
│  ├───────────────────────────────────────────────────────────────────┤      │
│  │                                                                   │      │
│  │  ENTRY_CREATE (new.jar)                                           │      │
│  │       └──► Load new plugin                                        │      │
│  │                                                                   │      │
│  │  ENTRY_MODIFY (existing.jar)                                      │      │
│  │       └──► Unload → Reload → Re-enable if was enabled             │      │
│  │                                                                   │      │
│  │  ENTRY_DELETE (removed.jar)                                       │      │
│  │       └──► Unload plugin                                          │      │
│  │                                                                   │      │
│  └───────────────────────────────────────────────────────────────────┘      │
│                                                                             │
│  Benefits:                                                                  │
│  ├── No restart required for plugin updates                                 │
│  ├── Automatic command sync to Discord                                      │
│  └── Works in Docker (mount plugins/ as volume)                             │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Command System

### Annotation-Based Commands

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      ANNOTATION-BASED COMMANDS                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  @SlashCommand(                                                             │
│      name = "greet",                                                        │
│      description = "Greet someone",                                         │
│      global = false,           // Guild = instant, Global = up to 1 hour    │
│      permissions = {"SEND_MESSAGES"},                                       │
│      options = {                                                            │
│          @CommandOption(name = "user", type = "USER", required = true)      │
│      },                                                                     │
│      subcommands = {                                                        │
│          @Subcommand(name = "formal", description = "Formal greeting"),     │
│          @Subcommand(name = "casual", description = "Casual greeting")      │
│      }                                                                      │
│  )                                                                          │
│  public void greet(SlashCommandInteractionEvent event) { ... }              │
│                                                                             │
│  @TextCommand(value = "hello", aliases = {"hi"})                            │
│  public void hello(CommandContext ctx) { ctx.reply("Hello!"); }             │
│                                                                             │
│  @ButtonHandler("myplugin:confirm")                                         │
│  public void onConfirm(ButtonInteractionEvent event) { ... }                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Built-in Commands

| Command | Description |
|---------|-------------|
| `/settings` | Guild settings (prefix, verbosity, cooldown, channels) |
| `/ai` | AI configuration (toggle, personality, biography, nickname) |
| `/channel` | Channel management (ignore, unignore, list) |
| `/command` | Command management (enable, disable, list) |
| `/ping` | Bot latency |
| `/help` | Available commands |

---

## Brain Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PudelBrain                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Input: User Message                                                        │
│          │                                                                  │
│          ▼                                                                  │
│  ┌─────────────────────┐                                                    │
│  │ TextAnalyzerService │ ◄──── LangChain4j + Ollama                         │
│  │ (Intent, Sentiment) │                                                    │
│  └─────────┬───────────┘                                                    │
│            │                                                                │
│            ▼                                                                │
│  ┌─────────────────────┐    ┌─────────────────────┐                         │
│  │   MemoryManager     │───►│   Memory Embeddings │ (pgvector)              │
│  └─────────┬───────────┘    └─────────────────────┘                         │
│            │                                                                │
│            ▼                                                                │
│  ┌─────────────────────┐                                                    │
│  │  PersonalityEngine  │ ◄──── Biography, Personality, Preferences          │
│  └─────────┬───────────┘                                                    │
│            │                                                                │
│            ▼                                                                │
│  ┌─────────────────────┐    ┌─────────────────────┐                         │
│  │ ResponseGenerator   │───►│   Ollama LLM        │                         │
│  └─────────┬───────────┘    └─────────────────────┘                         │
│            │                                                                │
│            ▼                                                                │
│  Output: Bot Response                                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Database Schema

```
public schema (shared)
├── users
├── guild_settings
├── user_guilds
├── subscriptions
├── plugin_metadata
└── plugin_kv_store

guild_{id} schema (per-guild isolation)
├── conversation_history
├── memory_embeddings (pgvector)
├── agent_*
└── plugin_*
```

---

## Configuration

```env
# Discord
DISCORD_BOT_TOKEN=your_token

# Database
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=pudel
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password

# AI
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=phi3:mini

# JWT (for user authentication)
JWT_PRIVATE_KEY_PATH=./keys/pv.key
JWT_PUBLIC_KEY_PATH=./keys/pb.key

# Admin Authentication
PUDEL_ADMIN_INITIAL_OWNER=123456789012345678
PUDEL_ADMIN_OWNER_PUBLIC_KEY_PATH=./keys/owner_pb.key
```

---

## Authentication Architecture

### User Authentication (Discord OAuth)

Standard OAuth2 flow for dashboard users:

```
User → Discord OAuth → Pudel API → User JWT (RSA signed)
```

See: [AuthFlow.mermaid](./AuthFlow.mermaid)

### Admin Authentication (Mutual RSA)

Mutual RSA authentication for admin panel access. Each admin has their own RSA keypair.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     ADMIN MUTUAL RSA AUTHENTICATION                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Admin                         Pudel                                        │
│  ─────                         ─────                                        │
│                                                                             │
│  1. Login with Discord OAuth   →  Verify, issue User JWT                    │
│                                                                             │
│  2. GET /api/admin/challenge   →  Generate nonce                            │
│                                ←  Sign with Pudel's PRIVATE key             │
│                                   Return { challengeId, nonce, signature }  │
│                                                                             │
│  3. (Optional) Verify Pudel's     Proves server identity                    │
│     signature with PUBLIC key                                               │
│                                                                             │
│  4. Sign nonce with Admin's       Client-side signing (Web Crypto API)      │
│     PRIVATE key (in browser)      Private key never transmitted!            │
│                                                                             │
│  5. POST /api/admin/auth/mutual                                             │
│     { challengeId, signature } →  Extract discordUserId from User JWT       │
│                                   Lookup admin's PUBLIC key from DB         │
│                                   RSA_VERIFY(nonce, signature, publicKey)   │
│                                ←  If valid: Issue AdminJWT (24h session)    │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**Key Security Features:**
- **Mutual Authentication**: Both parties prove identity
- **Per-Admin Keys**: Each admin has unique RSA keypair
- **Private Keys Stay Local**: Never transmitted, signing in browser
- **Challenge Expiry**: 5-minute window prevents replay attacks

See: [AdminMutualAuth.mermaid](./AdminMutualAuth.mermaid)

### Token Types

| Token | Subject | Duration | Purpose |
|-------|---------|----------|---------|
| User JWT | `{discordUserId}` | 7 days | Dashboard access |
| Admin JWT | `pudel-admin-session` | 24 hours | Admin panel access |

### Admin Roles

| Role | Permissions |
|------|-------------|
| OWNER | Full access + manage admin whitelist |
| ADMIN | Plugin management, settings |
| MODERATOR | View-only access |

---

## Migration Notes (v1.x → v2.0.0)

| v1.x | v2.0.0 |
|------|--------|
| `implements PudelPlugin` | `@Plugin` annotation |
| `getPluginInfo()` | `@Plugin(name, version, author)` |
| `onEnable(ctx)` | `@OnEnable` method |
| `shutdown(ctx)` | `@OnShutdown` (returns boolean) |
| Manual `syncCommands()` | Automatic sync |
| Manual `registerSlashCommand()` | `@SlashCommand` annotation |

---

*Last updated: 2026-02-03 for Pudel v2.0.0*
