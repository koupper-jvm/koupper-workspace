import com.koupper.shared.annotations.Export

@Export
val test: () -> Unit = {
    println("--- Hardware Sidecar Test (llama.cpp) ---")
    println("Requires: KOUPPER_LLM_EXECUTABLE and KOUPPER_LLM_MODEL_PATH env vars")
    println("The LlamaCppSidecar API has been superseded — use inference via")
    println("the InferenceEngine provider instead: app.getInstance(InferenceEngine::class)")
    println("--- Test Skipped ---")
}
