# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-08 (sesión 13)_

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

| Feature | Detalle |
|---|---|
| Overview cards clickables | Navegan a `/jobs?filter=STATUS` via `useNavigate` |
| Jobs/Logs URL sync | `useSearchParams` sincroniza filtro con `?filter=` param |
| Agent split panel | "View" abre Info + Code tabs con ✕ close |
| Fullscreen code IDE | Code tab → overlay `position:fixed`, CodeMirror editable, Ctrl+S, Ctrl+F, Esc |
| `POST /api/agent/{name}/save` | Escribe `.kts` editado de vuelta al disco |
| Tag pills coloreados | Mapa de color por nombre: llm, mcp, docker, aws, voice, email, etc. |
| Log viewer fix | `useLogStream` reemplazó SSE rota → polling REST GET cada 2s |
| Env vars actuales | `currentValue` en snapshot via `System.getenv()`, secretos enmascarados |
| Markdown en chat | `react-markdown` renderiza respuestas de CORTEX; `stripMd()` limpia para TTS |

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

## Notas para retoma en frío

- **Repos**: Koupper = framework; CORTEX = producto. `cortex/dashboard` es git submodule — commit en submodule primero, luego bump pointer en cortex
- **gh CLI no está instalado** — usar `git push origin develop`
- `emit()` = `println()` capturado por worker. CORTEX agents son daemons con session logs propios — NO reemplazar
- SSE log stream (`/api/logs/{id}/stream`) tiene bug `kotlin.Unit cannot be cast to String` en Grizzly router — workaround: polling REST
- `computeTokenMetrics()` en `CortexWebUiAgent.kts` lee de `logs/cortex/cortex-session.log` — no romper esa ruta
- `marketplaceCache` es `var` top-level con TTL 5 min
- `KOUPPER_AGENT_REGISTRY` env var sobreescribe URL del registry
- Pipeline view en `JobsPage.tsx` — `job.pipelineStep` / `job.pipelineTotal` en las cards
- AppContext: `snapshot`, `nodes`, `chatOpen`, `selectedJob`, `voiceMuted`, `toggleMute`
- Dashboard pages: Overview, Jobs, Agents, Nodes, Calendar, Logs
- Vite dev en 5173, proxies `/api`, `/events`, `/voice` → 18083

---

## Commits clave esta sesión

```
cortex/develop:
  bc8f849  feat(dashboard): bump — markdown chat + TTS strip
  7c5a407  feat(agents): currentValue en envVars snapshot
  dc36a15  fix(dashboard): bump — log viewer polling
  0f63b5c  feat(cortex): POST /api/agent/{name}/save + bump

dashboard/main:
  8934fb8  feat(chat): react-markdown + stripMd para TTS
  59bc673  feat(agents): env var values con set/unset badges
  76824fd  fix(logs): SSE → polling REST
  b9b21d2  feat(dashboard): UX overhaul completo
```
