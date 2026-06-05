/**
 * Deep-Type Resolution Debug Script
 */

import com.koupper.providers.files.toTypeRef

@Export
val debugTypeResolution: () -> Map<String, Any?> = {
    println("DEBUG: Inspecting runtime classes for toTypeRef...")
    
    try {
        val clazz = Class.forName("com.koupper.providers.files.JSONFileHandlerImplKt")
        println("✅ Found JSONFileHandlerImplKt")
        println("Methods:")
        clazz.methods.filter { it.name.contains("toTypeRef") }.forEach { println("  - ${it.name}") }
    } catch (e: Exception) {
        println("❌ JSONFileHandlerImplKt NOT found: ${e.message}")
    }
    
    // Test the extension directly
    data class TestObj(val id: Int)
    val map = mapOf("id" to 123)
    try {
        val result = map.toTypeRef<TestObj>()
        println("✅ Direct Test Success: id=${result.id}")
    } catch (e: Exception) {
        println("❌ Direct Test Failed: ${e.message}")
    }
    
    mapOf("done" to true)
}
