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
        // BONUS XP — Scales with enchant count × power score
        // =================================================================
        val bonusXP = floor(enchantCount * powerScore * 1.5).toInt().coerceAtLeast(1)
        val killer = source.entity

        if (killer is ServerPlayer) {
            // Give XP directly to the killing player
            killer.giveExperiencePoints(bonusXP)
        } else {
            // Spawn XP orbs at the death location
            ExperienceOrb.award(world, deadEntity.position(), bonusXP)
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

        world.server.playerList.broadcastSystemMessage(msg, false)
    }
}
