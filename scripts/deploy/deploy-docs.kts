import com.koupper.container.context
import com.koupper.shared.annotations.Export
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Deploy Koupper public documentation to S3 + CloudFront.
 *
 * Builds the VitePress site from koupper-docs/ (legacy alias koupper-document/),
 * syncs the output to s3://koupper-docs, and invalidates the CloudFront
 * distribution so https://koupper.com/ reflects the update immediately.
 *
 * Usage:
 *   koupper run scripts/deploy/deploy-docs.kts '{"dryRun": true}'
 *   koupper run scripts/deploy/deploy-docs.kts '{"dryRun": false}'
 */

data class Input(
    val dryRun: Boolean = false,
    val bucket: String = "koupper-docs",
    val distributionId: String = "E1EOMSACJRDKWH",
    val region: String = "us-east-1",
    val commandTimeoutSeconds: Long = 300
)

private fun isWindows(): Boolean =
    System.getProperty("os.name").lowercase().contains("win")

private fun run(command: String, cwd: File, timeoutSeconds: Long, dryRun: Boolean): Map<String, Any?> {
    if (dryRun) {
        println("[dry-run] ${cwd.name}> $command")
        return mapOf("command" to command, "exitCode" to 0, "output" to "dry-run", "dryRun" to true)
    }

    val args = if (isWindows()) {
        listOf("pwsh", "-NoProfile", "-Command", command)
    } else {
        listOf("bash", "-lc", command)
    }

    println("[run] ${cwd.name}> $command")

    val process = ProcessBuilder(args)
        .directory(cwd)
        .redirectErrorStream(true)
        .start()

    val output = process.inputStream.bufferedReader().readText().trim()
    val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

    if (!completed) {
        process.destroyForcibly()
        error("command timed out after ${timeoutSeconds}s: $command")
    }

    val exitCode = process.exitValue()
    if (exitCode != 0) error("command failed (exit $exitCode): $command\n$output")

    if (output.isNotBlank()) println(output)
    return mapOf("command" to command, "exitCode" to exitCode, "output" to output, "dryRun" to false)
}

private fun findDocumentDir(cwd: File): File {
    val names = listOf("koupper-docs", "koupper-document")

    fun probe(base: File): File? =
        names.map { File(base, it) }.firstOrNull { it.exists() && it.isDirectory }

    probe(cwd)?.let { return it }

    var cursor: File? = cwd
    repeat(5) {
        cursor = cursor?.parentFile
        val base = cursor ?: return@repeat
        probe(base)?.let { return it }
    }
    error(
        "Docs directory not found (tried ${names.joinToString(", ")}) relative to ${cwd.absolutePath}"
    )
}

/**
 * VitePress `cleanUrls: true` emits `/getting-started` links while S3 stores
 * `getting-started.html`. Copy each HTML object to an extensionless key with
 * an explicit HTML content type so both URLs work without a CloudFront rewrite.
 */
private fun publishExtensionlessHtml(
    distDir: File,
    bucket: String,
    region: String,
    timeoutSeconds: Long,
    dryRun: Boolean,
    cwd: File
): Int {
    if (!distDir.exists()) return 0
    var count = 0
    distDir.walkTopDown()
        .filter { it.isFile && it.extension.equals("html", ignoreCase = true) }
        .filter { it.name != "index.html" && it.name != "404.html" }
        .forEach { file ->
            val rel = file.relativeTo(distDir).invariantSeparatorsPath
            val key = rel.removeSuffix(".html")
            val local = file.absolutePath.replace("\\", "/")
            run(
                command = "aws s3 cp '$local' 's3://$bucket/$key' --content-type 'text/html; charset=utf-8' --cache-control 'public, max-age=300' --region $region",
                cwd = cwd,
                timeoutSeconds = timeoutSeconds,
                dryRun = dryRun
            )
            count++
        }
    println("Uploaded $count extensionless HTML object(s)")
    return count
}

@Export
val setup: (Input) -> Map<String, Any?> = { input ->
    val cwd = File(context ?: ".").absoluteFile
    val docsDir = findDocumentDir(cwd)
    val distDir = File(docsDir, "docs/.vitepress/dist")

    println("=== Koupper Docs Deploy ===")
    println("bucket        : s3://${input.bucket}")
    println("distribution  : ${input.distributionId}")
    println("region        : ${input.region}")
    println("dryRun        : ${input.dryRun}")
    println("docs dir      : ${docsDir.absolutePath}")
    println()

    // 1. Install dependencies (ci-safe, skips if already installed)
    println("[1/3] Installing docs dependencies...")
    val install = run(
        command = "npm ci --prefer-offline",
        cwd = docsDir,
        timeoutSeconds = input.commandTimeoutSeconds,
        dryRun = input.dryRun
    )

    // 2. Build VitePress site
    println("\n[2/3] Building VitePress site...")
    val build = run(
        command = "npm run docs:build",
        cwd = docsDir,
        timeoutSeconds = input.commandTimeoutSeconds,
        dryRun = input.dryRun
    )

    if (!input.dryRun && !distDir.exists()) {
        error("Build output not found at ${distDir.absolutePath} — docs:build may have failed silently")
    }

    val distPath = distDir.absolutePath.replace("\\", "/")

    // 3. Sync to S3
    println("\n[3/3] Syncing to s3://${input.bucket}...")
    val sync = run(
        command = "aws s3 sync '$distPath' 's3://${input.bucket}' --delete --region ${input.region}",
        cwd = cwd,
        timeoutSeconds = input.commandTimeoutSeconds,
        dryRun = input.dryRun
    )

    println("\nPublishing extensionless HTML keys for VitePress cleanUrls...")
    val cleanUrlUploads = publishExtensionlessHtml(
        distDir = distDir,
        bucket = input.bucket,
        region = input.region,
        timeoutSeconds = input.commandTimeoutSeconds,
        dryRun = input.dryRun,
        cwd = cwd
    )

    // 4. CloudFront invalidation
    println("\nInvalidating CloudFront distribution ${input.distributionId}...")
    val invalidation = run(
        command = "aws cloudfront create-invalidation --distribution-id ${input.distributionId} --paths '/*'",
        cwd = cwd,
        timeoutSeconds = 60,
        dryRun = input.dryRun
    )

    println("\n✓ Deploy complete — https://koupper.com/")

    mapOf(
        "ok" to true,
        "dryRun" to input.dryRun,
        "bucket" to input.bucket,
        "distributionId" to input.distributionId,
        "docsDir" to docsDir.absolutePath,
        "install" to install,
        "build" to build,
        "sync" to sync,
        "cleanUrlUploads" to cleanUrlUploads,
        "invalidation" to invalidation
    )
}
