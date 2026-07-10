# Getting Started with Pudel

Pudel is an AI-powered Discord bot designed to be your personal assistant (maid/secretary) in your Discord guild. Built with **Java 25**, **Spring Boot 4**, and **JDA 6**, Pudel combines intelligent conversation with extensible plugin architecture.

## What is Pudel?

Pudel is your intelligent Discord companion that provides:

- **🤖 AI Chatbot**: Local LLM integration via Ollama for private, intelligent conversations
- **🧠 Agent System**: Autonomous data management - store notes, create tables, remember facts
- **🔌 Plugin System**: Extend functionality with community or commercial plugins
- **🎨 Custom Personality**: Per-guild customization of biography, personality, and dialogue style
- **💾 Memory System**: Vector-based semantic search for conversation context
- **🎙️ Voice Support**: DAVE protocol encryption for voice features (March 2026+)

---

## Quick Start

### Step 1: Invite Pudel to Your Server

Click the invite link provided by your bot hoster to add Pudel to your Discord server. Ensure you grant the required permissions:
- Send Messages
- Read Message History
- Add Reactions
- Connect (for voice features)

### Step 2: Initial Setup

After Pudel joins, a database schema is automatically created for your guild. Use the `/settings` slash command:

```
/settings
```

This opens an interactive **Components V2 panel** with views for:
- **General** — Command prefix, verbosity, cooldown
- **AI** — Toggle, nickname, language, personality, biography
- **Channels** — Log channel, bot channel, ignored channels
- **Commands** — Enable/disable text commands
- **Plugins** — Enable/disable plugins per guild

Navigate between views using buttons — all within a single ephemeral message.

### Step 3: Configure Personality

Make Pudel unique to your server using the `/settings` > **🤖 AI** view:

1. Type `/settings` and click **🤖 AI**
2. **Enable AI** — Click the toggle button
3. **Set Nickname** — Click to open modal (e.g., "Maid")
4. **Set Language** — Click to set language code (e.g., `auto`, `en`, `th`, `ja`)
5. **Set Personality** — Click for long text input (e.g., "Friendly, witty, and slightly mischievous")
6. **Set Biography** — Click for backstory text
7. **Choose Response Length** — Short / Medium / Long
8. **Choose Formality** — Casual / Balanced / Formal
9. **Choose Emote Usage** — None / Minimal / Moderate / Frequent

### Step 4: Start Chatting

Pudel responds when you:
- **@mention** Pudel: `@Pudel hello!`
- **Reply** to Pudel's messages
- **DM** Pudel directly
- Use **trigger keywords** (configurable per guild)

---

## Understanding Pudel's Brain (v2.3.1)

### Architecture Overview

```
Your Message (@mention or trigger)
     ↓
[DiscordEventListener] → PudelBrain.processMessageAsync()
     ↓
[sendTyping().queue()] — non-blocking
     ↓
[EntityExtractor] — intent, sentiment, entities
     ↓
Should use Agent? ─── No ───→ [Context Gathering]
     │                           ├── PassiveContextProcessor (observed messages)
     │                           ├── DialogueHistoryManager (conversation history)
     │                           └── SystemPromptBuilder (personality)
     ↓                                   ↓
     Yes (data management)          [Ollama LLM + Context]
     ↓                                   ↓
     [PudelAgentService.processWithTools()]
     ├── MCP Tools (get_passive_context, get_dialogue_history, etc.)
     └── Agent Tools (create_table, store_data, remember/recall)
     ↓                                   ↓
     [sendMessage().queue()] ←── [Discord Markdown + Truncation]
```

### Key v2 Features

- **Passive Context**: Pudel observes messages it doesn't respond to, extracting entities (users, channels, roles, emojis, URLs, attachments) and storing them with message IDs for later retrieval
- **Dialogue History**: Per-user conversation tracking with `respond_to` message ID references
- **MCP Tools**: 5 built-in tools for the LLM to access brain context on demand
- **Async Handling**: Non-blocking Discord interaction — typing indicator sent immediately, response sent when ready

### Local AI Model (Ollama)

Pudel uses **Ollama** for local inference - your conversations stay private:

| Model | Size | RAM Required | Best For |
|-------|------|--------------|----------|
| qwen3:8b | 8B | ~8GB | Default (recommended) |
| phi3:mini | 3.8B | ~4GB | Balanced |
| gemma:2b | 2B | ~3GB | Lightweight |
| llama3.2:1b | 1B | ~2GB | Minimal resources |

If Ollama is unavailable, Pudel falls back to template-based responses.

### Memory System

Pudel remembers conversations using vector embeddings for semantic search:

| Tier | User Dialogue | Guild Dialogue | Memory Entries |
|------|---------------|----------------|----------------|
| Free | 1,000 | 5,000 | 500 |
| Tier 1 | 1,500 | 7,500 | 750 |
| Tier 2 | 2,000 | 10,000 | 1,000 |
| Unlimited | ∞ | ∞ | ∞ |

---

## Agent Mode (Maid/Secretary)

Pudel can act as your data assistant. Simply ask naturally:

**Creating Tables:**
> "Pudel, create a table called meeting_notes for our team meetings"

**Storing Information:**
> "Save this: The quarterly review is on March 15th at 2 PM"

**Searching:**
> "Find all notes about the quarterly review"

**Quick Memory:**
> "Remember that John's birthday is April 5th"
> "When is John's birthday?"

See [Agent System](./agent) for complete documentation.

---

## Dashboard

The web dashboard provides visual configuration:

1. **Login** with Discord OAuth
2. **Select** a guild where you have Admin permissions
3. **Configure**:
   - Guild settings (prefix, verbosity, channels)
   - AI personality settings
   - View memory usage statistics
   - Browse plugin marketplace

---

## Command Quick Reference

| Command | Description |
|---------|-------------|
| `/settings` | Interactive settings panel (General, AI, Channels, Commands, Plugins) |
| `!help` | Paginated command listing with button navigation |
| `!ping` | Check bot latency (rich embed) |

See [Commands](./commands) for full reference.

---

## Project Architecture

| Module | License | Purpose |
|--------|---------|---------|
| **pudel-api** | MIT | Plugin Development Kit |
| **pudel-core** | AGPL + Exception | Main bot & REST API |

---

*Happy chatting with Pudel!* 🐩
