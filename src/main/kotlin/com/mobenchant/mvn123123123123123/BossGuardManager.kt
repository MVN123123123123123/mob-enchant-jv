package com.mobenchant.mvn123123123123123

import com.mobenchant.mvn123123123123123.MobEnchantData.setMobEnchantments
import com.mobenchant.mvn123123123123123.MobEnchantData.getMobEnchantments
import com.mobenchant.mvn123123123123123.MobEnchantData.markAsRolled
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
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BossGuardManager {

    val DEBUG_HIGHLIGHT_GUARDS get() = MobEnchantConfig.debugEnabled

    enum class GuardState { CIRCLING, CHARGING }
    private val guardStates = ConcurrentHashMap<UUID, GuardState>()
    val guardCircleAngles = ConcurrentHashMap<UUID, Double>()
    val guardTargetRadii = ConcurrentHashMap<UUID, Double>()
    val guardTargetHeights = ConcurrentHashMap<UUID, Double>()
    val guardTargetSpeeds = ConcurrentHashMap<UUID, Double>()
    val guardAttackCooldowns = ConcurrentHashMap<UUID, Int>()

    // Track guards by their UUID
    private val activeGuards = mutableSetOf<UUID>()

    fun spawnGuards(boss: Mob, count: Int, falling: Boolean, enchanted: Boolean = !falling) {
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
                } else {
                    guard.setPos(boss.x + offsetX, boss.y + 1.0, boss.z + offsetZ)
                }

                // Equip Elytra and Rockets
                guard.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.ELYTRA))
                guard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack(Items.FIREWORK_ROCKET))

                // Tag and track
                guard.addTag("boss_guard")
                guard.addTag("boss_${boss.uuid}")
                
                // Mark as rolled so ENTITY_LOAD doesn't overwrite it with random enchants
                guard.markAsRolled()

                if (enchanted) {
                    // Initial guards get the boss's exact enchants
                    val bossEnchants = boss.getMobEnchantments() ?: emptyList()
                    guard.setMobEnchantments(bossEnchants)

                    world.addFreshEntity(guard)
                    
                    // Add nameplate and apply passive boosts after adding to world so passengers apply correctly
                    NameplateManager.setEnchantedNameplate(guard, bossEnchants)
                    EnchantmentEffects.applyPassiveBoosts(guard, bossEnchants)
                } else {
                    // Falling guards get NO enchants, just the standard health nameplate
                    guard.setMobEnchantments(emptyList())
                    world.addFreshEntity(guard)
                    NameplateManager.updateHealthNameplate(guard)
                }
                activeGuards.add(guard.uuid)

                if (DEBUG_HIGHLIGHT_GUARDS) {
                    guard.addEffect(MobEffectInstance(MobEffects.GLOWING, 20000000, 0, false, false))
                }
                
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

                    if (DEBUG_HIGHLIGHT_GUARDS) {
                        if (!guard.hasEffect(MobEffects.GLOWING)) {
                            guard.addEffect(MobEffectInstance(MobEffects.GLOWING, 20000000, 0, false, false))
                        }
                    } else {
                        if (guard.hasEffect(MobEffects.GLOWING)) {
                            guard.removeEffect(MobEffects.GLOWING)
                        }
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
                        } else {
                            guard.fallDistance = 0.0 // Prevent taking fall damage when they land
                            
                            // Spawn bright meteor-like trail particles
                            world.sendParticles(ParticleTypes.END_ROD, guard.x, guard.y + 1.0, guard.z, 2, 0.2, 0.5, 0.2, 0.0)
                            world.sendParticles(ParticleTypes.FLAME, guard.x, guard.y + 1.0, guard.z, 5, 0.3, 0.8, 0.3, 0.05)
                            world.sendParticles(ParticleTypes.LAVA, guard.x, guard.y + 1.0, guard.z, 1, 0.2, 0.2, 0.2, 0.0)
                            
                            continue // Wait until they touch the ground
                        }
                    }
                    
                    // Reset fall distance unconditionally for all flying guards to prevent them taking kinetic/fall damage when swooping
                    guard.fallDistance = 0.0

                    // Attempt to track player
                    val target = guard.target ?: world.players().minByOrNull { it.distanceToSqr(guard) }?.takeIf { it.distanceTo(guard) < 200.0 && it.isAlive && !it.isSpectator && !it.isCreative }
                    
                    if (target != null && target.isAlive) {
                        if (guard.target != target) guard.target = target

                        val dist = guard.distanceTo(target)
                        val dir = target.position().subtract(guard.position())
                        val length = dir.length()
                        if (length > 0) {
                            // Visual flying using fall flying flag (7) - ALWAYS when targeting
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

                            val state = guardStates.getOrDefault(uuid, GuardState.CIRCLING)
                            var angle = guardCircleAngles.getOrDefault(uuid, world.random.nextDouble() * Math.PI * 2)

                            // Distance based logic
                            if (dist > teleportThreshold) {
                                // Teleport if verge of unloaded
                                guard.teleportTo(target.x, target.y + 20.0, target.z)
                                world.playSound(null, guard.x, guard.y, guard.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f)
                                guardStates[uuid] = GuardState.CIRCLING
                                guardAttackCooldowns[uuid] = world.random.nextInt(301) + 300 // 15-30s cooldown
                            } else {
                                if (state == GuardState.CIRCLING) {
                                    var attackCooldown = guardAttackCooldowns.getOrDefault(uuid, world.random.nextInt(301) + 300)
                                    attackCooldown--
                                    
                                    // Change radius, height, and speed periodically (e.g., every 5 seconds = 100 ticks)
                                    if (attackCooldown % 100 == 0) {
                                        guardTargetRadii[uuid] = 15.0 + world.random.nextDouble() * 30.0 // 15 to 45 blocks horizontal
                                        guardTargetHeights[uuid] = 10.0 + world.random.nextDouble() * 20.0 // 10 to 30 blocks vertical
                                        guardTargetSpeeds[uuid] = 0.5 + world.random.nextDouble() * 1.5 // 0.5x to 2.0x speed multiplier
                                    }
                                    
                                    val radius = guardTargetRadii.getOrDefault(uuid, 30.0)
                                    val height = guardTargetHeights.getOrDefault(uuid, 20.0)
                                    val dynamicSpeedMult = guardTargetSpeeds.getOrDefault(uuid, 1.0)
                                    val finalSpeedMult = baseSpeedMult * dynamicSpeedMult
                                    
                                    // Calculate target circle position, capping height so they don't fly over the build limit
                                    val targetX = target.x + kotlin.math.cos(angle) * radius
                                    val targetY = Math.min(target.y + height, world.maxY.toDouble() - 10.0)
                                    val targetZ = target.z + kotlin.math.sin(angle) * radius
                                    
                                    val circlePos = net.minecraft.world.phys.Vec3(targetX, targetY, targetZ)
                                    val circleDir = circlePos.subtract(guard.position())
                                    if (circleDir.length() > 0) {
                                        val normalizedCircleDir = circleDir.normalize()
                                        val currentVel = guard.deltaMovement
                                        // Smooth glide towards circle pos
                                        var newVel = currentVel.add(normalizedCircleDir.x * 0.15 * finalSpeedMult, normalizedCircleDir.y * 0.15 * finalSpeedMult, normalizedCircleDir.z * 0.15 * finalSpeedMult).scale(0.9)
                                        
                                        // Obstacle avoidance
                                        val lookVec = newVel.normalize()
                                        val endPos = guard.position().add(lookVec.scale(8.0))
                                        val hitResult = world.clip(net.minecraft.world.level.ClipContext(guard.position(), endPos, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, guard))
                                        if (hitResult.type == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                                            val dir = hitResult.direction
                                            val avoidanceForce = net.minecraft.world.phys.Vec3(dir.stepX.toDouble(), dir.stepY.toDouble() + 0.5, dir.stepZ.toDouble()).normalize()
                                            newVel = newVel.add(avoidanceForce.scale(0.85))
                                        }
                                        
                                        guard.deltaMovement = newVel
                                        
                                        // Look at velocity vector
                                        val horizontalDistance = kotlin.math.sqrt(newVel.x * newVel.x + newVel.z * newVel.z)
                                        val targetYRot = (kotlin.math.atan2(newVel.z, newVel.x) * (180.0 / Math.PI)).toFloat() - 90.0f
                                        val targetXRot = (-(kotlin.math.atan2(newVel.y, horizontalDistance) * (180.0 / Math.PI))).toFloat()
                                        
                                        guard.setYRot(targetYRot)
                                        guard.setXRot(targetXRot)
                                        guard.yBodyRot = targetYRot
                                        guard.yHeadRot = targetYRot
                                    }
                                    
                                    // Add firework rockets for circling so they look like they are flying
                                    if (server.tickCount % 5 == 0) {
                                        world.sendParticles(ParticleTypes.FIREWORK, guard.x, guard.y + 1.0, guard.z, 2, 0.1, 0.1, 0.1, 0.05)
                                    }
                                    if (server.tickCount % 40 == 0) {
                                        world.playSound(null, guard.x, guard.y, guard.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 0.3f, 1.0f)
                                    }
                                    
                                    // Slowly rotate the circle angle
                                    angle += 0.05 * finalSpeedMult
                                    guardCircleAngles[uuid] = angle
                                    
                                    // Switch to charging when cooldown reaches 0
                                    if (attackCooldown <= 0) {
                                        guardStates[uuid] = GuardState.CHARGING
                                        world.playSound(null, guard.x, guard.y, guard.z, SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 1.0f, 1.0f)
                                    } else {
                                        guardAttackCooldowns[uuid] = attackCooldown
                                    }
                                } else { // CHARGING
                                    val chargeDir = target.position().add(0.0, target.eyeHeight.toDouble() / 2.0, 0.0).subtract(guard.position())
                                    if (chargeDir.length() > 0) {
                                        val currentVel = guard.deltaMovement
                                        val normalizedChargeDir = chargeDir.normalize()
                                        val speedMult = 2.0 * baseSpeedMult // Super fast charge
                                        val targetVel = normalizedChargeDir.scale(speedMult)
                                        
                                        // Smoothly interpolate velocity to curve towards the player
                                        var newVel = currentVel.add(targetVel.subtract(currentVel).scale(0.25))
                                        
                                        // Obstacle avoidance
                                        val lookVec = newVel.normalize()
                                        val endPos = guard.position().add(lookVec.scale(8.0))
                                        val hitResult = world.clip(net.minecraft.world.level.ClipContext(guard.position(), endPos, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, guard))
                                        if (hitResult.type == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                                            val distToTarget = target.position().distanceTo(hitResult.location)
                                            if (distToTarget > 4.0) { // Ignore ground near the target so they can still hit
                                                val dir = hitResult.direction
                                                val avoidanceForce = net.minecraft.world.phys.Vec3(dir.stepX.toDouble(), dir.stepY.toDouble() + 0.5, dir.stepZ.toDouble()).normalize()
                                                newVel = newVel.add(avoidanceForce.scale(0.85))
                                            }
                                        }
                                        
                                        guard.deltaMovement = newVel
                                        
                                        // Look at the velocity vector for a smooth curve rotation
                                        val horizontalDistance = kotlin.math.sqrt(newVel.x * newVel.x + newVel.z * newVel.z)
                                        val targetYRot = (kotlin.math.atan2(newVel.z, newVel.x) * (180.0 / Math.PI)).toFloat() - 90.0f
                                        val targetXRot = (-(kotlin.math.atan2(newVel.y, horizontalDistance) * (180.0 / Math.PI))).toFloat()
                                        
                                        guard.setYRot(targetYRot)
                                        guard.setXRot(targetXRot)
                                        guard.yBodyRot = targetYRot
                                        guard.yHeadRot = targetYRot
                                        
                                        // Firework sounds and visual rocket trails
                                        if (server.tickCount % 2 == 0) {
                                            world.sendParticles(ParticleTypes.FIREWORK, guard.x, guard.y + 1.0, guard.z, 10, 0.2, 0.2, 0.2, 0.1)
                                        }
                                        if (server.tickCount % 10 == 0) {
                                            world.playSound(null, guard.x, guard.y, guard.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.HOSTILE, 1.0f, 1.0f)
                                        }
                                        
                                        // Melee attack hit detection
                                        if (guard.boundingBox.inflate(1.0).intersects(target.boundingBox)) {
                                            guard.doHurtTarget(world, target)
                                            // Bounce back to circling
                                            guardStates[uuid] = GuardState.CIRCLING
                                            guardAttackCooldowns[uuid] = world.random.nextInt(301) + 300 // Reset to 15-30s cooldown
                                            val vec = guard.position().subtract(target.position())
                                            val currentAngle = kotlin.math.atan2(vec.z, vec.x)
                                            guardCircleAngles[uuid] = currentAngle + Math.PI
                                        }
                                    }
                                    
                                    // If hit the ground, switch back to CIRCLING
                                    if (guard.onGround() || guard.horizontalCollision) {
                                        guardStates[uuid] = GuardState.CIRCLING
                                        guardAttackCooldowns[uuid] = world.random.nextInt(301) + 300 // Reset to 15-30s cooldown
                                        
                                        // Update the circle angle to make them swoop past the player instead of turning around
                                        val vec = guard.position().subtract(target.position())
                                        val currentAngle = kotlin.math.atan2(vec.z, vec.x)
                                        guardCircleAngles[uuid] = currentAngle + Math.PI
                                    }
                                }
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
