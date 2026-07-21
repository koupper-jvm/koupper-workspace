---
name: kotlin-jvm
description: Kotlin/JVM and Gradle conventions for Koupper. Auto-loads when editing .kt, .kts, or .gradle files to keep code consistent with project patterns.
paths: "**/*.kt,**/*.kts,**/*.gradle"
user-invocable: false
---

Kotlin/JVM conventions for this codebase (Kotlin 2.0.20, K2 compiler, Java 17):

**Gradle (Groovy DSL)**
- `DuplicatesStrategy.EXCLUDE` on all fatJar tasks
- Always exclude `module-info.class`, `META-INF/versions/*/module-info.class`, `META-INF/*.SF/DSA/RSA` from zipTree
- `tasks.register()` not `tasks.create()`

**Kotlin idioms**
- Coroutines: `kotlinx.coroutines 1.9.0` — prefer `suspend fun` over raw threads
- Serialization: Jackson 2.17.2 + `@JsonIgnoreProperties(ignoreUnknown = true)`, never manual JSON parsing
- Prefer `data class` for DTOs; `sealed class` for result types
- No `!!` unless you can prove null is impossible at that callsite

**Dependency injection**
- Use the custom `container` / `app` from `com.koupper.container` — not Spring, not Koin

**Script entrypoints**
- One `@Export`-annotated function per `.kts` file
- All infrastructure through Service Providers — no scattered SDK calls

**Testing**
- Kotest StringSpec + Mockk — `shouldBe`, `coEvery`, independent tests
- `failFast = true` applies — every test must stand alone

**K2 scripting**
- `getSymbol()` returns field or method (method wrapped as `FunctionN` proxy)
- Top-level `fun` in `.kts` compiles as JVM method, not field
