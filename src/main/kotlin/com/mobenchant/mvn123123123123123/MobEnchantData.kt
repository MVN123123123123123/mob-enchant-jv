package com.mobenchant.mvn123123123123123

import com.mojang.serialization.Codec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity

// ============================================================================
// Enchantment data stored on each enchanted mob
// ============================================================================
@Serializable
data class MobEnchantment(
    val id: String,
    val level: Int,
    val maxLevel: Int,
    val category: String,
)

// ============================================================================
// Fabric Data Attachments — persistent custom data on entities
// ============================================================================
object MobEnchantData {

    const val MOD_ID = "mob-enchant"

    private val json = Json { ignoreUnknownKeys = true }

    // --- Attachments ---

    /** JSON-serialized list of MobEnchantment stored on the entity. */
    val ENCHANTMENTS: AttachmentType<String> = AttachmentRegistry.createPersistent(
        Identifier.fromNamespaceAndPath(MOD_ID, "enchantments"),
        Codec.STRING,
    )

    /** Whether the Infinity shield has been broken by a potion effect. */
    val INFINITY_BROKEN: AttachmentType<Boolean> = AttachmentRegistry.createPersistent(
        Identifier.fromNamespaceAndPath(MOD_ID, "infinity_broken"),
        Codec.BOOL,
    )

    /** Whether this entity has already been rolled for enchantment (prevents re-roll on chunk reload). */
    val ALREADY_ROLLED: AttachmentType<Boolean> = AttachmentRegistry.createPersistent(
        Identifier.fromNamespaceAndPath(MOD_ID, "rolled"),
        Codec.BOOL,
    )

    // ========================================================================
    // Extension helpers — called on any Entity
    // ========================================================================

    fun Entity.getMobEnchantments(): List<MobEnchantment>? {
        val raw = getAttached(ENCHANTMENTS) ?: return null
        return try {
            json.decodeFromString<List<MobEnchantment>>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun Entity.setMobEnchantments(enchants: List<MobEnchantment>) {
        setAttached(ENCHANTMENTS, json.encodeToString(enchants))
    }

    fun Entity.clearMobEnchantments() {
        removeAttached(ENCHANTMENTS)
        removeAttached(INFINITY_BROKEN)
    }

    fun Entity.getMobEnchant(enchantId: String): MobEnchantment? {
        return getMobEnchantments()?.find { it.id == enchantId }
    }

    fun Entity.isInfinityBroken(): Boolean {
        return getAttached(INFINITY_BROKEN) == true
    }

    fun Entity.setInfinityBroken(broken: Boolean) {
        setAttached(INFINITY_BROKEN, broken)
    }

    fun Entity.isAlreadyRolled(): Boolean {
        return getAttached(ALREADY_ROLLED) == true
    }

    fun Entity.markAsRolled() {
        setAttached(ALREADY_ROLLED, true)
    }

    val RESPIRATION_TICKS: AttachmentType<Int> = AttachmentRegistry.createPersistent(
        Identifier.fromNamespaceAndPath(MOD_ID, "respiration_ticks"),
        Codec.INT,
    )

    fun Entity.getRespirationTicks(): Int {
        return getAttached(RESPIRATION_TICKS) ?: 0
    }

    fun Entity.setRespirationTicks(ticks: Int) {
        setAttached(RESPIRATION_TICKS, ticks)
    }

    fun Entity.canDealDamage(): Boolean {
        if (this !is net.minecraft.world.entity.LivingEntity) return false
        
        // Hostile mobs
        if (this is net.minecraft.world.entity.monster.Enemy) return true
        
        // Neutral mobs (Wolves, Bees, Iron Golems, etc.)
        if (this is net.minecraft.world.entity.NeutralMob) return true
        
        // Explicit ranged shooters
        if (this.type in EnchantmentPool.RANGED_SHOOTER_TYPES) return true
        
        // Other specific animals that can attack
        if (this.type == net.minecraft.world.entity.EntityTypes.CAT) return true
        if (this.type == net.minecraft.world.entity.EntityTypes.OCELOT) return true
        if (this.type == net.minecraft.world.entity.EntityTypes.FOX) return true
        if (this.type == net.minecraft.world.entity.EntityTypes.AXOLOTL) return true
        if (this.type == net.minecraft.world.entity.EntityTypes.PUFFERFISH) return true
        
        return false
    }
}
