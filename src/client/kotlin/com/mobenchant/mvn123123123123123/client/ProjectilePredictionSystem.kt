package com.mobenchant.mvn123123123123123.client

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.ItemStack
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

object ProjectilePredictionSystem {
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientTickEvents.EndTick { mc ->
            val player = mc.player ?: return@EndTick
            val level = mc.level ?: return@EndTick
            
            var useItem: ItemStack = player.mainHandItem
            if (useItem.item !is BowItem && useItem.item !is CrossbowItem) {
                useItem = player.offhandItem
            }

            var arrowSpeed = 0.0f
            if (useItem.item is BowItem) {
                if (player.useItemRemainingTicks <= 0) return@EndTick
                val charge = useItem.item.getUseDuration(useItem, player) - player.useItemRemainingTicks
                val power = BowItem.getPowerForTime(charge)
                if (power <= 0.1f) return@EndTick
                arrowSpeed = power * 3.0f
            } else if (useItem.item is CrossbowItem) {
                // Crossbow always fires at max speed 3.15, but we only show if it's held
                arrowSpeed = 3.15f
            } else {
                return@EndTick
            }

            // Only update/draw particles every few ticks
            if (player.tickCount % 2 != 0) return@EndTick

            // Find the nearest boss guard
            val maxDist = 64.0
            var nearestGuard: Mob? = null
            var closestDistSqr = maxDist * maxDist

            for (entity in level.entitiesForRendering()) {
                if (entity is Mob && entity.entityTags().contains("boss_guard") && entity.isAlive) {
                    val distSqr = entity.distanceToSqr(player)
                    if (distSqr < closestDistSqr) {
                        closestDistSqr = distSqr
                        nearestGuard = entity
                    }
                }
            }

            val guard = nearestGuard ?: return@EndTick

            val startX = player.x
            val startY = player.eyeY
            val startZ = player.z

            var t = sqrt(closestDistSqr) / arrowSpeed
            var pHitX = guard.x + guard.deltaMovement.x * t
            var pHitY = guard.y + (guard.bbHeight / 2.0) + guard.deltaMovement.y * t
            var pHitZ = guard.z + guard.deltaMovement.z * t

            // Iterate to refine time-to-impact calculation
            for (i in 0..2) {
                val newDist = sqrt((pHitX - startX) * (pHitX - startX) + (pHitY - startY) * (pHitY - startY) + (pHitZ - startZ) * (pHitZ - startZ))
                t = newDist / arrowSpeed
                pHitX = guard.x + guard.deltaMovement.x * t
                pHitY = guard.y + (guard.bbHeight / 2.0) + guard.deltaMovement.y * t
                pHitZ = guard.z + guard.deltaMovement.z * t
            }

            // Gravity drop compensation
            // Arrow gravity in Minecraft is roughly 0.05 blocks/tick^2. Drop over time t is roughly 0.5 * g * t^2
            val drop = 0.5 * 0.05 * t * t

            // The exact point to aim at to hit pHit
            val exactAimX = pHitX
            val exactAimY = pHitY + drop
            val exactAimZ = pHitZ

            // Introduce looseness/inaccuracy by rounding coordinates
            val aimX = round(exactAimX)
            val aimY = round(exactAimY)
            val aimZ = round(exactAimZ)

            // Draw a circle of particles facing the player
            val dirX = startX - aimX
            val dirY = startY - aimY
            val dirZ = startZ - aimZ
            val dirLength = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
            
            if (dirLength < 0.1) return@EndTick // Too close

            val nx = dirX / dirLength
            val ny = dirY / dirLength
            val nz = dirZ / dirLength

            // Calculate orthogonal vectors right and up
            var tempUpX = 0.0
            var tempUpY = 1.0
            var tempUpZ = 0.0
            if (abs(ny) > 0.9) {
                tempUpX = 1.0
                tempUpY = 0.0
            }
            
            var rx = tempUpY * nz - tempUpZ * ny
            var ry = tempUpZ * nx - tempUpX * nz
            var rz = tempUpX * ny - tempUpY * nx
            val rLen = sqrt(rx * rx + ry * ry + rz * rz)
            rx /= rLen
            ry /= rLen
            rz /= rLen

            val tx = ny * rz - nz * ry
            val ty = nz * rx - nx * rz
            val tz = nx * ry - ny * rx

            val radius = 2.0 // "draw the circle abit big"
            val segments = 24
            for (i in 0 until segments) {
                val angle = (i.toDouble() / segments) * Math.PI * 2.0
                val cx = cos(angle) * radius
                val sy = sin(angle) * radius
                
                val px = aimX + rx * cx + tx * sy
                val py = aimY + ry * cx + ty * sy
                val pz = aimZ + rz * cx + tz * sy
                
                // Outer circle
                level.addParticle(
                    ParticleTypes.END_ROD,
                    px, py, pz,
                    0.0, 0.0, 0.0
                )
            }
            
            // Draw a small cross at the center of the rounded aim point
            level.addParticle(ParticleTypes.FLAME, aimX, aimY, aimZ, 0.0, 0.0, 0.0)
            level.addParticle(ParticleTypes.FLAME, aimX + rx * 0.5, aimY + ry * 0.5, aimZ + rz * 0.5, 0.0, 0.0, 0.0)
            level.addParticle(ParticleTypes.FLAME, aimX - rx * 0.5, aimY - ry * 0.5, aimZ - rz * 0.5, 0.0, 0.0, 0.0)
            level.addParticle(ParticleTypes.FLAME, aimX + tx * 0.5, aimY + ty * 0.5, aimZ + tz * 0.5, 0.0, 0.0, 0.0)
            level.addParticle(ParticleTypes.FLAME, aimX - tx * 0.5, aimY - ty * 0.5, aimZ - tz * 0.5, 0.0, 0.0, 0.0)
        })
    }
}
