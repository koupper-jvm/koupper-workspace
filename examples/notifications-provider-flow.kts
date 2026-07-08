import com.koupper.container.app
import com.koupper.providers.notifications.NotificationsProvider
import com.koupper.shared.annotations.Export

data class Input(
    val channel: String = "ops",
    val text: String = "Koupper notifications provider test",
    val mode: String = "text"
)

@Export
val notifyOps: (Input) -> Map<String, Any?> = { input ->
    val notifications = app.getInstance(NotificationsProvider::class)
    val result = when (input.mode.lowercase()) {
        "structured" -> notifications.sendStructured(
            channel = input.channel,
            payload = mapOf("service" to "koupper", "message" to input.text)
        )

        "error" -> notifications.sendError(input.channel, "ProviderError", input.text)
        else -> notifications.sendText(input.channel, input.text)
    }

    mapOf(
        "ok" to result.ok,
        "statusCode" to result.statusCode,
        "provider" to result.provider,
        "responseBody" to result.responseBody
    )
}
