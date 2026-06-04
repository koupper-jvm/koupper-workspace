#!/bin/bash
# Koupper system startup — Worker + CORTEX + TelegramBridge + WebUI
# Note: MCP server (:18082) is provided by octopus.jar (auto-started by koupper shim)
set -e

[ -f "$HOME/.profile" ] && source "$HOME/.profile"

LOGS=~/.koupper/jobs/logs
mkdir -p "$LOGS/cortex" "$LOGS/default" ~/.koupper/run

echo "[koupper-start] Starting worker daemon..."
nohup koupper worker >> "$LOGS/default/worker.log" 2>&1 &
echo $! > ~/.koupper/run/worker.pid
sleep 2

echo "[koupper-start] Starting CORTEX..."
nohup koupper run ~/.koupper/agents/CortexAgent.kts >> "$LOGS/cortex/cortex-session.log" 2>&1 &
echo $! > ~/.koupper/run/cortex.pid
sleep 3

echo "[koupper-start] Starting TelegramBridge..."
nohup koupper run ~/.koupper/agents/TelegramBridgeAgent.kts >> "$LOGS/default/telegram-bridge.log" 2>&1 &
echo $! > ~/.koupper/run/telegram.pid
sleep 1

echo "[koupper-start] Starting WebUI..."
nohup koupper run ~/.koupper/agents/CortexWebUiAgent.kts >> "$LOGS/default/webui.log" 2>&1 &
echo $! > ~/.koupper/run/webui.pid

echo "[koupper-start] Starting FileWatcherAgent..."
nohup koupper run ~/.koupper/agents/FileWatcherAgent.kts >> "$LOGS/default/filewatcher.log" 2>&1 &
echo $! > ~/.koupper/run/filewatcher.pid

echo "[koupper-start] Starting HeartbeatAgent loop (60 s)..."
(while true; do
    koupper run ~/.koupper/agents/HeartbeatAgent.kts >> "$LOGS/default/heartbeat.log" 2>&1
    sleep 60
done) &
echo $! > ~/.koupper/run/heartbeat.pid

echo "[koupper-start] All services started."
echo "[koupper-start]   Dashboard:  http://localhost:18083"
echo "[koupper-start]   MCP server: :18082 (via octopus)"
echo "[koupper-start]   Worker:     active"
