// SysMonitorAgent.kts
// Description: CPU, RAM, disco, top procesos y estado de puertos Koupper
// y genera un reporte en ~/.koupper/jobs/logs/default/sysmonitor-<fecha>.log

import com.koupper.shared.annotations.Export
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Export
val setup: () -> Unit = {
    val home    = System.getProperty("user.home")!!
    val jobsDir = File(System.getenv("CORTEX_JOBS_DIR") ?: "$home/.koupper/jobs")
    val logDir  = File(jobsDir, "logs/default").also { it.mkdirs() }
    val today   = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val logFile = File(logDir, "sysmonitor-$today.log")

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n").also { println(msg) }
    fun run(cmd: String) = Runtime.getRuntime().exec(arrayOf("bash", "-c", cmd))
        .inputStream.bufferedReader().readText().trim()

    log("=== System Monitor Report ===")
    log("")

    log("── CPU ──")
    log(run("top -bn1 | grep 'Cpu(s)' | awk '{print \"user: \"\$2\" sys: \"\$4\" idle: \"\$8}'"))
    log("")

    log("── Memory ──")
    log(run("free -h | awk '/Mem/{print \"total: \"\$2\"  used: \"\$3\"  free: \"\$4}'"))
    log("")

    log("── Disk ──")
    log(run("df -h / | awk 'NR==2{print \"used: \"\$3\"/\"\$2\"  (\"\$5\")\"}'"))
    log("")

    log("── Top 5 Processes by CPU ──")
    run("ps aux --sort=-%cpu | awk 'NR>1 && NR<=6{printf \"%-20s %s%%\\n\", \$11, \$3}'")
        .lines().forEach { log(it) }
    log("")

    log("── Koupper Ports ──")
    listOf(8081 to "llama-server", 9998 to "octopus", 18082 to "mcp", 18083 to "web-ui").forEach { (port, name) ->
        val status = run("ss -tlnp sport = :$port | grep -c LISTEN").let {
            if (it.trim() == "1") "UP" else "DOWN"
        }
        log("  $name (:$port) — $status")
    }

    log("")
    log("=== Done ===")
    log("Report saved to: ${logFile.absolutePath}")
}
