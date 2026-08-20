# Design: `koupper module` V7 DX

**Date:** 2026-07-21  
**Repos:** `koupper` + `koupper-cli`  
**Branch:** `fix/module-command-v7-router-dx`

## Goal

Make `koupper module` useful for V7 Gradle apps that use `RuntimeRouterDsl` + `@Export` scripts (e.g. igly-comms), without requiring legacy Jersey/`KHandler` layouts.

## Scope (Approach B)

### Octopus engine
1. Quiet scripting: `koupper.scripting.quiet=true` suppresses `[DEBUG] Compiling/Loading` prints.
2. `RouterAnalyzer`: when no `KHandler` is present, resolve handler from `script { ::fn }` / nested `fun handle`.
3. `ModuleAnalyzer`: report V7 routes + `@Export` symbols; keep legacy handler discovery as secondary.

### CLI
1. `ModuleCommand` sets quiet before running `list.kts`.
2. Display V7 routes from analyzer payload (not only Jersey `controllers.json`).
3. Detect octopus from `build.gradle(.kts)` Maven coords as well as `libs/octopus-*.jar`.

## Out of scope
- igly-comms `init.kts` placeholders (consumer follow-up)
- Requiring `.http.json` for V7 apps

## Success
- `koupper module` in igly-comms shows newsletter/health routes
- Minimal DEBUG noise
- Maven octopus version shown when present in Gradle deps
