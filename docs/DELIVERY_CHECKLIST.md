# Delivery Checklist

_Last updated: 2026-04-23_

## Wave: Module Helpers Directory Hotfix (2026-04-23)

### Scope

- [x] Reproduce and fix `koupper module` crash on clean user homes (`~/.koupper/helpers` missing).
- [x] Keep scope limited to CLI helper-file write path + standalone install/doctor directory expectations.

### Implementation

- [x] `koupper-cli/src/main/kotlin/com/koupper/cli/commands/ModuleCommand.kt`
  - [x] Create `~/.koupper/helpers` when missing before writing `list.kts`.
  - [x] Return explicit error when helper resource `list.txt` is not packaged.
  - [x] Ensure stream-to-file helper creates parent directory.
- [x] `koupper/install-standalone.kts`
  - [x] Create `~/.koupper/helpers` and `~/.koupper/logs` during install.
  - [x] Add doctor checks for helpers/logs directory presence.

### Validation

- [x] `koupper-cli` compile passes: `./gradlew.bat compileKotlin`.
- [x] Standalone doctor reflects and validates new expectations: `kotlinc -script install-standalone.kts -- --doctor`.
- [x] Fresh-profile runtime verification completed after fixes and release update (`koupper module demo-script` success reported by user).

### Documentation / Handoff

- [x] `docs/SESSION_STATE.md` updated with objective, completed work, and next commands.
- [x] `docs/DELIVERY_CHECKLIST.md` updated for this hotfix wave.
- [x] Opened and merged scoped hotfix PR(s).

### Publish / Release

- [x] Merged to `main` via sync PR path.
- [x] Published release `v6.5.2` with refreshed standalone assets.

## Wave: Compiled Job Worker Bug Cluster (PRs #125–#128)

### Scope

- [x] Requested scope is clear and documented (job worker compiled routing + SQS ack lifecycle).
- [x] No unrelated refactors included.

### Implementation

- [x] Code changes follow existing architecture and patterns.
- [x] Backward compatibility maintained — `JobResult.Ok(configName, task)` still compiles; `ackFn`/`releaseFn` default to `null`.
- [x] Migration note added: `docs/migrations/2026-04-job-worker-compiled-routing.md`.

### Validation

- [x] All `orchestrator-core` tests pass (`JobRunnerTest`, `CompiledClassResolutionTest`, paginator tests).
- [x] Local quick checks pass on feature branches before PR.
- [x] CI gates pass on GitHub (fast-checks-linux, Providers core/CLI consistency).
- [x] igly-comms local worker smoke: resolved classes, successful job execution confirmed.

### Documentation

- [x] No user-facing docs needed (internal framework fix).
- [x] Internal migration note written.
- [x] `docs/SESSION_STATE.md` updated.

### Release Flow

- [x] All branches followed `fix/*` naming.
- [x] PRs opened via fast-lane or `gh pr create`.
- [x] CI green before each merge.
- [x] Local `develop` synced after all merges.

---

## Wave: Koupper 6.4.0 Release Gate

### Scope

- [x] All 6.4.0 features merged to develop.
- [x] GitHub Release `v6.4.0` created (tag on develop).

### Validation

- [x] Smoke suite CI gates defined for `main` PRs.
- [x] PR #122 (`develop → main`) closed as stale after history rewrite.
- [x] Replacement sync PR #129 merged to align `main` content with rewritten `develop`.
- [x] `v6.4.0` tag moved to `main` (`f00f0552a6fa7b43bf7a455774ea68c8c33ca649`).

### Documentation

- [x] Internal agent guidance docs updated with 6.4.0 architecture overview.
- [x] `docs/NEXT_FEATURES_NOTES.md` updated with completed items.
- [x] `koupper-document` provider docs updated (secrets, observability, git, docker, k8s, mcp).
- [x] `koupper.com/docs` deployed via `deploy-docs.kts`.

### Release closeout notes

- [x] Commit message history cleanup applied on `develop` (Claude/Anthropic references removed).
- [x] PR descriptions cleanup applied for PRs #117-#128.
- [x] Main branch ruleset re-enabled after merge/tag operations.

---

## Standing checks (apply to every session)

- [x] `koupper run scripts/agent/validate.kts '{}'` returns `ok: true`.
- [x] `koupper run scripts/agent/preflight.kts '{}'` returns `ok: true`.
- [x] `docs/SESSION_STATE.md` updated before ending session.

---

## Wave: Standalone Release Installer Validation (PRs #148–#149)

### Scope

- [x] Validate standalone release installer path end-to-end (no repo clone required).
- [x] Keep changes limited to release asset packaging + install verification.

### Implementation

- [x] PR #148 merged: standalone installer + release asset publishing workflow.
- [x] PR #149 merged: fixed `SHA256SUMS` generation to use relative filenames.
- [x] Tags published for validation flow:
  - [x] `v6.5.0` (initial standalone asset publish)
  - [x] `v6.5.1` (checksum packaging hotfix release)

### Validation

- [x] `Publish Install Assets` workflow passed for `v6.5.0`.
- [x] Reproduced standalone install checksum failure on `v6.5.0` (expected after discovery).
- [x] `Publish Install Assets` workflow passed for `v6.5.1` after hotfix.
- [x] Local standalone install succeeded from `v6.5.1`.
- [x] Standalone doctor checks passed from `v6.5.1`.

### Documentation / Handoff

- [x] `docs/SESSION_STATE.md` updated with branch/PR/tag status and resume commands.
- [ ] Optional follow-up: annotate `v6.5.0` release notes as superseded by `v6.5.1` for standalone users.

---

## Wave: Maintainer Workspace Bootstrap + Install Docs Alignment

### Scope

- [x] Define a maintainer flow that initializes the full multi-repo workspace from zero.
- [x] Align install documentation across user and maintainer modes.

### Implementation

- [x] Added `scripts/setup/workspace-bootstrap.sh`.
- [x] Added `scripts/setup/workspace-bootstrap.ps1`.
- [x] Updated `scripts/setup/README.md` with multi-repo bootstrap usage.
- [x] Updated install guidance in:
  - [x] `README.md` (workspace root)
  - [x] `koupper/README.md`
  - [x] `koupper-document/docs/getting-started.md`
  - [x] `koupper-document/docs/production/troubleshooting.md`
  - [x] `koupper-document/docs/commands/provider.md`
  - [x] `koupper-document/docs/production/script-execution-checklist.md`

### Validation

- [x] Verified release-based standalone installer succeeds on `v6.5.1`.
- [x] Confirmed new bootstrap scripts are present in setup directory.
- [x] Run bootstrap scripts end-to-end on clean path (PowerShell smoke confirmed).

### Documentation / Publish

- [x] `docs/SESSION_STATE.md` updated for this wave.
- [x] Open/merge PRs for `koupper-workspace`, `koupper`, and `koupper-document` doc/setup changes.
- [x] Deploy `koupper-document` to publish updates to `koupper.com`.

---

## Wave: Workspace Bootstrap Clone-First Regression Fix (2026-04-23)

### Scope

- [x] Fix one-command bootstrap failure on fresh workspace clone (`install.kts not found in workspace root or ./koupper`).
- [x] Keep fix constrained to setup scripts ordering (clone child repos before installer-path validation).

### Implementation

- [x] Updated `scripts/setup/workspace-bootstrap.ps1` to clone `koupper`, `koupper-cli`, `koupper-document` before install script resolution.
- [x] Updated `scripts/setup/workspace-bootstrap.sh` with the same clone-first ordering.
- [x] Merged on `develop` and `main` (`koupper-jvm/koupper-workspace#11`, `#13`).

### Validation

- [x] Clean-path bootstrap smoke passed with `./scripts/setup/workspace-bootstrap.ps1 -Workspace <clean-path> -DoctorOnly -Pull`.
- [x] User rerun from documented flow succeeded after pulling updated script.
