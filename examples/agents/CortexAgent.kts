// CortexAgent.kts — CORTEX Orchestrator (native function calling)
// Uses OpenAI-compatible function calling when available; falls back to text parsing for local models.
// Communicates via CommandBridge files and MCP server on port 18082.

import com.koupper.container.app
import com.koupper.providers.agent.AgentMessage
import com.koupper.providers.agent.InferenceEngine
import com.koupper.providers.agent.NativeToolCall
import com.koupper.providers.agent.OpenAICompatibleEngine
import com.koupper.providers.agent.ToolDefinition
import com.koupper.providers.commandbridge.CommandBridgeProvider
import com.koupper.providers.http.HtppClient
import com.koupper.providers.http.Post
import com.koupper.providers.mcp.LocalMCPClientProvider
import com.koupper.providers.mcp.LocalMCPServerProvider
import com.koupper.providers.mcp.MCPConnectedServer
import com.koupper.providers.mcp.MCPServerConfig
import com.koupper.providers.memory.MemoryProvider
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ── Setup ─────────────────────────────────────────────────────────────────────

val jobsDir   = File(env("CORTEX_JOBS_DIR", "$home/.koupper/jobs"))
val agentsDir = File(home, ".koupper/agents").also { it.mkdirs() }
val mapper    = jacksonObjectMapper()
val http      = app.getInstance(HtppClient::class)
val memory    = runCatching { app.getInstance(MemoryProvider::class) }.getOrNull()
val isCloud   = env("KOUPPER_LLM_PROVIDER").lowercase() == "openai"

val SESSION_ID = "cortex-session"
val queueDir   = File(jobsDir, "cortex").also { it.mkdirs() }

// Fallback LLM engines — tried in order when the primary fails
val fallbackEngines: List<Pair<String, InferenceEngine>> = run {
    val list = mutableListOf<Pair<String, InferenceEngine>>()
    val groqKey = env("KOUPPER_LLM_API_KEY", "")
    if (groqKey.isNotBlank() && !env("KOUPPER_LLM_API_BASE", "").contains("groq.com")) {
        runCatching {
            list.add("groq" to OpenAICompatibleEngine(
                baseUrl = "https://api.groq.com/openai/v1",
                apiKey  = groqKey,
                model   = env("KOUPPER_LLM_MODEL_FALLBACK", "llama-3.3-70b-versatile")
            ))
        }
    }
    list
}

// ── Local MCP server (bash + list_files tools on port 18082) ─────────────────
val mcpServer = runCatching {
    val srv = LocalMCPServerProvider()
    srv.registerTool("bash", "Run a shell command and return stdout",
        mapOf("type" to "object", "properties" to mapOf(
            "command" to mapOf("type" to "string", "description" to "Shell command to execute")
        ), "required" to listOf("command"))
    ) { args ->
        val cmd = args["command"]?.toString() ?: return@registerTool "missing command"
        val proc = ProcessBuilder("bash", "-c", cmd)
            .redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        out.take(4000).ifBlank { "(no output)" }
    }
    srv.registerTool("list_files", "List files in a directory",
        mapOf("type" to "object", "properties" to mapOf(
            "path" to mapOf("type" to "string", "description" to "Directory path")
        ), "required" to listOf("path"))
    ) { args ->
        val path = args["path"]?.toString() ?: return@registerTool "missing path"
        val dir = File(path.replace("~", home))
        if (!dir.exists()) return@registerTool "Directory not found: $path"
        dir.listFiles()?.joinToString("\n") { f ->
            "${if (f.isDirectory) "dir" else "file"}  ${f.name}  (${f.length()} bytes)"
        } ?: "(empty)"
    }
    srv.startHttp()
    srv
}.getOrNull()
val logDir     = File(jobsDir, "logs/cortex").also { it.mkdirs() }
val cmdInDir   = File(jobsDir, "commands/wizard").also { it.mkdirs() }
val procFile   = File(queueDir, "$SESSION_ID.json.processing")
val logFile    = File(logDir,   "$SESSION_ID.log")

logFile.writeText("")

fun ts()              = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n")

procFile.writeText("""{"id":"$SESSION_ID","fileName":"CortexAgent","functionName":"cortex","scriptPath":"agents/CortexAgent.kts","sourceType":"script"}""")

// ── External MCP servers ──────────────────────────────────────────────────────

data class ExternalMcpServer(val client: LocalMCPClientProvider, val connected: MCPConnectedServer, val namePrefix: String)

fun loadExternalMcpServers(): List<ExternalMcpServer> {
    val configFile = File(home, ".koupper/mcp/servers.json")
    if (!configFile.exists()) return emptyList()
    return runCatching {
        val configs = mapper.readValue<List<Map<String, Any>>>(configFile)
        configs.mapNotNull { cfg ->
            val name      = cfg["name"]?.toString() ?: return@mapNotNull null
            val transport = cfg["transport"]?.toString() ?: "http"
            val cfgArgs = (cfg["args"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val cfgEnv  = (cfg["env"] as? Map<*, *>)?.entries
                ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
                ?.toMap() ?: emptyMap()
            val serverConfig = MCPServerConfig(
                name      = name,
                transport = transport,
                url       = cfg["url"]?.toString(),
                command   = cfg["command"]?.toString(),
                args      = cfgArgs,
                env       = cfgEnv
            )
            val client = LocalMCPClientProvider()
            runCatching {
                val connected = client.connect(serverConfig)
                log("  External MCP: $name (${connected.tools.size} tools) [$transport]")
                ExternalMcpServer(client, connected, name)
            }.onFailure { e -> log("  ⚠ Could not connect to MCP '$name': ${e.message?.take(80)}") }
            .getOrNull()
        }
    }.getOrDefault(emptyList())
}

// ── MCP client ────────────────────────────────────────────────────────────────

fun listMcpTools(): List<Map<String, Any>> = runCatching {
    val resp = http.get { url = "http://127.0.0.1:18082/mcp/tools" }
    val raw  = resp?.asString() ?: return@runCatching emptyList()
    @Suppress("UNCHECKED_CAST")
    val body = mapper.readValue<Map<String, Any>>(raw)
    (body["tools"] as? List<Map<String, Any>>) ?: emptyList()
}.getOrDefault(emptyList())

fun callMcpTool(toolName: String, args: Map<String, Any?>): String = runCatching {
    val payload = mapper.writeValueAsString(mapOf("name" to toolName, "arguments" to args))
    val resp = http.post {
        url = "http://127.0.0.1:18082/mcp/call"
        headers["Content-Type"] = "application/json"
        body { json(payload) }
    }
    val tree = mapper.readTree(resp.asString() ?: "{}")
    val result = tree.get("result")
    result?.get("content")?.get(0)?.get("text")?.asText()
        ?: result?.toString()
        ?: tree.get("error")?.toString()
        ?: "no result"
}.getOrElse { e -> "Error calling $toolName: ${e.message?.take(80)}" }

// ── Tool definitions for native function calling ──────────────────────────────

// Normalize a property type to OpenAI-compatible types (string/integer/number/boolean/array/object).
// Converts boolean to string with enum, ensures arrays have items, drops unsupported keys.
@Suppress("UNCHECKED_CAST")
fun sanitizeProperty(prop: Map<String, Any>): Map<String, Any> {
    // Flatten anyOf/oneOf/allOf → string
    if ("anyOf" in prop || "oneOf" in prop || "allOf" in prop) {
        val r = mutableMapOf<String, Any>("type" to "string")
        prop["description"]?.let { r["description"] = it.toString() }
        return r
    }

    // Resolve type — handle nullable arrays like ["string", "null"]
    val type = when (val rawType = prop["type"]) {
        is List<*> -> rawType.filterNotNull().firstOrNull { it != "null" }?.toString() ?: "string"
        is String  -> rawType
        else       -> "string"
    }

    return when (type) {
        "array" -> {
            val rawItems = prop["items"]
            val sanitizedItems = if (rawItems is Map<*, *>)
                sanitizeProperty(rawItems as Map<String, Any>)
            else mapOf("type" to "string")
            val r = mutableMapOf<String, Any>("type" to "array", "items" to sanitizedItems)
            prop["description"]?.let { r["description"] = it.toString() }
            r
        }
        "object" -> {
            val nested = (prop["properties"] as? Map<String, Any>) ?: emptyMap()
            val r = mutableMapOf<String, Any>(
                "type" to "object",
                "properties" to nested.mapValues { (_, v) ->
                    sanitizeProperty((v as? Map<String, Any>) ?: mapOf("type" to "string"))
                }
            )
            prop["required"]?.let { r["required"] = it }
            prop["description"]?.let { r["description"] = it.toString() }
            r
        }
        else -> {
            val r = mutableMapOf<String, Any>("type" to type)
            prop["description"]?.let { r["description"] = it.toString() }
            prop["enum"]?.let { r["enum"] = it }
            r
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun sanitizeSchema(raw: Map<String, Any>): Map<String, Any> {
    val props = (raw["properties"] as? Map<String, Any>) ?: emptyMap()
    val sanitized = props.mapValues { (_, v) ->
        sanitizeProperty((v as? Map<String, Any>) ?: mapOf("type" to "string"))
    }
    val r = mutableMapOf<String, Any>("type" to "object", "properties" to sanitized)
    raw["required"]?.let { r["required"] = it }
    return r
}

@Suppress("UNCHECKED_CAST")
fun buildToolDefinitions(
    localTools: List<Map<String, Any>>,
    externalServers: List<ExternalMcpServer>
): List<ToolDefinition> {
    val defs = mutableListOf<ToolDefinition>()

    for (t in localTools) {
        val name   = t["name"]?.toString() ?: continue
        val desc   = t["description"]?.toString() ?: ""
        val raw    = (t["inputSchema"] as? Map<String, Any>)
            ?: mapOf("type" to "object", "properties" to emptyMap<String, Any>())
        defs.add(ToolDefinition(name, desc, sanitizeSchema(raw)))
    }

    // External MCP servers: exclude Playwright (too many tools) and limit GitHub to read/search
    // to stay within Groq free-tier TPM limits (6000 TPM — tools definitions consume ~100 tokens each).
    val githubReadOnly = setOf("search_repositories","search_code","search_issues","search_users",
        "get_file_contents","list_commits","list_issues","get_issue","list_pull_requests",
        "get_pull_request","get_pull_request_files","get_pull_request_status")
    for (srv in externalServers.filter { it.namePrefix != "playwright" }) {
        for (t in srv.connected.tools) {
            if (srv.namePrefix == "github" && t.name !in githubReadOnly) continue
            val prefixed = "${srv.namePrefix}.${t.name}"
            val raw      = (t.inputSchema as? Map<String, Any>)
                ?: mapOf("type" to "object", "properties" to emptyMap<String, Any>())
            defs.add(ToolDefinition(prefixed, t.description ?: "", sanitizeSchema(raw)))
        }
    }

    if (memory != null) {
        defs.add(ToolDefinition("memory.remember", "Store a fact in long-term memory",
            mapOf("type" to "object", "properties" to mapOf("text" to mapOf("type" to "string", "description" to "Fact to store")), "required" to listOf("text"))))
        defs.add(ToolDefinition("memory.recall", "Search long-term memory",
            mapOf("type" to "object", "properties" to mapOf("query" to mapOf("type" to "string"), "topK" to mapOf("type" to "integer")), "required" to listOf("query"))))
        defs.add(ToolDefinition("memory.forget", "Remove a memory entry by id",
            mapOf("type" to "object", "properties" to mapOf("id" to mapOf("type" to "string")), "required" to listOf("id"))))
    }

    defs.add(ToolDefinition("telegram.send_photo",
        "Send an image file from the local filesystem to the current Telegram chat",
        mapOf("type" to "object",
            "properties" to mapOf(
                "path"    to mapOf("type" to "string", "description" to "Absolute path to the image file on this machine"),
                "caption" to mapOf("type" to "string", "description" to "Optional caption for the image")
            ),
            "required" to listOf("path"))))

    return defs
}

fun buildSystemPrompt(toolDefs: List<ToolDefinition>): String = buildString {
    appendLine("You are CORTEX, an autonomous engineer and coding assistant.")
    appendLine("You have access to tools. Use them proactively — don't ask permission to create files or run commands.")
    appendLine()
    appendLine("Key tools available:")
    appendLine("  bash       — run shell commands (mkdir, npm, git, etc.)")
    appendLine("  write_file — create or overwrite a file (~ supported)")
    appendLine("  read_file  — read a file")
    appendLine("  list_dir   — list directory contents")
    appendLine("  fetch_url  — fetch a URL")
    appendLine("  create_agent + run_agent — create and run Koupper .kts scripts")
    appendLine()
    appendLine("PROJECT SCAFFOLDING RULES:")
    appendLine("  - To create ~/projects/mi-app: cwd=~/projects, command='npm create vite@latest mi-app -- --template react-ts'")
    appendLine("  - ALWAYS use relative project name as target, parent dir as cwd — never absolute path as vite target")
    appendLine("  - Verify with 'npm run build' (not 'npm run dev' — dev server never exits)")
    appendLine("KOUPPER SCRIPTS: import com.koupper.shared.annotations.Export; @" + "Export val setup: () -> Unit = { ... }")
}

fun truncateToolResult(raw: String, maxChars: Int = 2000): String {
    if (raw.length <= maxChars) return raw
    // For JSON arrays, keep only the first few items so structure is preserved
    val trimmed = raw.trimStart()
    if (trimmed.startsWith("[")) {
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            val list = mapper.readValue<List<Any>>(raw)
            val keep = list.take(3)
            val truncated = mapper.writeValueAsString(keep)
            if (list.size > 3) "$truncated\n... (${list.size - 3} more items truncated)" else truncated
        }.getOrElse { raw.take(maxChars) + "\n...(truncated)" }
    }
    return raw.take(maxChars) + "\n...(truncated)"
}

// ── Tool execution ────────────────────────────────────────────────────────────

fun executeToolCall(name: String, args: Map<String, Any?>, externalServers: List<ExternalMcpServer>): String {
    val dotIdx = name.indexOf('.')
    return when {
        name == "telegram.send_photo" -> {
            val path    = args["path"]?.toString() ?: return "missing 'path'"
            val caption = args["caption"]?.toString() ?: ""
            val queueDir = File(home, ".koupper/jobs/telegram/photo_queue").also { it.mkdirs() }
            val req = mapper.writeValueAsString(mapOf("path" to path, "caption" to caption))
            File(queueDir, "${System.currentTimeMillis()}.json").writeText(req)
            "Photo queued for Telegram delivery: $path"
        }
        name.startsWith("memory.") && memory != null -> {
            when (name.removePrefix("memory.")) {
                "remember" -> {
                    val text = args["text"]?.toString() ?: return "missing 'text'"
                    """{"id":"${memory.remember(text)}","status":"stored"}"""
                }
                "recall" -> {
                    val query = args["query"]?.toString() ?: return "missing 'query'"
                    mapper.writeValueAsString(memory.recall(query, (args["topK"] as? Number)?.toInt() ?: 5))
                }
                "forget" -> {
                    val id = args["id"]?.toString() ?: return "missing 'id'"
                    """{"id":"$id","removed":${memory.forget(id)}}"""
                }
                else -> "Unknown memory action"
            }
        }
        dotIdx > 0 -> {
            val serverName = name.substring(0, dotIdx)
            val actualTool = name.substring(dotIdx + 1)
            val srv = externalServers.firstOrNull { it.namePrefix == serverName }
                ?: return "Unknown external MCP server: $serverName"
            srv.client.callTool(srv.connected, actualTool, args).toString()
        }
        else -> callMcpTool(name, args)
    }
}

// ── Job verification ──────────────────────────────────────────────────────────

fun waitForJob(jobId: String, queue: String, timeoutMs: Long = 300_000): String {
    val jobLog  = File(jobsDir, "logs/$queue/$jobId.log")
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!jobLog.exists() && System.currentTimeMillis() < deadline) Thread.sleep(500)
    while (System.currentTimeMillis() < deadline) {
        val content = runCatching { jobLog.readText() }.getOrDefault("")
        when {
            "[DONE]"    in content -> return "DONE"
            "[FAILED]"  in content -> return "FAILED"
            "[TIMEOUT]" in content -> return "TIMEOUT"
        }
        Thread.sleep(1_000)
    }
    return "TIMEOUT"
}

fun jobOutput(jobId: String, queue: String): String = runCatching {
    File(jobsDir, "logs/$queue/$jobId.log").readLines().takeLast(40).joinToString("\n")
}.getOrDefault("(log not found)")

fun verifyJobResult(rawResult: String): String {
    val data  = runCatching { mapper.readValue<Map<String, Any?>>(rawResult) }.getOrNull()
    val jobId = data?.get("jobId")?.toString() ?: return rawResult
    val queue = data["queue"]?.toString() ?: "default"
    log("  ⏳ waiting for job $jobId [$queue]...")
    val status = waitForJob(jobId, queue)
    val output = jobOutput(jobId, queue)
    return when (status) {
        "DONE"   -> { log("  ✓ $jobId completed"); "JOB_DONE(jobId=$jobId)\n$output" }
        "FAILED" -> { log("  ✗ $jobId FAILED"); "JOB_FAILED(jobId=$jobId)\n$output\n\nFix the error and retry." }
        else     -> { log("  ⏱ $jobId timed out"); "JOB_TIMEOUT(jobId=$jobId)" }
    }
}

// ── Native function calling loop ──────────────────────────────────────────────

fun memoryFallback(query: String): String {
    if (memory != null) {
        val recalls = runCatching { memory.recall(query, topK = 3) }.getOrDefault(emptyList())
        if (recalls.isNotEmpty()) {
            val ctx = recalls.joinToString("\n") { "• ${it.text.take(300)}" }
            return "📚 (LLM no disponible — respondiendo desde memoria)\n\n$ctx"
        }
    }
    return "⚠ Estoy teniendo dificultades de comunicación en este momento. El servidor LLM no está disponible. Intenta más tarde o verifica que el servidor esté encendido."
}

fun inferWithNativeTools(
    history: MutableList<AgentMessage>,
    engine: InferenceEngine,
    toolDefs: List<ToolDefinition>,
    externalServers: List<ExternalMcpServer>,
    maxIters: Int = 20
): String {
    var iters = 0
    var lastText = ""

    while (iters < maxIters) {
        logFile.appendText("[${ts()}] ")
        val result = runCatching {
            runBlocking { engine.predictWithTools(history, toolDefs) }
        }.getOrElse { e ->
            val err = "[Error: ${e.message?.take(100)}]"
            logFile.appendText("$err\n")
            return err
        }

        lastText = result.text

        if (result.toolCalls.isEmpty()) {
            if (lastText.isNotBlank()) logFile.appendText(lastText)
            logFile.appendText("\n")
            break
        }

        if (lastText.isNotBlank()) logFile.appendText(lastText)
        logFile.appendText("\n")

        // Add assistant message with ALL tool calls in one batch
        history.add(AgentMessage(
            role             = "assistant",
            content          = lastText,
            nativeToolCalls  = result.toolCalls
        ))

        // Execute all tool calls and add tool result messages
        for (tc in result.toolCalls) {
            log("  → ${tc.name}(${mapper.writeValueAsString(tc.arguments).take(120)})")
            val rawResult = runCatching {
                executeToolCall(tc.name, tc.arguments, externalServers)
            }.getOrElse { e -> "Error: ${e.message?.take(80)}" }

            val toolResult = if (tc.name == "run_agent") verifyJobResult(rawResult) else rawResult
            val contextResult = truncateToolResult(toolResult)
            log("  ↳ ${toolResult.take(200)}")
            log("")

            // Tool result message — role="tool", tool_call_id matches the tc.id
            history.add(AgentMessage(
                role     = "tool",
                content  = contextResult,
                toolCall = com.koupper.providers.agent.ToolCall(
                    toolName  = tc.name,
                    action    = tc.id,   // tool_call_id
                    arguments = tc.arguments
                )
            ))
        }

        iters++
    }

    return lastText
}

// ── Main entry point ──────────────────────────────────────────────────────────

@Export
val setup: () -> Unit = {

    val engine = runCatching { app.getInstance(InferenceEngine::class) }.getOrElse { e ->
        log("⚠ InferenceEngine not available: ${e.message}")
        procFile.delete()
        null
    }

    if (engine != null) {
        val localTools      = listMcpTools()
        val externalServers = loadExternalMcpServers()
        val toolDefs        = buildToolDefinitions(localTools, externalServers)
        val history         = mutableListOf(AgentMessage("system", buildSystemPrompt(toolDefs)))

        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        log("  CORTEX ONLINE — ${if (isCloud) "Cloud (${env("KOUPPER_LLM_MODEL") ?: "?"})" else "Local"}")
        log("  Tools: ${toolDefs.size} (${localTools.size} MCP + ${externalServers.sumOf { it.connected.tools.size }} external${if (memory != null) " + 3 memory" else ""})")
        log("  Mode: ${if (isCloud) "native function calling" else "text-based CORTEX_TOOL"}")
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val agentCount = agentsDir.listFiles { f -> f.name.endsWith(".kts") && f.name != "CortexAgent.kts" }?.size ?: 0
        val pending    = jobsDir.listFiles()?.flatMap { q ->
            q.listFiles()?.filter { it.name.endsWith(".json") } ?: emptyList()
        }?.size ?: 0

        history.add(AgentMessage("user",
            "System state: $agentCount agents deployed, $pending jobs pending. " +
            "Greet the user in 1-2 lines and ask what to build."
        ))

        val greeting = runCatching {
            runBlocking { engine.predict<String>(history) }
        }.getOrDefault("CORTEX ready. What do you want to build?")
        history.add(AgentMessage("assistant", greeting))
        logFile.appendText("[${ts()}] $greeting\n")
        log("")
        log("  Press Enter on this job to open the command bar.")
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        val bridge   = app.getInstance(CommandBridgeProvider::class)
        val deadline = System.currentTimeMillis() + 8 * 60 * 60 * 1000L

        bridge.watch(cmdInDir).drain()

        // Dedup window: ignore same command if it arrives again within 2s (watcher fires multiple events per file write)
        val recentCmds = mutableMapOf<Int, Long>()

        while (System.currentTimeMillis() < deadline) {
            val userMsg = bridge.nextCommand() ?: continue

            val now  = System.currentTimeMillis()
            val hash = userMsg.trimEnd().hashCode()
            recentCmds.entries.removeIf { now - it.value > 2_000L }
            if (hash in recentCmds) continue
            recentCmds[hash] = now

            log("▶ $userMsg")
            log("")

            val contextMsg = if (memory != null) {
                val recalls = runCatching { memory.recall(userMsg, topK = 3) }.getOrDefault(emptyList())
                if (recalls.isNotEmpty()) {
                    val ctx = recalls.joinToString("\n") { "- ${it.text.take(400)}" }
                    "[CONTEXT:\n$ctx]\n$userMsg"
                } else userMsg
            } else userMsg

            history.add(AgentMessage("user", contextMsg))
            val historyMark = history.size

            var reply = inferWithNativeTools(history, engine, toolDefs, externalServers)

            if (reply.startsWith("[Error:")) {
                var recovered = false
                for ((fbName, fbEngine) in fallbackEngines) {
                    while (history.size > historyMark) history.removeAt(history.size - 1)
                    log("  ↺ Primary LLM failed — trying $fbName")
                    reply = inferWithNativeTools(history, fbEngine, toolDefs, externalServers)
                    if (!reply.startsWith("[Error:")) { recovered = true; break }
                }
                if (!recovered) {
                    while (history.size > historyMark) history.removeAt(history.size - 1)
                    reply = memoryFallback(userMsg)
                    log(reply)
                }
            }

            history.add(AgentMessage("assistant", reply))
            log("")
        }

        log("[!] Session ended.")
        bridge.close()
    }
    procFile.delete()
}
