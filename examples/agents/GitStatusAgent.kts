// GitStatusAgent.kts
// Role      : GitHub repository monitor
// Objective : Check recent commits and open PRs on configured repos, write a daily digest

import com.koupper.providers.mcp.LocalMCPClientProvider
import com.koupper.providers.mcp.MCPServerConfig
import com.koupper.shared.annotations.Export
import com.koupper.providers.files.fromJson
import com.koupper.providers.files.getInt
import com.koupper.providers.files.getObject
import com.koupper.providers.files.getString
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Export
val setup: () -> Unit = {
    val jobsDir = File(env("CORTEX_JOBS_DIR", "$home/.koupper/jobs"))
    val logDir  = File(jobsDir, "logs/default").also { it.mkdirs() }
    val today   = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val logFile = File(logDir, "gitstatus-$today.log")

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) { logFile.appendText("[${ts()}] $msg\n"); emit(msg) }

    // ── Repos to monitor ─────────────────────────────────────────────────────

    val configFile = File(home, ".koupper/gitstatus-repos.json")
    if (!configFile.exists()) {
        configFile.writeText("""[
  {"owner": "koupper-jvm", "repo": "koupper"},
  {"owner": "koupper-jvm", "repo": "koupper-cli"},
  {"owner": "koupper-jvm", "repo": "koupper-workspace"}
]""")
    }

    data class RepoConfig(val owner: String, val repo: String)

    val repos = runCatching {
        configFile.readText().fromJson<List<Map<String, String>>>().mapNotNull { m ->
            val owner = m["owner"] ?: return@mapNotNull null
            val repo  = m["repo"]  ?: return@mapNotNull null
            RepoConfig(owner, repo)
        }
    }.getOrDefault(emptyList())

    // ── Connect to GitHub MCP ────────────────────────────────────────────────

    val mcpConfigs = runCatching {
        File(home, ".koupper/mcp/servers.json").readText().fromJson<List<Map<String, Any>>>()
    }.getOrDefault(emptyList())

    val githubCfg = mcpConfigs.firstOrNull { it["name"] == "github" }

    if (repos.isEmpty()) {
        log("⚠ No repos configured in gitstatus-repos.json")
    } else if (githubCfg == null) {
        log("⚠ GitHub MCP server not found in mcp/servers.json")
    } else {
        log("◈ GitStatus — connecting to GitHub MCP")

        val serverConfig = MCPServerConfig(
            name      = "github",
            transport = "stdio",
            command   = githubCfg["command"]?.toString() ?: "npx",
            args      = (githubCfg["args"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            env       = (githubCfg["env"] as? Map<*, *>)?.entries
                ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
                ?.toMap() ?: emptyMap()
        )

        val client         = LocalMCPClientProvider()
        val connectResult  = runCatching { client.connect(serverConfig) }

        if (connectResult.isFailure) {
            log("⚠ Could not connect to GitHub MCP: ${connectResult.exceptionOrNull()?.message?.take(100)}")
        } else {
            val connected = connectResult.getOrThrow()
            log("  Connected — ${connected.tools.size} tools available")

            fun callTool(tool: String, args: Map<String, Any?>): String =
                runCatching { client.callTool(connected, tool, args).toString() }
                    .getOrElse { e -> """{"error":"${e.message?.take(100)}"}""" }

            // ── Report ───────────────────────────────────────────────────────

            log("")
            log("══ GIT STATUS — $today ══")
            log("")

            repos.forEach { (owner, repo) ->
                log("── $owner/$repo ──")

                // Recent commits
                val commitsRaw = callTool("list_commits", mapOf(
                    "owner" to owner, "repo" to repo, "sha" to "main", "per_page" to 5
                ))
                runCatching {
                    val arr = runCatching { commitsRaw.fromJson<List<Map<String, Any?>>>() }.getOrDefault(emptyList())
                    if (arr.isEmpty()) {
                        log("  commits : (none on main)")
                    } else {
                        arr.take(5).forEach { c ->
                            val commit = c.getObject("commit") ?: emptyMap()
                            val authorObj = commit.getObject("author") ?: emptyMap()
                            val msg    = commit.getString("message")?.lines()?.first()?.take(72) ?: ""
                            val author = authorObj.getString("name") ?: "?"
                            val date   = authorObj.getString("date")?.take(10) ?: ""
                            log("  [$date] $author — $msg")
                        }
                    }
                }.onFailure { log("  commits : (parse error)") }

                // Open PRs
                val prsRaw = callTool("list_pull_requests", mapOf(
                    "owner" to owner, "repo" to repo, "state" to "open"
                ))
                runCatching {
                    val arr = runCatching { prsRaw.fromJson<List<Map<String, Any?>>>() }.getOrDefault(emptyList())
                    log("  PRs open: ${arr.size}")
                    arr.take(5).forEach { pr ->
                        val title = pr.getString("title")?.take(72) ?: "?"
                        val user  = pr.getObject("user")?.getString("login") ?: "?"
                        log("    #${pr.getInt("number") ?: 0} [$user] $title")
                    }
                }.onFailure { /* silent */ }

                log("")
            }

            log("══ Done ══")
            runCatching { client.disconnect(connected) }
        }
    }
}
