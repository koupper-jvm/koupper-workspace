# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-05-29 — Sesión de fixes, UI y estabilización_

---

## Current Objective

Construir IGLY CORTEX — un runtime de agentes AI local usando Koupper como framework. El producto vive en `igly/cortex`. El framework open-source en `develop`.

---

## Estado de ramas

| Repo | Branch | Estado |
|---|---|---|
| `koupper` | `develop` | Framework limpio, pusheado |
| `koupper` | `igly/cortex` | Producto CORTEX completo, pusheado |
| `koupper-cli` | `develop` | CLI sin monitor/start (open-source) |
| `koupper-cli` | `igly/cortex` | CLI con monitor/start/schedule |
| `workspace` | `develop` | Agentes y docs actualizados |

---

## Stack funcionando HOY

```bash
# Setup inicial (una vez) — ya está en ~/.bashrc
export KOUPPER_LLM_MODEL_PATH=/home/tdn-dell/develop/llama.cpp/modelo_prueba.gguf
export KOUPPER_LLM_EXECUTABLE=/home/tdn-dell/develop/llama.cpp/build/bin/llama-server

# Arrancar todo
koupper start
```

Levanta:
- **Worker** daemon (`~/.koupper/logs/worker.log`)
- **Web UI** en http://localhost:18083 (`~/.koupper/logs/webui.log`)
- **Monitor TUI** en terminal
- **MCP server** en http://localhost:18082 (9 tools)
- **CORTEX** via `CortexAgent.kts` (InferenceEngine SP, SSE streaming, MCP tools)

---

## Archivos instalados en ~/.koupper/

```
agents/CortexAgent.kts        — agente CORTEX (InferenceEngine + MCP + streaming)
agents/CortexWebUiAgent.kts   — dashboard web (Grizzly, SSE, chat, schedules, history)
agents/GreetingAgent.kts      — análisis de swarm al arrancar (@Export, compatible con worker)
agents/AgentCreatorAgent.kts  — wizard interactivo para crear nuevos agentes
libs/octopus.jar              — runtime con todos los SPs incluyendo MCPClientProvider
libs/koupper-cli.jar          — CLI con start/worker/schedule/monitor
libs/koupper-monitor.jar      — TUI Lanterna
```

---

## Features completados (2026-05-29)

### Web UI dashboard
- Historial de jobs — WatchService detecta DONE/DEAD, escribe a `.history.jsonl` (max 500)
- Historial persiste entre reinicios vía `loadHistory()` al arrancar
- Métricas: Pending / Processing / Done / Failed
- Jobs DEAD visibles en tabla y guardados en historial
- Paneles redimensionables (Jobs / Log / Sidebar) con drag handles
- Available Agents con puntos de color pulsantes por agente
- CORTEX chat con glow degradado animado (purple→blue→cyan)
- Filtros: All / Active / Done / Failed
- `/api/history` endpoint

### Worker
- Detecta errores de script cuando exit code es 0 (`logContainsScriptError`)
  - `No function annotated with @Export was found`
  - `Script error:`, `<ERROR::>`, `error: unresolved reference`, `Exception in thread "main"`
- Jobs con errores de script van a `.failed/` en vez de marcarse como DONE

### Agentes
- `GreetingAgent.kts` — migrado a `@Export val setup` para compatibilidad con worker
- `CortexWebUiAgent.kts` — en workspace repo `examples/agents/`

---

## Features completados (2026-05-28)

### develop (framework open-source)
- `MCPClientProvider` — HTTP + stdio para servidores MCP externos
- `LocalMCPServerProvider` reescrito a JSON-RPC 2.0 (spec 2024-11-05)
- `InferenceConfig` — params configurables para LlamaServerSidecar
- `EnvironmentProfiler` — degradación graceful (no más kill switch)
- `AgentOrchestrator` — parseo real de tool calls
- `DefaultToolExecutor` — operaciones reales de archivo
- `optimized` JAR filter — regex preciso, sin leakage externo
- `GrizzlyRuntimeRouterProvider` — content-type HTML correcto
- `LlamaServerSidecar` — fix null content node en SSE
- `koupper worker` — daemon con timeout, dead-letter queue, `--enable-scheduling`
- `koupper schedule` — add/list/remove/enable/disable (cron/rate/once)
- `CronMatcher` — evaluador cron 5 campos, zero deps

### igly/cortex (producto privado)
- `CortexMcpServer` — 9 tools incluyendo `swarm_run`
- Monitor refactored — display layer puro, lanza `CortexAgent.kts` externamente
- `CortexAgent.kts` — InferenceEngine SP + TokenListener + MCP HTTP + external MCPs
- `CortexMemoryStore` — memoria persistente TF-IDF + embeddings
- Pipeline visualization TUI
- `swarm_run` — conecta CORTEX a `SwarmCoordinator.runSequence()`
- Web UI 3 columnas: jobs + log + sidebar (chat CORTEX, agents, schedules)
- WatchService registra subdirectorios creados después del arranque
- `koupper start` — un solo comando para todo
- `QUICKSTART.md` en workspace

---

## Bugs conocidos

1. **Merge develop→igly/cortex en CLI elimina `MonitorCommand.kt`** — usar cherry-pick en vez de merge completo
2. **Octopus cachea scripts compilados** — matar daemon para forzar recompilación al cambiar un .kts
3. **CLI crash con CoroutinesInternalError** — ocurre en algunos reinicios; usar `nohup` para que octopus sobreviva al crash del CLI

---

## Próximos features (roadmap)

### Alta prioridad
1. **`koupper doctor`** — diagnóstico: octopus, llama-server, modelo, puertos, schedules activos
2. **Observability** — métricas jobs/min, success rate, latencia P95 en web UI
3. **`koupper worker --status`** — muestra queues sin arrancar daemon

### Media prioridad
4. **Agent marketplace** — `koupper agent list/install/publish`
5. **AgentCreatorAgent con LLM** — que el wizard use CORTEX para generar el código real del agente (hoy solo genera scaffold con TODOs)

### igly/cortex
6. Sync limpio de develop → igly/cortex usando cherry-pick
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
