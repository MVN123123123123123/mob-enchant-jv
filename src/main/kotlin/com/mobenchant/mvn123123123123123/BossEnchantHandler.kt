package com.mobenchant.mvn123123123123123

import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.EntityTypes
import com.mobenchant.mvn123123123123123.MobEnchantData.setMobEnchantments

object BossEnchantHandler {

    /**
     * Checks if the entity is one of the supported bosses.
     */
    fun isBoss(entity: Mob): Boolean {
        return entity.type == EntityTypes.ENDER_DRAGON || entity.type == EntityTypes.WITHER
    }

    /**
     * Handles boss spawn, rolling 5 max-level enchants and applying boosts/nameplates.
     */
    fun onBossSpawn(boss: Mob) {
        val bossEnchants = rollBossEnchantments(5)

        boss.setMobEnchantments(bossEnchants)
        NameplateManager.setEnchantedNameplate(boss, bossEnchants)
        EnchantmentEffects.applyPassiveBoosts(boss, bossEnchants)
    }

    /**
     * Pick [count] unique random enchantments from the pool, all at max level.
     */
    fun rollBossEnchantments(count: Int): List<MobEnchantment> {
        val pool = EnchantmentPool.ALL.toMutableList()
        val picked = mutableListOf<MobEnchantment>()

        for (i in 0 until count) {
            if (pool.isEmpty()) break
            val chosen = pool.random()
            pool.remove(chosen)

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
