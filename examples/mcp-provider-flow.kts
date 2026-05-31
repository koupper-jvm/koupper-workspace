/**
 * MCP Provider Demo
 *
 * Purpose:
 * - Register tools and validate local HTTP MCP-style endpoints.
 */
import com.koupper.container.app
import com.koupper.shared.annotations.Export
import com.koupper.providers.mcp.MCPServerProvider
import com.koupper.providers.http.HtppClient

data class Input(
    val port: Int = 18082
)

@Export
val mcpDemo: (Input) -> Map<String, Any?> = { input ->
    val mcp = app.getInstance(MCPServerProvider::class)

    mcp.registerTool(
        name = "sum",
        description = "Adds two numbers",
        inputSchema = mapOf("a" to "number", "b" to "number")
    ) { args ->
        val a = (args["a"] as? Number)?.toDouble() ?: 0.0
        val b = (args["b"] as? Number)?.toDouble() ?: 0.0
        mapOf("value" to (a + b))
    }

    val server = mcp.startHttp(port = input.port)

    val http = app.getInstance(HtppClient::class)

    val listResponse = http.get { url = "http://127.0.0.1:${input.port}/mcp/tools" }
    val callResponse = http.post {
        url = "http://127.0.0.1:${input.port}/mcp/call"
        body { json("""{"name":"sum","arguments":{"a":2,"b":5}}""") }
    }

    mcp.stop()

    mapOf(
        "ok"          to (listResponse?.isSuccessful() == true && callResponse.isSuccessful()),
        "server"      to server,
        "toolsStatus" to (listResponse?.code() ?: -1),
        "toolsPayload" to listResponse?.asString(),
        "callStatus"  to callResponse.code(),
        "callPayload" to callResponse.asString()
    )
}
