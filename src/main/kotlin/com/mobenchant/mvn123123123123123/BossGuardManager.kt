package com.mobenchant.mvn123123123123123

import com.mobenchant.mvn123123123123123.MobEnchantData.setMobEnchantments
import com.mobenchant.mvn123123123123123.MobEnchantData.getMobEnchantments
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

object BossGuardManager {

    // Track guards by their UUID
    private val activeGuards = mutableSetOf<UUID>()

    fun spawnGuards(boss: Mob, count: Int, falling: Boolean) {
        val world = boss.level() as? ServerLevel ?: return
        if (!boss.isAlive) return // If boss is already dead, don't spawn guards

        val guardType = when (boss.type) {
            EntityTypes.ENDER_DRAGON -> EntityTypes.ENDERMAN
            EntityTypes.WITHER -> EntityTypes.WITHER_SKELETON
            else -> null
        }

        if (guardType != null) {
            for (i in 0 until count) {
                val guard = guardType.create(world, EntitySpawnReason.COMMAND) as? Mob ?: continue
                
                // Position around the boss
                val offsetX = (world.random.nextDouble() - 0.5) * 4.0
                val offsetZ = (world.random.nextDouble() - 0.5) * 4.0
                if (falling) {
                    guard.setPos(boss.x + offsetX, boss.y + 50.0, boss.z + offsetZ)
                    guard.addTag("falling_guard")
                    guard.isNoAi = true
                } else {
                    guard.setPos(boss.x + offsetX, boss.y + 1.0, boss.z + offsetZ)
                }

                // Equip Elytra and Rockets
                guard.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.ELYTRA))
                guard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.FIREWORK_ROCKET))

                // Tag and track
                guard.addTag("boss_guard")
                guard.addTag("boss_${boss.uuid}")
                
                // Give them the boss's exact enchants
                val bossEnchants = boss.getMobEnchantments() ?: emptyList()
                guard.setMobEnchantments(bossEnchants)
                NameplateManager.setEnchantedNameplate(guard, bossEnchants)

                world.addFreshEntity(guard)
                activeGuards.add(guard.uuid)
                
                // Spawn particles/sound for their arrival
                world.sendParticles(ParticleTypes.EXPLOSION, guard.x, guard.y + 1.0, guard.z, 5, 0.5, 0.5, 0.5, 0.1)
                world.playSound(null, guard.x, guard.y, guard.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.5f, 1.0f)
            }
        }
    }

    fun tick(server: MinecraftServer) {
        val guardsToRemove = mutableListOf<UUID>()

        for (uuid in activeGuards) {
            var found = false
            for (world in server.allLevels) {
                val guard = world.getEntity(uuid) as? Mob
                if (guard != null) {
                    found = true
                    if (!guard.isAlive) {
                        guardsToRemove.add(uuid)
                        break
                    }

                    // Clear invalid targets
                    if (guard.target is net.minecraft.world.entity.player.Player) {
                        val p = guard.target as net.minecraft.world.entity.player.Player
                        if (p.isCreative || p.isSpectator || !p.isAlive) {
                            guard.target = null
                        }
                    }
                    
                    // Falling guards logic
                    if (guard.entityTags().contains("falling_guard")) {
                        if (guard.onGround()) {
                            guard.removeTag("falling_guard")
                            guard.isNoAi = false
                        } else {
                            continue // Wait until they touch the ground
                        }
                    }

                    // Attempt to track player
                    val target = guard.target ?: world.players().minByOrNull { it.distanceToSqr(guard) }?.takeIf { it.distanceTo(guard) < 200.0 && it.isAlive && !it.isSpectator && !it.isCreative }
                    
                    if (target != null && target.isAlive) {
                        if (guard.target != target) guard.target = target

                        val dist = guard.distanceTo(target)
                        val dir = target.position().subtract(guard.position())
                        val length = dir.length()
                        if (length > 0) {
                            val normalizedDir = dir.normalize()

                            // Set fall flying visually if possible (reflection)
                            try {
                                val setSharedFlagMethod = net.minecraft.world.entity.Entity::class.java.getDeclaredMethod("setSharedFlag", Int::class.java, Boolean::class.java)
                                setSharedFlagMethod.isAccessible = true
                                setSharedFlagMethod.invoke(guard, 7, true)
                            } catch (_: Exception) { }
                            
                            // Calculate dynamic teleport distance based on server simulation/view distance
                            val simDistance = try {
                                server.playerList.simulationDistance
                            } catch (_: Throwable) {
                                server.playerList.viewDistance
                            }
                            val calculatedThreshold = (simDistance * 16.0) - 16.0 // 1 chunk buffer
                            val teleportThreshold = if (calculatedThreshold < 64.0) 64.0 else calculatedThreshold

                            // Depth strider bonus
                            val hasDepthStrider = guard.getMobEnchantments()?.any { it.id == "depth_strider" } ?: false
                            val baseSpeedMult = if (hasDepthStrider) 1.2 else 1.0

                            // Distance based logic
                            if (dist > teleportThreshold) {
                                // Teleport if verge of unloaded
                                guard.teleportTo(target.x, target.y + 10.0, target.z)
                                world.playSound(null, guard.x, guard.y, guard.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f)
                            } else if (dist > 15.0) {
                                // Flight and rocket logic
                                val speedMult = (if (dist > 40.0) 1.5 else 0.8) * baseSpeedMult
                                val currentVel = guard.deltaMovement
                                guard.deltaMovement = currentVel.add(normalizedDir.x * 0.1 * speedMult, (normalizedDir.y * 0.1 + 0.05) * speedMult, normalizedDir.z * 0.1 * speedMult)
                                
                                // Spam rocket if > 40, moderate if 15-40
                                val tickChance = if (dist > 40.0) 10 else 40
                                if (server.tickCount % tickChance == 0) {
                                    world.playSound(null, guard.x, guard.y, guard.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.0f, 1.0f)
                                    world.sendParticles(ParticleTypes.FIREWORK, guard.x, guard.y, guard.z, 5, 0.2, 0.2, 0.2, 0.05)
                                    // Additional boost
                                    guard.deltaMovement = guard.deltaMovement.add(normalizedDir.scale(0.5 * speedMult))
                                }
                            } else {
                                // Close range: normal elytra glide/flight towards them, no rockets
                                guard.deltaMovement = guard.deltaMovement.add(normalizedDir.x * 0.05 * baseSpeedMult, normalizedDir.y * 0.05 * baseSpeedMult, normalizedDir.z * 0.05 * baseSpeedMult)
                            }
                        }
                    } else {
                        // No target, disable visual flying
                        try {
                            val setSharedFlagMethod = net.minecraft.world.entity.Entity::class.java.getDeclaredMethod("setSharedFlag", Int::class.java, Boolean::class.java)
                            setSharedFlagMethod.isAccessible = true
                            setSharedFlagMethod.invoke(guard, 7, false)
                        } catch (_: Exception) { }
                    }
                    break
                }
            }
            if (!found) {
                // Not found in any loaded world, could be unloaded.
                // We leave it in activeGuards to reconnect when it loads,
                // but since it's unloaded we can't actively track or tick it.
                // However, since it teleports if > 100 blocks, it shouldn't get unloaded often unless the player uses a portal.
            }
        }

        activeGuards.removeAll(guardsToRemove)
        
        // Find untracked guards that might have loaded
        for (world in server.allLevels) {
            for (entity in world.allEntities) {
                if (entity is Mob && entity.entityTags().contains("boss_guard") && entity.isAlive) {
                    if (!activeGuards.contains(entity.uuid)) {
                        activeGuards.add(entity.uuid)
                    }
                }
            }
        }
    }
}
