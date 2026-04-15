import com.koupper.container.context
import com.koupper.octopus.annotations.Export
import java.io.File

data class Input(
    val requireReceptionReferenceInAgents: Boolean = true,
    val requireReceptionReferenceInBootstrap: Boolean = true
)

private fun readIfExists(base: File, relPath: String): String? {
    val file = File(base, relPath)
    return if (file.exists()) file.readText() else null
}

private fun resolveRepoRoot(start: File): File {
    var current: File? = start
    repeat(8) {
        if (current == null) return@repeat
        val hasAgents = File(current, "AGENTS.md").exists()
        val hasDocs = File(current, "docs").exists()
        if (hasAgents && hasDocs) {
            return current!!
        }
        current = current?.parentFile
    }
    return start
}

@Export
val validate: (Input) -> Map<String, Any?> = { input ->
    val cwd = resolveRepoRoot(File(context ?: ".").absoluteFile)
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    val requiredFiles = listOf(
        "docs/AGENT_RECEPTION.md",
        "docs/AGENT_BOOTSTRAP.md",
        "docs/SESSION_STATE.md",
        "docs/DELIVERY_CHECKLIST.md",
        "scripts/agent/preflight.kts",
        "scripts/agent/init.kts",
        "scripts/agent/validate.kts"
    )

    requiredFiles.forEach { relPath ->
        if (!File(cwd, relPath).exists()) {
            errors += "Missing required file: $relPath"
        }
    }

    val agentsContent = readIfExists(cwd, "AGENTS.md")
    if (agentsContent == null) {
        warnings += "AGENTS.md not found"
    } else if (input.requireReceptionReferenceInAgents && !agentsContent.contains("docs/AGENT_RECEPTION.md")) {
        errors += "AGENTS.md must reference docs/AGENT_RECEPTION.md"
    }

    val bootstrapContent = readIfExists(cwd, "docs/AGENT_BOOTSTRAP.md")
    if (bootstrapContent == null) {
        errors += "docs/AGENT_BOOTSTRAP.md not found"
    } else if (input.requireReceptionReferenceInBootstrap && !bootstrapContent.contains("docs/AGENT_RECEPTION.md")) {
        errors += "docs/AGENT_BOOTSTRAP.md must reference docs/AGENT_RECEPTION.md"
    }

    mapOf(
        "ok" to errors.isEmpty(),
        "errors" to errors,
        "warnings" to warnings,
        "nextAction" to if (errors.isEmpty()) null else "Run koupper run scripts/agent/init.kts '{\"force\":false}' and re-check"
    )
}
