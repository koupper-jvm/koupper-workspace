// AgentCreatorAgent.kts — CORTEX Wizard
// Interactive wizard: collects name, role, objective then uses InferenceEngine
// to generate a working Koupper agent script with a correction loop.

import com.koupper.container.app
import com.koupper.providers.agent.AgentMessage
import com.koupper.providers.agent.InferenceEngine
import com.koupper.providers.commandbridge.CommandBridgeProvider
import com.koupper.shared.annotations.Export
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class WizardStep { NAME, ROLE, OBJECTIVE, GENERATING, DONE }

@Export
val setup: () -> Unit = {
    val jobsDir   = koupper.files().load(env("CORTEX_JOBS_DIR", "$home/.koupper/jobs"))
    val sessionId = "wizard-${System.currentTimeMillis()}"

    val agentsDir   = koupper.files().load(home, ".koupper/agents").also { it.mkdirs() }
    val wizardInDir = koupper.files().load(jobsDir, "commands/wizard").also { it.mkdirs() }
    val logDir      = koupper.files().load(jobsDir, "logs/wizard").also { it.mkdirs() }
    val logFile     = koupper.files().load(logDir, "$sessionId.log")
    val queueDir    = koupper.files().load(jobsDir, "wizard").also { it.mkdirs() }
    val procFile    = koupper.files().load(queueDir, "$sessionId.json.processing")

    logFile.writeText("")

    fun ts()           = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) { logFile.appendText("[${ts()}] $msg\n"); emit(msg) }
    fun ask(msg: String) { logFile.appendText("[${ts()}] [?] $msg\n"); emit("[?] $msg") }

    procFile.writeText("""{"id":"$sessionId","fileName":"AgentCreatorAgent","functionName":"setup","scriptPath":"agents/AgentCreatorAgent.kts","sourceType":"script"}""")

    // ── Prompt ────────────────────────────────────────────────────────────────

    val exportTag = "@" + "Export"

    fun buildPrompt(name: String, role: String, objective: String) = """
Write a Koupper agent script in Kotlin. Output ONLY code, no markdown, no explanation.

Rules:
- Start with: // $name.kts
- Imports: com.koupper.container.app, com.koupper.shared.annotations.Export, java.io.File, java.time.*
- Preamble already provides: home:String, env(name,default=""):String, emit(text):Unit
- One entrypoint: $exportTag  val setup: () -> Unit = { ... }
- Inside setup: val jobsDir = koupper.files().load(env("CORTEX_JOBS_DIR", System.getProperty("user.home") + "/.koupper/jobs"))
- Write logs to koupper.files().load(jobsDir, "logs/default/$name-session.log")
- Use app.getInstance(HtppClient::class) for HTTP, app.getInstance(RSSReader::class) for feeds
- No TODO comments — implement the logic fully

Agent:
Name: $name | Role: $role
Objective: $objective
""".trimIndent()

    fun buildCorrectionPrompt(issues: List<String>, code: String) = """
The following Koupper agent script has issues. Fix them and return ONLY the corrected code.

ISSUES TO FIX:
${issues.joinToString("\n") { "- $it" }}

Rules: no markdown fences, no explanation, no new TODO comments.

CODE:
$code
""".trimIndent()

    // ── Code extraction ───────────────────────────────────────────────────────

    fun extractCode(raw: String): String {
        // Strip markdown fences in any variation
        val stripped = Regex("```(?:kotlin|kts)?\\s*\\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)
            .find(raw)?.groupValues?.get(1)?.trim() ?: raw.trim()
        // Remove leading/trailing blank lines
        return stripped.lines().dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
            .joinToString("\n")
    }

    fun validateCode(code: String): List<String> {
        val issues = mutableListOf<String>()
        if (!code.contains("@Export")) issues += "Missing @Export annotation"
        if (!code.contains("val setup")) issues += "Missing 'val setup' entrypoint"
        val todoCount = Regex("// ?TODO").findAll(code).count()
        if (todoCount > 0) issues += "$todoCount TODO comment(s) not implemented — replace with real code"
        if (code.contains("// implement") || code.contains("// add logic"))
            issues += "Placeholder comments found — implement the actual logic"
        return issues
    }

    // ── Scaffold (defined first so generateWithLLM can reference it) ──────────

    fun generateScaffold(name: String, role: String, objective: String): String {
        val ann = "@" + "Export"
        return """
// $name.kts
// Role      : $role
// Objective : $objective

import com.koupper.shared.annotations.Export
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

$ann
val setup: () -> Unit = {
    val jobsDir = koupper.files().load(env("CORTEX_JOBS_DIR", "${'$'}home/.koupper/jobs"))
    val logDir  = koupper.files().load(jobsDir, "logs/default").also { it.mkdirs() }
    val logFile = koupper.files().load(logDir, "$name-session.log")
    fun ts()         = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(m: String) { logFile.appendText("[${'$'}{ts()}] ${'$'}m\n"); emit(m) }

    log("$name started")
    // TODO: Implement $objective
    log("$name done")
}
""".trimIndent()
    }

    // ── LLM call ─────────────────────────────────────────────────────────────

    fun callLLM(engine: InferenceEngine, systemMsg: String, userMsg: String): String {
        val history = listOf(
            AgentMessage("system", systemMsg),
            AgentMessage("user", userMsg)
        )
        return runCatching {
            runBlocking { engine.predict<String>(history) }.toString().trim()
        }.getOrElse { e ->
            log("  ⚠ LLM call failed: ${e.message?.take(80)}")
            ""
        }
    }

    fun generateWithLLM(name: String, role: String, objective: String): String {
        log("")
        log("  ◈ Generating agent with LLM...")
        log("")

        val engine = runCatching { app.getInstance(InferenceEngine::class) }.getOrNull()
        if (engine == null) {
            log("  ⚠ InferenceEngine not available — generating scaffold.")
            return generateScaffold(name, role, objective)
        }

        val systemMsg = "You are an expert Kotlin developer. Output only valid Kotlin code, no markdown, no explanation."

        // Pass 1 — generate
        val raw1 = callLLM(engine, systemMsg, buildPrompt(name, role, objective))
        if (raw1.isBlank()) return generateScaffold(name, role, objective)

        var code = extractCode(raw1)
        val issues = validateCode(code)

        if (issues.isEmpty()) {
            log("  ✓ Code generated and validated.")
            return code
        }

        // Pass 2 — correction loop
        log("  ↺ Issues found: ${issues.joinToString("; ")}. Requesting correction...")
        val raw2 = callLLM(engine, systemMsg, buildCorrectionPrompt(issues, code))
        if (raw2.isNotBlank()) {
            code = extractCode(raw2)
            val remaining = validateCode(code)
            if (remaining.isEmpty()) {
                log("  ✓ Correction successful.")
            } else {
                log("  ⚠ ${remaining.size} issue(s) remain — saving anyway.")
            }
        }

        return code
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun saveAgent(name: String, role: String, objective: String, code: String) {
        koupper.files().load(agentsDir, "$name.kts").writeText(code)
        koupper.files().load(agentsDir, "$name.skill.json").writeText("""
{
  "name": "$name",
  "version": "1.0.0",
  "description": "$objective",
  "role": "$role",
  "entrypoint": "setup",
  "triggers": ["manual", "worker-job"],
  "tags": ["generated"]
}
""".trimIndent())

        log("")
        log("┌─────────────────────────────────────┐")
        log("│   AGENT READY                       │")
        log("└─────────────────────────────────────┘")
        log("  NAME : $name")
        log("  FILE : ~/.koupper/agents/$name.kts")
        log("")
        log("  Run: koupper run ~/.koupper/agents/$name.kts")
    }

    // ── State machine ─────────────────────────────────────────────────────────

    var step = WizardStep.NAME
    val draft = mutableMapOf<String, String>()

    fun nextQuestion() = when (step) {
        WizardStep.NAME      -> ask("Agent name? (e.g. GitStatusAgent)")
        WizardStep.ROLE      -> ask("Agent role / specialty?")
        WizardStep.OBJECTIVE -> ask("What should it do? Be specific.")
        else                 -> {}
    }

    fun processAnswer(answer: String) {
        when (step) {
            WizardStep.NAME -> {
                val name = answer.trim().replace(" ", "").let {
                    if (it.endsWith("Agent")) it else "${it}Agent"
                }
                draft["name"] = name
                log("  ✓ Name: $name")
                step = WizardStep.ROLE
                nextQuestion()
            }
            WizardStep.ROLE -> {
                draft["role"] = answer.trim()
                log("  ✓ Role: ${answer.trim()}")
                step = WizardStep.OBJECTIVE
                nextQuestion()
            }
            WizardStep.OBJECTIVE -> {
                draft["objective"] = answer.trim()
                log("  ✓ Objective: ${answer.trim()}")
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

    // ── Main loop ─────────────────────────────────────────────────────────────

    log("┌─────────────────────────────────────┐")
    log("│   CORTEX WIZARD — AGENT CREATOR     │")
    log("└─────────────────────────────────────┘")
    log("")
    nextQuestion()

    val bridge   = app.getInstance(CommandBridgeProvider::class)
    val deadline = System.currentTimeMillis() + 10 * 60 * 1000L

    bridge.watch(wizardInDir).drain()

    while (step != WizardStep.DONE && step != WizardStep.GENERATING
           && System.currentTimeMillis() < deadline) {
        val answer = bridge.nextCommand() ?: continue
        if (answer.isNotBlank()) processAnswer(answer)
    }

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
