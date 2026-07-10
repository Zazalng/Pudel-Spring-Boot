# Subscription System

Guide to Pudel's subscription tiers and capacity limits.

## Overview

The subscription system allows hosters to define capacity limits for users and guilds. Each tier provides different limits for:

- **Dialogue History** - Conversation messages stored
- **Memory Entries** - Key-value memory capacity
- **Features** - Voice, priority support, etc.

---

## Default Tiers

### Free Tier

```yaml
FREE:
  name: "Free"
  description: "Basic free tier with limited capacity"
  user:
    dialogueLimit: 1000
    memoryLimit: 100
  guild:
    dialogueLimit: 5000
    memoryLimit: 500
  features:
    chatbot: true
    customPersonality: true
    pluginLimit: -1      # maxInt
    voiceEnabled: false
    prioritySupport: false
```

### Tier 1 (Supporter)

```yaml
TIER_1:
  name: "Supporter"
  description: "Basic paid subscription"
  user:
    dialogueLimit: 1500
    memoryLimit: 150
  guild:
    dialogueLimit: 7500
    memoryLimit: 750
  features:
    chatbot: true
    customPersonality: true
    pluginLimit: -1
    voiceEnabled: true
    prioritySupport: false
```

### Tier 2 (Premium)

```yaml
TIER_2:
  name: "Premium"
  description: "Premium subscription"
  user:
    dialogueLimit: 2000
    memoryLimit: 200
  guild:
    dialogueLimit: 10000
    memoryLimit: 1000
  features:
    chatbot: true
    customPersonality: true
    pluginLimit: -1
    voiceEnabled: true
    prioritySupport: true
```

### Unlimited

```yaml
UNLIMITED:
  name: "Unlimited"
  description: "No limits"
  user:
    dialogueLimit: -1    # -1 = maxInt
    memoryLimit: -1
  guild:
    dialogueLimit: -1
    memoryLimit: -1
  features:
    chatbot: true
    customPersonality: true
    pluginLimit: -1
    voiceEnabled: true
    prioritySupport: true
```

---

## Tier Comparison

| Feature | Free | Tier 1 | Tier 2 | Unlimited |
|---------|------|--------|--------|-----------|
| **User Dialogue** | 1,000 | 1,500 | 2,000 | ∞ |
| **User Memory** | 100 | 150 | 200 | ∞ |
| **Guild Dialogue** | 5,000 | 7,500 | 10,000 | ∞ |
| **Guild Memory** | 500 | 750 | 1,000 | ∞ |
| **Voice Features** | ❌ | ✅ | ✅ | ✅ |
| **Priority Support** | ❌ | ❌ | ✅ | ✅ |
| **Custom Personality** | ✅ | ✅ | ✅ | ✅ |

---

## How Limits Work

### Dialogue Limit

Each conversation message counts toward the dialogue limit:

```
User message → Pudel response = 1 dialogue entry
```

When limit is reached:
1. Oldest entries are automatically pruned
2. Configurable via `autoCleanup.keepPercentage`
3. Only entries older than `minAgeDays` are removed

### Memory Limit

Memory entries are key-value pairs:

```
"birthday_john" → "April 5th"
```

Each unique key counts as one entry.

### Agent Data

Agent tables count toward dialogue limit:
- Each row in `agent_*` tables = 1 dialogue entry
- Tables themselves don't have a separate limit

---

## Checking Usage

### Via API

```http
GET /api/subscription/guild/{guildId}/usage
```

Response:
```json
{
  "guildId": "123456789",
  "tier": "FREE",
  "active": true,
  "dialogue": {
    "current": 1500,
    "limit": 5000,
    "percentage": 30.0
  },
  "memory": {
    "current": 50,
    "limit": 500,
    "percentage": 10.0
  }
}
```

### Via Dashboard

1. Login to web dashboard
2. Select your guild
3. View "Usage" section

---

## Custom Tiers

Hosters can define custom tiers in `subscription-tiers.yml`:

```yaml
subscription:
  tiers:
    # ... default tiers ...
    
    ENTERPRISE:
      name: "Enterprise"
      description: "For large communities"
      user:
        dialogueLimit: 10000
        memoryLimit: 2000
      guild:
        dialogueLimit: 100000
        memoryLimit: 20000
      features:
        chatbot: true
        customPersonality: true
        pluginLimit: -1
        voiceEnabled: true
        prioritySupport: true
        customFeature: true  # Custom features
        
  defaultTier: FREE
  enableExpiration: true
```

---

## Subscription Types

### User Subscription

Applies to DM interactions with Pudel:
- Personal dialogue history
- Personal memory storage

### Guild Subscription

Applies to guild-wide usage:
- All users share the guild's limits
- Separate from individual user limits

---

## Expiration

Subscriptions can expire if `enableExpiration: true`:

```yaml
subscription:
  enableExpiration: true
```

After expiration:
- User/guild reverts to `defaultTier`
- Data is **not** deleted
- New entries may be blocked until cleanup

---

## Auto-Cleanup

Configure automatic cleanup when limits are reached:

```yaml
pudel:
  memory:
    autoCleanup:
      enabled: true
      keepPercentage: 80    # Keep newest 80%
      minAgeDays: 30        # Only delete if older than 30 days
```

Cleanup process:
1. Triggered when capacity > 95%
2. Removes oldest entries first
3. Keeps at least `keepPercentage` of limit
4. Never removes entries newer than `minAgeDays`

---

## Schema Isolation

Each guild/user has isolated storage:

```
guild_123456789/
  ├── dialogue_history
  ├── memory
  ├── user_preferences
  └── agent_* (custom tables)

user_152140348980723712/
  ├── dialogue_history
  └── memory
```

Benefits:
- Complete data isolation
- Per-entity capacity tracking
- No cross-contamination

---

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/subscription/tiers` | Get all tier definitions |
| `GET /api/subscription/tiers/{name}` | Get specific tier |
| `GET /api/subscription/guild/{id}/usage` | Guild usage stats |
| `GET /api/subscription/user/{id}/usage` | User usage stats |

---

## For Hosters

### Setting User/Guild Tier

Currently managed via database:

```sql
INSERT INTO subscriptions (target_id, subscription_type, tier, tier_name)
VALUES ('123456789', 'GUILD', 'TIER_1', 'Supporter')
ON CONFLICT (target_id, subscription_type) 
DO UPDATE SET tier = 'TIER_1', tier_name = 'Supporter';
```

### Monitoring Usage

Check capacity across all guilds:

```sql
SELECT 
    target_id,
    tier,
    dialogue_limit,
    memory_limit
FROM subscriptions 
WHERE subscription_type = 'GUILD';
```

---

## FAQ

**Q: What happens when I hit the limit?**
A: Auto-cleanup removes oldest entries. New entries still work.

**Q: Can I upgrade mid-period?**
A: Yes, limits increase immediately.

**Q: Is data deleted on downgrade?**
A: No, but new entries may be blocked until under limit.

**Q: Do plugins count toward limits?**
A: Plugin data uses the Plugin Database system (separate from subscription limits).

---

*Manage your Pudel resources wisely!* 📊
