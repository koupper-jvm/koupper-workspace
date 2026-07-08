// RssFeedAgent.kts
// Role      : RSS feed aggregator and summarizer
// Objective : Fetch configured RSS feeds, extract latest items, write a digest report
//
// Config: ~/.koupper/agents/rss-feeds.json
// Format: [{"name":"HackerNews","url":"https://news.ycombinator.com/rss"},...]
//
// Output: ~/.koupper/jobs/logs/default/rss-digest-<date>.log

import com.koupper.container.app
import com.koupper.providers.agent.AgentMessage
import com.koupper.providers.agent.InferenceEngine
import com.koupper.providers.agent.TokenListener
import com.koupper.providers.files.fromJson
import com.koupper.providers.rss.RSSReader
import com.koupper.shared.annotations.Export
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Export
val setup: () -> Unit = {
    val home    = System.getProperty("user.home")!!
    val jobsDir = koupper.files().load(env("CORTEX_JOBS_DIR", "$home/.koupper/jobs"))
    val logDir  = koupper.files().load(jobsDir, "logs/default").also { it.mkdirs() }
    val today   = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val logFile = koupper.files().load(logDir, "rss-digest-$today.log")

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n")

    // ── Load feed config ──────────────────────────────────────────────────────

    val configFile = koupper.files().load(home, ".koupper/agents/rss-feeds.json")
    val defaultFeeds = listOf(
        mapOf("name" to "Hacker News", "url" to "https://news.ycombinator.com/rss"),
        mapOf("name" to "The Verge",   "url" to "https://www.theverge.com/rss/index.xml")
    )

    val feeds: List<Map<String, String>> = runCatching {
        if (configFile.exists()) configFile.readText().fromJson<List<Map<String, String>>>()
        else defaultFeeds
    }.getOrDefault(defaultFeeds)

    // ── Fetch feeds ───────────────────────────────────────────────────────────

    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    log("  RSS FEED AGENT — Daily Digest")
    log("  Date: $today")
    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    log("")

    val reader = app.getInstance(RSSReader::class)
    val allItems = mutableListOf<String>()

    feeds.forEach { feed ->
        val name = feed["name"] ?: "Feed"
        val url  = feed["url"]
        if (url.isNullOrBlank()) return@forEach

        log("  Fetching: $name")

        val items = runCatching { reader.read(url) }.getOrElse { e ->
            log("  ⚠ Failed to fetch $name: ${e.message?.take(60)}")
            emptyList()
        }

        val latest = items.take(5)
        if (latest.isEmpty()) {
            log("  No items found.")
        } else {
            log("  ${latest.size} items fetched.")
            latest.forEach { item ->
                allItems.add("[$name] ${item.title} — ${item.link}")
                log("    • ${item.title.take(80)}")
            }
        }
        log("")
    }

    val hasItems = allItems.isNotEmpty()
    if (!hasItems) {
        log("No items fetched from any feed. Check network or feed URLs.")
    }

    // ── Summarize with LLM (optional) ─────────────────────────────────────────

    if (hasItems) {
    val engine = runCatching { app.getInstance(InferenceEngine::class) }.getOrNull()

    if (engine != null) {
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        log("  Generating AI summary...")
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        log("")

        val itemList = allItems.joinToString("\n") { "- $it" }
        val prompt   = "Summarize these news items in 5 bullet points, focusing on the most important tech trends:\n\n$itemList"
        val history  = listOf(
            AgentMessage("system", "You are a concise tech news summarizer. Output bullet points only, no preamble."),
            AgentMessage("user", prompt)
        )

        logFile.appendText("[${ts()}] ")
        runCatching {
            val listener = object : TokenListener {
                override fun onToken(token: String, agentId: String) {
                    logFile.appendText(token)
                }
            }
            runBlocking { engine.predict<String>(history, listener = listener) }
            logFile.appendText("\n")
        }.onFailure { e ->
            log("⚠ LLM summary failed: ${e.message?.take(60)}")
        }
    } else {
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        log("  Full item list (no LLM summary — set KOUPPER_LLM_MODEL_PATH)")
        log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        log("")
        allItems.forEach { log("  • $it") }
    }

    log("")
    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    log("  Digest saved to: $logFile")
    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    } // end if (hasItems)
}
