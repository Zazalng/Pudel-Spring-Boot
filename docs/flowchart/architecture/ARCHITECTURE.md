# Pudel Architecture v1.1.0

This document describes the complete architecture of Pudel Discord Bot.

---

## Table of Contents

- [System Overview](#system-overview)
- [Module Structure](#module-structure)
- [Data Flow](#data-flow)
- [Command System](#command-system)
- [Plugin System](#plugin-system)
- [Brain Architecture](#brain-architecture)
- [Database Schema](#database-schema)
- [Configuration](#configuration)

---

## System Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                             PUDEL DISCORD BOT v1.1.0                         │
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
│  │                         Plugin System                                 │   │
│  │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────────────┐  │   │
│  │  │ Plugin1 │ │ Plugin2 │ │ Plugin3 │ │  ...    │ │ Default Plugins │  │   │
│  │  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────────────┘  │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Module Structure

```
pudel/
├── pudel-api/          # Plugin Development Kit (MIT License)
│   ├── PudelPlugin.java         # Main plugin interface
│   ├── PluginContext.java       # Runtime context for plugins
│   ├── PluginInfo.java          # Plugin metadata
│   ├── SimplePlugin.java        # Annotation-based plugin helper
│   ├── command/                 # Text command API
│   │   ├── CommandContext.java
│   │   └── TextCommandHandler.java
│   ├── interaction/             # Discord interactions API
│   │   ├── SlashCommandHandler.java
│   │   ├── ButtonHandler.java
│   │   ├── ModalHandler.java
│   │   ├── SelectMenuHandler.java
│   │   ├── ContextMenuHandler.java
│   │   ├── AutoCompleteHandler.java
│   │   └── InteractionManager.java
│   ├── event/                   # Event listener API
│   ├── audio/                   # Voice/Audio API (DAVE support)
│   │   ├── VoiceManager.java
│   │   ├── DAVEProvider.java
│   │   └── AudioProvider.java
│   ├── agent/                   # Agent Tools API
│   │   ├── AgentTool.java
│   │   ├── AgentToolProvider.java
│   │   └── AgentToolRegistry.java
│   └── database/                # Plugin database API
│       ├── PluginDatabaseManager.java
│       ├── PluginRepository.java
│       └── TableSchema.java
│
├── pudel-model/        # AI/Brain Module (AGPL-3.0)
│   ├── PudelModelService.java   # Main model orchestration
│   ├── ollama/
│   │   └── OllamaClient.java    # Ollama LLM client
│   ├── embedding/
│   │   └── OllamaEmbeddingService.java
│   ├── analyzer/
│   │   └── TextAnalyzerService.java
│   └── agent/
│       ├── PudelAgentService.java  # Agent orchestration
│       ├── PudelAgentTools.java    # Built-in agent tools
│       └── AgentDataExecutor.java  # Data operations interface
│
├── pudel-core/         # Main Bot Application (AGPL-3.0)
│   ├── Pudel.java               # Entry point
│   ├── bootstrap/               # Startup runners
│   │   ├── CommandBootstrapRunner.java
│   │   ├── PluginBootstrapRunner.java
│   │   └── SchemaBootstrapRunner.java
│   ├── brain/                   # Pudel's brain logic
│   │   ├── PudelBrain.java      # Central intelligence
│   │   ├── context/
│   │   │   └── PassiveContextProcessor.java
│   │   ├── memory/
│   │   │   └── MemoryManager.java
│   │   ├── personality/
│   │   │   └── PersonalityEngine.java
│   │   └── response/
│   │       └── ResponseGenerator.java
│   ├── command/                 # Built-in text commands
│   │   ├── CommandRegistry.java
│   │   ├── CommandContextImpl.java
│   │   └── builtin/
│   │       ├── HelpCommandHandler.java
│   │       ├── PingCommandHandler.java
│   │       ├── SettingsCommandHandler.java  # Wizard-based
│   │       └── AICommandHandler.java        # Wizard-based
│   ├── interaction/             # Discord interactions
│   │   ├── InteractionManagerImpl.java
│   │   ├── InteractionEventListener.java
│   │   └── builtin/             # Built-in slash commands
│   │       ├── SettingsSlashCommand.java
│   │       ├── AISlashCommand.java
│   │       ├── ChannelSlashCommand.java
│   │       ├── CommandManageSlashCommand.java
│   │       └── BuiltinSlashCommandRegistrar.java
│   ├── discord/                 # JDA event handling
│   │   ├── DiscordEventListener.java
│   │   ├── GuildEventListener.java
│   │   └── ReactionNavigationListener.java
│   ├── plugin/                  # Plugin loader/manager
│   │   ├── PluginClassLoader.java
│   │   ├── PluginContextImpl.java
│   │   └── PluginContextFactory.java
│   ├── service/                 # Business logic
│   │   ├── ChatbotService.java
│   │   ├── CommandExecutionService.java
│   │   ├── GuildInitializationService.java
│   │   ├── PluginWatcherService.java
│   │   ├── SubscriptionService.java
│   │   └── ...
│   ├── controller/              # REST API endpoints
│   │   ├── AuthController.java
│   │   ├── GuildSettingsController.java
│   │   ├── PluginController.java
│   │   └── ...
│   ├── entity/                  # JPA entities
│   │   ├── GuildSettings.java
│   │   ├── User.java
│   │   ├── Subscription.java
│   │   └── ...
│   ├── config/                  # Configuration classes
│   │   └── springboot/
│   │       ├── JwtUtil.java
│   │       └── SecurityConfiguration.java
│   ├── audio/                   # Voice implementation
│   │   └── VoiceManagerImpl.java
│   └── agent/                   # Agent implementation
│       ├── AgentToolRegistryImpl.java
│       └── AgentDataExecutorImpl.java
│
└── database/           # SQL Scripts
    ├── init.sql                 # Complete initial schema
    └── migrations/              # Version migrations
        ├── V2_add_plugin_licensing.sql
        ├── V3_add_ai_settings.sql
        └── ...
```

---

## Data Flow

### 1. Discord Message Flow

```
Discord User                              Pudel Bot
    │                                        │
    │ ──── Message ────────────────────────► │
    │                                        │
    │                              ┌─────────┴───────────┐
    │                              │ DiscordEventListener│
    │                              └─────────┬───────────┘
    │                                        │
    │                              ┌─────────▼───────────┐
    │                              │   Route Message     │
    │                              └─────────┬───────────┘
    │                                        │
    │           ┌────────────────────────────┼────────────────────────────┐
    │           │                            │                            │
    │    ┌──────▼──────┐             ┌───────▼───────┐            ┌──────▼──────┐
    │    │ !command    │             │  @mention     │            │   AI Chat   │
    │    │ (prefix)    │             │ (alternative) │            │  (trigger)  │
    │    └──────┬──────┘             └───────┬───────┘            └──────┬──────┘
    │           │                            │                           │
    │           └────────────┬───────────────┘                           │
    │                        │                                           │
    │              ┌─────────▼─────────┐                        ┌────────▼────────┐
    │              │ CommandExecution  │                        │ ChatbotService  │
    │              │     Service       │                        └────────┬────────┘
    │              └─────────┬─────────┘                                 │
    │                        │                                  ┌────────▼────────┐
    │              ┌─────────▼─────────┐                        │  Agent Mode?    │
    │              │  CommandRegistry  │                        └────────┬────────┘
    │              └─────────┬─────────┘                          yes/   │   \no
    │                        │                                   ┌──────▼──┐ ┌──▼──────┐
    │              ┌─────────▼─────────┐                         │ Agent   │ │ Brain   │
    │              │ Built-in / Plugin │                         │ Service │ │ Service │
    │              │    Command        │                         └────┬────┘ └────┬────┘
    │              └─────────┬─────────┘                              │           │
    │                        │                                        └─────┬─────┘
    │                        └────────────────────────┬─────────────────────┘
    │                                                 │
    │ ◄──────── Response ─────────────────────────────│
    │                                                 │
```

### 2. Slash Command Flow

```
Discord User                              Pudel Bot
    │                                        │
    │ ──── /command ───────────────────────► │
    │                                        │
    │                              ┌─────────┴──────────────┐
    │                              │InteractionEventListener│
    │                              └─────────┬──────────────┘
    │                                        │
    │                              ┌─────────▼──────────────┐
    │                              │ InteractionManagerImpl │
    │                              └─────────┬──────────────┘
    │                                        │
    │                    ┌───────────────────┼───────────────────┐
    │                    │                   │                   │
    │             ┌──────▼──────┐     ┌──────▼──────┐     ┌──────▼──────┐
    │             │   Built-in  │     │   Plugin    │     │   Modal/    │
    │             │   Slash Cmd │     │   Slash Cmd │     │   Button    │
    │             └──────┬──────┘     └──────┬──────┘     └──────┬──────┘
    │                    │                   │                   │
    │                    └───────────┬───────┴───────────────────┘
    │                                │
    │ ◄──────── Reply (Ephemeral) ───│
    │                                │
```

### 3. API Request Flow

```
Vue Frontend                Spring Boot                 Database
    │                           │                          │
    │ ── GET /api/guilds ─────► │                          │
    │    (JWT Token)            │                          │
    │                    ┌──────┴──────┐                   │
    │                    │ JWT Filter  │                   │
    │                    │ Validate    │                   │
    │                    └──────┬──────┘                   │
    │                           │                          │
    │                    ┌──────▼──────┐                   │
    │                    │ Controller  │                   │
    │                    └──────┬──────┘                   │
    │                           │                          │
    │                    ┌──────▼──────┐                   │
    │                    │ Service     │ ── Query ────────►│
    │                    └──────┬──────┘                   │
    │                           │ ◄─────── Result ─────────│
    │                           │                          │
    │ ◄─── JSON Response ───────│                          │
    │                           │                          │
```

---

## Command System

Pudel supports two command interfaces:

### Text Commands (Prefix-based)

```
┌─────────────────────────────────────────────────────────────┐
│                    Text Command System                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Built-in Commands (pudel-core):                            │
│  ├── !help        - Show help information                   │
│  ├── !ping        - Check bot latency                       │
│  ├── !settings    - View settings + wizard                  │
│  └── !ai          - AI config + wizard + text settings      │
│                                                             │
│  Best for:                                                  │
│  ├── Interactive wizards (sequential input)                 │
│  ├── Multi-line text input (biography, personality)         │
│  └── Quick access without slash menu                        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Slash Commands (Discord Native)

```
┌─────────────────────────────────────────────────────────────┐
│                   Slash Command System                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Built-in Slash Commands (pudel-core):                      │
│  ├── /settings    - Guild settings (prefix, verbosity...)   │
│  ├── /ai          - AI settings (enable, language...)       │
│  ├── /channel     - Channel management (ignore, listen)     │
│  └── /command     - Command management (enable, disable)    │
│                                                             │
│  Best for:                                                  │
│  ├── Simple toggles and selections                          │
│  ├── Ephemeral responses (private to user)                  │
│  ├── Discord autocomplete support                           │
│  └── Mobile-friendly UI                                     │
│                                                             │
│  Plugin Slash Commands:                                     │
│  └── Registered via InteractionManager.registerSlashCommand │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Plugin System

### Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                     Plugin Lifecycle                        │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   [JAR File] ──load──► [initialize()] ──► [LOADED]          │
│                                              │              │
│                                     enable   │   disable    │
│                                       ▼      ▲              │
│                                   [onEnable()]              │
│                                       │                     │
│                                       ▼                     │
│                                  [ENABLED]                  │
│                                       │                     │
│                                       ▼                     │
│                                 [onDisable()]               │
│                                       │                     │
│                                       ▼                     │
│                                  [DISABLED]                 │
│                                       │                     │
│                                       ▼                     │
│   [shutdown()] ◄──unload──────  [shutdown()]                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Hot-Reload (PluginWatcherService)

```
┌─────────────────────────────────────────────────────────────┐
│                  Plugin Hot-Reload System                   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  PluginWatcherService                                       │
│       │                                                     │
│       │ (every 60 seconds)                                  │
│       ▼                                                     │
│  ┌───────────────┐                                          │
│  │ Scan /plugins │                                          │
│  │ Compute SHA256│                                          │
│  └──────┬────────┘                                          │
│         │                                                   │
│         ├─── New JAR ──► Copy to temp ──► Load plugin       │
│         │                                                   │
│         ├─── Hash changed (disabled) ──► Apply update now   │
│         │                                                   │
│         └─── Hash changed (enabled) ──► Queue update        │
│                                          │                  │
│                                          ▼                  │
│                                    Warn every 1 min         │
│                                    until disable/restart    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Plugin API Capabilities

```
┌─────────────────────────────────────────────────────────────┐
│                    Plugin Context API                       │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Commands & Interactions:                                   │
│  ├── registerCommand(name, handler)                         │
│  ├── getInteractionManager()                                │
│  │   ├── registerSlashCommand(pluginId, handler)            │
│  │   ├── registerButtonHandler(pluginId, handler)           │
│  │   ├── registerModalHandler(pluginId, handler)            │
│  │   ├── registerSelectMenuHandler(pluginId, handler)       │
│  │   ├── registerContextMenuHandler(pluginId, handler)      │
│  │   ├── registerAutoCompleteHandler(pluginId, handler)     │
│  │   └── syncCommands()                                     │
│  │                                                          │
│  Events:                                                    │
│  └── registerEventListener(listener)                        │
│                                                             │
│  Database:                                                  │
│  └── getDatabaseManager()                                   │
│      ├── createTable(schema)                                │
│      ├── getRepository(table, entityClass)                  │
│      └── getKeyValueStore(namespace)                        │
│                                                             │
│  Agent Tools:                                               │
│  └── getAgentToolRegistry()                                 │
│      └── registerProvider(pluginId, toolProvider)           │
│                                                             │
│  Voice (DAVE):                                              │
│  └── getVoiceManager()                                      │
│      ├── connect(guildId, channelId)                        │
│      ├── disconnect(guildId)                                │
│      └── isDAVEAvailable(guildId)                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Brain Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           PudelBrain                                    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Input: User Message                                                    │
│          │                                                              │
│          ▼                                                              │
│  ┌─────────────────────┐                                                │
│  │ TextAnalyzerService │ ◄──── LangChain4j + Ollama                     │
│  │ (Intent, Sentiment, │                                                │
│  │  Entities, Language)│                                                │
│  └─────────┬───────────┘                                                │
│            │                                                            │
│            ▼                                                            │
│  ┌─────────────────────┐    ┌─────────────────────┐                     │
│  │   MemoryManager     │───►│   Memory Embeddings │ (pgvector)          │
│  │ (Retrieve context)  │    │   (Semantic Search) │                     │
│  └─────────┬───────────┘    └─────────────────────┘                     │
│            │                                                            │
│            ▼                                                            │
│  ┌─────────────────────┐                                                │
│  │  PersonalityEngine  │ ◄──── Biography, Personality, Preferences      │
│  │ (Apply guild traits)│       DialogueStyle, Quirks, Language          │
│  └─────────┬───────────┘                                                │
│            │                                                            │
│            ▼                                                            │
│  ┌─────────────────────┐    ┌─────────────────────┐                     │
│  │ ResponseGenerator   │───►│   Ollama LLM        │                     │
│  │ (Build prompt)      │    │   (Generate reply)  │                     │
│  └─────────┬───────────┘    └─────────────────────┘                     │
│            │                                                            │
│            ▼                                                            │
│  Output: Bot Response                                                   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Agent System (PudelAgentService)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Agent System                                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  User Message (data management intent detected)                         │
│          │                                                              │
│          ▼                                                              │
│  ┌─────────────────────┐                                                │
│  │  PudelAgentService  │ ◄──── LangChain4j AI Service                   │
│  └─────────┬───────────┘                                                │
│            │                                                            │
│            ▼                                                            │
│  ┌─────────────────────┐    ┌─────────────────────┐                     │
│  │  PudelAgentTools    │    │ AgentToolRegistry   │                     │
│  │  (Built-in tools)   │    │ (Plugin tools)      │                     │
│  │  - createTable      │    │ - Custom tools via  │                     │
│  │  - storeData        │    │   @AgentTool        │                     │
│  │  - searchData       │    └─────────────────────┘                     │
│  │  - remember/recall  │                                                │
│  └─────────┬───────────┘                                                │
│            │                                                            │
│            ▼                                                            │
│  ┌─────────────────────┐                                                │
│  │ AgentDataExecutor   │ ──► guild_{id}.agent_* tables                  │
│  │ (Database ops)      │                                                │
│  └─────────────────────┘                                                │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Passive Context Processing

```
┌─────────────────────────────────────────────────────────────────────────┐
│                   PassiveContextProcessor                               │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  All Messages (even non-trigger)                                        │
│          │                                                              │
│          ▼                                                              │
│  ┌───────────────────────────┐                                          │
│  │ Track channel conversation│                                          │
│  │ (rolling window)          │                                          │
│  └─────────────┬─────────────┘                                          │
│                │                                                        │
│                ▼                                                        │
│  ┌───────────────────────────┐                                          │
│  │ Queue for async processing│                                          │
│  └─────────────┬─────────────┘                                          │
│                │                                                        │
│                ▼                                                        │
│  ┌───────────────────────────┐                                          │
│  │ Build context for future  │──► Available when user triggers Pudel    │
│  │ responses                 │                                          │
│  └───────────────────────────┘                                          │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Database Schema

### Public Schema (Main Tables)

```sql
┌─────────────────────────────────────────────────────────────┐
│                     PUBLIC SCHEMA                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  users ──────────────── user_guilds ──────────────── guilds │
│  │                           │                          │   │
│  │                           │                          │   │
│  └──────── subscriptions ────┘                          │   │
│                                                         │   │
│                              guild_settings ────────────┘   │
│                              (prefix, ai_enabled,           │
│                               biography, personality,       │
│                               dialogue_style, language,     │
│                               response_length, formality,   │
│                               emote_usage, quirks,          │
│                               ignored_channels,             │
│                               disabled_commands, ...)       │
│                                                             │
│  plugin_metadata ─────── Installed plugin info              │
│                                                             │
│  plugin_database_registry ── Plugin table tracking          │
│                                                             │
│  market_plugins ─────── Marketplace listings                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Per-Guild Schema

```sql
┌─────────────────────────────────────────────────────────────┐
│               SCHEMA: guild_{guild_id}                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  dialogue_history ─────── Stores conversation history       │
│  │                        (user_id, channel_id, messages)   │
│  │                                                          │
│  user_preferences ─────── Per-user settings in this guild   │
│  │                        (preferred_name, custom_settings) │
│  │                                                          │
│  memory ───────────────── Key-value memory storage          │
│  │                        (key, value, category)            │
│  │                                                          │
│  memory_embeddings ────── Vector embeddings (pgvector)      │
│  │                        (embedding vector, memory_id)     │
│  │                                                          │
│  agent_table_metadata ─── Agent-created table tracking      │
│  │                                                          │
│  agent_* ──────────────── Agent-created data tables         │
│  │                        (dynamic, user-defined)           │
│  │                                                          │
│  plugin_{id}_* ────────── Plugin-created tables             │
│                           (isolated per-plugin)             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Configuration

### Configuration Files

| File | Location | Purpose |
|------|----------|---------|
| `application.yml` | `pudel-core/src/main/resources/` | Main bot config |
| `subscription-tiers.yml` | `pudel-core/src/main/resources/` | Tier limits |

### Key Configuration Sections

```yaml
pudel:
  discord:
    token: ${DISCORD_BOT_TOKEN}
    prefix: "!"
    oauth:
      client-id: ${DISCORD_CLIENT_ID}
      client-secret: ${DISCORD_CLIENT_SECRET}
    
  ollama:
    enabled: true
    base-url: http://localhost:11434
    model: phi3:mini
    timeout-seconds: 60
    
  chatbot:
    triggers:
      onMention: true
      onDirectMessage: true
      onReplyToBot: true
      keywords: ["pudel"]
    
    embedding:
      enabled: true
      dimension: 384
    
  subscription:
    tiers:
      FREE: { dialogueLimit: 5000, memoryLimit: 500 }
      TIER_1: { dialogueLimit: 7500, memoryLimit: 750 }
      TIER_2: { dialogueLimit: 10000, memoryLimit: 1000 }

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pudel
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD}
```

---

## Security

### Authentication Flow

```
┌────────────────────────────────────────────────────────────────┐
│                   Discord OAuth 2.0 Flow                       │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  1. User clicks "Login with Discord"                           │
│          │                                                     │
│          ▼                                                     │
│  2. Redirect to Discord OAuth                                  │
│          │                                                     │
│          ▼                                                     │
│  3. User authorizes                                            │
│          │                                                     │
│          ▼                                                     │
│  4. Redirect back with auth code                               │
│          │                                                     │
│          ▼                                                     │
│  5. Exchange code for Discord tokens                           │
│          │                                                     │
│          ▼                                                     │
│  6. Fetch user info from Discord                               │
│          │                                                     │
│          ▼                                                     │
│  7. Generate Pudel JWT token (RSA signed)                      │
│          │                                                     │
│          ▼                                                     │
│  8. Return JWT to frontend                                     │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Permission Checking

| Action | Required Permission |
|--------|---------------------|
| Basic commands (`!help`, `!ping`) | Everyone |
| Settings slash commands | Administrator |
| AI settings | Administrator |
| Channel management | Administrator |
| REST API | Valid JWT + Guild Admin |

---

## Version Info

| Component       | Version | License    |
|-----------------|---------|------------|
| Pudel Core      | 1.1.0   | AGPL-3.0 + Plugin Exception |
| Pudel API (PDK) | 1.1.0   | MIT        |
| Pudel Model     | 1.1.0   | AGPL-3.0   |
| JDA             | 6.3.0   | Apache-2.0 |
| Spring Boot     | 4.0.x   | Apache-2.0 |
| LangChain4j     | 1.x     | Apache-2.0 |
| PostgreSQL      | 14+     | PostgreSQL |
| pgvector        | 0.5+    | PostgreSQL |

---

*Last Updated: February 2026*
*Version: 1.1.0*
