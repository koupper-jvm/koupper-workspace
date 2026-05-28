// GreetingAgent.kts — CORTEX Init Agent
// Analyzes swarm state and writes a dynamic greeting to the monitor's LogStream.
// Invoked by MonitorApp on startup. Args: [jobsDir]

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

val home    = System.getProperty("user.home")
val jobsDir = File(args.firstOrNull() ?: "$home/.koupper/jobs")

val sessionId  = "cortex-greeting"
val queueDir   = File(jobsDir, "jobs/cortex").also { it.mkdirs() }
val logDir     = File(jobsDir, "logs/cortex").also  { it.mkdirs() }
val jobFile    = File(queueDir, "$sessionId.json")
val logFile    = File(logDir,   "$sessionId.log")

logFile.writeText("")  // reset on each startup

fun ts()         = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n")

// Create job entry so WatchService picks it up and monitor shows it
jobFile.writeText("""{"id":"$sessionId","fileName":"GreetingAgent","functionName":"run","scriptPath":"agents/GreetingAgent.kts","sourceType":"script"}""")
val procFile = File(queueDir, "$sessionId.json.processing")
jobFile.renameTo(procFile)

Thread.sleep(200)  // let WatchService register the .processing file

// ── Swarm analysis ────────────────────────────────────────────────────────────

data class SwarmStats(val pending: Int, val processing: Int, val failed: Int, val queues: Set<String>)

fun analyzeJobs(): SwarmStats {
    var pending = 0; var processing = 0; var failed = 0
    val queues  = mutableSetOf<String>()
    val jobsRoot = File(jobsDir, "jobs")
    if (!jobsRoot.exists()) return SwarmStats(0, 0, 0, emptySet())

    for (qDir in jobsRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") } ?: emptyList()) {
        queues.add(qDir.name)
        for (f in qDir.listFiles() ?: continue) {
            when {
                f.name.endsWith(".json.processing") && f.name != "$sessionId.json.processing" -> processing++
                f.name.endsWith(".json") -> pending++
            }
        }
        failed += File(qDir, ".failed").listFiles { f -> f.name.endsWith(".json") }?.size ?: 0
    }
    return SwarmStats(pending, processing, failed, queues)
}

val agentsDir  = File(home, ".koupper/agents")
val agentCount = agentsDir.listFiles { f -> f.name.endsWith(".kts") && f.name != "GreetingAgent.kts" && f.name != "AgentCreatorAgent.kts" }?.size ?: 0
val stats      = analyzeJobs()

val statusLine = when {
    stats.processing > 0 -> "ACTIVE     ${stats.processing} job(s) in flight"
    stats.pending    > 0 -> "STANDBY    ${stats.pending} job(s) queued"
    stats.failed     > 0 -> "ALERT      ${stats.failed} job(s) failed"
    else                  -> "IDLE       No active jobs"
}

// ── Write greeting to LogStream ───────────────────────────────────────────────

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

// Mark greeting done (delete processing file — appears as DONE in monitor briefly)
Thread.sleep(500)
procFile.delete()
