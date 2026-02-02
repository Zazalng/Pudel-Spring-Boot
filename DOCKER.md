# Pudel Discord Bot - Docker Deployment Guide

This guide covers deploying Pudel Discord Bot using Docker on Ubuntu Linux.

## Prerequisites

- Docker 20.10+ and Docker Compose v2+
- At least 4GB RAM (8GB recommended for Ollama)
- Discord Bot Token (from [Discord Developer Portal](https://discord.com/developers/applications))

## Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/World-Standard-Group/Pudel-Spring-Boot.git
cd Pudel-Spring-Boot
```

### 2. Configure Environment

```bash
# Copy the example environment file
cp .env.example .env

# Edit the .env file with your configuration
nano .env
```

**Required settings in `.env`:**
```env
# Discord Bot (REQUIRED)
DISCORD_BOT_TOKEN=your_discord_bot_token_here
DISCORD_CLIENT_ID=your_client_id_here
DISCORD_CLIENT_SECRET=your_client_secret_here
```

### 2.5. Generate RSA Keys for JWT Authentication

Generate RSA key pair for JWT token signing:

```bash
# Navigate to the keys directory
cd keys

# Generate RSA private key (PKCS#8 format)
openssl genpkey -algorithm RSA -out private.key -pkeyopt rsa_keygen_bits:2048

# Extract public key
openssl rsa -pubout -in private.key -out public.key

# Set proper permissions
chmod 600 private.key
chmod 644 public.key

# Return to project root
cd ..
```

> **Important:** The keys directory is automatically mounted into the Docker container at `/app/keys/`. Never commit private keys to version control!

### 3. Start the Services

**Option A: Production Mode (Pre-built)**
```bash
docker-compose up -d
```

**Option B: Auto-Update Mode (Pulls latest on restart)**
```bash
docker-compose -f docker-compose.dev.yml up -d
```

### 4. Pull Ollama Models (if using AI features)

```bash
# Pull the main chat model
docker-compose exec ollama ollama pull qwen3:8b

# Pull the embedding model
docker-compose exec ollama ollama pull qwen3-embedding:8b
```

## Environment Variables Reference

### Database Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_HOST` | `localhost` | PostgreSQL host |
| `POSTGRES_PORT` | `5432` | PostgreSQL port |
| `POSTGRES_USER` | `postgres` | Database username |
| `POSTGRES_PASS` | - | Database password |
| `POSTGRES_DB` | `pudel` | Database name |

### Discord Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `DISCORD_BOT_TOKEN` | - | **Required.** Bot token from Discord Developer Portal |
| `DISCORD_CLIENT_ID` | - | OAuth2 Client ID |
| `DISCORD_CLIENT_SECRET` | - | OAuth2 Client Secret |
| `DISCORD_REDIRECT_URI` | `http://localhost/auth/callback` | OAuth2 redirect URI |

### Security Configuration
| Variable | Default | Description                             |
|----------|---------|-----------------------------------------|
| `JWT_PRIVATE_KEY_PATH` | `/app/keys/private.key` | Path to RSA private key (mounted via volume) |
| `JWT_PUBLIC_KEY_PATH` | `/app/keys/public.key` | Path to RSA public key (mounted via volume) |
| `JWT_EXPIRATION` | `604800000` | JWT expiration in milliseconds (7 days) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | Comma-separated CORS origins            |

### Ollama LLM Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `OLLAMA_ENABLED` | `true` | Enable/disable Ollama AI |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama API URL |
| `OLLAMA_MODEL` | `qwen3:8b` | Chat model name |
| `EMBEDDING_ENABLED` | `true` | Enable semantic search |
| `EMBEDDING_MODEL` | `qwen3-embedding:8b` | Embedding model name |

### Server Configuration
| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Application HTTP port |
| `JAVA_OPTS` | `-Xms512m -Xmx2g...` | JVM options |

### Auto-Update Configuration (docker-compose.dev.yml only)
| Variable | Default | Description |
|----------|---------|-------------|
| `GIT_REPO` | `https://github.com/World-Standard-Group/Pudel-Spring-Boot.git` | Git repository URL |
| `GIT_BRANCH` | `main` | Branch to track |
| `AUTO_UPDATE` | `true` | Pull latest on container restart |

## Docker Files Overview

| File | Purpose |
|------|---------|
| `Dockerfile` | Production multi-stage build |
| `Dockerfile.dev` | Development/auto-update mode |
| `docker-compose.yml` | Production deployment |
| `docker-compose.dev.yml` | Development with auto-updates |
| `.env.example` | Environment template |

## Updating the Bot

### Production Mode
```bash
# Pull latest changes
git pull origin main

# Rebuild and restart
docker-compose build --no-cache pudel
docker-compose up -d pudel
```

### Auto-Update Mode
```bash
# Simply restart the container - it will pull latest automatically
docker-compose -f docker-compose.dev.yml restart pudel
```

### Using the Update Script
```bash
chmod +x scripts/update.sh
./scripts/update.sh
```

## Common Commands

```bash
# View logs
docker-compose logs -f pudel

# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: deletes data)
docker-compose down -v

# Restart a specific service
docker-compose restart pudel

# Check service status
docker-compose ps

# Execute command in container
docker-compose exec pudel bash

# View Ollama models
docker-compose exec ollama ollama list
```

## GPU Support for Ollama

To enable GPU acceleration for Ollama, uncomment the GPU section in `docker-compose.yml`:

```yaml
ollama:
  # ... other config ...
  deploy:
    resources:
      reservations:
        devices:
          - driver: nvidia
            count: all
            capabilities: [gpu]
```

Make sure you have [NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/install-guide.html) installed.

## Volumes and Data Persistence

| Volume | Path | Description |
|--------|------|-------------|
| `postgres_data` | `/var/lib/postgresql/data` | Database files |
| `ollama_data` | `/root/.ollama` | Downloaded AI models |
| `pudel_logs` | `/app/logs` | Application logs |
| `./plugins` (bind mount) | `/app/plugins` | Bot plugins (hot-reloadable) |
| `./keys` (bind mount) | `/app/keys` | RSA keys for JWT (read-only) |

## Plugin Management in Docker

Pudel supports **live plugin installation** even while running in Docker. The plugins directory is mounted as a bind mount, allowing you to add, update, or remove plugins without rebuilding the container.

### How It Works

1. **Bind Mount**: The `./plugins` directory on your host is mounted to `/app/plugins` in the container
2. **NIO WatchService**: Pudel watches for file changes in real-time
3. **Polling Fallback**: If WatchService doesn't work (some Docker/NFS setups), polling every 60 seconds detects changes
4. **Stability Delay**: Waits 1 second after file changes to ensure uploads are complete

### Adding Plugins at Runtime

```bash
# Simply copy a JAR file to the plugins directory
cp my-awesome-plugin-1.0.0.jar ./plugins/

# The plugin will be auto-detected and loaded within seconds
docker-compose logs -f pudel | grep "New plugin discovered"
```

### Updating Plugins

```bash
# Replace the JAR file
cp my-awesome-plugin-1.1.0.jar ./plugins/my-awesome-plugin-1.0.0.jar

# If the plugin is DISABLED: Update is applied immediately
# If the plugin is ENABLED: Update is queued (requires disable or restart)

# To force apply update, disable the plugin first:
# (via API or bot command)
```

### Removing Plugins

```bash
# Simply delete the JAR file
rm ./plugins/my-awesome-plugin-1.0.0.jar

# The plugin will be detected as removed
```

### Plugin Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PUDEL_PLUGIN_DIRECTORY` | `/app/plugins` | Path to plugins directory |
| `PUDEL_PLUGIN_ENABLE_AUTO_DISCOVERY` | `true` | Auto-detect new plugins |
| `PUDEL_PLUGIN_ENABLE_AUTO_LOAD` | `true` | Auto-load discovered plugins |
| `PUDEL_PLUGIN_RETRY_INTERVAL` | `30000` | Retry interval for failed plugins (ms) |
| `PUDEL_PLUGIN_MAX_RETRIES` | `3` | Max retry attempts for failed plugins |

### Monitoring Plugin Status

```bash
# Check plugin loading logs
docker-compose logs pudel | grep -E "(plugin|Plugin)"

# Watch for plugin changes in real-time
docker-compose logs -f pudel | grep -i plugin
```

### Failed Plugin Handling

If a plugin fails to load:
1. Pudel tracks the failure with the JAR's hash
2. Retries up to `PUDEL_PLUGIN_MAX_RETRIES` times at `PUDEL_PLUGIN_RETRY_INTERVAL` intervals
3. If the JAR file is updated (hash changes), retry count resets
4. After max retries, waits for JAR update before trying again

### Docker Volume vs Bind Mount

**Bind Mount (Recommended for plugins)**:
```yaml
volumes:
  - ./plugins:/app/plugins  # Live access from host
```

**Named Volume (NOT recommended for plugins)**:
```yaml
volumes:
  - pudel_plugins:/app/plugins  # Isolated from host
```

The production `docker-compose.yml` uses a bind mount for plugins to allow runtime management.

## Troubleshooting

### Plugin Issues

#### Plugins not being detected
1. Check the plugins directory is properly mounted:
   ```bash
   docker-compose exec pudel ls -la /app/plugins
   ```
2. Verify NIO WatchService is working (check logs for "NIO WatchService started")
3. If WatchService fails, the polling fallback runs every 60 seconds
4. Ensure JAR files have `.jar` extension

#### Plugin fails to load
1. Check logs for specific error:
   ```bash
   docker-compose logs pudel | grep -A5 "Failed to load"
   ```
2. Verify plugin has valid `MANIFEST.MF` with `Plugin-Main` or a `plugin.yml`
3. Try updating the JAR to trigger a reload
4. Use the retry mechanism: replace the JAR file to reset retry counter

#### Plugin not updating when JAR is replaced
1. If plugin is ENABLED, updates are queued
2. Disable the plugin first, then the update applies immediately
3. Or restart the container to apply all pending updates

### Bot not connecting to Discord
1. Verify `DISCORD_BOT_TOKEN` is correct
2. Check logs: `docker-compose logs pudel`
3. Ensure bot has proper intents enabled in Discord Developer Portal

### Database connection issues
1. Wait for PostgreSQL to be ready: `docker-compose logs postgres`
2. Verify credentials match between services

### Ollama not responding
1. Check if Ollama is running: `docker-compose ps ollama`
2. Verify models are downloaded: `docker-compose exec ollama ollama list`
3. Check Ollama logs: `docker-compose logs ollama`

### Out of memory
1. Adjust `JAVA_OPTS` in `.env` to reduce memory usage
2. Consider using a smaller Ollama model (e.g., `qwen3:4b`)

## Security Recommendations

1. **Generate strong RSA keys** - Use at least 2048-bit RSA keys for JWT signing
2. **Protect private key** - Ensure `private.key` has strict permissions (600) and never commit to version control
3. **Use strong database password**
4. **Limit CORS_ALLOWED_ORIGINS** to your actual domains
5. **Use HTTPS** in production (configure reverse proxy)
6. **Keep images updated** regularly
7. **Mount keys as read-only** - The keys volume is mounted with `:ro` flag for security

## License

This project is licensed under AGPLv3 with the Pudel Plugin Exception. See [LICENSE](LICENSE) for details.

