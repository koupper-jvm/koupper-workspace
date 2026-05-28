// CortexAgent.kts — CORTEX Orchestrator
// Real Koupper agent using local LLM inference (llama-server via LlamaServerSidecar).
// Launched by koupper-monitor on startup. Communicates via CommandBridge files.
// Required env vars:
//   KOUPPER_LLM_MODEL_PATH   — path to the .gguf model file
//   KOUPPER_LLM_EXECUTABLE   — path to llama-server binary (default: llama-server)
//   CORTEX_JOBS_DIR          — jobs dir (default: ~/.koupper/jobs)

import com.koupper.container.app
import com.koupper.shared.annotations.Export
import com.koupper.providers.agent.AgentMessage
import com.koupper.providers.agent.InferenceEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.*
import java.nio.file.StandardWatchEventKinds.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

// ── Setup ─────────────────────────────────────────────────────────────────────

val home      = System.getProperty("user.home")
val jobsDir   = File(System.getenv("CORTEX_JOBS_DIR") ?: "$home/.koupper/jobs")
val agentsDir = File(home, ".koupper/agents").also { it.mkdirs() }

val SESSION_ID  = "cortex-session"
val queueDir    = File(jobsDir, "cortex").also   { it.mkdirs() }
val logDir      = File(jobsDir, "logs/cortex").also { it.mkdirs() }
val cmdInDir    = File(jobsDir, "commands/wizard").also { it.mkdirs() }
val procFile    = File(queueDir, "$SESSION_ID.json.processing")
val logFile     = File(logDir,   "$SESSION_ID.log")

logFile.writeText("")

fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n")

// Register job entry so monitor shows CORTEX in the table
procFile.writeText("""{"id":"$SESSION_ID","fileName":"CortexAgent","functionName":"cortex","scriptPath":"agents/CortexAgent.kts","sourceType":"script"}""")

// ── CORTEX system prompt ──────────────────────────────────────────────────────

val SYSTEM_PROMPT = """
You are CORTEX, the AI orchestrator of a Koupper automation swarm.
You run entirely on local LLM infrastructure — no cloud, no remote APIs.

Your capabilities:
- Understand what the user needs in natural language
- Design and generate Koupper agent scripts (.kts files)
- Each agent you create has a clear name, role, and objective
- Generated agents follow the Koupper script pattern

When generating an agent, always wrap the script in a kotlin code block:
```kotlin
// Agent: AgentName
// Role: clear description of what this agent does
// Objective: what it achieves

import java.io.File
val home = System.getProperty("user.home")

// implementation here
println("AgentName running...")
```

After generating, tell the user the file was saved and how to run it.
Be concise. This is a terminal interface — keep responses under 10 lines unless generating code.
""".trimIndent()

// ── Drain stale responses from previous sessions ───────────────────────────────

cmdInDir.listFiles { f -> f.name.endsWith(".response") }?.forEach { it.delete() }

// ── Initialize inference engine ────────────────────────────────────────────────

@Export
val cortex: () -> Unit = {

    val engine = try {
        app.getInstance(InferenceEngine::class)
    } catch (e: Exception) {
        log("⚠ InferenceEngine not available: ${e.message}")
        log("  Check KOUPPER_LLM_MODEL_PATH and KOUPPER_LLM_EXECUTABLE.")
        procFile.delete()
        return@cortex
    }

    val history = mutableListOf(AgentMessage("system", SYSTEM_PROMPT))

    // ── Greeting via local LLM ─────────────────────────────────────────────────

    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    log("  CORTEX ONLINE — Local inference")
    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

    val agentCount = agentsDir.listFiles { f -> f.name.endsWith(".kts") && f.name != "CortexAgent.kts" }?.size ?: 0
    val pending    = jobsDir.listFiles()?.flatMap { q ->
        q.listFiles()?.filter { it.name.endsWith(".json") } ?: emptyList()
    }?.size ?: 0

    val contextMsg = "System state: $agentCount agents deployed, $pending jobs pending. Greet the user (2 lines max) and ask what they need built today."
    history.add(AgentMessage("user", contextMsg))

    try {
        val greeting = runBlocking { engine.predict<String>(history) }
        history.add(AgentMessage("assistant", greeting))
        greeting.lines().forEach { log(it) }
    } catch (e: Exception) {
        log("  Hello. I'm CORTEX. What do you need built today?")
        log("  (LLM warming up — ${e.message?.take(60)})")
    }

    log("")
    log("  Press Enter on this job to open the command bar.")
    log("  Type your request and press Enter.")
    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

    // ── Command loop ───────────────────────────────────────────────────────────

    val ws       = FileSystems.getDefault().newWatchService()
    val deadline = System.currentTimeMillis() + 60 * 60 * 1000L  // 1h session timeout

    cmdInDir.toPath().register(ws, ENTRY_CREATE)

    while (System.currentTimeMillis() < deadline) {
        val key = ws.poll(500, TimeUnit.MILLISECONDS) ?: continue

        for (ev in key.pollEvents()) {
            if (ev.kind() == OVERFLOW) continue
            @Suppress("UNCHECKED_CAST")
            val fname = (ev as WatchEvent<Path>).context().fileName.toString()
            if (!fname.endsWith(".response")) continue

            val responseFile = File(cmdInDir, fname)
            val userMsg = runCatching { responseFile.readText().trim() }.getOrDefault("")
            responseFile.delete()
            if (userMsg.isBlank()) continue

            log("")
            log("▶ $userMsg")
            log("")

            history.add(AgentMessage("user", userMsg))

            val response = try {
                runBlocking { engine.predict<String>(history) }
            } catch (e: Exception) {
                "Error during inference: ${e.message}"
            }

            history.add(AgentMessage("assistant", response))

            // Extract and save any generated .kts scripts
            val scriptRegex = Regex("```kotlin(.*?)```", RegexOption.DOT_MATCHES_ALL)
            val scriptMatch = scriptRegex.find(response)

            if (scriptMatch != null) {
                val script    = scriptMatch.groupValues[1].trim()
                val agentName = Regex("//\\s*Agent:\\s*(.+)").find(script)
                    ?.groupValues?.get(1)?.trim()
                    ?.replace(" ", "")
                    ?: "GeneratedAgent${System.currentTimeMillis() % 1000}"

                val agentFile = File(agentsDir, "$agentName.kts")
                agentFile.writeText(script)

                // Print response with code block replaced by confirmation
                val confirmation = "\n[✓ Agent saved → ~/.koupper/agents/$agentName.kts]\n[  Run with: koupper run ~/.koupper/agents/$agentName.kts]"
                val textOnly = response.replace(scriptMatch.value, confirmation)
                textOnly.lines().forEach { log(it) }
            } else {
                response.lines().forEach { log(it) }
            }

            log("")
        }
        key.reset()
    }

    log("[!] Session expired after 1 hour.")
    procFile.delete()
    ws.close()
}
