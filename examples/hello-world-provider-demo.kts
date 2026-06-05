/**
 * Hello World Provider Demo
 * 
 * Demonstrates the use of a custom Service Provider created via 'koupper provider new'.
 */

import com.koupper.providers.helloworld.HelloWorldProvider

val helloWorldDemo: (Map<String, Any?>) -> Map<String, Any?> = {
    val hello = app.getInstance(HelloWorldProvider::class)
    val response = hello.ping()
    
    println("👋 Provider Response: ${response.message}")
    
    mapOf(
        "ok" to response.ok,
        "message" to response.message
    )
}
