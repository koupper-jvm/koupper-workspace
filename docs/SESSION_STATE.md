# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-03 — auto-start, dashboard, memoria, AgentCreatorAgent con LLM real_

---

## Current Objective

CORTEX completamente operativo. Pendiente: HeartbeatAgent, TelegramBridge verificado, GitStatusAgent.
Ver `docs/CORTEX_FEATURE_CHECKLIST.md` para el orden completo.

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
```

---

## Config LLM

```bash
# En ~/.profile y ~/.bashrc
export KOUPPER_LLM_PROVIDER=openai
export KOUPPER_LLM_API_BASE=http://192.168.1.9:1234/v1   # servidor LAN (LM Studio / qwen3.6-35b-a3b)
export KOUPPER_LLM_API_KEY=gsk_...                        # Groq key (usada como auth en el server local)
export KOUPPER_LLM_MODEL=qwen/qwen3.6-35b-a3b

# Fallback local
export KOUPPER_LLM_MODEL_PATH=~/develop/llama.cpp/models/qwen2.5-7b-instruct-q4_k_m-00001-of-00002.gguf
export KOUPPER_LLM_EXECUTABLE=~/develop/llama.cpp/build/bin/llama-server
```

El servidor LAN (192.168.1.9:1234) es qwen3.6-35b-a3b. Tiene contexto limitado — prompts cortos.

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
CortexAgent.kts         — coding assistant, native FC, topK=3 memory recall, log() para logFile
CortexWebUiAgent.kts    — dashboard http://localhost:18083
TelegramBridgeAgent.kts — bridge Telegram ↔ CortexAgent
AgentCreatorAgent.kts   — wizard con LLM real + correction loop + fallback scaffold
HeartbeatAgent.kts      — proactive monitor (condiciones en ~/.koupper/heartbeat.md)
RssFeedAgent.kts, GreetingAgent.kts, y varios más
```

---

## Memoria vectorial

19 entries pre-cargados en `~/.koupper/memory/memory-texts.json` + vectordb.
CortexAgent inyecta top-3 memories relevantes por query (take 400 chars).

Para agregar entries: `memory.remember` via CORTEX, o Python directo:
```python
# HashEmbedder en Python (validado contra implementación Kotlin)
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

## Bugs resueltos (2026-06-03)

1. **octopus.jar ZipException** — fatJar con zip64 incremental corrompe LOC headers. Fix: `doFirst { delete(archiveFile) }` ya estaba en build.gradle; reconstruir desde cero: `./gradlew :octopus:fatJar` (borrar el jar antes).
2. **Conflicto emit()** — preamble de octopus define `emit(text)`. CortexAgent la redefinía. Fix: renombrado local a `log()`.
3. **MCP server = octopus.jar** — monitor.jar no es necesario para el MCP server.
4. **Race condition CommandBridge** — `cat > file` dispara ENTRY_CREATE antes de escribir. Fix: `mv /tmp/cmd`.
5. **AgentCreatorAgent: streaming vacío** — `predict` con listener usa `stream:true`; el server LAN no streameaba. Fix: usar `predict<String>` sin listener.
6. **AgentCreatorAgent: Privacy Guard** — FederatedInferenceEngine bloquea prompts con `~/` o `/home/`. Fix: prompt sin paths hardcodeados.
7. **AgentCreatorAgent: @Export duplicado** — AnnotationsProcessor escanea strings del script. Fix: `"@" + "Export"` en strings generados.

---

## Próximos pasos (ver CORTEX_FEATURE_CHECKLIST.md)

1. **HeartbeatAgent operativo** — definir condiciones reales en `~/.koupper/heartbeat.md`
2. **TelegramBridgeAgent end-to-end** — verificar flow completo mensaje → CORTEX → respuesta
3. **GitStatusAgent** — agente útil real usando MCP GitHub
