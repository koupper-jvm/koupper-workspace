# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-10 (sesión 17)_

---

## Estado general

- **CORTEX** vive en su propio repo: `git@github.com:Iglymx/cortex.git` → `~/develop/cortex/`
- **Koupper** (framework open-source): `git@github.com:koupper-jvm/koupper-workspace.git`
- Todos los repos están limpios y al día

---

## Repos y ramas activas

| Repo | Ruta local | Rama | Estado |
|---|---|---|---|
| koupper (framework) | `~/develop/koupper workspace/koupper` | `develop` | limpio ✅ |
| koupper-cli | `~/develop/koupper workspace/koupper-cli` | `igly/cortex` | limpio ✅ |
| cortex | `~/develop/cortex` | `develop` | limpio ✅ |
| dashboard (submodule) | `~/develop/cortex/dashboard` | `main` | limpio ✅ |

---

## Lo que está construido

### Fases 1–16 ✅ (sesiones 1–16)
Worker hardening, doctor, pipeline, SSE transport, multi-tenant, marketplace, edge nodes, dashboard UX, setup wizard, chat UX, nodes reconnect, jobs UX overhaul, schema typing — todo entregado.

### Sesión 17 ✅ — @Scheduled queue-visible + fix feedback loop

| Feature | Detalle |
|---|---|
| `ScheduledSetup.kt` — enqueue to queue | `@Scheduled` ahora escribe un job JSON a `~/.koupper/jobs/{queue}/` en vez de ejecutar en-proceso. El worker lo levanta → job visible en dashboard con logs, resultado e historial |
| `toJsonValue()` helper | Serializa params bare-string a JSON válido (`hello` → `"hello"`) |
| Fix feedback loop | `registeredScripts` ConcurrentHashMap en `ScheduledSetup`: si el worker re-ejecuta el script vía `koupper run`, octopus detecta que ya está registrado y retorna sin agregar otra tarea. Previene el loop exponencial (16k+ jobs en minutos) |
| Jobs limpiados | Queue `default` limpiada de jobs de prueba del loop |
| Escape fix CortexWebUiAgent | `Regex("-\\d+$")` corregido de `"-\d+$"` (invalid Kotlin escape) — agent name inference para jobs DONE sin fileName |

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
Definido en `~/.koupper/heartbeat.md`:
```markdown
## Condition: morning-digest
- when: time_after
- target: 08:00
- pipeline: RssFeedAgent.kts > SummarizerAgent.kts > TelegramNotifyAgent.kts
- queue: default
- cooldown: 720
```

### Cómo debería funcionar (PENDIENTE)
```
RssFeedAgent.kts con @Scheduled(cron="0 8 * * *", pipeline="SummarizerAgent.kts > TelegramNotifyAgent.kts")
→ octopus escribe job con pipelineNext → worker ejecuta pipeline completo
```

### Estado actual de @Scheduled
- **Framework**: `@Scheduled` funciona — escribe a la cola, visible en dashboard ✅
- **Anotación**: tiene `cron`, `rate`, `delay`, `at`, `configId` — NO tiene `pipeline` aún ❌
- **Agentes digest**: ninguno tiene `@Scheduled` ni `@Logger` — usan `println` ❌
- **`enqueueJob()`**: no construye `pipelineNext` aún ❌

---

## Notas para retoma en frío

- **Repos**: Koupper = framework; CORTEX = producto. `cortex/dashboard` es git submodule — commit en submodule primero, luego bump pointer en cortex
- **gh CLI no está instalado** — usar `git push origin develop`
- **Build del dashboard**: `cd ~/develop/cortex/dashboard && npm run build && cp -r dist/. ~/.koupper/web/`
- **Reinicio completo**:
  1. `kill $(ps aux | grep 'octopus.jar' | grep -v grep | awk '{print $2}')`
  2. `nohup java -jar ~/.koupper/libs/octopus.jar > /tmp/octopus.log 2>&1 &`
  3. `until ss -tlnp | grep -q 9998; do sleep 2; done`
  4. `rm ~/.koupper/cache/compiled-scripts/*.bin`
  5. `java -Dfile.encoding=UTF-8 -jar ~/.koupper/libs/koupper-cli.jar run ~/.koupper/agents/CortexWebUiAgent.kts > /tmp/webui.log 2>&1 &`
- **Script cache** en `~/.koupper/cache/compiled-scripts/` — limpiar con `rm *.bin` si hay errores
- **Job input cache**: `~/.koupper/web-cache/jobs/{id}.json`
- **Worker trunca resultados grandes**: bug de Koupper, detail endpoint tiene fallback regex
- **`ScheduledSetup` es singleton object** — `registeredScripts` se resetea al reiniciar octopus. Después de restart hay que re-ejecutar `koupper run <script>` para volver a registrar el schedule
- **CortexWebUiAgent**: `post<Map<String,Any>>` falla el cast genérico — siempre usar `post<String>` + `fromJson()`
- **Nodos**: `~/.koupper/nodes/{host}.json` guarda estado

---

## Pendiente INMEDIATO — próxima sesión

### 1. Agregar `pipeline` a `@Scheduled` (framework)
Archivo: `koupper/octopus/src/main/kotlin/com/koupper/octopus/annotations/Scheduled.kt`
```kotlin
annotation class Scheduled(
    val rate: Long = 0L,
    val cron: String = "",
    val configId: String = "",
    val debug: Boolean = false,
    val delay: Long = 0L,
    val at: String = "",
    val pipeline: String = ""   // ← AGREGAR: "AgentB.kts > AgentC.kts"
)
```

### 2. Actualizar `enqueueJob()` en `ScheduledSetup.kt`
Cuando `scheduledParams["pipeline"]` no es blank, construir el `pipelineNext` JSON anidado igual que hace `HeartbeatAgent.dispatchPipeline()` y escribirlo en el job JSON.

### 3. Migrar agentes digest
- `RssFeedAgent.kts` → agregar `@Scheduled(cron = "0 8 * * *", pipeline = "SummarizerAgent.kts > TelegramNotifyAgent.kts")` + `@Logger(destination = "file:morning-digest-[yyyy-MM-dd]", level = "INFO")` + migrar `println` a `log.info {}`
- `SummarizerAgent.kts` → agregar `@Logger(destination = "file:morning-digest-[yyyy-MM-dd]", level = "INFO")` + migrar `println` a `log.info {}`
- `TelegramNotifyAgent.kts` → agregar `@Logger` + migrar `println` a `log.info {}`

### 4. Rebuild + deploy
```bash
cd ~/develop/koupper\ workspace/koupper && ./gradlew :octopus:fatJar -x test
cp octopus/build/libs/octopus-6.5.3.jar ~/.koupper/libs/octopus.jar
# restart octopus + webui
```

### 5. Commit ambos repos
- `koupper/develop`: `feat(scheduled): add pipeline param to @Scheduled annotation`
- `cortex/develop`: `feat(agents): migrate digest pipeline to @Scheduled + @Logger`

---

## Otros pendientes

- **Run script en nodo remoto**: SSH execution en NodeProvisionerAgent (`doRun`) + modal con credenciales
- **Gemma3 Ollama error**: `gemma3:12b` falla con 400 desde `localhost:11434` — investigar
- **extractAgentSchema recursion**: data classes anidadas (e.g. `List<FeedArticle>`) no se expanden
- **Worker truncation bug**: resultados grandes (>8KB) quedan incompletos en `.result.json`

---

## Archivos clave modificados sesión 17

- `~/develop/koupper workspace/koupper/octopus/src/main/kotlin/com/koupper/octopus/annotations/ScheduledSetup.kt`
- `~/.koupper/agents/CortexWebUiAgent.kts` (+ `~/develop/cortex/agents/`)

---

## Commits sesión 17

```
cortex/develop:
  5961b6a  fix(agents): fix Kotlin escape in agent-name inference regex

koupper/develop:
  ea09352  feat(scheduled): enqueue jobs to worker queue instead of in-process execution
  9899139  fix(scheduled): prevent exponential job feedback loop on worker re-execution
```
