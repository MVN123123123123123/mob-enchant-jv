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
        // player.setFrozen(true) // Removed, handled by map to prevent perm invuln
    }

    data class IceRecord(val dimension: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, val pos: net.minecraft.core.BlockPos, val originalState: net.minecraft.world.level.block.state.BlockState, var expireTime: Long)
    val temporaryIce = mutableListOf<IceRecord>()

    fun tick(server: MinecraftServer) {
        val currentTime = server.tickCount.toLong()
        
        // Handle expiring blue ice
        val iceIterator = temporaryIce.iterator()
        while(iceIterator.hasNext()) {
            val record = iceIterator.next()
            if (currentTime >= record.expireTime) {
                val world = server.getLevel(record.dimension)
                if (world != null && world.getBlockState(record.pos).block == Blocks.BLUE_ICE) {
                    world.setBlockAndUpdate(record.pos, record.originalState)
                }
                iceIterator.remove()
            }
        }

        val playersToRemove = mutableListOf<UUID>()

        for ((uuid, velocity) in frozenPlayers) {
            var found = false
            for (world in server.allLevels) {
                val player = world.getPlayerByUUID(uuid)
                if (player != null) {
                    found = true
                    
                    if (!player.isAlive || velocity.length() < 0.05) {
                        playersToRemove.add(uuid)
                        player.removeEffect(MobEffects.SLOWNESS)
                        break
                    }
                    
                    // Apply velocity directly to deltaMovement so the client predicts it smoothly
                    player.deltaMovement = Vec3(velocity.x, player.deltaMovement.y.coerceAtMost(0.0), velocity.z)
                    player.hurtMarked = true
                    
                    // Place temporary blue ice under feet
                    val pos = player.blockPosition().below()
                    val currentState = world.getBlockState(pos)
                    val dim = world.dimension()
                    
                    if (currentState.block != Blocks.BLUE_ICE) {
                        // Don't replace unbreakable blocks or entities (like chests)
                        if (currentState.getDestroySpeed(world, pos) >= 0.0f && world.getBlockEntity(pos) == null) {
                            temporaryIce.add(IceRecord(dim, pos, currentState, currentTime + 40))
                            world.setBlockAndUpdate(pos, Blocks.BLUE_ICE.defaultBlockState())
                        }
                    } else {
                        // Extend expiration time if it's already temporary ice
                        val record = temporaryIce.find { it.dimension == dim && it.pos == pos }
                        if (record != null) {
                            record.expireTime = currentTime + 40
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
