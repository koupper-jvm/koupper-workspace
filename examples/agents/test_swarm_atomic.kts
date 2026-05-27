/**
 * Multi-Agent Swarm Handoff - ATOMIC PROOF v2
 */
import com.koupper.providers.files.fromJson
import com.koupper.providers.files.toJsonString
import com.koupper.shared.annotations.Export
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.*

class AtomicSidecar(val modelPath: String, val exePath: String, val port: Int = 8081) {
    private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    suspend fun infer(prompt: String): String {
        println("[SIDECAR] Starting inference...")

        val requestBody = mapOf(
            "prompt" to "### System:\nYou are an expert assistant. Respond ONLY with valid JSON.\n\n### User:\n$prompt\n\n### Assistant:\n",
            "stream" to false,
            "n_predict" to 128,
            "temperature" to 0.1
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:$port/completion"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJsonString()))
            .build()

        return try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            val parsed = response.body().fromJson<Map<String, Any>>()
            (parsed?.get("content") as? String)?.trim() ?: "Error: empty response"
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

class AtomicSwarmCoordinator(val sidecar: AtomicSidecar) {
    suspend fun runHandoff(prompt1: String, prompt2: String) {
        println("\n--- Agent A (Generator) Starting ---")
        val resultA = sidecar.infer(prompt1)
        println("[AGENT A OUTPUT]: $resultA")

        println("\n--- HANDOFF PROTOCOL: Injecting Result A into Agent B ---")
        val finalPromptB = """
            CONTEXT FROM PREVIOUS AGENT:
            $resultA

            YOUR TASK:
            $prompt2
        """.trimIndent()

        println("\n--- Agent B (Judge) Starting ---")
        val resultB = sidecar.infer(finalPromptB)
        println("[AGENT B OUTPUT]: $resultB")
    }
}

@Export
val run: () -> Unit = {
    val exe = "/home/tdn-dell/develop/llama.cpp/build/bin/llama-server"
    val model = "/home/tdn-dell/develop/llama.cpp/modelo_prueba.gguf"
    val port = 8082

    println("--- SWARM HANDOFF ATOMIC TEST ---")

    val process = ProcessBuilder(listOf(exe, "-m", model, "--port", port.toString(), "-t", "4", "-ngl", "0", "--log-disable"))
        .redirectError(File("/dev/null"))
        .start()

    runBlocking {
        println("Waiting for server health (8082)...")
        delay(15000)

        val sidecar = AtomicSidecar(model, exe, port)
        val coordinator = AtomicSwarmCoordinator(sidecar)

        try {
            coordinator.runHandoff(
                prompt1 = "Escribe una lista de 3 lenguajes de programación en formato JSON: {\"languages\": [\"...\", \"...\", \"...\"]}",
                prompt2 = "Evalúa cuál de estos lenguajes es el más rápido y explica por qué en una línea."
            )
        } finally {
            println("\n[CLEANUP] Killing llama-server...")
            process.destroyForcibly()
        }
    }
}
