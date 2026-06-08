# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-08 (sesión 10)_

---

## Estado general

- **CORTEX** vive en su propio repo: `git@github.com:Iglymx/cortex.git` → `~/develop/cortex/`
- **Koupper** (framework open-source): `git@github.com:koupper-jvm/koupper-workspace.git`
- Todos los repos están limpios y al día en sus ramas principales

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

### Sesión 10 — Worker hardening + Doctor + SSE ✅

| Componente | Descripción |
|---|---|
| `WorkerCommand` retry fix | `retryCount` embebido en job JSON — el dead-letter ahora funciona correctamente |
| `WorkerCommand --retry` | Mueve `.failed/` → queue para reintento manual |
| `WorkerCommand --purge dead\|failed` | Borra jobs de buckets, con filtro opcional de queue |
| `WorkerCommand --logs [jobId]` | Lista jobs recientes con estado o muestra log completo |
| `DoctorCommand` completo | Telegram, Ollama, CORTEX ports, koupperDir injectable; 23 tests |
| `POST /api/jobs/retry` | Mueve .failed/ → queue desde el dashboard |
| `POST /api/jobs/purge` | Borra .dead/ o .failed/ desde el dashboard |
| `JobsPanel` acciones | Botones "↩ Retry Failed" y "☠ Purge Dead" con feedback per-botón |
| `MCPClientProvider` SSE | Transport `sse` para @playwright/mcp y similares; `parseSseLines` + `resolveSseEndpoint` internal testables; 22 tests |

### Totales de tests
- koupper providers: 287 tests
- koupper-cli: 159 tests

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

## Próximos pasos (por impacto)

1. **Live log streaming en dashboard** — SSE desde `/api/logs/{jobId}/stream`; LogViewer se actualiza en tiempo real mientras el job corre
2. **`koupper pipeline` CLI** — `koupper pipeline submit pipeline.json` para pipelines multi-step desde CLI
3. **`emit()` vs `log()` en CortexAgent** — evaluar reemplazo de `logFile.appendText(...)` con `emit()` del preamble de octopus (menor)

---

## Notas para retoma en frío

- **Separación estricta de repos**: Koupper = framework; CORTEX = producto. Cero mezcla en commits.
- **gh CLI no está instalado** en tdn-dell — usar `git push` directo
- `retryCount` está embebido en el job JSON; `updateRetryCount()` es `internal` top-level en `WorkerCommand.kt`
- `parseSseLines()` y `resolveSseEndpoint()` son `internal` top-level en `MCPClientProvider.kt` — testables sin servidor
- `DoctorCommand` acepta `koupperDir: File` como constructor param — inyectable en tests
- `marketplaceCache` es `var` top-level en `CortexWebUiAgent.kts` — TTL 5 min
- `KOUPPER_AGENT_REGISTRY` env var sobreescribe URL del registry en CLI y dashboard
- SSE transport: `MCPServerConfig(transport = "sse", url = "http://host:port")` — el `/sse` se agrega automáticamente si no está en la URL
