#!/bin/bash

# ===========================================
# Pudel Discord Bot - Docker Entrypoint
# Handles permissions fix and application startup
# ===========================================

set -e

echo "========================================="
echo "   Pudel Discord Bot - Starting...      "
echo "========================================="

# Fix permissions for bind-mounted directories
# This is needed because bind mounts from host may have different ownership
echo "[1/4] Fixing directory permissions..."

# Ensure plugins directory is writable
if [ -d "/app/plugins" ]; then
    chown -R pudel:pudel /app/plugins 2>/dev/null || true
    chmod -R 755 /app/plugins 2>/dev/null || true
fi

# Ensure data directory is writable
if [ -d "/app/data" ]; then
    chown -R pudel:pudel /app/data 2>/dev/null || true
fi

# Ensure logs directory is writable
if [ -d "/app/logs" ]; then
    chown -R pudel:pudel /app/logs 2>/dev/null || true
fi

echo "[2/4] Permissions fixed"

git config --global --add safe.directory /app/src

# Check if source directory exists for auto-update mode
if [ -d "/app/src" ]; then
    cd /app/src

    # Clone or update repository
    if [ "$AUTO_UPDATE" = "true" ] || [ ! -f "/app/src/pom.xml" ]; then
        echo "[3/4] Checking for updates from Git..."

        if [ -d "/app/src/.git" ]; then
            echo "Updating existing repository..."
            git fetch origin ${GIT_BRANCH:-main}
            git reset --hard origin/${GIT_BRANCH:-main}
        else
            echo "Cloning repository..."
            rm -rf /app/src/*
            git clone --depth 1 --branch ${GIT_BRANCH:-main} ${GIT_REPO} .
        fi

        echo "Building application..."
        mvn clean package -DskipTests -pl pudel-core -am

        echo "Copying built JAR..."
        cp pudel-core/target/*.jar /app/app.jar
    else
        echo "[3/4] Skipping update (AUTO_UPDATE=false)"
    fi

    # Copy plugins if they exist in the build
    if [ -d "/app/src/plugins" ]; then
        cp /app/src/plugins/*.jar /app/plugins/ 2>/dev/null || true
    fi
else
    echo "[3/4] Using pre-built JAR"
fi

echo "[4/4] Starting Pudel Bot..."

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

