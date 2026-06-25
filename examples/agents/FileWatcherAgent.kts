// FileWatcherAgent.kts
// Role      : Directory monitor
// Objective : Watch configured dirs for new files and trigger actions (log, move, dispatch agent)
//
// Config: ~/.koupper/filewatcher-rules.json
// Example:
//   [
//     { "watchDir": "~/Downloads", "pattern": "*.pdf",  "action": "dispatch", "agent": "PdfSummaryAgent.kts", "queue": "default" },
//     { "watchDir": "~/Downloads", "pattern": "*",      "action": "log" },
//     { "watchDir": "~/tmp/inbox", "pattern": "*.json", "action": "move", "destDir": "~/tmp/processed" }
//   ]
//
// Actions: log | move | dispatch

import com.koupper.shared.annotations.Export
import com.koupper.providers.files.WatchEvent
import com.koupper.providers.files.fromJson
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

@Export
val setup: () -> Unit = {
    val jobsDir = File(env("CORTEX_JOBS_DIR", "$home/.koupper/jobs"))
    val logDir  = File(jobsDir, "logs/default").also { it.mkdirs() }
    val logFile = File(logDir, "filewatcher.log")

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) { logFile.appendText("[${ts()}] $msg\n"); emit(msg) }

    // ── Config ────────────────────────────────────────────────────────────────

    val configFile = File(home, ".koupper/filewatcher-rules.json")
    if (!configFile.exists()) {
        configFile.writeText("""[
  {
    "watchDir": "~/Downloads",
    "pattern": "*.pdf",
    "action": "log"
  },
  {
    "watchDir": "~/Downloads",
    "pattern": "*",
    "action": "log"
  }
]""")
        log("Created default filewatcher-rules.json — watching ~/Downloads")
    }

    data class Rule(
        val watchDir: String,
        val pattern: String,
        val action: String,
        val agent: String = "",
        val queue: String = "default",
        val destDir: String = ""
    )

    val rules = runCatching {
        configFile.readText().fromJson<List<Map<String, String>>>().map { m ->
            Rule(
                watchDir = m["watchDir"] ?: "~/Downloads",
                pattern  = m["pattern"]  ?: "*",
                action   = m["action"]   ?: "log",
                agent    = m["agent"]    ?: "",
                queue    = m["queue"]    ?: "default",
                destDir  = m["destDir"]  ?: ""
            )
        }
    }.getOrElse { e -> log("⚠ Could not parse rules: ${e.message?.take(80)}"); emptyList<Rule>() }

    // ── Pattern matching ──────────────────────────────────────────────────────

    fun matches(filename: String, pattern: String): Boolean =
        pattern == "*" || Regex(pattern.replace(".", "\\.").replace("*", ".*").replace("?", "."), RegexOption.IGNORE_CASE).matches(filename)

    // ── Actions ───────────────────────────────────────────────────────────────

    fun waitStable(file: File) {
        val deadline = System.currentTimeMillis() + 5_000L
        var prev = -1L
        var stable = false
        while (System.currentTimeMillis() < deadline && !stable) {
            val size = file.length()
            if (size == prev && size >= 0) stable = true
            else { prev = size; Thread.sleep(300) }
        }
    }

    fun doDispatch(file: File, rule: Rule) {
        val agentFile = File(home, ".koupper/agents/${rule.agent}")
        if (!agentFile.exists()) {
            log("  ⚠ Agent not found: ${rule.agent}")
        } else {
            val agentName = rule.agent.removeSuffix(".kts")
            val jobId     = "$agentName-fw-${System.currentTimeMillis()}"
            val queueDir  = File(jobsDir, rule.queue).also { it.mkdirs() }
            File(queueDir, "$jobId.json").writeText(
                """{"id":"$jobId","fileName":"$agentName","functionName":"setup","scriptPath":"agents/${rule.agent}","sourceType":"script","triggeredBy":"filewatcher","filePath":"${file.absolutePath}"}"""
            )
            log("  ▶ Dispatched ${rule.agent} for ${file.name} → queue:${rule.queue}")
        }
    }

    fun doMove(file: File, rule: Rule) {
        val dest = File(rule.destDir.replace("~", home)).also { it.mkdirs() }
        val target = File(dest, file.name)
        val moved = file.renameTo(target)
        if (!moved) {
            file.copyTo(target, overwrite = true)
            file.delete()
        }
        log("  ↳ Moved ${file.name} → ${dest.absolutePath}")
    }

    fun handleFile(dir: File, filename: String) {
        val file = File(dir, filename)
        if (file.isFile) {
            val matched = rules.filter {
                it.watchDir.replace("~", home) == dir.absolutePath && matches(filename, it.pattern)
            }
            if (matched.isNotEmpty()) {
                waitStable(file)
                matched.forEach { rule ->
                    log("  [${rule.action.uppercase()}] ${dir.name}/$filename (pattern: ${rule.pattern})")
                    when (rule.action) {
                        "dispatch" -> doDispatch(file, rule)
                        "move"     -> doMove(file, rule)
                    }
                }
            }
        }
    }

    // ── Watch ─────────────────────────────────────────────────────────────────

    if (rules.isEmpty()) {
        log("No rules configured — exiting.")
    } else {
        val dirs = rules.map { File(it.watchDir.replace("~", home)).also { d -> d.mkdirs() } }.distinct()

        if (dirs.isEmpty()) {
            log("No dirs to watch — exiting.")
        } else {
            dirs.forEach { log("◈ Watching: ${it.absolutePath}") }
            log("  FileWatcherAgent running — ${dirs.size} dir(s), ${rules.size} rule(s)")

            val running = AtomicBoolean(true)
            Runtime.getRuntime().addShutdownHook(Thread { running.set(false) })

            log("  FileWatcherAgent scanning mode (5s demo)")
            val deadline = System.currentTimeMillis() + 5_000L
            while (System.currentTimeMillis() < deadline && running.get()) {
                dirs.forEach { dir ->
                    dir.listFiles { f -> f.isFile && !f.name.startsWith(".") }
                        ?.forEach { file -> handleFile(dir, file.name) }
                }
                Thread.sleep(1000)
            }

            log("FileWatcherAgent stopped.")
        }
    }
}
