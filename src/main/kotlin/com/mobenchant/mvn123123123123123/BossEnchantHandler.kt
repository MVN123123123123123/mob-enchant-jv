package com.mobenchant.mvn123123123123123

import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.EntityTypes
import com.mobenchant.mvn123123123123123.MobEnchantData.setMobEnchantments

object BossEnchantHandler {

    val UNMODIFIED_ENCHANTS = listOf(
        "protection", "projectile_protection", "fire_protection", "blast_protection", "thorns",
        "soul_speed", "sharpness", "smite", "knockback", "mending", "unbreaking"
    )

    /**
     * Checks if the entity is one of the supported bosses.
     */
    fun isBoss(entity: Mob): Boolean {
        return entity.type == EntityTypes.ENDER_DRAGON || entity.type == EntityTypes.WITHER
    }

    /**
     * Handles boss spawn, applying 6 random enchants chosen from the restricted pool,
     * and applying boosts/nameplates.
     */
    fun onBossSpawn(boss: Mob) {
        val bossEnchants = rollBossEnchantments(6, fromPool = UNMODIFIED_ENCHANTS)

        boss.setMobEnchantments(bossEnchants)
        NameplateManager.setEnchantedNameplate(boss, bossEnchants)
        EnchantmentEffects.applyPassiveBoosts(boss, bossEnchants)
        
        // Schedule the boss guards
        BossGuardManager.scheduleGuards(boss)
    }

    /**
     * Pick [count] unique random enchantments from the restricted pool, all at max level.
     */
    fun rollBossEnchantments(count: Int, fromPool: List<String>): List<MobEnchantment> {
        val availableEnchants = EnchantmentPool.ALL.filter { it.id in fromPool }.toMutableList()
        val picked = mutableListOf<MobEnchantment>()

        for (i in 0 until count) {
            if (availableEnchants.isEmpty()) break
            val chosen = availableEnchants.random()
            availableEnchants.remove(chosen)

            picked.add(
                MobEnchantment(
                    id = chosen.id,
                    level = chosen.maxLevel,
                    maxLevel = chosen.maxLevel,
                    category = chosen.category
                )
            )
        }
        return picked
    }
}
