#!/bin/bash

# ===========================================
# Pudel Discord Bot - Docker Entrypoint
# Handles permissions fix and application startup
#
# UPDATE WORKFLOW (from the host):
#   git pull origin main
#   docker compose build pudel
#   docker compose up -d pudel
# ===========================================

set -e

echo "========================================="
echo "   Pudel Discord Bot - Starting...      "
echo "========================================="

# ===========================================
# [1/2] Fix permissions for bind-mounted directories
# ===========================================
echo "[1/2] Fixing directory permissions..."

for dir in /app/plugins /app/data /app/logs; do
    if [ -d "$dir" ]; then
        chown -R pudel:pudel "$dir" 2>/dev/null || true
        chmod -R 755 "$dir" 2>/dev/null || true
    fi
done

echo "      Permissions fixed"

# ===========================================
# [2/2] Start the application
# ===========================================
echo "[2/2] Starting Pudel Bot..."

cd /app

# Sanitize environment variables
# Strip Windows carriage returns (\r) that may leak from .env files edited on Windows
# Strip literal quotes from JAVA_OPTS that Docker Compose may pass through from .env
POSTGRES_HOST=$(echo "${POSTGRES_HOST}" | tr -d '\r')
POSTGRES_PORT=$(echo "${POSTGRES_PORT}" | tr -d '\r')
POSTGRES_USER=$(echo "${POSTGRES_USER}" | tr -d '\r')
POSTGRES_PASS=$(echo "${POSTGRES_PASS}" | tr -d '\r')
POSTGRES_DB=$(echo "${POSTGRES_DB}" | tr -d '\r')
SERVER_PORT=$(echo "${SERVER_PORT}" | tr -d '\r')
JAVA_OPTS=$(echo "${JAVA_OPTS}" | tr -d '\r' | tr -d '"')

# Debug: Print resolved database configuration (password masked)
PASS_MASKED=$(echo "${POSTGRES_PASS}" | sed 's/./*/g')
echo "Database config: host=${POSTGRES_HOST:-unset}, port=${POSTGRES_PORT:-unset}, db=${POSTGRES_DB:-unset}, user=${POSTGRES_USER:-unset}, pass=${PASS_MASKED:-unset}"
echo "JAVA_OPTS: ${JAVA_OPTS}"

# Build Spring Boot arguments only for variables that are actually set
# This prevents overriding application.yml defaults with empty values
SPRING_ARGS=""

if [ -n "${POSTGRES_HOST}" ] && [ -n "${POSTGRES_PORT}" ] && [ -n "${POSTGRES_DB}" ]; then
    SPRING_ARGS="${SPRING_ARGS} --spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}"
fi

if [ -n "${POSTGRES_USER}" ]; then
    SPRING_ARGS="${SPRING_ARGS} --spring.datasource.username=${POSTGRES_USER}"
fi

if [ -n "${POSTGRES_PASS}" ]; then
    SPRING_ARGS="${SPRING_ARGS} --spring.datasource.password=${POSTGRES_PASS}"
fi

if [ -n "${SERVER_PORT}" ]; then
    SPRING_ARGS="${SPRING_ARGS} --server.port=${SERVER_PORT}"
fi

echo "Spring args: $(echo "${SPRING_ARGS}" | sed 's/--spring.datasource.password=[^ ]*/--spring.datasource.password=****/g')"

# Drop privileges and run as pudel user
# Note: JAVA_OPTS is intentionally unquoted to allow word splitting of JVM flags
exec gosu pudel java ${JAVA_OPTS} -jar app.jar ${SPRING_ARGS}

