package com.mobenchant.mvn123123123123123

import com.mobenchant.mvn123123123123123.MobEnchantData.getMobEnchantments
import com.mobenchant.mvn123123123123123.MobEnchantData.setMobEnchantments
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.protocol.game.ClientboundGameEventPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import java.util.UUID

object BossEnchantHandler {

    val UNMODIFIED_ENCHANTS = listOf(
        "protection", "projectile_protection", "fire_protection", "blast_protection", "thorns",
        "soul_speed", "sharpness", "smite", "knockback", "mending", "unbreaking",
        "feather_falling", "respiration", "aqua_affinity", "depth_strider", "frost_walker"
    )

    val activeBosses = mutableSetOf<UUID>()
    val bossGuardsKilled = mutableMapOf<UUID, Int>()
    val playersInFakeRain = mutableSetOf<UUID>()

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

        // Schedule the initial 2 guards to avoid ConcurrentModificationException during ENTITY_LOAD
        MobEnchant.scheduleTask(1) {
            BossGuardManager.spawnGuards(boss, 2, falling = false)
        }
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
        val currentPlayersInRain = mutableSetOf<UUID>()
        
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
                    val featherFallingEnchant = enchants.find { it.id == "feather_falling" }
                    val hasRainEffect = enchants.any { it.id in listOf("respiration", "aqua_affinity", "depth_strider", "frost_walker") }
                    
                    // Repeating feather falling guards
                    if (featherFallingEnchant != null && server.tickCount % 1200 == 0) {
                        // Spawn falling guards equal to the enchantment level (normal)
                        BossGuardManager.spawnGuards(boss, featherFallingEnchant.level, falling = true, enchanted = false)
                        // Spawn 2 enchanted falling guards
                        BossGuardManager.spawnGuards(boss, 2, falling = true, enchanted = true)
                    }
                    
                    // Rain particles around nearby players
                    if (hasRainEffect) {
                        val rainSources = mutableListOf<Mob>(boss)
                        for (guardId in BossGuardManager.activeGuards) {
                            val guard = server.allLevels.mapNotNull { it.getEntity(guardId) as? Mob }.firstOrNull()
                            if (guard != null && guard.entityTags().contains("boss_${boss.uuid}")) {
                                rainSources.add(guard)
                            }
                        }
                        
                        for (source in rainSources) {
                            val players = source.level().getEntitiesOfClass(Player::class.java, source.boundingBox.inflate(48.0))
                            for (player in players) {
                                if (!player.isSpectator) {
                                    currentPlayersInRain.add(player.uuid)
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
        
        // Handle fake rain packets and manual particles
        for (uuid in currentPlayersInRain) {
            val player = server.playerList.getPlayer(uuid)
            if (player != null) {
                if (!playersInFakeRain.contains(uuid)) {
                    player.connection.send(ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0f))
                    player.connection.send(ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 1.0f))
                    playersInFakeRain.add(uuid)
                }
                
                // Spawn manual particles because biomes like The End don't render normal rain
                val sl = player.level() as? net.minecraft.server.level.ServerLevel
                if (sl != null) {
                    for (i in 0..150) {
                        val dx = (sl.random.nextDouble() - 0.5) * 30.0
                        val dz = (sl.random.nextDouble() - 0.5) * 30.0
                        // Spawn high above the player (y+10 to y+20) so they naturally fall down
                        val dy = sl.random.nextDouble() * 5.0 + 10.0
                        sl.sendParticles(ParticleTypes.RAIN, player.x + dx, player.y + dy, player.z + dz, 1, 0.0, 0.0, 0.0, 0.0)
                    }
                }
            }
        }
        
        val toRemoveFromRain = mutableListOf<UUID>()
        for (uuid in playersInFakeRain) {
            if (!currentPlayersInRain.contains(uuid)) {
                val player = server.playerList.getPlayer(uuid)
                if (player != null) {
                    if (player.level().isRaining()) {
                        player.connection.send(ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0.0f))
                        player.connection.send(ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, player.level().getRainLevel(1.0f)))
                    } else {
                        player.connection.send(ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0f))
                        player.connection.send(ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0.0f))
                    }
                }
                toRemoveFromRain.add(uuid)
            }
        }
        playersInFakeRain.removeAll(toRemoveFromRain)
        
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
