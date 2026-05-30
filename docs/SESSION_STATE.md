# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-05-30 — Observability panel en dashboard + Fase 3 marketplace_

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
agents/CortexAgent.kts           — agente CORTEX (InferenceEngine + MCPClient + CommandBridge)
agents/CortexWebUiAgent.kts      — dashboard web (Grizzly, SSE, historial, resize, glow)
agents/GreetingAgent.kts         — análisis de swarm (@Export, compatible con worker)
agents/AgentCreatorAgent.kts     — wizard v2 (@Export, LLM code gen, skill.json auto)
agents/RssFeedAgent.kts          — fetch RSS + resumen LLM opcional
agents/HeartbeatAgent.kts        — monitor proactivo de condiciones
agents/TelegramBridgeAgent.kts   — bridge Telegram ↔ CortexAgent (requiere token)
agents/*.skill.json              — metadatos portables por agente
libs/octopus-6.5.3.jar           — runtime con CommandBridgeProvider incluido
libs/octopus.jar                 — symlink al anterior
libs/koupper-cli.jar             — CLI con start/worker/schedule/monitor/doctor
libs/koupper-monitor.jar         — TUI Lanterna
```

---

## Features completados (2026-05-29 — sesión actual)

### Fase 1 — Agentes útiles (2026-05-30)
- **`skill.json`** — formato de metadatos portable para todos los agentes: name, version, description, role, providers, triggers, tags, env vars. Base para marketplace futuro.
- **`AgentCreatorAgent` v2** — usa `InferenceEngine` SP para generar código real (no TODOs). Prompt estructurado con convenciones Koupper + SPs disponibles. Streaming al log con `TokenListener`. Genera `skill.json` automáticamente para cada agente creado.
- **`RssFeedAgent`** — fetch RSS real (Hacker News, The Verge por default; configurable en `rss-feeds.json`). Resume con LLM si disponible. Probado en producción.
- **`HeartbeatAgent`** — autonomía proactiva. Lee `~/.koupper/heartbeat.md`, evalúa condiciones (`file_exists`, `queue_empty`, `queue_has_failed`, `time_after`, `always`), despacha agentes con cooldown. Probado: disparó 2 agentes automáticamente.

### koupper worker --status
- Muestra pending/processing/failed/dead por queue y sale inmediatamente sin levantar el daemon
- Indicadores visuales: `▶` activo · `⚠` failed · `☠` dead-letter
- Disponible en `develop` y `igly/cortex`

### koupper doctor
- Diagnostica el runtime completo en un comando: env vars, JARs, puertos, queues, agentes, schedules
- Output ✓/⚠/✗ con colores ANSI y resumen de errores/warnings al final
- Disponible en `develop` y `igly/cortex`

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

### Completado
- ~~`koupper doctor`~~ ✓
- ~~`koupper worker --status`~~ ✓
- ~~Fase 1: skill.json, AgentCreatorAgent v2, RssFeedAgent, HeartbeatAgent~~ ✓

### Observability panel (2026-05-30)
- Barra permanente encima de los paneles en el dashboard con: **Jobs/min**, **Success rate** (color-coded: verde ≥95%, amarillo ≥80%, rojo <80% + contador done/failed), **P50**, **P95** (parseados de `[DONE] Xms` en los logs)
- **Sparkline** — 12 barras de 5min mostrando actividad de la última hora (verde=done, rojo=failed)
- `computeObservability()` lee `jobHistory` + archivos de log; se incluye en cada SSE snapshot
- Probado: `totalLastHour=7`, `successRate=100%`, `p50Ms=3103ms` con jobs reales

### Fase 3 — Agent Marketplace (2026-05-30)
- **`koupper agent list`** — tabla de agentes instalados con nombre, rol, versión, persistent (lee `*.skill.json`)
- **`koupper agent info <name>`** — detalles completos: descripción, triggers, providers, env vars, docs URL
- **`koupper agent install <url|github:user/repo/Agent.kts>`** — descarga `.kts` + `.skill.json` desde URL directa o shorthand GitHub
- **`koupper agent remove <name>`** — elimina `.kts`, `.skill.json` y `draft_*.json`
- Commiteado en `igly/cortex` y `develop` del CLI

### Fase 2 — TelegramChannelProvider (2026-05-30)
- **`TelegramChannelProvider`** SP — long-polling Bot API, sin webhooks, sin SDK externo. `startPolling()` con whitelist de chat IDs, `sendMessage()` / `sendLongMessage()` (chunks para límite de 4096 chars). Mergeado a `koupper/develop`.
- **`TelegramBridgeAgent.kts`** — conecta Telegram ↔ CortexAgent. Lee config de `~/.koupper/telegram.json` o env vars. Detecta si CORTEX está corriendo, manda "typing...", monitorea `cortex-session.log`, limpia ANSI, divide respuestas largas. Instalado en `~/.koupper/agents/`.
- **Docs** — `koupper-docs/agents/telegram-bridge.md` con setup paso a paso. Mergeado a `koupper-docs/main`.
- **octopus.jar** rebuildeado e instalado con el nuevo SP (124MB).

### Documentación pública (2026-05-30)
- `koupper-docs` actualizado con sección **Agent Runtime**: `worker`, `schedule`, `doctor`
- Nueva sección **Agents**: overview + docs individuales de GreetingAgent, AgentCreatorAgent, RssFeedAgent, HeartbeatAgent
- Sidebar de VitePress actualizado, mergeado a `main` de `koupper-docs`

### Próximo
1. ~~**Observability**~~ — Done ✓ (jobs/min, success rate, P50/P95, sparkline)
2. ~~**Fase 2: TelegramChannelProvider**~~ — Done ✓
3. ~~**Fase 3: Marketplace**~~ — Done ✓ (list/info/install/remove)
4. **Fase 4: Memory + VectorDb** — `VectorDbProvider` real, `memory.md` human-readable

### Para probar Telegram
```bash
# 1. Crear bot en @BotFather → copiar token
# 2. Configurar
cat > ~/.koupper/telegram.json << 'EOF'
{"token": "TU_TOKEN", "allowedChatIds": [TU_CHAT_ID]}
EOF
# 3. Arrancar stack + bridge
koupper start &
koupper run ~/.koupper/agents/TelegramBridgeAgent.kts
```

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
