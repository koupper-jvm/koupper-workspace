import com.koupper.container.context
import com.koupper.octopus.annotations.Export
import java.io.File

data class Input(
    val force: Boolean = false,
    val profile: String = "standard",
    val projectName: String = "Project",
    val baseBranch: String = "develop"
)

private data class WriteResult(
    val path: String,
    val status: String
)

private fun ensureParent(file: File) {
    file.parentFile?.mkdirs()
}

private fun writeTemplate(base: File, relPath: String, content: String, force: Boolean): WriteResult {
    val target = File(base, relPath)
    if (target.exists() && !force) {
        return WriteResult(relPath, "skipped")
    }
    ensureParent(target)
    target.writeText(content)
    return WriteResult(relPath, if (target.exists() && force) "updated" else "created")
}

private fun ensureReference(file: File, marker: String) {
    if (!file.exists()) return
    val content = file.readText()
    if (!content.contains(marker)) {
        file.writeText(content.trimEnd() + "\n\n- Agent startup entrypoint: `docs/AGENT_RECEPTION.md`\n")
    }
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
val init: (Input) -> Map<String, Any?> = { input ->
    val cwd = resolveRepoRoot(File(context ?: ".").absoluteFile)
    val results = mutableListOf<WriteResult>()

    val receptionContent = """
        # Agent Reception

        Repository: ${input.projectName}

        This is the single startup file for every agent session.

        ## Startup checklist

        1. Read this file fully.
        2. Read `docs/SESSION_STATE.md`.
        3. Read `docs/AGENT_BOOTSTRAP.md`.
        4. Run `koupper run scripts/agent/preflight.kts '{}'`.

        ## Default workflow

        1. Start from `${input.baseBranch}`.
        2. Branch by scope (`feature/*`, `fix/*`, `docs/*`).
        3. Implement strict scope only.
        4. Run local checks before push.
        5. Open PR via release scripts.
        6. Update `docs/SESSION_STATE.md` before ending session.
    """.trimIndent() + "\n"

    val sessionStateContent = """
        # Session State

        ## Current Objective

        -

        ## Completed This Session

        -

        ## Pending Tasks

        -

        ## Branch / PR

        - Branch: `${input.baseBranch}`
        - PR: `none`

        ## Next 3 Commands

        1.
        2.
        3.

        ## Notes / Risks

        -
    """.trimIndent() + "\n"

    val checklistContent = """
        # Delivery Checklist

        ## Scope

        - [ ] Scope is clear and bounded.
        - [ ] No unrelated refactors.

        ## Implementation

        - [ ] Backward compatibility considered.
        - [ ] Behavior/default changes include migration notes.

        ## Validation

        - [ ] Relevant tests pass.
        - [ ] Local quick checks pass.
        - [ ] Docs checks pass when docs are changed.

        ## Release

        - [ ] PR opened via release scripts.
        - [ ] Required CI checks pass.
        - [ ] `${input.baseBranch}` synced after merge.
    """.trimIndent() + "\n"

    val manifestContent = """
        project:
          name: "${input.projectName}"
          base_branch: "${input.baseBranch}"

        profile: "${input.profile}"

        startup:
          reception_file: "docs/AGENT_RECEPTION.md"
          session_state_file: "docs/SESSION_STATE.md"

        required_files:
          - "docs/AGENT_RECEPTION.md"
          - "docs/AGENT_BOOTSTRAP.md"
          - "docs/SESSION_STATE.md"
          - "docs/DELIVERY_CHECKLIST.md"
          - "scripts/agent/preflight.kts"
          - "scripts/agent/init.kts"
          - "scripts/agent/validate.kts"
    """.trimIndent() + "\n"

    val setupDocContent = """
        # Agent Setup

        ## Initialize

        ```bash
        koupper run scripts/agent/init.kts '{"force":false,"profile":"standard"}'
        ```

        ## Validate

        ```bash
        koupper run scripts/agent/validate.kts '{}'
        ```

        ## Preflight (every session)

        ```bash
        koupper run scripts/agent/preflight.kts '{}'
        ```

        ## Force refresh generated files

        ```bash
        koupper run scripts/agent/init.kts '{"force":true,"profile":"standard"}'
        ```
    """.trimIndent() + "\n"

    results += writeTemplate(cwd, "docs/AGENT_RECEPTION.md", receptionContent, input.force)
    results += writeTemplate(cwd, "docs/SESSION_STATE.md", sessionStateContent, input.force)
    results += writeTemplate(cwd, "docs/DELIVERY_CHECKLIST.md", checklistContent, input.force)
    results += writeTemplate(cwd, "agent/agent-manifest.yaml", manifestContent, input.force)
    results += writeTemplate(cwd, "docs/agent-setup.md", setupDocContent, input.force)

    ensureReference(File(cwd, "AGENTS.md"), "docs/AGENT_RECEPTION.md")
    ensureReference(File(cwd, "docs/AGENT_BOOTSTRAP.md"), "docs/AGENT_RECEPTION.md")

    mapOf(
        "ok" to true,
        "profile" to input.profile,
        "force" to input.force,
        "results" to results.map { mapOf("path" to it.path, "status" to it.status) },
        "nextAction" to "Run koupper run scripts/agent/validate.kts '{}'"
    )
}
