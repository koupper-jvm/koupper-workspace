# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-01 — native function calling + koupper start fully working_

---

## Current Objective

CORTEX operativo como coding assistant de nivel OpenCode:
- `koupper start` arranca todo automáticamente (cloud mode, native function calling)
- CORTEX puede crear proyectos completos (React, etc.) con autocorrección

---

## Estructura de ramas (definitiva)

| Repo | Branch público | Branch privado CORTEX |
|---|---|---|
| `koupper` | `develop` | — |
| `koupper-cli` | `develop` | `igly/cortex` |
| `koupper-workspace` | `develop` | `igly/cortex` |

---

## Estado de ramas hoy

| Repo | Branch | Último commit |
|---|---|---|
| `koupper` | `develop` | `2bafb64` — native function calling en InferenceEngine |
| `koupper-cli` | `igly/cortex` | `00f79ba` — StartCommand arranca CORTEX + forwardEnv completo |
| `koupper-workspace` | `igly/cortex` | `b95e4ef` — CortexAgent con native function calling |

---

## Cómo arrancar

```bash
koupper start
```

Eso es todo. Levanta:
- **Worker** — procesa jobs de la queue
- **Web UI** — dashboard en http://localhost:18083
- **Monitor TUI** — en el terminal (q para salir y matar todo)
- **MCP server** — http://localhost:18082/mcp/tools (14 tools)
- **CORTEX** — Cloud mode, Llama 3.3 70B (NVIDIA), native function calling

---

## Config LLM (en ~/.profile — activa en todas las shells)

```bash
export KOUPPER_LLM_PROVIDER=openai
export KOUPPER_LLM_API_BASE=https://integrate.api.nvidia.com/v1
export KOUPPER_LLM_API_KEY=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
export KOUPPER_LLM_MODEL=meta/llama-3.3-70b-instruct

# Fallback local (si sin internet)
export KOUPPER_LLM_MODEL_PATH=~/develop/llama.cpp/models/qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf
export KOUPPER_LLM_EXECUTABLE=~/develop/llama.cpp/build/bin/llama-server
```

**IMPORTANTE:** Las vars van en `~/.profile` (no en `.bashrc`) para que funcionen en shells no interactivas.

El shim `~/.koupper/bin/koupper` hace `source ~/.profile` automáticamente al arrancar.

---

## MCP Tools disponibles en CORTEX (40 total)

**14 built-in:**
`bash`, `write_file`, `read_file`, `list_dir`, `fetch_url`, `create_agent`, `run_agent`,
`list_agents`, `job_status`, `read_log`, `inspect_swarm`, `pipeline_run`, `cancel_job`, `swarm_run`

**23 Playwright (external MCP):**
`navigate`, `click`, `screenshot`, `snapshot`, `fill_form`, etc.

**3 memory:**
`memory.remember`, `memory.recall`, `memory.forget`

---

## Agentes instalados en ~/.koupper/agents/

```
CortexAgent.kts        — orchestrator con native function calling, 8h session
CortexWebUiAgent.kts   — dashboard web + POST /api/run-agent + botón ▶
GreetingAgent.kts      — saludo + análisis de swarm
AgentCreatorAgent.kts  — wizard LLM code gen
RssFeedAgent.kts       — fetch RSS + resumen LLM
HeartbeatAgent.kts     — monitor proactivo de condiciones
TelegramBridgeAgent.kts— bridge Telegram ↔ CORTEX
SysMonitorAgent.kts    — CPU, RAM, disco, puertos
FileOrganizerAgent.kts — clasifica ~/Downloads por tipo
PortScannerAgent.kts   — escanea puertos locales
DiaryAgent.kts         — resume logs del día con LLM
CodeReviewAgent.kts    — code review con LLM (CODE_REVIEW_FILE env)
```

---

## Qué se hizo hoy

### Native function calling
- `ToolModels.kt` — nuevos tipos: `ToolDefinition`, `NativeToolCall`, `NativeInferenceResult`
- `InferenceEngine` — nuevo método `predictWithTools()` con fallback para modelos locales
- `OpenAICompatibleEngine` — implementación completa usando `tools` field de OpenAI API
- `AgentMessage` — campo `nativeToolCalls: List<NativeToolCall>?` para batch tool calls
- `CortexAgent` — loop `inferWithNativeTools()` reemplaza CORTEX_TOOL text parsing

**Resultado:** 15 inferencias → 3-4 por proyecto. Autocorrección real (instaló `@vitejs/plugin-react` solo).

### MCP tools de filesystem (en `CortexMcpServer.kt`)
- `write_file`, `read_file`, `list_dir`, `bash` — con expansión de `~` y timeout 120s

### `koupper start` — flujo limpio
- `StartCommand` arranca octopus directamente con `forwardEnv` (NVIDIA vars incluidas)
- `StartCommand` lanza `CortexAgent` fuera del worker (sin timeout de 300s)
- Shim `~/.koupper/bin/koupper` hace `source ~/.profile` antes de arrancar octopus
- Vars LLM movidas a `~/.profile` para que funcionen en shells no interactivas

---

## Puertos

| Puerto | Servicio |
|---|---|
| 9998 | Octopus daemon socket |
| 18082 | MCP server JSON-RPC |
| 18083 | Web UI Grizzly HTTP |
| 8081 | llama-server (solo modo local) |

---

## Bugs conocidos

1. **`koupper doctor` warning de :8081** cuando `KOUPPER_LLM_PROVIDER=openai` — esperado, cosmético
2. **`npm run dev` timeout en bash** — el dev server no termina; usar `npm run build` para verificar
3. **Merge develop→igly/cortex en CLI** — usar `./scripts/sync-from-develop.sh`

---

## Próximo

- Probar CORTEX creando un proyecto completo end-to-end sin interrupciones
- Fix cosmético: omitir warning de llama-server en doctor cuando PROVIDER=openai
- Explorar más casos de uso con native function calling

---

## Cómo retomar

```bash
# Arrancar todo
koupper start

# Ver log de CORTEX en tiempo real
tail -f ~/.koupper/jobs/logs/cortex/cortex-session.log

# Mandar comando a CORTEX
echo "tu pedido aquí" > ~/.koupper/jobs/commands/wizard/$(date +%s%3N).response

# Ver estado
koupper doctor
```
