#!/bin/bash

# ===========================================
# Pudel Discord Bot - Update Script
# Pulls latest changes from Git and rebuilds
# ===========================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}   Pudel Discord Bot - Update Script    ${NC}"
echo -e "${BLUE}=========================================${NC}"

# Load environment variables (filter out JAVA_OPTS which contains spaces)
if [ -f .env ]; then
    # Only load simple key=value pairs needed by this script
    # JAVA_OPTS is used by Docker/Java, not needed here
    while IFS='=' read -r key value; do
        # Skip comments and empty lines
        [[ "$key" =~ ^#.*$ ]] && continue
        [[ -z "$key" ]] && continue
        # Skip JAVA_OPTS (has spaces that break shell)
        [[ "$key" == "JAVA_OPTS" ]] && continue
        # Strip quotes if present
        value="${value%\"}"
        value="${value#\"}"
        # Export the variable
        export "$key"="$value"
    done < .env
fi

GIT_BRANCH=${GIT_BRANCH:-main}

echo -e "\n${YELLOW}[1/5] Fetching latest changes from Git...${NC}"
git fetch origin ${GIT_BRANCH}

# Check if there are updates
LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse origin/${GIT_BRANCH})

if [ "$LOCAL" = "$REMOTE" ]; then
    echo -e "${GREEN}Already up to date!${NC}"
    read -p "Do you want to rebuild anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 0
    fi
fi

echo -e "\n${YELLOW}[2/5] Pulling latest changes...${NC}"
git pull origin ${GIT_BRANCH}

echo -e "\n${YELLOW}[3/5] Stopping current containers...${NC}"
# Check if Ollama is enabled to stop it too
OLLAMA_ENABLED=$(grep "OLLAMA_ENABLED=" .env 2>/dev/null | cut -d'=' -f2 | tr -d '"' | tr -d "'" | tr '[:upper:]' '[:lower:]')
if [ "$OLLAMA_ENABLED" = "true" ]; then
    docker compose --profile ollama down
else
    docker compose down
fi

echo -e "\n${YELLOW}[4/5] Rebuilding Docker images...${NC}"
docker compose build --no-cache pudel

echo -e "\n${YELLOW}[5/5] Starting containers...${NC}"
if [ "$OLLAMA_ENABLED" = "true" ]; then
    echo -e "${BLUE}Starting with Ollama (AI enabled)...${NC}"
    docker compose --profile ollama up -d
else
    echo -e "${BLUE}Starting without Ollama (AI disabled)...${NC}"
    docker compose up -d
fi

echo -e "\n${GREEN}=========================================${NC}"
echo -e "${GREEN}   Update completed successfully!        ${NC}"
echo -e "${GREEN}=========================================${NC}"

echo -e "\n${BLUE}Checking container status...${NC}"
docker compose ps

echo -e "\n${BLUE}To view logs, run: docker compose logs -f pudel${NC}"

