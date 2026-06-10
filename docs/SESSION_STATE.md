# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-09 (sesión 15)_

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

### Fases 1–13 ✅ (sesiones 1–13)
Worker hardening, doctor, pipeline, SSE transport, multi-tenant, marketplace, edge nodes, dashboard UX overhaul — todo entregado.

### Sesión 14 ✅ — Setup wizard + Calendar UX + bugfixes críticos
Setup redirect fix, "ya configurado" screen, `/api/providers` POST fix, Calendar cron legible + click-to-view, marketplace badges, tab colors diferenciados, HeartbeatAgent logging.

### Sesión 15 ✅ — Chat UX + Nodes reconnect + persistencia

| Feature | Detalle |
|---|---|
| Delete per-session styled | Click × en historial → fila de confirmación inline con estilo magenta (`--accent-2`). Sin `confirm()` del browser. Sin botón global de papelera |
| Chat persistencia refresh | `chatOpen` guardado en localStorage. `PENDING_KEY` guarda `linesBeforeSend` al enviar mensaje. Al refrescar, si último mensaje es de 'user', retoma el polling automáticamente |
| refreshNodes() en AppContext | Mecanismo de `nodesTick` en App.tsx → fuerza re-fetch inmediato de `/api/nodes` |
| Reconnect nodo | Nuevo endpoint `GET /api/nodes/touch/{host}` actualiza `registeredAt=now + status=ready` en el JSON del nodo. Frontend llama touch → `refreshNodes()` → nodo verde inmediatamente |
| Run script en nodo | **PENDIENTE correcta implementación** — se removió botón porque ejecutaba localmente (no en el nodo remoto vía SSH). Implementación correcta requiere SSH |

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

## Notas para retoma en frío

- **Repos**: Koupper = framework; CORTEX = producto. `cortex/dashboard` es git submodule — commit en submodule primero, luego bump pointer en cortex
- **gh CLI no está instalado** — usar `git push origin develop`
- **Build del dashboard**: `cd ~/develop/cortex/dashboard && npm run build && rm -rf ~/.koupper/web/assets && cp -r dist/* ~/.koupper/web/`
- **Reinicio**: `bash ~/.koupper/bin/koupper-start.sh` — sourcea `~/.profile`, levanta octopus + todos los agentes
- **Env authority**: `~/.profile` es la fuente de verdad. `.env` es override del wizard
- `post<Map<String,Any>>` en Kotlin scripting falla el cast genérico — siempre usar `post<String>` + `fromJson()` para bodies JSON complejos
- `@Scheduled` NO funciona como daemon persistente cuando el script usa implicit vars — usar bash while loop
- HeartbeatAgent corre como bash loop con `nohup`
- Script cache en `~/.koupper/cache/compiled-scripts/` — limpiar con `rm *.bin` si hay errores de compilación
- CortexWebUiAgent sirve archivos estáticos de `~/.koupper/web/`
- **Nodos**: `~/.koupper/nodes/{host_con_puntos_reemplazados}.json` guarda estado. Endpoint `GET /api/nodes/touch/{host}` refresca timestamp
- **Scripts `@Export`** pueden recibir cualquier tipo Kotlin como input (data class, primitivo, etc.), no solo Map/List

---

## Commits clave sesión 15

```
dashboard/main:
  7a8c713  fix(nodes): remove broken Run script button
  21465bb  feat(nodes): run script shows dropdown (luego removido)
  c642a61  fix(chat): correct pending poll restore on refresh
  2124d1c  feat(chat): inline per-session delete confirmation + refresh persistence

cortex/develop:
  72d75e4  chore: bump dashboard — remove broken node run script
  dec83aa  chore: bump dashboard — node run script dropdown (luego removido)
  a64b18c  chore: bump dashboard — fix refresh persistence + deploy new build
  baa2e70  chore: bump dashboard — chat delete UX + refresh persistence + reconnect fix
```

---

## Pendientes / próximas sesiones

- **Run script en nodo remoto**: Implementar SSH execution correctamente en NodeProvisionerAgent (`doRun` action) + modal en dashboard con credenciales SSH + persistir creds en node JSON
- **Gemma3 Ollama error**: `gemma3:12b` falla con 400 desde `localhost:11434` — investigar
- **FileIndexerAgent `@Logger`**: Migrar logging igual que HeartbeatAgent
- **Docs tab en dashboard**: Sección de ayuda/documentación accesible desde sidebar
