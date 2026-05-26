/**
 * Agentic Control Plane Live Server
 * 
 * Exposes the Agentic Core via REST and SSE.
 * 
 * Run:
 *   koupper run examples/agents/agent_control_plane.kts
 */
import com.koupper.container.app
import com.koupper.shared.annotations.Export
import com.koupper.providers.runtime.router.RuntimeRouterProvider
import com.koupper.providers.agent.AgentOrchestrator

@Export
val serve: () -> Unit = {
    val router = app.getInstance(RuntimeRouterProvider::class)
    
    // El AgentServiceProvider ya registró las rutas /api/v1/...
    // pero necesitamos arrancar el motor HTTP físicamente.
    
    val port = 18080
    val host = "127.0.0.1"
    
    val server = router.start(port = port, host = host)
    
    println("🚀 Koupper Agentic Control Plane is live!")
    println("📍 Budget: GET http://$host:$port/api/v1/system/budget")
    println("📍 Run Agent: POST http://$host:$port/api/v1/agents/run")
    println("📍 Token Stream: GET http://$host:$port/api/v1/agents/stream/{taskId}")
    println("\n[Press Ctrl+C to shutdown]")

    try {
        while (!Thread.currentThread().isInterrupted) {
            Thread.sleep(1000)
        }
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    } finally {
        router.stop()
        println("🛑 Control Plane stopped.")
    }
}
