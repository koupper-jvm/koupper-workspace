# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-04 — Planner-Executor, multi-provider LLM reader, Fase 1 completa_

---

## Current Objective

CORTEX con arquitectura Planner-Executor operativa. Fase 1 del checklist completada.
Próximo foco: validar el flujo completo Telegram → Plan → Confirmación → Ejecución con LLM online.

---

## Estructura de ramas

| Repo | Branch público | Branch privado CORTEX |
|---|---|---|
| `koupper` | `develop` | — |
| `koupper-cli` | `develop` | `igly/cortex` |
| `koupper-workspace` | `develop` | `igly/cortex` |

---

## Cómo arrancar

```bash
systemctl --user start koupper.service    # levanta todo
systemctl --user restart koupper.service  # reinicia

# Ver CORTEX en tiempo real
tail -f ~/.koupper/jobs/logs/cortex/cortex-session.log

# Mandar pedido a CORTEX (mv = atómico, evita race condition con WatchService)
echo "tu pedido" > /tmp/cmd.tmp && mv /tmp/cmd.tmp ~/.koupper/jobs/commands/wizard/$(date +%s%3N).response

# Confirmar un plan
echo "si" > /tmp/cmd.tmp && mv /tmp/cmd.tmp ~/.koupper/jobs/commands/wizard/$(date +%s%3N).response
```

---

## Config LLM — Convención K_*_LLM

```bash
# ~/.profile — patrón: K_[PROVIDER]_LLM=true activa el provider
# CortexAgent escanea todos, los ordena por PRIORITY y construye la cadena de fallback

export K_LAN_LLM=true
export K_LAN_LLM_API_KEY=gsk_...          # Groq key usada como auth del server LAN
export K_LAN_LLM_URL=http://192.168.1.9:1234/v1
export K_LAN_LLM_MODEL=qwen/qwen3.6-35b-a3b
export K_LAN_LLM_PRIORITY=1
export K_LAN_LLM_ROLE=general

export K_GROQ_LLM=true
export K_GROQ_LLM_API_KEY=gsk_...
export K_GROQ_LLM_URL=https://api.groq.com/openai/v1
export K_GROQ_LLM_MODEL=llama-3.3-70b-versatile
export K_GROQ_LLM_PRIORITY=2
export K_GROQ_LLM_ROLE=fast               # usado para planning (Intent Analyzer)

# Templates comentados en ~/.profile: OpenAI, DeepSeek, Gemini, Mistral, NVIDIA
# Para agregar uno: descomenta 5 vars, reinicia el servicio — sin tocar código

# Compat framework (AgentCreatorAgent y otros)
export KOUPPER_LLM_PROVIDER=openai
export KOUPPER_LLM_API_BASE=$K_LAN_LLM_URL
export KOUPPER_LLM_API_KEY=$K_LAN_LLM_API_KEY
export KOUPPER_LLM_MODEL=$K_LAN_LLM_MODEL
```

### Cadena de fallback en CortexAgent
```
[1] LAN server (priority=1, role=general)
      ↓ falla
[2] Groq (priority=2, role=fast)
      ↓ falla
[3] Memoria vectorial (top-3 recalls)
      ↓ nada relevante
[4] "⚠ Estoy teniendo dificultades de comunicación..."
```

### Roles disponibles
- `general` — uso general, ejecución principal
- `fast` — planning rápido (Intent Analyzer usa estos)
- `reasoning` — análisis arquitectónico (futuro)
- `code` — generación de código especializada (futuro, ej. DeepSeek)

---

## Arquitectura hoy

| Puerto | Servicio |
|---|---|
| 9998 | Octopus daemon |
| 18082 | MCP server (dentro de octopus.jar) |
| 18083 | Web UI (CortexWebUiAgent) |

**IMPORTANTE:**
- MCP server vive en `octopus.jar`, NO en `koupper-monitor.jar`
- Comandos a CORTEX: usar `mv /tmp/file ~/.koupper/jobs/commands/wizard/$(date +%s%3N).response` (atómico)
- `emit()` en agentes `.kts` = stdout (del preamble de octopus). El logFile es separado.

---

## Agentes instalados

```
CortexAgent.kts         — orchestrator: Planner-Executor, multi-LLM fallback, 19 tools
CortexWebUiAgent.kts    — dashboard http://localhost:18083
TelegramBridgeAgent.kts — bridge Telegram ↔ CortexAgent (offset persistido)
AgentCreatorAgent.kts   — wizard con LLM real + correction loop + fallback scaffold
HeartbeatAgent.kts      — proactive monitor (condiciones en ~/.koupper/heartbeat.md)
GitStatusAgent.kts      — digest diario de commits/PRs via MCP GitHub
FileWatcherAgent.kts    — monitor de directorios con reglas (log/move/dispatch)
RssFeedAgent.kts, GreetingAgent.kts, y varios más
```

---

## Arquitectura CortexAgent — Planner-Executor

### Flujo de un request complejo
```
Input → isComplexRequest()?
  NO  → ejecución directa (inferWithNativeTools)
  SÍ  → Intent Analyzer: buildPlan() con planningEngines (role=fast)
       → formatPlan() mostrado al usuario
       → ¿Ejecutar? (si / no / feedback)
           "si"      → ejecuta con plan como contexto
           "no"      → cancela
           feedback  → re-planifica con buildPlan(revisedRequest)
```

### Intent Analyzer (ensemble)
- Llama a todos los providers con `role=fast` o `role=reasoning` en paralelo (`async/awaitAll`)
- Si hay múltiples propuestas → sintetiza con el engine primario
- Output: JSON `{summary, type, risk, steps[], needs_confirmation}`
- `type=conversational` o `steps=[]` → ejecución directa sin confirmación

### Tools MCP registrados (local, puerto 18082)
- `bash` — ejecuta comando shell, devuelve stdout (max 4000 chars)
- `list_files` — lista archivos de un directorio
- `job_status` — busca job por ID en todas las queues + últimas 20 líneas de log

### Tools MCP externos
- `github.*` — 26 tools (list_commits, list_pull_requests, etc.)
- `playwright.*` — 23 tools (navegación web headless)

---

## Memoria vectorial

19 entries en `~/.koupper/memory/memory-texts.json` + vectordb.
CortexAgent inyecta top-3 memories relevantes por query.

**IMPORTANTE:** La entrada 18 fue corregida — ya NO lista tools ficticios.
Tools reales: `bash`, `list_files`, `job_status` (local) + github + playwright.

Para agregar entries vía Python:
```python
import re, math
def java_hash(s):
    h = 0
    for c in s: h = (h * 31 + ord(c)) & 0xFFFFFFFF
    return h
def accumulate(vec, token):
    h = java_hash(token)
    b1 = h % 512
    h = ((h ^ (h >> 16)) * 0x45d9f3b) & 0xFFFFFFFF
    vec[b1] += 1.0; vec[h % 512] += 0.5
def embed(text):
    vec = [0.0]*512
    for t in re.split(r'[\s,.:;!?"\'()\[\]{}<>/\\@#$%^&*+=|~`]+', text.lower()):
        if t: accumulate(vec, t); [accumulate(vec, t[i:i+2]) for i in range(len(t)-1)]
    m = math.sqrt(sum(x*x for x in vec))
    return [x/m for x in vec] if m else vec
```

---

## Heartbeat conditions (`~/.koupper/heartbeat.md`)

| Condición | Cuándo | Agente | Cooldown |
|---|---|---|---|
| morning-digest | time_after 08:00 | RssFeedAgent | 720 min |
| failed-jobs-alert | queue_has_failed default | GreetingAgent | 60 min |
| nightly-cleanup | time_after 23:00 | DiskCleanerAgent | 720 min |
| daily-git-status | time_after 09:00 | GitStatusAgent | 720 min |

---

## Bugs resueltos (2026-06-04)

1. **HeartbeatAgent: imports y log.info{}** — faltaban `@Export`, `jacksonObjectMapper`; `log.info{}` no existe en .kts. Fix: imports + `fun log()` local.
2. **HeartbeatAgent: formato job JSON** — dispatch creaba JSON incompleto. Fix: incluir `id`, `fileName`, `functionName`, `sourceType`.
3. **TelegramBridgeAgent: write no atómico** — `writeText` dispara ENTRY_CREATE antes de escribir. Fix: tmp file + `renameTo`.
4. **TelegramBridgeAgent: offset=0 en restart** — reprocesaba todos los mensajes. Fix: persistir en `~/.koupper/telegram-offset.json`.
5. **TelegramBridgeAgent: errores LLM swallowed** — `[Error:` filtrado, usuario recibía mensaje vacío. Fix: detectar tipo de error y enviar mensaje descriptivo.
6. **FileWatcherAgent: `return` en local fun** — `.kts` no permite `return` con tipo en funciones locales de expresión. Fix: `fun f(): Boolean = expr`.
7. **FileWatcherAgent: `continue` en forEach** — lambda no-inline. Fix: `for` loop + `if (ev.kind() == ENTRY_CREATE)`.
8. **CortexAgent: memoria con tools ficticios** — entrada 18 listaba write_file, read_file, job_status, inspect_swarm (inexistentes). Fix: corregida con tools reales.
9. **CortexAgent: `job_status` no existía** — LLM inventaba `bash job_status.sh`. Fix: registrado como tool MCP real.

---

## Próximos pasos

1. **Validar Planner-Executor end-to-end** — probar con LLM server LAN online: plan → "si" → ejecución real
2. **TelegramChannelProvider como SP** — mover lógica Telegram a Service Provider reutilizable (Fase 2)
3. **Marketplace** — `koupper agent list/install/publish` (Fase 3)
4. **Métricas en dashboard** — jobs/min, success rate, P95 latency (Fase 4)
