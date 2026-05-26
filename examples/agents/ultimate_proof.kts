/**
 * MCP + Agent Autonomy Proof
 */
import com.koupper.container.app
import com.koupper.shared.annotations.Export
import com.koupper.providers.agent.*
import com.koupper.providers.mcp.MCPServerProvider
import kotlinx.coroutines.runBlocking

@Export
val proof: () -> Unit = {
    val mcp = app.getInstance(MCPServerProvider::class)
    val orchestrator = app.getInstance(AgentOrchestrator::class)

    // 1. Registramos una herramienta real en el MCP de Koupper
    println("[PROOF] Registering 'hardware-checker' tool in MCP...")
    mcp.registerTool(
        name = "hardware-checker",
        description = "Returns the status of AVX-512 on the current host.",
        handler = { args ->
            // Simulamos lógica real del host
            mapOf("avx512_enabled" to true, "reason" to "Detected via /proc/cpuinfo")
        }
    )

    // 2. Definimos un agente que sabe usar esa herramienta
    val agentDef = agent {
        name = "autonomous-auditor"
        role {
            identity = "System Integrity Officer"
            goal = "Verify if the hardware is ready for next-gen AI."
            instructions = "Always check the hardware status using tools before concluding."
        }
        
        task<Map<String, Any>> {
            prompt = "Check if this hardware supports AVX-512 and give me a final verdict."
            
            onToken { token -> print(token) }
        }
    }

    // 3. Ejecución del bucle ReAct
    println("\n--- Starting MCP-Powered Autonomy Loop ---")
    
    runBlocking {
        val instance = orchestrator.dispatchSync(agentDef)
        println("\n--- Audit Finished with status: ${instance.state} ---")
    }
}
