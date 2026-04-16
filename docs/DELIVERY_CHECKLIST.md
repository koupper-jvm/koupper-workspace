# Delivery Checklist

_Last updated: 2026-04-15 (end of day)_

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

### Multi-repo readiness snapshot

- [x] `koupper`: `main` and `develop` aligned at `522670803c29edd11b9bf3224689097ba3ab803a`; `v6.4.0` retargeted and verified.
- [x] `koupper-cli`: `main` and `develop` aligned; `v4.5.0` tag present.
- [x] `koupper-docs`: sync flow completed; `docs-v6.4.0` tag present.
- [x] Open PRs across `koupper`, `koupper-cli`, `koupper-docs`: none.
- [x] AI-trace checks for commit messages/PR text re-run for active branches and current metadata.

---

## Standing checks (apply to every session)

- [x] `koupper run scripts/agent/validate.kts '{}'` returns `ok: true`.
- [x] `koupper run scripts/agent/preflight.kts '{}'` returns `ok: true`.
- [x] `docs/SESSION_STATE.md` updated before ending session.
