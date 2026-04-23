# Agent Reception

This is the single startup file for every agent session in this workspace.

## Startup checklist (always execute in order)

1. Read this file fully.
2. Read `docs/SESSION_STATE.md` — get current objective, pending tasks, active PRs.
3. Read `docs/AGENT_BOOTSTRAP.md` — required follow-up reads by task scope.
4. Run preflight (from repo root):
   ```bash
   cd "C:\Users\dosek\develop\koupper infrastructure"
   koupper run scripts/agent/preflight.kts '{}'
   ```
5. Run structure validation:
   ```bash
   koupper run scripts/agent/validate.kts '{}'
   ```
6. Check open PRs:
   ```bash
   gh pr list --repo koupper-jvm/koupper-workspace --state open
   gh pr list --repo koupper-jvm/koupper --state open
   ```
7. Pull latest develop in the Koupper workspace copy:
   ```bash
   cd koupper && git checkout develop && git pull origin develop && cd ..
   ```
8. If validating workspace install script, use:
   ```bash
   kotlinc -script install-workspace.kts -- --doctor
   ```
   Workspace uninstall script is `uninstall-workspace.kts`.

If any step fails, read the error before proceeding — do not skip.

---

## Workspace layout

| Path | Repo | Notes |
|------|------|-------|
| `.` (root) | `koupper-jvm/koupper-workspace` | agent workflow, release scripts, CI/deploy helpers |
| `koupper/` | `koupper-jvm/koupper` (octopus engine) | main Gradle multi-module project |
| `koupper-cli/` | `koupper-jvm/koupper-cli` | CLI module |
| `koupper-document/` | `koupper-jvm/koupper-docs` | VitePress docs site (local folder keeps legacy name) |

---

## Delivery flow (default)

1. Start from `develop` inside `koupper/`.
2. Create a scoped branch: `feature/*`, `fix/*`, or `docs/*`.
3. Implement only requested scope.
4. Build and test:
   ```bash
   cd koupper && ./gradlew :orchestrator-core:test
   ```
5. Run local checks before push:
   ```powershell
   # Windows
   ./scripts/ci/local-quick-checks.ps1 -Target all
   ```
   ```bash
   # Linux/macOS
   ./scripts/ci/local-quick-checks.sh all
   ```
6. Open PR via fast-lane (preferred):
   ```bash
   koupper run scripts/release/fast-lane.kts '{"featureBranch":"fix/my-fix","enableAutoMerge":true}'
   ```
7. Merge only when required CI checks are green.
8. Update `docs/SESSION_STATE.md` before ending session.

---

## Local install update protocol

When `octopus.jar` needs to be replaced (after rebuilding from develop):

```bash
# 1. Kill the stale daemon
powershell.exe -Command "netstat -ano | findstr :9998"
# Note the PID, then:
powershell.exe -Command "Stop-Process -Id <PID> -Force"

# 2. Rebuild (force fresh)
cd koupper && ./gradlew :octopus:fatJar -x test --rerun-tasks

# 3. Copy via PowerShell (more reliable than bash cp for large JARs on Windows)
powershell.exe -Command "Copy-Item -Path 'koupper\octopus\build\libs\octopus-6.4.0.jar' -Destination '$env:USERPROFILE\.koupper\libs\octopus.jar' -Force"
```

The next `koupper` invocation will boot a fresh daemon with the new jar.

---

## Release commands

```bash
# Fast lane (preferred — opens PR + auto-merge)
koupper run scripts/release/fast-lane.kts '{"featureBranch":"fix/my-fix","enableAutoMerge":true}'

# Manual fallback
gh pr create --repo koupper-jvm/koupper --base develop --head fix/my-fix \
  --title "fix: description" --body "## Summary"
gh pr merge --repo koupper-jvm/koupper --squash --auto fix/my-fix
```

---

## Session handoff protocol

Before stopping work, update `docs/SESSION_STATE.md` with:
- Current objective
- Completed this session (factual only)
- Pending tasks (ordered by priority)
- Branch/PR status per repo
- Next 3 exact commands to resume
- Risks/blockers
