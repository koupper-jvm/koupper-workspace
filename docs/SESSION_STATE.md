# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-09 (sesión 14 — continuación)_

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

### Fases 1–11 ✅ (sesiones 1–11)
Worker hardening, doctor, pipeline, SSE transport, multi-tenant, marketplace, edge nodes — todo entregado.

### Sesión 13 ✅ — Dashboard UX overhaul
Overview cards clickables, Jobs/Logs URL sync, Agent split panel + fullscreen IDE, POST /api/agent/{name}/save, tag pills, log viewer polling, env vars con currentValue, markdown en chat.

### Sesión 14 ✅ — Setup wizard + Calendar UX + bugfixes críticos

| Feature | Detalle |
|---|---|
| Setup wizard | Multi-step: welcome → LLM → cloud → telegram → done. Presets: LM Studio, Ollama, OpenAI, Custom. Guarda en `~/.koupper/.env` + `~/.koupper/telegram.json` |
| Setup redirect fix | Usaba `snapshot.providers` (siempre vacío). Cambiado a `GET /api/setup/status` que checa `.env` + env vars del proceso |
| Setup "ya configurado" | Al entrar con config existente muestra resumen de providers (nombre, modelo, URL, estado) + Telegram, opción de reconfigurar |
| Fix `/api/providers` POST | Cast genérico `as? List<Map<String,Any>>` fallaba silenciosamente. Cambiado a `post<String>` + `fromJson()` — ahora guarda correctamente en `.env` |
| Fix DSL endpoints | `method { GET }` / `method { POST }` eran DSL inválido → `get<Unit>` / `post<Map>` correcto |
| Fix `/api/setup/status` | Checa tanto `.env` como env vars del proceso (para providers en `~/.profile`) |
| Calendar cron legible | `cronToHuman()`: `0 8 * * *` → "Daily at 8:00 AM", `0 23 * * *` → "Daily at 11:00 PM" |
| Calendar legend mejorada | Muestra schedule `id` como nombre, pipeline agents como segunda línea, badge de estado separado |
| Calendar click-to-view | Click en schedule (leyenda o bloque del grid) → modal con código `.kts`. Pipelines con tabs por agente. Usa `/api/agent/{name}` existente |
| LAN provider fix | IP correcta: `192.168.1.8:1234` (era `.9`). Gemma-4-12b soporta tool calling |
| HeartbeatAgent logging | Migrado a `@Logger` + `GlobalLogger.log` — sin duplicados, sin ANSI codes |
| schedules.json sync | HeartbeatAgent escribe `~/.koupper/schedules.json` en cada ciclo → Calendar lo muestra |
| History cleanup | `~/.koupper/jobs/.history.jsonl` es la fuente de `/api/history` — truncar + reiniciar WebUI para limpiar |
| Marketplace install badge | `installedNames` set derivado de `snapshot.agents` — agentes ya instalados muestran "✓ Installed" en verde |
| Tabs diferenciados | Installed = cyan (`--accent`), Marketplace = magenta (`--accent-2`); count marketplace muestra `···` antes de cargar |
| Botón installed visible | `btn-installed` ahora full opacity con texto "✓ Installed", era invisible con solo icono + 0.4 opacity |
| Setup config display | Pantalla "ya configurado" usa CSS grid para alinear pos/nombre/modelo/url/badge en columnas |

---

## Arquitectura del LLM routing

```
Local (Gemma 3 12B — Ollama :11434)  prioridad 3
LAN   (Gemma-4-12B — LM Studio :1234, 192.168.1.8)  prioridad 2  ← soporta tool calling
Cloud (Qwen3 35B — Groq)  prioridad cloud
```

Authoritative config: `~/.profile` (sourced por `koupper-start.sh`).  
Wizard guarda en `~/.koupper/.env` — se aplica en el siguiente restart.

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

## Notas para retoma en frío

- **Repos**: Koupper = framework; CORTEX = producto. `cortex/dashboard` es git submodule — commit en submodule primero, luego bump pointer en cortex
- **gh CLI no está instalado** — usar `git push origin develop`
- **Reinicio**: `bash ~/.koupper/bin/koupper-start.sh` — sourcea `~/.profile`, levanta octopus + todos los agentes
- **Env authority**: `~/.profile` es la fuente de verdad. `.env` es override del wizard. Proceso hereda env del shell que lanzó octopus
- `emit()` = `println()` capturado por worker. CORTEX agents son daemons con session logs propios
- SSE log stream tiene bug `kotlin.Unit cannot be cast to String` en Grizzly — workaround: polling REST en `useLogStream`
- `post<Map<String,Any>>` en Kotlin scripting falla el cast genérico — siempre usar `post<String>` + `fromJson()` para bodies JSON complejos
- `@Scheduled` NO funciona como daemon persistente cuando el script usa implicit vars del scripting host (`home`, `env`, `emit`). Usar bash while loop en `koupper-start.sh`
- HeartbeatAgent corre como bash loop con `nohup`, lee `~/.koupper/heartbeat.md`, escribe `schedules.json`
- Script cache en `~/.koupper/cache/compiled-scripts/` — limpiar con `rm *.bin` si hay errores de compilación extraños tras editar `.kts`
- `computeTokenMetrics()` en `CortexWebUiAgent.kts` lee de `logs/cortex/cortex-session.log`
- Pipeline view en `JobsPage.tsx` — `job.pipelineStep` / `job.pipelineTotal` en las cards
- AppContext: `snapshot`, `nodes`, `chatOpen`, `selectedJob`, `voiceMuted`, `toggleMute`
- Dashboard pages: Overview, Jobs, Agents, Nodes, Calendar, Logs, Providers, Setup

---

## Commits clave sesión 14

```
cortex/develop:
  bf78ac7  chore: bump dashboard — agents tab UX fixes
  ebf6540  chore: bump dashboard — marketplace install state fix
  9a21655  chore: bump dashboard — setup config layout fix
  a25140b  chore: bump dashboard — calendar UX + setup config view
  0deb8c5  fix(webui): parse /api/providers body as String (cast genérico fallaba)
  f5ab6af  feat(webui): add setup wizard API endpoints + fix DSL syntax

dashboard/main:
  fb19ae2  fix(agents): marketplace tab count, differentiated tab colors, visible installed button
  6978ada  fix(agents): show installed badge for marketplace agents already deployed
  75c91dd  fix(setup): center done-ring, grid layout for config rows, position index
  613ae5b  feat(calendar): human-readable cron + click-to-view script modal + setup config view
  4d75c9e  fix(setup): use /api/setup/status for redirect instead of snapshot.providers
  6ea3195  feat(setup): multi-step setup wizard for first-run configuration
```

---

## Pendientes / próximas sesiones

- **Gemma3 Ollama error**: `gemma3:12b` falla con 400 desde `localhost:11434` — investigar
- **FileIndexerAgent `@Logger`**: Migrar logging igual que HeartbeatAgent
- **Docs tab en dashboard**: Sección de ayuda/documentación accesible desde sidebar
- **`@Scheduled` alternativa limpia**: Para agentes que NO usan implicit vars, explorar si funciona
</content>
</invoke>