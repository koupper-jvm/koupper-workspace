/**
 * Hello World Provider Demo
 * 
 * Demonstrates the use of a custom Service Provider created via 'koupper provider new'.
 */

import com.koupper.providers.helloworld.HelloWorldProvider
import com.koupper.shared.annotations.Export
import com.koupper.container.app

@Export
val helloWorldDemo: (Map<String, Any?>) -> Map<String, Any?> = {
    val hello = app.getInstance(HelloWorldProvider::class)
    val response = hello.ping()
    
    println("👋 Provider Response: ${response.message}")
    
    mapOf(
        "ok" to response.ok,
        "message" to response.message
    )
}
