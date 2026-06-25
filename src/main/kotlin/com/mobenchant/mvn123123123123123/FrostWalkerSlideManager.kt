package com.mobenchant.mvn123123123123123

import com.mobenchant.mvn123123123123123.MobEnchantData.setFrozen
import net.minecraft.server.MinecraftServer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.util.UUID

object FrostWalkerSlideManager {
    // Maps Player UUID to their sliding velocity vector
    val frozenPlayers = mutableMapOf<UUID, Vec3>()

    fun freezePlayer(player: Player, initialVelocity: Vec3) {
        frozenPlayers[player.uuid] = initialVelocity
        // Add strong effects to disable manual walking
        player.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 60, 255, false, false))
        
        // Mark them as frozen so custom movements (like lunge) know to abort
        player.setFrozen(true)
    }

    fun tick(server: MinecraftServer) {
        val playersToRemove = mutableListOf<UUID>()

        for ((uuid, velocity) in frozenPlayers) {
            var found = false
            for (world in server.allLevels) {
                val player = world.getPlayerByUUID(uuid)
                if (player != null) {
                    found = true
                    
                    if (!player.isAlive || velocity.length() < 0.05) {
                        playersToRemove.add(uuid)
                        player.setFrozen(false)
                        player.removeEffect(MobEffects.SLOWNESS)
                        break
                    }
                    
                    // Force position update to lock them onto the slide trajectory
                    val nextX = player.x + velocity.x
                    // Let gravity act normally, but force horizontal slide
                    player.setPos(nextX, player.y, player.z + velocity.z)
                    // Zero out delta movement so they don't get shot into the sky by knockback
                    player.deltaMovement = Vec3(0.0, player.deltaMovement.y.coerceAtMost(0.0), 0.0)
                    player.hurtMarked = true
                    
                    // Place temporary blue ice under feet
                    val pos = player.blockPosition().below()
                    if (world.isEmptyBlock(pos) || world.getBlockState(pos).isAir || world.getBlockState(pos).canBeReplaced()) {
                        world.setBlockAndUpdate(pos, Blocks.BLUE_ICE.defaultBlockState())
                        // Schedule removal of blue ice
                        MobEnchant.scheduleTask(40) {
                            if (world.getBlockState(pos).block == Blocks.BLUE_ICE) {
                                world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())
                            }
                        }
                    }

                    // Decay velocity
                    frozenPlayers[uuid] = velocity.scale(0.95)
                    
                    // Re-apply effects just in case
                    player.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 10, 255, false, false, false))
                    
                    break
                }
            }
            if (!found) playersToRemove.add(uuid)
        }

        for (uuid in playersToRemove) {
            frozenPlayers.remove(uuid)
        }
    }
}
