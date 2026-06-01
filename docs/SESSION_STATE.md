# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-05-31 — Dashboard agent viewer + multi-tool fix + chat UX_

---

## Current Objective

Construir IGLY CORTEX — un runtime de agentes AI local usando Koupper como framework. El producto vive en `igly/cortex`. El framework open-source en `develop`.

---

## Estado de ramas

| Repo | Branch | Estado |
|---|---|---|
| `koupper` | `develop` | resultFn en orchestrator (`55c3212`), pusheado |
| `koupper-cli` | `igly/cortex` | Worker escribe result a `.done/` (`5f4d58b`), pusheado |
| `workspace` | `develop` | Multi-CORTEX_TOOL + dashboard chat UX (`5fe501c`), pusheado |

---

## Stack funcionando HOY

```bash
# Variables en ~/.bashrc (actualizadas 2026-05-30)
export KOUPPER_LLM_MODEL_PATH=/home/tdn-dell/develop/llama.cpp/models/qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf
export KOUPPER_LLM_EXECUTABLE=/home/tdn-dell/develop/llama.cpp/build/bin/llama-server

# Arrancar todo
source ~/.bashrc
koupper start
```

Levanta: Worker daemon · Web UI :18083 · Monitor TUI · MCP server :18082 · CORTEX

**Modelo activo:** Qwen2.5-7B-Instruct-Q4_K_M (split en 2 partes, ~4.4GB total)
- `~/.../models/qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf` (3.7GB)
- `~/.../models/qwen2.5-7b-instruct-q4_k_m-00002-of-00002.gguf` (658MB)
- llama-server carga ambas partes automáticamente con el primer archivo

---

## Archivos instalados en ~/.koupper/

```
agents/CortexAgent.kts           — CORTEX con MemoryProvider + A3 retry + multi-tool integrados
agents/CortexWebUiAgent.kts      — dashboard web (Grizzly, SSE, historial, resize, glow, agent viewer)
agents/GreetingAgent.kts         — análisis de swarm (@Export, compatible con worker)
agents/AgentCreatorAgent.kts     — wizard v2 (@Export, LLM code gen, skill.json auto)
agents/RssFeedAgent.kts          — fetch RSS + resumen LLM opcional
agents/HeartbeatAgent.kts        — monitor proactivo de condiciones
agents/TelegramBridgeAgent.kts   — bridge Telegram ↔ CortexAgent (requiere token)
agents/*.skill.json              — metadatos portables por agente
libs/octopus.jar                 — runtime Fase 4 (MemoryProvider + VectorDb persistente)
libs/octopus-6.5.3.jar           — misma versión (sincronizado)
libs/koupper-cli.jar             — CLI con start/worker/schedule/monitor/doctor
libs/koupper-monitor.jar         — TUI Lanterna
memory/memory-texts.json         — textos de memoria persistidos
memory/memory.md                 — vista human-readable de la memoria
vectordb/memory.json             — colección de vectores persistida en disco
```

---

## Features completados hoy (2026-05-31)

### Dashboard — Agent Script Viewer + Chat UX

**`workspace/examples/agents/CortexWebUiAgent.kts`:**

- **`GET /api/agent/{name}`** — endpoint que devuelve el contenido `.kts` de un agente
- **Sidebar clickable** — cada agente en la lista abre su script en el panel de log con syntax coloring básico: imports → verde, @Export → cyan, val/fun → amarillo, // → dim
- **`viewingAgent` flag** — el refresh cada 2s pausa mientras se ve un script; hacer click en el título (`AgentName.kts  ×`) cierra la vista y reanuda el log normal
- **`watchForResponse()`** — polling del log de cortex-session cada 2s para extraer líneas de respuesta LLM y mostrarlas como burbujas `msg-c`; detecta estabilización (sin cambio en 2 polls = 4s) en lugar de esperar la línea vacía final (~54s)
- **Auto-scroll inteligente** — solo hace scroll al fondo si el usuario ya estaba dentro de 60px del fondo
- **`selectJob(queue, id)`** — corregido (antes llamaba con 1 arg → `/api/logs/undefined`)

### CortexAgent — Multi-tool processing

**`workspace/examples/agents/CortexAgent.kts` — commit `5fe501c`:**

- `inferWithTools` ahora encuentra **todos** los `CORTEX_TOOL:` por respuesta (filter, no firstOrNull)
- Agrega el reply del assistant **una sola vez** para todo el batch de tool calls
- Ejecuta cada tool secuencialmente y agrega cada `TOOL_RESULT` al historial
- Re-infiere una sola vez con todos los resultados en contexto
- Extraído `executeTool()` helper para reducir anidamiento
- Caso de uso arreglado: `create_agent` + `run_agent` en la misma respuesta ya no pierde el segundo

### Catalog fix — WebReaderServiceProvider + PDFReaderServiceProvider

**`koupper/providers/src/main/resources/providers-catalog.json`:**

- Agregados `web-reader` y `pdf-reader` al catálogo (estaban registrados en `ServiceProviderManager` pero faltaban en el JSON)
- Tests `ProviderCatalogConsistencyTest` pasan ✓

---

## Features completados (2026-05-30 — sesión tarde)

### Job result en dashboard + nav teclado
- **`WorkerCommand.kt`** (`igly/cortex` `5f4d58b`) — extrae última línea del log y escribe `.done/<id>.result.json`
- **`JobResult.Ok`** (`koupper/develop` `55c3212`) — campo `resultFn`
- **`CortexWebUiAgent`** — columna Result en tabla (60 chars + hover); ArrowUp/Down nav

### Fase 4 — Memory + VectorDb
- `LocalVectorDbProvider` persistente, `HashEmbedder`, `MemoryProvider` + `LocalMemoryProvider`
- `MemoryServiceProvider` registrado en catálogo
- `CortexAgent.kts` con dispatch nativo de memory.*, A3 retry

### Fase 3 — Agent Marketplace
- `koupper agent list/info/install/remove`

### Fase 2 — TelegramChannelProvider
- `TelegramChannelProvider` SP + `TelegramBridgeAgent.kts`

### Fase 1 — Observability panel
- Jobs/min, Success rate, P50, P95, Sparkline

---

## Bugs conocidos

1. **Merge develop→igly/cortex en CLI** elimina `MonitorCommand.kt` — usar cherry-pick
2. **Octopus cachea scripts compilados** — matar daemon al cambiar un .kts
3. **CLI crash con CoroutinesInternalError** — usar `nohup` para que octopus sobreviva
4. **`bridge.drain()` al inicio de CortexAgent** borra `.response` files que lleguen durante el greeting — enviar comandos solo después de ver "Press Enter on this job" en el log

---

## Próximo

1. ~~Observability~~ ✓
2. ~~Fase 2: TelegramChannelProvider~~ ✓
3. ~~Fase 3: Marketplace~~ ✓
4. ~~Fase 4: Memory + VectorDb~~ ✓
5. ~~Job result visible en dashboard~~ ✓
6. ~~Navegación teclado web dashboard~~ ✓
7. ~~Agent script viewer en dashboard~~ ✓
8. ~~Chat response display + watchForResponse~~ ✓
9. ~~Multi CORTEX_TOOL processing en CortexAgent~~ ✓
10. ~~Catalog fix: WebReader + PDFReader~~ ✓
11. **Flechas TUI monitor** — fuente en `igly/cortex`, pendiente verificar
12. **Sync develop → igly/cortex** — cherry-pick Fase 4 + CortexAgent + result
13. **`CortexMemoryStore` en igly/cortex** → reemplazar por `MemoryProvider` real
14. **CORTEX multimodal** — Playwright MCP

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
cd ..          && git log --oneline -5

# Arrancar stack
source ~/.bashrc
koupper start

# Logs en vivo
tail -f ~/.koupper/jobs/logs/cortex/cortex-session.log
tail -f ~/.koupper/logs/worker.log

# Verificar memoria acumulada
cat ~/.koupper/memory/memory.md
```
