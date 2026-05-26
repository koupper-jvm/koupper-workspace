/**
 * FINAL 100% CONFIRMATION SCRIPT
 */
import com.koupper.container.app
import com.koupper.shared.annotations.Export
import com.koupper.providers.runtime.router.GrizzlyRuntimeRouterProvider
import com.koupper.providers.agent.*
import kotlinx.coroutines.runBlocking

@Export
val confirm: () -> Unit = {
    val router = GrizzlyRuntimeRouterProvider()
    val budget = app.getInstance(AgentBudget::class)
    val orchestrator = app.getInstance(AgentOrchestrator::class)

    println("[CONFIRMATION] Registering official API endpoints...")

    router.registerRouter {
        // endpoint real de la fase 1
        get<Unit> {
            path { "/api/v1/system/budget" }
            script {
                { budget }
            }
        }
        
        // endpoint real de la fase 3
        post<Map<String, String>> {
            path { "/api/v1/agents/run" }
            script {
                { request: Map<String, String> ->
                    val config = agent {
                        name = request["name"] ?: "agent-x"
                        role { identity = "tester"; goal = "confirm core" }
                        task<Map<String, Any>> { prompt = "ping" }
                    }
                    val instance = runBlocking { orchestrator.dispatch(config) }
                    mapOf("taskId" to instance.taskId, "status" to "dispatched")
                }
            }
        }
    }

    router.start(port = 18080, host = "127.0.0.1")
    println("✅ CONTROL PLANE ACTIVE AT PORT 18080")

    Thread.sleep(30000)
    router.stop()
}
