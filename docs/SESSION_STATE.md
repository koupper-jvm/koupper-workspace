# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-24 (sesión 21 — activa)_

---

## Estado general

- **Koupper** (framework): `github.com:koupper-jvm/koupper` → `develop`, PR #169, #170 merged, #171 y #172 ready
- **Koupper CLI**: `github.com:koupper-jvm/koupper-cli` → `develop`, pipelineNext mergeado
- **Branch activa**: `feature/ksp-runtime-integration` (listo para merge)

---

## Repos y ramas activas

| Repo | Ruta local | Rama | Estado |
|---|---|---|---|
| koupper (framework) | `~/develop/koupper workspace/koupper` | `feature/ksp-runtime-integration` | KSP integration ✅ |
| koupper-cli | `~/develop/koupper workspace/koupper-cli` | `develop` | limpio ✅ |
| cortex | `~/develop/cortex` | `develop` | limpio ✅ |
| dashboard (submodule) | `~/develop/cortex/dashboard` | `main` | limpio ✅ |

---

## Lo que está construido

### Sesión 21 — KSP Integration (completado)

| Fix | Estado | PR |
|---|---|---|
| Provider tier system (CORE/COMMUNITY/EXPERIMENTAL) | ✅ Merged | #169 |
| gRPC bidirectional streaming | ✅ Merged | #170 |
| KSP/PSI replaces regex annotation extraction | ✅ Integration lista | #172 (ready) |

#### KSP integration detalle
- KSP processor extendido para `@Scheduled` y `@Pipeline`
- `KspMetadataReader`: lector runtime para metadata JSON
- `extractExportFunctionSignature`: usa KSP primero, regex como fallback
- Parity tests: KSP vs regex para simple, param, complex, multi-annotation
- KSP genera `koupper-exports.json` con exports, scheduled, pipelines

#### Arquitectura actual
```
KSP Processor (compile time)
  → lee @Export/@Scheduled/@Pipeline de fuentes Kotlin
  → genera koupper-exports.json
  
Runtime
  → KspMetadataReader lee JSON
  → extractExportFunctionSignature usa KSP metadata primero
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
