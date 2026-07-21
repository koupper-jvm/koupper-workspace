# Koupper Changelog

All notable changes to the Koupper monorepo are documented here.
Versioning follows the Octopus engine version (`koupper/build.gradle`).
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

---

## [7.2.0] - 2026-06-25

### Added
- **Multiple @Export declarations** with priority-based selection. Scripts can now define multiple `@Export` entrypoints sorted by `priority` attribute.
- **YoutubeTranscriptProvider** SP for fetching and processing YouTube video transcripts.
- **RouterAnalyzer** — strongly-typed HTTP route analysis for multi-module projects.
- **Request context attributes** in RuntimeRouter with native support for `PUT`, `PATCH`, `DELETE` methods.
- **CORS + ExceptionHandler DSL** blocks in RuntimeRouterDsl for global middleware configuration.
- **PathParams extraction** from route patterns into `RequestContext`.
- **Non-generic route methods** in RuntimeRouterDsl for simpler registration.
- **FileHandler fs ops + TextFileHandler stateless methods** for direct filesystem operations.

### Fixed
- **Windows File Concurrency**: Replaced legacy non-atomic `renameTo` in `FileJobDriver` with atomic `Files.move(..., ATOMIC_MOVE)` to prevent double claiming in high-concurrency races on Windows.
- **Kotest Annotation Lifecycle**: Changed ignored `@BeforeTest`/`@AfterTest` to `@BeforeEach`/`@AfterEach` in `JwtAuthTest` and `HttpApiServerTest` to ensure test setup/teardown functions execute cleanly.
- **Scheduled Annotation Resolver**: Corrected a bug in the `@Scheduled` resolver where the return callback was ignored, ensuring scheduled execution correctly reports its registration status string.
- **SseEmitter buffering**: Buffered events until stream callbacks register, preventing dropped initial events.
- **Script compilation with module-info**: Scripts now compile against a module-info-stripped JAR copy for compatibility with JPMS classloaders.
- **Sandbox params on Windows**: Resolved parameter passing through sandbox on Windows hosts.
- **OpenAI + DynamoDB error handling**: Enhanced error recovery and logging in cloud provider clients.
- **OkHttp response body leak**: Closed response body in `YoutubeTimedTextClient` preventing resource exhaustion.
- **Build**: Excluded `module-info` from both optimized (Maven local) and shadowJar artifacts.
- **Installer**: Replaced obsolete `fatJar` task reference with `shadowJar` in `install.kts`.

### Removed
- 25 `.tmp` garbage files left in `examples/` tree by previous session.
- `.env.test` config file mistakenly committed to repository.

### Release alignment
- `octopus 7.2.0` / `koupper-cli 7.2.0`

---

## [7.1.1] - 2026-06-25

### Added
- Type generic fallback for inline data class deserialization in `ScriptRunnerOrchestrator.kt`. Uses `target.javaClass.genericInterfaces` → `FunctionN` interface to resolve parameter types when `resolveClassFromArgName` returns null (e.g. `SalesReportCommand`).
- `splitTypesTopLevel()` used in regex fallback path of `extractExportFunctionSignature()` for generic-aware parameter splitting (`Map<String, Any?>` no longer splits naively by comma).
- Multi-annotation regex support: `@Export` followed by `@Scheduled`, `@Logger`, etc. is now correctly parsed.

### Fixed
- **Sandbox parameter pass-through**: Removed `--` prefix from `SandboxWorker.kt` CLI args construction. The prefix caused key mismatch between `parseArgs` (stores `--key`) and `buildParamsJson` (looks up `key`).
- **`@Scheduled` script execution**: Scripts like `logger-scheduled-demo.kts` now correctly extract their parameter types when stacked with `@Logger` and `@Scheduled` annotations.
- **Example script compatibility**: `ContextPreloaderAgent.kts` and `PluginManagerAgent.kts` restructured to avoid `return@label` (prohibited in Kotlin scripting). Provider-flow scripts updated with default parameter values.

### Regression analysis
- v7.1.0 broke parameter passing for dynamic `.kts` scripts due to two commits on Jun 24: KSP-only metadata (removed regex fallback) + sandbox enabled by default. Both fixes now coexist: KSP is primary, regex fallback handles dynamic scripts.

### Smoke test
- 59 example scripts tested: 36 pass, 3 daemon (expected), 14 need infrastructure, 6 use obsolete APIs, **0 framework bugs**.

### Release alignment
- `octopus 7.1.1` / `koupper-cli 7.1.1`

---

## [7.1.0] - 2026-06-24

### Added
- Process Isolation & Sandboxing via JVM `ProcessBuilder` (`ProcessSandbox.kt`, `SandboxWorker.kt`). Enabled by default (`koupper.sandbox.enabled=true`).
- Server-Sent Events (SSE) Streaming endpoint `POST /api/v1/run-stream`.
- Hot-Reloading of ServiceProviders via `URLClassLoader` + `RELOAD_PROVIDERS` protocol command.
- `koupper reload` CLI command for dynamic provider refresh.

### Fixed
- `ClassCastException: Unit → String` in `SandboxWorker.kt` — changed generic from `<String>` to `<Any?>` with Unit/null handling.
- SPI Services missing in FatJar — added `META-INF/services/com.koupper.providers.ServiceProvider` and `providers-catalog.json`.

### Release alignment
- `octopus 7.1.0` / `koupper-cli 7.1.0`

---

## [6.5.3] - 2026-05-24

### Added
- Migration note for unified `@Export` annotation path in `docs/migrations/2026-05-export-annotation-path.md`.
- Maintenance branches logic to the `develop` workflow for cleaner `gitignore` and IDE state management.

### Changed
- **BREAKING**: Moved `@Export` annotation from `com.koupper.octopus.annotations` to `com.koupper.shared.annotations` to support unified classpath resolution.
- Updated all internal scripts, examples, and CLI templates to use the new `com.koupper.shared.annotations.Export` path.
- Refactored `koupper-cli` command handlers for jobs, modules, and scripts to generate code with the updated annotation path.

### Fixed
- Fixed `gitignore` missing patterns for `bin/` directories in Gradle submodules and template projects.
- Rescued missing bootstrap fixes from `main` back into `develop` in the root workspace repository.

### Release alignment
- `octopus 6.5.0` / `koupper-cli 4.8.0`

---

## [6.4.0] - 2026-04-10

### Added
- `koupper infra init|validate|plan|apply|drift|output` — Terraform-backed infrastructure lifecycle suite with retry/timeout/backoff controls and drift spec v1 evaluation.
- `koupper reconcile run` — reconcile command with stage policies and stable JSON output contracts.
- AWS deploy hardening: Lambda waiter support, timeout/retry/backoff per action, frontend backup modes (`full|incremental|disabled`), structured per-action result metadata, `preflight`, `smokeTestApis`, and `callerIdentity` operations.
- `docs/CONTRACT_VERSIONING_POLICY.md` — governs additive/behavior/breaking change taxonomy, deprecation lifecycle, and migration note format.
- `docs/PROVIDER_AUTHORING_CHECKLIST.md` — four-surface checklist (register + catalog + docs + tests) for every new service provider.
- `docs/migrations/` — directory for per-change migration notes on behavior changes.
- `docs/KOUPPER_FRAMEWORK_MATURITY_PLAYBOOK.md` — strategic enterprise hardening execution plan.
- `SecretsClient.delete(key)` and `SecretsClient.list()` — completes the secrets contract.
- `ObservabilityExecutionMonitor` — wires runtime script execution lifecycle (trace, metric, failure event) to `ObservabilityProvider` via the existing `CompositeExecutionMonitor` chain.
- `CLAUDE.md` — Claude Code guidance file for AI-assisted development sessions.

### Fixed
- `KubectlK8sProvider` timeout now returns `K8sResult(exitCode=124, timedOut=true)` instead of throwing `IllegalStateException`. Launch failures return `exitCode=127`. Migration note in `docs/migrations/`.
- `MCPServerProvider` — replaced `com.sun.net.httpserver` (internal JDK API) with `ServerSocket` + `CachedThreadPool` using only `java.net` standard library.

### Release alignment
- `octopus 6.4.0` / `koupper-cli 4.7.1`

---

## [6.3.1] - 2026-03-28

### Added
- `koupper run --serve` for long-running script sessions with attached CLI output and daemon-side cancellation via `Ctrl+C`.
- `koupper provider list` and `koupper provider info <name>` — provider discoverability from installed catalog.
- `process-supervisor` provider for detached local long-running process management with persisted metadata and per-process logs.
- GitHub provider (`GitHubServiceProvider`) with `GitHubClient` operations: issues, pull requests, workflow dispatch/runs, and check-runs.
- Terminal runtime demo and interactive prompt visibility fix for PowerShell.
- Setup helpers (`scripts/setup/install.sh`, `scripts/setup/install.ps1`) with optional `--auto-install-deps` mode.
- `--force` reinstall and `--doctor` verification mode in installer.
- `--force` and `--purge` flags in uninstaller.
- Installer provisions providers catalog at `~/.koupper/catalog/providers.json`.
- `install-uninstall-e2e-windows` heavy CI gate added to `full-smoke-suite.yml`.
- `PR Fast Checks` and `Provider Consistency` workflows for fast CI on `develop` PRs.
- Remote deploy token authentication and payload checksum verification.
- Deploy payload size limits with explicit rejection for oversized payloads.

### Release alignment
- `octopus 6.3.1` / `koupper-cli 4.7.1`

---

## [6.0.0] - 2026-03-26

### Added
- Monorepo migration: consolidated `koupper`, `koupper-cli`, and `.koupper` template into a single repository for version parity.
- Advanced JSON mapping for CLI socket dispatcher — raw JSON string injection with deep PowerShell quote cleanup and permissive Jackson deserialization into nested Kotlin POJOs.
- Event-driven background worker logging — deprecated untraceable `println` usage across async tasks; injected `GlobalLogger` lifecycle tracking with rolling log files.
- Socket exception bubbling — fatal Jackson/mapper errors now flush upstream via `<ERROR::>` marker instead of failing silently.
- UTF-8 byte preservation across TCP socket streams — emoji and multi-byte characters survive the CLI rendering pipeline cross-OS.
- Release governance: semver policy, stable tagging convention (`octopus-v*`, `cli-v*`), and independent artifact versioning.

---
