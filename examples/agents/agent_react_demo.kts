/**
 * Agent ReAct Autonomy Demo
 */
import com.koupper.container.app
import com.koupper.shared.annotations.Export
import com.koupper.providers.agent.*
import kotlinx.coroutines.runBlocking

data class AnalysisResult(
    val status: String,
    val findings: String
)

@Export
val run: () -> Unit = {
    val orchestrator = app.getInstance(AgentOrchestrator::class)

    val agentDef = agent {
        name = "autonomous-agent"
        
        role {
            identity = "Autonomous Systems Auditor"
            goal = "Audit system metrics and provide a summary."
            instructions = "Use tools to gather data before providing a final answer."
        }

        tools {
            use("file-handler")
            use("command-runner")
        }

        task<AnalysisResult> {
            // Este prompt contiene 'file', lo que disparará el ToolCall en nuestro motor simulado
            prompt = "Read the system metrics from the file 'metrics.json' and analyze the CPU usage."
        }
    }

    println("--- Starting Autonomous ReAct Loop ---")
    
    runBlocking {
        val instance = orchestrator.dispatchSync(agentDef)
        println("\n--- Final State: ${instance.state} ---")
    }
}
