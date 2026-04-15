import com.koupper.container.context
import com.koupper.octopus.annotations.Export
import java.io.File
import java.util.concurrent.TimeUnit

data class Input(
    val baseBranch: String = "develop",
    val allowedBranchPrefixes: List<String> = listOf("feature/", "fix/", "docs/"),
    val requireCleanTree: Boolean = false,
    val commandTimeoutSeconds: Long = 20
)

data class CommandResult(
    val command: String,
    val exitCode: Int,
    val output: String
)

private fun runCommand(args: List<String>, cwd: File, timeoutSeconds: Long): CommandResult {
    val process = ProcessBuilder(args)
        .directory(cwd)
        .redirectErrorStream(true)
        .start()

    val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
    if (!completed) {
        process.destroyForcibly()
        return CommandResult(args.joinToString(" "), 124, "command timed out after ${timeoutSeconds}s")
    }

    return CommandResult(
        command = args.joinToString(" "),
        exitCode = process.exitValue(),
        output = process.inputStream.bufferedReader().readText().trim()
    )
}

private fun isAllowedBranch(branch: String, baseBranch: String, allowedPrefixes: List<String>): Boolean {
    return branch == baseBranch || allowedPrefixes.any { prefix -> branch.startsWith(prefix) }
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
val check: (Input) -> Map<String, Any?> = { input ->
    val cwd = resolveRepoRoot(File(context ?: ".").absoluteFile)
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    val requiredFiles = listOf(
        "docs/AGENT_RECEPTION.md",
        "docs/AGENT_BOOTSTRAP.md",
        "docs/SESSION_STATE.md",
        "docs/DELIVERY_CHECKLIST.md",
        "scripts/release/README.md",
        "docs/NEXT_FEATURES_NOTES.md"
    )

    requiredFiles.forEach { relPath ->
        if (!File(cwd, relPath).exists()) {
            errors += "Missing required file: $relPath"
        }
    }

    val branchResult = runCommand(listOf("git", "branch", "--show-current"), cwd, input.commandTimeoutSeconds)
    if (branchResult.exitCode != 0) {
        errors += "Failed to detect current branch: ${branchResult.output}"
    } else {
        val branch = branchResult.output.trim()
        if (!isAllowedBranch(branch, input.baseBranch, input.allowedBranchPrefixes)) {
            warnings += "Current branch '$branch' does not match recommended branches (${input.baseBranch}, ${input.allowedBranchPrefixes.joinToString(", ")})"
        }
    }

    if (input.requireCleanTree) {
        val statusResult = runCommand(listOf("git", "status", "--porcelain"), cwd, input.commandTimeoutSeconds)
        if (statusResult.exitCode != 0) {
            errors += "Failed to check git status: ${statusResult.output}"
        } else if (statusResult.output.isNotBlank()) {
            warnings += "Working tree is not clean"
        }
    }

    val ok = errors.isEmpty()
    mapOf(
        "ok" to ok,
        "errors" to errors,
        "warnings" to warnings,
        "nextActions" to listOf(
            "Read docs/AGENT_RECEPTION.md",
            "Read docs/SESSION_STATE.md",
            "Run local quick checks before push"
        )
    )
}
