# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-18 (sesión 19)_

---

## Estado general

- **CORTEX** vive en su propio repo: `git@github.com:Iglymx/cortex.git` → `~/develop/cortex/`
- **Koupper** (framework open-source): `git@github.com:koupper-jvm/koupper-workspace.git`
- Todos los repos están limpios y al día (koupper-cli push bloqueado por branch protection — fix deployado localmente)

---

## Repos y ramas activas

| Repo | Ruta local | Rama | Estado |
|---|---|---|---|
| koupper (framework) | `~/develop/koupper workspace/koupper` | `develop` | 8 commits ahead, limpio ✅ |
| koupper-cli | `~/develop/koupper workspace/koupper-cli` | `igly/cortex` | push bloqueado por branch protection — fix deployado ✅ |
| cortex | `~/develop/cortex` | `develop` | limpio ✅ |
| dashboard (submodule) | `~/develop/cortex/dashboard` | `main` | limpio ✅ |

---

## Lo que está construido

### Fases 1–17 ✅ (sesiones 1–17)
Worker hardening, doctor, pipeline, SSE transport, multi-tenant, marketplace, edge nodes, dashboard UX, setup wizard, chat UX, nodes reconnect, jobs UX overhaul, schema typing, @Scheduled queue-visible — todo entregado.

### Sesión 18 ✅ — Morning digest pipeline fixes + dashboard job detail

| Fix | Detalle |
|---|---|
| `Octopus.kt` autoFlush=false | `PrintStream(RoutingOutputStream, autoFlush=true)` cortaba mensajes al límite 8192 bytes del BufferedWriter, truncando `[RESULT]` JSON. Fix: `autoFlush=false` |
| `ScriptRunnerOrchestrator.kt` unescapeIfEscapedJson | Blanket replace `\"→"` corrompía JSON válido con comillas escapadas en strings. Fix: guard `mapper.readTree(t)` antes del replace |
| `TelegramChannelProviderImpl.kt` 403 check | `sendMessage`/`sendPhoto` ignoraban código HTTP no-200, retornaban `sent=true` aunque el bot estuviera bloqueado. Fix: lanza excepción con status+description |
| `SummarizerAgent.kts` | Switch a `OpenAICompatibleEngine` + fallback Groq + parsing robusto de JSON con newlines literales + `SUMMARIZER_MAX=10` |
| `KOUPPER_LLM_MAX_TOKENS=4096` en `~/.profile` | Evita truncación de respuesta LLM a 2048 tokens |
| Dashboard: pantalla negra en job digest | `detail.result` llegaba como objeto ya parseado, `JSON.parse(objeto)` fallaba, `parseKotlinDataClass(objeto).match()` → TypeError → sin ErrorBoundary → pantalla negra. Fix: detectar objeto vs string + ErrorBoundary en Routes |
| Dashboard: jobs inconsistentes | result.json solo guardaba `id`+`result`. Fix en worker: también guarda `fileName`, `scriptPath`, `submittedAt`, `completedAt`, `input`. Backfill manual de web-cache para jobs existentes |
| Dashboard: Input duplicado | Sección "Input" con "Sin parámetros" se mostraba aunque SchemaView ya mostrara inputType. Fix: ocultar sección Input cuando schema tiene inputType y no hay datos runtime |
| Dashboard: completedAt | Nuevo campo ISO timestamp en result.json, mostrado en panel de detalle |

---

## Arquitectura del LLM routing

```
Local (Gemma 3 12B — Ollama :11434)  prioridad 3
LAN   (Gemma-4-12B — LM Studio :1234, 192.168.1.8)  prioridad 2  ← soporta tool calling
Cloud (Qwen3 35B — Groq)  prioridad cloud
```

---

## Puertos

| Puerto | Servicio |
|---|---|
| 9998 | Octopus daemon (socket) |
| 18082 | MCP server |
| 18083 | Dashboard web (CortexWebUiAgent) |
| 18085 | KnowledgeQueryAgent |
| 18086 | MasterKnowledgeAgent |
| 11434 | Ollama (local) |
| 1234 | LM Studio LAN (192.168.1.8) |

---

## Arquitectura de scheduling — ESTADO ACTUAL

### Cómo funciona HOY el morning-digest
```
heartbeat.md → HeartbeatAgent (cada 60s) → job JSON en cola → worker → RssFeedAgent → SummarizerAgent → TelegramNotifyAgent
```
Definido en `~/.koupper/heartbeat.md` con `cooldown: 720` minutos.

### Cómo debería funcionar (PENDIENTE)
```
RssFeedAgent.kts con @Scheduled(cron="0 8 * * *", pipeline="SummarizerAgent.kts > TelegramNotifyAgent.kts")
→ octopus escribe job con pipelineNext → worker ejecuta pipeline completo
```

### Estado actual de @Scheduled
- **Framework**: `@Scheduled` funciona — escribe a la cola, visible en dashboard ✅
- **Anotación**: `cron`, `rate`, `delay`, `at`, `configId`, `chain` ✅ (sesión 19)
- **Pipeline chaining**: `enqueuePipelineJob()` ya existía en `ScheduledSetup`, solo faltaba exponer `chain` ✅ (sesión 19)
- **SIDE_EFFECT**: `@Scheduled` ya no bloquea `@Export`, permite script runnable + scheduled ✅ (sesión 19)
- **Agentes digest**: ninguno tiene `@Scheduled` ni `@Logger` ❌ (pendiente migrar)

---

## Notas para retoma en frío

- **Repos**: Koupper = framework; CORTEX = producto. `cortex/dashboard` es git submodule — commit en submodule primero, luego bump pointer en cortex
- **gh CLI no está instalado** — usar `git push origin develop`
- **Build del dashboard**: `cd ~/develop/cortex/dashboard && npm run build && cp -r dist/. ~/.koupper/web/`
- **koupper-cli push**: bloqueado por branch protection en `github.com:koupper-jvm/koupper-cli.git` — WorkerCommand fix está en `igly/cortex` branch, deployado en `~/.koupper/libs/koupper-cli-4.8.0.jar`
- **Web-cache**: `~/.koupper/web-cache/jobs/{id}.json` — contiene fileName/scriptPath para que el detail endpoint extraiga schema. Backfill hecho para todos los jobs existentes
- **Cooldown state**: `~/.koupper/heartbeat-state.json` — borrar entrada `morning-digest` para forzar re-ejecución sin esperar los 720 min
- **Script cache** en `~/.koupper/cache/compiled-scripts/` — limpiar con `rm *.bin` si hay errores
- **Worker trunca resultados grandes**: bug de Koupper resuelto (autoFlush=false en Octopus.kt)
- **`ScheduledSetup` es singleton object** — `registeredScripts` se resetea al reiniciar octopus

---

## Pendiente

- **Migrar agentes digest** — `RssFeedAgent.kts`, `SummarizerAgent.kts`, `TelegramNotifyAgent.kts` a usar `@Scheduled(chain="...")` + `@Logger`
- **Run script en nodo remoto**: SSH execution en NodeProvisionerAgent (`doRun`) + modal con credenciales
- **Gemma3 Ollama error**: `gemma3:12b` falla con 400 desde `localhost:11434`
- **extractAgentSchema recursion**: data classes anidadas no se expanden
- **koupper-cli PR**: WorkerCommand fix necesita PR a `develop` en `koupper-jvm/koupper-cli`

---

## Otros pendientes

- **Run script en nodo remoto**: SSH execution en NodeProvisionerAgent (`doRun`) + modal con credenciales
- **Gemma3 Ollama error**: `gemma3:12b` falla con 400 desde `localhost:11434`
- **extractAgentSchema recursion**: data classes anidadas no se expanden
- **koupper-cli PR**: WorkerCommand fix necesita PR a `develop` en `koupper-jvm/koupper-cli`

---

## Commits sesión 19

```
koupper/develop:
  2743d87  feat(providers): SPI-based auto-discovery with topological init order
  5834ab5  feat(scheduled): add chain param to @Scheduled + enable parallel Export
  6a33462  test(octopus): add E2E test harness with embedded Octopus
  85cb5a0  feat(security): add @Secret annotation for automatic output redaction
  cc7a24c  feat(contract): add @KoupperVersion annotation and versioned preamble
  872ac43  feat(errors): add structured error codes to execution pipeline
  33f1dcb  fix(scripting): map compile errors to original source lines
  2a1c2fb  feat(scripting): add reflection-based export signature extraction
```

## Assessment: completado ✅

Los 8 ítems del `KOUPPER_FRAMEWORK_ASSESSMENT.md` entregados en esta sesión.

## Commits sesión 18

```
koupper/develop:
  c75e54a  fix(octopus): prevent [RESULT] truncation + JSON corruption in pipeline
  02269f7  fix(telegram): throw on non-200 HTTP response in sendMessage/sendPhoto

cortex/develop:
  8f29fb3  fix(agents): SummarizerAgent — OpenAICompatibleEngine + Groq fallback + robust JSON parsing
  ee7abd6  chore(dashboard): update submodule

cortex/dashboard (main):
  3e7483b  fix(dashboard): black screen when clicking digest job + add ErrorBoundary
  3370213  fix(dashboard): show completedAt date+time in job detail panel
  e7db4d7  fix(dashboard): hide redundant Input section when schema already shows inputType

koupper-cli (igly/cortex — push bloqueado):
  4f98401  fix(worker): persist fileName, scriptPath, submittedAt, completedAt, input in result.json
```
