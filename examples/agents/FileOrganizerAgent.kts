// FileOrganizerAgent.kts
// Description: Clasifica ~/Downloads en carpetas por tipo (images/docs/code/archives)
// y genera un reporte de lo que movió.
// Input opcional: ORGANIZER_TARGET env var para cambiar el directorio.

import com.koupper.shared.annotations.Export
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Export
val setup: () -> Unit = {
    val home      = System.getProperty("user.home")!!
    val jobsDir   = File(System.getenv("CORTEX_JOBS_DIR") ?: "$home/.koupper/jobs")
    val logDir    = File(jobsDir, "logs/default").also { it.mkdirs() }
    val logFile   = File(logDir, "file-organizer.log")
    val targetDir = File(System.getenv("ORGANIZER_TARGET") ?: "$home/Downloads")

    fun ts()             = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    fun log(msg: String) = logFile.appendText("[${ts()}] $msg\n").also { println(msg) }

    val categoryMap = mapOf(
        "images"    to setOf("jpg","jpeg","png","gif","webp","svg","bmp","ico"),
        "videos"    to setOf("mp4","mkv","avi","mov","webm","flv"),
        "docs"      to setOf("pdf","doc","docx","odt","txt","md","csv","xlsx","xls","ppt","pptx"),
        "code"      to setOf("kt","kts","py","js","ts","sh","java","json","yaml","yml","xml","html","css"),
        "archives"  to setOf("zip","tar","gz","bz2","rar","7z"),
        "audio"     to setOf("mp3","wav","flac","ogg","aac")
    )

    log("=== File Organizer ===")
    log("Target: ${targetDir.absolutePath}")

    if (!targetDir.exists()) {
        log("ERROR: target directory does not exist")
    } else {
        var moved = 0
        var skipped = 0

        targetDir.listFiles { f -> f.isFile }?.forEach { file ->
            val ext = file.extension.lowercase()
            val category = categoryMap.entries.firstOrNull { ext in it.value }?.key ?: "other"
            val destDir  = File(targetDir, category).also { it.mkdirs() }
            val dest     = File(destDir, file.name)

            if (dest.exists()) {
                log("  skip  ${file.name} (already in $category/)")
                skipped++
            } else {
                file.renameTo(dest)
                log("  moved ${file.name} → $category/")
                moved++
            }
        }

        log("")
        log("Done — moved: $moved  skipped: $skipped")
    }
}
