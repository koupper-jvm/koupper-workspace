# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-25 (sesión 22 — completada)_

---

## Estado general

- **Koupper** (framework): `github.com:koupper-jvm/koupper` → `develop`, fixes pusheados
- **Koupper CLI**: `github.com:koupper-jvm/koupper-cli` → `develop`, pipelineNext mergeado
- **Assessment**: 3/3 items completados, regex legacy removido, ejemplos y docs actualizados
- **Koupper v7 Architecture**: Sandboxing, SSE, Hot Reloading y validación de HA implementados.
- **Koupper v7 Install**: ✅ Funcional. FatJar (~300MB) con fixes de framework.

---

## Repos y ramas activas

| Repo | Ruta local | Rama | Estado |
|---|---|---|---|
| koupper (framework) | `~/develop/koupper workspace/koupper` | `develop` | limpio ✅ (commit `1af35d6`) |
| koupper-cli | `~/develop/koupper workspace/koupper-cli` | `develop` | limpio ✅ |
| cortex | `~/develop/cortex` | `develop` | limpio ✅ |
| dashboard (submodule) | `~/develop/cortex/dashboard` | `main` | limpio ✅ |

---

## Lo que está construido

### Sesión 21 — KSP Integration + Regex Removal (completado)

| Fix | Estado | PR |
|---|---|---|
| Provider tier system (CORE/COMMUNITY/EXPERIMENTAL) | ✅ Merged | #169 |
| gRPC bidirectional streaming | ✅ Merged | #170 |
| KSP/PSI replaces regex annotation extraction | ✅ Completo | #171, #172, #173 |

#### KSP integration detalle
- `:annotation-processor` module con KSP 2.0.20-1.0.25
- `KoupperSymbolProcessor`: extrae `@Export`, `@Scheduled`, `@Pipeline`
- `KspMetadataReader`: lector runtime para metadata JSON
- `extractExportFunctionSignature`: **KSP único camino** (regex removido)
- KSP genera `koupper-exports.json` con exports, scheduled, pipelines
- ~40 líneas de regex legacy eliminadas

---

### Sesión 22 — Fixes de Framework v7.1.1

| Fix | Archivo | Detalle |
|---|---|---|
| `ClassCastException: Unit → String` | `SandboxWorker.kt` | Cambiado `runFromScriptFile<String>` a `runFromScriptFile<Any?>` |
| `ClassCastException: Unit → String` | `HttpApiServer.kt` | Cambiado ambos `<String>` a `<Any?>` con manejo de `Unit`/null |
| SPI Services faltantes en JAR | `octopus.jar` | Agregados `META-INF/services/com.koupper.providers.ServiceProvider` y `providers-catalog.json` |

#### Resultado del fix
- Scripts que retornan `Unit` (ej: `GreetingAgent.kts`, `FileOrganizerAgent.kts`, `SysMonitorAgent.kts`) ya no crashean con `ClassCastException`.
- El sandbox ejecuta correctamente sin error de `No ServiceProviders discovered via SPI`.

#### Scripts probados y funcionando (post-fix)
| Script | Estado |
|---|---|
| `examples/agents/GreetingAgent.kts` | ✅ Funciona (retorna `kotlin.Unit`) |
| `examples/agents/CodeReviewAgent.kts` | ✅ Funciona |
| `examples/agents/FileOrganizerAgent.kts` | ✅ Funciona |
| `examples/agents/HeartbeatAgent.kts` | ✅ Funciona |
| `examples/agents/PortScannerAgent.kts` | ✅ Funciona |
| `examples/agents/SysMonitorAgent.kts` | ✅ Funciona |

#### Scripts con errores NO relacionados al framework (errores de scripts)
| Script | Error | Causa |
|---|---|---|
| `agent_react_demo.kts`, `omega_researcher.kts` | `Unresolved reference 'onToken'` | API obsoleta en scripts |
| `ContextPreloaderAgent.kts`, `PluginManagerAgent.kts` | `'return' is prohibited here` | `return` en lambdas (Kotlin scripting) |
| `FileIndexerAgent.kts` | `No hay 'invoke' con aridad 0` | Firma incompatible con KSP |
| `FileWatcherAgent.kts` | `Unresolved reference 'watcher'` | API obsoleta en script |

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
| 18085 | KnowledgeQueryAgent |
| 18086 | MasterKnowledgeAgent |
| 11434 | Ollama (local) |
| 1234 | LM Studio LAN (192.168.1.8) |

---

## Notas para retoma en frío

- **Branch activa**: `develop` (limpia, todo mergeado)
- **Assessment**: 3/3 items completados
- **Instalación**: Funcional, CLI v7.1.1 operativo con fixes de framework
- **Commits recientes koupper/develop**: `1af35d6` — fix(octopus): support Any? return type in sandbox and HTTP API

---

## Pendiente próxima sesión

- [ ] Arreglar scripts de `examples/` con errores de sintaxis (`return` en lambdas, APIs obsoletas)
- [ ] Validar E2E completo sobre TODOS los scripts del directorio `examples/`
- [ ] Tag release `v7.1.1` y changelog

---

## Assessment: completado

| Item | Estado |
|---|---|
| 1. Provider tier system | ✅ |
| 2. gRPC bidirectional streaming | ✅ |
| 3. KSP/PSI replaces regex | ✅ |

---

## Commits sesión 21-22

```
koupper/develop:
  1af35d6  fix(octopus): support Any? return type in sandbox and HTTP API
  761c441  (otros cambios remotos)
  37eeb17  fix(octopus): support Any? return type in sandbox and HTTP API
  a6f80ac  chore: ignore koupper-vscode repo directory
```
