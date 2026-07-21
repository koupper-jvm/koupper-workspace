---
name: release
description: Execute the Koupper release workflow via fast-lane or release-flow scripts. Use when the user says "release", "ship", "create PR", "fast lane", or "push to develop".
disable-model-invocation: true
argument-hint: [feature branch name, e.g. "feature/my-change"]
---

Execute the Koupper release workflow for: $ARGUMENTS

## Pre-flight check

```!
git branch --show-current
git status --short
```

Abort if there are uncommitted changes. Stash or commit first.

## Step 1 — Local checks (mandatory)

```bash
./scripts/ci/local-quick-checks.sh all
```

Fix any failures before proceeding. Do NOT bypass with `--no-verify`.

## Step 2 — Fast-lane (preferred)

```bash
koupper run scripts/release/fast-lane.kts '{"featureBranch":"$ARGUMENTS","enableAutoMerge":true}'
```

## Fallback — if fast-lane unavailable

```bash
koupper run scripts/release/release-flow.kts '{"featureBranch":"$ARGUMENTS","waitForCi":true,"mergeAfterCi":false}'
```

## CI gates to watch

PRs → `develop`: `fast-checks-linux`, `Providers core consistency`, `Providers CLI consistency`

Merge only when all gates conclude `success`. See `scripts/release/README.md` for flag reference.
