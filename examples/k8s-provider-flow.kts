import com.koupper.container.app
import com.koupper.providers.k8s.K8sProvider
import com.koupper.shared.annotations.Export

data class Input(
    val manifestPath: String = "examples/manifests/sample-deployment.yaml",
    val namespace: String = "default"
)

@Export
val k8sOpsDemo: (Input) -> Map<String, Any?> = { input ->
    val k8s = app.getInstance(K8sProvider::class)
    val result = k8s.apply(input.manifestPath, namespace = input.namespace, dryRun = true)
    mapOf(
        "ok" to (result.exitCode == 0),
        "command" to result.command,
        "exitCode" to result.exitCode,
        "stdout" to result.stdout,
        "stderr" to result.stderr
    )
}
