# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-23 (sesión 20 — cerrada)_

---

## Estado general

- **Koupper** (framework): `github.com:koupper-jvm/koupper` → `develop`, PR #169 y #170 merged
- **Koupper CLI**: `github.com:koupper-jvm/koupper-cli` → `develop`, pipelineNext mergeado
- **Koupper Docs**: `github.com:koupper-jvm/koupper-docs` → `main`, 4 commits hoy
- Todas las ramas feature mergeadas a develop ✅

---

## Repos y ramas activas

| Repo | Ruta local | Rama | Estado |
|---|---|---|---|
| koupper (framework) | `~/develop/koupper workspace/koupper` | `develop` | PR #169, #170 merged ✅ |
| koupper-cli | `~/develop/koupper workspace/koupper-cli` | `develop` | limpio ✅ |
| cortex | `~/develop/cortex` | `develop` | limpio ✅ |
| dashboard (submodule) | `~/develop/cortex/dashboard` | `main` | limpio ✅ |

---

## Lo que está construido

### Sesión 20 — Framework Assessment Fixes (completado parcial)

| Fix | Estado | PR |
|---|---|---|
| Provider tier system (CORE/COMMUNITY/EXPERIMENTAL) | ✅ Merged | #169 |
| gRPC bidirectional streaming | ✅ Merged | #170 |
| KSP/PSI replaces regex annotation extraction | ❌ Pendiente (6-8 semanas) | — |

#### Tier system detalle
- `ProviderTier.kt` enum con criterios de CI gate
- `ServiceProvider.tier()` default COMMUNITY
- 5 CORE: DB, File, Http, SSH, Logger
- 3 EXPERIMENTAL: AILlmOps, Vision, SpeechToText
- `ServiceProviderManager.listProvidersByTier()` para filtrado CI
- `ProviderTierConsistencyTest` valida asignaciones
- Fix: `KoupperTelemetry` compilación (deps OTel en `shared/build.gradle`)
- Fix: `TextMapGetter` type inference en `extractContext()`

#### gRPC streaming detalle (WIP)
- Branch: `feature/grpc-bidirectional-streaming`
- Protobuf plugin configurado en `build.gradle`
- `.proto` creado: `JobQueue` service con `StreamJobs` bidi streaming
- Dependencias gRPC agregadas al root `build.gradle`
- **Pendiente**: generar stubs, implementar servidor, tests

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

- **Assessment pendiente**: KSP/PSI migration (P0, 6-8 semanas) — planificar para sesión 21
- **Repos**: Koupper = framework; CORTEX = producto. `cortex/dashboard` es git submodule
- **Build del dashboard**: `cd ~/develop/cortex/dashboard && npm run build && cp -r dist/. ~/.koupper/web/`
- **Script cache**: `~/.koupper/cache/compiled-scripts/` — limpiar con `rm *.bin` si hay errores
- **Cooldown state**: `~/.koupper/heartbeat-state.json` — borrar entrada para forzar re-ejecución

---

## Pendiente próxima sesión

- [ ] **KSP/PSI migration** (P0 — reemplazar regex annotation extraction con Kotlin Symbol Processing)
  - Crear módulo `:annotation-processor` con KSP processor
  - Migrar `@Export`, `@Scheduled`, `@Pipeline` resolvers de regex a KSP
  - Mantener backward compatibility durante transición
  - Tests: verificar que KSP y regex producen mismos resultados para todos los providers
  - Estimación: 6-8 semanas de trabajo

---

## Assessment: completado parcial

| Item | Estado |
|---|---|
| 1. Provider tier system | ✅ |
| 2. gRPC bidirectional streaming | ✅ |
| 3. KSP/PSI replaces regex | ❌ (planificado sesión 21) |

---

## Commits sesión 20

```
koupper/develop:
  71aab68  feat(providers): add tier system (CORE/COMMUNITY/EXPERIMENTAL)
  0ace380  feat(grpc): add bidirectional streaming job queue
```
