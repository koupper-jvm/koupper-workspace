/**
 * Agentic Control Plane Live Server (Flat Registry Version)
 */
import com.koupper.container.app
import com.koupper.shared.annotations.Export
import com.koupper.providers.runtime.router.RuntimeRouterProvider
import com.koupper.providers.runtime.router.StreamResponse
import com.koupper.providers.agent.*
import kotlinx.coroutines.runBlocking

@Export
val serve: () -> Unit = {
    val router = app.getInstance(RuntimeRouterProvider::class)
    val orchestrator = app.getInstance(AgentOrchestrator::class)
    val budget = app.getInstance(AgentBudget::class)
    
    println("[DEBUG] Registering flat routes...")

    router.registerRouter {
        // 1. GET /api/v1/system/budget
        get<Unit> {
            path { "/api/v1/system/budget" }
            script {
                { _: Unit -> budget }
            }
        }

        // 2. POST /api/v1/agents/run
        post<Map<String, String>> {
            path { "/api/v1/agents/run" }
            script {
                { request: Map<String, String> ->
                    val config = agent {
                        name = request["name"] ?: "anonymous"
                        role {
                            identity = request["role"] ?: ""
                            goal = request["goal"] ?: ""
                        }
                        task<Map<String, Any>> {
                            prompt = request["prompt"] ?: ""
                        }
                    }

                    val instance = runBlocking { orchestrator.dispatch(config) }
                    
                    mapOf(
                        "status" to "accepted",
                        "taskId" to instance.taskId
                    )
                }
            }
        }

        // 3. GET /api/v1/agents/stream/{taskId}
        get<String> {
            path { "/api/v1/agents/stream/{taskId}" }
            script {
                { taskId: String ->
                    val instance = orchestrator.getTask(taskId)
                        ?: throw IllegalArgumentException("Task not found")

                    object : StreamResponse {
                        override fun onData(callback: (String) -> Unit) {
                            instance.onToken { token -> callback(token) }
                        }
                        override fun onClose(callback: () -> Unit) {}
                    }
                }
            }
        }
    }
    
    val port = 18080
    val host = "127.0.0.1"
    
    val server = router.start(port = port, host = host)
    
    println("🚀 Koupper Agentic Control Plane is live (Flat Registry)!")
    println("📍 Budget: GET http://$host:$port/api/v1/system/budget")
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
