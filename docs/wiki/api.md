# REST API Reference

Complete REST API documentation for Pudel.

## Base URL

```
http://localhost:8080/api
```

## Authentication

Most endpoints require JWT authentication:

```
Authorization: Bearer <jwt_token>
```

Obtain tokens via Discord OAuth callback.

---

## Authentication Endpoints

### POST /api/auth/discord/callback

Exchange Discord OAuth code for JWT token.

**Request:**
```json
{
  "code": "discord_oauth_code",
  "redirectUri": "http://localhost:5173/auth/callback"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJSUzI1NiJ9...",
  "user": {
    "id": "152140348980723712",
    "username": "Username",
    "avatar": "avatar_hash"
  }
}
```

### GET /api/auth/user/guilds

Get all guilds for authenticated user.

**Auth:** Required

**Response:**
```json
[
  {
    "id": "123456789",
    "name": "My Server",
    "icon": "icon_hash",
    "owner": true,
    "permissions": 8,
    "hasBot": true
  }
]
```

---

## Bot Status

### GET /api/bot/status

Get bot online status.

**Auth:** Not required

**Response:**
```json
{
  "online": true,
  "guildCount": 5,
  "userCount": 1234,
  "shardCount": 1,
  "uptime": "2d 5h 30m",
  "version": "1.0.0"
}
```

### GET /api/bot/stats

Get detailed bot statistics.

**Response:**
```json
{
  "guildCount": 5,
  "userCount": 1234,
  "channelCount": 50,
  "commandsExecuted": 10000,
  "messagesProcessed": 50000,
  "shards": [
    {
      "id": 0,
      "status": "CONNECTED",
      "guildCount": 5,
      "ping": 45
    }
  ]
}
```

---

## Guild Settings

### GET /api/guilds/{guildId}/settings

Get guild settings.

**Auth:** Required (must have permissions in guild)

**Response:**
```json
{
  "guildId": "123456789",
  "commandPrefix": "!",
  "verbosityLevel": 3,
  "commandCooldown": 0,
  "logChannelId": null,
  "botChannelId": null,
  "botBiography": "A helpful assistant",
  "botPersonality": "friendly, helpful",
  "botPreferences": "casual conversation",
  "dialogueStyle": "natural",
  "botNickname": "Pudel",
  "language": "en",
  "aiEnabled": true,
  "systemPromptPrefix": null,
  "ignoreChannels": [],
  "disabledCommands": []
}
```

### PATCH /api/guilds/{guildId}/settings

Update guild settings (partial update).

**Auth:** Required

**Request:**
```json
{
  "commandPrefix": "?",
  "botPersonality": "formal, professional"
}
```

---

## Guild Data

### GET /api/guilds/{guildId}/data/schema/status

Check if guild schema is initialized.

**Response:**
```json
{
  "guildId": "123456789",
  "schemaCreated": true,
  "tables": ["dialogue_history", "memory", "user_preferences"]
}
```

### POST /api/guilds/{guildId}/data/schema/initialize

Initialize guild schema (creates tables).

### GET /api/guilds/{guildId}/data/memory/{key}

Get a memory entry.

### POST /api/guilds/{guildId}/data/memory

Store a memory entry.

**Request:**
```json
{
  "key": "meeting_time",
  "value": "Every Tuesday at 3 PM",
  "category": "schedule"
}
```

---

## Plugins

### GET /api/plugins/installed

List all installed plugins on this instance.

**Response:**
```json
{
  "plugins": [
    {
      "pluginName": "DefaultPudelPlugins",
      "pluginVersion": "1.0.0",
      "pluginAuthor": "Pudel Team",
      "pluginDescription": "Default plugin bundle",
      "enabled": true,
      "loaded": true
    }
  ],
  "total": 1
}
```

### GET /api/plugins/installed/{name}

Get a specific installed plugin by name.

### GET /api/plugins/enabled

List all enabled plugins on this instance.

### POST /api/admin/plugins/{name}/enable

Enable a plugin globally.

**Auth:** Required (admin RSA auth)

### POST /api/admin/plugins/{name}/disable

Disable a plugin globally.

**Auth:** Required (admin RSA auth)

### Guild-Level Plugin Control

Guilds can disable specific globally-enabled plugins for their server
via the `disabled_plugins` field in guild settings (`PATCH /api/guilds/{guildId}/settings`).

---

## Plugin Marketplace

### GET /api/plugins/market

List marketplace plugins.

**Query Parameters:**
- `category` - Filter by category
- `search` - Search by name/description

**Response:**
```json
[
  {
    "id": "uuid",
    "name": "My Plugin",
    "description": "A cool plugin",
    "category": "moderation",
    "authorId": "152140348980723712",
    "authorName": "Author",
    "version": "1.0.0",
    "downloads": 100,
    "sourceUrl": "https://github.com/...",
    "licenseType": "MIT",
    "isCommercial": false,
    "createdAt": "2025-01-01T00:00:00Z"
  }
]
```

### GET /api/plugins/market/top

Get top downloaded plugins.

### POST /api/plugins/publish

Publish a new plugin.

**Auth:** Required

**Request:**
```json
{
  "name": "My Plugin",
  "description": "A cool plugin for...",
  "category": "moderation",
  "version": "1.0.0",
  "sourceUrl": "https://github.com/user/plugin",
  "licenseType": "MIT"
}
```

---

## Subscriptions

### GET /api/subscription/tiers

Get all subscription tiers.

**Response:**
```json
{
  "FREE": {
    "name": "Free",
    "description": "Basic free tier",
    "dialogueLimit": 5000,
    "memoryLimit": 500,
    "features": {
      "chatbot": true,
      "voiceEnabled": false
    }
  }
}
```

### GET /api/subscription/guild/{guildId}/usage

Get guild usage statistics.

**Auth:** Required

**Response:**
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

---

## Brain / AI

### GET /api/brain/status

Get AI system status.

**Response:**
```json
{
  "ollamaAvailable": true,
  "ollamaModel": "qwen3:8b",
  "embeddingEnabled": true,
  "embeddingDimension": 1024
}
```

### POST /api/brain/analyze

Analyze text for intent and sentiment.

**Auth:** Required

**Request:**
```json
{
  "text": "What time is the meeting tomorrow?"
}
```

**Response:**
```json
{
  "intent": "question",
  "sentiment": "neutral",
  "language": "en",
  "entities": [],
  "isQuestion": true,
  "isCommand": false,
  "keywords": ["time", "meeting", "tomorrow"]
}
```

---

## Error Responses

All errors return a consistent format:

```json
{
  "error": "Error type",
  "message": "Human readable message",
  "timestamp": "2025-01-01T00:00:00Z",
  "path": "/api/endpoint"
}
```

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 400 | Bad Request |
| 401 | Unauthorized |
| 403 | Forbidden |
| 404 | Not Found |
| 429 | Rate Limited |
| 500 | Server Error |

---

## Admin API (Self-Hosted)

The Admin API provides endpoints for managing self-hosted Pudel instances using **Mutual RSA Authentication**. Each admin has their own RSA keypair.

### Authentication Flow (Mutual RSA)

1. Login with Discord OAuth first (get User JWT)
2. Request a challenge: `GET /api/admin/challenge`
3. (Optional) Verify Pudel's signature with Pudel's public key
4. Sign the challenge nonce with your RSA private key (client-side)
5. Submit signed challenge: `POST /api/admin/auth/mutual`
6. Use returned AdminJWT for subsequent requests

### GET /api/admin/public-key

Get Pudel's public key for verifying server identity.

**Auth:** Not required

**Response:**
```json
{
  "publicKey": "-----BEGIN PUBLIC KEY-----\n...",
  "algorithm": "RSA",
  "usage": "Use this key to verify challenge signatures from Pudel"
}
```

### GET /api/admin/challenge

Request an authentication challenge. Pudel signs this with its private key.

**Auth:** Not required

**Response:**
```json
{
  "challengeId": "uuid",
  "nonce": "uuid",
  "timestamp": 1738540800000,
  "expiry": 1738541100000,
  "signature": "eyJhbGciOiJSUzI1NiJ9...",
  "message": "Verify this signature with Pudel's public key, then sign the nonce with your private key"
}
```

### GET /api/admin/check

Check if current Discord user is an admin.

**Auth:** User JWT required (Discord OAuth)

**Response:**
```json
{
  "isAdmin": true,
  "isAdminSession": false,
  "adminRole": "OWNER",
  "canModify": true,
  "canManageAdmins": true,
  "hasPublicKey": true,
  "message": "You can access the admin panel. Complete challenge verification to continue."
}
```

### POST /api/admin/auth/mutual

Authenticate with Mutual RSA - submit signed challenge.

**Auth:** User JWT required (Discord OAuth)

**Request:**
```json
{
  "challengeId": "uuid-from-challenge",
  "signature": "base64-encoded-rsa-signature-of-nonce"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Mutual authentication successful",
  "adminToken": "eyJhbGciOiJSUzI1NiJ9...",
  "discordUserId": "123456789012345678",
  "discordUsername": "YourUsername",
  "adminRole": "OWNER",
  "canModify": true,
  "canManageAdmins": true,
  "expiresAt": 1738627200000,
  "expiresIn": 86400
}
```

### POST /api/admin/auth ⚠️ DEPRECATED

**Status:** Returns `410 GONE`

This endpoint has been removed. Use `/api/admin/auth/mutual` instead.

### POST /api/admin/logout

Invalidate admin session.

**Auth:** AdminJWT required

**Response:**
```json
{
  "success": true,
  "message": "Logged out successfully"
}
```

### GET /api/admin/status

Get system status.

**Auth:** AdminJWT required

**Response:**
```json
{
  "admin": {
    "discordUserId": "123456789012345678",
    "adminRole": "OWNER",
    "canModify": true,
    "canManageAdmins": true
  },
  "javaVersion": "25",
  "osName": "Linux",
  "memory": {
    "max": "2048 MB",
    "total": "512 MB",
    "free": "256 MB",
    "used": "256 MB"
  },
  "plugins": {
    "total": 5,
    "enabled": 3,
    "loaded": 3
  },
  "admins": {
    "total": 2,
    "enabled": 2
  }
}
```

### GET /api/admin/plugins

List all plugins.

**Auth:** AdminJWT required

**Response:**
```json
{
  "plugins": [
    {
      "id": 1,
      "name": "MyPlugin",
      "version": "1.0.0",
      "author": "Author",
      "description": "Description",
      "jarFile": "myplugin.jar",
      "enabled": true,
      "loaded": true,
      "loadError": null
    }
  ],
  "total": 1,
  "enabled": 1
}
```

### POST /api/admin/plugins/upload

Upload a plugin JAR file.

**Auth:** AdminJWT required (ADMIN+ role)

**Request:** `multipart/form-data` with `file` field

**Response:**
```json
{
  "success": true,
  "message": "Plugin uploaded successfully: myplugin.jar",
  "filename": "myplugin.jar",
  "size": 12345,
  "path": "/app/plugins/myplugin.jar"
}
```

### POST /api/admin/plugins/{name}/enable

Enable a plugin.

**Auth:** AdminJWT required (ADMIN+ role)

### POST /api/admin/plugins/{name}/disable

Disable a plugin.

**Auth:** AdminJWT required (ADMIN+ role)

### POST /api/admin/plugins/{name}/reload

Reload a plugin.

**Auth:** AdminJWT required (ADMIN+ role)

### DELETE /api/admin/plugins/{name}

Remove a plugin (unload and delete JAR).

**Auth:** AdminJWT required (ADMIN+ role)

### GET /api/admin/whitelist

List admin whitelist entries.

**Auth:** AdminJWT required (OWNER role only)

**Response:**
```json
{
  "admins": [
    {
      "id": 1,
      "discordUserId": "123456789012345678",
      "discordUsername": "OwnerUser",
      "adminRole": "OWNER",
      "enabled": true,
      "canModify": true,
      "canManageAdmins": true,
      "hasPublicKey": true,
      "lastLogin": "2026-02-03T10:00:00"
    }
  ],
  "total": 1,
  "enabled": 1
}
```

### POST /api/admin/whitelist

Add a Discord user to admin whitelist with their RSA public key.

**Auth:** AdminJWT required (OWNER role only)

**Request:**
```json
{
  "discordUserId": "987654321098765432",
  "discordUsername": "NewAdmin",
  "adminRole": "ADMIN",
  "publicKeyPem": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhki...\n-----END PUBLIC KEY-----",
  "note": "Added for plugin management"
}
```

> **Note:** `publicKeyPem` is **required** for Mutual RSA Authentication. The new admin must generate their own RSA keypair and provide their public key.

### PUT /api/admin/whitelist/{discordUserId}

Update an admin whitelist entry.

**Auth:** AdminJWT required (OWNER role only)

**Request:**
```json
{
  "adminRole": "MODERATOR",
  "enabled": false,
  "publicKeyPem": "-----BEGIN PUBLIC KEY-----\nNEW_KEY...\n-----END PUBLIC KEY-----",
  "note": "Updated role and key"
}
```

### DELETE /api/admin/whitelist/{discordUserId}

Remove a Discord user from admin whitelist.

**Auth:** AdminJWT required (OWNER role only)

---

## Rate Limiting

| Endpoint Type | Limit |
|---------------|-------|
| Authentication | 10/min |
| Read operations | 60/min |
| Write operations | 30/min |

Headers returned:
```
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 55
X-RateLimit-Reset: 1609459200
```

---

## WebSocket (Future)

```
ws://localhost:8080/ws
```

Topics:
- `/topic/bot/status` - Bot status updates
- `/topic/guild/{id}/chat` - Guild chat events
- `/topic/plugins` - Plugin status changes

---

*API Version: 2.1.1*
