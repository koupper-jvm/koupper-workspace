# Delivery Checklist

_Last updated: 2026-04-22_

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
- [ ] Run bootstrap scripts end-to-end on a clean machine (Linux/macOS + Windows).

### Documentation / Publish

- [x] `docs/SESSION_STATE.md` updated for this wave.
- [ ] Open/merge PRs for `koupper-infrastructure`, `koupper`, and `koupper-document` doc changes.
- [ ] Deploy `koupper-document` to publish updates to `koupper.com`.
