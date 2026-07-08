import com.koupper.providers.agent.*
import com.koupper.shared.annotations.Export

@Export
val test: () -> Unit = {
    println("--- 🚀 Sincronic Hardware Test ---")

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

    val executablePath = env("KOUPPER_LLM_EXECUTABLE", "/home/tdn-dell/develop/llama.cpp/build/bin/llama-cli")
    val modelPath = env("KOUPPER_LLM_MODEL_PATH", "/home/tdn-dell/develop/llama.cpp/modelo_prueba.gguf")

    val sidecar = LlamaCppSidecar(cpuBudget, modelPath, executablePath)
    val prompt = "Responde solo con la palabra: EXITO"

    println("[PROMPT]: $prompt")
    println("[WAITING FOR LLAMA...]")

    // Ejecución sincrónica
    val response = sidecar.inferSync(prompt)

    println("\n[LLAMA_RESPONSE]: $response")
    println("\n--- ✅ Test Finalized ---")
}
