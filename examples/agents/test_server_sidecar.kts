/**
 * Llama.cpp Server Sidecar Test
 * 
 * Verifies the persistent server daemon and HTTP SSE streaming.
 */
import com.koupper.container.app
import com.koupper.shared.annotations.Export
import com.koupper.providers.agent.*
import kotlinx.coroutines.runBlocking

@Export
val test: () -> Unit = {
    println("--- 🚀 Booting Persistent Server Sidecar Test ---")

    val cpuBudget = AgentBudget(
        tier = HardwareTier.CPU_OPTIMIZED,
        maxConcurrentAgents = 1,
        telemetry = HardwareTelemetry(
            physicalCores = 4,
            logicalProcessors = 8,
            totalRamGb = 16.0,
            freeRamGb = 8.0,
            hasAvx512Vnni = true,
            hasAvx2 = true,
            isNvme = true,
            hasGpu = false
        )
    )

    // Configuration
    val executablePath = System.getenv("KOUPPER_LLM_EXECUTABLE") ?: "/home/tdn-dell/develop/llama.cpp/build/bin/llama-server"
    val modelPath = System.getenv("KOUPPER_LLM_MODEL_PATH") ?: "/home/tdn-dell/develop/llama.cpp/modelo_prueba.gguf"

    println("📍 Executable: $executablePath")
    println("📍 Model: $modelPath")

    // 1. Initializing Sidecar
    val sidecar = LlamaServerSidecar(
        budget = cpuBudget,
        modelPath = modelPath,
        executablePath = executablePath,
        port = 8081
    )

    val history = listOf(
        AgentMessage("system", "You are a helpful assistant."),
        AgentMessage("user", "Explain what is a persistent sidecar in one sentence.")
    )

    runBlocking {
        try {
            println("\n[STARTING INFERENCE...]")
            print("[LLAMA_OUTPUT]: ")
            
            // 2. Performing Inference (Automatic Start + Health Check)
            sidecar.infer(history).collect { token ->
                print(token)
            }
            
            println("\n\n--- ✅ Inference Completed ---")
            
            // Note: The shutdown hook will handle the process death.
            // But for this test, we might want to stop it manually to see it works.
            println("[CLEANUP] Shutting down sidecar...")
            sidecar.stop()
            
        } catch (e: Exception) {
            println("\n\n[!] Error: ${e.message}")
            sidecar.stop()
        }
    }
}
