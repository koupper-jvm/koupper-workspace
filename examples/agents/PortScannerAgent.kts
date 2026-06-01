// PortScannerAgent.kts
// Description: Escanea puertos locales y alerta si hay servicios inesperados abiertos
// y genera un reporte de seguridad básico.
// Config: SCANNER_HOST (default: localhost), SCANNER_PORTS (default: rango común)

import com.koupper.shared.annotations.Export
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Export
val setup: () -> Unit = {
    val home    = System.getProperty("user.home")!!
    val jobsDir = File(System.getenv("CORTEX_JOBS_DIR") ?: "$home/.koupper/jobs")
    val logDir  = File(jobsDir, "logs/default").also { it.mkdirs() }
    val logFile = File(logDir, "port-scanner.log")
    val host    = System.getenv("SCANNER_HOST") ?: "localhost"

    val knownPorts = mapOf(
        22 to "SSH", 80 to "HTTP", 443 to "HTTPS", 3306 to "MySQL",
        5432 to "PostgreSQL", 6379 to "Redis", 8080 to "HTTP-alt",
        8081 to "llama-server", 9998 to "Octopus", 18082 to "MCP",
        18083 to "Koupper WebUI", 3000 to "Node/Dev", 5000 to "Flask/Dev",
        27017 to "MongoDB", 9200 to "Elasticsearch", 2181 to "Zookeeper"
    )

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n").also { println(msg) }

    fun isOpen(port: Int, timeoutMs: Int = 300): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), timeoutMs); true }
    }.getOrDefault(false)

    log("=== Port Scanner — $host ===")
    log("")

    val open   = mutableListOf<Pair<Int, String>>()
    val closed = mutableListOf<Int>()

    knownPorts.forEach { (port, service) ->
        if (isOpen(port)) {
            log("  [OPEN]   :$port  $service")
            open.add(port to service)
        } else {
            closed.add(port)
        }
    }

    log("")
    log("Summary: ${open.size} open, ${closed.size} closed")

    // Flag ports that shouldn't be exposed
    val suspicious = open.filter { (port, _) -> port !in listOf(8081, 9998, 18082, 18083) }
    if (suspicious.isNotEmpty()) {
        log("")
        log("⚠ Attention — unexpected open ports:")
        suspicious.forEach { (port, service) -> log("  :$port ($service)") }
    } else {
        log("✓ Only expected Koupper ports are open")
    }

    log("")
    log("=== Done ===")
}
