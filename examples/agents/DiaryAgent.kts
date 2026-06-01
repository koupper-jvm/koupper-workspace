// DiaryAgent.kts
// Description: Resume los logs del día con el LLM y guarda el diario en memory/
// lee los logs del worker de hoy y le pide al LLM que escriba un resumen
// en ~/.koupper/memory/diary-<fecha>.md

import com.koupper.container.app
import com.koupper.providers.agent.AgentMessage
import com.koupper.providers.agent.InferenceEngine
import com.koupper.providers.agent.TokenListener
import com.koupper.shared.annotations.Export
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking

@Export
val setup: () -> Unit = {
    val home    = System.getProperty("user.home")!!
    val jobsDir = File(System.getenv("CORTEX_JOBS_DIR") ?: "$home/.koupper/jobs")
    val logDir  = File(jobsDir, "logs/default").also { it.mkdirs() }
    val memDir  = File(home, ".koupper/memory").also { it.mkdirs() }
    val today   = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val logFile = File(logDir, "diary-$today.log")

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n").also { println(msg) }

    log("=== Diary Agent ===")

    val jobLogs = jobsDir.walk()
        .filter { it.isFile && it.name.endsWith(".log") && it.lastModified() > System.currentTimeMillis() - 86_400_000 }
        .sortedByDescending { it.lastModified() }
        .take(5)
        .joinToString("\n---\n") { f ->
            "# ${f.name}\n${f.readLines().takeLast(20).joinToString("\n")}"
        }

    if (jobLogs.isBlank()) {
        log("No job logs found for today — nothing to summarize")
    } else {
        val engine = runCatching { app.getInstance(InferenceEngine::class) }.getOrNull()

        if (engine == null) {
            log("LLM not available — writing raw log summary instead")
            File(memDir, "diary-$today.md").writeText("# $today\n\n$jobLogs")
        } else {
            log("Calling LLM for diary entry...")

            val history = mutableListOf(
                AgentMessage("user",
                    "You are a technical diary assistant. Based on the following job logs from today, " +
                    "write a concise diary entry (3-5 bullet points) summarizing what ran, what succeeded, " +
                    "and what needs attention. Be direct and factual.\n\nLogs:\n$jobLogs"
                )
            )

            val entry = StringBuilder()
            val listener = object : TokenListener {
                override fun onToken(token: String, agentId: String) { entry.append(token) }
            }
            runBlocking { engine.predict<String>(history, listener = listener) }

            val diaryFile = File(memDir, "diary-$today.md")
            diaryFile.writeText("# Technical Diary — $today\n\n${entry.toString().trim()}\n")

            log("Diary written to: ${diaryFile.absolutePath}")
            log(entry.toString().trim())
        }
    }
}
