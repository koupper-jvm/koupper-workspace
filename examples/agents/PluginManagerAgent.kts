// PluginManagerAgent.kts — Koupper Plugin Manager
// Installs plugins from GitHub or local paths into the koupper ecosystem.
//
// Supported types (auto-detected):
//   mcp    — npm-based MCP server → ~/.koupper/mcp/servers.json
//   agent  — .kts script(s)       → ~/.koupper/agents/
//   sp     — koupper SP + JAR     → ~/.koupper/libs/ + catalog/providers.json
//
// Job payload: { "source": "user/repo" | "https://..." | "/local/path" }
// Trigger:     koupper run PluginManagerAgent.kts  (after writing job to queue)

import com.koupper.shared.annotations.Export
import com.koupper.providers.files.fromJson
import com.koupper.providers.files.toJson
import com.koupper.providers.files.toJsonPretty
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Export
val setup: () -> Unit = {

    val jobsDir     = File(env("CORTEX_JOBS_DIR", "$home/.koupper/jobs"))
    val agentsDir   = File(home, ".koupper/agents").also  { it.mkdirs() }
    val cacheDir    = File(home, ".koupper/cache/plugins").also { it.mkdirs() }
    val catalogFile = File(home, ".koupper/catalog/providers.json")
    val serversFile = File(home, ".koupper/mcp/servers.json")
    val queueDir    = File(jobsDir, "plugin-manager").also { it.mkdirs() }
    val logDir      = File(jobsDir, "logs/plugin-manager").also { it.mkdirs() }
    // ── Pick next job ─────────────────────────────────────────────────────────

    val jobFile = queueDir.listFiles { f -> f.name.endsWith(".json") }
        ?.minByOrNull { it.lastModified() }

    if (jobFile == null) {
        emit("[!] No jobs in plugin-manager queue.")
    } else {

    val sessionId = jobFile.nameWithoutExtension
    val logFile   = File(logDir, "$sessionId.log")
    val procFile  = File(queueDir, "$sessionId.json.processing")

    jobFile.renameTo(procFile)
    logFile.writeText("")

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) { logFile.appendText("[${ts()}] $msg\n"); emit(msg) }
    fun fail(msg: String) { log("  ✗ $msg"); logFile.appendText("[FAILED]\n"); procFile.delete() }

    val job = runCatching { procFile.readText().fromJson<Map<String, Any>>() }.getOrNull()

    if (job == null) {
        fail("Invalid job payload")
    } else {

    val source = job["source"]?.toString()

    if (source == null) {
        fail("Missing 'source' in job")
    } else {

    // ── Normalize source URL ──────────────────────────────────────────────────

    val repoUrl = when {
        source.startsWith("http")                          -> source.trimEnd('/')
        source.contains("/") && !source.startsWith("/")   -> "https://github.com/$source"
        else                                               -> source  // local path
    }
    val repoName = repoUrl.trimEnd('/').substringAfterLast('/')
    val cloneDir = if (source.startsWith("/")) File(source) else File(cacheDir, repoName)

    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    log("  PLUGIN MANAGER")
    log("  Source  : $repoUrl")
    log("  Cache   : ${cloneDir.absolutePath}")
    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

    // ── Shell helper ──────────────────────────────────────────────────────────

    fun bash(cmd: String): Pair<Int, String> {
        val proc = ProcessBuilder("bash", "-c", cmd).redirectErrorStream(true).start()
        val out  = proc.inputStream.bufferedReader().readText().trim()
        return proc.waitFor() to out
    }

    // ── Clone / update ────────────────────────────────────────────────────────

    if (!source.startsWith("/")) {
        if (cloneDir.exists()) {
            log("  ↺ Updating clone...")
            val (code, out) = bash("git -C '${cloneDir.absolutePath}' pull --ff-only")
            if (code != 0) log("  ⚠ git pull: ${out.take(120)}")
        } else {
            log("  ↓ Cloning $repoUrl ...")
            val (code, out) = bash("git clone '$repoUrl' '${cloneDir.absolutePath}'")
            if (code != 0) { fail("Clone failed: ${out.take(200)}"); return@setup }
        }
    } else if (!cloneDir.exists()) {
        fail("Local path not found: $source"); return@setup
    }

    // ── Detect plugin type ────────────────────────────────────────────────────

    val manifestFile = File(cloneDir, "koupper-plugin.json")
    val packageFile  = File(cloneDir, "package.json")
    val gradleFile   = File(cloneDir, "build.gradle")
    val ktsFiles     = cloneDir.listFiles { f -> f.extension == "kts" && f.name != "build.gradle.kts" }
        ?: emptyArray()

    val declaredType = job["type"]?.toString()
    val type = declaredType?.takeIf { it != "auto" } ?: when {
        manifestFile.exists() ->
            runCatching { manifestFile.readText().fromJson<Map<String, Any>>()["type"]?.toString() }
                .getOrNull() ?: "sp"
        packageFile.exists() && (packageFile.readText().contains("mcp") || repoName.contains("mcp")) -> "mcp"
        ktsFiles.isNotEmpty() && !gradleFile.exists() -> "agent"
        gradleFile.exists() -> "sp"
        else -> "mcp"
    }

    log("  Type    : $type")
    log("")

    // ── Install ───────────────────────────────────────────────────────────────

    when (type) {

        // ── MCP server (npm) ──────────────────────────────────────────────────
        "mcp" -> {
            val pkgJson = runCatching { packageFile.readText().fromJson<Map<String, Any>>() }.getOrNull()
            val pkgName = pkgJson?.get("name")?.toString() ?: repoName

            if (packageFile.exists()) {
                log("  ◈ Installing npm dependencies...")
                val (code, out) = bash("cd '${cloneDir.absolutePath}' && npm install --silent 2>&1 | tail -5")
                if (code != 0) log("  ⚠ npm install issues: ${out.take(200)}")
            }

            // Build executable command
            val pkgMain  = pkgJson?.get("main")?.toString() ?: "index.js"
            val mainFile = File(cloneDir, pkgMain)
            val (execCmd, execArgs) = if (mainFile.exists()) {
                "node" to listOf(mainFile.absolutePath)
            } else {
                "npx" to listOf("-y", pkgName)
            }

            // Extra args / env from manifest
            val manifest   = runCatching { manifestFile.readText().fromJson<Map<String, Any>>() }.getOrNull() ?: emptyMap()
            val serverName = manifest["name"]?.toString()
                ?: repoName.replace(Regex("-?mcp-?|mcp-?"), "").trim('-').ifBlank { repoName }
            val extraArgs  = (manifest["args"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val envVars    = (manifest["env"] as? Map<*, *>)
                ?.entries?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
                ?.toMap() ?: emptyMap()

            // Upsert in servers.json
            val servers: MutableList<MutableMap<String, Any>> = runCatching {
                serversFile.readText().fromJson<MutableList<MutableMap<String, Any>>>()
            }.getOrDefault(mutableListOf())

            val entry: MutableMap<String, Any> = mutableMapOf(
                "name"      to serverName,
                "transport" to "stdio",
                "command"   to execCmd,
                "args"      to (execArgs + extraArgs)
            )
            if (envVars.isNotEmpty()) entry["env"] = envVars

            val idx = servers.indexOfFirst { it["name"] == serverName }
            if (idx >= 0) servers[idx] = entry else servers.add(entry)
            serversFile.writeText(servers.toJsonPretty())

            log("  ✓ MCP server '$serverName' registered in mcp/servers.json")
            log("  ↺ Restart CORTEX to load it (tools will appear as $serverName.*)")
        }

        // ── Koupper agent (.kts) ──────────────────────────────────────────────
        "agent" -> {
            var count = 0
            ktsFiles.forEach { f ->
                f.copyTo(File(agentsDir, f.name), overwrite = true)
                log("  ✓ ${f.name}")
                count++
            }
            // Copy skill definitions if present
            cloneDir.listFiles { f -> f.extension == "json" && f.name.contains("skill") }
                ?.forEach { f -> f.copyTo(File(agentsDir, f.name), overwrite = true) }

            log("  ✓ $count agent(s) installed → ~/.koupper/agents/")
            log("  Run with: koupper run ~/.koupper/agents/<AgentName>.kts")
        }

        // ── Koupper Service Provider (JAR + manifest) ─────────────────────────
        "sp" -> {
            if (!manifestFile.exists()) {
                fail("Missing koupper-plugin.json — SP plugins require a manifest.")
                return@setup
            }
            val manifest    = manifestFile.readText().fromJson<MutableMap<String, Any>>()
            val providerId  = manifest["id"]?.toString() ?: repoName

            // Build if Gradle project
            if (gradleFile.exists()) {
                log("  ◈ Building with Gradle (this may take a while)...")
                val (code, out) = bash("cd '${cloneDir.absolutePath}' && ./gradlew build -x test 2>&1 | tail -20")
                if (code != 0) { fail("Build failed:\n${out.take(400)}"); return@setup }

                val jar = cloneDir.walk()
                    .filter { it.extension == "jar" && "sources" !in it.name && "javadoc" !in it.name }
                    .maxByOrNull { it.length() }
                if (jar != null) {
                    val dest = File(home, ".koupper/libs/$providerId.jar")
                    jar.copyTo(dest, overwrite = true)
                    log("  ✓ JAR → ~/.koupper/libs/$providerId.jar")
                } else {
                    log("  ⚠ Build succeeded but no JAR found — skipping JAR copy")
                }
            } else {
                // Pre-built: look for a JAR in the repo
                val jar = cloneDir.walk().filter { it.extension == "jar" }.firstOrNull()
                if (jar != null) {
                    jar.copyTo(File(home, ".koupper/libs/$providerId.jar"), overwrite = true)
                    log("  ✓ JAR → ~/.koupper/libs/$providerId.jar")
                }
            }

            // Register in catalog/providers.json
            @Suppress("UNCHECKED_CAST")
            val catalog: MutableList<MutableMap<String, Any>> = runCatching {
                val raw = catalogFile.readText().fromJson<MutableMap<String, Any>>()
                raw["providers"] as? MutableList<MutableMap<String, Any>> ?: mutableListOf()
            }.getOrDefault(mutableListOf())

            val idx = catalog.indexOfFirst { it["id"] == providerId }
            if (idx >= 0) catalog[idx] = manifest else catalog.add(manifest)

            catalogFile.writeText(mapOf("version" to "1.0", "providers" to catalog).toJsonPretty())

            log("  ✓ SP '$providerId' registered in catalog/providers.json")
            manifest["env"]?.let { envList ->
                @Suppress("UNCHECKED_CAST")
                val required = (envList as? List<Map<String, Any>>)
                    ?.filter { it["required"] == true }
                    ?.mapNotNull { it["name"]?.toString() } ?: emptyList()
                if (required.isNotEmpty())
                    log("  ⚠ Required env vars to set in ~/.profile: ${required.joinToString(", ")}")
            }
        }

        else -> { fail("Unknown plugin type '$type'") }
    }

    log("")
    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    log("  Plugin installed successfully.")
    log("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    logFile.appendText("[DONE]\n")
    procFile.delete()
    }}}
    }
