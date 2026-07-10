# Commands Reference

Complete reference for all Pudel since v2.2.2 commands.

---

## Command Types

| Type | Example | Best For |
|------|---------|----------|
| **Slash Commands** | `/settings` | Settings panel, quick actions |
| **Text Commands** | `!help`, `!ping` | Help listings, latency check |

::: tip Unified Architecture
All commands — slash, text, buttons, modals — follow the same `@Plugin` annotation pattern.
Built-in commands use the exact same system as plugin commands.
:::

---

## Built-in Slash Commands

### /settings

Opens a **Components V2 interactive panel** — a single ephemeral message with buttons, modals, and select menus. Navigate between views without subcommands.

**Views:**

| View | Description | Controls |
|------|-------------|----------|
| **⚙️ Main** | Overview of all settings | Navigation buttons to all views |
| **⚙️ General** | Prefix, verbosity, cooldown | Modal inputs, toggle buttons |
| **🤖 AI** | AI toggle, nickname, language, personality, biography, response length, formality, emote usage | Modals + toggle buttons |
| **📢 Channels** | Log/bot channel, ignored channels | EntitySelectMenu channel picker |
| **📝 Commands** | Enable/disable text commands | Paginated toggle buttons |
| **🧩 Plugins** | Enable/disable plugins per guild | Paginated toggle buttons + syncGuildCommands() |

**AI Settings View Details:**

| Setting | Control | Values                                                                       |
|---------|---------|------------------------------------------------------------------------------|
| AI Toggle | Button | Enable / Disable                                                             |
| Nickname | Modal | Custom bot nickname                                                          |
| Language | Modal | Language code (`auto`, `en`, `th`, `ja`, `ko`, `zh`, `de`, `fr`, `es`, etc.) |
| Personality | Modal (long) | Personality traits text                                                      |
| Biography | Modal (long) | Bot backstory text                                                           |
| Response Length | Buttons | Short / Medium / Long                                                        |
| Formality | Buttons | Casual / Balanced / Formal                                                   |
| Emote Usage | Buttons | None / Minimal / Moderate / Frequent                                         |

**Removed commands** (merged into `/settings` panel):
- ~~`/ai`~~ → Settings Panel > AI view
- ~~`/channel`~~ → Settings Panel > Channels view
- ~~`/command`~~ → Settings Panel > Commands view

---

## Built-in Text Commands

Text commands are registered via `BuiltinTextCommands` using the same `@Plugin` annotation pattern.

### !ping

Check bot latency with detailed embed.

```
!ping
```

**Response:** Rich embed showing gateway latency + round-trip time.

### !help

Show all available commands with **button-based paging** (8 commands per page).

```
!help              # Show page 1
!help 2            # Jump to page 2
!help ping         # Detailed help for the 'ping' command
!help settings     # Detailed help for a slash command
```

**Features:**
- ⏮ ◀ ▶ ⏭ navigation buttons
- Groups commands by category: Built-in Slash, Built-in Text, Plugin Slash, Plugin Text
- Per-command detail view with description, usage, permissions, and source
- Respects the guild's configured prefix
- Session expires after 5 minutes
- Only the command issuer can navigate

**Aliases:** `!h`, `!?`

---

## AI Agent (Natural Language)

When AI is enabled, Pudel can manage data using natural language. All agent tools follow the same `AgentToolProvider` + `@AgentTool` standard as plugin tools.

### Built-in Agent Tools

| Tool | Description |
|------|-------------|
| `create_table` | Create a new data table |
| `list_tables` | List all managed tables |
| `store_data` | Save data to a table |
| `archive_message` | Archive a Discord message |
| `search_data` | Search by keyword |
| `get_all_data` | List all entries |
| `get_data_by_id` | Get specific entry |
| `update_data` | Modify an entry |
| `delete_data` | Remove an entry |
| `delete_table` | Delete a table (requires Manage Server) |
| `remember` | Quick key-value memory |
| `recall` | Retrieve a memory |
| `list_memories` | Show all memories |
| `get_current_datetime` | Current date/time |

### Examples

```
"Create a table called notes"
"Save this: important meeting tomorrow"
"Show all my notes"
"Find notes about meeting"
"Delete note #5"
"Remember John's birthday is April 5"
"When is John's birthday?"
```

---

## Plugin Commands

Plugins can register their own commands using annotations:

```java
@SlashCommand(name = "music", description = "Music controls")
public void music(SlashCommandInteractionEvent event) {
    // Plugin handles this
}

@TextCommand(value = "play", description = "Play a song")
public void play(CommandContext ctx) {
    // Text command handler
}
```

See [Plugin Development](./plugin-development) for creating custom commands.

---

## Command Permissions

| Command | Required Permission |
|---------|---------------------|
| `/settings` | Administrator |
| `!ping` | Everyone |
| `!help` | Everyone |

---

## Response Types

| Type | Visibility | Use Case |
|------|------------|----------|
| **Ephemeral** | Only you | `/settings` panel, errors |
| **Public** | Everyone | `!ping`, `!help`, AI chat, plugin commands |

::: info
The `/settings` panel responds ephemerally (only visible to you).
Text commands (`!ping`, `!help`) respond publicly.
:::

---

*Pudel since v2.2.2 — All built-in commands use the same `@Plugin` annotation pattern as plugins*
