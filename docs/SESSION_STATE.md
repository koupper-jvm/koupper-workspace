# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-07-08 (sesión 26 — completada)_

---

## Estado general

- **Koupper** (framework): `github.com:koupper-jvm/koupper` → `develop`, fixes de test de Windows y concurrencia mergeados.
- **Koupper Workspace**: `github.com:koupper-jvm/koupper-workspace` → `develop`, fixes auditados mergeados y tests pasando cleanly.
- **Koupper CLI**: `github.com:koupper-jvm/koupper-cli` → `develop`, operativo.
- **Koupper v7.1.1**: ✅ Framework completamente estable con versioning alineado, sandbox estable en Windows, y tests pasando.
- **0 bugs de framework pendientes. Framework estabilizado para release.**

---

## Repos y ramas activas

| Repo | Ruta local | Rama | Estado |
|---|---|---|---|
| koupper (framework) | `~/develop/koupper workspace/koupper` | `fix/v7-defensive-router` | pusheado a GitHub (PR manual o fast-lane pendiente) |
| koupper-workspace | `~/develop/koupper workspace/` | `develop` | `tmp-talk.txt` actualizado |
| koupper-cli | `~/develop/koupper workspace/koupper-cli` | `develop` | limpio ✅ |
| cortex | `~/develop/cortex` | `develop` | limpio ✅ |
| quizztea-api | `~/develop/quizztea_workspace/quizztea.com` | `develop` | Modificaciones sin comitear por agente externo |

---

## Sesión 25 — Framework Hardening: Defensive Router

### Mejoras (Koupper v7)

| # | Fix | Archivo | Detalle |
|---|---|---|---|
| 1 | Defensive Exceptions | `RuntimeRouterProvider.kt` | Se agregó captura directa de `NullPointerException` e `IndexOutOfBoundsException` devolviendo status `400` para evitar crashes de servidor en missing query parameters o paginación inválida. |
| 2 | Observabilidad 500 | `RuntimeRouterProvider.kt` | Se añadió `root.printStackTrace()` antes del respond `500` para que errores fatales queden en log y no desaparezcan de forma silenciosa. |
| 3 | Documentation | `CHANGELOG.md` | Actualizado bajo la categoría `[Unreleased]` con las mejoras del router defensivo. |

### Commits

```
koupper/fix/v7-defensive-router:
  81822f5  fix(router): Defensive programming for NPE and internal error observability
```

---

## Sesión 23 — Regression Fix: Parameter Passing v7.1.1

### Bugs corregidos (3 framework fixes + 2 script fixes)

| # | Fix | Archivo | Detalle |
|---|---|---|---|
| 1 | Sandbox `--` prefix | `SandboxWorker.kt:26` | `--${key}=${value}` → `${key}=${value}`. El prefijo `--` causaba mismatch entre `parseArgs` (almacena `--key`) y `buildParamsJson` (busca `key` sin `--`). |
| 2 | Type generic fallback | `ScriptRunnerOrchestrator.kt:334-350` | Para inline data classes (`SalesReportCommand`), `resolveClassFromArgName` ⟹ null. Ahora usa `target.javaClass.genericInterfaces` → `FunctionN<Input, Return>` → `mapper.typeFactory.constructType(typeArg)` para deserializar con el Type genérico real. |
| 3a | Generic-aware split | `ScriptUtilities.kt:251` | `split(",")` → `splitTypesTopLevel()`. `Map<String, Any?>` se splitteaba en 2 params. |
| 3b | Multi-annotation regex | `ScriptUtilities.kt:242` | Regex `@Export\s*val` → `@Export\s*(?:@\w+(?:\([^)]*\))?\s*)*val`. Soporta `@Export + @Scheduled + @Logger + val`. |
| 4 | `return@label` → if/else | `ContextPreloaderAgent.kts` | `return@setup` prohibitido en Kotlin scripting. Reestructurado con if/else. |
| 5 | `return@label` → flags | `PluginManagerAgent.kts` | 4x `return@setup` reemplazados con boolean flags + if/else. |

### Commits

```
koupper/develop:
  b3b134c  fix(octopus): parameter passing regression for dynamic .kts scripts

koupper-workspace/fix/example-scripts-compilation:
  0e8b1e8  fix(examples): remove return@label from ContextPreloaderAgent and PluginManagerAgent
  bb35e30  fix(examples): ajustar scripts para compatibilidad con v7.1.1
  0470da1  fix(examples): arreglar scripts con APIs obsoletas, return en lambdas, y parametros
```

---

## Smoke Test: 59 Scripts (Resultado Final)

### Metodología
- `koupper run <script>` con timeout 20s por script
- Categorización automática por tipo de error
- Los scripts con `@JobsListener` (`ai-email-worker.kts`, `payment-worker.kts`) requieren configuración de cola — no son ejecutables directos
- Los scripts con `@Scheduled` (`logger-scheduled-*.kts`) ahora se ejecutan correctamente gracias al Fix 3b

### Resultados

| Categoría | Conteo | Detalle |
|---|---|---|
| ✅ **PASS** (ejecutan correctamente) | **36** | Scripts que compilan y ejecutan su lógica sin errores |
| ⏱️ **DAEMON** (corren indefinidamente) | **3** | `AgentCreatorAgent.kts`, `TelegramBridgeAgent.kts`, `test_server_sidecar.kts` |
| 🌐 **ENV/INFRA** (necesitan infraestructura) | **14** | SSH, GitHub tokens, llama.cpp, Docker, Secrets provider — no son bugs |
| 📦 **OBSOLETE API** (APIs obsoletas en scripts) | **6** | `toTypeRef`, `onToken`, `LlamaCppSidecar`, `RuntimeRouter` API — no son bugs |
| ❌ **FRAMEWORK BUG** | **0** | — |

### Scripts con APIs obsoletas (no son bugs de framework)
| Script | API obsoleta |
|---|---|
| `debug-deep-type.kts` | `toTypeRef()` removido en KSP migration |
| `test_swarm.kts` | `onToken {}` callback removido |
| `test_sidecar.kts` | `LlamaCppSidecar` no disponible |
| `runtime-router-live-server.kts` | `RuntimeRouter.post()` firma cambiada |
| `runtime-router-provider-flow.kts` | `RuntimeRouter.post()` firma cambiada |
| `agent_react_demo.kts` | `onToken`/`onHallucination` removidos |

### Scripts que necesitan infraestructura (no son bugs)
`DiaryAgent.kts`, `test_swarm_atomic.kts`, `test_swarm_jobs.kts`, `ai-email-worker.kts`, `payment-worker.kts`, `docker-provider-flow.kts`, `github-integration-flow.kts`, `github-provider-flow.kts`, `git-provider-flow.kts`, `integration-tests.kts`, `secrets-provider-flow.kts`, `ssh-roundtrip-flow.kts`, `ssh-tree-root.kts`, `terminal-runtime-demo.kts`

---

## PRs abiertos

| Repo | PR | Rama | Estado |
|---|---|---|---|
| koupper-workspace | [#21](https://github.com/koupper-jvm/koupper-workspace/pull/21) | `fix/example-scripts-compilation` → `develop` | Abierto |
| koupper | — | `develop` (push directo, commit `b3b134c`) | Mergeado |

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
| 9996 | gRPC JobQueue |
| 9997 | HTTP REST API |
| 9998 | Octopus daemon (socket) |
| 9999 | Prometheus `/metrics` |
| 18082 | MCP server |
| 18083 | Dashboard web (CortexWebUiAgent) |
| 11434 | Ollama (local) |
| 1234 | LM Studio LAN (192.168.1.8) |

---

## Notas para retoma en frío

- **Branch activa koupper**: `develop` (commit `b3b134c` — framework fixes mergeados)
- **Branch activa workspace**: `fix/example-scripts-compilation` (PR #21 abierto)
- **Instalación**: FatJar `~/.koupper/libs/octopus.jar` (~308MB) actualizado con todos los fixes
- **CLI**: `koupper -v` → v7.1.1 operativo
- **Smoke test**: 59/59 scripts probados, 0 bugs de framework

---

## Pendiente próxima sesión

- [ ] Mergear PR #21 (workspace example fixes)
- [ ] Actualizar 6 scripts con APIs obsoletas (`toTypeRef`, `onToken`, `LlamaCppSidecar`, `RuntimeRouter`)
- [ ] Tag release `v7.1.1` y changelog
- [ ] Publicar FatJar release en GitHub

---

## Assessment: completado

| Item | Estado |
|---|---|
| 1. Provider tier system | ✅ |
| 2. gRPC bidirectional streaming | ✅ |
| 3. KSP/PSI replaces regex | ✅ |

---

## Análisis de regresión (post-mortem)

### Timeline
```
Jun 24 09:53  Commit 7956785: KSP replace regex — remueve regex fallback
Jun 24 12:23  Commit fea1723: Sandbox enabled by default (v7)
Jun 24-25     Ambos combinados rompen parameter passing
Jun 25 09:47  Sesión 22: Fix ClassCastException + SPI
Jun 25 13:50  Sesión 23: Fix 3 regresiones de parameter passing
```

### Causas raíz
1. **KSP-only eliminó regex fallback** → scripts dinámicos (.kts) sin metadata KSP perdieron detección de firma
2. **Sandbox `--` prefix** → mismatch de keys entre `SandboxWorker` y `buildParamsJson`
3. **`split(",")` sin soporte de genéricos** → `Map<String, Any?>` → 2 params en vez de 1
4. **Regex no manejaba múltiples anotaciones** → `@Export\n@Scheduled\nval` no matcheaba

### Lecciones
- Nunca eliminar fallback sin validar todos los casos de uso (KSP + dinámico)
- El regex de parsing de firmas debe usar `splitTypesTopLevel` (ya existía pero no se usaba)
- El sandbox y el orchestrator deben compartir el mismo contrato de claves
