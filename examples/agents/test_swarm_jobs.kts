/**
 * Native Swarm Job Queuing Test
 * 
 * Demonstrates the use of Koupper's native job queue for multi-agent handoffs.
 */
import com.koupper.orchestrator.*
import com.koupper.providers.agent.*

data class ProgrammingLanguages(val list: List<String>)

// 1. Agent A (Generator) - Enqueued as a Koupper Job
@Export
val generateLanguages: () -> Any? = {
    val config = agent {
        name = "generator"
        task<ProgrammingLanguages> { 
            prompt = "List 3 programming languages in JSON format: {\"list\": [\"...\", \"...\", \"...\"]}" 
        }
    }
    
    // Inferencia real usando el orquestador nativo
    val result = orchestrator.execute(config)
    
    println("[SWARM] Agent A finished. Enqueuing Agent B via Native Job Queue...")

    // TRASPASO NATIVO: El Agente A termina y encola al Agente B usando la sintaxis de Koupper
    // El resultado estructurado se pasa como argumento al siguiente Job
    ::evaluateLanguages.asJob(result).dispatchToQueue()
}

// 2. Agent B (Judge) - Processes the result from the queue
@Export
val evaluateLanguages: (Any) -> Unit = { resultFromA ->
    val config = agent {
        name = "judge"
        contextFromPrevious = resultFromA // Inyección automática de contexto
        task<String> { 
            prompt = "From the list provided by the previous agent, which one is considered the most versatile for AI?" 
        }
    }
    
    println("[SWARM] Agent B (Judge) starting from Queue...")
    val result = orchestrator.execute(config)
    
    println("\n--- 🏁 Swarm Conclusion ---")
    println("Final Verdict: $result")
}
