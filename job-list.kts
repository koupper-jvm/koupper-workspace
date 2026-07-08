import com.koupper.container.context
import com.koupper.orchestrator.JobInfo
import com.koupper.orchestrator.JobLister
import com.koupper.orchestrator.JobResult
import com.koupper.shared.annotations.Export

@Export
val setup: (JobLister) -> String = { runner ->
    val sb = StringBuilder()

    runner.list(context!!, jobId = null, configId = null) { res ->
        res.forEach {
            when (it) {
                is JobInfo -> {
                    if (sb.isNotEmpty()) sb.appendLine()
                    sb.appendLine("From config with id: ${it.configId}")
                    sb.appendLine("📦 Job ID: ${it.id}")
                    sb.appendLine(" - Function: ${it.function}")
                    sb.appendLine(" - Params: ${it.params}")
                    sb.appendLine(" - Source: ${it.source}")
                    sb.appendLine(" - Context: ${it.context}")
                    sb.appendLine(" - Version: ${it.version}")
                    sb.appendLine(" - Origin: ${it.origin}")
                }
                is JobResult.Error -> {
                    if (sb.isNotEmpty()) sb.appendLine()
                    sb.appendLine("${it.message}")
                }
            }
        }
    }

    sb.toString()
}