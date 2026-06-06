# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-05 (sesión 2)_

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
| `CortexWebUiAgent.kts` | Dashboard REST+SSE en localhost:18083 + voz edge-tts bilingüe |
| `TelegramBridgeAgent.kts` | Bridge Telegram ↔ CORTEX, offset persistido |
| `HeartbeatAgent.kts` | Monitor proactivo, condiciones en `~/.koupper/heartbeat.md`, loop 60s |

### Fase 2 — CORTEX Enterprise (swarm de conocimiento) ✅

| Agente | Descripción |
|---|---|
| `FileIndexerAgent.kts` | Walk dirs → extrae texto (PDF/TXT/MD) → chunks → embeddings → vector DB |
| `KnowledgeQueryAgent.kts` | Busca en vector DB local — one-shot o HTTP servidor (puerto 18085) |
| `MasterKnowledgeAgent.kts` | Fan-out paralelo a N nodos → agrega resultados — HTTP servidor (puerto 18086) |

### Integración en CortexAgent (sesión 2)

| Tool | Comportamiento |
|---|---|
| `knowledge_index` | Usuario da una ruta → CORTEX indexa automáticamente (nunca pide hacerlo manualmente) |
| `knowledge_query` | Busca en vector DB — intenta MasterKnowledgeAgent (18086) → fallback KnowledgeQueryAgent (18085) |

### Koupper framework

- `FileHandler.listFiles(dirPath, recursive, extensions)` — walk de directorios desde scripts sin java.io.File directo

---

## Cómo arranca CORTEX (stack completo)

```bash
~/develop/cortex/scripts/cortex-start.sh
open http://localhost:18083
tail -f ~/.koupper/jobs/logs/cortex/cortex-session.log
```

### Para activar búsqueda de documentos

```bash
# Copiar agentes al directorio de Koupper
cp ~/develop/cortex/agents/FileIndexerAgent.kts     ~/.koupper/agents/
cp ~/develop/cortex/agents/KnowledgeQueryAgent.kts  ~/.koupper/agents/
cp ~/develop/cortex/agents/MasterKnowledgeAgent.kts ~/.koupper/agents/

# Arrancar servicio de consulta (opcional — knowledge_index funciona sin esto)
QUERY_SERVE=true koupper run ~/.koupper/agents/KnowledgeQueryAgent.kts &

# Después, el usuario simplemente le dice a CORTEX:
# "analiza los contratos en /docs/contratos"
# → CORTEX llama knowledge_index automáticamente, luego knowledge_query
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

1. **Deploy igly.mx a prod** — merge `develop→master` en igly2, `npm run build`, S3 sync, CloudFront invalidation (manual)
2. **Dashboard multi-tenant** — separar jobs/logs por cliente en el panel
3. **Agent templates parametrizables** — el cliente llena un formulario → genera su config → se despliega
4. **Embeddings semánticos** — reemplazar HashEmbedder por llamada a LLM embedding API (mejor recall en knowledge_query)
5. **TelegramChannelProvider como SP** — SP ya existe en Koupper (`telegram/` package), solo mover lógica del bridge

---

## Docs de referencia

| Archivo | Propósito |
|---|---|
| `~/develop/cortex/docs/CORTEX_STRATEGIC_VISION.md` | Visión, arquitectura, casos de uso, competencia, estado actual |
| `~/develop/cortex/docs/CORTEX_FEATURE_CHECKLIST.md` | Checklist detallado con estado de cada feature |
| `~/develop/cortex/agents/CortexAgent.kts` | Orquestador principal — loop de inferencia, tools, routing LLM |
| `~/develop/cortex/scripts/cortex-start.sh` | Startup completo del stack |
| `~/develop/cortex/config/knowledge-nodes.example.json` | Plantilla de nodos para MasterKnowledgeAgent |

---

## Notas para retoma en frío

- **No existe SP de Excel** (Apache POI no en deps Koupper) — FileIndexerAgent omite `.xlsx`
- **No existe SP de OCR** — imágenes no se indexan
- **HashEmbedder** es keyword-overlap (no semántica profunda) — suficiente para MVP
- **FileWatcherAgent y GitStatusAgent** existen en `~/.koupper/agents/` pero no en el repo cortex — instalados manualmente
- **La landing igly.mx** (`/ourservices/ai-agents`) está en `develop` de igly2 — reescrita para público general, lista para prod
- **TelegramChannelProvider** ya existe como SP en Koupper — no hay que crearlo, solo usarlo en TelegramBridgeAgent
