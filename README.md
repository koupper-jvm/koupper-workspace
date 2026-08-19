# Koupper

Kotlin scripting runtime + CLI for production automation: workers, runtime routes, Service Providers, and deploy.

- **Current release:** [v7.2.1](https://github.com/koupper-jvm/koupper/releases/tag/v7.2.1)
- **Install / upgrade:** download `install-standalone.kts` from the latest release and run with `--force`
- **Docs:** https://koupper.com/getting-started.html
- **Contribute:** [koupper/CONTRIBUTING.md](https://github.com/koupper-jvm/koupper/blob/develop/CONTRIBUTING.md)
- **License:** [MIT](LICENSE)
- **Distribution:** GitHub Releases + mavenLocal (`com.koupper:octopus-api`) — not Maven Central

```bash
curl -L -o install-standalone.kts https://github.com/koupper-jvm/koupper/releases/latest/download/install-standalone.kts
kotlinc -script install-standalone.kts -- --force
```

Windows PowerShell:

```powershell
Invoke-WebRequest -Uri "https://github.com/koupper-jvm/koupper/releases/latest/download/install-standalone.kts" -OutFile "install-standalone.kts"
kotlinc -script .\install-standalone.kts -- --force
```

Then `koupper -v` should print **cli 7.2.1** / **octopus engine 7.2.1**.

End users should **not** clone this repository. Clone [koupper](https://github.com/koupper-jvm/koupper) only if you are contributing to the engine. This repo is the **maintainer workspace** (release scripts, examples, nested checkouts).

---

## Maintainer workspace

`koupper/`, `koupper-cli/`, and `koupper-docs/` are **not** in this git tree. Bootstrap clones them:

```powershell
git clone https://github.com/koupper-jvm/koupper-workspace.git
cd koupper-workspace
./scripts/setup/workspace-bootstrap.ps1 -Workspace (Get-Location).Path -Pull
```

```bash
git clone https://github.com/koupper-jvm/koupper-workspace.git
cd koupper-workspace
bash ./scripts/setup/workspace-bootstrap.sh --workspace "$(pwd)" --pull
```

`scripts/setup/install.ps1` / `install.sh` now run that bootstrap automatically when the nested repos are missing.

Verify:

```bash
kotlinc -script install-workspace.kts -- --doctor
koupper --version
```

| Path | Repo | Role |
|------|------|------|
| `koupper/` | [koupper-jvm/koupper](https://github.com/koupper-jvm/koupper) | Octopus engine + providers |
| `koupper-cli/` | [koupper-jvm/koupper-cli](https://github.com/koupper-jvm/koupper-cli) | CLI |
| `koupper-docs/` | [koupper-jvm/koupper-docs](https://github.com/koupper-jvm/koupper-docs) | Public docs → koupper.com |
| `examples/` | this repo | Runnable reference scripts |
| `docs/` | this repo | Maintainer playbooks |

Branch from `develop`. Prefer `koupper run scripts/release/fast-lane.kts` over ad-hoc git/gh flows.
