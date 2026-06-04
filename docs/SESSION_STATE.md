# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-04 — Dashboard v2: chat history, agent search, collapsible cols, chat UI_

---

## Current Objective

CORTEX con arquitectura Planner-Executor operativa y dashboard funcional.
Próximo foco: validar Planner-Executor end-to-end con LLM online + TelegramChannelProvider como SP.

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
export K_LAN_LLM_URL=http://192.168.1.9:1234/v1
export K_LAN_LLM_API_KEY=gsk_...
export K_LAN_LLM_MODEL=qwen/qwen3.6-35b-a3b
export K_LAN_LLM_PRIORITY=1
export K_LAN_LLM_ROLE=general

export K_GROQ_LLM=true
export K_GROQ_LLM_URL=https://api.groq.com/openai/v1
export K_GROQ_LLM_API_KEY=gsk_...
export K_GROQ_LLM_MODEL=llama-3.3-70b-versatile
export K_GROQ_LLM_PRIORITY=2
export K_GROQ_LLM_ROLE=fast   # usado para Intent Analyzer (planning)

# Templates comentados en ~/.profile: OpenAI, DeepSeek, Gemini, Mistral, NVIDIA
# Para agregar: descomenta 5 vars (true + KEY + URL + MODEL + PRIORITY), reinicia
```

### Cadena de fallback
```
[1] LAN server (priority=1)  →  [2] Groq (priority=2)  →  [3] Memoria vectorial  →  [4] Apología
```

### Roles disponibles
- `general` — ejecución principal
- `fast` — Intent Analyzer / planning (Groq)
- `reasoning`, `code` — futuros (DeepSeek, etc.)

---

## Arquitectura

| Puerto | Servicio |
|---|---|
| 9998 | Octopus daemon |
| 18082 | MCP server (dentro de octopus.jar) |
| 18083 | Web UI (CortexWebUiAgent) |

**IMPORTANTE:**
- Comandos a CORTEX: usar `mv /tmp/file ~/.koupper/jobs/commands/wizard/$(date +%s%3N).response` (atómico)
- MCP server vive en `octopus.jar`, NO en `koupper-monitor.jar`
- `emit()` en agentes `.kts` = stdout (preamble). `log()` local es para el logFile.
- index.html del dashboard: vite genera `<!doctype html>` (lowercase) — el build script tiene `sed` para convertirlo a uppercase (Grizzly solo detecta `<!DOCTYPE` uppercase)

---

## Agentes instalados

```
CortexAgent.kts         — Planner-Executor, multi-LLM fallback, 19 tools, K_*_LLM reader
CortexWebUiAgent.kts    — dashboard http://localhost:18083
TelegramBridgeAgent.kts — bridge Telegram ↔ CORTEX (offset persistido)
AgentCreatorAgent.kts   — wizard LLM real + correction loop + fallback scaffold
HeartbeatAgent.kts      — proactive monitor (condiciones en ~/.koupper/heartbeat.md)
GitStatusAgent.kts      — digest diario commits/PRs via MCP GitHub
FileWatcherAgent.kts    — monitor de directorios (log/move/dispatch)
RssFeedAgent.kts        — RSS aggregator + AI summary
GreetingAgent.kts, DiskCleanerAgent.kts, y varios más
```

---

## Arquitectura CortexAgent — Planner-Executor

```
Input → isComplexRequest()?
  NO  → ejecución directa
  SÍ  → buildPlan() con providers role=fast (paralelo async/awaitAll)
       → formatPlan() mostrado al usuario
       → ¿Ejecutar? (si / no / feedback para re-planificar)
           "si"  → inferWithNativeTools con plan como contexto
           "no"  → cancela
           texto → re-planifica con feedback
```

### Tools MCP registrados (local :18082)
- `bash` — ejecuta comando shell
- `list_files` — lista directorio
- `job_status` — busca job por ID en todas las queues + últimas 20 líneas de log

### Tools MCP externos
- `github.*` — 26 tools
- `playwright.*` — 23 tools

---

## Dashboard — koupper-dashboard

### Características actuales
- **Header**: Aurora ring animada (72px) + "CORTEX" en pixel font (Press Start 2P) + glow neon
- **Chat**: Historial de sesiones (localStorage), burbujas usuario/CORTEX, typing indicator animado, resize vertical
- **Columnas colapsables**: Jobs, Log, Sidebar cada una con × para cerrar y franja con nombre rotado para reabrir
- **Búsqueda de agentes**: scoring por nombre/descripción/rol/tags, multi-palabra fuzzy
- **Búsqueda de jobs**: filtro por ID o queue en tiempo real
- **Búsqueda en log**: highlight de matches con contador, opacidad reducida en no-matches
- **Agentes filtrados**: oculta test/scratch agents (HelloWorld, FreshStart, etc.), toggle "▼ +N more"

### Build/deploy
```bash
cd koupper-dashboard
npm run build   # incluye sed para uppercase DOCTYPE
cp -r dist/. ~/.koupper/web/
```

### Bug conocido resuelto
- `<!doctype html>` lowercase → Grizzly servía como application/json → HTML no renderizaba
- Fix: `sed -i 's/<!doctype html>/<!DOCTYPE html>/g' dist/index.html` en build script

---

## Heartbeat conditions (`~/.koupper/heartbeat.md`)

| Condición | Cuándo | Agente | Cooldown |
|---|---|---|---|
| morning-digest | 08:00 | RssFeedAgent | 720 min |
| failed-jobs-alert | queue_has_failed | GreetingAgent | 60 min |
| nightly-cleanup | 23:00 | DiskCleanerAgent | 720 min |
| daily-git-status | 09:00 | GitStatusAgent | 720 min |

---

## Memoria vectorial

19 entries en `~/.koupper/memory/memory-texts.json`.
CortexAgent inyecta top-3 recalls relevantes por query.
Entrada 18 corregida — ya no lista tools ficticios.

---

## Bugs resueltos (2026-06-04)

1. **HeartbeatAgent**: imports + `log.info{}` → `fun log()` + formato job JSON correcto
2. **TelegramBridgeAgent**: write atómico + offset persistido + errores descriptivos al usuario
3. **FileWatcherAgent**: `return` en fun local → expression body; `continue` en forEach → for+if
4. **CortexAgent: tools ficticios en memoria** → corregida entrada 18
5. **CortexAgent: job_status inexistente** → registrado como tool MCP real
6. **Dashboard: DOCTYPE lowercase** → build script agrega sed uppercase
7. **Dashboard: agente RSS mostraba nada** → handleViewAgent stripea .kts antes del API call

---

## Próximos pasos

1. **Validar Planner-Executor end-to-end** — probar con LLM LAN online: plan → "si" → ejecución
2. **TelegramChannelProvider como SP** — mover lógica Telegram a Service Provider (Fase 2)
3. **Marketplace** — `koupper agent list/install/publish` (Fase 3)
4. **Métricas en dashboard** — jobs/min, success rate, P95 latency (Fase 4)
