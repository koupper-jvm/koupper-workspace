/**
 * Multi-Agent Swarm Handoff Test
 * 
 * Verifies that the SwarmCoordinator can pass structured data between agents.
 */
import com.koupper.container.app
import com.koupper.providers.agent.*
import com.koupper.shared.annotations.Export
import kotlinx.coroutines.runBlocking

data class LanguageList(val languages: List<String>)
data class Evaluation(val best: String, val reason: String)

@Export
val run: () -> Unit = {
    val coordinator = app.getInstance(SwarmCoordinator::class)

    // 1. Agent Generador
    val generator = agent {
        name = "generator"
        role {
            identity = "Programming Expert"
            goal = "List programming languages"
        }
        task<LanguageList> {
            prompt = "Escribe 3 nombres de lenguajes de programación."
        }
    }

    // 2. Agent Juez
    val judge = agent {
        name = "judge"
        role {
            identity = "Software Architect"
            goal = "Evaluate performance"
        }
        task<Evaluation> {
            prompt = "Evalúa cuál de estos lenguajes es el más rápido y explica por qué en una línea."
        }
    }

    println("--- 🚀 Starting Swarm Execution (Handoff Protocol) ---")

    runBlocking {
        val results = coordinator.runSequence(listOf(generator, judge))
        
        println("\n\n--- 🏁 Swarm Summary ---")
        results.forEach { instance ->
            println("Agent: ${instance.config.name} | Status: ${instance.state} | Result: ${instance.result}")
        }
    }
}
