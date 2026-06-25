fun main() {
    val method = net.minecraft.world.entity.Entity::class.java.methods.find { it.name.contains("Tags", ignoreCase = true) }
    println("Method: ${method?.name}")
}
