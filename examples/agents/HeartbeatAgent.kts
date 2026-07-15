// HeartbeatAgent.kts
// Role      : Proactive condition monitor + agent watchdog
// Objective : Read ~/.koupper/heartbeat.md, evaluate conditions, dispatch or restart agents
//
// Run periodically via cortex-start.sh (60 s loop)
//
// Condition types:
//   file_exists      — triggers when a file/dir path exists
//   queue_empty      — triggers when a queue has no pending/processing jobs
//   queue_has_failed — triggers when a queue has failed jobs
//   time_after       — triggers after HH:mm each day
//   agent_down       — triggers when daemon PID is not alive; restarts it directly (not via queue)
//   always           — always triggers (respects cooldown)
//
// heartbeat.md format:
//   ## Condition: <id>
//   - when: <type>
//   - target: <path, queue name, or PID file>
//   - agent: <AgentName.kts>
//   - queue: <queue name>        (dispatch conditions only)
//   - log: <log file path>       (agent_down only, optional)
//   - cooldown: <minutes>        (default 60; use 2 for watchdogs)

import com.koupper.providers.files.fromJson
import com.koupper.providers.files.toJson
import com.koupper.shared.annotations.Export
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Export
val setup: () -> Unit = {
    val jobsDir       = koupper.files().load(env("CORTEX_JOBS_DIR", "$home/.koupper/jobs"))
    val runDir        = koupper.files().load(home, ".koupper/run").also { it.mkdirs() }
    val stateFile     = koupper.files().load(home, ".koupper/heartbeat-state.json")
    val heartbeatFile = koupper.files().load(home, ".koupper/heartbeat.md")
    val logDir        = koupper.files().load(jobsDir, "logs/default").also { it.mkdirs() }
    val logFile       = koupper.files().load(logDir, "heartbeat.log")

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) { logFile.appendText("[${ts()}] $msg\n"); emit(msg) }

    if (!heartbeatFile.exists()) {
        heartbeatFile.writeText("""
# CORTEX Heartbeat Conditions

## Condition: morning-digest
- when: time_after
- target: 08:00
- agent: RssFeedAgent.kts
- queue: default
- cooldown: 720

## Condition: failed-jobs-alert
- when: queue_has_failed
- target: default
- agent: GreetingAgent.kts
- queue: default
- cooldown: 60

## Condition: nightly-cleanup
- when: time_after
- target: 23:00
- agent: DiskCleanerAgent.kts
- queue: default
- cooldown: 720

## Condition: watchdog-cortex
- when: agent_down
- target: ~/.koupper/run/cortex.pid
- agent: CortexAgent.kts
- log: ~/.koupper/jobs/logs/cortex/cortex-session.log
- cooldown: 2

## Condition: watchdog-telegram
- when: agent_down
- target: ~/.koupper/run/telegram.pid
- agent: TelegramBridgeAgent.kts
- log: ~/.koupper/jobs/logs/default/telegram-bridge.log
- cooldown: 2

## Condition: watchdog-webui
- when: agent_down
- target: ~/.koupper/run/webui.pid
- agent: CortexWebUiAgent.kts
- log: ~/.koupper/jobs/logs/default/webui.log
- cooldown: 2

## Condition: watchdog-worker
- when: agent_down
- target: ~/.koupper/run/worker.pid
- agent: worker
- log: ~/.koupper/jobs/logs/default/worker.log
- cooldown: 2
""".trimIndent())
        log("Created default heartbeat.md")
    }

    // ── State ─────────────────────────────────────────────────────────────────

    val state: MutableMap<String, Long> = runCatching {
        if (stateFile.exists()) stateFile.readText().fromJson<MutableMap<String, Long>>() ?: mutableMapOf()
        else mutableMapOf()
    }.getOrDefault(mutableMapOf())

    fun saveState() = stateFile.writeText(state.toJson())

    // ── Parse conditions ──────────────────────────────────────────────────────

    data class Condition(
        val id: String,
        val whenever: String,
        val target: String,
        val agent: String,
        val queue: String,
        val cooldownMin: Long,
        val logPath: String?
    )

    fun parseConditions(): List<Condition> {
        val result = mutableListOf<Condition>()
        var currentId: String? = null
        val current = mutableMapOf<String, String>()

        fun flush() {
            val id = currentId ?: return
            result.add(Condition(
                id          = id,
                whenever    = current["when"]     ?: return,
                target      = current["target"]   ?: "",
                agent       = current["agent"]    ?: return,
                queue       = current["queue"]    ?: "default",
                cooldownMin = current["cooldown"]?.toLongOrNull() ?: 60L,
                logPath     = current["log"]
            ))
            current.clear()
        }

        heartbeatFile.readLines().forEach { line ->
            val s = line.trim()
            when {
                s.startsWith("## Condition:") -> { flush(); currentId = s.removePrefix("## Condition:").trim() }
                s.startsWith("- ") && currentId != null -> {
                    val parts = s.removePrefix("- ").split(":", limit = 2)
                    if (parts.size == 2) current[parts[0].trim()] = parts[1].trim()
                }
            }
        }
        flush()
        return result
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun cooldownOk(id: String, cooldownMin: Long): Boolean {
        val lastMs  = state[id] ?: return true
        val elapsed = ChronoUnit.MINUTES.between(
            LocalDateTime.ofEpochSecond(lastMs / 1000, 0, java.time.ZoneOffset.UTC),
            LocalDateTime.now()
        )
        return elapsed >= cooldownMin
    }

    fun isPidAlive(pidFile: File): Boolean {
        if (!pidFile.exists()) return false
        val pid = pidFile.readText().trim().toLongOrNull() ?: return false
        return koupper.files().load("/proc/$pid").exists()
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    fun dispatch(cond: Condition) {
        val agentFile = koupper.files().load(home, ".koupper/agents/${cond.agent}")
        if (!agentFile.exists()) { log("  ⚠ Agent not found: ${cond.agent}"); return }

        val agentName = cond.agent.removeSuffix(".kts")
        val jobId     = "$agentName-hb-${System.currentTimeMillis()}"
        val queueDir  = koupper.files().load(jobsDir, cond.queue).also { it.mkdirs() }

        koupper.files().load(queueDir, "$jobId.json").writeText(
            """{"id":"$jobId","fileName":"$agentName","functionName":"setup","scriptPath":"agents/${cond.agent}","sourceType":"script","triggeredBy":"heartbeat/${cond.id}"}"""
        )
        state[cond.id] = System.currentTimeMillis()
        log("  ▶ Dispatched ${cond.agent} → queue:${cond.queue}  job:$jobId")
    }

    fun restart(cond: Condition) {
        val pidFile = koupper.files().load(cond.target.replace("~", home))

        // Kill stale process
        runCatching {
            val oldPid = pidFile.readText().trim()
            Runtime.getRuntime().exec(arrayOf("kill", "-9", oldPid)).waitFor()
        }

        val resolvedLog = (cond.logPath ?: "$home/.koupper/jobs/logs/default/${cond.agent.removeSuffix(".kts")}.log")
            .replace("~", home)
        val agentLog = koupper.files().load(resolvedLog).also { it.parentFile?.mkdirs() }

        val cmd = if (cond.agent == "worker") {
            listOf("koupper", "worker")
        } else {
            val agentPath = koupper.files().load(home, ".koupper/agents/${cond.agent}")
            if (!agentPath.exists()) { log("  ⚠ Agent not found: ${cond.agent}"); return }
            listOf("koupper", "run", agentPath.absolutePath)
        }

        val proc = ProcessBuilder(cmd)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(agentLog))
            .redirectError(ProcessBuilder.Redirect.appendTo(agentLog))
            .start()

        pidFile.parentFile?.mkdirs()
        pidFile.writeText(proc.pid().toString())
        state[cond.id] = System.currentTimeMillis()
        log("  ↺ Restarted ${cond.agent} → PID ${proc.pid()}  log:${agentLog.name}")
    }

    // ── Evaluate ──────────────────────────────────────────────────────────────

    fun evaluate(cond: Condition): Boolean = when (cond.whenever) {
        "file_exists"      -> koupper.files().load(cond.target.replace("~", home)).exists()
        "queue_empty"      -> koupper.files().load(jobsDir, cond.target).let { q ->
            (q.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0) == 0 &&
            (q.listFiles { f -> f.name.endsWith(".json.processing") }?.size ?: 0) == 0
        }
        "queue_has_failed" -> (koupper.files().load(koupper.files().load(jobsDir, cond.target), ".failed")
            .listFiles { f -> f.name.endsWith(".json") }?.size ?: 0) > 0
        "time_after"       -> runCatching {
            LocalTime.now().isAfter(LocalTime.parse(cond.target, DateTimeFormatter.ofPattern("HH:mm")))
        }.getOrDefault(false)
        "agent_down"       -> !isPidAlive(koupper.files().load(cond.target.replace("~", home)))
        "always"           -> true
        else               -> false
    }

    // ── Main ──────────────────────────────────────────────────────────────────

    log("◈ HEARTBEAT — evaluating conditions")

    val conditions = parseConditions()
    log("  ${conditions.size} condition(s) loaded")

    var triggered = 0
    conditions.forEach { cond ->
        val fires = evaluate(cond)
        val ready = cooldownOk(cond.id, cond.cooldownMin)
        log("  [${cond.id}] when=${cond.whenever} fires=$fires cooldown_ok=$ready")
        if (fires && ready) {
            if (cond.whenever == "agent_down") restart(cond) else dispatch(cond)
            triggered++
        }
    }

    saveState()
    log("  Done — $triggered condition(s) triggered")
}
