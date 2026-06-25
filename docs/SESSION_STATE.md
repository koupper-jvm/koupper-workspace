# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-25 (sesión 22 — en progreso)_

---

## Estado general

- **Koupper** (framework): `github.com:koupper-jvm/koupper` → `develop`, PR #169-#174 merged
- **Koupper CLI**: `github.com:koupper-jvm/koupper-cli` → `develop`, pipelineNext mergeado
- **Assessment**: 3/3 items completados, regex legacy removido, ejemplos y docs actualizados
- **Koupper v7 Architecture**: Sandboxing, SSE, Hot Reloading y validación de HA implementados exitosamente.
- **Koupper v7 Install**: ✅ Funcional. FatJar (~300MB) construido e instalado correctamente a nivel SO.

---

## Repos y ramas activas

| Repo | Ruta local | Rama | Estado |
|---|---|---|---|
| koupper (framework) | `~/develop/koupper workspace/koupper` | `develop` | limpio ✅ |
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

#### Arquitectura final
```
KSP Processor (compile time)
  → lee @Export/@Scheduled/@Pipeline de fuentes Kotlin
  → genera koupper-exports.json

Runtime
  → KspMetadataReader lee JSON
  → extractExportFunctionSignature usa KSP metadata únicamente
  → Fails fast con error claro si KSP no está configurado
```

---

### Sesión 22 — Verificación de Instalación y Correcciones de Ejemplos

| Observación | Estado | Detalle |
|---|---|---|
| FatJar construcción | ✅ OK | `octopus:fatJar` completa en ~21s con cache. JAR funcional para instalación SO. |
| CLI version | ✅ v7.1.1 | `koupper -v` reporta `koupper cli 7.1.1` / `octopus engine 7.1.1` |
| Ejemplos KSP compatibles | ✅ Fixed | `sandbox_test.kts` y `sse_test.kts` usan `val` con `@Export` (no `fun`) |
| Working tree | ✅ Limpio | No hay cambios locales pendientes en ningún repo |

#### Notas técnicas sesión 22
- **FatJar (~300MB)**: Es el JAR de sistema operativo, contiene todas las dependencias (gRPC, protobuf, native libs). El `optimized` JAR (~2.2MB) es para proyectos web.
- **KSP requiere `@Export` en `val`**: `@Export fun name()` no funciona con KSP. Debe ser `@Export val name: () -> ReturnType = { ... }`.
- **Instalación funcional**: `install-workspace.kts` ejecuta `:octopus:fatJar`, despliega artefactos, genera shims en `~/.koupper/bin`, y el PATH funciona.
- **Ningún fix necesario**: Los ejemplos ya están corregidos en `develop`. No se requiere branch de fix.

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
- **Instalación**: Funcional, CLI v7.1.1 operativo
- **Próximos pasos potenciales**:
  1. Tag release `v7.0.0` / `v7.1.1` y changelog
  2. Ejecutar pipelineNext para generar release formal
  3. Validar E2E con `koupper run` sobre todos los ejemplos

---

## Pendiente próxima sesión

- [x] Validar flujos completos E2E con el nuevo CLI `koupper reload` (Hot Reload arreglado vía Singleton + ClassLoader)
- [x] Validar Process Sandbox y SSE scripts (Fallback de Reflexión implementado para bypass KSP en scripts dinámicos)
- [x] Preparar el tag release para `v7.0.0` (Versión bumps listos, documentación README actualizada y pusheados a origin/develop)
- [ ] Tag release y changelog (Ejecutar pipelineNext para generar release)
- [ ] Validar `koupper run` sobre todos los ejemplos del directorio `examples/`

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
koupper/feature/ksp-runtime-integration:
  4215877  feat(ksp): integrate KSP metadata into runtime extraction

koupper/feature/ksp-annotation-processor:
  baaccd2  feat(annotation-processor): add KSP foundation for @Export extraction

koupper/develop (post-merge):
  a6f80ac  chore: ignore koupper-vscode repo directory
  5085f5b  docs: mark v7 and script tests as completed
  1d34df0  docs: v7 architecture documentation and state update
```
