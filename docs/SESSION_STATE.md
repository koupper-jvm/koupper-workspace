# Session State

_Last updated: 2026-04-23 (main alignment + bootstrap/release/docs closeout)_

## Current Objective

Complete installer/bootstrap reliability closure so clean-machine setup works via documented one-command flows, with fixes merged on `main`, release assets updated, and docs published.

## Completed This Session

- Merged module helper-path hardening in `koupper-cli` (`koupper-jvm/koupper-cli#4`) and synced to `main` (`koupper-jvm/koupper-cli#5`).
- Merged standalone installer hardening in `koupper` (`koupper-jvm/koupper#155`) and ensured `main` carries the same content.
- Published release tag `v6.5.2` on `main` and verified `Publish Install Assets` success for that tag.
- Verified `v6.5.2` assets include installer + jars + checksums (`install-standalone.kts`, `koupper-cli.jar`, `octopus.jar`, `model-project.zip`, `providers.json`, `SHA256SUMS`).
- Merged docs fixes for install/troubleshooting (`koupper-jvm/koupper-docs#45`) and synced to `main` (`koupper-jvm/koupper-docs#46`).
- Merged workspace bootstrap alignment (`koupper-jvm/koupper-workspace#8`, `#9`).
- Fixed bootstrap clean-clone regression (clone child repos before `install.kts` validation):
  - develop: `koupper-jvm/koupper-workspace#11`
  - main: `koupper-jvm/koupper-workspace#13`
- Deployed docs to production via `scripts/deploy/deploy-docs.kts` and triggered CloudFront invalidation (`ICRP688SHZ4Z0DH2RUW3F0QBAT`).
- Confirmed one-command workspace bootstrap smoke works from a clean path using PowerShell script.

## Pending Tasks (priority order)

1. Optional: annotate legacy `v6.5.0` release as superseded by `v6.5.2` to reduce user confusion.
2. Optional: run one additional external-machine smoke and attach log evidence to release notes.

## Branch / PR Status

| Repo | Status |
|---|---|
| `koupper` | No open PRs; `v6.5.2` latest release on `main` |
| `koupper-cli` | No open PRs; module helpers fix in `main` |
| `koupper-docs` | No open PRs; install/troubleshooting updates in `main` |
| `koupper-workspace` | No open PRs; bootstrap clone-first path fix in `main` |

## Next 3 Commands to Resume

```bash
# 1) Confirm latest release metadata
gh release view v6.5.2 --repo koupper-jvm/koupper

# 2) Confirm no open PR backlog across core repos
gh pr list --repo koupper-jvm/koupper --state open

# 3) Run fresh-machine bootstrap smoke (PowerShell path)
./scripts/setup/workspace-bootstrap.ps1 -Workspace (Get-Location).Path -Pull
```

## Risks / Blockers

- No critical blockers remain for installer/bootstrap path.
- `main`/`develop` commit counts may differ because of squash merges; content parity for delivered fixes is maintained.
