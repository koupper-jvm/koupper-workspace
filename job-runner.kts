import com.koupper.container.context
import com.koupper.orchestrator.JobDisplayer
import com.koupper.shared.annotations.Export

@Export
val setup: (JobDisplayer) -> String = { displayer ->
    displayer.showStatus(context!!, configId = "null")
}
