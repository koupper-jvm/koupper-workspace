# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-01 — React 19 project working in 12s_

---

## Current Objective

CORTEX operativo como coding assistant. Probado y funcionando:
crea proyectos React 19 + TypeScript + Vite en 12 segundos con autocorrección de errores.

---

## Estructura de ramas (definitiva)

| Repo | Branch público | Branch privado CORTEX |
|---|---|---|
| `koupper` | `develop` | — |
| `koupper-cli` | `develop` | `igly/cortex` |
| `koupper-workspace` | `develop` | `igly/cortex` |

---

## Estado de ramas

| Repo | Branch | Último commit |
|---|---|---|
| `koupper` | `develop` | `2bafb64` — native function calling en InferenceEngine |
| `koupper-cli` | `igly/cortex` | `86f747c` — StartCommand limpio (terminal vs web) |
| `koupper-workspace` | `igly/cortex` | `5bc37e5` — SESSION_STATE anterior |

---

## Cómo usar

```bash
koupper start          # terminal — worker + monitor TUI (q para salir)
koupper start --web    # web — worker + dashboard http://localhost:18083

# CORTEX (coding assistant) — se lanza aparte:
koupper run ~/.koupper/agents/CortexAgent.kts
```

CORTEX usa Groq automáticamente si las vars están en `~/.profile`.

---

## Config LLM (en ~/.profile y ~/.bashrc)

```bash
# Groq (activo — free, sin rate limits agresivos)
export KOUPPER_LLM_PROVIDER=openai
export KOUPPER_LLM_API_BASE=https://api.groq.com/openai/v1
export KOUPPER_LLM_API_KEY=<tu-groq-api-key>  # console.groq.com
export KOUPPER_LLM_MODEL=llama-3.3-70b-versatile

# Fallback local
export KOUPPER_LLM_MODEL_PATH=~/develop/llama.cpp/models/qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf
export KOUPPER_LLM_EXECUTABLE=~/develop/llama.cpp/build/bin/llama-server
```

El shim `~/.koupper/bin/koupper` hace `source ~/.profile` automáticamente.

---

## Lo que CORTEX hace hoy (verificado)

**React 19 project en 12 segundos:**

```
17:38:14  Pedido enviado
17:38:15  Tool call 1: npm create vite@latest mi-app --template react-ts → ✓
17:38:15  Tool call 2: npm run build → ✗ (tsc not found — autocorrección)
17:38:16  Tool call 3: npm install → 152 packages ✓
17:38:24  Tool call 4: npm run build → vite v8.0.16, 20 modules ✓
17:38:26  DONE — dist/ listo para producción
```

Resultado: React 19.2.6 + TypeScript 6 + Vite 8, proyecto completo.

---

## Arquitectura CORTEX hoy

**Native function calling** (OpenAI tool_calls format):
- Una sola inferencia puede pedir múltiples tools en paralelo
- El modelo se autocorrige viendo los resultados reales
- 3-4 inferencias para un proyecto completo vs 15+ antes

**Tools disponibles en CORTEX (17 en native FC):**
- 14 MCP built-in: `bash`, `write_file`, `read_file`, `list_dir`, `fetch_url`, `create_agent`, `run_agent`, `list_agents`, `job_status`, `read_log`, `inspect_swarm`, `pipeline_run`, `cancel_job`, `swarm_run`
- 3 memory: `memory.remember`, `memory.recall`, `memory.forget`
- Playwright (23 tools) — conectado pero fuera del native FC para reducir tokens

**Puertos activos:**
| Puerto | Servicio |
|---|---|
| 9998 | Octopus daemon |
| 18082 | MCP server (requiere monitor jar corriendo) |
| 18083 | Web UI (solo con `--web`) |

**IMPORTANTE:** El MCP server (:18082) vive dentro de `koupper-monitor.jar`.
Con `koupper start --web` no hay monitor, entonces hay que arrancarlo manualmente:
```bash
nohup java -jar ~/.koupper/libs/koupper-monitor.jar ~/.koupper/jobs &
```
O usar `koupper start` (modo terminal) que incluye el monitor.

---

## Agentes instalados

```
CortexAgent.kts        — coding assistant, native FC, 8h session, schema sanitization
CortexWebUiAgent.kts   — dashboard + POST /api/run-agent + botón ▶
GreetingAgent, AgentCreatorAgent, RssFeedAgent, HeartbeatAgent, TelegramBridgeAgent
SysMonitorAgent, FileOrganizerAgent, PortScannerAgent, DiaryAgent, CodeReviewAgent
```

---

## Bugs conocidos / limitaciones

1. **MCP server no corre con `--web`** — arrancarlo con monitor.jar manualmente si se usa `--web` + CORTEX
2. **Groq rate limits (free)** — 30 RPM, 6000 TPM. Para proyectos largos, esperar entre pedidos
3. **Duplicate log lines** — CortexAgent a veces aparece dos veces en el log (cosmético)
4. **Merge develop→igly/cortex en CLI** — usar `./scripts/sync-from-develop.sh`

---

## Próximos pasos

- Hacer que `koupper start --web` también levante el MCP server automáticamente
- Probar más casos de uso de CORTEX (APIs, CLIs, backends)
- Fix del duplicate log en CortexAgent

---

## Cómo retomar

```bash
# Opción A — terminal
koupper start
# En otra terminal:
koupper run ~/.koupper/agents/CortexAgent.kts

# Opción B — web
koupper start --web
nohup java -jar ~/.koupper/libs/koupper-monitor.jar ~/.koupper/jobs &
koupper run ~/.koupper/agents/CortexAgent.kts

# Ver CORTEX en tiempo real
tail -f ~/.koupper/jobs/logs/cortex/cortex-session.log

# Mandar pedido a CORTEX
echo "tu pedido" > ~/.koupper/jobs/commands/wizard/$(date +%s%3N).response
```
