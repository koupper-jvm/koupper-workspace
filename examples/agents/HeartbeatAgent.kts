// HeartbeatAgent.kts
// Role      : Proactive condition monitor
// Objective : Read ~/.koupper/heartbeat.md, evaluate conditions, dispatch agents when triggered
//
// Run periodically via koupper-start.sh (60 s loop)
//
// heartbeat.md format:
//   ## Condition: <id>
//   - when: file_exists | queue_empty | queue_has_failed | time_after | always
//   - target: <path or queue name>
//   - agent: <AgentName.kts>
//   - queue: <queue name>
//   - cooldown: <minutes>   (minimum time between triggers, default 60)

import com.koupper.shared.annotations.Export
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Export
val setup: () -> Unit = {
    val jobsDir       = File(env("CORTEX_JOBS_DIR", "$home/.koupper/jobs"))
    val stateFile     = File(home, ".koupper/heartbeat-state.json")
    val heartbeatFile = File(home, ".koupper/heartbeat.md")
    val logDir        = File(jobsDir, "logs/default").also { it.mkdirs() }
    val logFile       = File(logDir, "heartbeat.log")

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
""".trimIndent())
        log("Created default heartbeat.md")
    }

    // ── State (last-triggered timestamps) ────────────────────────────────────

    val mapper = jacksonObjectMapper()

    @Suppress("UNCHECKED_CAST")
    val state: MutableMap<String, Long> = runCatching {
        if (stateFile.exists()) mapper.readValue(stateFile, Map::class.java) as MutableMap<String, Long>
        else mutableMapOf()
    }.getOrDefault(mutableMapOf())

    fun saveState() = stateFile.writeText(mapper.writeValueAsString(state))

    // ── Parse conditions ──────────────────────────────────────────────────────

    data class Condition(
        val id: String, val whenever: String, val target: String,
        val agent: String, val queue: String, val cooldownMin: Long
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
                cooldownMin = current["cooldown"]?.toLongOrNull() ?: 60L
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

    // ── Evaluate & dispatch ───────────────────────────────────────────────────

    fun cooldownOk(id: String, cooldownMin: Long): Boolean {
        val lastMs  = state[id] ?: return true
        val elapsed = ChronoUnit.MINUTES.between(
            LocalDateTime.ofEpochSecond(lastMs / 1000, 0, java.time.ZoneOffset.UTC),
            LocalDateTime.now()
        )
        return elapsed >= cooldownMin
    }

    fun dispatch(cond: Condition) {
        val agentFile = File(home, ".koupper/agents/${cond.agent}")
        if (!agentFile.exists()) { log("  ⚠ Agent not found: ${cond.agent}"); return }

        val agentName = cond.agent.removeSuffix(".kts")
        val jobId     = "$agentName-hb-${System.currentTimeMillis()}"
        val queueDir  = File(jobsDir, cond.queue).also { it.mkdirs() }

        File(queueDir, "$jobId.json").writeText(
            """{"id":"$jobId","fileName":"$agentName","functionName":"setup","scriptPath":"agents/${cond.agent}","sourceType":"script","triggeredBy":"heartbeat/${cond.id}"}"""
        )
        state[cond.id] = System.currentTimeMillis()
        log("  ▶ Dispatched ${cond.agent} → queue:${cond.queue}  job:$jobId")
    }

    fun evaluate(cond: Condition): Boolean = when (cond.whenever) {
        "file_exists"      -> File(cond.target.replace("~", home)).exists()
        "queue_empty"      -> File(jobsDir, cond.target).let { q ->
            (q.listFiles { f -> f.name.endsWith(".json") }?.size ?: 0) == 0 &&
            (q.listFiles { f -> f.name.endsWith(".json.processing") }?.size ?: 0) == 0
        }
        "queue_has_failed" -> (File(File(jobsDir, cond.target), ".failed")
            .listFiles { f -> f.name.endsWith(".json") }?.size ?: 0) > 0
        "time_after"       -> runCatching {
            LocalTime.now().isAfter(LocalTime.parse(cond.target, DateTimeFormatter.ofPattern("HH:mm")))
        }.getOrDefault(false)
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
        if (fires && ready) { dispatch(cond); triggered++ }
    }

    saveState()
    log("  Done — $triggered condition(s) triggered")
}
