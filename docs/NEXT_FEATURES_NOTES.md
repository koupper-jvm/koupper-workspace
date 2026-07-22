# Next Features Notes

This note keeps the next implementation waves scoped and aligned with current `develop` CI/release policy.

Strategic reference for enterprise hardening: `docs/KOUPPER_FRAMEWORK_MATURITY_PLAYBOOK.md`.

## Current operating baseline

- Branch from `develop`.
- Run local quick checks before push (`scripts/ci/local-quick-checks.ps1|.sh`).
- Use fast lane for high-velocity PR flow:
  - `koupper run scripts/release/fast-lane.kts '{"featureBranch":"feature/<name>","enableAutoMerge":true}'`
- Keep heavy validation for `main`/release.

## Completed in 6.5.3+ wave (2026-05-28)

- ~~`MCPClientProvider` — HTTP + stdio transport to external MCP servers (Playwright, GitHub, filesystem, etc.).~~ Done — `providers/mcp/MCPClientProvider.kt`.
- ~~`LocalMCPServerProvider` rewrite to JSON-RPC 2.0 (MCP spec 2024-11-05).~~ Done — `POST /` handles full MCP protocol; legacy `/mcp/tools` and `/mcp/call` preserved.
- ~~`koupper worker` daemon — atomic job claiming + agent execution.~~ Done — `koupper-cli/WorkerCommand.kt`.
- ~~`InferenceConfig` — configurable inference params for `LlamaServerSidecar`.~~ Done.
- ~~`EnvironmentProfiler` kill switch — replaced with graceful `LOW_END` degradation.~~ Done.
- ~~`AgentOrchestrator` stub tool call — replaced with real JSON parsing.~~ Done.
- ~~`DefaultToolExecutor` fake responses — replaced with real `java.io.File` operations.~~ Done.
- ~~`optimized` JAR filter — regex-based matching eliminates Grizzly/Jackson leakage; JAR reduced to 1.6MB.~~ Done.
- ~~`GrizzlyRuntimeRouterProvider` HTML content-type — detects `<!DOCTYPE`/`<html>` and sets `text/html`.~~ Done.

## Near-term priorities

1. **MCP ecosystem expansion**
   - Add `MCPClientProvider` usage examples to `https://koupper.com/` (stdio and HTTP patterns).
   - Validate compatibility with: `@playwright/mcp`, `@modelcontextprotocol/server-github`, `@modelcontextprotocol/server-filesystem`, `@modelcontextprotocol/server-postgres`.
   - Add SSE transport support to `MCPClientProvider` for servers that use HTTP+SSE (not just stdio or plain HTTP).

2. **Worker hardening**
   - ~~Add per-job timeout to `WorkerCommand` (kill subprocess if it runs > N minutes).~~ Done — `--timeout` / `KOUPPER_WORKER_TIMEOUT`.
   - ~~Add retry count tracking per job — after N failures, move to `.dead/` instead of `.failed/`.~~ Done — `attempts` field + `--max-retries`.
   - ~~Add `koupper worker --status` subcommand to show queue sizes without starting the daemon.~~ Done.
   - ~~`koupper doctor` health command (Java/PATH/jars/ports/queues).~~ Done (CLI ops-hardening wave).

3. **Provider developer experience**
   - ~~Add a provider authoring checklist template (`register + catalog + docs + tests`).~~ Done — `docs/PROVIDER_AUTHORING_CHECKLIST.md`.
   - ~~Add test coverage for all providers.~~ Done (6.4.0) — 74 tests across all providers.
   - ~~Add provider scaffold command or script that generates the starter files from the checklist template.~~ Done (6.5.3) — `koupper provider new <name>`.
   - Add deep-type resolution for complex JSON/Map inputs in scripts. Done (6.5.3) — `.toType<T>()`.

4. **Installer lifecycle hardening**
   - ~~Fix `install-uninstall-e2e-windows` CI PATH issue.~~ Done (6.4.0).
   - Add Linux/macOS uninstall E2E parity to heavy workflow.
   - ~~Add `koupper doctor` health command.~~ Done (CLI).

5. **Observability**
   - ~~Wire ObservabilityProvider into the Octopus execution monitor chain.~~ Done (6.4.0).
   - Next evolution: OpenTelemetry / Datadog export (deferred, not blocking).

6. **Docs deploy automation**
   - ~~Add deploy script for `https://koupper.com/`.~~ Done (6.4.0).
   - Next: wire docs deploy into CI on merge to `koupper-docs` main.

## Scope guardrails

- Do not mix provider feature code with unrelated refactors.
- Keep docs and catalog updates in the same delivery wave as provider changes.
- If a change touches release scripts, update `scripts/release/README.md` in the same PR.
- `MCPClientProvider` changes must maintain backward compatibility with `MCPServerProvider` (server side is independent).
