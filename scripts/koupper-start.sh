#!/bin/bash
# Koupper daemon startup — starts the worker process only.
# To start CORTEX, run cortex-start.sh from the CORTEX repo after the worker is up.
set -e

[ -f "$HOME/.profile" ] && source "$HOME/.profile"

LOGS=~/.koupper/jobs/logs
mkdir -p "$LOGS/default" ~/.koupper/run

echo "[koupper-start] Starting worker daemon..."
nohup koupper worker >> "$LOGS/default/worker.log" 2>&1 &
echo $! > ~/.koupper/run/worker.pid

echo "[koupper-start] Worker started (PID $(cat ~/.koupper/run/worker.pid))"
echo "[koupper-start] MCP server: :18082 (via octopus)"
