// CortexWebUiAgent.kts — IGLY CORTEX API backend (multi-tenant)
//
// Client isolation:
//   "default" client → ~/.koupper/jobs/  (backward compat, zero migration)
//   Additional clients → ~/.koupper/jobs/clients/<clientId>/
//
// Config:
//   CORTEX_JOBS_DIR  — base jobs directory (default: ~/.koupper/jobs)
//   CORTEX_WEB_PORT  — HTTP port (default: 18083)

import com.koupper.container.app
import com.koupper.providers.runtime.router.GrizzlyRuntimeRouterProvider
import com.koupper.providers.runtime.router.StreamResponse
import com.koupper.providers.files.fromJson
import com.koupper.providers.files.toJson
import com.koupper.shared.annotations.Export
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchKey
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

val baseDir  = File(System.getenv("CORTEX_JOBS_DIR") ?: "${System.getProperty("user.home")}/.koupper/jobs")
val uiPort   = System.getenv("CORTEX_WEB_PORT")?.toIntOrNull() ?: 18083
val excluded = setOf("logs", "commands", "clients")

// ── Client resolution ─────────────────────────────────────────────────────────

fun jobsDirFor(clientId: String): File =
    if (clientId == "default") baseDir
    else File(baseDir, "clients/$clientId").also { it.mkdirs() }

fun historyFileFor(clientId: String): File =
    if (clientId == "default") File(baseDir, ".history.jsonl")
    else File(baseDir, "clients/$clientId/.history.jsonl")

fun listClients(): List<String> {
    val ids = mutableListOf("default")
    File(baseDir, "clients").listFiles()
        ?.filter { it.isDirectory && !it.name.startsWith(".") }
        ?.forEach { ids += it.name }
    return ids
}

fun createClient(clientId: String): Boolean {
    if (clientId.isBlank() || clientId == "default") return false
    return File(baseDir, "clients/$clientId").mkdirs()
}

// ── History (per client) ──────────────────────────────────────────────────────

data class HistoryEntry(
    val id: String, val queue: String, val status: String,
    val time: String, val finishedAt: String, val result: String? = null
)

val histories = ConcurrentHashMap<String, CopyOnWriteArrayList<HistoryEntry>>()

fun historyFor(clientId: String) =
    histories.computeIfAbsent(clientId) { CopyOnWriteArrayList() }

fun loadHistory(clientId: String) {
    val file = historyFileFor(clientId)
    if (!file.exists()) return
    file.readLines().takeLast(500).forEach { line ->
        runCatching {
            val m = line.fromJson<Map<String, String>>() ?: return@forEach
            historyFor(clientId).add(HistoryEntry(
                id         = m["id"]         ?: return@forEach,
                queue      = m["queue"]      ?: "",
                status     = m["status"]     ?: "DONE",
                time       = m["time"]       ?: "",
                finishedAt = m["finishedAt"] ?: "",
                result     = m["result"]
            ))
        }
    }
}

fun appendHistory(clientId: String, entry: HistoryEntry) {
    val hist = historyFor(clientId)
    hist.add(entry)
    if (hist.size > 500) hist.removeAt(0)
    runCatching {
        val file = historyFileFor(clientId)
        file.parentFile?.mkdirs()
        file.appendText(mapOf(
            "id"         to entry.id,
            "queue"      to entry.queue,
            "status"     to entry.status,
            "time"       to entry.time,
            "finishedAt" to entry.finishedAt,
            "result"     to entry.result
        ).toJson() + "\n")
        val lines = file.readLines()
        if (lines.size > 500) file.writeText(lines.takeLast(500).joinToString("\n") + "\n")
    }
}

fun ts()     = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
fun isoNow() = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

// ── SSE broadcast ─────────────────────────────────────────────────────────────

val sseClients = CopyOnWriteArrayList<(String) -> Unit>()

fun broadcast(data: String) {
    val dead = mutableListOf<(String) -> Unit>()
    sseClients.forEach { cb -> try { cb(data) } catch (_: Exception) { dead.add(cb) } }
    sseClients.removeAll(dead.toSet())
}

// ── Observability (per client) ────────────────────────────────────────────────

fun parseDurationMs(logFile: File): Long? = runCatching {
    logFile.readLines().lastOrNull { "[DONE]" in it || "[FAILED]" in it }
        ?.let { Regex("(\\d+)ms").find(it)?.groupValues?.get(1)?.toLongOrNull() }
}.getOrNull()

fun computeObservability(clientId: String): Map<String, Any> {
    val jobsDir    = jobsDirFor(clientId)
    val hist       = historyFor(clientId)
    val now        = LocalDateTime.now()
    val isoFmt     = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    val oneHourAgo = now.minusHours(1)

    val recent = hist.filter { entry ->
        runCatching { LocalDateTime.parse(entry.finishedAt, isoFmt).isAfter(oneHourAgo) }.getOrDefault(false)
    }

    val total       = recent.size
    val done        = recent.count { it.status == "DONE" }
    val failed      = recent.count { it.status == "FAILED" || it.status == "DEAD" }
    val successRate = if (total > 0) (done * 100.0 / total) else 100.0
    val jobsPerMin  = total / 60.0

    val durations = recent.filter { it.status == "DONE" }.mapNotNull { entry ->
        val logFile = File(jobsDir, "logs/${entry.queue}/${entry.id}.log")
        if (logFile.exists()) parseDurationMs(logFile) else null
    }.sorted()

    val p50 = if (durations.isNotEmpty()) durations[durations.size / 2] else 0L
    val p95 = if (durations.isNotEmpty())
        durations[(durations.size * 0.95).toInt().coerceAtMost(durations.size - 1)] else 0L

    val buckets = (0 until 12).map { i ->
        val bucketEnd   = now.minusMinutes(((11 - i) * 5).toLong())
        val bucketStart = bucketEnd.minusMinutes(5)
        val bucketDone = recent.count { e ->
            runCatching {
                val t = LocalDateTime.parse(e.finishedAt, isoFmt)
                (t.isAfter(bucketStart) || t.isEqual(bucketStart)) && t.isBefore(bucketEnd)
            }.getOrDefault(false) && e.status == "DONE"
        }
        val bucketFailed = recent.count { e ->
            runCatching {
                val t = LocalDateTime.parse(e.finishedAt, isoFmt)
                (t.isAfter(bucketStart) || t.isEqual(bucketStart)) && t.isBefore(bucketEnd)
            }.getOrDefault(false) && (e.status == "FAILED" || e.status == "DEAD")
        }
        listOf(bucketDone, bucketFailed)
    }

    return mapOf(
        "jobsPerMin"     to String.format("%.2f", jobsPerMin),
        "successRate"    to String.format("%.1f", successRate),
        "p50Ms"          to p50,
        "p95Ms"          to p95,
        "totalLastHour"  to total,
        "doneLastHour"   to done,
        "failedLastHour" to failed,
        "sparkline"      to buckets
    )
}

// ── Swarm snapshot (per client) ───────────────────────────────────────────────

fun swarmSnapshot(clientId: String = "default"): Map<String, Any> {
    val jobsDir = jobsDirFor(clientId)
    val hist    = historyFor(clientId)
    val jobs    = mutableListOf<Map<String, Any?>>()
    var pending = 0; var processing = 0; var failed = 0

    jobsDir.listFiles()
        ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name !in excluded }
        ?.forEach { qDir ->
            qDir.listFiles()?.forEach { f ->
                when {
                    f.name.endsWith(".json.processing") -> {
                        processing++
                        jobs += mapOf("id" to f.name.removeSuffix(".json.processing"),
                            "queue" to qDir.name, "status" to "PROCESSING", "time" to ts())
                    }
                    f.name.endsWith(".json") -> {
                        pending++
                        jobs += mapOf("id" to f.nameWithoutExtension,
                            "queue" to qDir.name, "status" to "PENDING", "time" to ts())
                    }
                }
            }
            listOf(".done" to "DONE", ".failed" to "FAILED", ".dead" to "DEAD").forEach { (folder, status) ->
                File(qDir, folder).listFiles { f -> f.name.endsWith(".json") || f.name.endsWith(".result.json") }?.forEach { f ->
                    val id = f.name.removeSuffix(".json").removeSuffix(".result")
                    if (status == "FAILED") failed++
                    jobs += mapOf("id" to id, "queue" to qDir.name, "status" to status, "time" to "-")
                }
            }
        }

    val seenIds = jobs.map { it["id"] }.toMutableSet()
    hist.asReversed().take(100).forEach { e ->
        if (e.id !in seenIds) {
            jobs += mapOf("id" to e.id, "queue" to e.queue, "status" to e.status,
                "time" to e.time, "result" to e.result)
            seenIds.add(e.id)
        }
    }

    val agentsDir  = File(System.getProperty("user.home"), ".koupper/agents")
    val agentFiles = agentsDir.listFiles()
        ?.filter { f -> f.name.endsWith(".kts") && !f.name.startsWith(".") }
        ?.sortedBy { it.nameWithoutExtension }
        ?: emptyList()

    val agents = agentFiles.map { f ->
        val agentName = f.nameWithoutExtension
        val skillFile = File(agentsDir, "$agentName.skill.json")
        @Suppress("UNCHECKED_CAST")
        val skill: Map<String, Any> = if (skillFile.exists())
            runCatching { skillFile.readText().fromJson<Map<String, Any>>() ?: emptyMap() }.getOrDefault(emptyMap())
        else emptyMap()

        val headerLines = runCatching { f.readLines().take(8).joinToString("\n") }.getOrDefault("")
        val headerDesc  = Regex("//\\s*(?:Role|Objective|Description)\\s*:\\s*(.+)")
            .find(headerLines)?.groupValues?.get(1)?.trim() ?: ""

        val isRunning = jobs.any { j ->
            (j["queue"] == agentName || j["id"] == "$agentName-session") && j["status"] == "PROCESSING"
        } || File(jobsDir, "$agentName/$agentName-session.json.processing").exists()

        val agentHistory = hist.filter { it.queue == agentName }
        val totalRuns   = agentHistory.size
        val successRuns = agentHistory.count { it.status == "DONE" }
        val failedRuns  = agentHistory.count { it.status == "FAILED" || it.status == "DEAD" }
        val successRate = if (totalRuns > 0) successRuns * 100.0 / totalRuns else 100.0
        val lastRun     = agentHistory.lastOrNull()?.finishedAt ?: ""

        val desc = skill["description"]?.toString()?.takeIf { it.isNotBlank() } ?: headerDesc

        mapOf(
            "name"        to agentName,
            "description" to desc,
            "role"        to (skill["role"]?.toString() ?: ""),
            "tags"        to (skill["tags"]     ?: emptyList<String>()),
            "persistent"  to (skill["persistent"] ?: false),
            "providers"   to (skill["providers"] ?: emptyList<String>()),
            "triggers"    to (skill["triggers"]  ?: emptyList<String>()),
            "envVars"     to (skill["envVars"]   ?: emptyList<Map<String, Any>>()),
            "setup"       to (skill["setup"]     ?: emptyList<String>()),
            "requires"    to (skill["requires"]  ?: emptyList<String>()),
            "running"     to isRunning,
            "metrics"     to mapOf(
                "totalRuns"   to totalRuns,
                "successRuns" to successRuns,
                "failedRuns"  to failedRuns,
                "successRate" to String.format("%.1f", successRate),
                "lastRun"     to lastRun
            )
        )
    }

    val schedules = runCatching {
        val f = File(System.getProperty("user.home"), ".koupper/schedules.json")
        if (f.exists()) f.readText().fromJson<List<Map<String, Any>>>() ?: emptyList()
        else emptyList<Map<String, Any>>()
    }.getOrDefault(emptyList())

    val cortexActive = jobs.any { it["id"] == "cortex-session" && it["status"] == "PROCESSING" }

    return mapOf(
        "type"          to "snapshot",
        "clientId"      to clientId,
        "jobs"          to jobs,
        "metrics"       to mapOf("pending" to pending, "processing" to processing,
            "done" to hist.size, "failed" to failed),
        "observability" to computeObservability(clientId),
        "agents"        to agents,
        "schedules"     to schedules,
        "cortexActive"  to cortexActive,
        "time"          to ts()
    )
}

// ── Log search (across all clients) ──────────────────────────────────────────

fun findLog(jobId: String): File? {
    val defaultSearch = File(baseDir, "logs").walkTopDown().firstOrNull { it.name == "$jobId.log" }
    if (defaultSearch != null) return defaultSearch
    return File(baseDir, "clients").listFiles()?.flatMap { clientDir ->
        File(clientDir, "logs").walkTopDown().filter { it.name == "$jobId.log" }.toList()
    }?.firstOrNull()
}

// ── WatchService ──────────────────────────────────────────────────────────────

fun startWatcher() = Thread {
    val ws = FileSystems.getDefault().newWatchService()
    baseDir.mkdirs()

    // WatchKey → (clientId, dir)
    val keyMap     = mutableMapOf<WatchKey, Pair<String, File>>()
    val clientsDir = File(baseDir, "clients")

    fun reg(clientId: String, d: File) {
        if (d.exists() && !d.name.startsWith(".") && d.name !in excluded) {
            val key = d.toPath().register(ws, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
            keyMap[key] = clientId to d
        }
    }

    // Default client — watch baseDir + queue subdirs
    reg("default", baseDir)
    baseDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && it.name !in excluded }
        ?.forEach { reg("default", it) }

    // Additional clients
    if (clientsDir.exists()) {
        clientsDir.mkdirs()
        reg("_clients_root_", clientsDir)
        clientsDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { cDir ->
            reg(cDir.name, cDir)
            cDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") && it.name !in excluded }
                ?.forEach { reg(cDir.name, it) }
        }
    } else {
        clientsDir.mkdirs()
        reg("_clients_root_", clientsDir)
    }

    while (true) {
        val key = ws.poll(500, TimeUnit.MILLISECONDS) ?: continue
        val (clientId, dir) = keyMap[key] ?: run { key.reset(); continue }

        for (ev in key.pollEvents()) {
            @Suppress("UNCHECKED_CAST")
            val fname = (ev as? java.nio.file.WatchEvent<Path>)?.context()?.fileName?.toString() ?: continue
            val child = File(dir, fname)

            // New directory created — register it
            if (ev.kind() == ENTRY_CREATE && child.isDirectory) {
                when {
                    clientId == "_clients_root_" -> {
                        // New client directory
                        reg(fname, child)
                        child.listFiles()?.filter { it.isDirectory }?.forEach { reg(fname, it) }
                    }
                    !fname.startsWith(".") && fname !in excluded -> reg(clientId, child)
                }
            }

            // Job completed — .json.processing deleted
            if (ev.kind() == ENTRY_DELETE && fname.endsWith(".json.processing") && clientId != "_clients_root_") {
                val jobId      = fname.removeSuffix(".json.processing")
                val failedFile = File(dir, ".failed/$jobId.json")
                val deadFile   = File(dir, ".dead/$jobId.json")
                val finalStatus = when {
                    deadFile.exists()   -> "DEAD"
                    failedFile.exists() -> "FAILED"
                    else                -> "DONE"
                }
                if (finalStatus != "FAILED") {
                    val result = runCatching {
                        val resultFile = File(dir, ".done/$jobId.result.json")
                        if (resultFile.exists()) {
                            val raw = resultFile.readText().fromJson<Map<String, Any?>>() ?: emptyMap()
                            val r   = raw["result"]?.toString()?.take(500)
                            resultFile.delete()
                            r
                        } else null
                    }.getOrNull()
                    appendHistory(clientId, HistoryEntry(
                        id         = jobId,
                        queue      = dir.name,
                        status     = finalStatus,
                        time       = ts(),
                        finishedAt = isoNow(),
                        result     = result
                    ))
                }
            }
        }

        if (sseClients.isNotEmpty() && clientId != "_clients_root_") {
            broadcast(swarmSnapshot(clientId).toJson())
        }
        key.reset()
    }
}.also { it.isDaemon = true }.start()

// ── API routes ────────────────────────────────────────────────────────────────

@Export
val setup: () -> Unit = {
    // Load history for all existing clients at startup
    listClients().forEach { loadHistory(it) }
    startWatcher()

    val router = GrizzlyRuntimeRouterProvider()

    router.registerRouter {

        // ── Static / UI ──────────────────────────────────────────────────────

        get<String> {
            path { "/" }
            script { {
                val indexFile = File(System.getProperty("user.home") + "/.koupper/web/index.html")
                if (indexFile.exists()) indexFile.readText()
                else "<!DOCTYPE html><html><body><h2>Dashboard not deployed.</h2><p>Run: npm run build in koupper-dashboard/</p></body></html>"
            } }
        }

        // ── Client management ────────────────────────────────────────────────

        get<Unit> {
            path { "/api/clients" }
            script { {
                mapOf("clients" to listClients()).toJson()
            } }
        }

        post<String> {
            path { "/api/clients" }
            script { { body: String ->
                runCatching {
                    val payload  = body.fromJson<Map<String, String>>() ?: emptyMap()
                    val clientId = payload["id"]?.trim() ?: ""
                    if (clientId.isBlank()) {
                        mapOf("ok" to false, "error" to "id is required").toJson()
                    } else if (createClient(clientId)) {
                        mapOf("ok" to true, "clientId" to clientId).toJson()
                    } else {
                        mapOf("ok" to false, "error" to "client already exists or invalid id").toJson()
                    }
                }.getOrElse { e -> mapOf("ok" to false, "error" to e.message).toJson() }
            } }
        }

        // ── Per-client snapshot & history ────────────────────────────────────

        get<String> {
            path { "/api/client/{clientId}/swarm" }
            script { { clientId: String -> swarmSnapshot(clientId).toJson() } }
        }

        get<String> {
            path { "/api/client/{clientId}/history" }
            script { { clientId: String ->
                mapOf("clientId" to clientId, "entries" to historyFor(clientId).asReversed().take(200).map { e ->
                    mapOf("id" to e.id, "queue" to e.queue, "status" to e.status,
                        "time" to e.time, "finishedAt" to e.finishedAt, "result" to e.result)
                }).toJson()
            } }
        }

        // ── Legacy routes (default client — backward compat) ─────────────────

        get<Unit> {
            path { "/api/swarm" }
            script { { swarmSnapshot("default").toJson() } }
        }

        get<Unit> {
            path { "/api/history" }
            script { {
                mapOf("entries" to historyFor("default").asReversed().take(200).map { e ->
                    mapOf("id" to e.id, "queue" to e.queue, "status" to e.status,
                        "time" to e.time, "finishedAt" to e.finishedAt, "result" to e.result)
                }).toJson()
            } }
        }

        // ── Logs ─────────────────────────────────────────────────────────────

        get<String> {
            path { "/api/logs/{jobId}" }
            script { { jobId: String ->
                val logFile = findLog(jobId)
                if (logFile != null && logFile.exists())
                    mapOf("jobId" to jobId, "lines" to logFile.readLines().takeLast(300)).toJson()
                else
                    mapOf("jobId" to jobId, "lines" to emptyList<String>(), "error" to "log not found").toJson()
            } }
        }

        // ── Agent source ──────────────────────────────────────────────────────

        get<String> {
            path { "/api/agent/{name}" }
            script { { name: String ->
                val file = File(System.getProperty("user.home") + "/.koupper/agents/$name.kts")
                if (file.exists())
                    mapOf("name" to name, "content" to file.readText()).toJson()
                else
                    mapOf("name" to name, "error" to "agent not found").toJson()
            } }
        }

        // ── Run agent (client-aware) ──────────────────────────────────────────

        post<String> {
            path { "/api/run-agent" }
            script { { body: String ->
                runCatching {
                    val payload  = body.fromJson<Map<String, String>>() ?: emptyMap()
                    val name     = payload["name"]?.trim() ?: ""
                    val queue    = payload["queue"]?.trim()?.takeIf { it.isNotEmpty() } ?: "default"
                    val clientId = payload["clientId"]?.trim()?.takeIf { it.isNotEmpty() } ?: "default"
                    if (name.isBlank()) {
                        mapOf("ok" to false, "error" to "name is required").toJson()
                    } else {
                        val agentFile = File(System.getProperty("user.home") + "/.koupper/agents/$name.kts")
                        if (!agentFile.exists()) {
                            mapOf("ok" to false, "error" to "agent not found: $name").toJson()
                        } else {
                            val jobId = "$name-${System.currentTimeMillis()}"
                            val qDir  = File(jobsDirFor(clientId), queue).also { it.mkdirs() }
                            File(qDir, "$jobId.json").writeText(
                                mapOf("id" to jobId, "fileName" to name, "functionName" to "run",
                                    "scriptPath" to "agents/$name.kts", "sourceType" to "script",
                                    "clientId" to clientId, "args" to emptyMap<String, String>(),
                                    "submittedAt" to isoNow()).toJson()
                            )
                            mapOf("ok" to true, "jobId" to jobId, "queue" to queue, "clientId" to clientId).toJson()
                        }
                    }
                }.getOrElse { e -> mapOf("ok" to false, "error" to e.message).toJson() }
            } }
        }

        // ── Cortex message (client-aware) ─────────────────────────────────────

        post<String> {
            path { "/api/cortex" }
            script { { body: String ->
                runCatching {
                    val payload  = body.fromJson<Map<String, String>>() ?: emptyMap()
                    val msg      = payload["message"]?.trim() ?: ""
                    val clientId = payload["clientId"]?.trim()?.takeIf { it.isNotEmpty() } ?: "default"
                    if (msg.isNotBlank()) {
                        val cmdDir = File(jobsDirFor(clientId), "commands/wizard").also { it.mkdirs() }
                        File(cmdDir, "${System.currentTimeMillis()}.response").writeText(msg)
                        mapOf("ok" to true, "clientId" to clientId).toJson()
                    } else {
                        mapOf("ok" to false, "error" to "empty message").toJson()
                    }
                }.getOrElse { e -> mapOf("ok" to false, "error" to e.message).toJson() }
            } }
        }

        // ── SSE ───────────────────────────────────────────────────────────────

        get<Unit> {
            path { "/events" }
            script { {
                object : StreamResponse {
                    override fun onData(callback: (String) -> Unit) {
                        sseClients.add(callback)
                        // Send snapshots for all clients on connect
                        listClients().forEach { clientId ->
                            try { callback(swarmSnapshot(clientId).toJson()) } catch (_: Exception) {}
                        }
                    }
                    override fun onClose(callback: () -> Unit) {}
                }
            } }
        }

        // ── Voice ─────────────────────────────────────────────────────────────

        post<String> {
            path { "/api/voice" }
            script { { body: String ->
                val text    = body.trim().ifBlank { "CORTEX en línea." }
                val edgeBin = ProcessBuilder("which", "edge-tts").start()
                    .inputStream.bufferedReader().readLine()?.trim() ?: "edge-tts"

                val hasSpanish = text.any { it in "áéíóúñÁÉÍÓÚÑ¿¡" } ||
                    text.lowercase().split(Regex("\\W+"))
                        .count { it in setOf("de","la","el","que","es","un","una","con","para",
                            "por","pero","como","más","ya","si","me","te","su","se","no","sí",
                            "hola","está","hay","esto","eso","también","qué","cómo","muy") } >= 1
                val hasEnglish = text.lowercase().split(Regex("\\W+"))
                    .count { it in setOf("the","is","are","was","were","have","has","this",
                        "that","with","from","they","their","there","about","will","would",
                        "should","could","what","where","when","your","our","can","you") } >= 3

                val voiceEs = System.getenv("K_VOICE_EDGE")    ?: "es-MX-DaliaNeural"
                val voiceEn = System.getenv("K_VOICE_EDGE_EN") ?: "en-US-AriaNeural"
                val voice   = if (!hasSpanish && hasEnglish) voiceEn else voiceEs

                val webVoiceDir = File(System.getProperty("user.home"), ".koupper/web/voice").also { it.mkdirs() }
                val outFile     = File(webVoiceDir, "voice-${System.currentTimeMillis()}.mp3")

                val proc = ProcessBuilder(edgeBin, "--voice", voice, "--text", text, "--write-media", outFile.absolutePath)
                    .redirectErrorStream(true).start()
                proc.waitFor()

                webVoiceDir.listFiles { f -> f.name.endsWith(".mp3") || f.name.endsWith(".wav") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(10)
                    ?.forEach { it.delete() }

                if (outFile.exists() && outFile.length() > 0)
                    mapOf("url" to "/voice/${outFile.name}").toJson()
                else
                    mapOf("error" to "edge-tts failed").toJson()
            } }
        }

        get<Unit> {
            path { "/api/voice/status" }
            script { {
                val voice = System.getenv("K_VOICE_EDGE") ?: "es-MX-DaliaNeural"
                val check = ProcessBuilder("which", "edge-tts").start()
                val ready = check.waitFor() == 0
                mapOf("ready" to ready, "engine" to "edge-tts", "voice" to voice).toJson()
            } }
        }
    }

    router.start(uiPort)

    val webRoot = System.getProperty("user.home") + "/.koupper/web"
    val httpServer = System.getProperties()["koupper.runtime.server"]
        as? org.glassfish.grizzly.http.server.HttpServer
    if (httpServer != null && File(webRoot).exists()) {
        val assetsHandler = org.glassfish.grizzly.http.server.StaticHttpHandler("$webRoot/assets")
        assetsHandler.isFileCacheEnabled = false
        httpServer.serverConfiguration.addHttpHandler(assetsHandler, "/assets/")

        val voiceWebDir = "$webRoot/voice"
        File(voiceWebDir).mkdirs()
        val voiceHandler = org.glassfish.grizzly.http.server.StaticHttpHandler(voiceWebDir)
        voiceHandler.isFileCacheEnabled = false
        httpServer.serverConfiguration.addHttpHandler(voiceHandler, "/voice/")

        val rootStaticHandler = org.glassfish.grizzly.http.server.StaticHttpHandler(webRoot)
        rootStaticHandler.isFileCacheEnabled = false
        httpServer.serverConfiguration.addHttpHandler(rootStaticHandler, "/favicon.svg", "/icons.svg")
        println("  Serving dashboard from $webRoot")
    }

    println("◈ CORTEX API → http://localhost:$uiPort")
    println("  Press Ctrl+C to stop.")
    Thread.currentThread().join()
}
