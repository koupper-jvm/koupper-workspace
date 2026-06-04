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
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Export
val setup: () -> Unit = {
    val jobsDir = File(env("CORTEX_JOBS_DIR", "$home/.koupper/jobs"))
    val logDir  = File(jobsDir, "logs/default").also { it.mkdirs() }
    val logFile = File(logDir, "filewatcher.log")
    val mapper  = jacksonObjectMapper()

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
        mapper.readValue<List<Map<String, String>>>(configFile).map { m ->
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

    // ── Setup WatchService ────────────────────────────────────────────────────

    if (rules.isEmpty()) {
        log("No rules configured — exiting.")
    } else {
        val ws       = FileSystems.getDefault().newWatchService()
        val keyToDir = mutableMapOf<java.nio.file.WatchKey, File>()
        val watched  = mutableSetOf<String>()

        rules.map { it.watchDir.replace("~", home) }.distinct().forEach { path ->
            val dir = File(path).also { it.mkdirs() }
            if (dir.absolutePath !in watched) {
                val key = dir.toPath().register(ws, ENTRY_CREATE)
                keyToDir[key] = dir
                watched += dir.absolutePath
                log("◈ Watching: ${dir.absolutePath}")
            }
        }

        if (keyToDir.isEmpty()) {
            log("No dirs to watch — exiting.")
        } else {
            log("  FileWatcherAgent running — ${keyToDir.size} dir(s), ${rules.size} rule(s)")

            val running = AtomicBoolean(true)
            Runtime.getRuntime().addShutdownHook(Thread { running.set(false) })

            while (running.get()) {
                val key = ws.poll(1, TimeUnit.SECONDS)
                if (key != null) {
                    val dir = keyToDir[key]
                    if (dir != null) {
                        key.pollEvents().forEach { ev ->
                            if (ev.kind() == ENTRY_CREATE) {
                                @Suppress("UNCHECKED_CAST")
                                val filename = (ev as? java.nio.file.WatchEvent<Path>)?.context()?.fileName?.toString()
                                if (filename != null && !filename.startsWith(".")) {
                                    handleFile(dir, filename)
                                }
                            }
                        }
                    }
                    key.reset()
                }
            }

            log("FileWatcherAgent stopped.")
            ws.close()
        }
    }
}
