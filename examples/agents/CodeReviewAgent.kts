// CodeReviewAgent.kts
// Description: Code review de cualquier .kt/.kts con el LLM local
// Config: CODE_REVIEW_FILE env var con el path del archivo a revisar.
// Output: ~/.koupper/jobs/logs/default/code-review-<filename>.log

import com.koupper.container.app
import com.koupper.providers.agent.AgentMessage
import com.koupper.providers.agent.InferenceEngine
import com.koupper.providers.agent.TokenListener
import com.koupper.shared.annotations.Export
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking

@Export
val setup: () -> Unit = {
    val home       = System.getProperty("user.home")!!
    val jobsDir    = koupper.files().load(env("CORTEX_JOBS_DIR", "$home/.koupper/jobs"))
    val logDir     = koupper.files().load(jobsDir, "logs/default").also { it.mkdirs() }
    val targetPath = env("CODE_REVIEW_FILE", "$home/.koupper/agents/GreetingAgent.kts")

    val targetFile = koupper.files().load(targetPath)
    val logFile    = koupper.files().load(logDir, "code-review-${targetFile.nameWithoutExtension}.log")

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n").also { println(msg) }

    log("=== Code Review Agent ===")
    log("File: ${targetFile.absolutePath}")

    val code = if (targetFile.exists()) targetFile.readText() else ""

    if (!targetFile.exists()) {
        log("ERROR: file not found — set CODE_REVIEW_FILE env var")
    } else if (code.isBlank()) {
        log("ERROR: file is empty")
    } else {
        val engine = runCatching { app.getInstance(InferenceEngine::class) }.getOrNull()

        if (engine == null) {
            log("ERROR: LLM not available — start with 'koupper start'")
        } else {
            log("Sending to LLM for review...")
            log("")

            val history = mutableListOf(
                AgentMessage("user",
                    "You are a senior Kotlin engineer. Review the following code and provide:\n" +
                    "1. A one-line summary of what it does\n" +
                    "2. Up to 3 concrete issues or improvements (be specific, cite line content)\n" +
                    "3. One thing done well\n\n" +
                    "Be concise. No fluff.\n\n```kotlin\n$code\n```"
                )
            )

            val review = StringBuilder()
            val listener = object : TokenListener {
                override fun onToken(token: String, agentId: String) {
                    print(token)
                    review.append(token)
                }
            }
            runBlocking { engine.predict<String>(history, listener = listener) }
            println()

            logFile.appendText("\n${review.toString().trim()}\n")
            log("")
            log("Review complete — saved to: ${logFile.absolutePath}")
        }
    }
}
