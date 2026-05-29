# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-05-29 — Sesión de refactoring y nuevo SP_

---

## Current Objective

Construir IGLY CORTEX — un runtime de agentes AI local usando Koupper como framework. El producto vive en `igly/cortex`. El framework open-source en `develop`.

---

## Estado de ramas

| Repo | Branch | Estado |
|---|---|---|
| `koupper` | `develop` | Framework con CommandBridgeProvider, pusheado |
| `koupper` | `igly/cortex` | Producto CORTEX completo, pusheado |
| `koupper-cli` | `develop` | CLI sin monitor/start (open-source) |
| `koupper-cli` | `igly/cortex` | CLI con monitor/start/schedule |
| `workspace` | `develop` | Agentes refactorizados, docs actualizados |

---

## Stack funcionando HOY

```bash
# Variables ya en ~/.bashrc
export KOUPPER_LLM_MODEL_PATH=/home/tdn-dell/develop/llama.cpp/modelo_prueba.gguf
export KOUPPER_LLM_EXECUTABLE=/home/tdn-dell/develop/llama.cpp/build/bin/llama-server

# Arrancar todo
koupper start
```

Levanta: Worker daemon · Web UI :18083 · Monitor TUI · MCP server :18082 · CORTEX

---

## Archivos instalados en ~/.koupper/

```
agents/CortexAgent.kts        — agente CORTEX (InferenceEngine + MCPClient + CommandBridge)
agents/CortexWebUiAgent.kts   — dashboard web (Grizzly, SSE, historial, resize, glow)
agents/GreetingAgent.kts      — análisis de swarm (@Export, compatible con worker)
agents/AgentCreatorAgent.kts  — wizard interactivo (@Export, CommandBridgeProvider)
libs/octopus-6.5.3.jar        — runtime con CommandBridgeProvider incluido
libs/octopus.jar              — symlink al anterior
libs/koupper-cli.jar          — CLI con start/worker/schedule/monitor
libs/koupper-monitor.jar      — TUI Lanterna
```

---

## Features completados (2026-05-29 — sesión actual)

### Nuevo Service Provider: CommandBridgeProvider
- Interfaz: `watch(dir)` → `drain()` → `nextCommand(timeoutMs)` → `close()`
- Encapsula el patrón WatchService + `.response` files usado en 3 agentes
- Registrado en `ServiceProviderManager` y `providers-catalog.json`
- Verificado end-to-end: wizard recibe respuestas, procesa state machine, genera agente

### Refactoring de agentes
- `CortexAgent.kts` — usa `HtppClient` SP en vez de `java.net.http.*` (GET/POST al MCP local)
- `CortexAgent.kts` — usa `CommandBridgeProvider` en vez de WatchService manual
- `AgentCreatorAgent.kts` — usa `CommandBridgeProvider`, migrado a `@Export val setup`
- `GreetingAgent.kts` — migrado a `@Export val setup` (sesión anterior)

### Web UI dashboard
- Historial de jobs (DONE/DEAD) persistido en `.history.jsonl`
- Métricas: Pending / Processing / Done / Failed
- Paneles redimensionables (Jobs / Log / Sidebar)
- Available Agents con puntos de color pulsantes
- CORTEX chat con glow degradado animado
- Filtros: All / Active / Done / Failed

### Worker
- Detecta errores de script cuando exit code es 0 (`logContainsScriptError`)
- Jobs con errores van a `.failed/` correctamente

---

## Features completados (2026-05-28)

### develop (framework open-source)
- `MCPClientProvider` — HTTP + stdio para servidores MCP externos
- `LocalMCPServerProvider` reescrito a JSON-RPC 2.0
- `InferenceConfig` — params configurables para LlamaServerSidecar
- `EnvironmentProfiler` — degradación graceful
- `AgentOrchestrator` — parseo real de tool calls
- `DefaultToolExecutor` — operaciones reales de archivo
- `LlamaServerSidecar` — fix null content node en SSE
- `koupper worker` — daemon con timeout, dead-letter, `--enable-scheduling`
- `koupper schedule` — add/list/remove/enable/disable (cron/rate/once)
- `CronMatcher` — evaluador cron 5 campos

### igly/cortex
- `CortexMcpServer` — 9 tools incluyendo `swarm_run`
- `CortexAgent.kts` — InferenceEngine + TokenListener + MCP + streaming
- Web UI 3 columnas con SSE
- `koupper start` — un solo comando para todo

---

## Bugs conocidos

1. **Merge develop→igly/cortex en CLI** elimina `MonitorCommand.kt` — usar cherry-pick
2. **Octopus cachea scripts compilados** — matar daemon al cambiar un .kts
3. **CLI crash con CoroutinesInternalError** — usar `nohup` para que octopus sobreviva

---

## Próximos features (roadmap)

### Alta prioridad
1. **`koupper doctor`** — diagnóstico: octopus, llama-server, modelo, puertos, schedules
2. **Observability** — métricas jobs/min, success rate, latencia P95 en web UI
3. **`koupper worker --status`** — muestra queues sin arrancar daemon

### Media prioridad
4. **AgentCreatorAgent con LLM** — usar CORTEX para generar el código real del agente (hoy solo genera scaffold con TODOs)
5. **Agent marketplace** — `koupper agent list/install/publish`

### igly/cortex
6. Sync limpio develop → igly/cortex con cherry-pick
7. `CortexMemoryStore` → `VectorDbProvider` real
8. CORTEX multimodal con Playwright MCP

---

## Puertos del sistema

| Puerto | Servicio |
|---|---|
| 8081 | llama-server (LLM) |
| 9998 | Octopus daemon socket |
| 18082 | MCP server JSON-RPC 2.0 |
| 18083 | Web UI Grizzly HTTP |

---

## Cómo retomar

```bash
cd ~/develop/koupper\ workspace

# Ver estado
cd koupper     && git log --oneline -5
cd ../koupper-cli && git log --oneline -5

# Arrancar stack (variables ya en ~/.bashrc)
koupper start

# Logs en vivo
tail -f ~/.koupper/jobs/logs/cortex/cortex-session.log
tail -f ~/.koupper/logs/worker.log
```
