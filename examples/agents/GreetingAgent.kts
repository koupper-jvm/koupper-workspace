// GreetingAgent.kts — CORTEX Init Agent
// Analyzes swarm state and writes a dynamic greeting to the monitor's LogStream.
// Can be run directly by the MonitorApp or submitted as a worker job.

import com.koupper.shared.annotations.Export
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class SwarmStats(val pending: Int, val processing: Int, val failed: Int, val queues: Set<String>)

@Export
val setup: () -> Unit = {
    val home    = System.getProperty("user.home")!!
    val jobsDir = koupper.files().load(env("CORTEX_JOBS_DIR", "$home/.koupper/jobs"))

    val sessionId = "cortex-greeting"
    val queueDir  = koupper.files().load(jobsDir, "cortex").also { it.mkdirs() }
    val logDir    = koupper.files().load(jobsDir, "logs/cortex").also { it.mkdirs() }
    val logFile   = koupper.files().load(logDir, "$sessionId.log")

    logFile.writeText("")

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n")

    // Create job entry so WatchService picks it up and monitor shows it
    val jobFile  = koupper.files().load(queueDir, "$sessionId.json")
    val procFile = koupper.files().load(queueDir, "$sessionId.json.processing")
    jobFile.writeText("""{"id":"$sessionId","fileName":"GreetingAgent","functionName":"setup","scriptPath":"agents/GreetingAgent.kts","sourceType":"script"}""")
    jobFile.renameTo(procFile)

    Thread.sleep(200)

    fun analyzeJobs(): SwarmStats {
        var pending = 0; var processing = 0; var failed = 0
        val queues  = mutableSetOf<String>()
        jobsDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && it.name !in setOf("logs", "commands") }
            ?.forEach { qDir ->
                queues.add(qDir.name)
                qDir.listFiles()?.forEach { f ->
                    when {
                        f.name.endsWith(".json.processing") && f.name != "$sessionId.json.processing" -> processing++
                        f.name.endsWith(".json") -> pending++
                    }
                }
                failed += koupper.files().load(qDir, ".failed").listFiles { f -> f.name.endsWith(".json") }?.size ?: 0
            }
        return SwarmStats(pending, processing, failed, queues)
    }

    val agentsDir  = koupper.files().load(home, ".koupper/agents")
    val agentCount = agentsDir.listFiles { f -> f.name.endsWith(".kts") && f.name !in setOf("GreetingAgent.kts", "AgentCreatorAgent.kts") }?.size ?: 0
    val stats      = analyzeJobs()

    val statusLine = when {
        stats.processing > 0 -> "ACTIVE     ${stats.processing} job(s) in flight"
        stats.pending    > 0 -> "STANDBY    ${stats.pending} job(s) queued"
        stats.failed     > 0 -> "ALERT      ${stats.failed} job(s) failed"
        else                  -> "IDLE       No active jobs"
    }

    log("┌─────────────────────────────────────┐")
    log("│   CORTEX ONLINE — SWARM ANALYSIS    │")
    log("└─────────────────────────────────────┘")
    log("")
    log("  DEPLOYED AGENTS  : $agentCount")
    log("  ACTIVE QUEUES    : ${if (stats.queues.isEmpty()) "none" else stats.queues.joinToString(", ")}")
    log("  JOBS PENDING     : ${stats.pending}")
    log("  JOBS IN FLIGHT   : ${stats.processing}")
    log("  JOBS FAILED      : ${stats.failed}")
    log("")
    log("  STATUS ► $statusLine")
    log("")
    log("┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄")
    log("  COMMANDS")
    log("   :create     spawn a new agent via wizard")
    log("   :help       show available commands")
    log("   j / k       navigate job table")
    log("   Enter       view job log")
    log("   ESC         back / cancel")
    log("┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄")
    log("  Ready for instruction.")

    Thread.sleep(500)
    procFile.delete()
}
