# Scripting DX: Implicit Provider Functions

Elimina el boilerplate de los scripts Koupper inyectando funciones top-level de los providers automáticamente.

## Problema

Hoy, cada script necesita esto aunque solo quieras llamar a Docker:

```kotlin
import com.koupper.container.app
import com.koupper.providers.docker.DockerClient
import com.koupper.shared.annotations.Export

@Export
val setup: () -> Unit = {
    val docker = app.getInstance(DockerClient::class)
    docker.listContainers()
}
```

Para scripting rápido, este boilerplate es ruido. El usuario debería poder escribir:

```kotlin
docker().listContainers()
```

## Solución

Inyectar un **preámbulo** con funciones top-level antes de compilar cada script. El engine de scripting (`BasicJvmScriptingHost.eval()`) recibe el source modificado en vez del raw.

### Antes de la compilación

```
// --- PREÁMBULO INYECTADO ---
fun docker(): DockerClient = app.getInstance(DockerClient::class)
fun github(): GitHubClient = app.getInstance(GitHubClient::class)
fun memory(): MemoryProvider = app.getInstance(MemoryProvider::class)
fun ai(): AIServiceProvider = app.getInstance(AIServiceProvider::class)
// ... uno por cada provider registrado
// --- FIN PREÁMBULO ---

// Código del usuario tal cual
docker().ps()
```

## Implementación

### 1. Extender `ServiceProvider` (clase base)

Archivo: `koupper/providers/src/main/kotlin/com/koupper/providers/ServiceProvider.kt`

Agregar método opcional:

```kotlin
/**
 * Retorna pares (nombreFunción, códigoFuente) para funciones top-level
 * que se inyectarán en todos los scripts.
 *
 * Ejemplo:
 *   "docker" to "fun docker(): DockerClient = app.getInstance(DockerClient::class)"
 */
open fun topLevelFunctions(): Map<String, String> = emptyMap()
```

### 2. Cada provider expone su función

Ejemplo para Docker (`DockerServiceProvider`):

```kotlin
override fun topLevelFunctions(): Map<String, String> = mapOf(
    "docker" to """
        import com.koupper.providers.docker.DockerClient
        fun docker(): DockerClient = app.getInstance(DockerClient::class)
    """.trimIndent()
)
```

Ejemplo para GitHub (`GitHubServiceProvider`):

```kotlin
override fun topLevelFunctions(): Map<String, String> = mapOf(
    "github" to """
        import com.koupper.providers.github.GitHubClient
        fun github(): GitHubClient = app.getInstance(GitHubClient::class)
    """.trimIndent()
)
```

### 3. Colectar funciones en `Octopus.kt`

Archivo: `koupper/octopus/src/main/kotlin/com/koupper/octopus/Octopus.kt`

En `registerBuildInServicesProvidersInContainer()`, después de registrar providers, colectar las `topLevelFunctions()`:

```kotlin
private val providerPreamble: String by lazy {
    val functions = mutableListOf<String>()
    // Iterar providers registrados y colectar topLevelFunctions
    providersRegistry.getAllProviders().forEach { provider ->
        provider.topLevelFunctions().forEach { (name, code) ->
            functions.add(code)
        }
    }
    functions.joinToString("\n\n")
}
```

### 4. Inyectar preámbulo en `AnnotationsProcessor.kt`

Archivo: `koupper/octopus/src/main/kotlin/com/koupper/octopus/AnnotationsProcessor.kt`

En el resolver `"Export"`, antes de `backend.eval(diParams.sentence)`, prepender el preámbulo:

```kotlin
put("Export") { diParams, res ->
    var backend: ScriptingHostBackend? = null
    if (diParams.callable == null) {
        backend = ScriptingHostBackend(extraClasspath = resolveGradleBuildClasspath(File(diParams.scriptContext)))
        
        // --- INYECCIÓN DE PREÁMBULO ---
        val preamble = getProviderPreamble() // del paso 3
        val augmentedScript = if (preamble.isNotBlank()) {
            "$preamble\n\n${diParams.sentence}"
        } else {
            diParams.sentence
        }
        backend.eval(augmentedScript) // antes: backend.eval(diParams.sentence)
    }
    // ... resto igual
}
```

### 5. (Opcional) Atajo vía `ScriptingHostBackend.eval()`

Archivo: `koupper/shared/src/main/kotlin/com/koupper/shared/runtime/ScriptingHostBackend.kt`

Agregar un parámetro opcional `preamble: String = ""`:

```kotlin
fun eval(code: String, preamble: String = ""): Any {
    val source = if (preamble.isNotBlank()) "$preamble\n\n$code" else code
    val future = host.eval(source.toScriptSource(ScriptSourceKind.ktsFile), compilationConfig, evalConfig)
    // ... resto igual
}
```

## Backward Compatibility

- **Scripts viejos sin cambios**: Siguen compilando igual. Las funciones inyectadas solo agregan nombres al namespace, no modifican nada existente.
- **Scripts que usan `app.getInstance()`**: Siguen funcionando. El container no se toca.
- **Scripts con `@Export`**: Siguen funcionando. El preámbulo se inyecta antes del código del usuario, después de los imports que el usuario escriba (el orden no importa porque Kotlin permite declaraciones en cualquier orden).
- **Scripts con función propia llamada `docker()`**: Único edge case. El compilador de Kotlin lanza `ConflictingOverloadsError`. Soluciones:
  - Omisión automática: si el usuario ya declara `docker()` en su script, excluirla del preámbulo.
  - Documentación: listar nombres reservados por providers.
  - Namespace: prefijo como `_kpr_docker()`.

## Edge Cases

| Caso | Comportamiento |
|------|---------------|
| Script declara `fun docker()` | Error de compilación (nombre duplicado). Solución: detectar y excluir del preámbulo. |
| Provider no implementa `topLevelFunctions()` | No se inyecta nada de ese provider. |
| Dos providers registran el mismo nombre | Error de compilación. Solución: validar en colecta, úlimo wins o prefix. |
| Script sin `@Export` | El preámbulo se inyecta igual (no afecta). |
| Script usa `docker().ps()` sin import | Compila porque el preámbulo incluye el import necesario. |

## Nombres de funciones por provider (propuesta)

| Provider | Función | Return Type |
|----------|---------|-------------|
| Docker | `docker()` | `DockerClient` |
| GitHub | `github()` | `GitHubClient` |
| Memory | `memory()` | `MemoryProvider` |
| AI | `ai()` | `AIServiceProvider` |
| SSH | `ssh()` | `SSHServiceProvider` |
| Git | `git()` | `GitServiceProvider` |
| AWS S3 | `s3()` | `AwsS3ServiceProvider` |
| MCP | `mcp()` | `MCPClientProvider` |
| RSS | `rss()` | `RSSServiceProvider` |
| Telegram | `telegram()` | `TelegramServiceProvider` |
| n8n | `n8n()` | `N8NProvider` |
| DB | `db()` | `DBServiceProvider` |

## Criterios de aceptación

- [ ] `ServiceProvider.topLevelFunctions()` existe y retorna `Map<String, String>`
- [ ] Al menos 3 providers implementan el método (empezar con Docker, GitHub, Memory)
- [ ] Script sin imports, sin `@Export`, sin `app.getInstance()` funciona y puede llamar `docker().ps()`
- [ ] Script viejo con `@Export` + `app.getInstance()` sigue funcionando exactamente igual
- [ ] Script viejo y nuevo pueden convivir en el mismo workspace
- [ ] Error claro si hay naming collision entre preámbulo y script del usuario

## No incluido (scope futuro)

- Proveer estos mismos atajos como funciones globales también desde `koupper CLI` (ej: `koupper docker ps`)
- Generar automáticamente la documentación de funciones disponibles via `koupper provider functions`
