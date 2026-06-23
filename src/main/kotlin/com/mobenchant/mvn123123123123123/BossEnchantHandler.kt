package com.mobenchant.mvn123123123123123

import com.mobenchant.mvn123123123123123.MobEnchantData.getMobEnchantments
import com.mobenchant.mvn123123123123123.MobEnchantData.setMobEnchantments
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import java.util.UUID

object BossEnchantHandler {

    val UNMODIFIED_ENCHANTS = listOf(
        "protection", "projectile_protection", "fire_protection", "blast_protection", "thorns",
        "soul_speed", "sharpness", "smite", "knockback", "mending", "unbreaking"
    )

    val activeBosses = mutableSetOf<UUID>()
    val bossGuardsKilled = mutableMapOf<UUID, Int>()

    fun isBoss(entity: Mob): Boolean {
        return entity.type == EntityTypes.ENDER_DRAGON || entity.type == EntityTypes.WITHER
    }

    fun onBossSpawn(boss: Mob) {
        val bossEnchants = rollBossEnchantments(6, fromPool = UNMODIFIED_ENCHANTS)

        boss.setMobEnchantments(bossEnchants)
        NameplateManager.setEnchantedNameplate(boss, bossEnchants)
        EnchantmentEffects.applyPassiveBoosts(boss, bossEnchants)
        
        activeBosses.add(boss.uuid)
        bossGuardsKilled[boss.uuid] = 0

        // Schedule the initial 2 guards
        BossGuardManager.spawnGuards(boss, 2, falling = false)
    }

    fun rollBossEnchantments(count: Int, fromPool: List<String>): List<MobEnchantment> {
        val availableEnchants = EnchantmentPool.ALL.filter { it.id in fromPool }.toMutableList()
        val picked = mutableListOf<MobEnchantment>()

        for (i in 0 until count) {
            if (availableEnchants.isEmpty()) break
            val chosen = availableEnchants.random()
            availableEnchants.removeIf { it.id == chosen.id }

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

    fun tick(server: MinecraftServer) {
        val bossesToRemove = mutableListOf<UUID>()
        
        for (uuid in activeBosses) {
            var found = false
            for (world in server.allLevels) {
                val boss = world.getEntity(uuid) as? Mob
                if (boss != null) {
                    found = true
                    if (!boss.isAlive) {
                        bossesToRemove.add(uuid)
                        break
                    }
                    
                    val enchants = boss.getMobEnchantments() ?: emptyList()
                    val hasFeatherFalling = enchants.any { it.id == "feather_falling" }
                    val hasRainEffect = enchants.any { it.id in listOf("respiration", "aqua_affinity", "depth_strider", "frost_walker") }
                    
                    // Repeating feather falling guards
                    if (hasFeatherFalling && server.tickCount % 600 == 0) {
                        // Spawn 4 falling guards
                        BossGuardManager.spawnGuards(boss, 4, falling = true)
                    }
                    
                    // Rain particles around nearby players
                    if (hasRainEffect) {
                        val players = world.getEntitiesOfClass(Player::class.java, boss.boundingBox.inflate(48.0))
                        for (player in players) {
                            if (!player.isSpectator) {
                                // Spawn dense local rain particles to simulate heavy rain without lagging server globally
                                for (i in 0 until 50) {
                                    val px = player.x + (world.random.nextDouble() - 0.5) * 20.0
                                    val py = player.y + 10.0 + world.random.nextDouble() * 10.0
                                    val pz = player.z + (world.random.nextDouble() - 0.5) * 20.0
                                    world.sendParticles(ParticleTypes.RAIN, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0)
                                    world.sendParticles(ParticleTypes.SPLASH, px, player.y, pz, 1, 0.0, 0.0, 0.0, 0.0)
                                }
                            }
                        }
                    }
                    
                    break
                }
            }
            if (!found) {
                // Keep it if unloaded
            }
        }
        
        activeBosses.removeAll(bossesToRemove)
        
        // Find newly loaded bosses
        for (world in server.allLevels) {
            for (entity in world.allEntities) {
                if (entity is Mob && isBoss(entity) && entity.isAlive && entity.getMobEnchantments() != null) {
                    activeBosses.add(entity.uuid)
                    if (!bossGuardsKilled.containsKey(entity.uuid)) {
                        bossGuardsKilled[entity.uuid] = 0
                    }
                }
            }
        }
    }
}
