#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PATH="$HOME/.local/bin:$PATH"

REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
PID_FILE="$DIR/.redis.pid"
LOG_FILE="$DIR/.redis.log"

echo "=================================================="
echo " Starting Redis Server for Kryo + Lettuce Demo"
echo " Host: $REDIS_HOST | Port: $REDIS_PORT"
echo "=================================================="

# Check if Redis is already running and reachable
if command -v redis-cli >/dev/null 2>&1 && redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping 2>/dev/null | grep -q "PONG"; then
    echo "✓ Redis is ALREADY RUNNING and reachable at $REDIS_HOST:$REDIS_PORT"
    exit 0
fi

# Check for local redis-server binary
if command -v redis-server >/dev/null 2>&1; then
    echo "Starting local redis-server in background..."
    redis-server --port "$REDIS_PORT" --daemonize yes --logfile "$LOG_FILE" --pidfile "$PID_FILE"
elif command -v docker >/dev/null 2>&1; then
    echo "redis-server binary not found. Launching via Docker..."
    docker run -d --name redis-kryo-demo -p "$REDIS_PORT:6379" redis:7-alpine || docker start redis-kryo-demo
else
    echo "Error: Neither 'redis-server' nor 'docker' was found in PATH."
    echo "Please ensure Redis is installed or start it manually."
    exit 1
fi

# Wait for Redis to be ready
echo "Waiting for Redis to become available..."
MAX_ATTEMPTS=15
ATTEMPT=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    if command -v redis-cli >/dev/null 2>&1 && redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping 2>/dev/null | grep -q "PONG"; then
        echo "✓ Redis started successfully and is responding with PONG!"
        echo "✓ Connection string: redis://$REDIS_HOST:$REDIS_PORT"
        exit 0
    fi
    sleep 0.5
    ATTEMPT=$((ATTEMPT + 1))
done

echo "⚠ Warning: Redis did not respond within timeout. Check log at $LOG_FILE"
exit 1
