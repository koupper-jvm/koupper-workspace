# Koupper Framework: Assessment & Refactoring Roadmap

> **Audience:** Maintainers. **Status:** Living document — sync with reality after each wave.
>
> Based on full codebase audit of `koupper/` (Octopus Engine v6.5.3), `koupper-cli/` (v4.8.0), and `koupper-document/` as of 2026-06-18.

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
| `octopus/` | Daemon, TCP loop, FunctionDispatcher, AnnotationsProcessor, pipeline | ~1600 (Octopus.kt alone) |
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
        → extractExportedDeclarations()  ← regex-based!
        → extractExportedAnnotations()   ← regex-based!
        → FunctionDispatcher.dispatch()
            → @Logger resolver (pri 30)
            → @Scheduled/@Pipeline/@JobsListener resolvers (pri 20)
            → @Export resolver (pri 10) ← terminal
                → ScriptingHostBackend.eval() (compile + MD5 cache)
                → ScriptRunner.runScript() (Jackson params + reflection invoke)
    → SessionOutput.result() → TCP response to CLI
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

#### A. Annotation extraction via regex (CRITICAL)
**Files:** `shared/.../ScriptUtilities.kt:36-95`
**Problem:** `@Export`, `@Scheduled`, `@Pipeline` annotations are discovered by regex-matching Kotlin source code strings. This is fundamentally fragile — any Kotlin syntax change, multiline annotation, or code comment can break detection.
**Impact:** False negatives (script not recognized), false positives (comment mistaken for annotation), no compiler validation.
**Fix:** Replace with a Kotlin compiler plugin or KSP (Kotlin Symbol Processing) that extracts annotations at compile time into a manifest.

#### B. Hardcoded provider registry (HIGH)
**Files:** `providers/.../ServiceProviderManager.kt:82-134`
**Problem:** All 46 providers are listed as a hardcoded `List<KClass<*>>`. Adding a provider requires editing this core file. No auto-discovery.
**Impact:** Coupling between providers module and core. Provider scaffold command creates files but user must manually edit the registry.
**Fix:** Implement SPI (ServiceLoader) or package-scanning with `loadAbstractClassesFromScope()` at build time, generating the list automatically.

#### C. Raw TCP protocol (HIGH)
**Files:** `octopus/.../Octopus.kt:1185-1526`, `cli/.../RunCommand.kt:147-271`
**Problem:** CLI ↔ Octopus communicate over a plain TCP socket with a custom text/JSON protocol. Two response formats (legacy text + JSON) maintained. No standard framing, no HTTP.
**Impact:** No standard tooling integration (curl, Postman, load balancers). No streaming/gRPC. Auth is a single token sent as first line.
**Fix:** Add gRPC or HTTP/2 endpoint alongside legacy socket. Define protobuf/OpenAPI contract.

#### D. Regex-based fatJar filtering (MEDIUM)
**Files:** `octopus/build.gradle.kts`
**Problem:** The optimized JAR uses regex `exclude` rules to strip Grizzly/Jackson leakage. Brittle when dependencies update.
**Fix:** Use Gradle's `configurations` with explicit dependency scoping instead of post-build filtering.

### 4.2 Architecture & Scalability

#### E. Single-node by design (HIGH)
**Problem:** The daemon runs as a single JVM process. State is in-memory (compiled scripts cache, provider instances). No sharding, no consensus, no leader election.
**Impact:** Cannot scale horizontally. Single point of failure. No HA.
**Fix:** Externalize state (Redis/DB for compiled script cache, config store). Add optional distributed queue backend (already partially in orchestration-core with Redis/SQS drivers but not wired for daemon state).

#### F. No versioned script API contract (MEDIUM)
**Problem:** Scripts depend on `topLevelFunctions()` being injected. If a provider changes its API, scripts silently break on next execution.
**Fix:** Version the provider preamble. Scripts declare `@KoupperVersion("6.5")` or similar. Compilation fails on mismatch.

#### G. No streaming execution model (MEDIUM)
**Problem:** Script execution is request-response. No WebSocket/SSE for streaming output, progress, or long-lived agent conversations.
**Impact:** The `--serve` mode exists but is limited. Interactive prompts (`PROMPT::`) are a hack over the TCP socket.
**Fix:** Add gRPC bidirectional streaming or WebSocket endpoint.

### 4.3 Provider Ecosystem Maturity

#### H. Inconsistent provider implementation depth
**Problem:** Some providers are thin wrappers (e.g., `command-runner`), others are production-grade (`ssh` with round-trip editing, sync, rollback, tree rendering). No uniform quality bar.
**Fix:** Define provider tier system: `core` (fully tested, documented, exception-safe), `community` (basic), `experimental`. Enforce via CI.

#### I. Missing provider tests (MEDIUM)
**Problem:** 26 test directories exist but not all have actual test bodies. Some are empty skeletons.
**Fix:** Enforce `ProviderAuthoringChecklist` gate in CI: no merge without tests.

#### J. No provider hot-reload (LOW)
**Problem:** Adding a new provider or changing one requires rebuilding the Octopus JAR and restarting the daemon.
**Fix:** Isolate providers into classloader-isolated JARs loaded at startup via SPI. Enable `koupper provider reload`.

### 4.4 Developer Experience

#### K. No structured error reporting (HIGH)
**Problem:** Script compilation errors are raw Kotlin compiler output with line offsets relative to the preamble-injected source, not the original `.kts` file. Very confusing.
**Fix:** Map compile errors back to original source lines. Add source maps.

#### L. No IDE support (MEDIUM)
**Problem:** No IntelliJ plugin, no LSP integration for `.kts` Koupper scripts. The LSP provider exists but is generic — no Koupper-specific completions.
**Fix:** Ship a Koupper IntelliJ plugin that resolves `koupper.xxx()` completions from the provider catalog.

#### M. Debugging story is weak (MEDIUM)
**Problem:** No step-through debugging, no breakpoints, no variable inspection for running scripts.
**Fix:** Integrate with Kotlin debugger. Alternatively, add `koupper run --debug` with REPL-like introspection.

#### N. Schema extraction from @Export signatures (MEDIUM)
**Problem:** `extractExportFunctionSignature()` (ScriptUtilities.kt:153) parses type signatures from raw source. Works for simple types but fragile for generics, data classes.
**Fix:** Extract type information from compiled class via reflection, not regex.

### 4.5 Security

#### O. Token-based auth is rudimentary (HIGH)
**Problem:** Single static token sent as plaintext over TCP. No rotation, no scoping, no TLS.
**Fix:** Add mTLS support, JWT-based auth with scopes (read/execute/admin), token rotation.

#### P. No secret redaction in logs by default (MEDIUM)
**Problem:** `println()` inside scripts can leak secrets. The `SessionStdoutBridge` captures all output.
**Fix:** Add `@Secret` annotation for parameters. Redact automatically in logs and stdout capture.

#### Q. Script sandboxing is absent (MEDIUM)
**Problem:** Scripts run in the same JVM as the daemon with full access. A malicious `.kts` can `System.exit(0)` or read filesystem.
**Fix:** Add optional `SecurityManager`-based sandbox, or execute scripts in isolated classloaders with restricted permissions.

### 4.6 Observability

#### R. No distributed tracing (MEDIUM)
**Problem:** No trace IDs across pipeline steps, job executions, or provider calls. Hard to debug `RssFeedAgent → SummarizerAgent → TelegramNotifyAgent` chains.
**Fix:** Add OpenTelemetry instrumentation. Propagate trace context through job JSON.

#### S. Metrics are ad-hoc (MEDIUM)
**Problem:** `ObservabilityProvider` writes JSONL to local disk. No aggregation, no dashboards, no alerting.
**Fix:** Add Prometheus metrics endpoint. Wire into OpenTelemetry collector.

### 4.7 Documentation

#### T. Docs/code drift (MEDIUM)
**Problem:** `providers-catalog.json` and `ServiceProviderManager.kt` are supposed to be in sync. The consistency test helps but there's no automated check for provider docs vs implementation.
**Fix:** Auto-generate provider docs from catalog JSON + contract interfaces. CI gate for drift.

### 4.8 Testing

#### U. No integration/E2E test harness (HIGH)
**Problem:** Tests are unit-level (Kotest + Mockk). No framework for spinning up an Octopus daemon, running real scripts end-to-end, and asserting results.
**Fix:** Build a test harness that starts an embedded Octopus on a random port, runs `.kts` files, and asserts stdout/exit/result.

---

## 5. Prioritized Refactoring Roadmap

### Wave 1: Foundation (4-6 weeks)
**Goal:** Eliminate technical debt that blocks all future work.

| # | Task | Files affected | Effort |
|---|---|---|---|
| 1.1 | **Replace regex annotation extraction** with KSP compiler plugin | `shared/ScriptUtilities.kt`, new `octopus/processing/` | High |
| 1.2 | **Auto-discover providers via SPI** instead of hardcoded list | `ServiceProviderManager.kt`, all `*ServiceProvider.kt` | Medium |
| 1.3 | **Map compile errors to original source lines** | `ScriptingHostBackend.kt`, `AnnotationsProcessor.kt` | Medium |
| 1.4 | **Add structured error protocol** (error codes, stack traces) | `Octopus.kt` (result serialization), `RunCommand.kt` | Medium |
| 1.5 | **Build E2E test harness** (embedded Octopus + script runner) | New `octopus/src/test/` dir | Medium |
| 1.6 | **Add `@Secret` annotation + auto-redaction** | `shared/annotations/`, `SessionStdoutBridge` | Low |
| 1.7 | **Version provider preamble** (`@KoupperVersion`) | `Octopus.kt`, `ScriptingHostBackend.kt` | Low |

### Wave 2: Scale & Protocol (4-6 weeks)
**Goal:** Multi-node readiness, standard protocol, HA.

| # | Task | Files affected | Effort |
|---|---|---|---|
| 2.1 | **Add gRPC endpoint** alongside legacy TCP socket | New `octopus/grpc/`, `.proto` definitions | High |
| 2.2 | **Externalize compiled script cache to Redis/DB** | `ScriptingHostBackend.kt`, `orchestrator-core/` | Medium |
| 2.3 | **Add WebSocket/SSE endpoint for streaming** | `bootstrap/` or new module | Medium |
| 2.4 | **Job queue horizontal scaling** (Redis/SQS-backed worker coordination) | `WorkerCommand.kt`, `orchestrator-core/` | High |
| 2.5 | **Add OpenTelemetry tracing** across pipeline + jobs | New `octopus/telemetry/` | Medium |
| 2.6 | **Add mTLS + JWT auth with scopes** | `Octopus.kt` (socket accept), new `security/` | Medium |

### Wave 3: Enterprise Polish (4-6 weeks)
**Goal:** Production-grade, external contributor friendly.

| # | Task | Files affected | Effort |
|---|---|---|---|
| 3.1 | **Provider tier system** (core/community/experimental) + CI enforcement | All providers, CI config | Medium |
| 3.2 | **Auto-generate provider docs from catalog JSON** | `providers-catalog.json`, `docs/` generation script | Medium |
| 3.3 | **IntelliJ plugin** with `koupper.*()` completions | New repo `koupper-intellij-plugin` | High |
| 3.4 | **Provider hot-reload** (isolated classloader per provider) | `KoupperContainer.kt`, `Octopus.kt` | Medium |
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

| Priority | Task | Why now? |
|---|---|---|
| **P0** | Replace regex annotation extraction with KSP | Every other feature depends on reliable annotation discovery |
| **P0** | Auto-discover providers via SPI | Unblocks external provider contributions |
| **P1** | Add structured error protocol (error codes) | Makes debugging possible for users |
| **P1** | Build E2E test harness | Makes all future refactoring safe |
| **P2** | Add `@Secret` annotation + auto-redaction | Immediate security win, low effort |
| **P2** | Version provider preamble | Prevents silent breakage |

---

## 8. Anti-Goals (explicitly out of scope)

- ❌ Rewrite in another language
- ❌ Replace the DI container with Spring/Guice
- ❌ Break backward compatibility of `@Export` or provider contracts without migration path
- ❌ Add Kubernetes operator (deferred — let the K8s provider handle this)
- ❌ Build a SaaS platform (keep self-hosted as primary deployment model)

---

*Last updated: 2026-06-18. Sync with `SESSION_STATE.md` after completing any item above.*

---

## 9. External Review Notes (2026-06-18)

> **Reviewer:** opencode agent session. **Context:** Full codebase audit + SESSION_STATE cross-reference.
>
> These observations complement the assessment above and should be considered by Koupper agents during implementation waves.

### 9.1 Wave 1 Effort Reality Check

**Task 1.1 (KSP compiler plugin) is underestimated.**
- Labeled "High" effort for 4-6 week wave, but KSP requires: understanding the KSP API, creating a new `processing/` module, changing the entire annotation discovery pipeline, and ensuring all 46+ providers remain compatible.
- **Revised estimate:** 6–8 weeks as a standalone task. Consider splitting into phases: (a) KSP processor skeleton + manifest generation, (b) migration of `@Export` detection only, (c) migration of `@Scheduled`/`@Pipeline`/`@JobsListener`, (d) removal of legacy regex path.

### 9.2 Missing P0 Item: @Scheduled Pipeline Gap

**Not mentioned in assessment but blocking active development.**
- `SESSION_STATE.md` documents across sessions 14–18 that `@Scheduled` needs a `pipeline: String` parameter.
- Current workaround: `HeartbeatAgent` dispatches pipelines manually via `dispatchPipeline()`.
- The fix touches: `annotations/Scheduled.kt` (add `pipeline` field), `ScheduledSetup.kt` (build `pipelineNext` in `enqueueJob()`), and digest agent scripts (`RssFeedAgent.kts`, `SummarizerAgent.kts`, `TelegramNotifyAgent.kts`).
- **Recommendation:** Add as P0 in Immediate Action Items. This blocks real features today — it's not future debt.

### 9.3 E2E Test Harness Should Be P0

**Task 1.5 is listed as Medium/P1 but gates all other refactoring.**
- Without an embedded Octopus test harness, every change to regex→KSP (1.1), TCP→gRPC (2.1), or SPI registry (1.2) is surgery without anesthesia.
- The harness itself unlocks: regression testing per commit, CI gate for provider changes, safe refactoring of core modules.
- **Recommendation:** Promote to P0. Build it first, then use it to validate every subsequent wave task.

### 9.4 Sandbox Implementation Note

**Task 3.6 (Script sandboxing) labeled High effort — additional complexity not documented.**
- `SecurityManager` is **deprecated since Java 17** and removed in Java 21+.
- Real options are: (a) custom `AccessController`-based policy, (b) isolated classloader with restricted permissions per script, (c) process-level sandbox (fork JVM per script — heavy but clean).
- **Recommendation:** Document which Java target version Koupper locks to before choosing a sandbox strategy. If targeting Java 17+, option (b) is most viable.

### 9.5 Suggested Execution Order Revision

Based on dependency analysis, the recommended execution sequence differs slightly from the assessment:

```
Phase A (Foundation — must come first):
  1. E2E harness (1.5)          ← builds safety net
  2. SPI auto-discovery (1.2)   ← unblocks external contributors
  3. @Scheduled pipeline gap    ← unblocks active feature work
  4. Structured errors (1.4)    ← improves debuggability immediately

Phase B (Core refactor — uses harness for validation):
  5. Regex → KSP migration (1.1) ← biggest change, now testable
  6. Compile error source mapping (1.3)
  7. @Secret redaction (1.6)
  8. Provider preamble versioning (1.7)

Phase C (Scale — after Wave 1 solid):
  9. gRPC endpoint (2.1)
  10. Externalize cache (2.2)
  11. OpenTelemetry tracing (2.5)
```

### 9.6 Observability Quick Win

Before full OpenTelemetry (Wave 2, task 2.5), consider an intermediate step:
- Add a **correlation ID** (`jobId` / `traceId`) that propagates through: TCP request → FunctionDispatcher → ScriptRunner → Provider calls → SessionOutput.
- This is a string field passed through context, no external dependency needed.
- Enables manual log correlation across pipeline steps (`RssFeedAgent → SummarizerAgent → TelegramNotifyAgent`) *today*.
- Cost: ~half day of work. Value: immediate debugging improvement.

### 9.7 Provider Tier System Detail

When implementing Wave 3 task 3.1 (provider tiers), define concrete criteria:

| Tier | Criteria | CI Gate |
|---|---|---|
| `core` | Full test coverage (>80%), exception-safe, documented, schema-typed I/O | Block merge if tests fail or coverage drops |
| `community` | Basic happy-path tests, documented | Warn on no-test merge |
| `experimental` | No test requirement, marked `@Experimental` | No CI block, but excluded from fatJar by default |

This prevents the current situation where some providers are production-grade (SSH with round-trip editing, sync, rollback) and others are thin wrappers (command-runner) with no quality differentiation visible to users.
