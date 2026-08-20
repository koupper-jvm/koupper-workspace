# Community Docs + Install Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Koupper discoverable, installable, and correctly documented for any newcomer — live site, install paths, and V7 mental model all consistent with engine/CLI `7.2.0` + post-release fixes on `develop`.

**Architecture:** Five sequential phases. Phases 0–1 unblock and ship the current docs site. Phase 2 rewrites the pages that mislead V7 users. Phase 3 fixes discovery (READMEs, URLs, distribution story). Phase 4 is optional Maven Central and must not block “perfect for everyone” if GitHub Releases remain the official install channel.

**Tech Stack:** VitePress (`koupper-docs`), S3 + CloudFront (`s3://koupper-docs`, distribution `E1EOMSACJRDKWH`), Koupper deploy script (`scripts/deploy/deploy-docs.kts`), GitHub Releases (`koupper-jvm/koupper` v7.2.0), workspace bootstrap (`scripts/setup/workspace-bootstrap.*`).

**Success criteria (definition of done):**
1. `https://koupper.com/` and clean paths (`/getting-started`, `/architecture/v7-engine`) return 200.
2. `koupper run scripts/deploy/deploy-docs.kts '{"dryRun": true}'` finds the docs repo and completes without error.
3. Getting Started Option A (standalone) and Option B (workspace) match real scripts/folder names.
4. `module` + `job` docs describe V7 RuntimeRouter / `asJob` / `dispatchToQueue` patterns.
5. Landing READMEs point to the same install + docs URLs a stranger would use.
6. Distribution story is explicit: GitHub Releases (not Maven Central) unless Phase 4 lands.

**Out of scope for this plan:** Publishing engine/CLI to Maven Central (Phase 4 optional only). Releasing a new `7.2.1` tag (nice-to-have after content; not required for docs perfection). igly frontends.

---

## File map

| File | Responsibility |
|------|----------------|
| `scripts/deploy/deploy-docs.kts` | Resolve docs dir (`koupper-docs` + legacy `koupper-document`); print correct public URL |
| `scripts/setup/workspace-bootstrap.ps1` | Clone docs as `koupper-docs` (align with `.sh`) |
| `scripts/setup/README.md`, `CLAUDE.md`, `AGENTS.md`, `docs/AGENT_RECEPTION.md` | Stop advertising `koupper.com/docs` and wrong folder names |
| `koupper-docs/docs/.vitepress/config.js` | `cleanUrls: true` already set — CloudFront/S3 must honor it |
| `koupper-docs/docs/getting-started.md` | Option A/B install accuracy + prerequisites |
| `koupper-docs/docs/commands/module.md` | V7 router DX + exports |
| `koupper-docs/docs/commands/job.md` + `examples/*jobs*` | V7 `asJob` / queue / pipeline |
| `koupper-docs/docs/providers/runtime-router.md` | CORS Origin-echo behavior |
| `koupper-docs/docs/index.md` / architecture pages | Version signal `7.2.0` |
| `koupper/README.md`, workspace `README.md`, `koupper-cli/README.md` | Newcomer entry points |
| CloudFront / S3 (ops) | SPA/clean URL behavior + optional `/docs` → `/` redirect |

---

## Phase 0 — Unblock deploy tooling (must be first)

### Task 1: Fix `findDocumentDir` to accept both folder names

**Files:**
- Modify: `scripts/deploy/deploy-docs.kts`
- Modify: `scripts/deploy/README.md` (if it mentions docs deploy; else skip)

- [ ] **Step 1: Replace `findDocumentDir` so it accepts `koupper-docs` first, then legacy `koupper-document`**

Replace the function body in `scripts/deploy/deploy-docs.kts` with:

```kotlin
private fun findDocumentDir(cwd: File): File {
    val names = listOf("koupper-docs", "koupper-document")

    fun probe(base: File): File? =
        names.map { File(base, it) }.firstOrNull { it.exists() && it.isDirectory }

    probe(cwd)?.let { return it }

    var cursor: File? = cwd
    repeat(5) {
        cursor = cursor?.parentFile
        val base = cursor ?: return@repeat
        probe(base)?.let { return it }
    }
    error(
        "Docs directory not found (tried ${names.joinToString(", ")}) relative to ${cwd.absolutePath}"
    )
}
```

- [ ] **Step 2: Fix header comment + success URL to `https://koupper.com/` (not `/docs`)**

In the same file:
- Line ~9–11 comment: say builds from `koupper-docs/` (legacy alias `koupper-document/`) and publishes to `https://koupper.com/`.
- Line ~132: `println("\n✓ Deploy complete — https://koupper.com/")`

- [ ] **Step 3: Dry-run from workspace root**

```powershell
cd C:\Users\jacob\develop\tdn_workspace\quizztea_workspace\koupper-workspace
koupper run scripts/deploy/deploy-docs.kts '{"dryRun": true}'
```

Expected: prints `docs dir` pointing at `...\koupper-docs`, no error, dry-run for `npm ci`, `docs:build`, `aws s3 sync`, CloudFront invalidation.

- [ ] **Step 4: Commit on workspace branch `fix/docs-deploy-path`**

```bash
git checkout develop && git pull origin develop
git checkout -b fix/docs-deploy-path
git add scripts/deploy/deploy-docs.kts
git commit -m "fix(deploy): resolve koupper-docs and correct public docs URL"
```

---

### Task 2: Align bootstrap folder naming (PS ↔ SH)

**Files:**
- Modify: `scripts/setup/workspace-bootstrap.ps1` (uses `koupper-document` today)
- Modify: `scripts/setup/README.md`
- Modify: `docs/AGENT_RECEPTION.md` only if it hard-codes `koupper-document` as the only name

- [ ] **Step 1: Change PowerShell clone folder to `koupper-docs`**

In `workspace-bootstrap.ps1`, replace both occurrences of folder name `koupper-document` with `koupper-docs` (Ensure-ChildRepo + Update-Repo), matching `workspace-bootstrap.sh`.

- [ ] **Step 2: Update `scripts/setup/README.md` layout table**

Replace `koupper-document/` with `koupper-docs/` and note: “legacy local folder `koupper-document` still accepted by deploy-docs.kts”.

- [ ] **Step 3: Commit on same branch**

```bash
git add scripts/setup/workspace-bootstrap.ps1 scripts/setup/README.md
git commit -m "fix(setup): clone docs repo as koupper-docs on Windows"
```

---

### Task 3: Fix maintainer link rot (`/docs` → site root)

**Files:**
- Modify: `CLAUDE.md` (Deploy Docs section)
- Modify: `docs/AGENT_RECEPTION.md` if it mentions `/docs`
- Grep workspace for `koupper.com/docs` and `koupper-document` and fix remaining **public-facing** strings

- [ ] **Step 1: Grep and patch**

```powershell
rg -n "koupper\.com/docs|koupper-document" CLAUDE.md AGENTS.md docs scripts README.md
```

Replace user-facing URLs with `https://koupper.com/`. Keep historical notes only if clearly marked legacy.

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md docs scripts README.md AGENTS.md
git commit -m "docs: point maintainers at koupper.com root, not /docs"
```

- [ ] **Step 3: Open/merge PR to workspace `develop`**

User may merge via GitHub UI if `gh` is not authenticated.

---

## Phase 1 — Ship undeployed docs + fix clean URLs

### Task 4: Merge `koupper-docs` `develop` → `main`

**Repos:** `koupper-docs` (local folder `koupper-docs/`)

Known commits ahead of `main` (verify with `git log origin/main..origin/develop --oneline`):
- `v7-engine` architecture page
- `reload` command
- hello-world provider
- pathParams / router docs

- [ ] **Step 1: Sync and open PR**

```bash
cd koupper-docs
git fetch origin
git checkout develop && git pull origin develop
# create PR develop → main via GitHub UI:
# https://github.com/koupper-jvm/koupper-docs/compare/main...develop?expand=1
```

Title: `docs: sync develop to main for v7.2 community site`
Body: list the 3 commits; note deploy follows after merge.

- [ ] **Step 2: Merge when green**

- [ ] **Step 3: Pull main locally**

```bash
git checkout main && git pull origin main
```

---

### Task 5: Make cleanUrls work on S3/CloudFront

**Problem:** VitePress has `cleanUrls: true` in `koupper-docs/docs/.vitepress/config.js`, but live `/getting-started` → 404 while `/getting-started.html` → 200. S3 website hosting / CloudFront is not rewriting extensionless paths.

**Preferred approach (pick one during execution; recommended = A):**

| Option | Pros | Cons |
|--------|------|------|
| **A. CloudFront Function / viewer-request rewrite** | Keeps `cleanUrls: true`; pretty URLs | Needs AWS change on dist `E1EOMSACJRDKWH` |
| **B. Turn off `cleanUrls` + update all links to `.html`** | No AWS change | Ugly URLs; many link edits |
| **C. S3 static website error doc + redirect hacks** | Simple | Fragile; bad for SPA/assets |

- [ ] **Step 1: Confirm current CloudFront behavior**

```powershell
foreach ($u in @(
  "https://koupper.com/",
  "https://koupper.com/getting-started",
  "https://koupper.com/getting-started.html",
  "https://koupper.com/architecture/v7-engine",
  "https://koupper.com/docs"
)) {
  try {
    $r = Invoke-WebRequest -Uri $u -Method Head -MaximumRedirection 0 -TimeoutSec 15 -UseBasicParsing
    "$u -> $($r.StatusCode)"
  } catch {
    "$u -> $($_.Exception.Response.StatusCode.value__)"
  }
}
```

- [ ] **Step 2 (Option A): Add CloudFront Function rewrite**

Create function logic (pseudo — implement in AWS console or IaC if present):

```javascript
function handler(event) {
  var request = event.request;
  var uri = request.uri;
  if (uri.endsWith('/')) {
    request.uri = uri + 'index.html';
  } else if (!uri.includes('.') && uri !== '/') {
    request.uri = uri + '.html';
  }
  return request;
}
```

Associate with distribution `E1EOMSACJRDKWH` viewer-request. Publish.

- [ ] **Step 3: Add `/docs` → `/` redirect (301)**

Same function or a CloudFront custom error / behavior:
- Request URI `/docs` or `/docs/` → redirect Location `https://koupper.com/`

- [ ] **Step 4: Re-test URLs from Step 1 — all primary paths must be 200 or 301→200**

---

### Task 6: Real docs deploy

**Prereqs:** Tasks 1–5 done; AWS credentials with S3 + CloudFront access on this machine.

- [ ] **Step 1: Local quality gate**

```bash
cd koupper-docs
npm ci
npm run docs:check
npm run docs:build
```

Expected: `docs:check` exit 0; dist at `docs/.vitepress/dist`.

- [ ] **Step 2: Dry-run deploy**

```bash
cd ..   # workspace root
koupper run scripts/deploy/deploy-docs.kts '{"dryRun": true}'
```

- [ ] **Step 3: Real deploy**

```bash
koupper run scripts/deploy/deploy-docs.kts '{"dryRun": false}'
```

- [ ] **Step 4: Smoke live site**

Verify 200:
- `https://koupper.com/`
- `https://koupper.com/getting-started`
- `https://koupper.com/architecture/v7-engine`
- `https://koupper.com/commands/module`
- `https://koupper.com/commands/job`

---

## Phase 2 — Content accuracy (V7 mental model)

### Task 7: Fix Getting Started Option B + prerequisites

**Files:**
- Modify: `koupper-docs/docs/getting-started.md`

- [ ] **Step 1: Add prerequisites block at top of Install section**

```markdown
### Prerequisites

- **Java 17+** (`java -version`)
- **Kotlin compiler** (`kotlinc -version`) — required to run `install-standalone.kts`
- ~350 MB free disk for `octopus.jar`
```

- [ ] **Step 2: Fix Option B paths**

Replace references to:
- `koupper-document` → `koupper-docs`
- `./koupper/install.kts` doctor → workspace-accurate path:

```markdown
### Option B: Developers/maintainers (full workspace)

Clone/bootstrap the workspace (see [koupper-workspace](https://github.com/koupper-jvm/koupper-workspace)), then:

```bash
# from workspace root
kotlinc -script install-workspace.kts -- --doctor
```

Windows:

```powershell
kotlinc -script .\install-workspace.kts -- --doctor
```

Bootstrap clones/updates: `koupper`, `koupper-cli`, `koupper-docs`.
```

Verify `install-workspace.kts` exists at workspace root before publishing this text; if the entrypoint differs, use the real script name from `docs/AGENT_RECEPTION.md`.

- [ ] **Step 3: Add explicit version line**

Near the top:

```markdown
> Current community release: **v7.2.0** ([GitHub Release](https://github.com/koupper-jvm/koupper/releases/tag/v7.2.0)).
```

- [ ] **Step 4: `npm run docs:check` + commit on `docs/getting-started-v720`**

```bash
cd koupper-docs
git checkout develop && git pull
git checkout -b docs/getting-started-v720
git add docs/getting-started.md
git commit -m "docs(getting-started): fix Option B paths and state v7.2.0"
```

---

### Task 8: Rewrite `koupper module` docs for V7

**Files:**
- Modify: `koupper-docs/docs/commands/module.md`
- Reference (read-only): `docs/superpowers/specs/2026-07-21-module-command-v7-dx-design.md` (workspace)
- Reference: live CLI behavior after local install

- [ ] **Step 1: Capture current CLI output as source of truth**

```bash
# from a small V7 sample module if available, else document expected sections
koupper module --help
```

- [ ] **Step 2: Restructure `module.md`**

Keep Usage. Replace Jersey/KHandler-centric analysis section with:

1. **What it reports**
   - Module metadata / Gradle octopus dependency
   - `@Export` entrypoints (V7)
   - RuntimeRouter routes (method + path)
   - Scripts / workers when present
2. **Legacy note** (short): older modules may still show `KHandler` / `RequestHandler` under `handlers/` — secondary.
3. **Troubleshooting**: keep useful scripting diagnostics; trim noise.

Sample content outline to write into the file:

```markdown
## Analyze a V7 module

```bash
koupper module my-app
```

Typical output sections:

- **Octopus / engine**: detected via Gradle dependency (not only `libs/octopus.jar`)
- **Exports**: functions annotated with `@Export`
- **Routes**: routes registered through RuntimeRouter / DSL (`GET /path`, etc.)

> Tip: after changing routes, rebuild the module JAR so analysis and runtime stay in sync.
```

- [ ] **Step 3: Commit**

```bash
git add docs/commands/module.md
git commit -m "docs(module): document V7 RuntimeRouter and export scan"
```

---

### Task 9: Document V7 jobs (`asJob` / queue / pipeline)

**Files:**
- Modify: `koupper-docs/docs/commands/job.md`
- Modify or create: `koupper-docs/docs/examples/jobs-worker-flow.md` (if present; else add a short section under examples index)
- Reference: workspace rule / examples using `::fn.asJob(...).dispatchToQueue()` and `ScriptExecutor.runPipeline`

- [ ] **Step 1: Keep existing CLI `job list` / worker docs**

Do not delete CLI job commands — they remain valid.

- [ ] **Step 2: Add “V7 programmatic jobs” section to `job.md`**

```markdown
## V7 programmatic jobs (in-module)

Dispatch work to the local job queue from Kotlin (compiled module or script):

```kotlin
::processDiagnosis.asJob(
    mapOf("ticketId" to ticketId)
).dispatchToQueue()
```

Orchestrate steps:

```kotlin
ScriptExecutor.runPipeline(
    listOf(
        ::step1,
        ::step2.dependsOn(::step1)
    ),
    async = false
)
```

### Success vs failure semantics

- A job lands in **done** when the function returns normally — even if you return an `OperationResponse(500)`.
- To mark **failed**, **throw** (or rethrow) from the job function.

### Working directory

`dispatchToQueue()` resolves paths from the **JVM working directory**. In IntelliJ, set Run Configuration Working Directory to the module root (not the parent multi-project folder).
```

- [ ] **Step 3: Cross-link from examples index if one exists**

- [ ] **Step 4: Commit**

```bash
git add docs/commands/job.md docs/examples
git commit -m "docs(jobs): document V7 asJob, pipeline, and failure semantics"
```

---

### Task 10: CORS + version signal on RuntimeRouter page

**Files:**
- Modify: `koupper-docs/docs/providers/runtime-router.md`
- Modify: `koupper-docs/docs/architecture/v7-engine.md` (ensure it says 7.2.0+ if present on develop)

- [ ] **Step 1: Expand CORS from one-liner**

```markdown
## CORS

Configure allowed origins on the router (list form supported):

```kotlin
allowedOrigins = listOf(
    "http://localhost:5173",
    "https://app.example.com"
)
```

Behavior: if the request `Origin` matches an allowed entry, the response echoes **that** origin (browsers reject a comma-joined allow list). Use `*` only when intentionally wide-open.
```

- [ ] **Step 2: Commit**

```bash
git add docs/providers/runtime-router.md docs/architecture
git commit -m "docs(router): document multi-origin CORS echo behavior"
```

---

### Task 11: Merge content PR + redeploy

- [ ] **Step 1: PR `docs/getting-started-v720` (or squash branch) → `koupper-docs` `develop`, then → `main`**
- [ ] **Step 2: Redeploy**

```bash
koupper run scripts/deploy/deploy-docs.kts '{"dryRun": false}'
```

- [ ] **Step 3: Manual read-through of the five pages in Task 6 smoke list**

---

## Phase 3 — Discovery for strangers

### Task 12: Engine README as primary public entry

**Files:**
- Modify: `koupper/README.md`

- [ ] **Step 1: Ensure above-the-fold has**
  1. One-line what Koupper is
  2. Badge / link to `https://koupper.com/`
  3. Standalone install (curl + PowerShell) identical to Getting Started Option A
  4. Link to release `v7.2.0`
  5. Explicit: “Distributed via GitHub Releases (not Maven Central)”

- [ ] **Step 2: Commit on `koupper` branch `docs/readme-community-entry` → PR to `develop`**

---

### Task 13: Workspace README bilingual / dual audience

**Files:**
- Modify: workspace root `README.md`

- [ ] **Step 1: Add English “Community users” section at top** pointing to GitHub Release install + `https://koupper.com/getting-started` — keep Spanish maintainer content below or clearly separated.

- [ ] **Step 2: Commit on workspace `docs/readme-community`**

---

### Task 14: CLI README minimum viable

**Files:**
- Modify: `koupper-cli/README.md`

- [ ] **Step 1: Replace empty/near-empty README with**

```markdown
# koupper-cli

Terminal client for the Koupper (Octopus) engine.

## Install

Prefer the [standalone installer](https://github.com/koupper-jvm/koupper/releases/latest) (installs CLI + engine together).

Docs: https://koupper.com/

## Common commands

```bash
koupper -v
koupper doctor
koupper run script.kts
koupper module <name>
koupper provider list
```
```

- [ ] **Step 2: PR to `koupper-cli` `develop`**

---

### Task 15: Newcomer install smoke (clean profile)

- [ ] **Step 1: On a clean user profile or temp HOME (or document manual run)**

Windows:

```powershell
Invoke-WebRequest -Uri "https://github.com/koupper-jvm/koupper/releases/latest/download/install-standalone.kts" -OutFile "install-standalone.kts"
kotlinc -script .\install-standalone.kts -- --force
kotlinc -script .\install-standalone.kts -- --doctor
koupper -v
koupper doctor
```

Expected: doctor green; versions `7.2.0`.

- [ ] **Step 2: Record result in `docs/COMMUNITY_RELEASE_v7.2.0.md` checklist (mark smoke done) or SESSION_STATE**

---

## Phase 4 — Optional (do not block “perfect”)

### Task 16 (optional): Maven Central decision

**Recommendation:** **Do not publish to Maven Central in this plan.** Instead document clearly that app modules consume octopus via local/GitHub release artifacts / `mavenLocal` after publishing from source.

If later you want Central:
1. Sonatype account + namespace `com.koupper`
2. Signing keys + Gradle `maven-publish` to Central
3. Separate release plan — not mixed with docs deploy

- [ ] **Step 1: Add “Distribution” callout to Getting Started + engine README (if not already done in Tasks 7/12)**
- [ ] **Step 2: Explicitly mark Maven Central as “not published; use GitHub Releases”**

### Task 17 (optional): Patch release `7.2.1`

Only if you want GitHub Release jars to include post-`7.2.0` develop fixes (module DX, jobs compiled routing, CORS) without requiring users to build from source.

Follow `docs/COMMUNITY_RELEASE_v7.2.0.md` adapted to `7.2.1` (develop → main → tag → publish assets → smoke).

---

## Suggested calendar (aggressive)

| Day | Focus |
|-----|--------|
| Day 1 | Phase 0 (Tasks 1–3) + Task 4 PR |
| Day 1–2 | Task 5 CloudFront + Task 6 deploy |
| Day 2–3 | Phase 2 content (Tasks 7–11) + redeploy |
| Day 3 | Phase 3 READMEs + smoke (Tasks 12–15) |
| Later | Phase 4 if desired |

---

## Self-review checklist

| Gap from audit | Task(s) |
|----------------|---------|
| Wrong public URL `/docs` | 1, 3, 5 |
| `deploy-docs.kts` looks for `koupper-document` | 1 |
| Bootstrap PS/SH folder mismatch | 2 |
| Undeployed develop docs | 4, 6 |
| cleanUrls 404 | 5 |
| Stale Getting Started Option B | 7 |
| Module docs legacy | 8 |
| Jobs docs missing V7 | 9 |
| CORS one-liner | 10 |
| Version not stated | 7, 12 |
| Maven Central confusion | 12, 16 |
| README / CLI empty | 12–14 |
| Install unproven in wild | 15 |

No intentional placeholders remain; CloudFront Function code is concrete enough to paste into AWS. Exact `install-workspace.kts` wording in Task 7 must be verified against workspace root at execution time (agent: `Test-Path install-workspace.kts` before editing).

---

## Execution handoff

Plan saved to `docs/superpowers/plans/2026-07-22-community-docs-readiness.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks  
2. **Inline Execution** — same session, batch with checkpoints  

Which approach?
