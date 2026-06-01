// CortexWebUiAgent.kts — IGLY CORTEX Web Dashboard (polished)
// Serves a real-time swarm monitor at http://localhost:<port>
// Uses Koupper's RuntimeRouterProvider (Grizzly HTTP) — no external deps.
//
// Config:
//   CORTEX_JOBS_DIR   — jobs directory (default: ~/.koupper/jobs)
//   CORTEX_WEB_PORT   — HTTP port (default: 18083)

import com.koupper.container.app
import com.koupper.providers.runtime.router.GrizzlyRuntimeRouterProvider
import com.koupper.providers.runtime.router.StreamResponse
import com.koupper.shared.annotations.Export
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.*
import java.nio.file.WatchKey
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

val home    = System.getProperty("user.home")!!
val jobsDir = File(System.getenv("CORTEX_JOBS_DIR") ?: "$home/.koupper/jobs")
val uiPort  = System.getenv("CORTEX_WEB_PORT")?.toIntOrNull() ?: 18083
val mapper  = jacksonObjectMapper()

val excluded = setOf("logs", "commands")

// ── Job history ───────────────────────────────────────────────────────────────

val historyFile = File(jobsDir, ".history.jsonl")

data class HistoryEntry(
    val id: String,
    val queue: String,
    val status: String,
    val time: String,
    val finishedAt: String,
    val result: String? = null   // null for old entries — backward compatible
)

val jobHistory = CopyOnWriteArrayList<HistoryEntry>()

fun loadHistory() {
    if (!historyFile.exists()) return
    historyFile.readLines()
        .takeLast(500)
        .forEach { line ->
            runCatching {
                val m = mapper.readValue<Map<String, String>>(line)
                jobHistory.add(HistoryEntry(
                    id         = m["id"] ?: return@forEach,
                    queue      = m["queue"] ?: "",
                    status     = m["status"] ?: "DONE",
                    time       = m["time"] ?: "",
                    finishedAt = m["finishedAt"] ?: "",
                    result     = m["result"]
                ))
            }
        }
}

fun appendHistory(entry: HistoryEntry) {
    jobHistory.add(entry)
    // Keep last 500 in memory
    if (jobHistory.size > 500) jobHistory.removeAt(0)
    // Append to disk
    runCatching {
        historyFile.appendText(
            mapper.writeValueAsString(mapOf(
                "id"         to entry.id,
                "queue"      to entry.queue,
                "status"     to entry.status,
                "time"       to entry.time,
                "finishedAt" to entry.finishedAt,
                "result"     to entry.result
            )) + "\n"
        )
        // Trim file to last 500 lines
        val lines = historyFile.readLines()
        if (lines.size > 500) historyFile.writeText(lines.takeLast(500).joinToString("\n") + "\n")
    }
}

fun ts() = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
fun isoNow() = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

// ── SSE broadcast ─────────────────────────────────────────────────────────────

val sseClients = CopyOnWriteArrayList<(String) -> Unit>()

fun broadcast(data: String) {
    val dead = mutableListOf<(String) -> Unit>()
    sseClients.forEach { cb -> try { cb(data) } catch (_: Exception) { dead.add(cb) } }
    sseClients.removeAll(dead.toSet())
}

// ── Observability ─────────────────────────────────────────────────────────────

fun parseDurationMs(logFile: File): Long? = runCatching {
    logFile.readLines().lastOrNull { "[DONE]" in it || "[FAILED]" in it }
        ?.let { Regex("(\\d+)ms").find(it)?.groupValues?.get(1)?.toLongOrNull() }
}.getOrNull()

fun computeObservability(): Map<String, Any> {
    val now        = LocalDateTime.now()
    val isoFmt     = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    val oneHourAgo = now.minusHours(1)

    // Filter history to last hour
    val recent = jobHistory.filter { entry ->
        runCatching {
            LocalDateTime.parse(entry.finishedAt, isoFmt).isAfter(oneHourAgo)
        }.getOrDefault(false)
    }

    val total   = recent.size
    val done    = recent.count { it.status == "DONE" }
    val failed  = recent.count { it.status == "FAILED" || it.status == "DEAD" }
    val successRate = if (total > 0) (done * 100.0 / total) else 100.0
    val jobsPerMin  = total / 60.0

    // Extract durations from log files
    val durations = recent
        .filter { it.status == "DONE" }
        .mapNotNull { entry ->
            val logFile = File(jobsDir, "logs/${entry.queue}/${entry.id}.log")
            if (logFile.exists()) parseDurationMs(logFile) else null
        }
        .sorted()

    val p50 = if (durations.isNotEmpty()) durations[durations.size / 2] else 0L
    val p95 = if (durations.isNotEmpty())
        durations[(durations.size * 0.95).toInt().coerceAtMost(durations.size - 1)] else 0L

    // Sparkline: 12 buckets of 5 minutes = 1 hour, [done, failed] per bucket
    val buckets = (0 until 12).map { i ->
        val bucketEnd   = now.minusMinutes(((11 - i) * 5).toLong())
        val bucketStart = bucketEnd.minusMinutes(5)
        val bucketDone   = recent.count { e ->
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

// ── Swarm snapshot ────────────────────────────────────────────────────────────

fun swarmSnapshot(): Map<String, Any> {
    val jobs = mutableListOf<Map<String, Any?>>()
    var pending = 0; var processing = 0; var failed = 0

    jobsDir.listFiles()
        ?.filter { it.isDirectory && !it.name.startsWith(".") && it.name !in excluded }
        ?.forEach { qDir ->
            // Active
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
            // Finished
            listOf(".done" to "DONE", ".failed" to "FAILED", ".dead" to "DEAD").forEach { (folder, status) ->
                File(qDir, folder).listFiles { f -> f.name.endsWith(".json") || f.name.endsWith(".result.json") }?.forEach { f ->
                    val id = f.name.removeSuffix(".json").removeSuffix(".result")
                    if (status == "FAILED") failed++
                    jobs += mapOf("id" to id, "queue" to qDir.name, "status" to status, "time" to "-")
                }
            }
        }

    // Unify with live history (keep unique)
    val seenIds = jobs.map { it["id"] }.toMutableSet()
    val done = jobHistory.size
    jobHistory.asReversed().take(100).forEach { e ->
        if (e.id !in seenIds) {
            jobs += mapOf("id" to e.id, "queue" to e.queue, "status" to e.status, "time" to e.time, "result" to e.result)
            seenIds.add(e.id)
        }
    }

    val agentsDir = File(home, ".koupper/agents")
    val agents = agentsDir.listFiles { f -> f.name.endsWith(".kts") }
        ?.map { f ->
            val header = runCatching { f.readLines().take(6).joinToString("\n") }.getOrDefault("")
            val desc   = Regex("//\\s*(?:Role|Objective|Description)\\s*:\\s*(.+)").find(header)
                ?.groupValues?.get(1)?.trim() ?: ""
            mapOf("name" to f.nameWithoutExtension, "description" to desc)
        } ?: emptyList()

    val schedules = runCatching {
        val f = File(home, ".koupper/schedules.json")
        if (f.exists()) mapper.readValue<List<Map<String, Any>>>(f) else emptyList()
    }.getOrDefault(emptyList())

    val cortexActive = jobs.any { it["id"] == "cortex-session" && it["status"] == "PROCESSING" }

    return mapOf(
        "type"          to "snapshot",
        "jobs"          to jobs,
        "metrics"       to mapOf("pending" to pending, "processing" to processing, "done" to done, "failed" to failed),
        "observability" to computeObservability(),
        "agents"        to agents,
        "schedules"     to schedules,
        "cortexActive"  to cortexActive,
        "time"          to ts()
    )
}

// ── WatchService ──────────────────────────────────────────────────────────────

fun startWatcher() = Thread {
    val ws = FileSystems.getDefault().newWatchService()
    jobsDir.mkdirs()

    // Map WatchKey → directory so we know where DELETE events come from
    val keyToDir = mutableMapOf<WatchKey, File>()

    fun reg(d: File) {
        if (d.exists()) {
            val key = d.toPath().register(ws, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY)
            keyToDir[key] = d
        }
    }

    reg(jobsDir)
    jobsDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { reg(it) }

    while (true) {
        val key = ws.poll(500, TimeUnit.MILLISECONDS) ?: continue
        val dir = keyToDir[key]

        for (ev in key.pollEvents()) {
            @Suppress("UNCHECKED_CAST")
            val fname = (ev as? java.nio.file.WatchEvent<Path>)?.context()?.fileName?.toString() ?: continue

            // Register new subdirectories as they appear
            if (ev.kind() == ENTRY_CREATE && dir == jobsDir) {
                val newDir = File(jobsDir, fname)
                if (newDir.isDirectory && !fname.startsWith(".") && fname !in excluded) {
                    reg(newDir)
                }
            }

            // Detect completed jobs: .json.processing deleted from a queue dir
            if (ev.kind() == ENTRY_DELETE && dir != null && dir != jobsDir) {
                if (fname.endsWith(".json.processing")) {
                    val jobId = fname.removeSuffix(".json.processing")
                    // Only record as DONE if it's not moving to .failed or .dead
                    // (those still exist on disk — we can check)
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
                                val raw = mapper.readValue<Map<String, Any?>>(resultFile.readText())
                                val r = raw["result"]?.toString()?.take(500)
                                resultFile.delete()
                                r
                            } else null
                        }.getOrNull()
                        appendHistory(HistoryEntry(
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
        }

        if (sseClients.isNotEmpty()) broadcast(mapper.writeValueAsString(swarmSnapshot()))
        key.reset()
    }
}.also { it.isDaemon = true }.start()

// ── HTML Dashboard ────────────────────────────────────────────────────────────

val HTML = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>IGLY CORTEX</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
:root{--bg:#0d1117;--panel:#161b22;--border:#30363d;--cyan:#79c0ff;--green:#56d364;--yellow:#e3b341;--red:#f85149;--purple:#d2a8ff;--text:#c9d1d9;--muted:#8b949e;--hover:#1f2937;--sel:#21262d}
body{background:var(--bg);color:var(--text);font-family:'Courier New',monospace;font-size:13px;height:100vh;display:flex;flex-direction:column;overflow:hidden}

/* Header */
header{display:flex;justify-content:space-between;align-items:center;padding:10px 20px;border-bottom:1px solid var(--border);background:var(--panel);flex-shrink:0}
header h1{color:var(--cyan);font-size:14px;letter-spacing:2px;font-weight:bold}
.hdr-right{display:flex;align-items:center;gap:16px}
#cortex-badge{font-size:11px;padding:2px 10px;border-radius:12px;background:#1a0a2e;color:var(--purple);border:1px solid #4a1a7e;display:none}
#cortex-badge.active{display:inline}
#conn{width:7px;height:7px;border-radius:50%;background:var(--green)}
#conn.off{background:var(--red)}
#clock{color:var(--muted);font-size:12px}

/* Metrics */
.metrics{display:flex;align-items:center;gap:24px;padding:8px 20px;border-bottom:1px solid var(--border);flex-shrink:0}
.metric{display:flex;flex-direction:column;align-items:center;min-width:50px}
.metric .lbl{color:var(--muted);font-size:10px;text-transform:uppercase;letter-spacing:1px}
.metric .val{font-size:20px;font-weight:bold;margin-top:1px;font-variant-numeric:tabular-nums}
.val.p{color:var(--yellow)}.val.pr{color:var(--purple)}.val.d{color:var(--green)}.val.f{color:var(--red)}.val.a{color:var(--cyan)}.val.s{color:#6ee7b7}
.metrics-sep{width:1px;height:32px;background:var(--border);margin:0 4px}
/* Observability bar */
.obs-bar{display:flex;align-items:center;gap:20px;padding:6px 20px;border-bottom:1px solid var(--border);background:#0a0d12;flex-shrink:0;overflow-x:auto}
.obs-item{display:flex;flex-direction:column;align-items:center;min-width:70px}
.obs-lbl{color:var(--muted);font-size:9px;text-transform:uppercase;letter-spacing:1px;white-space:nowrap}
.obs-val{font-size:16px;font-weight:bold;font-variant-numeric:tabular-nums;margin-top:1px}
.obs-sub{font-size:9px;color:var(--muted);margin-top:1px}
.obs-sep{width:1px;height:28px;background:#1a2030;margin:0 4px;flex-shrink:0}
.obs-spark{display:flex;align-items:flex-end;gap:2px;height:28px}
.obs-spark-col{display:flex;flex-direction:column;align-items:center;gap:1px;justify-content:flex-end}
.obs-spark-done{background:var(--green);min-height:2px;width:8px;border-radius:1px 1px 0 0;transition:height .3s}
.obs-spark-fail{background:var(--red);min-height:0;width:8px;border-radius:1px 1px 0 0}
.sr-good{color:var(--green)}.sr-warn{color:var(--yellow)}.sr-bad{color:var(--red)}

/* Main layout */
.main{display:flex;flex:1;overflow:hidden;position:relative}

/* Resize handle */
.resize-handle{width:4px;background:var(--border);cursor:col-resize;flex-shrink:0;transition:background .15s;position:relative;z-index:10}
.resize-handle:hover,.resize-handle.dragging{background:var(--cyan)}
.resize-handle::after{content:'';position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);width:12px;height:24px;display:flex;align-items:center;justify-content:center}

/* Jobs panel */
.jobs{min-width:180px;display:flex;flex-direction:column;overflow:hidden}
.panel-hdr{padding:8px 16px;color:var(--muted);font-size:10px;text-transform:uppercase;letter-spacing:1px;border-bottom:1px solid var(--border);background:var(--bg);display:flex;align-items:center;justify-content:space-between;flex-shrink:0}
.filters{display:flex;gap:4px}
.filter-btn{padding:2px 10px;border-radius:10px;border:1px solid var(--border);background:transparent;color:var(--muted);font-size:11px;cursor:pointer;font-family:inherit}
.filter-btn.active{border-color:var(--cyan);color:var(--cyan);background:#0a1929}
.jobs-table{flex:1;overflow-y:auto}
table{width:100%;border-collapse:collapse}
th{text-align:left;padding:7px 16px;color:var(--muted);font-size:10px;text-transform:uppercase;letter-spacing:1px;border-bottom:1px solid var(--border);background:var(--bg);position:sticky;top:0}
td{padding:7px 16px;border-bottom:1px solid #0d1117;cursor:pointer;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:200px}
tr:hover td{background:var(--hover)}
tr.sel td{background:var(--sel)}
.badge{padding:1px 7px;border-radius:3px;font-size:10px;font-weight:bold}
.badge.PROCESSING{background:#2d1b4e;color:var(--purple)}
.badge.PENDING{background:#2d2100;color:var(--yellow)}
.badge.DONE{background:#0d2b0d;color:var(--green)}
.badge.FAILED{background:#2b0d0d;color:var(--red)}
.badge.DEAD{background:#1a1a1a;color:#555}

/* Log panel */
.log{min-width:120px;display:flex;flex-direction:column;overflow:hidden}
#log-body{flex:1;overflow-y:auto;padding:10px 14px;font-size:12px;line-height:1.7;white-space:pre-wrap;word-break:break-all;background:var(--panel);color:var(--text)}
.l-w{color:var(--red)}.l-ok{color:var(--green)}.l-info{color:var(--purple)}.l-dim{color:var(--muted)}

/* Sidebar */
.sidebar{min-width:180px;display:flex;flex-direction:column;overflow:hidden}
.side-section{border-bottom:1px solid var(--border);display:flex;flex-direction:column}
.side-items{overflow-y:auto;flex:1}
.side-item{padding:8px 14px;border-bottom:1px solid #0d1117;font-size:12px;display:flex;align-items:center;gap:10px}
.side-item:hover{background:var(--hover)}
.agent-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0;position:relative}
.agent-dot::after{content:'';position:absolute;inset:-3px;border-radius:50%;opacity:.4}
.agent-dot.c0{background:#56d364;box-shadow:0 0 6px #56d364}.agent-dot.c0::after{background:#56d364}
.agent-dot.c1{background:#79c0ff;box-shadow:0 0 6px #79c0ff}.agent-dot.c1::after{background:#79c0ff}
.agent-dot.c2{background:#d2a8ff;box-shadow:0 0 6px #d2a8ff}.agent-dot.c2::after{background:#d2a8ff}
.agent-dot.c3{background:#e3b341;box-shadow:0 0 6px #e3b341}.agent-dot.c3::after{background:#e3b341}
.agent-dot.c4{background:#6ee7b7;box-shadow:0 0 6px #6ee7b7}.agent-dot.c4::after{background:#6ee7b7}
.agent-info .name{color:var(--text);font-size:12px}
.agent-info .desc{color:var(--muted);font-size:10px;margin-top:1px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;max-width:180px}
.side-item .badge-s{font-size:9px;padding:1px 5px;border-radius:8px}
.cron-badge{background:#0a2a1a;color:var(--green)}.rate-badge{background:#1a1a0a;color:var(--yellow)}.once-badge{background:#0a1a2a;color:var(--cyan)}

/* CORTEX chat — magic glow */
.cortex-section{flex:1;display:flex;flex-direction:column;overflow:hidden;position:relative}
.cortex-glow-wrap{flex:1;display:flex;flex-direction:column;overflow:hidden;margin:6px;border-radius:8px;position:relative}
.cortex-glow-wrap::before{content:'';position:absolute;inset:-1px;border-radius:9px;background:linear-gradient(135deg,#7c3aed,#2563eb,#06b6d4,#7c3aed);background-size:300% 300%;animation:gradientShift 4s ease infinite;z-index:0;opacity:.9}
.cortex-glow-wrap::after{content:'';position:absolute;inset:-4px;border-radius:12px;background:linear-gradient(135deg,#7c3aed,#2563eb,#06b6d4,#7c3aed);background-size:300% 300%;animation:gradientShift 4s ease infinite;filter:blur(8px);z-index:-1;opacity:.5}
@keyframes gradientShift{0%{background-position:0% 50%}50%{background-position:100% 50%}100%{background-position:0% 50%}}
.cortex-inner{position:relative;z-index:1;background:#0d1117;border-radius:7px;flex:1;display:flex;flex-direction:column;overflow:hidden}
#chat-log{flex:1;overflow-y:auto;padding:10px 14px;font-size:12px;line-height:1.6;white-space:pre-wrap;word-break:break-word;color:var(--text)}
.chat-input-row{display:flex;padding:8px;border-top:1px solid #1e1e3a;gap:6px;background:#0d0f1a}
#chat-input{flex:1;background:#0d1117;border:1px solid #2d2060;border-radius:4px;padding:6px 10px;color:var(--text);font-family:inherit;font-size:12px;outline:none;transition:border-color .2s}
#chat-input:focus{border-color:#7c3aed;box-shadow:0 0 8px rgba(124,58,237,.3)}
#chat-send{padding:6px 12px;background:linear-gradient(135deg,#3b1d8a,#1d4ed8);border:none;border-radius:4px;color:#e0d7ff;cursor:pointer;font-family:inherit;font-size:11px;transition:opacity .2s}
#chat-send:hover{opacity:.85}
.msg-u{color:var(--cyan);margin-bottom:4px}.msg-c{color:var(--text)}
.empty{padding:20px;color:#30363d;text-align:center;font-size:12px}

@keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}
.pulse{animation:pulse 1.5s infinite}
@keyframes dotPulse{0%,100%{opacity:1;transform:scale(1)}50%{opacity:.6;transform:scale(.85)}}
.agent-dot{animation:dotPulse 2.5s ease-in-out infinite}
.agent-dot.c1{animation-delay:.4s}.agent-dot.c2{animation-delay:.8s}.agent-dot.c3{animation-delay:1.2s}.agent-dot.c4{animation-delay:1.6s}
</style>
</head>
<body>
<header>
  <h1>◈ &nbsp;IGLY CORTEX — SWARM MONITOR</h1>
  <div class="hdr-right">
    <span id="cortex-badge">● CORTEX ONLINE</span>
    <span id="conn"></span>
    <span id="clock">--:--:--</span>
  </div>
</header>
<div class="metrics">
  <div class="metric"><span class="lbl">Pending</span><span class="val p" id="m-p">0</span></div>
  <div class="metric"><span class="lbl">Processing</span><span class="val pr" id="m-pr">0</span></div>
  <div class="metric"><span class="lbl">Done</span><span class="val d" id="m-d">0</span></div>
  <div class="metric"><span class="lbl">Failed</span><span class="val f" id="m-f">0</span></div>
  <div class="metrics-sep"></div>
  <div class="metric"><span class="lbl">Agents</span><span class="val a" id="m-a">0</span></div>
  <div class="metric"><span class="lbl">Schedules</span><span class="val s" id="m-s">0</span></div>
</div>

<div class="obs-bar" id="obs-bar">
  <div class="obs-item"><span class="obs-lbl">Jobs/min</span><span class="obs-val" id="o-jpm" style="color:var(--cyan)">—</span><span class="obs-sub">last 60m</span></div>
  <div class="obs-sep"></div>
  <div class="obs-item"><span class="obs-lbl">Success rate</span><span class="obs-val sr-good" id="o-sr">—</span><span class="obs-sub" id="o-sr-sub">—</span></div>
  <div class="obs-sep"></div>
  <div class="obs-item"><span class="obs-lbl">P50 latency</span><span class="obs-val" id="o-p50" style="color:var(--purple)">—</span><span class="obs-sub">median</span></div>
  <div class="obs-item"><span class="obs-lbl">P95 latency</span><span class="obs-val" id="o-p95" style="color:var(--yellow)">—</span><span class="obs-sub">95th pct</span></div>
  <div class="obs-sep"></div>
  <div class="obs-item" style="align-items:flex-start">
    <span class="obs-lbl" style="margin-bottom:4px">Activity (1h)</span>
    <div class="obs-spark" id="o-spark"></div>
  </div>
</div>

<div class="main" id="main">
  <!-- Jobs -->
  <div class="jobs" id="pane-jobs" style="width:35%">
    <div class="panel-hdr">
      <span>Jobs</span>
      <div class="filters">
        <button class="filter-btn active" onclick="setFilter('all',this)">All</button>
        <button class="filter-btn" onclick="setFilter('active',this)">Active</button>
        <button class="filter-btn" onclick="setFilter('done',this)">Done</button>
        <button class="filter-btn" onclick="setFilter('failed',this)">Failed</button>
      </div>
    </div>
    <div class="jobs-table">
      <table>
        <thead><tr><th>Job ID</th><th>Queue</th><th>Status</th><th>Time</th><th>Result</th></tr></thead>
        <tbody id="jobs-tbody"><tr><td colspan="4" class="empty">— no jobs —</td></tr></tbody>
      </table>
    </div>
  </div>

  <div class="resize-handle" id="rh1"></div>

  <!-- Log -->
  <div class="log" id="pane-log" style="width:30%">
    <div class="panel-hdr">
      <span id="log-title" style="color:var(--text)">Log</span>
      <span style="cursor:pointer;color:var(--muted)" onclick="refreshLog()" title="Refresh">↺</span>
    </div>
    <div id="log-body"><span class="empty" style="display:block;text-align:center;padding:30px">Select a job to view its log</span></div>
  </div>

  <div class="resize-handle" id="rh2"></div>

  <!-- Sidebar -->
  <div class="sidebar" id="pane-sidebar" style="flex:1">
    <!-- CORTEX chat -->
    <div class="cortex-section" style="min-height:180px;flex:1">
      <div class="panel-hdr">CORTEX</div>
      <div class="cortex-glow-wrap">
        <div class="cortex-inner">
          <div id="chat-log"><span class="empty" style="display:block;padding:20px;color:#4a3a6a">Ask CORTEX anything…</span></div>
          <div class="chat-input-row">
            <input id="chat-input" placeholder="Message CORTEX..." onkeydown="if(event.key==='Enter')sendChat()">
            <button id="chat-send" onclick="sendChat()">⚡ Send</button>
          </div>
        </div>
      </div>
    </div>

    <!-- Available Agents -->
    <div class="side-section" style="max-height:35%">
      <div class="panel-hdr">Available Agents (<span id="a-count">0</span>)</div>
      <div class="side-items" id="agents-list"></div>
    </div>

    <!-- Schedules -->
    <div class="side-section" style="max-height:28%">
      <div class="panel-hdr">Schedules (<span id="s-count">0</span>)</div>
      <div class="side-items" id="sched-list"></div>
    </div>
  </div>
</div>

<script>
let selectedJob = null;
let jobFilter   = 'all';
let allJobs     = [];

// ── SSE ───────────────────────────────────────────────────────────────────────
const es = new EventSource('/events');
es.onopen  = () => document.getElementById('conn').className = '';
es.onerror = () => document.getElementById('conn').className = 'off';
es.onmessage = e => { const d = JSON.parse(e.data); if (d.type === 'snapshot') updateUI(d); };

// ── Clock ─────────────────────────────────────────────────────────────────────
setInterval(() => {
  const n = new Date();
  document.getElementById('clock').textContent =
    String(n.getHours()).padStart(2,'0')+':'+String(n.getMinutes()).padStart(2,'0')+':'+String(n.getSeconds()).padStart(2,'0');
}, 1000);

// ── Resize handles ────────────────────────────────────────────────────────────
function makeResizable(handleId, leftPaneId, rightPaneId) {
  const handle    = document.getElementById(handleId);
  const leftPane  = document.getElementById(leftPaneId);
  const rightPane = document.getElementById(rightPaneId);
  const main      = document.getElementById('main');

  handle.addEventListener('mousedown', e => {
    e.preventDefault();
    handle.classList.add('dragging');
    const startX    = e.clientX;
    const startLeft = leftPane.getBoundingClientRect().width;
    const startRight= rightPane.getBoundingClientRect().width;
    const total     = startLeft + startRight;

    function onMove(e) {
      const dx      = e.clientX - startX;
      const newLeft = Math.max(180, Math.min(total - 180, startLeft + dx));
      leftPane.style.width  = newLeft + 'px';
      leftPane.style.flex   = 'none';
      rightPane.style.width = (total - newLeft) + 'px';
      rightPane.style.flex  = 'none';
    }
    function onUp() {
      handle.classList.remove('dragging');
      document.removeEventListener('mousemove', onMove);
      document.removeEventListener('mouseup', onUp);
    }
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  });
}

makeResizable('rh1', 'pane-jobs', 'pane-log');
makeResizable('rh2', 'pane-log',  'pane-sidebar');

// ── UI update ─────────────────────────────────────────────────────────────────
function updateUI(d) {
  document.getElementById('m-p').textContent  = d.metrics.pending;
  document.getElementById('m-pr').textContent = d.metrics.processing;
  document.getElementById('m-d').textContent  = d.metrics.done;
  document.getElementById('m-f').textContent  = d.metrics.failed;
  document.getElementById('m-a').textContent  = d.agents.length;
  document.getElementById('m-s').textContent  = d.schedules.length;
  document.getElementById('a-count').textContent = d.agents.length;
  document.getElementById('s-count').textContent = d.schedules.length;
  document.getElementById('cortex-badge').className = d.cortexActive ? 'active' : '';
  if (d.observability) updateObservability(d.observability);
  allJobs = d.jobs;
  renderJobs();
  renderAgents(d.agents);
  renderSchedules(d.schedules);
}

function fmtMs(ms) {
  if (!ms || ms === 0) return '—';
  if (ms < 1000) return ms + 'ms';
  if (ms < 60000) return (ms / 1000).toFixed(1) + 's';
  return Math.round(ms / 60000) + 'm';
}

function updateObservability(o) {
  document.getElementById('o-jpm').textContent = o.jobsPerMin || '0.00';

  const sr    = parseFloat(o.successRate) || 100;
  const srEl  = document.getElementById('o-sr');
  const srSub = document.getElementById('o-sr-sub');
  srEl.textContent = sr.toFixed(1) + '%';
  srEl.className   = 'obs-val ' + (sr >= 95 ? 'sr-good' : sr >= 80 ? 'sr-warn' : 'sr-bad');
  srSub.textContent = (o.doneLastHour || 0) + 'd / ' + (o.failedLastHour || 0) + 'f';

  document.getElementById('o-p50').textContent = fmtMs(o.p50Ms);
  document.getElementById('o-p95').textContent = fmtMs(o.p95Ms);

  // Sparkline
  const spark   = document.getElementById('o-spark');
  const buckets = o.sparkline || [];
  if (!buckets.length) return;
  const maxVal  = Math.max(1, ...buckets.map(b => (b[0] || 0) + (b[1] || 0)));
  spark.innerHTML = buckets.map(b => {
    const done   = b[0] || 0;
    const failed = b[1] || 0;
    const total  = done + failed;
    const doneH  = Math.round((done / maxVal) * 24);
    const failH  = Math.round((failed / maxVal) * 24);
    return '<div class="obs-spark-col" title="' + done + ' done, ' + failed + ' failed">' +
      (failH > 0 ? '<div class="obs-spark-fail" style="height:' + failH + 'px"></div>' : '') +
      '<div class="obs-spark-done" style="height:' + Math.max(2, doneH) + 'px;' + (total === 0 ? 'opacity:.2' : '') + '"></div>' +
      '</div>';
  }).join('');
}

function setFilter(f, btn) {
  jobFilter = f;
  document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  renderJobs();
}

function renderJobs() {
  const jobs = allJobs.filter(j =>
    jobFilter === 'all'    ? true :
    jobFilter === 'active' ? (j.status === 'PROCESSING' || j.status === 'PENDING') :
    jobFilter === 'done'   ? j.status === 'DONE' :
    jobFilter === 'failed' ? (j.status === 'FAILED' || j.status === 'DEAD') : true
  );
  const tbody = document.getElementById('jobs-tbody');
  if (!jobs.length) { tbody.innerHTML = '<tr><td colspan="5" class="empty">— no jobs —</td></tr>'; return; }
  tbody.innerHTML = jobs.map(j => {
    const key   = j.queue + ':' + j.id;
    const sel   = key === selectedJob ? 'sel' : '';
    const pulse = j.status === 'PROCESSING' ? ' pulse' : '';
    const res   = j.result ? ('<span title="' + j.result.replace(/"/g,'&quot;') + '" style="color:var(--green);cursor:pointer">' + j.result.substring(0,60) + (j.result.length>60?'…':'') + '</span>') : '<span style="color:var(--muted)">—</span>';
    return '<tr class="' + sel + '" onclick="selectJob(\'' + j.queue + '\',\'' + j.id.replace(/'/g,"\\'") + '\')">' +
      '<td title="' + j.id + '">' + j.id + '</td>' +
      '<td style="color:var(--muted)">' + j.queue + '</td>' +
      '<td><span class="badge' + pulse + ' ' + j.status + '">' + j.status + '</span></td>' +
      '<td style="color:var(--muted)">' + (j.time||'') + '</td>' +
      '<td style="max-width:220px;overflow:hidden;white-space:nowrap">' + res + '</td></tr>';
  }).join('');
}

const agentColors = ['c0','c1','c2','c3','c4'];
function renderAgents(agents) {
  const el = document.getElementById('agents-list');
  if (!agents.length) { el.innerHTML = '<div class="empty">No agents installed</div>'; return; }
  el.innerHTML = agents.map((a, i) => {
    const col = agentColors[i % agentColors.length];
    return '<div class="side-item" style="cursor:pointer" onclick="viewAgent(\'' + a.name + '\')" title="View script">' +
      '<div class="agent-dot ' + col + '"></div>' +
      '<div class="agent-info">' +
        '<div class="name">' + a.name + '</div>' +
        (a.description ? '<div class="desc">' + a.description + '</div>' : '') +
      '</div></div>';
  }).join('');
}

function viewAgent(name) {
  document.getElementById('log-title').textContent = name + '.kts';
  fetch('/api/agent/' + name)
    .then(r => r.json())
    .then(d => {
      if (d.error) {
        document.getElementById('log-body').innerHTML = '<span class="l-w">' + d.error + '</span>';
        return;
      }
      const lines = d.content.split('\n');
      document.getElementById('log-body').innerHTML = lines.map(l => {
        const cls = l.trimStart().startsWith('//') ? 'l-dim' :
                    l.includes('@Export') || l.includes('@JobsListener') ? 'l-info' :
                    l.includes('import ') ? 'l-ok' :
                    l.includes('fun ') || l.includes('val ') || l.includes('var ') ? 'l-w' : '';
        return '<span class="' + cls + '">' + l.replace(/</g,'&lt;') + '</span>';
      }).join('\n');
      document.getElementById('log-body').scrollTop = 0;
    }).catch(() => {});
}

function renderSchedules(scheds) {
  const el = document.getElementById('sched-list');
  if (!scheds.length) { el.innerHTML = '<div class="empty" style="font-size:11px">No schedules<br><span style="color:#30363d">koupper schedule add</span></div>'; return; }
  el.innerHTML = scheds.map(s => {
    const type = s.type || 'cron';
    const info = type === 'cron' ? s.cron : type === 'rate' ? 'every ' + Math.round((s.rateMs||0)/1000) + 's' : s.runAt || '';
    const cls  = type === 'cron' ? 'cron-badge' : type === 'rate' ? 'rate-badge' : 'once-badge';
    const dot  = s.enabled === false ? '○' : '●';
    const color= s.enabled === false ? 'var(--muted)' : 'var(--green)';
    return '<div class="side-item" style="flex-direction:column;align-items:flex-start;gap:3px">' +
      '<div style="color:' + color + ';font-size:12px">' + dot + ' ' + s.agent + '</div>' +
      '<div style="color:var(--muted);font-size:10px"><span class="badge-s ' + cls + '">' + type + '</span> ' + info + '</div></div>';
  }).join('');
}

// ── Log ───────────────────────────────────────────────────────────────────────
function selectJob(queue, id) {
  selectedJob = queue + ':' + id;
  document.getElementById('log-title').textContent = id;
  renderJobs();
  refreshLog();
}

function refreshLog() {
  if (!selectedJob) return;
  const [queue, id] = selectedJob.split(':');
  fetch('/api/logs/' + id + '?queue=' + queue)
    .then(r => r.json())
    .then(d => {
      if (d.error || !d.lines.length) {
        document.getElementById('log-body').innerHTML = '<span class="empty" style="display:block;padding:20px">' + (d.error||'No log yet') + '</span>';
        return;
      }
      document.getElementById('log-body').innerHTML = d.lines.map(l => {
        const cls = l.includes('ERROR')||l.includes('FAIL')||l.includes('[!]')||l.includes('[FAILED]')||l.includes('[TIMEOUT]') ? 'l-w' :
                    l.includes('[DONE]')||l.includes('[✓]')||l.includes('✓')||l.includes('[OK]') ? 'l-ok' :
                    l.includes('▶')||l.includes('[?]')||l.includes('CORTEX')||l.includes('[WORKER]') ? 'l-info' :
                    l.startsWith('[DEBUG]') ? 'l-dim' : '';
        return '<span class="' + cls + '">' + l.replace(/</g,'&lt;') + '</span>';
      }).join('\n');
      const el = document.getElementById('log-body');
      el.scrollTop = el.scrollHeight;
    }).catch(() => {});
}
setInterval(() => { if (selectedJob) refreshLog(); }, 2000);

// ── CORTEX chat ───────────────────────────────────────────────────────────────
function sendChat() {
  const input = document.getElementById('chat-input');
  const msg   = input.value.trim();
  if (!msg) return;
  input.value = '';
  const log = document.getElementById('chat-log');
  log.innerHTML += '<div class="msg-u">▶ ' + msg.replace(/</g,'&lt;') + '</div>';
  log.scrollTop = log.scrollHeight;
  fetch('/api/cortex', {
    method: 'POST',
    headers: {'Content-Type':'application/json'},
    body: JSON.stringify({message: msg})
  }).then(r => r.json()).then(d => {
    if (d.ok) {
      log.innerHTML += '<div style="color:#4a3a6a;font-size:10px;margin-bottom:4px">→ sent. watching log…</div>';
      selectJob('cortex-session');
    }
    log.scrollTop = log.scrollHeight;
  }).catch(() => {});
}

// ── Keyboard navigation ───────────────────────────────────────────────────────
document.addEventListener('keydown', function(e) {
  // Don't intercept if user is typing in chat input
  if (document.activeElement === document.getElementById('chat-input')) return;

  const jobs = allJobs.filter(j =>
    jobFilter === 'all'    ? true :
    jobFilter === 'active' ? (j.status === 'PROCESSING' || j.status === 'PENDING') :
    jobFilter === 'done'   ? j.status === 'DONE' :
    jobFilter === 'failed' ? (j.status === 'FAILED' || j.status === 'DEAD') : true
  );
  if (!jobs.length) return;

  const idx = jobs.findIndex(j => (j.queue + ':' + j.id) === selectedJob);

  if (e.key === 'ArrowDown') {
    e.preventDefault();
    const next = idx < jobs.length - 1 ? idx + 1 : 0;
    selectJob(jobs[next].queue, jobs[next].id);
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    const prev = idx > 0 ? idx - 1 : jobs.length - 1;
    selectJob(jobs[prev].queue, jobs[prev].id);
  }
});
</script>
</body>
</html>"""

// ── Main ──────────────────────────────────────────────────────────────────────

@Export
val setup: () -> Unit = {
    loadHistory()
    startWatcher()

    val router = GrizzlyRuntimeRouterProvider()

    router.registerRouter {
        get<Unit> {
            path { "/" }
            script { { HTML } }
        }

        get<Unit> {
            path { "/api/swarm" }
            script { { mapper.writeValueAsString(swarmSnapshot()) } }
        }

        get<String> {
            path { "/api/logs/{jobId}" }
            script { {
                jobId: String ->
                val logFile = File(jobsDir, "logs").walkTopDown()
                    .firstOrNull { it.name == "$jobId.log" }
                if (logFile != null && logFile.exists())
                    mapper.writeValueAsString(mapOf("jobId" to jobId, "lines" to logFile.readLines().takeLast(300)))
                else
                    mapper.writeValueAsString(mapOf("jobId" to jobId, "lines" to emptyList<String>(), "error" to "log not found"))
            } }
        }

        get<String> {
            path { "/api/agent/{name}" }
            script { {
                name: String ->
                val file = File(home, ".koupper/agents/$name.kts")
                if (file.exists())
                    mapper.writeValueAsString(mapOf("name" to name, "content" to file.readText()))
                else
                    mapper.writeValueAsString(mapOf("name" to name, "error" to "agent not found"))
            } }
        }

        get<Unit> {
            path { "/api/history" }
            script { {
                mapper.writeValueAsString(mapOf("entries" to jobHistory.asReversed().take(200).map { e ->
                    mapOf("id" to e.id, "queue" to e.queue, "status" to e.status, "time" to e.time, "finishedAt" to e.finishedAt, "result" to e.result)
                }))
            } }
        }

        post<String> {
            path { "/api/cortex" }
            script { {
                body: String ->
                runCatching {
                    val payload = mapper.readValue<Map<String, String>>(body)
                    val msg     = payload["message"]?.trim() ?: ""
                    if (msg.isNotBlank()) {
                        val cmdDir = File(jobsDir, "commands/wizard").also { it.mkdirs() }
                        File(cmdDir, "${System.currentTimeMillis()}.response").writeText(msg)
                        mapper.writeValueAsString(mapOf("ok" to true))
                    } else {
                        mapper.writeValueAsString(mapOf("ok" to false, "error" to "empty message"))
                    }
                }.getOrElse { e -> mapper.writeValueAsString(mapOf("ok" to false, "error" to e.message)) }
            } }
        }

        get<Unit> {
            path { "/events" }
            script { {
                object : StreamResponse {
                    override fun onData(callback: (String) -> Unit) {
                        sseClients.add(callback)
                        try { callback(mapper.writeValueAsString(swarmSnapshot())) } catch (_: Exception) {}
                    }
                    override fun onClose(callback: () -> Unit) {}
                }
            } }
        }
    }

    router.start(uiPort)
    println("◈ CORTEX Web UI → http://localhost:$uiPort")
    println("  Press Ctrl+C to stop.")
    Thread.currentThread().join()
}
