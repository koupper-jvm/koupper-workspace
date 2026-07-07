
/**
 * Agentic Control Plane Live Server (Flat Registry Version)
 */
import com.koupper.container.app
import com.koupper.shared.annotations.Export
import com.koupper.providers.runtime.router.RuntimeRouterProvider
import com.koupper.providers.runtime.router.StreamResponse
import com.koupper.providers.runtime.router.RequestContext
import com.koupper.shared.runtime.GlobalRouteRegistry
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
        get {
            path { "/api/v1/system/budget" }
            script {
                { budget }
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
        get {
            path { "/api/v1/agents/stream/{taskId}" }
            script {
                { 
                    val reqCtx = GlobalRouteRegistry.currentRequest.get() as RequestContext
                    val taskId = reqCtx.pathParams["taskId"] ?: ""
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

    router.start(8081)
}

