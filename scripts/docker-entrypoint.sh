#!/bin/bash

# ===========================================
# Pudel Discord Bot - Docker Entrypoint
# Handles permissions fix, auto-update, and application startup
# ===========================================

set -e

echo "========================================="
echo "   Pudel Discord Bot - Starting...      "
echo "========================================="

# Ensure Maven and Git are on PATH
export PATH="/usr/local/bin:/opt/apache-maven-*/bin:$PATH"

# ===========================================
# [1/4] Fix permissions for bind-mounted directories
# ===========================================
echo "[1/4] Fixing directory permissions..."

for dir in /app/plugins /app/data /app/logs; do
    if [ -d "$dir" ]; then
        chown -R pudel:pudel "$dir" 2>/dev/null || true
        chmod -R 755 "$dir" 2>/dev/null || true
    fi
done

echo "[2/4] Permissions fixed"

# ===========================================
# [3/4] Auto-update from Git (if enabled)
# ===========================================
# How auto-update works:
#
#   1. The Dockerfile builds app.jar in the multi-stage build (Stage 1)
#      and copies it into the runtime image. This is the "initial" JAR.
#
#   2. When AUTO_UPDATE=true, at EVERY container start/restart, this
#      entrypoint script:
#        a) Clones or pulls the latest code from GIT_REPO/GIT_BRANCH
#           into the /app/src volume (persisted across restarts)
#        b) Runs `mvn clean package` to rebuild from source
#        c) Copies the fresh JAR to /app/app.jar, replacing the original
#
#   3. The bot then starts from the updated JAR.
#
# To trigger an update after pushing to GitHub:
#   - Simply restart the container: `docker compose restart pudel`
#   - Or use the updater service: `docker compose run --rm pudel-updater`
#     then `docker compose restart pudel`
#   - Or set up a GitHub webhook / cron to auto-restart
#
# When AUTO_UPDATE=false:
#   - The pre-built JAR from the Docker image is used as-is
#   - To update, rebuild the image: `docker compose build --no-cache pudel`
#
# ===========================================

git config --global --add safe.directory /app/src

if [ "${AUTO_UPDATE}" = "true" ]; then
    echo "[3/4] Auto-update enabled — checking for updates from Git..."

    # Ensure /app/src exists and is writable
    mkdir -p /app/src
    chown -R pudel:pudel /app/src 2>/dev/null || true
    cd /app/src

    GIT_BRANCH="${GIT_BRANCH:-main}"
    GIT_REPO="${GIT_REPO:-https://github.com/World-Standard-Group/Pudel-Spring-Boot.git}"

    if [ -d "/app/src/.git" ]; then
        # Existing clone — pull latest
        echo "Updating existing repository (branch: ${GIT_BRANCH})..."
        git fetch origin "${GIT_BRANCH}" 2>&1 || { echo "WARNING: git fetch failed, using cached source"; }
        git reset --hard "origin/${GIT_BRANCH}" 2>&1 || true
    else
        # First run — clone fresh
        echo "Cloning repository (branch: ${GIT_BRANCH})..."
        rm -rf /app/src/*
        git clone --depth 1 --branch "${GIT_BRANCH}" "${GIT_REPO}" . 2>&1
    fi

    # Verify Maven is available
    if ! command -v mvn &> /dev/null; then
        echo "ERROR: Maven not found in PATH. Falling back to pre-built JAR."
        echo "PATH=$PATH"
        ls -la /usr/local/bin/mvn /opt/apache-maven-*/bin/mvn 2>/dev/null || true
    elif [ -f "/app/src/pom.xml" ]; then
        echo "Building application with Maven..."
        mvn clean package -DskipTests -pl pudel-core -am 2>&1

        if [ -f pudel-core/target/*.jar ]; then
            echo "Copying freshly built JAR..."
            cp pudel-core/target/*.jar /app/app.jar
            echo "Build successful — using updated JAR"
        else
            echo "WARNING: Build produced no JAR, using previous app.jar"
        fi
    else
        echo "WARNING: No pom.xml found in /app/src, using pre-built JAR"
    fi

    # Copy plugins if they exist in the repo
    if [ -d "/app/src/plugins" ]; then
        cp /app/src/plugins/*.jar /app/plugins/ 2>/dev/null || true
    fi
else
    echo "[3/4] Auto-update disabled (AUTO_UPDATE=false) — using pre-built JAR"
fi

# ===========================================
# [4/4] Start the application
# ===========================================
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

