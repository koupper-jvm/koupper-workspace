# Agent Bootstrap

This file defines required follow-up reads and operational defaults after startup.

Single startup entrypoint is `docs/AGENT_RECEPTION.md`.

## Session defaults

- Base branch: `develop`
- Feature branches: `feature/*`, `fix/*`, `docs/*`
- Keep scope strict; avoid unrelated refactors
- Use release scripts (no manual ad-hoc git/gh flows)
- Treat `docs/SESSION_STATE.md` as current truth for active release/tag/PR status before taking action.

## Required follow-up reads by scope

- Always read next:
  - `docs/NEXT_FEATURES_NOTES.md`
  - `scripts/release/README.md`
- If task touches docs/release policy, also read:
  - `koupper-document/docs/production/release-workflow.md`
- If task touches maintainer governance/policy, also read:
  - `docs/MAINTAINER_GUIDE.md`
- If task touches framework maturity/enterprise hardening, also read:
  - `docs/KOUPPER_FRAMEWORK_MATURITY_PLAYBOOK.md`

## Delivery policy

- `develop` PRs: fast checks only
  - `fast-checks-linux`
  - `Providers core consistency`
  - `Providers CLI consistency`
- `main`/release: heavy validation
  - `smoke-windows`
  - `smoke-linux`
  - `install-uninstall-e2e-windows`
- `main` governance: active ruleset enforces PR path, linear history, and code scanning gate.

## Recommended execution mode

For high-velocity feature delivery to `develop`:

```bash
koupper run scripts/release/fast-lane.kts '{"featureBranch":"feature/my-change","enableAutoMerge":true}'
```

Fallback when needed:

```bash
koupper run scripts/release/release-flow.kts '{"featureBranch":"feature/my-change","waitForCi":false,"mergeAfterCi":false}'
```

## Local pre-push checks

Windows:

```powershell
./scripts/ci/local-quick-checks.ps1 -Target all
```

Linux/macOS:

```bash
./scripts/ci/local-quick-checks.sh all
```

## If X happens, update Y

| If this changes... | Update this file | Why |
| --- | --- | --- |
| Branching/CI/release policy | `AGENTS.md` | Global agent rules must stay accurate |
| Startup commands or default execution path | `docs/AGENT_BOOTSTRAP.md` | New agent sessions need correct boot instructions |
| Upcoming priorities/roadmap | `docs/NEXT_FEATURES_NOTES.md` | Keeps next feature wave aligned |
| Release script flags/behavior | `scripts/release/README.md` | Prevents wrong command usage |
| Public release process docs | `koupper-document/docs/production/release-workflow.md` | Keeps docs synced with real process |

## Practical cadence

- Update `docs/NEXT_FEATURES_NOTES.md` at the end of each feature wave.
- Update release docs/scripts in the same PR when release behavior changes.
- Keep `AGENTS.md` stable; only edit when policy truly changes.
- Update `docs/SESSION_STATE.md` and `docs/DELIVERY_CHECKLIST.md` at session end with factual branch/PR/tag status.
