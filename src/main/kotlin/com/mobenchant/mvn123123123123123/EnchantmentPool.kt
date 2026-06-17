package com.mobenchant.mvn123123123123123

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

// ============================================================================
// Enchantment definition used across the mod
// ============================================================================
data class EnchantDef(val id: String, val maxLevel: Int, val category: String)

// Generic weighted entry for random selection
data class WeightedEntry<T>(val value: T, val weight: Int)

// ============================================================================
// ENCHANTMENT POOL — All positive vanilla enchantments (no curses)
// ============================================================================
object EnchantmentPool {

    val ALL: List<EnchantDef> = listOf(
        // --- Defensive ---
        EnchantDef("protection", 4, "defensive"),
        EnchantDef("projectile_protection", 4, "defensive"),
        EnchantDef("fire_protection", 4, "defensive"),
        EnchantDef("blast_protection", 4, "defensive"),
        EnchantDef("feather_falling", 4, "defensive"),
        EnchantDef("thorns", 3, "defensive"),
        EnchantDef("respiration", 3, "passive"),
        EnchantDef("aqua_affinity", 1, "offensive"),

        // --- Movement ---
        EnchantDef("depth_strider", 3, "passive"),
        EnchantDef("frost_walker", 2, "passive"),
        EnchantDef("soul_speed", 3, "passive"),

        // --- Offensive ---
        EnchantDef("sharpness", 5, "offensive"),
        EnchantDef("smite", 5, "offensive"),
        EnchantDef("bane_of_arthropods", 5, "offensive"),
        EnchantDef("knockback", 2, "offensive"),
        EnchantDef("fire_aspect", 2, "offensive"),
        EnchantDef("looting", 3, "flavor"),

        // --- Tool ---
        EnchantDef("efficiency", 5, "flavor"),
        EnchantDef("fortune", 3, "flavor"),
        EnchantDef("silk_touch", 1, "flavor"),
        EnchantDef("unbreaking", 3, "defensive"),
        EnchantDef("mending", 1, "passive"),

        // --- Ranged ---
        EnchantDef("power", 5, "offensive"),
        EnchantDef("punch", 2, "offensive"),
        EnchantDef("flame", 1, "offensive"),
        EnchantDef("infinity", 1, "defensive"),
        EnchantDef("multishot", 1, "offensive"),
        EnchantDef("piercing", 4, "offensive"),
        EnchantDef("quick_charge", 3, "offensive"),

        // --- Trident ---
        EnchantDef("impaling", 5, "offensive"),
        EnchantDef("riptide", 3, "flavor"),
        EnchantDef("loyalty", 3, "flavor"),
        EnchantDef("channeling", 1, "offensive"),

        // --- Fishing ---
        EnchantDef("luck_of_the_sea", 3, "flavor"),
        EnchantDef("lure", 3, "flavor"),

        // --- New Additions ---
        EnchantDef("wind_burst", 3, "offensive"),
        EnchantDef("density", 5, "offensive"),
        EnchantDef("breach", 4, "offensive"),
        EnchantDef("sweeping_edge", 3, "offensive"),
        EnchantDef("swift_sneak", 3, "passive"),
        EnchantDef("curse_of_vanishing", 1, "offensive"),
        EnchantDef("curse_of_binding", 1, "offensive"),
    )

    // ========================================================================
    // LEVEL WEIGHT TABLE — Higher levels are rarer
    // Index 0 = level 1, index 1 = level 2, etc. (Percentages: 35%, 25%, 18%, 12%, 10%)
    // ========================================================================
    val LEVEL_WEIGHTS = intArrayOf(35, 25, 18, 12, 10)

    // ========================================================================
    // ENCHANT COUNT WEIGHTS — How many enchantments the mob gets
    // 1 enchant: 40%, 2: 30%, 3: 20%, 4: 7%, 5: 3%
    // ========================================================================
    val COUNT_WEIGHTS: List<WeightedEntry<Int>> = listOf(
        WeightedEntry(1, 40),
        WeightedEntry(2, 30),
        WeightedEntry(3, 20),
        WeightedEntry(4, 7),
        WeightedEntry(5, 3),
    )

    // ========================================================================
    // ENTITY TYPE SETS — for targeting-specific enchantments
    // ========================================================================
    val UNDEAD_TYPES: Set<EntityType<*>> = setOf(
        EntityTypes.ZOMBIE, EntityTypes.ZOMBIE_VILLAGER, EntityTypes.HUSK,
        EntityTypes.DROWNED, EntityTypes.SKELETON, EntityTypes.STRAY,
        EntityTypes.WITHER_SKELETON, EntityTypes.ZOMBIFIED_PIGLIN,
        EntityTypes.PHANTOM, EntityTypes.WITHER, EntityTypes.ZOGLIN,
        EntityTypes.SKELETON_HORSE, EntityTypes.ZOMBIE_HORSE, EntityTypes.BOGGED,
    )

    val ARTHROPOD_TYPES: Set<EntityType<*>> = setOf(
        EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER, EntityTypes.SILVERFISH,
        EntityTypes.ENDERMITE, EntityTypes.BEE,
    )

    val AQUATIC_TYPES: Set<EntityType<*>> = setOf(
        EntityTypes.COD, EntityTypes.SALMON, EntityTypes.TROPICAL_FISH,
        EntityTypes.PUFFERFISH, EntityTypes.SQUID, EntityTypes.GLOW_SQUID,
        EntityTypes.DOLPHIN, EntityTypes.TURTLE, EntityTypes.AXOLOTL,
        EntityTypes.GUARDIAN, EntityTypes.ELDER_GUARDIAN, EntityTypes.DROWNED,
    )

    // ========================================================================
    // Ranged mob types eligible for multishot projectile duplication
    // ========================================================================
    val RANGED_SHOOTER_TYPES: Set<EntityType<*>> = setOf(
        EntityTypes.SKELETON, EntityTypes.STRAY, EntityTypes.BOGGED,
        EntityTypes.PIGLIN, EntityTypes.WITCH,
        EntityTypes.ENDER_DRAGON, EntityTypes.WITHER,
    )

    // ========================================================================
    // BONUS LOOT TABLE — Items that can drop from enchanted mobs on death
    // ========================================================================
    val BONUS_LOOT: List<WeightedEntry<Item>> = listOf(
        WeightedEntry(Items.EMERALD, 400),
        WeightedEntry(Items.IRON_INGOT, 290),
        WeightedEntry(Items.GOLD_INGOT, 290),
        WeightedEntry(Items.DIAMOND, 19),
        WeightedEntry(Items.NETHERITE_SCRAP, 1),
    )

    // ========================================================================
    // CONSTANTS
    // ========================================================================
    const val ENCHANT_CHANCE = 6          // 1-in-N chance to become enchanted
    const val MAX_ENCHANTS = 5            // Maximum enchantments per mob
    const val MOB_SEARCH_RADIUS = 5.0     // Command target search radius (blocks)
}
