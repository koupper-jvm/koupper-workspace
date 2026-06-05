/**
 * Deep-Type Resolution Demo
 * 
 * Demonstrates the use of the new .toType<T>() extension for Maps, 
 * which allows scripts to bind complex nested inputs to Data Classes.
 */

import com.koupper.providers.files.toType

// 1. Define nested data structures
data class Coords(val lat: Double, val lon: Double)
data class Location(val city: String, val coords: Coords)
data class User(val id: Int, val name: String, val location: Location)

@Export
val deepTypeDemo: (Map<String, Any?>) -> Map<String, Any?> = { input ->
    // 2. The 'input' map is typically what your script receives from a Webhook or JSON file.
    val rawData = input["data"] as? Map<String, Any?> ?: mapOf(
        "id" to 101,
        "name" to "Jacob G. Acosta",
        "location" to mapOf(
            "city" to "Chihuahua",
            "coords" to mapOf(
                "lat" to 28.633,
                "lon" to -106.069
            )
        )
    )
    
    // 3. APPLY DEEP-TYPE RESOLUTION
    val user = rawData.toType<User>()
    
    println("✅ Deep-Type Resolution Success!")
    println("👤 User: ${user.name} (ID: ${user.id})")
    println("📍 Location: ${user.location.city} [${user.location.coords.lat}, ${user.location.coords.lon}]")
    
    mapOf(
        "processedUser" to user,
        "status" to "success"
    )
}
