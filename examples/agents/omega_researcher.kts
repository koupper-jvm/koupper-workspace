import com.koupper.providers.agent.*
import com.koupper.container.app
import com.koupper.shared.annotations.Export
import kotlinx.coroutines.runBlocking

/**
 * Contrato Estricto para el Reporte
 */
data class ResearchReport(
    val mainThesis: String,
    val supportingArguments: List<String>,
    val confidenceScore: Double,
    val requiresHumanReview: Boolean
)

@Export
val setup: () -> Unit = {
    // 1. Definimos el Agente con el DSL de Koupper
    val agentDef = agent {
        name = "omega-researcher"
        
        role {
            identity = "System Telemetry Expert"
            goal = "Interpret hardware capabilities and potential for AI automation."
            instructions = """
                - Use technical language.
                - Base your confidence on the presence of AVX-512 and NVMe.
                - If data is missing, request human review.
            """.trimIndent()
        }

        tools {
            use("file-handler") // Usamos el Service Provider nativo de Koupper
        }

        task<ResearchReport> {
            prompt = """
                Read the file 'examples/agents/hardware_audit.json' and evaluate if this system 
                is optimized for a Multi-Agent distributed swarm. 
            """.trimIndent()
        }
    }

    // 2. Despachamos al Orquestador
    val orchestrator = app.getInstance(AgentOrchestrator::class)
    
    println("--- Starting Omega Researcher (SYNC MODE) ---")
    
    runBlocking {
        // Usamos dispatchSync para forzar la salida en el hilo principal y que Koupper la capture
        val instance = orchestrator.dispatchSync(agentDef)
        
        println("--- Task Finished with status: ${instance.state} ---")
    }
}
