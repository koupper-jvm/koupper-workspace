import com.koupper.shared.annotations.Export
import com.koupper.octopus.process.ModuleAnalyzer

@Export
val setup: (ModuleAnalyzer) -> Unit = { analyzer ->
    val target = System.getenv("KOUPPER_MODULE_TARGET") ?: error("KOUPPER_MODULE_TARGET environment variable is not set")
    analyzer.target(target).run()
}
