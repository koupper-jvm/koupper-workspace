# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-01 — repo split + 5 agentes + dashboard run button_

---

## Current Objective

Separación limpia entre Koupper (open-source) e IGLY CORTEX (privado).
CORTEX vive en `igly/cortex` en todos los repos. Framework en `develop`.

---

## Estructura de ramas (definitiva)

| Repo | Branch público | Branch privado CORTEX |
|---|---|---|
| `koupper` | `develop` | — |
| `koupper-cli` | `develop` | `igly/cortex` |
| `koupper-workspace` | `develop` | `igly/cortex` |

**Regla:** todo lo de CORTEX va a `igly/cortex`. El framework a `develop`.

---

## Estado de ramas hoy

| Repo | Branch | Último commit |
|---|---|---|
| `koupper` | `develop` | `aa0898b` — catalog fix WebReader/PDFReader |
| `koupper-cli` | `igly/cortex` | `d1ac229` — test fix + sync-from-develop script |
| `koupper-cli` | `develop` | `b2a1b8c` — add koupper agent marketplace commands |
| `koupper-workspace` | `igly/cortex` | `ad548f7` — 5 agentes + dashboard run button |
| `koupper-workspace` | `develop` | `87abf01` — add skills reference to CLAUDE.md |

---

## Stack funcionando HOY

```bash
# Variables en ~/.bashrc
export KOUPPER_LLM_MODEL_PATH=/home/tdn-dell/develop/llama.cpp/models/fast/qwen2.5-1.5b-instruct-q4_k_m.gguf
export KOUPPER_LLM_EXECUTABLE=/home/tdn-dell/develop/llama.cpp/build/bin/llama-server

# Arrancar todo
source ~/.bashrc
koupper start
```

Levanta: Worker daemon · Web UI :18083 · Monitor TUI · MCP server :18082

**Modelo activo:** Qwen2.5-1.5B-Instruct-Q4_K_M (rápido, ~1GB)

---

## Agentes instalados en ~/.koupper/agents/

```
CortexAgent.kts           — LLM orchestrator multi-tool + memory + Playwright MCP
CortexWebUiAgent.kts      — dashboard web (Grizzly, SSE, historial, run button)
GreetingAgent.kts         — análisis de swarm + saludo
AgentCreatorAgent.kts     — wizard v2 LLM code gen
RssFeedAgent.kts          — fetch RSS + resumen LLM
HeartbeatAgent.kts        — monitor proactivo de condiciones
TelegramBridgeAgent.kts   — bridge Telegram ↔ CortexAgent
SysMonitorAgent.kts       — CPU, RAM, disco, top procesos, puertos ← NUEVO
FileOrganizerAgent.kts    — clasifica ~/Downloads por tipo de archivo ← NUEVO
PortScannerAgent.kts      — escanea puertos, alerta servicios inesperados ← NUEVO
DiaryAgent.kts            — resume logs del día con LLM → memory/diary-<date>.md ← NUEVO
CodeReviewAgent.kts       — code review .kt/.kts con LLM (CODE_REVIEW_FILE env) ← NUEVO
```

---

## Dashboard — features activos

- **Sidebar con botón ▶** por agente → `POST /api/run-agent` → encola en worker
- **Agent script viewer** → click en agente muestra el .kts con syntax coloring
- **Chat SSE** → respuestas del LLM en tiempo real como burbujas
- **Job result** → columna Result en tabla (`.done/<id>.result.json`)
- **Keyboard nav** → ArrowUp/Down en tabla de jobs
- **Observability panel** → jobs/min, success rate, P50/P95, sparkline

---

## Puertos del sistema

| Puerto | Servicio |
|---|---|
| 8081 | llama-server (LLM) |
| 9998 | Octopus daemon socket |
| 18082 | MCP server JSON-RPC 2.0 |
| 18083 | Web UI Grizzly HTTP |

---

## Fixes aplicados hoy

### koupper-cli `igly/cortex`
- `ModuleCommandAddScriptsTest` — path con backslash en Linux creaba nombre literal, corregido a `/`
- `scripts/sync-from-develop.sh` — merge seguro de develop→igly/cortex sin perder StartCommand/MonitorCommand

### koupper-workspace `igly/cortex`
- 5 agentes nuevos verificados y pasando desde el dashboard
- `CortexWebUiAgent` — `POST /api/run-agent` + botón ▶ en sidebar

### Separación de repos
- `koupper-workspace/develop` reseteado a `87abf01` (pre-CORTEX)
- `koupper-workspace/igly/cortex` creado con todo el historial CORTEX (50+ commits)
- Force-push de develop + push de igly/cortex a origin ✓

---

## Bugs conocidos

1. **Merge develop→igly/cortex en CLI** elimina StartCommand/MonitorCommand — usar `./scripts/sync-from-develop.sh`
2. **Octopus cachea scripts compilados** — matar daemon al cambiar un .kts (`pkill -f octopus.jar`)
3. **`bridge.drain()` al inicio de CortexAgent** borra `.response` files — enviar comandos solo después del greeting

---

## Próximo

- Nuevos features de CORTEX → branch `igly/cortex` en workspace
- Nuevos features del framework → branch `feature/*` desde `develop` en koupper/koupper-cli
- Para sincronizar CLI con framework: `./scripts/sync-from-develop.sh` desde `igly/cortex`

---

## Cómo retomar

```bash
cd ~/develop/koupper\ workspace

# Ver estado
git log --oneline igly/cortex -5
cd koupper-cli && git log --oneline igly/cortex -5

# Arrancar stack
source ~/.bashrc
koupper start

# Logs en vivo
tail -f ~/.koupper/logs/worker.log
tail -f ~/.koupper/jobs/logs/cortex/cortex-session.log

# Ejecutar agente desde CLI
koupper run ~/.koupper/agents/SysMonitorAgent.kts

# Verificar memoria acumulada
cat ~/.koupper/memory/memory.md
cat ~/.koupper/memory/diary-$(date +%Y-%m-%d).md
```
