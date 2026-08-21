#!/usr/bin/env bash

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PATH="$HOME/.local/bin:$PATH"

REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
PID_FILE="$DIR/.redis.pid"

echo "=================================================="
echo " Stopping Redis Server ($REDIS_HOST:$REDIS_PORT)"
echo "=================================================="

# Try redis-cli shutdown first
if command -v redis-cli >/dev/null 2>&1; then
    echo "Sending SHUTDOWN command via redis-cli..."
    redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" shutdown nosave 2>/dev/null
fi

# If PID file exists, kill if still alive
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null 2>&1; then
        echo "Stopping process PID: $PID"
        kill -9 "$PID" 2>/dev/null
    fi
    rm -f "$PID_FILE"
fi

# Check Docker container if running
if command -v docker >/dev/null 2>&1; then
    if docker ps -q -f name=redis-kryo-demo | grep -q .; then
        echo "Stopping Docker container 'redis-kryo-demo'..."
        docker stop redis-kryo-demo >/dev/null 2>&1
    fi
fi

echo "✓ Redis has been stopped."
