# Next Features Notes

This note keeps the next implementation waves scoped and aligned with current `develop` CI/release policy.

Strategic reference for enterprise hardening: `docs/KOUPPER_FRAMEWORK_MATURITY_PLAYBOOK.md`.

## Release baseline (as of 2026-04-15)

- `main` and `develop` are content-aligned after release-history cleanup closeout.
- `v6.4.0` is anchored to `main` head.
- No open release PRs remain from the 6.4.0 wave.

## Current operating baseline

- Branch from `develop`.
- Run local quick checks before push (`scripts/ci/local-quick-checks.ps1|.sh`).
- Use fast lane for high-velocity PR flow:
  - `koupper run scripts/release/fast-lane.kts '{"featureBranch":"feature/<name>","enableAutoMerge":true}'`
- Keep heavy validation for `main`/release.

## Near-term priorities

1. **Provider developer experience**
   - ~~Add a provider authoring checklist template (`register + catalog + docs + tests`).~~ Done — `docs/PROVIDER_AUTHORING_CHECKLIST.md`.
   - ~~Add test coverage for all providers.~~ Done (6.4.0) — 74 tests across all providers.
   - Add provider scaffold command or script that generates the starter files from the checklist template.

2. **Installer lifecycle hardening**
   - ~~Fix `install-uninstall-e2e-windows` CI PATH issue.~~ Done (6.4.0) — PATH step added after install.
   - Add Linux/macOS uninstall E2E parity to heavy workflow.
   - Add a lightweight health command (`koupper doctor`) smoke to release checks.

3. **Release script ergonomics**
    - Add a `--no-auto-merge` fallback mode message in fast lane output.
    - Emit explicit PR URL + next actions at end of every release script.
    - Add a guard that detects stale/conflicted `develop -> main` PRs and suggests regeneration path.

4. **Observability**
   - ~~Wire ObservabilityProvider into the Octopus execution monitor chain.~~ Done (6.4.0) — auto emits traces/metrics on every script run.
   - Next evolution: OpenTelemetry / Datadog export (deferred, not blocking).
   - Track median CI duration for `develop` and `main` checks.

5. **Docs deploy automation**
   - ~~Add deploy script for `koupper.com/docs`.~~ Done (6.4.0) — `scripts/deploy/deploy-docs.kts`.
   - Next: wire docs deploy into CI on merge to `koupper-docs` main (auto-deploy on push).

6. **Module scanner hardening (follow-up)**
   - Validate `koupper module` detection in mixed Kotlin module layouts without class-level `@Path`.
   - Add Java/Spring-style controller detection fallback if needed by downstream projects.
   - Add regression fixtures for `.http.yml/.json` absent/present behavior messaging.

## Scope guardrails

- Do not mix provider feature code with unrelated refactors.
- Keep docs and catalog updates in the same delivery wave as provider changes.
- If a change touches release scripts, update `scripts/release/README.md` in the same PR.
