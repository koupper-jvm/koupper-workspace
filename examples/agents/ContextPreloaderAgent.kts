import com.koupper.container.app
import com.koupper.providers.files.fromJson
import com.koupper.providers.memory.MemoryProvider
import com.koupper.shared.annotations.Export
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Export
val setup: () -> Unit = {
    val home = System.getProperty("user.home")!!
    fun ts() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = println("[${ts()}] $msg")

    val memory = runCatching { app.getInstance(MemoryProvider::class) }.getOrNull()
    if (memory == null) {
        log("MemoryProvider not available — aborting")
    } else {

    // --- 1. Purge stale/bad entries from previous sessions ---
    val textsFile = koupper.files().load(home, ".koupper/memory/memory-texts.json")
    if (textsFile.exists()) {
        val raw = textsFile.readText()
        val entries = raw.fromJson<List<Map<String, Any?>>>()
        var purged = 0
        val seen = mutableSetOf<String>()
        entries.forEach { entry ->
            val id   = entry["id"] as? String
            if (id != null) {
                val text = entry["text"] as? String ?: ""
                val isDump      = text.length > 350
                val isDuplicate = !seen.add(text.take(80))
                val isTestEntry = text == "the exact fact to store"
                if (isDump || isDuplicate || isTestEntry) {
                    runCatching { memory.forget(id) }
                    purged++
                }
            }
        }
        log("Purged $purged stale entries")
    }

    // --- 2. Load structured context facts ---
    val facts = listOf(
        // System & stack
        "CORTEX stack: Octopus=9998, MCP=18082, WebUI=18083. Start: systemctl --user start koupper.service",
        "LLM config: Groq llama-3.3-70b-versatile (KOUPPER_LLM_PROVIDER=openai, API_BASE=api.groq.com). Fallback: qwen2.5-7b local via llama-server",
        "Job queue: ~/.koupper/jobs/<queue>/ — .json=PENDING, .json.processing=PROCESSING, .failed/=FAILED, .dead/=DEAD. History in .history.jsonl (last 500)",
        "CORTEX logs: ~/.koupper/jobs/logs/cortex/cortex-session.log | worker: logs/default/worker.log | MCP: logs/default/mcp-server.log",

        // Agents
        "Agent CortexAgent.kts: main LLM orchestrator, native function calling, 17 tools (bash, file, MCP), 8h session, Groq auto-detect",
        "Agent CortexWebUiAgent.kts: real-time dashboard at http://localhost:18083 with job history, agent list, SSE chat, ▶ run button",
        "Agent TelegramBridgeAgent.kts: bidirectional Telegram↔CortexAgent bridge via CommandBridge. Routes messages and sends responses",
        "Agent AgentCreatorAgent.kts: interactive wizard — collects name/role/objective then uses LLM to generate a working .kts agent script",
        "Agent HeartbeatAgent.kts: proactive monitor — reads ~/.koupper/heartbeat.md conditions and dispatches agents when triggered",
        "Agent RssFeedAgent.kts: fetches configured RSS feeds, extracts latest items, optionally generates AI summary with local LLM",
        "Agent GreetingAgent.kts: runs at startup — analyzes swarm state (queues, jobs, agents) and writes summary to cortex-greeting log",

        // Dev conventions
        ".kts scripts: single @Export entrypoint named 'setup'. Service Providers via app.getInstance(). Never direct SDK calls inside scripts",
        "Build: cd koupper && ./gradlew build | cd koupper-cli && ./gradlew build. Tests: ./gradlew test --tests 'ClassName'. failFast=true",
        "Release: koupper run scripts/release/fast-lane.kts '{\"featureBranch\":\"feature/name\",\"enableAutoMerge\":true}'",
        "Pre-push check: ./scripts/ci/local-quick-checks.sh all (targets: core, cli, docs, all)",
        "Repos: koupper/ (core v7.2.1, 50 SPs), koupper-cli/ (v7.2.1), workspace/ (agents+docs). Branch: develop.",
        "MCP tools available to CortexAgent: bash, write_file, read_file, list_dir, fetch_url, create_agent, run_agent, list_agents, job_status, read_log, inspect_swarm, pipeline_run, cancel_job, swarm_run + memory.remember/recall/forget",

        // Recent significant changes
        "Recent koupper changes: emit() helper in script preamble, two-tier compilation cache (faster cold start), CommandBridge dedup fix, native suspend lambda support",
        "Recent koupper-cli changes: clean terminal vs web mode in StartCommand, port probe with interactive selection, octopus boot with full env loading"
    )

    var loaded = 0
    facts.forEach { fact ->
        runCatching {
            memory.remember(fact)
            loaded++
        }.onFailure { log("WARN: failed to store fact — ${it.message?.take(60)}") }
    }

    log("Context preloaded: $loaded facts stored in memory")
    log("Memory ready — CORTEX will now have project context on every query")
    }
}
