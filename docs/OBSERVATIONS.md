# Observations & Technical Debt

Hallazgos del review general del workspace. Pendientes para que otro agente los evalúe y priorice.

## 1. Versiones inconsistentes en koupper-cli

`koupper-cli/build.gradle` usa versiones muy antiguas vs el engine:

| Dependencia | CLI | Engine (koupper) | Riesgo |
|-------------|-----|-------------------|--------|
| `kotlin-script-runtime` | **1.4.32** | 2.0.20 | Classpath conflict |
| `kotlinx-coroutines-core` | **1.4.2** | 1.9.0 | Behavioral mismatch |
| `snakeyaml` | **2.2** | 2.3 | Menor |
| `jackson-module-kotlin` | **2.17.0** | 2.17.2 | Menor |

**Acción:** Alinear versiones del CLI con las del engine, o extraer un BOM/version catalog compartido.

---

## 2. `printStackTrace()` en producción (~7 ocurrencias)

| Archivo | Línea |
|---------|-------|
| `orchestrator-core/.../JobsOrchestrator.kt` | 505, 1258 |
| `octopus/.../Octopus.kt` | 594, 603 |
| `octopus/.../utils/OutputUtils.kt` | 30 |
| `octopus/.../process/LocalAWSDeployer.kt` | 66, 68 |

`e.printStackTrace()` en producción no es estructurado, no tiene contexto, y no respeta niveles de log.

**Acción:** Reemplazar con `LoggerCore` del módulo `logging` (que ya existe y se usa en otras partes).

---

## 3. `TODO("Not yet implemented")` en código productivo

- `providers/.../files/TextFileHandlerImpl.kt:115` — método sin implementar
- `octopus/.../http/RouterMaker.kt:207` — método sin implementar

Si alguien invoca estos métodos, explota en runtime sin mensaje claro.

**Acción:** Implementar o lanzar `UnsupportedOperationException` con mensaje descriptivo, o eliminar el método si no se necesita.

---

## 4. `System.err.println` en vez del logger

- `shared/.../ScriptingHostBackend.kt:151` — diagnóstico de compilación
- `octopus/.../modules/Module.kt:45-46` — error de script
- `providers/.../notifications/NotificationsProvider.kt:75` — notificación

El módulo `logging` con `LoggerCore` existe y se usa en otras partes. Estos lugares lo bypassan.

**Acción:** Migrar a `LoggerCore` para consistencia.

---

## 5. Cobertura de tests concentrada en providers

| Módulo | Tests |
|--------|-------|
| `providers/` | ~50 tests |
| `octopus/` | 4 tests |
| `container/` | 2 tests |
| `shared/` | **0 tests** |

El corazón del sistema (scripting host, annotations processor, resolución de exports) tiene cobertura mínima.

**Acción:** Agregar tests unitarios para `ScriptingHostBackend`, `AnnotationsProcessor`, y `ScriptRunner`.

---

## 6. ServiceProvider sin ciclo de vida

```kotlin
abstract class ServiceProvider {
    abstract fun up()
}
```

No hay `down()`, `health()`, `validate()`. Si un provider falla silenciosamente en `up()`, no se detecta hasta que alguien intenta usarlo.

**Acción:** Evaluar si agregar métodos de ciclo de vida (`health()`, `down()`) es necesario para el caso de uso actual.

---

## 7. FatJar module-info exclusion frágil

`koupper-cli/build.gradle:36-38` excluye `module-info.class` del FatJar. `PHASE_9_DEBUG_GUIDE.md` documenta que hay un bug activo con K2 + ShadowJar.

**Acción:** Monitorear si el bug se resuelve en futuras versiones de Kotlin/Gradle. Mientras tanto, mantener la exclusión y el debug guide actualizados.

---

## 8. CI: smoke-linux corre vía PowerShell

`full-smoke-suite.yml` tiene `smoke-linux` que corre en Ubuntu pero ejecuta `pwsh ./examples/full-smoke-suite.ps1`. No hay un smoke test nativo de Linux (bash).

**Acción:** Opcional. Crear `full-smoke-suite.sh` como complemento nativo, o eliminar el job si no agrega valor vs Windows.

---

## 9. Paths hardcodeados (ya aplicado parcialmente)

Ver `docs/SCRIPTING_DX_IMPROVEMENTS.md` para el contexto completo. Los cambios a `docs/AGENT_RECEPTION.md`, `.koupper/helpers/list.kts`, y los test files con `KOUPPER_LLM_*` ya están hechos.

**Pendiente:** Verificar que ningún otro archivo tenga paths fijos del desarrollador original.

---

## 10. Scripting DX: boilerplate (especificado aparte)

Ver `docs/SCRIPTING_DX_IMPROVEMENTS.md` — propuesta completa de inyección de funciones top-level con implementación paso a paso y criterios de aceptación.
