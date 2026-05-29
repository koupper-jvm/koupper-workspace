# IGLY CORTEX — Strategic Vision
_For agent consumption. Written to be picked up cold by any agent or developer._

---

## What is this document

This document defines what CORTEX is today, what exists in the codebase, who the competition is, the gap analysis, and the concrete roadmap to surpass them. It is meant to be the single source of truth for strategic decisions on this project.

---

## What CORTEX is

**IGLY CORTEX** is a local AI agent runtime built on top of the **Koupper framework**. It runs entirely on local infrastructure — no cloud, no subscriptions, no external API calls for inference.

It is NOT just a chatbot. It is an **agent orchestration operating system** that:
- Runs a local LLM (via llama.cpp / llama-server with any `.gguf` model)
- Executes autonomous agents as background jobs
- Schedules agents on cron/rate/once triggers
- Exposes a real-time web dashboard for monitoring
- Connects to external tools via MCP (Model Context Protocol)

The product lives in the `igly/cortex` branch. The underlying framework is open-source in the `develop` branch of `koupper` and `koupper-cli`.

---

## Current architecture (as of 2026-05-29)

### Runtime components

```
koupper start
  ├── Worker daemon        polls ~/.koupper/jobs/, executes .kts agents
  ├── CortexWebUiAgent     dashboard at http://localhost:18083
  └── Monitor TUI          Lanterna terminal UI
```

### Stack

| Component | Technology | Port |
|---|---|---|
| Octopus daemon | JVM, Kotlin scripting runtime | 9998 (socket) |
| LLM server | llama.cpp llama-server | 8081 |
| MCP server | JSON-RPC 2.0 (Koupper LocalMCPServerProvider) | 18082 |
| Web UI | Grizzly HTTP, SSE | 18083 |

### Installed agents

| Agent | Does | Status |
|---|---|---|
| `CortexAgent.kts` | LLM inference loop, MCP tools, command bridge | Working |
| `CortexWebUiAgent.kts` | Real-time dashboard, job history, SSE | Working |
| `GreetingAgent.kts` | Swarm state analysis on startup | Working |
| `AgentCreatorAgent.kts` | Interactive wizard to scaffold new agents | Working — generates TODO stubs |

### CLI commands available

```bash
koupper start              # start full stack
koupper worker             # job worker daemon
koupper worker --status    # queue snapshot without starting daemon
koupper schedule add/list/remove/enable/disable
koupper doctor             # health diagnostic
koupper monitor            # TUI dashboard
```

### Service Providers used by agents

- `InferenceEngine` — local LLM via LlamaServerSidecar, SSE streaming, TokenListener
- `HtppClient` — HTTP GET/POST (used for MCP local server calls)
- `MCPClientProvider` — connects to external MCP servers (Playwright, GitHub, filesystem, etc.)
- `CommandBridgeProvider` — file-based command channel (watches commands/wizard/*.response)

### MCP tools (CortexMcpServer, port 18082)

9 tools including: `list_agents`, `run_agent`, `list_jobs`, `get_job_status`, `create_schedule`, `swarm_run`, and others.

### Job system

```
~/.koupper/jobs/<queue>/
  <jobId>.json              → PENDING
  <jobId>.json.processing   → PROCESSING
  .failed/<jobId>.json      → FAILED (up to maxRetries)
  .dead/<jobId>.json        → DEAD (exceeded maxRetries)

~/.koupper/jobs/.history.jsonl  → DONE/DEAD history (last 500)
```

Worker detects script errors even when exit code is 0 (missing `@Export`, compilation errors, etc.).

---

## What is working well

1. **Local-first by design** — zero external dependencies for inference
2. **Job queue system** — atomic claiming, timeout, dead-letter, history, real-time dashboard
3. **Schedule system** — cron/rate/once, persisted in JSON, integrates with worker
4. **MCP ecosystem** — connects to any MCP-compliant server (Playwright, GitHub, etc.)
5. **CommandBridgeProvider** — clean abstraction for agent↔user communication via files
6. **Dashboard** — resizable panels, job history, agent dots, CORTEX chat with glow effect
7. **Developer tooling** — `koupper doctor`, `koupper worker --status`, CHANGELOG, session state

---

## Competition analysis

### OpenClaw (formerly Clawdbot / Moltbot)

The closest competitor in philosophy. Key characteristics:

**What they have that CORTEX does not:**

| Feature | OpenClaw | CORTEX |
|---|---|---|
| External channels | WhatsApp, Telegram, Discord, custom | None — terminal + web UI only |
| Skill system | `SKILL.md` portable, human-readable | `.kts` scripts (powerful, Kotlin-only) |
| Proactive heartbeat | `HEARTBEAT.md` — agent checks conditions and acts | `koupper schedule` (cron/rate/once) |
| Memory | Markdown + YAML (debuggeable, Git-versionable) | TF-IDF + embeddings (powerful, opaque) |
| Plugin marketplace | Third-party skills (risk: malware) | None |
| Agent self-improvement | Agent can write its own skills | Scaffold only (TODOs) |

**What CORTEX has that OpenClaw does not:**

| Feature | CORTEX | OpenClaw |
|---|---|---|
| Production job queue | Atomic claiming, dead-letter, timeout, retries | None |
| Schedule system | cron/rate/once with enable/disable | Heartbeat only |
| Real-time dashboard | SSE, job history, metrics | None documented |
| Framework SPs | 40+ cloud/infra providers (AWS, SSH, Docker, DB) | No equivalent |
| Security model | Local-only by design, no exposed gateway | Port 18789 known vulnerabilities |
| DevOps tooling | doctor, worker --status, CHANGELOG | None |
| Multi-repo framework | Open-source core + private product layer | Monolithic |

**OpenClaw security issues:**
- Gateway port 18789 exposed by default
- Known vulnerabilities when gateway is internet-facing
- Third-party plugin marketplace introduces supply chain risk
- Now under a nonprofit foundation after creator moved to OpenAI

### AutoGPT / AgentGPT
- Cloud-first, requires OpenAI API
- CORTEX advantage: fully local, no API costs

### CrewAI
- Python-based, requires cloud LLM by default
- Better multi-agent coordination patterns (role-based crews)
- CORTEX advantage: integrated job system, local LLM, Kotlin/JVM ecosystem

### LangGraph / LangChain agents
- Framework-level, no runtime included
- CORTEX advantage: full runtime stack, deployment-ready

---

## Gap analysis — what CORTEX needs to match/surpass OpenClaw

### Gap 1: No external channels (CRITICAL)
OpenClaw's killer feature is that you can message your agent via WhatsApp or Telegram. This makes it feel like a real assistant. CORTEX is terminal/browser only.

**What's needed:**
- A `ChannelGatewayProvider` SP that abstracts messaging channels
- Implementations: Telegram bot (easiest), WhatsApp via Twilio/Meta API, Discord
- Messages route to the CommandBridge — agent receives them as commands, responds via channel
- Architecture: `TelegramChannelProvider`, `WhatsAppChannelProvider` → `CommandBridgeProvider`

### Gap 2: Skill system not portable (MEDIUM)
OpenClaw's `SKILL.md` approach means anyone can write a skill in any language. CORTEX's `.kts` scripts require Kotlin knowledge.

**What's needed:**
- A `skill.json` metadata format alongside each `.kts` agent: name, description, inputs, outputs
- `koupper agent list` — discovers installed skills from metadata
- `koupper agent install <url>` — downloads and installs skill from registry
- Agent marketplace at `koupper.com/agents`

### Gap 3: Proactive autonomy is a cron job (MEDIUM)
OpenClaw's heartbeat makes the agent genuinely proactive — it checks conditions and decides to act. Koupper's `schedule` is more like a cron daemon.

**What's needed:**
- A `HeartbeatAgent.kts` that runs on a short interval (e.g., every 60s), reads a `heartbeat.md` condition file, evaluates whether to trigger other agents
- Conditions could be: file exists, HTTP endpoint returns X, queue is empty, time is after Y
- This turns the scheduler into a reactive system, not just periodic

### Gap 4: Agent memory is opaque (LOW-MEDIUM)
OpenClaw's markdown/YAML memory is human-readable and Git-versionable. CORTEX's TF-IDF embeddings are powerful but you can't inspect them easily.

**What's needed:**
- A hybrid: keep embeddings for retrieval, but also maintain a `memory.md` human-readable log
- `VectorDbProvider` real implementation (currently stub) replacing `CortexMemoryStore`

### Gap 5: Agents generate stubs, not real code (MEDIUM)
`AgentCreatorAgent` uses the LLM to ask 3 questions and generates a scaffold. It should use the LLM to generate the actual agent implementation.

**What's needed:**
- After collecting name/role/objective, `AgentCreatorAgent` passes them to `CortexAgent` for code generation
- The generated `.kts` should be a working implementation, not a TODO
- Requires `CortexAgent` to be running when `AgentCreatorAgent` runs

---

## Recommended implementation order

### Phase 1 — Make agents useful (2-3 sessions)
1. `AgentCreatorAgent` with real LLM code generation
2. `HeartbeatAgent.kts` — proactive condition checking
3. `skill.json` metadata for installed agents
4. At least one useful real agent (e.g., `RssFeedAgent`, `FileWatcherAgent`, `GitStatusAgent`)

### Phase 2 — External channels (1-2 sessions)
1. `TelegramChannelProvider` SP — easiest to implement, no API costs
2. Route Telegram messages → CommandBridge → CortexAgent → respond via Telegram
3. This single feature closes the biggest gap with OpenClaw

### Phase 3 — Marketplace (1 session)
1. `koupper agent list/install/publish` CLI commands
2. `skill.json` format published spec
3. Simple registry (GitHub-based or S3)

### Phase 4 — Memory and observability (1 session)
1. `VectorDbProvider` real implementation
2. Observability metrics in web UI (jobs/min, success rate, P95 latency)
3. Human-readable `memory.md` alongside embeddings

---

## How to run CORTEX today

```bash
# Prerequisites: Java 17+, koupper installed, llama-server built, .gguf model

# Set env vars (already in ~/.bashrc on dev machine)
export KOUPPER_LLM_MODEL_PATH=/path/to/model.gguf
export KOUPPER_LLM_EXECUTABLE=/path/to/llama-server

# Health check
koupper doctor

# Start everything
koupper start

# Web UI
open http://localhost:18083

# Submit a job manually
echo '{"scriptPath":"/home/user/.koupper/agents/GreetingAgent.kts"}' \
  > ~/.koupper/jobs/default/my-job-$(date +%s).json

# Schedule an agent
koupper schedule add GreetingAgent.kts --rate=300000 --id=greeting-5min
koupper start --scheduling
```

---

## Repository map

| Repo | Branch | Contents |
|---|---|---|
| `koupper` | `develop` | Framework core: octopus runtime, 40+ SPs, CommandBridgeProvider |
| `koupper` | `igly/cortex` | Private: CortexMcpServer, SwarmCoordinator, CortexMemoryStore |
| `koupper-cli` | `develop` | CLI: run, worker, schedule, doctor, worker --status |
| `koupper-cli` | `igly/cortex` | Private: start, monitor (+ all develop commands) |
| `workspace` | `develop` | Agents, docs, examples, QUICKSTART |

---

## Key files to read first (for any agent picking this up)

1. `docs/SESSION_STATE.md` — current progress and immediate next steps
2. `docs/NEXT_FEATURES_NOTES.md` — detailed feature backlog
3. `examples/agents/CortexAgent.kts` — the main agent, understand the inference loop
4. `examples/agents/AgentCreatorAgent.kts` — the wizard, primary UX entry point
5. `QUICKSTART.md` — how to run the stack
6. `CLAUDE.md` — development conventions, build commands, release process

---

## Strategic position

CORTEX's defensible advantage is the **Koupper framework underneath** — 40+ production-grade SPs for cloud infrastructure (AWS, SSH, Docker, PostgreSQL, Redis, Terraform, etc.). When agents become powerful enough to use these, CORTEX becomes the only local agent runtime that can actually deploy infrastructure, query databases, send emails, manage GitHub repos, and run K8s commands — all without leaving the local machine and without API costs.

OpenClaw can chat on WhatsApp. CORTEX can provision an EC2 instance, run a migration, and send a Slack notification — all from a `.kts` script triggered by a cron schedule.

The goal: **close the UX gap** (external channels, useful agents) while **deepening the infrastructure advantage** (more SPs used by real agents).
