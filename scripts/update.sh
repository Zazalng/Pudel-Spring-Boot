#!/bin/bash

# ===========================================
# Pudel Discord Bot - Update Script
# Simple: git pull → rebuild → restart
#
# USAGE:
#   ./scripts/update.sh            # update from default branch
#   ./scripts/update.sh develop    # update from a specific branch
# ===========================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

BRANCH="${1:-main}"

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}   Pudel Discord Bot - Update           ${NC}"
echo -e "${BLUE}=========================================${NC}"

# Check prerequisites
if ! command -v docker &> /dev/null; then
    echo -e "${RED}Error: Docker is not installed.${NC}"
    exit 1
fi

if ! command -v git &> /dev/null; then
    echo -e "${RED}Error: Git is not installed.${NC}"
    exit 1
fi

# ===========================================
# [1/4] Pull latest code from Git
# ===========================================
echo -e "\n${YELLOW}[1/4] Pulling latest changes (branch: ${BRANCH})...${NC}"
git fetch origin "${BRANCH}"

LOCAL=$(git rev-parse HEAD)
REMOTE=$(git rev-parse "origin/${BRANCH}")

if [ "$LOCAL" = "$REMOTE" ]; then
    echo -e "${GREEN}Already up to date!${NC}"
    read -p "Rebuild anyway? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${GREEN}Nothing to do. Exiting.${NC}"
        exit 0
    fi
else
    git pull origin "${BRANCH}"
fi

# Detect Ollama profile from .env
OLLAMA_ENABLED="false"
if [ -f .env ]; then
    OLLAMA_ENABLED=$(grep "^OLLAMA_ENABLED=" .env 2>/dev/null | cut -d'=' -f2 | tr -d '"' | tr -d "'" | tr '[:upper:]' '[:lower:]')
fi

COMPOSE_CMD="docker compose"
if [ "$OLLAMA_ENABLED" = "true" ]; then
    COMPOSE_CMD="docker compose --profile ollama"
    echo -e "${BLUE}Ollama profile detected — will include it in restart${NC}"
fi

# ===========================================
# [2/4] Rebuild the Docker image
# ===========================================
echo -e "\n${YELLOW}[2/4] Rebuilding Pudel Docker image...${NC}"
$COMPOSE_CMD build pudel

# ===========================================
# [3/4] Restart only the bot (database stays up)
# ===========================================
echo -e "\n${YELLOW}[3/4] Restarting Pudel container...${NC}"
$COMPOSE_CMD up -d pudel

# ===========================================
# [4/4] Done
# ===========================================
echo -e "\n${GREEN}=========================================${NC}"
echo -e "${GREEN}   Update completed successfully!        ${NC}"
echo -e "${GREEN}=========================================${NC}"

echo -e "\n${BLUE}Container status:${NC}"
$COMPOSE_CMD ps

echo -e "\n${BLUE}To view logs: ${YELLOW}docker compose logs -f pudel${NC}"
