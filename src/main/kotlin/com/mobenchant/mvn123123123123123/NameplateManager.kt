package com.mobenchant.mvn123123123123123

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.entity.Entity

// ============================================================================
// Nametag display utilities for enchanted mobs
// ============================================================================
object NameplateManager {

    private val ROMAN_NUMERALS = arrayOf("I", "II", "III", "IV", "V")

    /**
     * Convert a level number (1–5) to a Roman numeral string.
     */
    fun toRoman(num: Int): String {
        return if (num in 1..5) ROMAN_NUMERALS[num - 1] else num.toString()
    }

    /**
     * Make a pretty display name from an enchantment ID.
     * e.g. "fire_protection" → "Fire Protection"
     */
    fun prettyName(id: String): String {
        return id.split("_").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Get the [ChatFormatting] color for an enchantment category.
     */
    private fun categoryColor(category: String): ChatFormatting {
        return when (category) {
            "offensive" -> ChatFormatting.RED
            "defensive" -> ChatFormatting.AQUA
            "passive"   -> ChatFormatting.GREEN
            "flavor"    -> ChatFormatting.YELLOW
            else        -> ChatFormatting.GRAY
        }
    }

    /**
     * Build a styled [Component] for a single enchantment entry.
     */
    private fun enchantComponent(enchant: MobEnchantment): MutableComponent {
        val name = prettyName(enchant.id)
        val levelStr = if (enchant.maxLevel > 1) " ${toRoman(enchant.level)}" else ""
        return Component.literal("$name$levelStr").withStyle(categoryColor(enchant.category))
    }

    /**
     * Set the nameplate of the entity to display its enchantments.
     * Java Edition doesn't support newlines in custom names, so we use a
     * single line: ✦ Enchanted | Sharpness V | Protection IV
     */
    fun setEnchantedNameplate(entity: Entity, enchantList: List<MobEnchantment>) {
        try {
            val separator = Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY)

            var display: MutableComponent? = null
            enchantList.forEachIndexed { index, enchant ->
                if (display == null) {
                    display = enchantComponent(enchant)
                } else {
                    display = display!!.append(separator).append(enchantComponent(enchant))
                }
            }

            // Hide default nameplate since we use TextDisplay now
            entity.isCustomNameVisible = false
            
            // Add or update the TextDisplay passenger
            var headerDisplay = entity.passengers.find { 
                it is net.minecraft.world.entity.Display.TextDisplay && it.entityTags().contains("mobenchant:header") 
            } as? net.minecraft.world.entity.Display.TextDisplay
            
            if (headerDisplay == null && !entity.level().isClientSide) {
                headerDisplay = net.minecraft.world.entity.Display.TextDisplay(net.minecraft.world.entity.EntityType.TEXT_DISPLAY, entity.level())
                headerDisplay.addTag("mobenchant:header")
                headerDisplay.setPos(entity.x, entity.y, entity.z)
                entity.level().addFreshEntity(headerDisplay)
                headerDisplay.startRiding(entity)
            }
            
            if (headerDisplay != null) {
                val hpString = if (entity is net.minecraft.world.entity.LivingEntity) {
                    val hp = kotlin.math.ceil(entity.health.toDouble()).toInt()
                    val maxHp = kotlin.math.ceil(entity.maxHealth.toDouble()).toInt()
                    " [❤ $hp/$maxHp]"
                } else ""
                
                val fullText = Component.literal("✦ Enchanted$hpString\n").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                if (display != null) {
                    fullText.append(display!!)
                }
                
                headerDisplay.text = fullText
                headerDisplay.billboardConstraints = net.minecraft.world.entity.Display.BillboardConstraints.CENTER
                headerDisplay.isInvisible = entity.isInvisible
                
                // Safe transformation with non-null scale and rotation
                val offset = org.joml.Vector3f(0f, 0.4f, 0f)
                val scale = org.joml.Vector3f(1f, 1f, 1f)
                val rot = org.joml.Quaternionf()
                val transformation = com.mojang.math.Transformation(offset, rot, scale, rot)
                headerDisplay.setTransformation(transformation)
            }
            
        } catch (_: Exception) {
            // Entity may have been removed
        }
    }

    /**
     * Sets a simple health nameplate for non-enchanted mobs.
     */
    fun updateHealthNameplate(entity: net.minecraft.world.entity.Mob) {
        try {
            val hp = kotlin.math.ceil(entity.health.toDouble()).toInt()
            val maxHp = kotlin.math.ceil(entity.maxHealth.toDouble()).toInt()
            val hpString = " [❤ $hp/$maxHp]"
            
            // Clean up any left-over TextDisplay passenger from when it was enchanted
            entity.passengers.forEach {
                if (it is net.minecraft.world.entity.Display.TextDisplay && it.entityTags().contains("mobenchant:header")) {
                    it.discard()
                }
            }
            val nameText = entity.type.description.copy().withStyle(ChatFormatting.WHITE)
            nameText.append(Component.literal(hpString).withStyle(ChatFormatting.RED))
            
            entity.customName = nameText
            entity.isCustomNameVisible = !entity.isInvisible
        } catch (_: Exception) {
            // Ignored
        }
    }

    /**
     * Build a list of enchantment names as a comma-separated plain string.
     * Used for chat messages.
     */
    fun enchantListString(enchants: List<MobEnchantment>): String {
        return enchants.joinToString(", ") { e ->
            val lvl = if (e.maxLevel > 1) " ${toRoman(e.level)}" else ""
            "${prettyName(e.id)}$lvl"
        }
    }
}
