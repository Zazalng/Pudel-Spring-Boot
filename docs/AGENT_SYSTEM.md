# Pudel Agent System - Maid/Secretary AI

Pudel's Agent System allows her to act as a true maid/secretary by managing data autonomously through natural conversation.

## Overview

The Agent System uses LangChain4j AI Services with tool annotations to enable Pudel to:
- **Create tables** to organize different types of information
- **Store documents, notes, news** and any data users want to keep
- **Search and retrieve** stored information
- **Remember facts** and recall them later

All operations are scoped to the guild/user's isolated database schema for security.

## Architecture

```
User Message
     ↓
[Intent Detection]
     ↓
Should use Agent? ─── No ───→ [Normal Brain/Chat Response]
     │
     Yes
     ↓
[PudelAgentService]
     ↓
[PudelAgentTools] ←→ [AgentDataExecutor]
     ↓                      ↓
[LLM + Tools]         [PostgreSQL Schema]
     ↓
Agent Response
```

## Components

### PudelAgentService (`pudel-model`)
The orchestrator that:
- Manages chat memory per session
- Creates AI service instances with tools
- Builds personality-aware system prompts
- Routes to agent mode when data management intent is detected

### PudelAgentTools (`pudel-model`)
Tool definitions that Pudel can use:

| Tool | Description |
|------|-------------|
| `createTable` | Create a new data table to organize information |
| `listTables` | List all custom tables in the guild/user schema |
| `storeData` | Store a piece of information in a table |
| `archiveMessage` | Archive a Discord message |
| `searchData` | Search for data by keyword |
| `getAllData` | Get all entries from a table |
| `getDataById` | Get specific entry by ID |
| `updateData` | Update an existing entry |
| `deleteData` | Delete an entry by ID |
| `deleteTable` | Delete a table and all its data |
| `remember` | Quick key-value memory storage |
| `recall` | Retrieve a remembered value |
| `listMemories` | List all remembered facts |
| `getCurrentDateTime` | Get current timestamp |

### AgentDataExecutor (`pudel-core`)
Database operations implementation that:
- Manages custom tables with `agent_` prefix
- Handles CRUD operations within schema boundaries
- Tracks table metadata for organization

## Usage Examples

### Creating a Document Table
User: "Pudel, create a table called news_documents to store news articles"
Pudel: "Successfully created table 'agent_news_documents' for storing news articles"

### Storing Information
User: "Save this: Today's headline is 'New Feature Released'"
Pudel: "Got it! I've saved \"Today's headline\" in my agent_news_documents records (ID: 1)"

### Archiving Messages
User: [forwards a message] "Archive this in the news"
Pudel: "Archived message from @User in agent_news (ID: 2)"

### Searching
User: "Find all news about features"
Pudel: "Here's what I found in agent_news:
**New Feature Released** (ID: 1)
Today's headline is 'New Feature Released'..."

### Quick Memory
User: "Remember that the meeting is on Friday"
Pudel: "I'll remember that: meeting = Friday"

User: "When is the meeting?"
Pudel: "meeting: Friday"

## Intent Detection

The agent is activated when messages contain keywords like:
- `remember`, `store`, `save`
- `create` + (table, list, document)
- `add` + (note, document, entry)
- `search`, `find`
- `show` + (my, all, list)
- `delete`, `remove`, `update`, `edit`
- `archive`, `record`, `keep track`
- `recall`, `do you remember`

## Security

1. **Schema Isolation**: All agent tables are created within the guild/user's schema
2. **Table Prefix**: All agent-created tables are prefixed with `agent_` for identification
3. **Input Sanitization**: Table names are sanitized to prevent SQL injection
4. **User Attribution**: All data records the creating user's ID

## Database Schema

Agent tables follow this structure:
```sql
CREATE TABLE guild_XXX.agent_<tablename> (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Metadata tracking table:
```sql
CREATE TABLE guild_XXX.agent_table_metadata (
    table_name VARCHAR(100) PRIMARY KEY,
    description TEXT,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Configuration

Agent behavior is configured via `application.yml`:

```yaml
pudel:
  ollama:
    enabled: true
    base-url: http://localhost:11434
    model: phi3:mini
    timeout-seconds: 60
```

The agent requires Ollama to be running with a compatible model for tool-calling support.

## Subscription Considerations

Agent operations count against the guild/user's storage capacity:
- Each table row counts toward the dialogue limit
- Memory entries use the memory limit
- Custom tables are subject to the overall schema size

See [SUBSCRIPTION_SYSTEM.md](./SUBSCRIPTION_SYSTEM.md) for tier limits.

## Plugin Tools API

Plugins can extend the agent's capabilities by registering custom tools.

### Overview

The Agent Tools API allows plugins to:
- Register custom tools that the AI agent can use
- Respond to user requests with specialized functionality
- Access guild/user context when executing tools

### Creating a Tool Provider

```java
public class MyTools implements AgentToolProvider {
    
    @AgentTool(
        name = "my_tool",
        description = "Description shown to the AI agent",
        keywords = {"keyword1", "keyword2"}
    )
    public String myTool(AgentToolContext context, String param1, int param2) {
        // Tool implementation
        return "Tool result";
    }
}
```

### Registering Tools

In your plugin's `onEnable`:

```java
@Override
public void onEnable(PluginContext context) {
    context.getAgentToolRegistry().registerProvider(
        getPluginInfo().getName(),
        new MyTools()
    );
}
```

### @AgentTool Annotation Options

| Property | Type | Description |
|----------|------|-------------|
| `name` | String | Tool name (defaults to method name) |
| `description` | String | Description for the AI (required) |
| `keywords` | String[] | Keywords to help match user intent |
| `guildOnly` | boolean | Only available in guild channels |
| `dmOnly` | boolean | Only available in DMs |
| `permission` | ToolPermission | Required permission level |
| `priority` | int | Higher priority = preferred when multiple match |

### Tool Method Signature

Tool methods must:
1. Return `String` (the result shown to the user)
2. Have `AgentToolContext` as the first parameter
3. Use only simple types for other parameters (String, int, long, double, boolean)

### AgentToolContext

Provides context about the execution environment:

```java
public interface AgentToolContext {
    long getTargetId();        // Guild ID or User ID
    boolean isGuild();         // True if in guild channel
    long getRequestingUserId(); // User who triggered the agent
    long getGuildId();         // Guild ID (0 if DM)
    Map<String, Object> getContextData(); // Additional data
}
```

### Example: Weather Plugin

See `examples/WeatherToolsPlugin.java` for a complete example.

### How It Works

1. Plugin registers tools via `AgentToolRegistry`
2. When agent mode is triggered, plugin tools are included
3. The AI agent decides which tools to use based on:
   - Tool descriptions
   - Keywords
   - User's message
4. Tools are executed and results returned to the AI
5. AI formulates response including tool results

### Best Practices

1. **Clear Descriptions**: Write descriptions that help the AI understand when to use the tool
2. **Useful Keywords**: Include keywords users might naturally use
3. **Handle Errors**: Return helpful error messages, don't throw exceptions
4. **Be Concise**: Return formatted but concise results
5. **Respect Context**: Use `guildOnly`/`dmOnly` for context-specific tools


