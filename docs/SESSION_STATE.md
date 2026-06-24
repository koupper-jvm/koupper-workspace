# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-24 (sesión 21 — cerrada)_

---

## Estado general

- **Koupper** (framework): `github.com:koupper-jvm/koupper` → `develop`, PR #169-#173 merged
- **Koupper CLI**: `github.com:koupper-jvm/koupper-cli` → `develop`, pipelineNext mergeado
- **Assessment**: 3/3 items completados, regex legacy removido

---

## Repos y ramas activas

| Repo | Ruta local | Rama | Estado |
|---|---|---|---|
| koupper (framework) | `~/develop/koupper workspace/koupper` | `develop` | PR #169-#173 merged ✅ |
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
  → Fallback a regex si KSP no está disponible
```

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

- **Branch activa**: `feature/ksp-runtime-integration` (listo para merge)
- **Para completar**: Merge PR #172, luego remover código regex legacy
- **Assessment**: 3/3 items completados

---

## Pendiente próxima sesión

- [ ] Mergear PR #172 (KSP runtime integration)
- [ ] Remover código regex legacy una vez validado en producción
- [ ] Performance benchmark: KSP vs regex extraction

---

## Assessment: completado

| Item | Estado |
|---|---|
| 1. Provider tier system | ✅ |
| 2. gRPC bidirectional streaming | ✅ |
| 3. KSP/PSI replaces regex | ✅ |

---

## Commits sesión 21

```
koupper/feature/ksp-runtime-integration:
  4215877  feat(ksp): integrate KSP metadata into runtime extraction
  
koupper/feature/ksp-annotation-processor:
  baaccd2  feat(annotation-processor): add KSP foundation for @Export extraction
```
