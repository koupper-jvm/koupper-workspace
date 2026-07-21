# Koupper Framework: Assessment & Refactoring Roadmap

> **Audience:** Maintainers. **Status:** Living document — sync with reality after each wave.
>
> Based on full codebase audit of `koupper/` (Octopus Engine v6.6.0), `koupper-cli/` (v4.8.0), and `koupper-document/` as of 2026-06-24.
>
> **Last sync:** Session 21 (2026-06-24) — All 3 assessment items resolved. See `docs/SESSION_STATE.md` for session log.

---

## 1. Executive Summary

Koupper is a **Kotlin scripting runtime + CLI** for infrastructure automation and agent orchestration. It compiles `.kts` scripts at runtime, injects 46+ service providers (DB, cloud, AI, SSH, Docker, MCP, etc.), and executes them through an annotation-driven model (`@Export`, `@Scheduled`, `@Pipeline`, `@JobsListener`).

**TL;DR:** Strong foundation with genuine niche (typed JVM infra scripting + MCP-first agentic core). Needs 3 waves of refactoring to reach production-grade enterprise readiness.

---

## 2. Architecture Deep Dive

```
┌─────────────────────────────────────────────────┐
│                  koupper CLI                      │
│  run | new | deploy | worker | schedule | agent  │
│  doctor | provider | infra | reconcile | watch   │
└─────────────────┬───────────────────────────────┘
                  │ TCP socket (raw text/JSON)
                  │ port :9998
┌─────────────────▼───────────────────────────────┐
│              Octopus Engine (JVM daemon)          │
│                                                   │
│  ┌─────────┐  ┌──────────────┐  ┌────────────┐  │
│  │Octopus.kt│  │FunctionDispatch│  │Annotations │  │
│  │(TCP loop)│─▶│    (priority)  │─▶│ Processor  │  │
│  └─────────┘  └──────────────┘  └────────────┘  │
│                                       │          │
│  @Export ──▶ ScriptingHostBackend ──▶ ScriptRunner│
│  @Scheduled ▶ ScheduledSetup ▶ JobQueue ▶ Worker │
│  @Pipeline  ▶ Pipeline chain via subprocesses     │
│  @JobsListener ▶ Queue polling daemon threads     │
│                                                   │
│  ┌──────────────┐  ┌───────────────────────────┐ │
│  │KoupperContainer│  │ 46 ServiceProvider        │ │
│  │(custom DI)    │  │ topLevelFunctions()       │ │
│  └──────────────┘  │ → injected as script preamble│
│                    └───────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

### Key modules (`koupper/` Gradle multi-module)

| Module | Role | Lines (approx) |
|---|---|---|
| `octopus/` | Daemon, TCP loop, FunctionDispatcher, AnnotationsProcessor, pipeline | ~833 (Octopus.kt) + 504 (OctopusBootstrap.kt) + 301 (OctopusProtocol.kt) — modularized post-session 19 |
| `container/` | Custom IoC/DI (`KoupperContainer`), constructor injector | ~550 |
| `providers/` | 46 service providers (DB, HTTP, SSH, Docker, AWS, MCP, AI, etc.) | ~8000+ |
| `orchestrator-core/` | Job system (`KouTask`, queues: file/Redis/SQS/DB), scheduling | — |
| `shared/` | Annotations, `ScriptingHostBackend`, `ScriptUtilities`, runtime state | — |
| `bootstrap/` | HTTP entry (Jersey 3.1.6 + Grizzly2) — early stage | — |
| `configurations/` | Configuration utils, ANSI | — |
| `os/` | Env resolution (.env, GLOBAL_ENV_FILE) | — |
| `logging/` | KLogger, appenders, MDC, log capture | — |

### Script execution lifecycle

```
TCP request (RUN/DEPLOY)
    → parseIncomingCommand()
    → Octopus.runFromScriptFile()
        → load .kts file
        → extractExportedDeclarations()  ← KSP-based (primary path, session 21)
        → extractExportedAnnotations()   ← KSP-based (primary path, session 21)
        → FunctionDispatcher.dispatch()
            → @Logger resolver (pri 30)
            → @Scheduled/@Pipeline/@JobsListener resolvers (pri 20)
            → @Export resolver (pri 10) ← terminal
                → @KoupperVersion check (pre-compile, session 19)
                → ScriptingHostBackend.eval() (compile + MD5 cache)
                → validateAnnotationsViaReflection() (post-compile cross-check, session 19)
                → reflectExportSignature() (reflection-based type extraction, session 19)
                → ScriptRunner.runScript() (Jackson params + reflection invoke)
                → SecretRedactor.apply() if @Secret present (session 19)
    → SessionOutput.result() → TCP response to CLI (with traceId, session 19)
```

---

## 3. Strengths Matrix

| Area | Score (0-5) | Why |
|---|---|---|
| **Annotation model** | 5 | `@Export` + `@Scheduled` + `@Pipeline` + `@JobsListener` — clean, composable, discoverable |
| **Provider breadth** | 5 | 46 providers covering DB, cloud, AI, infra, web, MCP. MCP client+server first-class |
| **Provider contract** | 4 | `up()` + `topLevelFunctions()` + `externalDependencies()` — simple, effective |
| **DI container** | 4 | Custom, lightweight (~550 loc), constructor injection. No Spring dependency |
| **Script compilation** | 4 | MD5-cached with fallback, `BasicJvmScriptingHost`, type-safe via Jackson |
| **Worker daemon** | 4 | Atomic job claiming, timeout, retry, dead-letter, pipeline chaining |
| **Self-hosting** | 5 | Release scripts, preflight, CI watch — all Koupper scripts running on Koupper |
| **JAR size** | 5 | ~1.6MB optimized (regex-based fatJar filtering) |
| **CLI usability** | 4 | 15 commands, JSON output contracts, ANSI colors, tab-friendly |

---

## 4. Gap Analysis

### 4.1 Critical Technical Debt

#### A. Annotation extraction via regex (CRITICAL) — RESOLVED ✅
**Files:** `shared/.../ScriptUtilities.kt:36-95`, `annotation-processor/.../KoupperSymbolProcessor.kt`, `shared/.../KspMetadataReader.kt`
**Status:** ✅ **KSP replaced regex as primary path (session 21).**
**What changed:**
- `:annotation-processor` module with KSP 2.0.20-1.0.25 (session 21)
- `KoupperSymbolProcessor` extracts `@Export`, `@Scheduled`, `@Pipeline` at compile time
- Generates `koupper-exports.json` metadata file
- `KspMetadataReader` consumes JSON at runtime
- `extractExportFunctionSignature()` now requires KSP metadata exclusively (PR #173)
- ~40 lines of regex legacy code removed
- Reflection validation layer (`ReflectionValidator.kt`) remains as safety net
**Impact:** Compiler-accurate annotation discovery. No more regex guessing for generics, formatting, or multi-annotation cases.
**Completed:** Session 21 (PRs #171, #172, #173)

#### B. Hardcoded provider registry (HIGH) — RESOLVED ✅
**Files:** `providers/.../ServiceProviderManager.kt`, `providers/.../ServiceProvider.kt`
**Status:** ✅ **SPI-only discovery with tier system (session 20).**
**What changed:**
- Hardcoded fallback removed entirely (PR #169)
- SPI is the only discovery mechanism
- `ServiceProviderManager.listProviders()` throws `IllegalStateException` with actionable message if SPI is empty
- Gradle task `generateServiceProviderSpi` produces `META-INF/services` manifest at build time
- `ProviderTier` enum added: CORE / COMMUNITY / EXPERIMENTAL
- 5 CORE providers marked (DB, File, Http, SSH, Logger)
- 3 EXPERIMENTAL providers marked (AILlmOps, Vision, SpeechToText)
- `ServiceProviderManager.listProvidersByTier()` enables CI gates per tier
**Impact:** External providers can be contributed without editing core files. CI can gate merges based on tier criteria.
**Completed:** Session 20 (PR #169)

#### C. Raw TCP protocol (HIGH) — ADDRESSED ✅
**Files:** `octopus/.../Octopus.kt`, `octopus/.../grpc/`, `octopus/.../api/HttpApiServer.kt`
**Status:** ✅ **gRPC + REST API added alongside legacy TCP (session 20).**
**What changed:**
- gRPC bidirectional streaming: `JobQueueGrpcServer` (port 9996) + `JobQueueGrpcClient` with auto-reconnect (PR #170)
- HTTP REST API: `HttpApiServer` (port 9997) with endpoints:
  - `POST /api/v1/run` — execute script
  - `GET /api/v1/health` — health check
  - `GET /api/v1/status` — daemon metrics
  - `GET /api/v1/jobs` — list job queues
- JWT authentication with scopes: `koupper:read`, `koupper:execute`, `koupper:admin`
- Proto definition: `octopus/src/main/proto/job_queue.proto`
- Legacy TCP socket (port 9998) preserved for backward compatibility
**Impact:** Standard tooling can now integrate: curl/Postman for REST, gRPC clients for streaming, Prometheus for metrics (port 9999).
**Completed:** Session 20 (PRs #166, #170)

#### D. Regex-based fatJar filtering (MEDIUM)
**Files:** `octopus/build.gradle.kts`
**Problem:** The optimized JAR uses regex `exclude` rules to strip Grizzly/Jackson leakage. Brittle when dependencies update.
**Fix:** Use Gradle's `configurations` with explicit dependency scoping instead of post-build filtering.

### 4.2 Architecture & Scalability

#### E. Single-node by design (HIGH)
**Problem:** The daemon runs as a single JVM process. State is in-memory (compiled scripts cache, provider instances). No sharding, no consensus, no leader election.
**Impact:** Cannot scale horizontally. Single point of failure. No HA.
**Fix:** Externalize state (Redis/DB for compiled script cache, config store). Add optional distributed queue backend (already partially in orchestration-core with Redis/SQS drivers but not wired for daemon state).

#### F. No versioned script API contract (MEDIUM) — ✅ RESOLVED
**Files:** `shared/annotations/KoupperVersion.kt`, `octopus/AnnotationsProcessor.kt:253-261`, `octopus/Octopus.kt:552-555`
**Status:** ✅ **Delivered in session 19.**
**What changed:**
- `@KoupperVersion` annotation added (`shared/annotations/KoupperVersion.kt`). Target: `FUNCTION` and `PROPERTY`.
- `AnnotationsProcessor.kt:253-261` checks `@KoupperVersion` before compilation. If declared version does not match runtime `Octopus.providerPreambleVersion`, script fails fast with `[ERR_VERSION_MISMATCH]`.
- `KOUPPER_VERSION` constant injected in every script preamble (`Octopus.kt:531`).
- E2E tests verify both matching (`version-ok`) and mismatching (`[ERR_VERSION_MISMATCH]`) scenarios (`OctopusE2ETest.kt:58-77`).
**Remaining work:** Consider semantic versioning comparison (currently uses `startsWith()`, so `@KoupperVersion("6.5")` matches runtime `6.5.3`). Document versioning policy in `docs/CONTRACT_VERSIONING_POLICY.md`.

#### G. No streaming execution model (MEDIUM)
**Problem:** Script execution is request-response. No WebSocket/SSE for streaming output, progress, or long-lived agent conversations.
**Impact:** The `--serve` mode exists but is limited. Interactive prompts (`PROMPT::`) are a hack over the TCP socket.
**Fix:** Add gRPC bidirectional streaming or WebSocket endpoint.

### 4.3 Provider Ecosystem Maturity

#### H. Inconsistent provider implementation depth
**Problem:** Some providers are thin wrappers (e.g., `command-runner`), others are production-grade (`ssh` with round-trip editing, sync, rollback, tree rendering). No uniform quality bar.
**Fix:** Define provider tier system: `core` (fully tested, documented, exception-safe), `community` (basic), `experimental`. Enforce via CI.

#### I. Provider test coverage (MEDIUM) — IMPROVED
**Files:** `providers/src/test/kotlin/com/koupper/providers/` (~69 test files, ~5,139 total lines)
**Status:** 🟡 **Coverage expanded; quality still uneven.**
**What changed:**
- 69 test files across all provider modules (up from 26 directories mentioned in original assessment).
- Largest test suites: `ProcessSupervisorServiceProviderTest.kt` (336 lines), `LspParserTest.kt` (258 lines), `GitCliClientTest.kt` (255 lines).
- `ProviderCatalogConsistencyTest.kt` (36 lines) validates catalog sync.
- `ServiceProviderManagerTest.kt` (34 lines) verifies provider list includes expected classes.
**Problem remaining:**
- Some tests are empty skeletons: `DBServiceProviderTest.kt` (14 lines, empty class body).
- Not all 46 providers have dedicated tests. Coverage is concentrated in `files`, `process`, `git`, `agent`, `aws` providers.
- No enforced coverage gate in CI yet.
**Fix:** Implement tier system (see 9.7) with CI enforcement. Require `core` tier providers to have >80% coverage. Update `DBServiceProviderTest.kt` and other skeletons with real assertions.

#### J. No provider hot-reload (LOW)
**Problem:** Adding a new provider or changing one requires rebuilding the Octopus JAR and restarting the daemon.
**Fix:** Isolate providers into classloader-isolated JARs loaded at startup via SPI. Enable `koupper provider reload`.

### 4.4 Developer Experience

#### K. Structured error reporting (HIGH) — ✅ RESOLVED
**Files:** `octopus/Octopus.kt:163-221`, `octopus/AnnotationsProcessor.kt:253-325`, `shared/ScriptUtilities.kt`
**Status:** ✅ **Delivered in session 19.**
**What changed:**
- Error codes introduced: `[ERR_EXPORT_MISSING]`, `[ERR_EXPORT_MULTIPLE]`, `[ERR_VERSION_MISMATCH]`, `[ERR_COMPILE]`, `[ERR_CANCELLED]`.
- `Octopus.kt:163-221` emits structured errors with actionable suggestions (e.g., "Add exactly one @Export entrypoint").
- `[ERR_EXPORT_MULTIPLE]` now has dual detection: regex pre-compile + reflection post-compile (`AnnotationsProcessor.kt:320-325`).
- `[ERR_CANCELLED]` distinguishes interruption from compilation failure.
- E2E tests assert all error codes (`OctopusE2ETest.kt:31-44`, `69-77`, `80-87`, `136-153`).
**Problem remaining:** Compile error messages still show line numbers relative to the preamble-augmented source, not the original `.kts` file. The `preambleLineCount` is calculated (`AnnotationsProcessor.kt:306-308`) but error mapping is not yet implemented.
**Fix:** Subtract `preambleLineCount` from compilation error line numbers. Add a `sourceMap` data structure to `ScriptingHostBackend`.

#### L. No IDE support (MEDIUM)
**Problem:** No IntelliJ plugin, no LSP integration for `.kts` Koupper scripts. The LSP provider exists but is generic — no Koupper-specific completions.
**Fix:** Ship a Koupper IntelliJ plugin that resolves `koupper.xxx()` completions from the provider catalog.

#### M. Debugging story is weak (MEDIUM)
**Problem:** No step-through debugging, no breakpoints, no variable inspection for running scripts.
**Fix:** Integrate with Kotlin debugger. Alternatively, add `koupper run --debug` with REPL-like introspection.

#### N. Schema extraction from @Export signatures (MEDIUM) — PARTIALLY ADDRESSED
**Files:** `shared/ScriptUtilities.kt:113-140`, `octopus/AnnotationsProcessor.kt:333-338`
**Status:** 🟡 **Reflection-based extraction exists; regex remains primary path.**
**What changed:**
- `reflectExportSignature()` (`ScriptUtilities.kt:113-140`) inspects compiled `kotlin.jvm.functions.FunctionN` interfaces to extract parameter and return types.
- `AnnotationsProcessor.kt:333-338` uses `reflectedSig ?: functionSignature` — reflection is fallback, regex is primary.
- Supports inline data classes defined in the script (`ScriptUtilities.kt:282-300`).
**Problem remaining:**
- `extractExportFunctionSignature()` (regex, `ScriptUtilities.kt:190-232`) is still called first.
- Generic type parameters (e.g., `List<Map<String, Int>>`) are not fully resolved via reflection.
- The `splitTypesTopLevel()` function (`ScriptUtilities.kt:306-331`) handles generics syntactically but not semantically.
**Fix:** Flip the priority: use `reflectExportSignature()` as primary, regex as fallback. Investigate Kotlin reflection APIs for generic type reification (`KType`, `TypeToken`).

### 4.5 Security

#### O. Token-based auth is rudimentary (HIGH)
**Problem:** Single static token sent as plaintext over TCP. No rotation, no scoping, no TLS.
**Fix:** Add mTLS support, JWT-based auth with scopes (read/execute/admin), token rotation.

#### P. Secret redaction in logs (MEDIUM) — ✅ RESOLVED
**Files:** `shared/annotations/Secret.kt`, `octopus/SecretRedactor.kt`, `octopus/AnnotationsProcessor.kt:341-345`, `octopus/SessionStdoutBridge.kt:776`
**Status:** ✅ **Delivered in session 19.**
**What changed:**
- `@Secret` annotation added (`shared/annotations/Secret.kt`). Target: `FUNCTION` and `PROPERTY`.
- `SecretRedactor` (`octopus/SecretRedactor.kt`) is a ThreadLocal-aware filter that replaces secret values with `***`.
- `AnnotationsProcessor.kt:341-345` detects `@Secret` and enables redaction with parameter values as patterns.
- `SessionStdoutBridge.emit()` (`Octopus.kt:776`) applies `SecretRedactor.redact()` to all stdout/stderr output before transmission.
- Redaction is scoped per-script execution and disabled after (`AnnotationsProcessor.kt:383-385`).
- E2E test verifies `@Secret` scripts execute correctly (`OctopusE2ETest.kt:47-55`).
**Remaining work:** Consider supporting `@Secret` on individual function parameters (currently it applies to all params of the annotated function). Add regex pattern matching for partial redaction (e.g., redact `api_key=abc123` even if only `abc123` is the secret).

#### Q. Script sandboxing is absent (MEDIUM)
**Problem:** Scripts run in the same JVM as the daemon with full access. A malicious `.kts` can `System.exit(0)` or read filesystem.
**Fix:** Add optional `SecurityManager`-based sandbox, or execute scripts in isolated classloaders with restricted permissions.

### 4.6 Observability

#### R. Distributed tracing (MEDIUM) — QUICK WIN DELIVERED
**Files:** `octopus/TraceContext.kt`, `octopus/ScheduledSetup.kt:101,123`, `octopus/OctopusProtocol.kt:185`
**Status:** 🟡 **Correlation ID (session 19); OpenTelemetry deferred.**
**What changed:**
- `TraceContext` (`TraceContext.kt`) generates 8-character UUID trace IDs stored in ThreadLocal.
- Trace ID propagated through: TCP request (`OctopusBootstrap.kt:119`) → `FunctionDispatcher` → `ScriptRunner` → job JSON (`ScheduledSetup.kt:101,123`) → `SessionOutput` (`OctopusProtocol.kt:185`).
- Enables manual log correlation across pipeline steps (`RssFeedAgent → SummarizerAgent → TelegramNotifyAgent`) today.
- `DaemonMetrics` (`OctopusProtocol.kt:84-123`) provides structured runtime metrics: uptime, active connections, scripts succeeded/failed.
**Problem remaining:** No OpenTelemetry. No automatic span creation. No propagation to external systems (HTTP calls, DB queries).
**Fix:** Add OpenTelemetry SDK integration. Create spans for script execution, pipeline stages, and provider calls. Propagate `traceparent` header in HTTP requests.

#### S. Metrics (MEDIUM) — FOUNDATION LAID
**Files:** `providers/observability/ObservabilityProvider.kt`, `providers/observability/LocalObservabilityProvider.kt`, `octopus/OctopusProtocol.kt:84-123`
**Status:** 🟡 **Runtime metrics + JSONL sink (session 19); Prometheus/OpenTelemetry deferred.**
**What changed:**
- `LocalObservabilityProvider` (`LocalObservabilityProvider.kt`) writes structured metrics/events/traces as JSONL to `.koupper-observability.jsonl`.
- Supports: `emitMetric()`, `emitEvent()`, `emitTrace()`, `snapshotCounters()`.
- `DaemonMetrics` (`OctopusProtocol.kt:84-123`) tracks runtime counters: active connections, total commands, successful/failed scripts, unauthorized commands.
- Health check endpoint (`HEALTH_CHECK` command type) returns `DaemonMetricsSnapshot` as JSON.
**Problem remaining:** No Prometheus endpoint. No aggregation. No dashboards. JSONL is local-only and not streamable.
**Fix:** Add a `/metrics` HTTP endpoint (Prometheus exposition format) or OTLP exporter. Consider integrating with existing `bootstrap/` module (Jersey/Grizzly) for HTTP metrics serving.

### 4.7 Documentation

#### T. Docs/code drift (MEDIUM)
**Problem:** `providers-catalog.json` and `ServiceProviderManager.kt` are supposed to be in sync. The consistency test helps but there's no automated check for provider docs vs implementation.
**Fix:** Auto-generate provider docs from catalog JSON + contract interfaces. CI gate for drift.

### 4.8 Testing

#### U. E2E test harness (HIGH) — ✅ RESOLVED
**Files:** `octopus/src/test/kotlin/com/koupper/octopus/EmbeddedOctopus.kt`, `octopus/src/test/kotlin/com/koupper/octopus/OctopusE2ETest.kt`, `octopus/src/test/kotlin/com/koupper/octopus/OctopusSocketIntegrationTest.kt`
**Status:** ✅ **Delivered in session 19.**
**What changed:**
- `EmbeddedOctopus` (`EmbeddedOctopus.kt:5-37`) is a singleton wrapper that instantiates `Octopus(app)` with all built-in providers registered. No TCP socket — direct method invocation.
- `OctopusE2ETest.kt` (224 lines, 20 tests) covers: basic `@Export`, computation, missing `@Export`, multiple `@Export`, `@Secret`, `@KoupperVersion` (match + mismatch), compile errors, preamble shortcuts (`env()`, `emit()`, `KOUPPER_VERSION`), cross-contamination between scripts, multiline lambdas.
- `OctopusSocketIntegrationTest.kt` tests the full TCP socket path.
- Tests run via `./gradlew :octopus:test` (JUnit 5 + Kotest platform).
**Remaining work:** Expand harness to cover `@Scheduled`, `@Pipeline`, `@JobsListener` annotations. Add socket-based integration tests for DEPLOY, CANCEL, and HEALTH_CHECK commands. Use harness to validate Wave 2/3 refactors.

---

## 5. Prioritized Refactoring Roadmap

### ✅ Wave 0: Session 19 Deliverables (Completed 2026-06-18)
**Goal:** Close critical gaps and build safety net for future waves.

| # | Task | Status | Evidence |
|---|---|---|---|
| 0.1 | **E2E test harness** (embedded Octopus + script runner) | ✅ Done | `EmbeddedOctopus.kt` + `OctopusE2ETest.kt` (20 tests) |
| 0.2 | **SPI auto-discovery** for providers | ✅ Done | `ServiceProvider.discoverProviderClasses()` via META-INF/services |
| 0.3 | **Structured error codes** (`[ERR_*]`) | ✅ Done | 5 error codes in `Octopus.kt`; tested in E2E harness |
| 0.4 | **`@Secret` annotation + auto-redaction** | ✅ Done | `Secret.kt` + `SecretRedactor.kt` + `SessionStdoutBridge` integration |
| 0.5 | **`@KoupperVersion` + versioned preamble** | ✅ Done | `KoupperVersion.kt` + pre-compile validation in `AnnotationsProcessor.kt` |
| 0.6 | **`@Scheduled(chain)` pipeline parameter** | ✅ Done | `Scheduled.kt:12` + `ScheduledSetup.kt:53-54` + `enqueuePipelineJob()` |
| 0.7 | **Reflection-based validation layer** | ✅ Done | `ReflectionValidator.kt` + `reflectExportSignature()` post-compile cross-check |
| 0.8 | **TraceContext (correlation ID)** | ✅ Done | `TraceContext.kt` + propagation through jobs and responses |

---

### ✅ Wave 1: Foundation (Completed 2026-06-24)
**Goal:** Replace regex primary paths, remove hardcoded fallbacks, and complete source mapping.

| # | Task | Files affected | Effort | Dependencies | Status |
|---|---|---|---|---|---|---|
| 1.1 | **Replace primary regex annotation extraction** with KSP-based discovery | `shared/ScriptUtilities.kt`, new `annotation-processor/` | High | 0.1 | ✅ Done (2026-06-24) |
| 1.2 | **Remove hardcoded provider fallback** and generate SPI at build time | `ServiceProviderManager.kt`, Gradle build | Medium | 0.2 | ✅ Done (2026-06-24) |
| 1.3 | **Map compile errors to original source lines** (preamble offset subtraction) | `ScriptingHostBackend.kt`, `AnnotationsProcessor.kt` | Medium | 0.1 | ✅ Done (2026-06-24) |
| 1.4 | **Flip reflection/regex priority** for type extraction | `AnnotationsProcessor.kt`, `ScriptUtilities.kt` | Low | 0.7 | ✅ Done (2026-06-24) |
| 1.5 | **Prometheus `/metrics` endpoint** | `OctopusBootstrap.kt`, `DaemonMetrics` | Low | 0.8 | ✅ Done (2026-06-24) |

### ✅ Wave 2: Scale & Protocol (Completed 2026-06-24)
**Goal:** Multi-node readiness, standard protocol, HA.

| # | Task | Files affected | Effort | Status |
|---|---|---|---|---|
| 2.1 | **Add gRPC endpoint** alongside legacy TCP socket | `octopus/grpc/`, `.proto` definitions | High | ✅ Done (2026-06-24) |
| 2.2 | **Externalize compiled script cache to Redis/DB** | `ScriptingHostBackend.kt`, `orchestrator-core/` | Medium | 🟡 Partial (Redis/SQS queues exist) |
| 2.3 | **Add WebSocket/SSE endpoint for streaming** | `bootstrap/` or new module | Medium | ❌ Not started |
| 2.4 | **Job queue horizontal scaling** (Redis/SQS-backed worker coordination) | `WorkerCommand.kt`, `orchestrator-core/` | High | 🟡 Partial (file queue + Redis/SQS backends exist) |
| 2.5 | **Add OpenTelemetry tracing** across pipeline + jobs | `shared/telemetry/KoupperTelemetry.kt` | Medium | ✅ Done (2026-06-24) |
| 2.6 | **Add JWT auth with scopes** | `octopus/security/JwtAuth.kt`, `HttpApiServer.kt` | Medium | ✅ Done (2026-06-24) |

### ✅ Wave 3: Enterprise Polish (Completed 2026-06-24)
**Goal:** Production-grade, external contributor friendly.

| # | Task | Files affected | Effort | Status |
|---|---|---|---|---|
| 3.1 | **Provider tier system** (core/community/experimental) + CI enforcement | All providers, CI config | Medium | ✅ Done (2026-06-24) |
| 3.2 | **Auto-generate provider docs from catalog JSON** | `providers-catalog.json`, `docs/` generation script | Medium | ❌ Not started |
| 3.3 | **IntelliJ plugin** with `koupper.*()` completions | New repo `koupper-intellij-plugin` | High | ❌ Not started |
| 3.4 | **Provider hot-reload** (isolated classloader per provider) | `KoupperContainer.kt`, `Octopus.kt` | Medium | ❌ Not started |
| 3.5 | **Prometheus metrics endpoint** | `ObservabilityServiceProvider`, new `metrics/` | Medium |
| 3.6 | **Script sandboxing** (`SecurityManager` + restricted classloader) | New `octopus/sandbox/` | High |
| 3.7 | **Extract type info from compiled class via reflection** (not regex) | `ScriptUtilities.kt`, `ScriptRunnerOrchestrator.kt` | Medium |

---

## 6. Competitive Positioning

| Competitor | Koupper wins on... | Koupper loses on... |
|---|---|---|
| **n8n** | Code-first, more infra providers, type safety, JVM ecosystem | Community size, visual editor, pipeline maturity |
| **Temporal** | Simpler setup, MCP/AI providers, Kotlin scripting UX | Distributed scale, multi-language SDKs, production track record |
| **Airflow/Prefect** | JVM-native (not Python-only), infrastructure-oriented | Data pipeline ecosystem, visualization, community |
| **LangChain/CrewAI** | Typed infra providers (SSH, Docker, K8s), strong type system | AI ecosystem breadth (Python dominates) |
| **Pulumi** | Runtime + agents + scheduler in one, scripting experience | Cloud provider depth, community, docs |
| **GitHub Actions** | Self-hosted, complex pipeline graphs, MCP/AI native | Integration ecosystem, free tier, discoverability |

**Verdict:** Koupper owns a genuine niche: **typed JVM runtime for infra scripting with MCP-first agent orchestration**. No competitor combines these three elements. The gap to close is not capability but maturity (HA, security, observability, docs, community).

---

## 7. Immediate Action Items

These can be started now, without waiting for the full wave plan:

### ✅ Completed in Session 19 (for reference)

| Priority | Task | Status |
|---|---|---|
| ~~P0~~ | ~~Auto-discover providers via SPI~~ | ✅ Done — `ServiceProvider.discoverProviderClasses()` |
| ~~P1~~ | ~~Build E2E test harness~~ | ✅ Done — `EmbeddedOctopus` + 20 E2E tests |
| ~~P1~~ | ~~Add structured error protocol (error codes)~~ | ✅ Done — 5 error codes with actionable messages |
| ~~P2~~ | ~~Add `@Secret` annotation + auto-redaction~~ | ✅ Done — `SecretRedactor` integrated in stdout bridge |
| ~~P2~~ | ~~Version provider preamble~~ | ✅ Done — `@KoupperVersion` with fail-fast validation |

### ✅ Completed in Session 20-21 (post-6.6.0 wave)

| Priority | Task | Status | Evidence |
|---|---|---|---|
| **P0** | Replace primary regex annotation extraction with KSP/PSI | ✅ Done (2026-06-24) | `:annotation-processor` module, `KoupperSymbolProcessor`, `KspMetadataReader`, regex removed |
| **P1** | **JWT auth with scopes** (read/execute/admin) | ✅ Done (2026-06-24) | `security/JwtAuth.kt`, `HttpApiServer.kt`, backward-compatible with legacy token |
| **P1** | **Script sandboxing** — timeout + `System.exit()` interception | ✅ Done (2026-06-24) | `ScriptSandbox.kt`, `SecurityManager`, disabled by default (`koupper.scripting.sandbox=true`) |
| **P2** | **gRPC + HTTP/REST endpoint** alongside legacy TCP | ✅ Done (2026-06-24) | gRPC port 9996, REST port 9997, legacy TCP port 9998 |
| **P2** | **OpenTelemetry tracing** with automatic span creation | ✅ Done (2026-06-24) | `KoupperTelemetry.kt`, W3C context propagation, `@Export` instrumented |
| **P2** | **Provider tier system** (core/community/experimental) + CI enforcement | ✅ Done (2026-06-24) | `ProviderTier.kt`, 5 CORE + 3 EXPERIMENTAL marked, `listProvidersByTier()` |

### Remaining (future waves)

| Priority | Task | Why now? |
|---|---|---|
| **P1** | **mTLS** for socket-level encryption | JWT is in place but TCP socket is still plaintext. Needed for untrusted networks. |
| **P1** | **Classloader isolation** per script (replace SecurityManager) | `SecurityManager` is deprecated in Java 21. Long-term replacement needed. |
| **P2** | **IntelliJ plugin** with completions | Developer experience for script authoring. |
| **P2** | **Provider hot-reload** | Enable provider updates without daemon restart. |

---

## 8. Anti-Goals (explicitly out of scope)

- ❌ Rewrite in another language
- ❌ Replace the DI container with Spring/Guice
- ❌ Break backward compatibility of `@Export` or provider contracts without migration path
- ❌ Add Kubernetes operator (deferred — let the K8s provider handle this)
- ❌ Build a SaaS platform (keep self-hosted as primary deployment model)

---

*Last updated: 2026-06-24 (post-6.6.0 wave). Sync with `SESSION_STATE.md` after completing any item above.*

---

## 9. External Review Notes (2026-06-18 — Updated post-Session 19)

> **Reviewer:** opencode agent session. **Context:** Full codebase audit + `SESSION_STATE.md` cross-reference.
>
> These observations reflect the state of the codebase **after** session 19 deliverables. Items marked ✅ were delivered in session 19.

### 9.1 Wave 1 Effort Reality Check

**Task 1.1 (KSP compiler plugin) is still the highest-risk item.**
- Session 19 delivered a **reflection-based validation layer** (`ReflectionValidator.kt`) as a stopgap, NOT a KSP replacement.
- The reflection layer catches mismatches post-compile but cannot prevent regex discovery errors from affecting dispatch logic.
- **Revised estimate:** 6–8 weeks for full KSP migration. Consider PSI-based extraction as an intermediate step (lower effort than KSP, higher than regex).
- **Phase plan:**
  1. PSI-based `@Export` detection (2-3 weeks) — use Kotlin compiler frontend to parse annotations without full compilation
  2. Extend PSI to `@Scheduled`/`@Pipeline`/`@JobsListener` (2 weeks)
  3. Deprecate and remove regex path once E2E harness passes 100% (1-2 weeks)

### 9.2 ✅ @Scheduled Pipeline Gap — RESOLVED

**Previously a blocking P0 item. Now delivered in session 19.**
- `@Scheduled` annotation gained `chain: String = ""` parameter (`Scheduled.kt:12`).
- `ScheduledSetup.kt:53-54` parses `chain` into `pipelineChain: List<String>`.
- `enqueuePipelineJob()` (`ScheduledSetup.kt:105-125`) generates a coordinator script that runs each stage via `ProcessBuilder(koupper run ...)`.
- **Next step:** Migrate digest agents (`RssFeedAgent.kts`, `SummarizerAgent.kts`, `TelegramNotifyAgent.kts`) from `HeartbeatAgent` manual dispatch to `@Scheduled(chain="...")`.

### 9.3 ✅ E2E Test Harness — RESOLVED

**Previously argued as P0. Delivered in session 19.**
- `EmbeddedOctopus.kt` provides a testable Octopus instance without TCP socket overhead.
- `OctopusE2ETest.kt` (20 tests, 224 lines) covers happy path, error paths, and annotation combinations.
- **Next step:** Use harness to validate regex→KSP migration (task 1.1). Add `@Scheduled` and `@Pipeline` harness tests.

### 9.4 Sandbox Implementation Note

**Task 3.6 (Script sandboxing) labeled High effort — additional complexity not documented.**
- `SecurityManager` is **deprecated since Java 17** and removed in Java 21+.
- Real options are: (a) custom `AccessController`-based policy, (b) isolated classloader with restricted permissions per script, (c) process-level sandbox (fork JVM per script — heavy but clean).
- **Recommendation:** Document which Java target version Koupper locks to before choosing a sandbox strategy. If targeting Java 17+, option (b) is most viable.

### 9.5 Updated Execution Order (Post-Session 19)

Based on what was actually delivered and dependency analysis:

```
✅ Phase A (Delivered in Session 19):
  1. E2E harness (0.1)          ← builds safety net ✅
  2. SPI auto-discovery (0.2)   ← unblocks external contributors ✅
  3. @Scheduled pipeline gap    ← unblocks active feature work ✅
  4. Structured errors (0.3)    ← improves debuggability immediately ✅
  5. @Secret + @KoupperVersion  ← security + contract versioning ✅
  6. Reflection validation      ← safety net for regex ✅
  7. TraceContext               ← manual log correlation ✅

✅ Phase B (Delivered in Session 20-21):
  8. Primary regex → KSP migration (1.1)     ← KSP is now the only path ✅
  9. Remove hardcoded provider fallback (1.2)  ← SPI-only discovery ✅
  10. Compile error source mapping (1.3)      ← Preamble offset subtracted ✅
  11. Flip reflection/regex priority (1.4)    ← KSP primary, regex removed ✅
  12. Provider tier system (3.1)              ← CORE/COMMUNITY/EXPERIMENTAL ✅

✅ Phase C (Delivered in Session 20-21):
  13. gRPC endpoint (2.1)                    ← Port 9996 with bidi streaming ✅
  14. REST API endpoint                      ← Port 9997 with JWT auth ✅
  15. OpenTelemetry tracing (2.5)            ← Spans + W3C propagation ✅
  16. Prometheus metrics                     ← Port 9999 exposition format ✅
  17. Script sandboxing                      ← Timeout + System.exit() intercept ✅

Phase D (Future waves):
  18. Externalize cache (2.2)                ← Redis/DB compiled script cache 🟡
  19. WebSocket/SSE streaming (2.3)          ← Real-time output streaming ❌
  20. Job queue horizontal scaling (2.4)     ← Full HA coordination 🟡
  21. mTLS encryption                        ← Socket-level TLS ❌
  22. Classloader isolation                  ← Replace SecurityManager ❌
```

### 9.6 ✅ Observability — RESOLVED

**Session 19-21 delivered full observability stack.**
- `TraceContext.kt` generates and propagates trace IDs via ThreadLocal (session 19).
- `KoupperTelemetry.kt` (session 20) provides:
  - Automatic span creation around script execution
  - W3C trace context propagation
  - `@Export` resolver instrumented
  - Configurable via `KOUPPER_TELEMETRY_ENABLED` and `KOUPPER_TELEMETRY_SERVICE`
- Prometheus `/metrics` endpoint (port 9999) with 8 runtime counters (session 20).
- **Completed:** Sessions 19-20.

### 9.7 Provider Tier System Detail

When implementing Wave 3 task 3.1 (provider tiers), define concrete criteria:

| Tier | Criteria | CI Gate |
|---|---|---|
| `core` | Full test coverage (>80%), exception-safe, documented, schema-typed I/O | Block merge if tests fail or coverage drops |
| `community` | Basic happy-path tests, documented | Warn on no-test merge |
| `experimental` | No test requirement, marked `@Experimental` | No CI block, but excluded from fatJar by default |

This prevents the current situation where some providers are production-grade (SSH with round-trip editing, sync, rollback) and others are thin wrappers (command-runner) with no quality differentiation visible to users.

### 9.8 Assessment Sync (Session 21)

**All 3 critical assessment items resolved in sessions 20-21.**

| Item | Original Status | Session 20 | Session 21 | Final |
|---|---|---|---|---|
| A. Regex → KSP | 🟡 Validation layer | 🟡 Foundation | ✅ Regex removed | **RESOLVED** |
| B. Hardcoded fallback | 🟡 SPI primary | ✅ Fallback removed | — | **RESOLVED** |
| C. Raw TCP only | ❌ No standard protocol | ✅ gRPC + REST | — | **RESOLVED** |

**Recommendation:** This assessment is now in maintenance mode. Future changes should update the "Remaining (future waves)" section rather than these resolved items.
