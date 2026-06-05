# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-05_

---

## Estado general

- **CORTEX** vive en su propio repo: `git@github.com:Iglymx/cortex.git` → `~/develop/cortex/`
- **Koupper** (framework open-source): `git@github.com:koupper-jvm/koupper-workspace.git`
- Los dos repos están al día en sus ramas principales (`main` / `develop`)

---

## Repos y ramas activas

| Repo | Ruta local | Rama activa | Estado |
|---|---|---|---|
| koupper (framework) | `~/develop/koupper workspace/koupper` | `develop` | ✅ limpio, up to date |
| koupper-workspace | `~/develop/koupper workspace` | `develop` | ✅ limpio, up to date |
| cortex | `~/develop/cortex` | `main` | ✅ limpio, up to date |
| igly2 (sitio web) | `~/develop/igly2` | `develop` | pendiente push a prod (merge→master manual) |

---

## Qué está construido (Fase 1 completa)

### CORTEX (`~/develop/cortex/agents/`)

| Agente | Descripción |
|---|---|
| `CortexAgent.kts` | Orquestador principal — Planner-Executor, multi-LLM, 19+ tools MCP |
| `CortexWebUiAgent.kts` | Dashboard REST+SSE en localhost:18083 + voz edge-tts bilingüe |
| `TelegramBridgeAgent.kts` | Bridge Telegram ↔ CORTEX, offset persistido |
| `HeartbeatAgent.kts` | Monitor proactivo, condiciones en `~/.koupper/heartbeat.md`, loop 60s |
| `FileIndexerAgent.kts` | ⭐ NUEVO — indexa directorios → chunks → embeddings → vector DB (100% SPs) |

Todos tienen su `*.skill.json` con env vars, providers, triggers documentados.

### Koupper framework (`~/develop/koupper workspace/koupper`)

Cambio más reciente en `develop`:
- `FileHandler.listFiles(dirPath, recursive, extensions)` — walk de directorios sin usar `java.io.File` directo en scripts

---

## Arrancar CORTEX

```bash
# Prerrequisitos
ollama serve && ollama pull gemma3:12b
pip3 install edge-tts  # si no está instalado

# Arrancar stack completo
~/develop/cortex/scripts/cortex-start.sh

# Dashboard
open http://localhost:18083

# Log en tiempo real
tail -f ~/.koupper/jobs/logs/cortex/cortex-session.log
```

### Config LLM (en `~/.bashrc`)

```bash
# Prioridad 1 — local
export K_OLLAMA_LLM=true
export K_OLLAMA_LLM_URL=http://localhost:11434/v1
export K_OLLAMA_LLM_MODEL=gemma3:12b
export K_OLLAMA_LLM_PRIORITY=1

# Prioridad 2 — LAN
export K_LAN_LLM=true
export K_LAN_LLM_URL=http://192.168.1.9:1234/v1
export K_LAN_LLM_MODEL=qwen/qwen3.6-35b-a3b
export K_LAN_LLM_PRIORITY=2

# Prioridad 3 — cloud
export K_GROQ_LLM=true
export K_GROQ_LLM_URL=https://api.groq.com/openai/v1
export K_GROQ_LLM_MODEL=llama-3.3-70b-versatile
export K_GROQ_LLM_PRIORITY=3
```

---

## Puertos

| Puerto | Servicio |
|---|---|
| 9998 | Octopus daemon (socket) |
| 18082 | MCP server (dentro de octopus.jar) |
| 18083 | Dashboard web (CortexWebUiAgent) |
| 11434 | Ollama (local) |

---

## FileIndexerAgent — cómo usarlo

```bash
# One-shot: indexar un directorio
koupper run ~/develop/cortex/agents/FileIndexerAgent.kts \
  '{"watchDir":"/ruta/docs","collection":"mi-empresa"}'

# Daemon: reindexar cada 5 minutos
INDEXER_DIR=/ruta/docs INDEXER_LOOP_INTERVAL_S=300 \
  koupper run ~/develop/cortex/agents/FileIndexerAgent.kts &

# El vector DB queda en:
~/.koupper/vectordb/mi-empresa.json

# El estado incremental (archivos ya indexados) en:
~/.koupper/indexer/mi-empresa-state.json
```

Extensiones soportadas: `txt, md, pdf, csv, json, yaml, yml, xml, html`

---

## Próximos pasos (por orden de impacto)

1. **KnowledgeQueryAgent** — recibe query de texto, busca en el vector DB local, devuelve fragmentos con filename + chunk index. Bloque directo del FileIndexerAgent.
2. **MasterKnowledgeAgent** — fan-out a múltiples nodos de la red, agrega resultados. Requiere KnowledgeQueryAgent funcionando + endpoint HTTP por nodo.
3. **Deploy igly.mx a prod** — merge `develop→master` en igly2, `npm run build`, S3 sync, CloudFront invalidation (manual por el usuario).
4. **TelegramChannelProvider como SP** — ya existe en Koupper (`telegram/` package), mover lógica del bridge a ese SP.
5. **Dashboard multi-tenant** — separar jobs/logs por cliente en el panel.

---

## Docs de referencia

| Archivo | Propósito |
|---|---|
| `~/develop/cortex/docs/CORTEX_STRATEGIC_VISION.md` | Visión completa, arquitectura, casos de uso, competencia |
| `~/develop/cortex/docs/CORTEX_FEATURE_CHECKLIST.md` | Checklist de features con estado actual |
| `~/develop/cortex/agents/CortexAgent.kts` | Lógica del orquestador principal |
| `~/develop/cortex/scripts/cortex-start.sh` | Startup completo del stack |
| `~/develop/igly2/src/components/ourservices/AIAgents.tsx` | Landing pública de Igly Cortex |

---

## Notas para retoma en frío

- La landing de Igly Cortex (`/ourservices/ai-agents`) fue reescrita para público general sin jerga técnica. Está en `develop` de igly2, lista para merge a `master` (deploy manual).
- `FileWatcherAgent` y `GitStatusAgent` existen en `~/.koupper/agents/` pero no están en el repo cortex — el usuario los instaló manualmente.
- `HashEmbedder` (vectordb package, Koupper) es un `object` público accesible desde scripts: `import com.koupper.providers.vectordb.HashEmbedder`.
- No existe SP de Excel (Apache POI no está en deps de Koupper). FileIndexerAgent omite `.xlsx`.
- No existe SP de OCR. Imágenes no se indexan todavía.
