package com.mobenchant.mvn123123123123123

import com.mobenchant.mvn123123123123123.MobEnchantData.clearMobEnchantments
import com.mobenchant.mvn123123123123123.MobEnchantData.getMobEnchantments
import com.mobenchant.mvn123123123123123.MobEnchantData.setMobEnchantments
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.AABB
import kotlin.math.min

// ============================================================================
// Brigadier command registration — /mobenchant subcommands
// ============================================================================
object MobEnchantCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("mobenchant")
                // /mobenchant help
                .then(Commands.literal("help").executes { ctx -> cmdHelp(ctx) })

                // /mobenchant list
                .then(Commands.literal("list").executes { ctx -> cmdList(ctx) })

                // /mobenchant info
                .then(Commands.literal("info").executes { ctx -> cmdInfo(ctx) })

                // /mobenchant debug
                .then(Commands.literal("debug").executes { ctx -> cmdDebug(ctx) })

                // /mobenchant bossreroll
                .then(Commands.literal("bossreroll").executes { ctx -> cmdBossReroll(ctx) })

                // /mobenchant clear
                .then(Commands.literal("clear").executes { ctx -> cmdClear(ctx) })

                // /mobenchant random [count]
                .then(
                    Commands.literal("random")
                        .executes { ctx -> cmdRandom(ctx, 0) }
                        .then(
                            Commands.argument("count", IntegerArgumentType.integer(1, EnchantmentPool.MAX_ENCHANTS))
                                .executes { ctx -> cmdRandom(ctx, IntegerArgumentType.getInteger(ctx, "count")) }
                        )
                )

                // /mobenchant enchant [name] [level]
                .then(
                    Commands.literal("enchant")
                        .executes { ctx -> cmdEnchantRandom(ctx) }
                        .then(
                            Commands.argument("name", StringArgumentType.word())
                                .executes { ctx -> cmdEnchantSpecific(ctx, StringArgumentType.getString(ctx, "name"), 0) }
                                .then(
                                    Commands.argument("level", IntegerArgumentType.integer(1, 5))
                                        .executes { ctx ->
                                            cmdEnchantSpecific(
                                                ctx,
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "level"),
                                            )
                                        }
                                )
                        )
                )

                // Default: /mobenchant → show help
                .executes { ctx -> cmdHelp(ctx) }
        )
    }

    // ========================================================================
    // Find the nearest mob to the command source within the search radius
    // ========================================================================
    private fun findNearestMob(ctx: CommandContext<CommandSourceStack>): Mob? {
        val source = ctx.source
        val pos = source.position
        val world = source.level
        val radius = EnchantmentPool.MOB_SEARCH_RADIUS

        val box = AABB(
            pos.x - radius, pos.y - radius, pos.z - radius,
            pos.x + radius, pos.y + radius, pos.z + radius,
        )

        return world.getEntitiesOfClass(Mob::class.java, box)
            .filter { it.isAlive }
            .minByOrNull { it.distanceToSqr(pos) }
    }

    /**
     * Get a pretty mob type name from an entity.
     */
    private fun mobTypeName(mob: Mob): String {
        val key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.type)
        return NameplateManager.prettyName(key.path)
    }

    /**
     * Send a styled mod message to the command source.
     */
    private fun tell(ctx: CommandContext<CommandSourceStack>, message: Component) {
        ctx.source.sendSuccess({ message }, false)
    }

    private fun prefix(): Component {
        return Component.literal("✦ ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
    }

    // ========================================================================
    // HELP
    // ========================================================================
    private fun cmdHelp(ctx: CommandContext<CommandSourceStack>): Int {
        tell(ctx, Component.literal("").append(prefix()).append(Component.literal("Mob Enchant Commands").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)))
        tell(ctx, Component.literal("/mobenchant enchant").withStyle(ChatFormatting.YELLOW).append(Component.literal(" — Random enchants on nearest mob").withStyle(ChatFormatting.GRAY)))
        tell(ctx, Component.literal("/mobenchant enchant <name> [level]").withStyle(ChatFormatting.YELLOW).append(Component.literal(" — Add specific").withStyle(ChatFormatting.GRAY)))
        tell(ctx, Component.literal("/mobenchant random [count]").withStyle(ChatFormatting.YELLOW).append(Component.literal(" — Add N random (1-5)").withStyle(ChatFormatting.GRAY)))
        tell(ctx, Component.literal("/mobenchant clear").withStyle(ChatFormatting.YELLOW).append(Component.literal(" — Remove all enchantments").withStyle(ChatFormatting.GRAY)))
        tell(ctx, Component.literal("/mobenchant list").withStyle(ChatFormatting.YELLOW).append(Component.literal(" — Show all enchantment names").withStyle(ChatFormatting.GRAY)))
        tell(ctx, Component.literal("/mobenchant info").withStyle(ChatFormatting.YELLOW).append(Component.literal(" — Inspect nearest mob").withStyle(ChatFormatting.GRAY)))
        tell(ctx, Component.literal("Max ${EnchantmentPool.MAX_ENCHANTS} enchantments per mob. Range: ${EnchantmentPool.MOB_SEARCH_RADIUS.toInt()} blocks.").withStyle(ChatFormatting.DARK_GRAY))
        return 1
    }

    // ========================================================================
    // LIST — Show all available enchantments grouped by category
    // ========================================================================
    private fun cmdList(ctx: CommandContext<CommandSourceStack>): Int {
        tell(ctx, Component.literal("").append(prefix()).append(Component.literal("Available Enchantments").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)))

        val categories = mapOf(
            "offensive" to Pair("Offensive", ChatFormatting.RED),
            "defensive" to Pair("Defensive", ChatFormatting.AQUA),
            "passive"   to Pair("Movement", ChatFormatting.GREEN),
            "flavor"    to Pair("Utility", ChatFormatting.YELLOW),
        )

        for ((cat, info) in categories) {
            val (displayName, color) = info
            val entries = EnchantmentPool.ALL.filter { it.category == cat }
            if (entries.isEmpty()) continue
            val names = entries.joinToString(", ") { "${NameplateManager.prettyName(it.id)} (${it.maxLevel})" }
            tell(ctx, Component.literal("$displayName: ").withStyle(color, ChatFormatting.BOLD).append(Component.literal(names).withStyle(color)))
        }
        return 1
    }

    // ========================================================================
    // INFO — Inspect nearest mob's enchantments
    // ========================================================================
    private fun cmdInfo(ctx: CommandContext<CommandSourceStack>): Int {
        val target = findNearestMob(ctx) ?: return noMobFound(ctx)
        val mobName = mobTypeName(target)
        val enchants = target.getMobEnchantments()

        if (enchants.isNullOrEmpty()) {
            tell(ctx, Component.literal("").append(prefix()).append(Component.literal("The $mobName has no enchantments.").withStyle(ChatFormatting.GRAY)))
            return 1
        }

        tell(ctx, Component.literal("").append(prefix()).append(Component.literal("$mobName — Enchantments (${enchants.size}/${EnchantmentPool.MAX_ENCHANTS})").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)))

        for (e in enchants) {
            val lvlStr = if (e.maxLevel > 1) " ${NameplateManager.toRoman(e.level)}" else ""
            val color = when (e.category) {
                "offensive" -> ChatFormatting.RED
                "defensive" -> ChatFormatting.AQUA
                "passive"   -> ChatFormatting.GREEN
                "flavor"    -> ChatFormatting.YELLOW
                else        -> ChatFormatting.GRAY
            }
            tell(ctx, Component.literal("  • ${NameplateManager.prettyName(e.id)}$lvlStr").withStyle(color))
        }
        tell(ctx, Component.literal("Power Score: ${EnchantmentRoller.calculatePowerScore(enchants)}").withStyle(ChatFormatting.DARK_GRAY))
        return 1
    }

    // ========================================================================
    // BOSS REROLL — Reroll nearest boss and remove old guards
    // ========================================================================
    private fun cmdBossReroll(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val world = source.level
        val pos = source.position
        val radius = 64.0

        val box = AABB(
            pos.x - radius, pos.y - radius, pos.z - radius,
            pos.x + radius, pos.y + radius, pos.z + radius,
        )

        // Find nearest boss
        val boss = world.getEntitiesOfClass(Mob::class.java, box)
            .filter { it.isAlive && BossEnchantHandler.isBoss(it) }
            .minByOrNull { it.distanceToSqr(pos) }

        if (boss == null) {
            tell(ctx, Component.literal("").append(prefix()).append(Component.literal("No boss found within ${radius.toInt()} blocks!").withStyle(ChatFormatting.RED)))
            return 0
        }

        // Remove old guards globally
        val bossTag = "boss_${boss.uuid}"
        val removedCount = java.util.concurrent.atomic.AtomicInteger(0)
        
        val guardsToRemove = mutableListOf<java.util.UUID>()
        for (guardId in BossGuardManager.activeGuards) {
            val guard = source.server.allLevels.mapNotNull { it.getEntity(guardId) as? Mob }.firstOrNull()
            if (guard != null && guard.entityTags().contains(bossTag)) {
                guard.discard()
                if (guard.level() is net.minecraft.server.level.ServerLevel) {
                    (guard.level() as net.minecraft.server.level.ServerLevel).sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, guard.x, guard.y + 1.0, guard.z, 10, 0.5, 0.5, 0.5, 0.05)
                }
                guardsToRemove.add(guardId)
                removedCount.incrementAndGet()
            }
        }
        BossGuardManager.activeGuards.removeAll(guardsToRemove)

        // Reroll enchants
        val newEnchants = BossEnchantHandler.rollBossEnchantments(6, BossEnchantHandler.UNMODIFIED_ENCHANTS)
        
        // Remove old passive effects
        try {
            boss.removeEffect(MobEffects.FIRE_RESISTANCE)
            boss.removeEffect(MobEffects.WATER_BREATHING)
            boss.removeEffect(MobEffects.SPEED)
            boss.removeEffect(MobEffects.RESISTANCE)
            boss.removeEffect(MobEffects.SLOW_FALLING)
        } catch (_: Exception) {}

        boss.setMobEnchantments(newEnchants)
        NameplateManager.setEnchantedNameplate(boss, newEnchants)
        EnchantmentEffects.applyPassiveBoosts(boss, newEnchants)
        
        // Reset guards killed tracker
        BossEnchantHandler.bossGuardsKilled[boss.uuid] = 0

        // Spawn new guards
        MobEnchant.scheduleTask(1) {
            BossGuardManager.spawnGuards(boss, 2, falling = false)
        }

        val mobName = mobTypeName(boss)
        tell(ctx, Component.literal("").append(prefix()).append(Component.literal("Rerolled $mobName enchantments and removed ${removedCount.get()} old guards!").withStyle(ChatFormatting.GREEN)))
        return 1
    }

    // ========================================================================
    // DEBUG — Toggle debug features
    // ========================================================================
    private fun cmdDebug(ctx: CommandContext<CommandSourceStack>): Int {
        MobEnchantConfig.debugEnabled = !MobEnchantConfig.debugEnabled
        MobEnchantConfig.save()
        val status = if (MobEnchantConfig.debugEnabled) "enabled" else "disabled"
        tell(ctx, Component.literal("").append(prefix()).append(Component.literal("Debug mode $status!").withStyle(ChatFormatting.GREEN)))
        return 1
    }

    // ========================================================================
    // CLEAR — Remove all enchantments from nearest mob
    // ========================================================================
    private fun cmdClear(ctx: CommandContext<CommandSourceStack>): Int {
        val target = findNearestMob(ctx) ?: return noMobFound(ctx)
        val mobName = mobTypeName(target)
        val enchants = target.getMobEnchantments()

        if (enchants.isNullOrEmpty()) {
            tell(ctx, Component.literal("").append(prefix()).append(Component.literal("The $mobName has no enchantments to remove.").withStyle(ChatFormatting.GRAY)))
            return 1
        }

        target.clearMobEnchantments()
        NameplateManager.updateHealthNameplate(target)
        // Remove passive effects
        try {
            target.removeEffect(MobEffects.FIRE_RESISTANCE)
            target.removeEffect(MobEffects.WATER_BREATHING)
            target.removeEffect(MobEffects.SPEED)
            target.removeEffect(MobEffects.RESISTANCE)
            target.removeEffect(MobEffects.SLOW_FALLING)
        } catch (_: Exception) { }

        tell(ctx, Component.literal("").append(prefix()).append(Component.literal("Cleared all enchantments from $mobName!").withStyle(ChatFormatting.GREEN)))
        return 1
    }

    // ========================================================================
    // RANDOM [count] — Add N random enchantments
    // ========================================================================
    private fun cmdRandom(ctx: CommandContext<CommandSourceStack>, requestedCount: Int): Int {
        val target = findNearestMob(ctx) ?: return noMobFound(ctx)
        val mobName = mobTypeName(target)
        val existing = target.getMobEnchantments() ?: emptyList()
        val slotsLeft = EnchantmentPool.MAX_ENCHANTS - existing.size

        if (slotsLeft <= 0) {
            tell(ctx, Component.literal("").append(prefix()).append(Component.literal("The $mobName already has ${EnchantmentPool.MAX_ENCHANTS}/${EnchantmentPool.MAX_ENCHANTS} enchantments! Use /mobenchant clear first.").withStyle(ChatFormatting.RED)))
            return 0
        }

        val count = if (requestedCount == 0) {
            min(EnchantmentRoller.rollEnchantCount(), slotsLeft)
        } else {
            min(requestedCount, slotsLeft)
        }

        val existingIds = existing.map { it.id }.toSet()
        val newEnchants = EnchantmentRoller.rollEnchantments(count, existingIds)

        if (newEnchants.isEmpty()) {
            tell(ctx, Component.literal("").append(prefix()).append(Component.literal("No more unique enchantments available!").withStyle(ChatFormatting.RED)))
            return 0
        }

        val combined = existing + newEnchants
        applyAndConfirm(ctx, target, combined, newEnchants, mobName)
        return 1
    }

    // ========================================================================
    // ENCHANT — Random enchantments (no args)
    // ========================================================================
    private fun cmdEnchantRandom(ctx: CommandContext<CommandSourceStack>): Int {
        return cmdRandom(ctx, 0)
    }

    // ========================================================================
    // ENCHANT <name> [level] — Specific enchantment
    // ========================================================================
    private fun cmdEnchantSpecific(ctx: CommandContext<CommandSourceStack>, name: String, requestedLevel: Int): Int {
        val target = findNearestMob(ctx) ?: return noMobFound(ctx)
        val mobName = mobTypeName(target)

        val enchantDef = EnchantmentRoller.findEnchantDef(name)
        if (enchantDef == null) {
            tell(ctx, Component.literal("").append(prefix()).append(Component.literal("Unknown enchantment: $name").withStyle(ChatFormatting.RED)))
            tell(ctx, Component.literal("Use /mobenchant list to see all available enchantments.").withStyle(ChatFormatting.GRAY))
            return 0
        }

        val level = if (requestedLevel == 0) {
            EnchantmentRoller.rollLevel(enchantDef.maxLevel)
        } else {
            requestedLevel.coerceIn(1, enchantDef.maxLevel)
        }

        val existing = target.getMobEnchantments()?.toMutableList() ?: mutableListOf()
        val existingIdx = existing.indexOfFirst { it.id == enchantDef.id }

        if (existingIdx != -1) {
            // Update existing enchantment level
            existing[existingIdx] = existing[existingIdx].copy(level = level)
            target.setMobEnchantments(existing)
            NameplateManager.setEnchantedNameplate(target, existing)
            EnchantmentEffects.applyPassiveBoosts(target, existing)

            val lvlStr = if (enchantDef.maxLevel > 1) " ${NameplateManager.toRoman(level)}" else ""
            tell(ctx, Component.literal("").append(prefix())
                .append(Component.literal("Updated $mobName's ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("${NameplateManager.prettyName(enchantDef.id)}$lvlStr").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("!").withStyle(ChatFormatting.GREEN)))
            return 1
        }

        // New enchantment — check cap
        if (existing.size >= EnchantmentPool.MAX_ENCHANTS) {
            tell(ctx, Component.literal("").append(prefix()).append(Component.literal("The $mobName already has ${EnchantmentPool.MAX_ENCHANTS}/${EnchantmentPool.MAX_ENCHANTS} enchantments!").withStyle(ChatFormatting.RED)))
            tell(ctx, Component.literal("Use /mobenchant clear to remove them, or specify an existing enchant to update its level.").withStyle(ChatFormatting.GRAY))
            return 0
        }

        val newEnchant = MobEnchantment(
            id = enchantDef.id,
            level = level,
            maxLevel = enchantDef.maxLevel,
            category = enchantDef.category,
        )

        val combined = existing + newEnchant
        applyAndConfirm(ctx, target, combined, listOf(newEnchant), mobName)
        return 1
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private fun applyAndConfirm(
        ctx: CommandContext<CommandSourceStack>,
        target: Mob,
        combined: List<MobEnchantment>,
        added: List<MobEnchantment>,
        mobName: String,
    ) {
        target.setMobEnchantments(combined)
        NameplateManager.setEnchantedNameplate(target, combined)
        EnchantmentEffects.applyPassiveBoosts(target, combined)

        val addedStr = added.joinToString(", ") { e ->
            val lvl = if (e.maxLevel > 1) " ${NameplateManager.toRoman(e.level)}" else ""
            "${NameplateManager.prettyName(e.id)}$lvl"
        }

        tell(ctx, Component.literal("").append(prefix())
            .append(Component.literal("Enchanted $mobName with: ").withStyle(ChatFormatting.GREEN))
            .append(Component.literal(addedStr).withStyle(ChatFormatting.YELLOW)))
        tell(ctx, Component.literal("Total: ${combined.size}/${EnchantmentPool.MAX_ENCHANTS} enchantments").withStyle(ChatFormatting.DARK_GRAY))
    }

    private fun noMobFound(ctx: CommandContext<CommandSourceStack>): Int {
        tell(ctx, Component.literal("").append(prefix()).append(
            Component.literal("No mob found within ${EnchantmentPool.MOB_SEARCH_RADIUS.toInt()} blocks! Get closer to a mob.").withStyle(ChatFormatting.RED)
        ))
        return 0
    }
}
