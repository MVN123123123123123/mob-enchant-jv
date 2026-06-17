package com.mobenchant.mvn123123123123123

import com.mobenchant.mvn123123123123123.MobEnchantData.getMobEnchant
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.block.Blocks

// ============================================================================
// Frost Walker tick handler — converts water to frosted ice under enchanted mobs
// ============================================================================
object FrostWalkerHandler {

    /**
     * Process frost walker for all enchanted mobs in a world.
     * Called every 5 server ticks.
     */
    fun tick(world: ServerLevel) {
        try {
            for (entity in world.allEntities) {
                if (entity !is Mob) continue
                if (!entity.isAlive) continue

                val fw = entity.getMobEnchant("frost_walker") ?: continue
                val radius = fw.level + 1

                val centerX = entity.blockX
                val centerY = entity.blockY - 1
                val centerZ = entity.blockZ

                for (dx in -radius..radius) {
                    for (dz in -radius..radius) {
                        if (dx * dx + dz * dz > radius * radius) continue
                        try {
                            val pos = BlockPos(centerX + dx, centerY, centerZ + dz)
                            val block = world.getBlockState(pos)
                            if (block.`is`(Blocks.WATER)) {
                                val above = world.getBlockState(pos.above())
                                if (above.isAir) {
                                    world.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState())
                                }
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        } catch (_: Exception) {
            // World may be unloading
        }
    }
}
