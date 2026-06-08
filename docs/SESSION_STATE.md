# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-08 (sesión 11)_

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
| cortex | `~/develop/cortex` | `main` | limpio ✅ |

---

## Lo que está construido

### Fase 1–5 ✅ (sesiones 1–9)
Todas las fases completadas: base operativa, enterprise, edge, multi-tenant, marketplace.

### Sesión 10 ✅
Worker hardening, doctor, --retry/--purge/--logs, dashboard job management, MCPClientProvider SSE transport.

### Sesión 11 ✅

| Componente | Descripción |
|---|---|
| Live log streaming | SSE `GET /api/logs/{jobId}/stream`; `useLogStream` hook; `● LIVE` badge pulsante en LogViewer |
| `koupper pipeline submit` | Encola pipeline multi-step desde JSON; construye cadena `pipelineNext` anidada |
| `koupper pipeline status` | Muestra progreso por step (pending/running/done/failed/dead) |
| `emit()` evaluation | No procede para CORTEX agents (daemons persistentes con session logs estructurados) |

### Totales de tests
- koupper providers: 287 tests
- koupper-cli: 196 tests (+37 en sesión 11)

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

---

## Roadmap — COMPLETADO ✅

Todos los ítems del roadmap original están entregados. No hay pending técnico.

---

## Notas para retoma en frío

- **Separación estricta de repos**: Koupper = framework; CORTEX = producto. Cero mezcla en commits.
- **gh CLI no está instalado** en tdn-dell — usar `git push` directo
- `cortex/dashboard` es git submodule — commit en submodule primero, luego actualizar pointer en cortex
- `emit()` preamble = `println()` capturado por worker. CORTEX agents son daemons con logs propios — NO reemplazar con emit()
- Pipeline submit: `parseStepObjects`, `buildStepNextJson`, `buildFirstJobJson` son `internal` top-level en `PipelineCommand.kt`
- `computeTokenMetrics()` en `CortexWebUiAgent.kts` lee de `logs/cortex/cortex-session.log` — no romper esa ruta
- Pipeline view ya existe en `JobsPanel.tsx` (`PipelineGroup` component con dots, progreso, collapsible)
- `marketplaceCache` es `var` top-level en `CortexWebUiAgent.kts` — TTL 5 min
- `KOUPPER_AGENT_REGISTRY` env var sobreescribe URL del registry
- SSE transport MCP: `MCPServerConfig(transport = "sse", url = "http://host:port")` — `/sse` se agrega automáticamente
