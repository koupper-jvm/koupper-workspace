# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-05-30 — Job result en dashboard + nav teclado + Qwen2.5-7B_

---

## Current Objective

Construir IGLY CORTEX — un runtime de agentes AI local usando Koupper como framework. El producto vive en `igly/cortex`. El framework open-source en `develop`.

---

## Estado de ramas

| Repo | Branch | Estado |
|---|---|---|
| `koupper` | `develop` | resultFn en orchestrator (`55c3212`), pusheado |
| `koupper-cli` | `igly/cortex` | Worker escribe result a `.done/` (`5f4d58b`), pusheado |
| `workspace` | `develop` | Dashboard result column + ArrowUp/Down nav (`3419a1e`), pusheado |

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
agents/CortexAgent.kts           — CORTEX con MemoryProvider + A3 retry integrados
agents/CortexWebUiAgent.kts      — dashboard web (Grizzly, SSE, historial, resize, glow)
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

## Features completados hoy (2026-05-30 — sesión tarde)

### Fase 4 — Memory + VectorDb

**`koupper/providers` — commit `9cae060` → mergeado `3d0a40a` en develop:**

- **`LocalVectorDbProvider` persistente** — escribe `~/.koupper/vectordb/<collection>.json` en cada upsert/delete; carga automática al iniciar. `VectorDbServiceProvider` pasa `dataDir` por defecto. Retrocompatible (null = in-memory).
- **`HashEmbedder`** — `HashEmbedder.embed(text)`: texto → vector 512-dims determinista. Tokeniza, genera bigrams, acumula en 2 buckets por hash, normaliza L2. Sin dependencias externas.
- **`MemoryProvider` interface + `LocalMemoryProvider`** — `remember(text)`, `recall(query, topK, minScore)`, `forget(id)`, `list()`. Internamente usa VectorDbProvider. Persiste textos en `memory-texts.json` y genera `memory.md` human-readable tras cada operación.
- **`MemoryServiceProvider`** — registrado en `ServiceProviderManager` y `providers-catalog.json`. Bind a `~/.koupper/memory/`.
- **Prueba directa** (`koupper run test-memory.kts`): `remember` × 3, `recall("qué es IGLY CORTEX")` → match correcto con score `0.423`. Archivos generados verificados.

**`workspace/examples/agents/CortexAgent.kts` — commit `f151115`:**

- Importa `MemoryProvider`; instancia lazy con fallback si no disponible
- Herramientas `memory.remember`, `memory.recall`, `memory.forget` expuestas al LLM con ejemplos concretos en el system prompt
- Dispatch nativo: `memory.*` → `LocalMemoryProvider` directamente (sin MCP hop)
- Inyección de contexto: antes de cada turno del usuario, hace `recall(userMsg, topK=3)` e inyecta bloque `[MEMORY CONTEXT]` si hay matches
- **A3 retry**: `looksLikeToolCall()` detecta intentos malformados (Kotlin syntax, JSON suelto, `func(...)`) y reintenta con ejemplo explícito antes de rendirse

### Modelo LLM — Qwen2.5-7B-Instruct

- Descargado `qwen2.5-7b-instruct-q4_k_m` (2 partes, ~4.4GB) de HuggingFace
- `.bashrc` actualizado para apuntar a la parte 1 (llama-server carga ambas)
- **Resultado de prueba con Qwen2.5:**
  - `memory.remember` → `CORTEX_TOOL: {"tool":"memory.remember","args":{"text":"..."}}` ✓ en primer intento
  - `memory.recall` → `CORTEX_TOOL: {"tool":"memory.recall","args":{"query":"IGLY CORTEX","topK":3}}` ✓
  - `memory.forget` → activó A3 retry y ejecutó correctamente
  - Responde en español automáticamente (modelo multilingual)
- Modelo anterior (`modelo_prueba.gguf`, 469MB) descartado — no tenía instruction tuning

### Job result en dashboard + nav teclado (2026-05-30 tarde)
- **`WorkerCommand.kt`** (`igly/cortex` `5f4d58b`) — extrae última línea del log (retorno `@Export`) y escribe `.done/<id>.result.json` antes de `ack()`. Todo `runCatching`, nunca bloquea el job.
- **`JobResult.Ok`** (`koupper/develop` `55c3212`) — campo `resultFn: ((Any?) -> Unit)? = null` para path `JobsOrchestrator` (backward compat SQS/Redis).
- **`CortexWebUiAgent`** (`workspace/develop` `3419a1e`) — `HistoryEntry.result: String? = null`; watcher lee y borra `.result.json`; columna **Result** en tabla (60 chars + hover); `Map<String,Any?>` fix.
- **ArrowUp/Down** — navegación de teclado en tabla de jobs del web dashboard.
- **TUI monitor** — flechas pendiente: fuente en `igly/cortex`, sin código fuente visible en workspace actual.

---

## Features completados (2026-05-30 — sesión mañana)

### Observability panel
- Barra permanente en dashboard: **Jobs/min**, **Success rate** (color-coded), **P50**, **P95**, **Sparkline** (12 barras × 5min)
- Probado: `totalLastHour=7`, `successRate=100%`, `p50Ms=3103ms`

### Fase 3 — Agent Marketplace
- `koupper agent list/info/install/remove`
- Commiteado en `igly/cortex` y `develop` del CLI

### Fase 2 — TelegramChannelProvider
- `TelegramChannelProvider` SP + `TelegramBridgeAgent.kts`
- Mergeado a `koupper/develop`

### Documentación pública
- `koupper-docs` con sección Agent Runtime + Agents individuales
- Mergeado a `koupper-docs/main`

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
5. ~~**Job result visible en dashboard**~~ ✓ (columna Result + hover full value)
6. ~~**Navegación teclado web dashboard**~~ ✓ (ArrowUp/Down en tabla de jobs)
7. **Flechas TUI monitor** — fuente en `igly/cortex`, pendiente verificar
8. **Sync develop → igly/cortex** — cherry-pick Fase 4 + CortexAgent + result
9. **`CortexMemoryStore` en igly/cortex** → reemplazar por `MemoryProvider` real
10. **CORTEX multimodal** — Playwright MCP

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
