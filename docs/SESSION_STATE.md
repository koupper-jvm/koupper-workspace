# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-07 (sesión 7)_

---

## Estado general

- **CORTEX** vive en su propio repo: `git@github.com:Iglymx/cortex.git` → `~/develop/cortex/`
- **Koupper** (framework open-source): `git@github.com:koupper-jvm/koupper-workspace.git`
- Todos los repos están al día en sus ramas principales

---

## Repos y ramas activas

| Repo | Ruta local | Rama | Estado |
|---|---|---|---|
| koupper (framework) | `~/develop/koupper workspace/koupper` | `develop` | ✅ limpio |
| koupper-workspace | `~/develop/koupper workspace` | `develop` | ✅ limpio |
| cortex | `~/develop/cortex` | `main` | ✅ limpio |
| igly2 (sitio web) | `~/develop/igly2` | `develop` | pendiente merge→master para prod |

---

## Lo que está construido

### Fase 1 — Base operativa ✅

| Agente | Descripción |
|---|---|
| `CortexAgent.kts` | Orquestador principal — Planner-Executor, multi-LLM, 21+ tools |
| `CortexWebUiAgent.kts` | Dashboard REST+SSE en localhost:18083 + voz edge-tts bilingüe + **multi-tenant** |
| `TelegramBridgeAgent.kts` | Bridge Telegram ↔ CORTEX, offset persistido |
| `HeartbeatAgent.kts` | Monitor proactivo + **watchdog auto-restart** — condiciones en `~/.koupper/heartbeat.md`, loop 60s |

### Fase 2 — CORTEX Enterprise (swarm de conocimiento) ✅

| Agente | Descripción |
|---|---|
| `FileIndexerAgent.kts` | Walk dirs → extrae texto (PDF/TXT/MD) → chunks → embeddings → vector DB |
| `KnowledgeQueryAgent.kts` | Busca en vector DB local — one-shot o HTTP servidor (puerto 18085) |
| `MasterKnowledgeAgent.kts` | Fan-out paralelo a N nodos + cosine re-rank — HTTP servidor (puerto 18086) |

### Fase 5 — Pipeline architecture ✅ (completado sesión 7)

| Componente | Descripción |
|---|---|
| `WorkerCommand.kt` | Input passing: job JSON `input` → positional arg a `koupper run`; `[RESULT] <json>` sentinel; `pipelineNext` dispatch automático al siguiente step |
| `RssFeedAgent.kts` | Primer agente tipado: `() -> FeedResult`, emite `[RESULT]` para chaining |
| `SummarizerAgent.kts` | Segundo agente: `(FeedResult) -> DigestSummary`, llama Ollama, emite `[RESULT]` |
| `HeartbeatAgent.kts` | Campo `pipeline: A.kts > B.kts` en condiciones → `dispatchPipeline()` construye pipelineNext JSON |
| `CortexWebUiAgent.kts` | Resultado tipado re-serializado con `toJson()` (fix: Map.toString() → JSON correcto) |
| `LogViewer.tsx` | Tab "Result" con árbol JSON colapsable (ResultPanel) cuando job emite `[RESULT]` |

**Job JSON v2 (pipeline):**
```json
{
  "id": "morning-digest-step0",
  "scriptPath": "agents/RssFeedAgent.kts",
  "pipelineId": "morning-digest",
  "pipelineStep": 0,
  "pipelineTotal": 2,
  "pipelineNext": { "scriptPath": "agents/SummarizerAgent.kts" }
}
```

**Cómo probar un pipeline manual:**
```bash
echo '{"id":"test-p0","scriptPath":"agents/RssFeedAgent.kts","pipelineId":"test","pipelineStep":0,"pipelineTotal":2,"pipelineNext":{"scriptPath":"agents/SummarizerAgent.kts"}}' > ~/.koupper/jobs/default/test-p0.json
# El worker encola test-step1 automáticamente al completarse el step0
```

### Fase 4 — Multi-tenant ✅ (completado sesión 6)

- `CortexWebUiAgent`: separa jobs/logs por cliente en `~/.koupper/jobs/clients/<id>/`
- Nuevas rutas: `GET /api/clients`, `POST /api/clients`, `GET /api/client/{id}/swarm`, `GET /api/client/{id}/history`
- `default` client retro-compatible con la estructura existente de `~/.koupper/jobs/`

### Integración en CortexAgent

| Tool | Comportamiento |
|---|---|
| `knowledge_index` | Usuario da una ruta → CORTEX indexa automáticamente |
| `knowledge_query` | Busca en vector DB — intenta MasterKnowledgeAgent (18086) → fallback KnowledgeQueryAgent (18085) |
| `remote_bash` | Ejecuta comandos en máquinas remotas vía SSH (JschSSHClient) |
| `remote_deploy` | Instala Koupper + agentes en host remoto, arranca servicios, registra nodo en knowledge-nodes.json |

### Koupper framework (develop)

- `FileHandler.listFiles(dirPath, recursive, extensions)` — walk de directorios sin java.io.File directo
- `OllamaEmbedder.embed(text, baseUrl, model)` — embeddings semánticos vía Ollama. Fallback silencioso.
- `OllamaEmbedder.isAvailable(baseUrl)` — health check rápido
- `VectorDbProvider.clear(collection)` — elimina todos los records y el archivo JSON en disco
- **EditProvider** (`edit()`) — surgical string replace, view/replaceLines/deleteLines por rango
- **BuildProvider** (`build()`) — Gradle+NPM, `BuildResult.Failure.summary` estructurado
- **LspBridgeProvider** (`lspBridge()`) — JSON-RPC 2.0 sobre stdio; `diagnostics()`, `hover()`, `definition()`; 30 tests
- **GitSP extendido** (sesión 6) — 11 métodos nuevos: `add`, `blame`, `currentBranch`, `listBranches`, `deleteBranch`, `push`, `pull`, `fetch`, `reset`, `stash`, `stashPop`; 19 tests
- **`staticFiles(prefix, dir)` DSL** (sesión 6) — en `GrizzlyRuntimeRouterProvider`: sirve assets estáticos con guard de path traversal, 16 MIME types; `buildStatics()` en `RuntimeRouterDsl`

### Migración SP (sesión 4) — todos los agentes CORTEX sin librerías externas

Agentes migrados de Jackson directo → Koupper SP (`fromJson<T>()` / `toJson()`):
`HeartbeatAgent`, `RssFeedAgent`, `TelegramBridgeAgent`, `GitStatusAgent`, `PluginManagerAgent`, `FileWatcherAgent`, `CortexAgent`, `CortexWebUiAgent`, `ContextPreloaderAgent`, `FileIndexerAgent`

> **Regla:** solo `com.koupper.*`, `java.*`, `kotlinx.*` — zero `com.fasterxml.*` fuera del framework

---

## HeartbeatAgent — watchdog auto-restart (sesión 6)

El `agent_down` es el único tipo de condición que **no despacha un job al worker** — actúa directamente:

1. Lee el PID del `target` file (ej. `~/.koupper/run/cortex.pid`)
2. Verifica `/proc/<pid>` en Linux
3. Si el proceso cayó: mata el PID viejo, lanza con `ProcessBuilder`, escribe el nuevo PID
4. `agent: worker` → arranca con `koupper worker` (caso especial)

Default `heartbeat.md` incluye 4 watchdogs (cortex, telegram, webui, worker) con `cooldown: 2`.

---

## staticFiles() DSL — cómo usarlo en agentes

```kotlin
val router = app.getInstance(RuntimeRouterProvider::class)
router.registerRouter {
    staticFiles("/assets", "/ruta/al/directorio/assets")
    staticFiles("/icons",  "/ruta/al/directorio")
    // ... rutas normales
    get<Unit> { path { "/health" }; script { { mapOf("ok" to true) } } }
}
```

No requiere configuración extra en Grizzly. Guard de path traversal incluido (`canonicalPath`).

---

## Cómo arranca CORTEX (stack completo)

```bash
~/develop/cortex/scripts/cortex-start.sh
open http://localhost:18083
tail -f ~/.koupper/jobs/logs/cortex/cortex-session.log
```

### Para activar búsqueda de documentos

```bash
QUERY_SERVE=true koupper run ~/.koupper/agents/KnowledgeQueryAgent.kts &
# Después el usuario le dice a CORTEX:
# "analiza los contratos en /docs/contratos"
```

---

## Puertos

| Puerto | Servicio |
|---|---|
| 9998 | Octopus daemon (socket) |
| 18082 | MCP server (dentro de octopus.jar) |
| 18083 | Dashboard web (CortexWebUiAgent) |
| 18085 | KnowledgeQueryAgent (modo servidor) |
| 18086 | MasterKnowledgeAgent (modo servidor) |
| 11434 | Ollama (local) |

---

## Config LLM (en `~/.bashrc`)

```bash
export K_OLLAMA_LLM=true
export K_OLLAMA_LLM_URL=http://localhost:11434/v1
export K_OLLAMA_LLM_MODEL=gemma3:12b
export K_OLLAMA_LLM_PRIORITY=1

export K_LAN_LLM=true
export K_LAN_LLM_URL=http://192.168.1.9:1234/v1
export K_LAN_LLM_MODEL=qwen/qwen3.6-35b-a3b
export K_LAN_LLM_PRIORITY=2

export K_GROQ_LLM=true
export K_GROQ_LLM_URL=https://api.groq.com/openai/v1
export K_GROQ_LLM_MODEL=llama-3.3-70b-versatile
export K_GROQ_LLM_PRIORITY=3
```

---

## Próximos pasos (por impacto)

1. **Deploy igly.mx a prod** — merge `develop→master` en igly2, `npm run build`, S3 sync, CloudFront invalidation
2. **Agent templates parametrizables** — el cliente llena formulario → genera config → despliega
3. **Panel de onboarding** — crear cliente desde dashboard genera dirs, config y agentes
4. **Pipeline visualization en dashboard** — agrupar jobs por `pipelineId`, mostrar progreso step N/total
5. **TelegramBridgeAgent como step 3** — enviar DigestSummary.overview via Telegram al completarse el pipeline

---

## Docs de referencia

| Archivo | Propósito |
|---|---|
| `~/develop/cortex/docs/CORTEX_STRATEGIC_VISION.md` | Visión, arquitectura, casos de uso, competencia |
| `~/develop/cortex/docs/CORTEX_FEATURE_CHECKLIST.md` | Checklist con estado de cada feature (actualizado sesión 6) |
| `~/develop/cortex/docs-site/docs/agents/heartbeat.md` | Condiciones Markdown, watchdog, env vars |
| `~/develop/cortex/docs-site/docs/agents/web-ui.md` | Multi-tenant API, env vars, staticFiles |
| `~/develop/cortex/docs-site/docs/reference/env-vars.md` | Referencia completa de env vars (limpia, sin vars inventadas) |
| `~/develop/cortex/scripts/cortex-start.sh` | Startup completo del stack |

---

## Notas para retoma en frío

- **Separación estricta de repos**: Koupper = framework; CORTEX = producto. Cero mezcla en commits.
- **No existe SP de Excel** (Apache POI no en deps Koupper) — FileIndexerAgent omite `.xlsx`
- **No existe SP de OCR** — imágenes no se indexan
- **HashEmbedder** es keyword-overlap (512-dim) — fallback cuando Ollama no corre
- **OllamaEmbedder** — nomic-embed-text, 768-dim. Requiere `ollama pull nomic-embed-text` (274MB, una vez)
- **Cambio de embedder invalida índices** — FileIndexerAgent detecta, limpia y reindexea automáticamente
- **TelegramChannelProvider** ya existe como SP en Koupper — no hay que crearlo
- **gh CLI no está instalado** en tdn-dell — usar `git push` directo; fast-lane script no funciona
- **local-quick-checks.sh `core` target** falla porque busca `koupper-document/` aunque no lo necesite — workaround: `./gradlew :providers:test` directo
