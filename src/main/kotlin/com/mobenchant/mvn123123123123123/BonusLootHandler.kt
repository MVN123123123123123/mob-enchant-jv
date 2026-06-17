package com.mobenchant.mvn123123123123123

import com.mobenchant.mvn123123123123123.MobEnchantData.getMobEnchantments
import com.mobenchant.mvn123123123123123.MobEnchantData.canDealDamage
import net.minecraft.ChatFormatting
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.ExperienceOrb
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

// ============================================================================
// Bonus loot & XP handler — called when an enchanted mob dies
// ============================================================================
object BonusLootHandler {

    fun onEnchantedMobDeath(
        deadEntity: LivingEntity,
        source: DamageSource,
        world: ServerLevel,
    ) {
        if (!deadEntity.canDealDamage()) return

        val enchants = deadEntity.getMobEnchantments() ?: return
        if (enchants.isEmpty()) return

        val powerScore = EnchantmentRoller.calculatePowerScore(enchants)
        val enchantCount = enchants.size

        // =================================================================
        // BONUS XP — Base: 3 per enchant + 2 per power level
        // =================================================================
        val bonusXP = (enchantCount * 3) + (powerScore * 2)
        val killer = source.entity

        if (killer is ServerPlayer) {
            // Give XP directly to the killing player
            killer.giveExperiencePoints(bonusXP)
        } else {
            // Spawn XP orbs at the death location
            ExperienceOrb.award(world, deadEntity.position(), bonusXP)
        }

        // =================================================================
        // BONUS LOOT — Scales with enchant count × power score
        // =================================================================
        var totalItemsToDrop = floor(enchantCount * powerScore * 1.5).toInt()
        totalItemsToDrop = totalItemsToDrop.coerceIn(1, 200)

        // Aggregate drops into stacks to prevent lag
        val drops = mutableMapOf<net.minecraft.world.item.Item, Int>()
        repeat(totalItemsToDrop) {
            val lootItem = EnchantmentRoller.weightedRandom(EnchantmentPool.BONUS_LOOT)
            drops[lootItem] = (drops[lootItem] ?: 0) + 1
        }

        val loc = deadEntity.position()
        for ((item, totalAmount) in drops) {
            var remaining = totalAmount
            while (remaining > 0) {
                val stackSize = min(remaining, 64)
                remaining -= stackSize

                val ox = loc.x + (Math.random() - 0.5) * 0.8
                val oz = loc.z + (Math.random() - 0.5) * 0.8
                try {
                    val stack = ItemStack(item, stackSize)
                    val itemEntity = ItemEntity(world, ox, loc.y + 0.5, oz, stack)
                    itemEntity.setDefaultPickUpDelay()
                    world.addFreshEntity(itemEntity)
                } catch (_: Exception) { }
            }
        }

        // =================================================================
        // DEATH MESSAGE — Announce the enchanted mob's death
        // =================================================================
        val entityTypeKey = BuiltInRegistries.ENTITY_TYPE.getKey(deadEntity.type)
        val mobName = NameplateManager.prettyName(entityTypeKey.path)
        val enchantNames = NameplateManager.enchantListString(enchants)

        val msg = Component.literal("")
            .append(Component.literal("✦ ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
            .append(Component.literal("An enchanted ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(mobName).withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" has been slain! ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal(enchantNames).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("+${bonusXP} bonus XP").withStyle(ChatFormatting.GOLD))
            .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.literal("${totalItemsToDrop} bonus drops").withStyle(ChatFormatting.GREEN))

        world.server.playerList.broadcastSystemMessage(msg, false)
    }
}
