// TelegramBridgeAgent.kts
// Role      : Telegram <-> CORTEX bidirectional bridge
// Objective : Forward Telegram messages to CortexAgent via CommandBridge,
//             monitor the cortex-session log for responses, send them back.
//
// Config: ~/.koupper/telegram.json
//   { "token": "BOT_TOKEN", "allowedChatIds": [123456789] }
//
// Setup:
//   1. Create a bot at https://t.me/BotFather -> get token
//   2. Run ~/.koupper/setup-telegram.sh to configure
//   3. Make sure CortexAgent is running
//   4. Run: koupper run ~/.koupper/agents/TelegramBridgeAgent.kts

import com.koupper.container.app
import com.koupper.providers.commandbridge.CommandBridgeProvider
import com.koupper.providers.files.fromJson
import com.koupper.providers.files.toJson
import com.koupper.shared.annotations.Export
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

@Export
val setup: () -> Unit = {
    val home    = System.getProperty("user.home")!!
    val jobsDir = koupper.files().load(env("CORTEX_JOBS_DIR", "$home/.koupper/jobs"))
    data class TelegramConfig(val token: String, val allowedChatIds: List<Long> = emptyList())

    val configFile = koupper.files().load(home, ".koupper/telegram.json")

    val rawConfig: TelegramConfig? = when {
        env("KOUPPER_TELEGRAM_TOKEN") != null -> {
            val token = env("KOUPPER_TELEGRAM_TOKEN")!!
            val ids   = env("KOUPPER_TELEGRAM_CHAT_IDS")
                ?.split(",")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
            TelegramConfig(token, ids)
        }
        configFile.exists() -> runCatching {
            configFile.readText().fromJson<TelegramConfig>()
        }.getOrElse {
            System.err.println("[TelegramBridge] Failed to parse telegram.json: ${it.message}")
            null
        }
        else -> {
            configFile.writeText("""{"token":"YOUR_BOT_TOKEN_HERE","allowedChatIds":[]}""")
            System.err.println("[TelegramBridge] Config not found. Edit ${configFile.absolutePath}")
            null
        }
    }

    val config = if (rawConfig != null && rawConfig.token.isNotBlank() && rawConfig.token != "YOUR_BOT_TOKEN_HERE") {
        rawConfig
    } else {
        if (rawConfig != null) System.err.println("[TelegramBridge] Token not configured.")
        null
    }

    if (config != null) {

    val bridge        = app.getInstance(CommandBridgeProvider::class)
    val cmdInDir      = koupper.files().load(jobsDir, "commands/wizard").also { it.mkdirs() }
    val cortexLogFile = koupper.files().load(jobsDir, "logs/cortex/cortex-session.log")
    val logDir        = koupper.files().load(jobsDir, "logs/default").also { it.mkdirs() }
    val agentLog      = koupper.files().load(logDir, "telegram-bridge.log")
    val offsetFile    = koupper.files().load(home, ".koupper/telegram-offset.json")
    val running       = AtomicBoolean(true)

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = agentLog.appendText("[${ts()}] $msg\n")

    Runtime.getRuntime().addShutdownHook(Thread { running.set(false) })

    val allowedSet = config.allowedChatIds.toSet()
    log("TELEGRAM BRIDGE started")
    log("  Token   : ${config.token.take(10)}...")
    log("  Allowed : ${if (allowedSet.isEmpty()) "all chats" else allowedSet.toString()}")

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    val tgHttp = java.net.http.HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(10))
        .build()

    fun tgSend(chatId: Long, text: String) {
        if (text.isBlank()) return
        runCatching {
            val payload = mapOf(
                "chat_id" to chatId, "text" to text.take(4096), "parse_mode" to "HTML"
            ).toJson()
            val req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.telegram.org/bot${config.token}/sendMessage"))
                .header("Content-Type", "application/json")
                .timeout(java.time.Duration.ofSeconds(15))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload)).build()
            tgHttp.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
        }.onFailure { log("  tgSend error: ${it.message}") }
    }

    fun tgSendPhoto(chatId: Long, file: java.io.File, caption: String) {
        if (!file.exists()) { tgSend(chatId, "File not found: ${file.absolutePath}"); return }
        runCatching {
            val boundary = "TelegramBridge${System.currentTimeMillis()}"
            val nl = "\r\n"
            val baos = java.io.ByteArrayOutputStream()
            fun part(name: String, value: String) {
                baos.write("--$boundary$nl".toByteArray())
                baos.write("Content-Disposition: form-data; name=\"$name\"$nl$nl".toByteArray())
                baos.write(value.toByteArray())
                baos.write(nl.toByteArray())
            }
            part("chat_id", chatId.toString())
            if (caption.isNotBlank()) part("caption", caption)
            baos.write("--$boundary$nl".toByteArray())
            baos.write("Content-Disposition: form-data; name=\"photo\"; filename=\"${file.name}\"$nl".toByteArray())
            baos.write("Content-Type: application/octet-stream$nl$nl".toByteArray())
            baos.write(file.readBytes())
            baos.write("$nl--$boundary--$nl".toByteArray())

            val req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.telegram.org/bot${config.token}/sendPhoto"))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .timeout(java.time.Duration.ofSeconds(60))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray())).build()
            val resp = tgHttp.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
            log("  <- sent photo ${file.name} (${resp.statusCode()})")
        }.onFailure { log("  tgSendPhoto error: ${it.message}") }
    }

    // ── Startup notification ──────────────────────────────────────────────────

    if (allowedSet.isNotEmpty()) {
        allowedSet.forEach { chatId ->
            runCatching {
                tgSend(chatId, "Robot CORTEX Bridge is online. Send me a message!")
                log("  Sent startup notification to $chatId")
            }.onFailure { log("  Could not notify $chatId: ${it.message}") }
        }
    }

    // ── Response collector ────────────────────────────────────────────────────

    fun collectResponse(logPosBefore: Long, userText: String, maxWaitMs: Long = 90_000L): String {
        val deadline  = System.currentTimeMillis() + maxWaitMs
        var lastSize  = logPosBefore
        var idleMs    = 0L
        val collected = StringBuilder()
        var echoSeen     = false   // true once we see the ▶ echo of the user's command
        var hasRealContent = false // true once real LLM text appears after the echo

        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(500)
            val currentSize = cortexLogFile.length()

            if (currentSize > lastSize) {
                val newContent = runCatching {
                    val raf = java.io.RandomAccessFile(cortexLogFile, "r")
                    raf.seek(lastSize)
                    val buf = ByteArray((currentSize - lastSize).toInt())
                    raf.readFully(buf)
                    raf.close()
                    String(buf)
                }.getOrDefault("")

                lastSize = currentSize
                idleMs   = 0

                for (line in newContent.lines()) {
                    val stripped = line.replace(Regex("^\\[[0-9:]+\\]\\s?"), "").trim()
                    if (!echoSeen) {
                        // Wait until we see the ▶ echo of the user's command
                        if (stripped.startsWith("▶") && stripped.contains(userText.take(20))) {
                            echoSeen = true
                        }
                        continue
                    }
                    // After echo: collect everything
                    collected.appendLine(line)
                    if (stripped.isNotBlank() && !stripped.startsWith("→") &&
                        !stripped.startsWith("↳") && !stripped.contains("━")) {
                        hasRealContent = true
                    }
                }
            } else {
                idleMs += 500
                val idleThreshold = if (hasRealContent) 15_000L else 60_000L
                if (idleMs >= idleThreshold && hasRealContent) break
            }
        }

        return collected.toString()
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
    }

    // ── Main polling loop (direct HTTP) ───────────────────────────────────────

    var offset = runCatching { offsetFile.readText().fromJson<Long>() }.getOrDefault(0L)
    log("  Poll loop starting (offset=$offset)")

    while (running.get()) {
        val pollResult = runCatching {
            val req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.telegram.org/bot${config.token}/getUpdates?offset=$offset&timeout=25"))
                .timeout(java.time.Duration.ofSeconds(30))
                .GET().build()
            val resp = tgHttp.send(req, java.net.http.HttpResponse.BodyHandlers.ofString())
            resp.body().fromJson<Map<String, Any>>()
        }
        val respBody = pollResult.getOrNull()

        if (respBody == null || respBody["ok"] != true) {
            val err = pollResult.exceptionOrNull()?.message ?: respBody?.toString() ?: "null"
            log("  poll error: $err")
            Thread.sleep(5000)
            continue
        }

        @Suppress("UNCHECKED_CAST")
        val updates = respBody["result"] as? List<Map<String, Any>> ?: emptyList()

        for (update in updates) {
            val updateId = (update["update_id"] as? Number)?.toLong() ?: continue
            offset = updateId + 1
            runCatching { offsetFile.writeText(offset.toJson()) }

            @Suppress("UNCHECKED_CAST")
            val message = update["message"] as? Map<String, Any> ?: continue
            val text    = message["text"] as? String ?: continue
            @Suppress("UNCHECKED_CAST")
            val chat    = message["chat"] as? Map<String, Any> ?: continue
            val chatId  = (chat["id"] as? Number)?.toLong() ?: continue

            if (allowedSet.isNotEmpty() && chatId !in allowedSet) continue

            log("MSG [$chatId] $text")

            val procFile = koupper.files().load(jobsDir, "cortex/cortex-session.json.processing")
            if (!procFile.exists()) {
                tgSend(chatId, "CORTEX is not running. Start it first.")
                continue
            }

            val logPosBefore = if (cortexLogFile.exists()) cortexLogFile.length() else 0L
            val ts = System.currentTimeMillis()
            val tmpCmd = koupper.files().load(jobsDir, "commands/.tmp_$ts")
            tmpCmd.writeText(text)
            tmpCmd.renameTo(koupper.files().load(cmdInDir, "$ts.response"))
            log("  -> sent to CommandBridge")

            val response = collectResponse(logPosBefore, text)
            if (response.isBlank()) {
                tgSend(chatId, "CORTEX did not respond in time.")
            } else {
                // Strip log noise — keep only LLM response lines
                val cleaned = response
                    .replace(Regex("\\[[0-9;]*m"), "")  // ANSI codes
                    .lines()
                    .map { it.replace(Regex("^\\[[0-9]{2}:[0-9]{2}:[0-9]{2}\\]\\s?"), "") }
                    .filter { line ->
                        !line.startsWith("  →") &&   // -> tool calls
                        !line.startsWith("  ↳") &&   // down-right tool results
                        !line.startsWith("▶") &&     // ▶ user echo
                        !line.contains("━")           // ━ separators
                    }
                    .joinToString("\n")
                    .replace(Regex("\n{3,}"), "\n\n")
                    .trim()

                if (cleaned.isBlank()) {
                    val hasRateLimit = response.contains("429")
                    val hasTooBig    = response.contains("413")
                    val hasNoRoute   = response.contains("No route to host") || response.contains("Connection refused")
                    val hasError     = response.contains("[Error:")
                    val msg = when {
                        hasRateLimit -> "⚠ CORTEX: rate limit hit. Try again in a moment."
                        hasTooBig    -> "⚠ CORTEX: request too large. Try a shorter question."
                        hasNoRoute   -> "⚠ CORTEX: LLM server not reachable (192.168.1.9:1234 offline?)."
                        hasError     -> "⚠ CORTEX error: ${response.lines().firstOrNull { it.contains("[Error:") }?.trim() ?: "unknown"}"
                        else         -> "⚠ CORTEX responded but the message was empty."
                    }
                    log("  <- sent error response to $chatId: $msg")
                    tgSend(chatId, msg)
                } else {
                    cleaned.chunked(3800).forEachIndexed { i, chunk ->
                        if (i > 0) Thread.sleep(300)
                        tgSend(chatId, chunk)
                    }
                    log("  <- sent response (${cleaned.length} chars)")
                }
            }

            // Send any photos queued by CORTEX during this request
            val photoQueueDir = koupper.files().load(jobsDir, "telegram/photo_queue")
            photoQueueDir.mkdirs()
            photoQueueDir.listFiles { f -> f.extension == "json" }
                ?.sortedBy { it.name }
                ?.forEach { reqFile ->
                    runCatching {
                        val req     = reqFile.readText().fromJson<Map<String, Any>>()
                        val path    = req["path"]?.toString() ?: return@runCatching
                        val caption = req["caption"]?.toString() ?: ""
                        tgSendPhoto(chatId, java.io.koupper.files().load(path), caption)
                        reqFile.delete()
                    }.onFailure { log("  photo queue error: ${it.message}") }
                }
        }
    }

    log("TELEGRAM BRIDGE stopped")
    bridge.close()

    } // end if (config != null)
}
