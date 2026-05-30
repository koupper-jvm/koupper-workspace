// HeartbeatAgent.kts
// Role      : Proactive condition monitor
// Objective : Read ~/.koupper/heartbeat.md, evaluate conditions, dispatch agents when triggered
//
// Designed to run on a short schedule (e.g., every 60s via koupper schedule --rate=60000).
// Each condition in heartbeat.md specifies: when to trigger, which agent to run, which queue.
//
// heartbeat.md format:
//   ## Condition: <id>
//   - when: file_exists | queue_empty | queue_has_failed | time_after | always
//   - target: <path or queue name>
//   - agent: <AgentName.kts>
//   - queue: <queue name>
//   - cooldown: <minutes>   (minimum time between triggers, default 60)

import com.koupper.shared.annotations.Export
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Export
val setup: () -> Unit = {
    val home    = System.getProperty("user.home")!!
    val jobsDir = File(System.getenv("CORTEX_JOBS_DIR") ?: "$home/.koupper/jobs")
    val logDir  = File(jobsDir, "logs/default").also { it.mkdirs() }
    val logFile = File(logDir, "heartbeat.log")
    val stateFile = File(home, ".koupper/heartbeat-state.json")

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n")

    val heartbeatFile = File(home, ".koupper/heartbeat.md")
    if (!heartbeatFile.exists()) {
        heartbeatFile.writeText("""
# CORTEX Heartbeat Conditions
# Each condition block defines when to dispatch an agent automatically.
# HeartbeatAgent evaluates these on every run (schedule it with koupper schedule).

## Condition: morning-digest
- when: time_after
- target: 08:00
- agent: RssFeedAgent.kts
- queue: default
- cooldown: 720

## Condition: queue-alert
- when: queue_has_failed
- target: default
- agent: GreetingAgent.kts
- queue: default
- cooldown: 60
""".trimIndent())
        log("Created default heartbeat.md at ~/.koupper/heartbeat.md")
    }

    // ── Load last-triggered state ─────────────────────────────────────────────

    val state: MutableMap<String, Long> = runCatching {
        if (stateFile.exists()) {
            @Suppress("UNCHECKED_CAST")
            com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .readValue(stateFile, Map::class.java) as MutableMap<String, Long>
        } else mutableMapOf()
    }.getOrDefault(mutableMapOf())

    fun saveState() {
        stateFile.writeText(
            com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .writeValueAsString(state)
        )
    }

    // ── Parse conditions from heartbeat.md ────────────────────────────────────

    data class Condition(
        val id: String,
        val whenever: String,
        val target: String,
        val agent: String,
        val queue: String,
        val cooldownMin: Long
    )

    fun parseConditions(): List<Condition> {
        val conditions = mutableListOf<Condition>()
        var currentId: String? = null
        val current = mutableMapOf<String, String>()

        fun flush() {
            val id = currentId ?: return
            conditions.add(Condition(
                id         = id,
                whenever   = current["when"]     ?: return,
                target     = current["target"]   ?: "",
                agent      = current["agent"]    ?: return,
                queue      = current["queue"]    ?: "default",
                cooldownMin= current["cooldown"]?.toLongOrNull() ?: 60L
            ))
            current.clear()
        }

        heartbeatFile.readLines().forEach { line ->
            val stripped = line.trim()
            if (stripped.startsWith("## Condition:")) {
                flush()
                currentId = stripped.removePrefix("## Condition:").trim()
            } else if (stripped.startsWith("- ") && currentId != null) {
                val parts = stripped.removePrefix("- ").split(":", limit = 2)
                if (parts.size == 2) current[parts[0].trim()] = parts[1].trim()
            }
        }
        flush()
        return conditions
    }

    // ── Evaluate and dispatch ─────────────────────────────────────────────────

    fun cooldownOk(id: String, cooldownMin: Long): Boolean {
        val lastMs = state[id] ?: return true
        val elapsed = ChronoUnit.MINUTES.between(
            LocalDateTime.ofEpochSecond(lastMs / 1000, 0, java.time.ZoneOffset.UTC),
            LocalDateTime.now()
        )
        return elapsed >= cooldownMin
    }

    fun dispatch(condition: Condition) {
        val agentFile = File(home, ".koupper/agents/${condition.agent}")
        if (!agentFile.exists()) {
            log("  ⚠ Agent not found: ${condition.agent}")
            return
        }

        val qDir  = File(jobsDir, condition.queue).also { it.mkdirs() }
        val jobId = "${condition.agent.removeSuffix(".kts")}-hb-${System.currentTimeMillis()}"
        File(qDir, "$jobId.json").writeText(
            """{"scriptPath":"${agentFile.absolutePath}","triggeredBy":"heartbeat/${condition.id}"}"""
        )
        state[condition.id] = System.currentTimeMillis()
        log("  ▶ Dispatched ${condition.agent} → queue:${condition.queue} (job: $jobId)")
    }

    fun evaluate(cond: Condition): Boolean = when (cond.whenever) {
        "file_exists" -> File(cond.target.replace("~", home)).exists()

        "queue_empty" -> {
            val qDir = File(jobsDir, cond.target)
            val pending    = qDir.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0
            val processing = qDir.listFiles { f -> f.name.endsWith(".json.processing") }?.size ?: 0
            pending == 0 && processing == 0
        }

        "queue_has_failed" -> {
            val failed = File(jobsDir, "${cond.target}/.failed")
                .listFiles { f -> f.name.endsWith(".json") }?.size ?: 0
            failed > 0
        }

        "time_after" -> runCatching {
            val target  = LocalTime.parse(cond.target, DateTimeFormatter.ofPattern("HH:mm"))
            val now     = LocalTime.now()
            now.isAfter(target)
        }.getOrDefault(false)

        "always" -> true

        else -> false
    }

    // ── Main evaluation loop ──────────────────────────────────────────────────

    log("◈ HEARTBEAT — evaluating conditions")

    val conditions = parseConditions()
    log("  ${conditions.size} condition(s) loaded from heartbeat.md")

    var triggered = 0
    conditions.forEach { cond ->
        val fires = evaluate(cond)
        val ready = cooldownOk(cond.id, cond.cooldownMin)
        log("  [${cond.id}] when=${cond.whenever} → fires=$fires cooldown_ok=$ready")
        if (fires && ready) {
            dispatch(cond)
            triggered++
        }
    }

    saveState()
    log("  Done — $triggered condition(s) triggered")
}
