// TelegramBridgeAgent.kts
// Role      : Telegram ↔ CORTEX bidirectional bridge
// Objective : Forward Telegram messages to CortexAgent via CommandBridge,
//             monitor the cortex-session log for responses, send them back.
//
// Config: ~/.koupper/telegram.json
//   { "token": "BOT_TOKEN", "allowedChatIds": [123456789] }
//
// Or via env vars:
//   KOUPPER_TELEGRAM_TOKEN     — Bot API token
//   KOUPPER_TELEGRAM_CHAT_IDS  — comma-separated allowed chat IDs (empty = all)
//
// Setup:
//   1. Create a bot at https://t.me/BotFather → get token
//   2. Start a chat with your bot, send /start
//   3. Get your chat ID: https://api.telegram.org/bot<TOKEN>/getUpdates
//   4. Fill ~/.koupper/telegram.json
//   5. Make sure CortexAgent is running (cortex-session job in PROCESSING)
//   6. Run: koupper run ~/.koupper/agents/TelegramBridgeAgent.kts

import com.koupper.container.app
import com.koupper.providers.commandbridge.CommandBridgeProvider
import com.koupper.providers.telegram.TelegramChannelProvider
import com.koupper.shared.annotations.Export
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

@Export
val setup: () -> Unit = {
    val home    = System.getProperty("user.home")!!
    val jobsDir = File(System.getenv("CORTEX_JOBS_DIR") ?: "$home/.koupper/jobs")
    val mapper  = jacksonObjectMapper()

    // ── Load config ───────────────────────────────────────────────────────────

    data class TelegramConfig(val token: String, val allowedChatIds: List<Long> = emptyList())

    val configFile = File(home, ".koupper/telegram.json")

    val config: TelegramConfig = when {
        System.getenv("KOUPPER_TELEGRAM_TOKEN") != null -> {
            val token = System.getenv("KOUPPER_TELEGRAM_TOKEN")!!
            val ids   = System.getenv("KOUPPER_TELEGRAM_CHAT_IDS")
                ?.split(",")?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
            TelegramConfig(token, ids)
        }
        configFile.exists() -> runCatching {
            mapper.readValue<TelegramConfig>(configFile)
        }.getOrElse {
            System.err.println("[TelegramBridge] Failed to parse telegram.json: ${it.message}")
            return@setup
        }
        else -> {
            // Create template config and exit
            configFile.writeText("""
{
  "token": "YOUR_BOT_TOKEN_HERE",
  "allowedChatIds": []
}
""".trimIndent())
            System.err.println("""
[TelegramBridge] Config not found. Created template at ${configFile.absolutePath}

Steps to set up:
  1. Create a bot at https://t.me/BotFather
  2. Copy the token into telegram.json
  3. Send /start to your bot, then run:
     curl https://api.telegram.org/bot<TOKEN>/getUpdates
     to get your chat ID and add it to allowedChatIds
  4. Run this agent again
""".trimIndent())
            return@setup
        }
    }

    if (config.token.isBlank() || config.token == "YOUR_BOT_TOKEN_HERE") {
        System.err.println("[TelegramBridge] Token not configured. Edit ${configFile.absolutePath}")
        return@setup
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    val telegram      = app.getInstance(TelegramChannelProvider::class)
    val bridge        = app.getInstance(CommandBridgeProvider::class)
    val cmdInDir      = File(jobsDir, "commands/wizard").also { it.mkdirs() }
    val cortexLogFile = File(jobsDir, "logs/cortex/cortex-session.log")
    val logDir        = File(jobsDir, "logs/default").also { it.mkdirs() }
    val agentLog      = File(logDir, "telegram-bridge.log")
    val running       = AtomicBoolean(true)

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = agentLog.appendText("[${ts()}] $msg\n")

    Runtime.getRuntime().addShutdownHook(Thread { running.set(false) })

    val allowedSet = config.allowedChatIds.toSet()
    log("◈ TELEGRAM BRIDGE started")
    log("  Token   : ${config.token.take(10)}...")
    log("  Allowed : ${if (allowedSet.isEmpty()) "all chats" else allowedSet.toString()}")
    log("  Waiting for messages...")

    // ── Response collector ────────────────────────────────────────────────────
    // Reads new lines added to cortex-session.log after a command is sent.
    // Waits up to [maxWaitMs] for the LLM to finish responding.

    fun collectResponse(logPosBefore: Long, maxWaitMs: Long = 30_000L): String {
        val deadline  = System.currentTimeMillis() + maxWaitMs
        var lastSize  = logPosBefore
        var idleMs    = 0L
        val collected = StringBuilder()

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

                collected.append(newContent)
                lastSize = currentSize
                idleMs   = 0
            } else {
                idleMs += 500
                // Stop if idle for 3s and we have content, or 5s if nothing yet
                val threshold = if (collected.isNotEmpty()) 3000L else 5000L
                if (idleMs >= threshold && collected.isNotEmpty()) break
            }
        }

        return collected.toString()
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
    }

    // ── Main polling loop ─────────────────────────────────────────────────────

    telegram.startPolling(
        token        = config.token,
        allowedChats = allowedSet,
        running      = { running.get() }
    ) { chatId, text ->

        log("▶ [$chatId] $text")

        // Check if CortexAgent is running
        val procFile = File(jobsDir, "cortex/cortex-session.json.processing")
        if (!procFile.exists()) {
            telegram.sendMessage(config.token, chatId,
                "⚠️ <b>CORTEX</b> is not running.\nStart it with: <code>koupper start</code>"
            )
            return@startPolling
        }

        // Note log size before sending command
        val logPosBefore = if (cortexLogFile.exists()) cortexLogFile.length() else 0L

        // Forward to CortexAgent via CommandBridge
        File(cmdInDir, "${System.currentTimeMillis()}.response").writeText(text)
        log("  → sent to CommandBridge")

        // Send typing indicator
        runCatching {
            val http = java.net.http.HttpClient.newHttpClient()
            val req  = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.telegram.org/bot${config.token}/sendChatAction"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                    """{"chat_id":$chatId,"action":"typing"}"""
                ))
                .build()
            http.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.ofString())
        }

        // Collect CORTEX response
        val response = collectResponse(logPosBefore)

        if (response.isBlank()) {
            telegram.sendMessage(config.token, chatId,
                "🤔 CORTEX is thinking... Check the dashboard for the full response."
            )
        } else {
            // Clean up log formatting for Telegram (strip ANSI, trim timestamps)
            val cleaned = response
                .replace(Regex("\\[[0-9;]*m"), "")        // ANSI codes
                .lines()
                .joinToString("\n") { it.trimStart() }
                .trim()

            telegram.sendLongMessage(config.token, chatId, cleaned)
            log("  ← sent response (${cleaned.length} chars)")
        }
    }

    log("◈ TELEGRAM BRIDGE stopped")
    bridge.close()
}
