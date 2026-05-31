// CortexAgent.kts — CORTEX Orchestrator
// Uses Koupper's InferenceEngine SP (LlamaServerSidecar, SSE streaming).
// Communicates via CommandBridge files and MCP server on port 18082.
//
// Required env vars:
//   KOUPPER_LLM_MODEL_PATH   — path to .gguf model file
//   KOUPPER_LLM_EXECUTABLE   — path to llama-server binary (default: llama-server)
//   CORTEX_JOBS_DIR          — jobs dir (default: ~/.koupper/jobs)

import com.koupper.container.app
import com.koupper.shared.annotations.Export
import com.koupper.providers.agent.AgentMessage
import com.koupper.providers.agent.InferenceEngine
import com.koupper.providers.agent.TokenListener
import com.koupper.providers.commandbridge.CommandBridgeProvider
import com.koupper.providers.http.HtppClient
import com.koupper.providers.http.Post
import com.koupper.providers.mcp.MCPClientProvider
import com.koupper.providers.mcp.MCPConnectedServer
import com.koupper.providers.mcp.MCPServerConfig
import com.koupper.providers.mcp.MCPToolDescriptor
import com.koupper.providers.memory.MemoryProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ── Setup ─────────────────────────────────────────────────────────────────────

val home      = System.getProperty("user.home")!!
val jobsDir   = File(System.getenv("CORTEX_JOBS_DIR") ?: "$home/.koupper/jobs")
val agentsDir = File(home, ".koupper/agents").also { it.mkdirs() }
val mapper    = jacksonObjectMapper()
val http      = app.getInstance(HtppClient::class)

val memory = runCatching { app.getInstance(MemoryProvider::class) }.getOrNull()

val SESSION_ID = "cortex-session"
val queueDir   = File(jobsDir, "cortex").also { it.mkdirs() }
val logDir     = File(jobsDir, "logs/cortex").also { it.mkdirs() }
val cmdInDir   = File(jobsDir, "commands/wizard").also { it.mkdirs() }
val procFile   = File(queueDir, "$SESSION_ID.json.processing")
val logFile    = File(logDir,   "$SESSION_ID.log")

logFile.writeText("")

fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n")

// Register job so monitor table shows CORTEX
procFile.writeText("""{"id":"$SESSION_ID","fileName":"CortexAgent","functionName":"cortex","scriptPath":"agents/CortexAgent.kts","sourceType":"script"}""")

// ── External MCP servers ──────────────────────────────────────────────────────
// Config: ~/.koupper/mcp/servers.json
// Format: [{"name":"playwright","transport":"stdio","command":"npx","args":["@playwright/mcp"]},
//          {"name":"github","transport":"http","url":"http://localhost:3001"}]

data class ExternalMcpServer(val connected: MCPConnectedServer, val namePrefix: String)

fun loadExternalMcpServers(): List<ExternalMcpServer> {
    val configFile = File(home, ".koupper/mcp/servers.json")
    if (!configFile.exists()) return emptyList()

    val client = runCatching { app.getInstance(MCPClientProvider::class) }.getOrNull() ?: return emptyList()

    return runCatching {
        val configs = mapper.readValue<List<Map<String, Any>>>(configFile)
        configs.mapNotNull { cfg ->
            val name      = cfg["name"]?.toString() ?: return@mapNotNull null
            val transport = cfg["transport"]?.toString() ?: "http"
            @Suppress("UNCHECKED_CAST")
            val serverConfig = MCPServerConfig(
                name      = name,
                transport = transport,
                url       = cfg["url"]?.toString(),
                command   = cfg["command"]?.toString(),
                args      = (cfg["args"] as? List<String>) ?: emptyList(),
                env       = (cfg["env"] as? Map<String, String>) ?: emptyMap()
            )
            runCatching {
                val connected = client.connect(serverConfig)
                log("  External MCP: $name (${connected.tools.size} tools) [$transport]")
                ExternalMcpServer(connected, name)
            }.onFailure { e -> log("  ⚠ Could not connect to MCP '$name': ${e.message?.take(60)}") }
            .getOrNull()
        }
    }.getOrDefault(emptyList())
}

// ── MCP client (calls CortexMcpServer on port 18082) ─────────────────────────

fun listMcpTools(): List<Map<String, Any>> = runCatching {
    val resp = http.get { url = "http://127.0.0.1:18082/mcp/tools" }
    @Suppress("UNCHECKED_CAST")
    val body = mapper.readValue<Map<String, Any>>(resp?.asString() ?: return emptyList())
    (body["tools"] as? List<Map<String, Any>>) ?: emptyList()
}.getOrDefault(emptyList())

fun callMcpTool(toolName: String, args: Map<String, Any?>): String = runCatching {
    val payload = mapper.writeValueAsString(mapOf(
        "name" to toolName,
        "arguments" to args
    ))
    val resp = http.post {
        url = "http://127.0.0.1:18082/mcp/call"
        headers["Content-Type"] = "application/json"
        body { json(payload) }
    }
    val tree = mapper.readTree(resp.asString() ?: "{}")
    val result = tree.get("result")
    
    // If it follows MCP format content[0].text
    result?.get("content")?.get(0)?.get("text")?.asText()
        // If it's the custom Koupper MCP format
        ?: result?.toString()
        ?: tree.get("error")?.toString()
        ?: "no result"
}.getOrElse { e -> "Error calling $toolName: ${e.message?.take(80)}" }

// ── System prompt with live tool list ────────────────────────────────────────

fun buildSystemPrompt(
    localTools: List<Map<String, Any>>,
    externalServers: List<ExternalMcpServer>
): String {
    val localLines = if (localTools.isEmpty()) "  (none)"
    else localTools.joinToString("\n") { t ->
        val name = t["name"]?.toString() ?: "unknown"
        val desc = t["description"]?.toString() ?: ""
        "  • $name: $desc"
    }

    val externalLines = if (externalServers.isEmpty()) ""
    else "\nEXTERNAL MCP TOOLS (prefix: serverName.toolName):\n" +
        externalServers.joinToString("\n") { srv ->
            srv.connected.tools.joinToString("\n") { t ->
                "  • ${srv.namePrefix}.${t.name}: ${t.description}"
            }
        }

    val memoryLines = if (memory != null) """

MEMORY TOOLS:
  • memory.remember — CORTEX_TOOL: {"tool":"memory.remember","args":{"text":"fact to store"}}
  • memory.recall   — CORTEX_TOOL: {"tool":"memory.recall","args":{"query":"what","topK":5}}
  • memory.forget   — CORTEX_TOOL: {"tool":"memory.forget","args":{"id":"abc123"}}""" else ""

    return buildString {
        appendLine("You are CORTEX, an autonomous Koupper engineer.")
        appendLine()
        appendLine("AVAILABLE TOOLS:")
        appendLine(localLines)
        append(externalLines)
        append(memoryLines)
        appendLine()
        appendLine()
        appendLine("TOOL CALL FORMAT — one per line, no markdown:")
        appendLine("""CORTEX_TOOL: {"tool":"<name>","args":{<arguments>}}""")
        appendLine()
        appendLine("WHEN A URL IS MENTIONED: always call fetch_url first, then respond.")
        appendLine("""Example: CORTEX_TOOL: {"tool":"fetch_url","args":{"url":"https://example.com"}}""")
        appendLine()
        appendLine("KOUPPER SCRIPT CONTRACT — every .kts script must follow this:")
        appendLine("  import com.koupper.shared.annotations.Export")
        appendLine("  import com.koupper.container.app")
        appendLine("  @Export val setup: () -> Unit = { /* your logic */ }")
        appendLine()
        appendLine("PROVIDER EXAMPLES:")
        appendLine("  // HTTP client")
        appendLine("  val http = app.getInstance(com.koupper.providers.http.HtppClient::class)")
        appendLine("  val resp = http.get { url = \"https://api.example.com\" }")
        appendLine("  println(resp?.asString())")
        appendLine()
        appendLine("  // HTTP endpoint server")
        appendLine("  val router = app.getInstance(com.koupper.providers.runtime.router.GrizzlyRuntimeRouterProvider::class)")
        appendLine("  router.registerRouter {")
        appendLine("    get<String> { path { \"/hello\" }; script { { -> \"Hello World\" } } }")
        appendLine("  }")
        appendLine("  router.start(8080)")
        appendLine()
        appendLine("  // Web reader (JS rendered, images, links)")
        appendLine("  val web = app.getInstance(com.koupper.providers.web.WebReaderProvider::class)")
        appendLine("  val page = web.fetch(\"https://example.com\")")
        appendLine("  println(page.title); println(page.text)")
        appendLine()
        appendLine("CREATE + RUN FLOW — output both tool calls in sequence:")
        appendLine("""  CORTEX_TOOL: {"tool":"create_agent","args":{"name":"MyAgent","content":"<full .kts content>"}}""")
        appendLine("""  CORTEX_TOOL: {"tool":"run_agent","args":{"name":"MyAgent"}}""")
        appendLine()
        appendLine("Rules: answer concisely, use tools when needed, generate valid Kotlin.")
    }
}

// ── Streaming inference with tool loop ────────────────────────────────────────

fun infer(history: List<AgentMessage>, engine: InferenceEngine): String {
    val sb = StringBuilder()
    // TokenListener writes each token directly to log file — real-time streaming
    val listener = object : TokenListener {
        override fun onToken(token: String, agentId: String) {
            sb.append(token)
            logFile.appendText(token)
        }
    }
    runCatching {
        runBlocking { engine.predict<String>(history, listener = listener) }
    }.onFailure { e ->
        val err = "[Error: ${e.message?.take(80)}]"
        logFile.appendText("$err\n")
        sb.append(err)
    }
    logFile.appendText("\n")
    return sb.toString()
}

// Returns true if the reply looks like a tool call attempt that missed the format.
// Catches patterns like: funcName(...), tool.name(...), {"tool":...}, memory.remember(...)
fun looksLikeToolCall(reply: String): Boolean {
    val lower = reply.lowercase()
    return lower.contains(Regex("""(memory\.|cortex_tool|\"tool\"\s*:|\w+\s*\(\s*[\"{\w])"""))
}

fun inferWithTools(
    history: MutableList<AgentMessage>,
    engine: InferenceEngine,
    externalServers: List<ExternalMcpServer> = emptyList(),
    maxIters: Int = 15
): String {
    logFile.appendText("[${ts()}] ")
    var reply = infer(history, engine)
    var iters = 0

    while (iters < maxIters) {
        var toolLine = reply.lines().firstOrNull { it.trimStart().startsWith("CORTEX_TOOL:") }

        // A3: if the model tried to call a tool but used wrong format, give it one retry
        if (toolLine == null && looksLikeToolCall(reply)) {
            log("  [retry] reformatting tool call...")
            history.add(AgentMessage("assistant", reply))
            history.add(AgentMessage("user",
                "Use the tool with EXACTLY this format on a single line — no code blocks, no extra text:\n" +
                """CORTEX_TOOL: {"tool":"<name>","args":{<arguments>}}""" + "\nExample: " +
                """CORTEX_TOOL: {"tool":"memory.remember","args":{"text":"the fact to store"}}"""
            ))
            logFile.appendText("[${ts()}] ")
            reply = infer(history, engine)
            toolLine = reply.lines().firstOrNull { it.trimStart().startsWith("CORTEX_TOOL:") }
        }

        toolLine ?: break
        val jsonStr  = toolLine.trimStart().removePrefix("CORTEX_TOOL:").trim()

        val result = runCatching {
            val parsed   = mapper.readValue<Map<String, Any?>>(jsonStr)
            val fullName = parsed["tool"]?.toString() ?: return@runCatching "missing 'tool' field"
            @Suppress("UNCHECKED_CAST")
            val toolArgs = parsed["args"] as? Map<String, Any?> ?: emptyMap()

            // Route: memory.* → local MemoryProvider, "playwright.*" → external MCP, else → local MCP
            val dotIdx = fullName.indexOf('.')
            if (fullName.startsWith("memory.") && memory != null) {
                val action = fullName.removePrefix("memory.")
                when (action) {
                    "remember" -> {
                        val text = toolArgs["text"]?.toString() ?: return@runCatching "missing 'text'"
                        val id = memory.remember(text)
                        """{"id":"$id","status":"stored"}"""
                    }
                    "recall" -> {
                        val query = toolArgs["query"]?.toString() ?: return@runCatching "missing 'query'"
                        val topK  = (toolArgs["topK"] as? Number)?.toInt() ?: 5
                        val matches = memory.recall(query, topK)
                        mapper.writeValueAsString(matches)
                    }
                    "forget" -> {
                        val id = toolArgs["id"]?.toString() ?: return@runCatching "missing 'id'"
                        val ok = memory.forget(id)
                        """{"id":"$id","removed":$ok}"""
                    }
                    else -> "Unknown memory action: $action"
                }
            } else if (dotIdx > 0) {
                val serverName = fullName.substring(0, dotIdx)
                val actualTool = fullName.substring(dotIdx + 1)
                val srv = externalServers.firstOrNull { it.namePrefix == serverName }
                    ?: return@runCatching "Unknown external MCP server: $serverName"
                val mcpClient = app.getInstance(MCPClientProvider::class)
                mcpClient.callTool(srv.connected, actualTool, toolArgs).toString()
            } else {
                callMcpTool(fullName, toolArgs)
            }
        }.getOrElse { e -> "error: ${e.message?.take(80)}" }

        log("  ↳ ${result.take(200)}")
        log("")

        history.add(AgentMessage("assistant", reply))
        history.add(AgentMessage("user", "TOOL_RESULT: $result"))

        logFile.appendText("[${ts()}] ")
        reply = infer(history, engine)
        iters++
    }

    return reply
}

// ── Main entry point ──────────────────────────────────────────────────────────

@Export
val cortex: () -> Unit = {

    val engine = runCatching { app.getInstance(InferenceEngine::class) }.getOrElse { e ->
        log("⚠ InferenceEngine not available: ${e.message}")
        log("  Set KOUPPER_LLM_MODEL_PATH and KOUPPER_LLM_EXECUTABLE.")
        procFile.delete()
        null
    }

    if (engine != null) {
        val localTools      = listMcpTools()
        val externalServers = loadExternalMcpServers()
        val history = mutableListOf(AgentMessage("system", buildSystemPrompt(localTools, externalServers)))

        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        log("  CORTEX ONLINE — Koupper InferenceEngine")
        log("  Built-in tools : ${localTools.size}")
        log("  External MCPs  : ${externalServers.size} servers")
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val agentCount = agentsDir.listFiles { f -> f.name.endsWith(".kts") && f.name != "CortexAgent.kts" }?.size ?: 0
        val pending    = jobsDir.listFiles()?.flatMap { q ->
            q.listFiles()?.filter { it.name.endsWith(".json") } ?: emptyList()
        }?.size ?: 0

        history.add(AgentMessage("user",
            "System state: $agentCount agents deployed, $pending jobs pending. " +
            "Greet the user warmly in 2 lines max and ask what they need built today."
        ))

        logFile.appendText("[${ts()}] ")
        val greeting = infer(history, engine)
        history.add(AgentMessage("assistant", greeting))
        log("")
        log("  Press Enter on this job to open the command bar.")
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val bridge   = app.getInstance(CommandBridgeProvider::class)
        val deadline = System.currentTimeMillis() + 60 * 60 * 1000L

        bridge.watch(cmdInDir).drain()

        while (System.currentTimeMillis() < deadline) {
            val userMsg = bridge.nextCommand() ?: continue

            log("▶ $userMsg")
            log("")

            val contextMsg = if (memory != null) {
                val recalls = memory.recall(userMsg, topK = 1)
                if (recalls.isNotEmpty()) "[CONTEXT: ${recalls.first().text.take(100)}...]\n$userMsg"
                else userMsg
            } else userMsg

            history.add(AgentMessage("user", contextMsg))
            val reply = inferWithTools(history, engine, externalServers)
            history.add(AgentMessage("assistant", reply))

            val scriptMatch = Regex("```kotlin(.*?)```", RegexOption.DOT_MATCHES_ALL).find(reply)
            if (scriptMatch != null) {
                val script    = scriptMatch.groupValues[1].trim()
                val agentName = Regex("//\\s*Agent:\\s*(.+)").find(script)
                    ?.groupValues?.get(1)?.trim()?.replace(" ", "")
                    ?: "Agent${System.currentTimeMillis() % 1000}"
                File(agentsDir, "$agentName.kts").writeText(script)
                log("[✓ Saved → ~/.koupper/agents/$agentName.kts]")
                log("[  Use run_agent to execute it]")
            }
            log("")
        }

        log("[!] Session expired after 1 hour.")
        bridge.close()
    }
    procFile.delete()
}
