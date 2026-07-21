# IGLY CORTEX — Quickstart

Local AI agent orchestration powered by Koupper. No cloud. No subscriptions. Everything runs on your machine.

---

## Prerequisites

- Java 17+
- Koupper installed (`~/.koupper/bin/koupper` on PATH)
- [llama.cpp](https://github.com/ggerganov/llama.cpp) built — `llama-server` binary + a `.gguf` model

---

## 1. Set environment variables

```bash
export KOUPPER_LLM_MODEL_PATH=/path/to/your/model.gguf
export KOUPPER_LLM_EXECUTABLE=/path/to/llama-server
```

Add to `~/.bashrc` or `~/.zshrc` to persist.

---

## 2. Start everything with one command

```bash
koupper start
```

That's it. This starts:
- **Worker** — polls `~/.koupper/jobs/` and executes agent scripts
- **Web UI** — dashboard at `http://localhost:18083` (if installed)
- **Monitor** — TUI dashboard in the terminal

```
  ◈ IGLY CORTEX — Starting
  Jobs dir : /home/you/.koupper/jobs

  [worker]   started (log: ~/.koupper/logs/worker.log)
  [web ui]   http://localhost:18083
  MCP tools : http://localhost:18082/mcp/tools
```

Close the monitor (`q`) to stop everything.

---

## Options

```bash
# Enable scheduled agents (reads ~/.koupper/schedules.json)
koupper start --scheduling

# Custom jobs directory
koupper start ~/myproject/jobs

# Skip components
koupper start --no-worker
koupper start --no-webui
```

---

## Individual commands

If you prefer to run components separately:

```bash
# Terminal 1 — TUI dashboard
koupper monitor

# Terminal 2 — job executor
koupper worker
koupper worker --enable-scheduling   # with schedule support

# Terminal 3 — web dashboard
koupper run ~/.koupper/agents/CortexWebUiAgent.kts
```

---

## Manage schedules

```bash
# Run DataAgent every weekday at 8am
koupper schedule add DataAgent --cron="0 8 * * 1-5" --id=daily-data

# Run HealthCheck every 5 minutes
koupper schedule add HealthCheckAgent --rate=300000

# Run ReportAgent once
koupper schedule add ReportAgent --once="2026-06-01T09:00:00"

# List / manage
koupper schedule list
koupper schedule disable daily-data
koupper schedule remove daily-data
```

Then start with scheduling enabled:
```bash
koupper start --scheduling
```

---

## Connect external MCP servers

Create `~/.koupper/mcp/servers.json`:

```json
[
  {
    "name": "playwright",
    "transport": "stdio",
    "command": "npx",
    "args": ["@playwright/mcp", "--headless"]
  },
  {
    "name": "github",
    "transport": "stdio",
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-github"],
    "env": { "GITHUB_PERSONAL_ACCESS_TOKEN": "your-token" }
  }
]
```

CORTEX discovers and uses these tools automatically on next start.

Install the servers:
```bash
npm install -g @playwright/mcp
```

---

## What CORTEX can do

Once running, open the command bar in the monitor (`Enter` on the cortex-session job) and talk to CORTEX:

```
list my agents
```
```
create a DataAgent that reads RSS feeds and saves summaries to a file
```
```
run DataAgent in queue default
```
```
create a swarm: ResearchAgent finds info about X, WriterAgent writes a report
```
```
create a pipeline: FetchAgent → ProcessAgent → ReportAgent
```

CORTEX uses your local LLM. Nothing leaves your machine.

---

## Logs

```bash
tail -f ~/.koupper/jobs/logs/cortex/cortex-session.log   # CORTEX output
tail -f ~/.koupper/logs/worker.log                        # Worker activity
tail -f ~/.koupper/logs/webui.log                         # Web UI
```

---

## Install the web dashboard agent

```bash
cp examples/agents/CortexWebUiAgent.kts ~/.koupper/agents/
```

Then `koupper start` will auto-launch it.
