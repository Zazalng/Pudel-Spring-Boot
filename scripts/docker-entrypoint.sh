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

# Drop privileges and run as pudel user
exec gosu pudel java ${JAVA_OPTS} -jar app.jar \
    --spring.datasource.url=jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB} \
    --spring.datasource.username=${POSTGRES_USER} \
    --spring.datasource.password=${POSTGRES_PASS} \
    --server.port=${SERVER_PORT}

