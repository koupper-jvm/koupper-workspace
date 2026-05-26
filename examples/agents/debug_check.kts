/**
 * Atmostic certitude test
 */
import com.koupper.container.app
import com.koupper.shared.annotations.Export
import com.koupper.providers.runtime.router.RuntimeRouterProvider
import com.koupper.providers.agent.AgentBudget

@Export
val check: () -> Unit = {
    val router = app.getInstance(RuntimeRouterProvider::class)
    val budget = app.getInstance(AgentBudget::class)

    println("[DEBUG] Registering and starting in ONE step...")

    router.registerRouter {
        get<Unit> {
            path { "/debug" }
            script {
                { _: Unit -> 
                    mapOf("status" to "CONNECTED_TO_CORE", "budget_tier" to budget.tier.javaClass.simpleName)
                }
            }
        }
    }

    router.start(port = 18080, host = "127.0.0.1")
    println("✅ SERVER IS UP! Try: curl http://127.0.0.1:18080/debug")

    Thread.sleep(300000) // 5 minutos
    router.stop()
}
