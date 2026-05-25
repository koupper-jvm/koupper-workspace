import com.koupper.shared.annotations.Export
import com.koupper.container.context
import com.koupper.orchestrator.JobDisplayer

@Export
val setup: (JobDisplayer) -> String = { displayer ->
    displayer.showStatus(context!!, configId = "null")
}
