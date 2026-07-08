#!/bin/bash

# ===========================================
# Pudel Discord Bot - Quick Start Script
# One-click setup for new installations
# ===========================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}   Pudel Discord Bot - Quick Start      ${NC}"
echo -e "${BLUE}=========================================${NC}"

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker compose &> /dev/null; then
    echo -e "${RED}Error: Docker Compose is not installed. Please install Docker Compose first.${NC}"
    exit 1
fi

# Create .env file if it doesn't exist
if [ ! -f .env ]; then
    echo -e "\n${YELLOW}Creating .env file from template...${NC}"
    cp .env.example .env
    echo -e "${YELLOW}Please edit .env file and add your Discord bot token!${NC}"
    echo -e "${YELLOW}Then run this script again.${NC}"
    exit 0
fi

# Check if Discord token is set
if grep -q "your_discord_bot_token_here" .env; then
    echo -e "${RED}Error: Please set your DISCORD_BOT_TOKEN in .env file!${NC}"
    echo -e "Get your token from: https://discord.com/developers/applications"
    exit 1
fi

# Load environment variables (filter out JAVA_OPTS which contains spaces)
while IFS= read -r line || [ -n "$line" ]; do
    # Strip Windows carriage return
    line="${line%$'\r'}"
    # Skip comments and empty lines
    [[ "$line" =~ ^#.*$ ]] && continue
    [[ -z "$line" ]] && continue
    # Extract key and value
    key="${line%%=*}"
    value="${line#*=}"
    # Skip empty keys
    [[ -z "$key" ]] && continue
    # Skip JAVA_OPTS (has spaces that break shell)
    [[ "$key" == "JAVA_OPTS" ]] && continue
    # Export the variable (strip quotes if present)
    value="${value%\"}"
    value="${value#\"}"
    export "$key"="$value"
done < .env

# ===========================================
# Determine DB deployment intent from POSTGRES_SSL:
#   POSTGRES_SSL=false  -> LOCAL:    run the bundled postgres container.
#   POSTGRES_SSL=true   -> EXTERNAL: connect to a remote Postgres over
#                          verify-full mTLS; the container is NOT started
#                          and the client cert/key/CA must exist in keys/.
# ===========================================
POSTGRES_SSL_LC=$(echo "${POSTGRES_SSL:-false}" | tr '[:upper:]' '[:lower:]')
if [ "$POSTGRES_SSL_LC" = "true" ]; then
    DB_MODE="external"
else
    DB_MODE="local"
fi

if [ "$DB_MODE" = "external" ]; then
    echo -e "\n${BLUE}Database mode: EXTERNAL (verify-full mTLS to ${POSTGRES_HOST:-<POSTGRES_HOST unset>})${NC}"

    # External connections must be verify-full.
    MODE_LC=$(echo "${POSTGRES_SSL_MODE:-}" | tr '[:upper:]' '[:lower:]')
    if [ "$MODE_LC" != "verify-full" ]; then
        echo -e "${RED}Error: external Postgres requires POSTGRES_SSL_MODE=verify-full (got '${POSTGRES_SSL_MODE:-unset}').${NC}"
        exit 1
    fi
    if [ -z "${POSTGRES_HOST}" ] || [ "${POSTGRES_HOST}" = "localhost" ] || [ "${POSTGRES_HOST}" = "postgres" ]; then
        echo -e "${RED}Error: set POSTGRES_HOST to your external database host (not '${POSTGRES_HOST:-unset}').${NC}"
        exit 1
    fi

    # Verify the client mTLS material referenced by .env exists on the host.
    # Paths are user-defined; map the in-container /app/keys prefix back to keys/.
    missing=""
    for var in POSTGRES_SSL_CA_CERT POSTGRES_SSL_CLIENT_CERT POSTGRES_SSL_CLIENT_KEY; do
        path="${!var}"
        if [ -z "$path" ]; then
            missing="${missing} ${var} (unset)"
            continue
        fi
        host_path="${path/#\/app\/keys/keys}"
        [ -f "$host_path" ] || missing="${missing} ${host_path} (${var})"
    done
    if [ -n "$missing" ]; then
        echo -e "${RED}Error: verify-full requires the client CA/cert/key in keys/:${NC}"
        for m in $missing; do echo -e "  ${RED}- ${m}${NC}"; done
        echo -e "${YELLOW}See keys/README.md for how to generate/place them.${NC}"
        exit 1
    fi
    echo -e "${GREEN}External mTLS material present — postgres container will be skipped.${NC}"
else
    echo -e "\n${BLUE}Database mode: LOCAL (bundled postgres container, no SSL)${NC}"
fi

echo -e "\n${YELLOW}[1/4] Pulling required Docker images...${NC}"
if [ "$DB_MODE" = "local" ]; then
    docker compose --profile local pull postgres
else
    echo -e "${BLUE}External DB mode — skipping local postgres image pull.${NC}"
fi

# Check if Ollama is enabled
OLLAMA_ENABLED=$(grep "OLLAMA_ENABLED=" .env | cut -d'=' -f2 | tr -d '"' | tr -d "'" | tr '[:upper:]' '[:lower:]')

if [ "$OLLAMA_ENABLED" = "true" ]; then
    echo -e "${BLUE}Ollama is enabled - pulling Ollama image...${NC}"
    docker compose --profile ollama pull ollama
fi

echo -e "\n${YELLOW}[2/4] Building Pudel Bot image...${NC}"
docker compose build pudel

echo -e "\n${YELLOW}[3/4] Starting services...${NC}"
if [ "$DB_MODE" = "local" ]; then
    docker compose --profile local up -d postgres
    echo -e "${BLUE}Waiting for PostgreSQL to be ready...${NC}"
    sleep 10
else
    echo -e "${BLUE}External DB mode — not starting a local postgres container.${NC}"
fi

# Start Ollama if enabled (using profile)
if [ "$OLLAMA_ENABLED" = "true" ]; then
    echo -e "\n${YELLOW}Starting Ollama service...${NC}"
    docker compose --profile ollama up -d ollama

    echo -e "${BLUE}Waiting for Ollama to be ready...${NC}"
    sleep 15

    # Pull the configured model
    OLLAMA_MODEL=$(grep "OLLAMA_MODEL=" .env | cut -d'=' -f2 | tr -d '"' | tr -d "'")
    EMBEDDING_MODEL=$(grep "EMBEDDING_MODEL=" .env | cut -d'=' -f2 | tr -d '"' | tr -d "'")

    echo -e "\n${YELLOW}Pulling Ollama models...${NC}"
    docker compose exec -T ollama ollama pull ${OLLAMA_MODEL:-qwen3:8b} || true
    docker compose exec -T ollama ollama pull ${EMBEDDING_MODEL:-qwen3-embedding:8b} || true
else
    echo -e "${BLUE}Ollama is disabled - skipping AI service${NC}"
fi

echo -e "\n${YELLOW}[4/4] Starting Pudel Bot...${NC}"
docker compose up -d pudel

echo -e "\n${GREEN}=========================================${NC}"
echo -e "${GREEN}   Setup completed successfully!         ${NC}"
echo -e "${GREEN}=========================================${NC}"

echo -e "\n${BLUE}Container Status:${NC}"
if [ "$DB_MODE" = "local" ]; then
    docker compose --profile local ps
else
    docker compose ps
fi

echo -e "\n${BLUE}Useful commands:${NC}"
echo -e "  View logs:     ${YELLOW}docker compose logs -f pudel${NC}"
echo -e "  Stop all:      ${YELLOW}docker compose down${NC}"
echo -e "  Restart:       ${YELLOW}docker compose restart pudel${NC}"
echo -e "  Update:        ${YELLOW}git pull && docker compose build pudel && docker compose up -d pudel${NC}"

echo -e "\n${BLUE}The bot should be online shortly! Check logs for status.${NC}"