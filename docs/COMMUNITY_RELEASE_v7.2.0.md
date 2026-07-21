# Community Release v7.2.0 — Checklist

Goal: anyone on Windows / Linux / macOS can install from GitHub Releases (no repo clone).

Public install (after release):

```bash
curl -L -o install-standalone.kts https://github.com/koupper-jvm/koupper/releases/latest/download/install-standalone.kts
kotlinc -script install-standalone.kts -- --force
kotlinc -script install-standalone.kts -- --doctor
koupper -v
```

## Order (do not skip)

### 1) Land release prep on `develop`

Repos / branches:

| Repo | Branch | What |
|---|---|---|
| `koupper` | `release/v7.2.0-community` | shadowJar publish workflow + router defensive + CHANGELOG |
| `koupper-cli` | `release/v7.2.0-community` | CHANGELOG → `[7.2.0]` |
| `koupper-workspace` | `release/v7.2.0-community` | this checklist + SESSION_STATE |

Merge each PR into `develop` (squash OK).

### 2) Sync `develop` → `main` (all 3 repos)

Create PRs:

- https://github.com/koupper-jvm/koupper/compare/main...develop
- https://github.com/koupper-jvm/koupper-cli/compare/main...develop
- https://github.com/koupper-jvm/koupper-workspace/compare/main...develop

Wait for **main** CI (smoke + install/uninstall where configured).

### 3) Tag + publish install assets

On **koupper** `main` (this triggers `Publish Install Assets`):

```bash
cd koupper
git checkout main && git pull
koupper run ../scripts/release/tag-release.kts '{"version":"7.2.0","push":true,"dryRun":false}'
```

On **koupper-cli** `main` (same tag for alignment; publish workflow checks out CLI `main`):

```bash
cd koupper-cli
git checkout main && git pull
git tag -a v7.2.0 -m "release v7.2.0"
git push origin v7.2.0
```

Confirm release assets exist:

https://github.com/koupper-jvm/koupper/releases/tag/v7.2.0

Must include: `install-standalone.kts`, `octopus.jar`, `koupper-cli.jar`, `model-project.zip`, `providers.json`, `SHA256SUMS`, `manifest.json`.

### 4) Smoke install (clean profile recommended)

Windows PowerShell:

```powershell
Invoke-WebRequest -Uri "https://github.com/koupper-jvm/koupper/releases/latest/download/install-standalone.kts" -OutFile "install-standalone.kts"
kotlinc -script .\install-standalone.kts -- --force
kotlinc -script .\install-standalone.kts -- --doctor
koupper -v
koupper doctor
```

Linux/macOS: same curl flow from README.

### 5) Optional

- Deploy docs: `koupper run scripts/deploy/deploy-docs.kts '{"dryRun": false}'`
- Close stale `fix/v7-defensive-router` (ported into `RouteDispatcher` for v7.2.0)

## Notes

- Latest public release before this work: **v6.5.3** — `releases/latest` will flip to **v7.2.0** after the tag publish job succeeds.
- Publish workflow now builds `:octopus:shadowJar` (fatJar removed in v7).
