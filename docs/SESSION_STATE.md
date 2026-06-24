# Session State — IGLY CORTEX / Koupper
_Last updated: 2026-06-24 (sesión 21 — activa)_

---

## Estado general

- **Koupper** (framework): `github.com:koupper-jvm/koupper` → `develop`, PR #169 y #170 merged
- **Koupper CLI**: `github.com:koupper-jvm/koupper-cli` → `develop`, pipelineNext mergeado
- **Branch activa**: `feature/ksp-annotation-processor` (en progreso)

---

## Repos y ramas activas

| Repo | Ruta local | Rama | Estado |
|---|---|---|---|
| koupper (framework) | `~/develop/koupper workspace/koupper` | `feature/ksp-annotation-processor` | KSP foundation ✅ |
| koupper-cli | `~/develop/koupper workspace/koupper-cli` | `develop` | limpio ✅ |
| cortex | `~/develop/cortex` | `develop` | limpio ✅ |
| dashboard (submodule) | `~/develop/cortex/dashboard` | `main` | limpio ✅ |

---

## Lo que está construido

### Sesión 21 — KSP Foundation (en progreso)

| Fix | Estado | PR |
|---|---|---|
| Provider tier system (CORE/COMMUNITY/EXPERIMENTAL) | ✅ Merged | #169 |
| gRPC bidirectional streaming | ✅ Merged | #170 |
| KSP/PSI replaces regex annotation extraction | 🔄 Foundation lista | #171 (pending) |

#### KSP foundation detalle
- Módulo `:annotation-processor` creado con KSP 2.0.20-1.0.25
- `KoupperSymbolProcessor`: extrae `@Export` en tiempo de compilación
- Genera archivo JSON: `koupper-exports.json`
- Integrado en build de `:octopus` (plugin KSP + dependencia ksp)
- Unit tests: `KoupperSymbolProcessorTest` (inicialización + proceso vacío)

#### Qué falta para completar KSP/PSI migration
1. **Extender processor** para `@Scheduled` y `@Pipeline`
2. **Consumir metadata JSON** en `AnnotationsProcessor.kt` en lugar de regex
3. **Tests de paridad** KSP vs regex para todos los providers (48)
4. **Remover código regex** legacy una vez validado
5. **Documentar** la nueva arquitectura

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

- **Branch activa**: `feature/ksp-annotation-processor`
- **Para continuar KSP**:
  - Extender `KoupperSymbolProcessor` para procesar `@Scheduled` y `@Pipeline`
  - Modificar `AnnotationsProcessor.kt` para leer metadata JSON generada
  - Crear tests de paridad que comparen regex vs KSP para cada provider
- **Assessment**: 2/3 items completados, 1 en progreso (foundation lista)

---

## Pendiente próxima sesión

- [ ] Mergear PR #171 (KSP foundation)
- [ ] Extender KSP processor para `@Scheduled` y `@Pipeline`
- [ ] Modificar `AnnotationsProcessor.kt` para consumir metadata KSP
- [ ] Tests de paridad: KSP vs regex para todos los providers
- [ ] Remover código regex legacy

---

## Assessment: completado parcial

| Item | Estado |
|---|---|
| 1. Provider tier system | ✅ |
| 2. gRPC bidirectional streaming | ✅ |
| 3. KSP/PSI replaces regex | 🔄 Foundation lista |

---

## Commits sesión 21

```
koupper/feature/ksp-annotation-processor:
  baaccd2  feat(annotation-processor): add KSP foundation for @Export extraction
```
