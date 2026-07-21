---
name: prime
description: Bootstrap a Koupper session with project context. Use at the start of any session, or when the user says "prime", "load context", "bootstrap", or "start session".
disable-model-invocation: true
allowed-tools: Read Bash(git log *) Bash(git branch *) Bash(git status *)
---

Bootstrap this Koupper session.

## Repository state

```!
git branch --show-current
git log --oneline -8
git status --short
```

## Read in order (stop at first missing file)

1. `CLAUDE.md` — project rules, build commands, architecture
2. `docs/SESSION_STATE.md` — last session checkpoint (if present)
3. `docs/NEXT_FEATURES_NOTES.md` — near-term priorities

## Respond with

- **Branch:** [current branch]
- **Recent commits:** [last 5, one-line each]
- **Pending work:** [from SESSION_STATE if present, else "none"]
- **Top priority:** [first item from NEXT_FEATURES_NOTES]

Keep response under 250 words. Read nothing else unless asked.
