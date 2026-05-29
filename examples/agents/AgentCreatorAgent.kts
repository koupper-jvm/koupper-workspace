// AgentCreatorAgent.kts — CORTEX Wizard
// Interactive wizard: collects name, role, objective then uses InferenceEngine
// to generate a working Koupper agent script (not just a scaffold).
// Driven by CommandBridgeProvider — reads answers from commands/wizard/*.response.

import com.koupper.container.app
import com.koupper.providers.agent.AgentMessage
import com.koupper.providers.agent.InferenceEngine
import com.koupper.providers.agent.TokenListener
import com.koupper.providers.commandbridge.CommandBridgeProvider
import com.koupper.shared.annotations.Export
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking

enum class WizardStep { NAME, ROLE, OBJECTIVE, GENERATING, DONE }

@Export
val setup: () -> Unit = {
    val home      = System.getProperty("user.home")!!
    val jobsDir   = File(System.getenv("CORTEX_JOBS_DIR") ?: "$home/.koupper/jobs")
    val sessionId = "wizard-${System.currentTimeMillis()}"

    val agentsDir   = File(home, ".koupper/agents").also { it.mkdirs() }
    val wizardInDir = File(jobsDir, "commands/wizard").also { it.mkdirs() }
    val logDir      = File(jobsDir, "logs/wizard").also { it.mkdirs() }
    val logFile     = File(logDir, "$sessionId.log")
    val queueDir    = File(jobsDir, "wizard").also { it.mkdirs() }
    val procFile    = File(queueDir, "$sessionId.json.processing")

    logFile.writeText("")

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n")
    fun ask(msg: String) = logFile.appendText("[${ts()}] [?] $msg\n")

    procFile.writeText("""{"id":"$sessionId","fileName":"AgentCreatorAgent","functionName":"setup","scriptPath":"agents/AgentCreatorAgent.kts","sourceType":"script"}""")

    // ── State machine ─────────────────────────────────────────────────────────

    var step = WizardStep.NAME
    val draft = mutableMapOf<String, String>()

    fun buildGenerationPrompt(name: String, role: String, objective: String): String = """
You are generating a Koupper agent script. Output ONLY the Kotlin script, no explanation.

Requirements:
- The script must import com.koupper.shared.annotations.Export
- Must have exactly one @Export annotated val named 'setup' of type () -> Unit
- Use java.io.File for log output to: File(jobsDir, "logs/default/$name-session.log")
- Use CORTEX_JOBS_DIR env var for jobsDir (default: ${'$'}home/.koupper/jobs)
- Must be self-contained — no external HTTP calls unless via Koupper SPs
- Include proper logging with timestamps using LocalDateTime

Agent specification:
  Name:      $name
  Role:      $role
  Objective: $objective

Available Koupper Service Providers (use via app.getInstance()):
  - InferenceEngine   — local LLM inference (predict, TokenListener for streaming)
  - HtppClient        — HTTP GET/POST
  - TextFileHandler   — read/write text files
  - RSSReader         — read RSS/Atom feeds (read(url): List<RSSItem {title,link,html,pubDate,source}>)
  - CommandBridgeProvider — watch directory for *.response command files

Output a complete, working .kts script that implements the agent's objective.
Start the script with these comment lines:
// $name.kts
// Role      : $role
// Objective : $objective
""".trimIndent()

    fun generateWithLLM(name: String, role: String, objective: String): String {
        log("")
        log("  ◈ Generating agent with local LLM...")
        log("  This may take a moment depending on your model.")
        log("")

        val engine = runCatching { app.getInstance(InferenceEngine::class) }.getOrNull()

        if (engine == null) {
            log("  ⚠ InferenceEngine not available — generating scaffold instead.")
            log("  Set KOUPPER_LLM_MODEL_PATH to enable LLM code generation.")
            return generateScaffold(name, role, objective)
        }

        val prompt = buildGenerationPrompt(name, role, objective)
        val history = listOf(
            AgentMessage("system", "You are an expert Kotlin developer who writes Koupper agent scripts. Output only valid Kotlin code, no markdown, no explanation."),
            AgentMessage("user", prompt)
        )

        val sb = StringBuilder()
        val listener = object : TokenListener {
            override fun onToken(token: String, agentId: String) {
                sb.append(token)
                logFile.appendText(token)
            }
        }

        return runCatching {
            runBlocking { engine.predict<String>(history, listener = listener) }
            logFile.appendText("\n")
            sb.toString().trim()
        }.getOrElse { e ->
            log("\n  ⚠ LLM generation failed: ${e.message?.take(80)}")
            log("  Falling back to scaffold.")
            generateScaffold(name, role, objective)
        }
    }

    fun generateScaffold(name: String, role: String, objective: String): String {
        val exportAnn = "@" + "Export"
        return """
// $name.kts
// Role      : $role
// Objective : $objective
// Generated by CORTEX WIZARD (scaffold — set KOUPPER_LLM_MODEL_PATH for LLM generation)

import com.koupper.shared.annotations.Export
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

$exportAnn
val setup: () -> Unit = {
    val home    = System.getProperty("user.home")!!
    val jobsDir = File(System.getenv("CORTEX_JOBS_DIR") ?: "${'$'}home/.koupper/jobs")
    val logDir  = File(jobsDir, "logs/default").also { it.mkdirs() }
    val logFile = File(logDir, "$name-session.log")
    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = logFile.appendText("[${'$'}{ts()}] ${'$'}msg\n")

    // TODO: Implement agent behavior
    // Role:      $role
    // Objective: $objective

    log("$name started")
    log("Role: $role")
    log("Objective: $objective")
    log("$name completed — READY")
}
""".trimIndent()
    }

    fun saveAgent(name: String, role: String, objective: String, code: String) {
        // Extract code block if LLM wrapped it in markdown
        val cleanCode = Regex("```kotlin\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)
            .find(code)?.groupValues?.get(1)?.trim() ?: code

        File(agentsDir, "draft_$name.json").writeText(
            """{"name":"$name","role":"$role","objective":"$objective","createdAt":"${ts()}"}"""
        )
        File(agentsDir, "$name.kts").writeText(cleanCode)

        // Write skill.json
        val exportAnn = "@" + "Export"
        File(agentsDir, "$name.skill.json").writeText("""
{
  "name": "$name",
  "version": "1.0.0",
  "description": "$objective",
  "role": "$role",
  "entrypoint": "setup",
  "inputs": [],
  "outputs": ["logs/default/$name-session.log"],
  "envVars": [],
  "providers": [],
  "triggers": ["manual", "worker-job"],
  "tags": ["generated"],
  "persistent": false
}
""".trimIndent())

        log("")
        log("┌─────────────────────────────────────┐")
        log("│   AGENT READY FOR DEPLOYMENT        │")
        log("└─────────────────────────────────────┘")
        log("  NAME       : $name")
        log("  ROLE       : $role")
        log("  OBJECTIVE  : $objective")
        log("  FILE       : ~/.koupper/agents/$name.kts")
        log("  SKILL      : ~/.koupper/agents/$name.skill.json")
        log("")
        log("  Run: koupper run ~/.koupper/agents/$name.kts")
    }

    fun nextQuestion() {
        when (step) {
            WizardStep.NAME      -> ask("What is the agent name? (e.g. DataSyncAgent)")
            WizardStep.ROLE      -> ask("What is the agent role / specialty?")
            WizardStep.OBJECTIVE -> ask("What should this agent do? (be specific)")
            else                 -> {}
        }
    }

    fun processAnswer(answer: String) {
        when (step) {
            WizardStep.NAME -> {
                val name = answer.replace(" ", "").let { if (it.endsWith("Agent")) it else "${it}Agent" }
                draft["name"] = name
                log("  ✓ Name      : $name")
                step = WizardStep.ROLE
                nextQuestion()
            }
            WizardStep.ROLE -> {
                draft["role"] = answer
                log("  ✓ Role      : $answer")
                step = WizardStep.OBJECTIVE
                nextQuestion()
            }
            WizardStep.OBJECTIVE -> {
                draft["objective"] = answer
                log("  ✓ Objective : $answer")
                step = WizardStep.GENERATING

                val name      = draft["name"]!!
                val role      = draft["role"]!!
                val objective = draft["objective"]!!

                val code = generateWithLLM(name, role, objective)
                saveAgent(name, role, objective, code)

                step = WizardStep.DONE
                Thread.sleep(300)
                procFile.delete()
            }
            else -> {}
        }
    }

    // ── Command loop ──────────────────────────────────────────────────────────

    log("┌─────────────────────────────────────┐")
    log("│   CORTEX WIZARD — AGENT CREATOR     │")
    log("└─────────────────────────────────────┘")
    log("  Type your answers in the command bar.")
    log("  LLM will generate the agent code automatically.")
    log("")
    nextQuestion()

    val bridge   = app.getInstance(CommandBridgeProvider::class)
    val deadline = System.currentTimeMillis() + 10 * 60 * 1000L

    bridge.watch(wizardInDir).drain()

    while (step != WizardStep.DONE && step != WizardStep.GENERATING && System.currentTimeMillis() < deadline) {
        val answer = bridge.nextCommand() ?: continue
        if (answer.isNotBlank()) processAnswer(answer)
    }

    // Wait for generation to finish if in progress
    val genDeadline = System.currentTimeMillis() + 5 * 60 * 1000L
    while (step == WizardStep.GENERATING && System.currentTimeMillis() < genDeadline) {
        Thread.sleep(500)
    }

    if (step != WizardStep.DONE) {
        log("")
        log("[!] Session timed out or cancelled.")
        procFile.delete()
    }

    bridge.close()
}
