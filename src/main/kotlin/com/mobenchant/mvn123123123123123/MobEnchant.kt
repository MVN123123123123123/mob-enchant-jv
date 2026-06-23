package com.mobenchant.mvn123123123123123

import com.mobenchant.mvn123123123123123.MobEnchantData.getMobEnchantments
import com.mobenchant.mvn123123123123123.MobEnchantData.isAlreadyRolled
import com.mobenchant.mvn123123123123123.MobEnchantData.markAsRolled
import com.mobenchant.mvn123123123123123.MobEnchantData.setMobEnchantments
import com.mobenchant.mvn123123123123123.MobEnchantData.canDealDamage
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.projectile.Projectile
import org.slf4j.LoggerFactory
import kotlin.random.Random

// ============================================================================
// Main Mod Initializer — Mob Enchantment for Fabric / Minecraft 26.1.x
// ============================================================================
object MobEnchant : ModInitializer {

    private val logger = LoggerFactory.getLogger("mob-enchant")

    // Tick-based delayed task scheduler (replaces Bedrock's system.runTimeout)
    private val scheduledTasks = mutableListOf<ScheduledTask>()
    private val tasksToAdd = mutableListOf<ScheduledTask>()

    private data class ScheduledTask(var ticksRemaining: Int, val action: () -> Unit)

    /** Schedule a task to run after [delayTicks] server ticks. */
    fun scheduleTask(delayTicks: Int, action: () -> Unit) {
        tasksToAdd.add(ScheduledTask(delayTicks, action))
    }

    // Tick counters for interval-based logic
    private var tickCounter = 0

    override fun onInitialize() {
        logger.info("[Mob Enchant] Initializing enchantment system for Minecraft 26.1.x...")

        // Force-load the attachment types so they register at startup
        MobEnchantData.ENCHANTMENTS
        MobEnchantData.INFINITY_BROKEN
        MobEnchantData.ALREADY_ROLLED

        // =================================================================
        // ENTITY LOAD — Roll for enchantment on first spawn
        // =================================================================
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (entity !is Mob) return@register
            if (world !is ServerLevel) return@register
            if (entity.isAlreadyRolled()) return@register
            if (entity.type == net.minecraft.world.entity.EntityTypes.SULFUR_CUBE) return@register

            entity.markAsRolled()

            // Boss check & specialized enchantment path
            if (BossEnchantHandler.isBoss(entity)) {
                BossEnchantHandler.onBossSpawn(entity)
                logger.debug("[Mob Enchant] Enchanted boss ${entity.type} with 5 max-level enchantments")
                return@register
            }

            // 1-in-6 chance to become enchanted
            if (Random.nextInt(EnchantmentPool.ENCHANT_CHANCE) != 0) return@register

            val enchantCount = EnchantmentRoller.rollEnchantCount()
            val enchantList = EnchantmentRoller.rollEnchantments(enchantCount, onlyDefensive = !entity.canDealDamage(), entityType = entity.type)

            entity.setMobEnchantments(enchantList)
            NameplateManager.setEnchantedNameplate(entity, enchantList)
            EnchantmentEffects.applyPassiveBoosts(entity, enchantList)

            logger.debug("[Mob Enchant] Enchanted ${entity.type} with ${enchantList.size} enchantments")
        }

        // =================================================================
        // ENTITY LOAD — Projectile multishot & quick charge
        // =================================================================
        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (entity !is Projectile) return@register
            if (world !is ServerLevel) return@register
            
            // Prevent infinite recursion: do not process projectiles spawned by our own enchants
            if (entity.entityTags().contains("mobenchant_extra_projectile")) return@register

            val owner = entity.owner
            if (owner !is Mob) return@register

            // Handle multishot projectile duplication
            EnchantmentEffects.handleMultishotRanged(entity, owner, world)

            // Handle quick charge follow-up shots
            EnchantmentEffects.handleQuickChargeRanged(entity, owner, world)
        }

        // =================================================================
        // ALLOW_DAMAGE — Cancel damage for Infinity shield & Unbreaking revive
        // =================================================================
        ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, amount ->
            if (entity !is Mob) return@register true
            val world = entity.level()
            if (world !is ServerLevel) return@register true

            if (EnchantmentEffects.isCustomDamage) return@register true

            // Gather all entities in the combination to check for shield/revive
            val combination = mutableListOf<Mob>()
            combination.add(entity)
            val vehicle = entity.vehicle
            if (vehicle is Mob) combination.add(vehicle)
            entity.passengers.forEach { if (it is Mob) combination.add(it) }

            // Infinity shield — blocks all damage until broken
            for (combEntity in combination) {
                if (EnchantmentEffects.handleInfinityShield(combEntity, source, world)) {
                    return@register false // CANCEL damage
                }
            }

            // Unbreaking revive — chance to survive fatal damage
            if (EnchantmentEffects.handleUnbreakingRevive(entity, amount, world)) {
                return@register false // CANCEL damage
            }

            // --- Custom Damage Reduction ---
            val combinationEnchants = mutableListOf<MobEnchantment>()
            for (combEntity in combination) {
                val enchants = combEntity.getMobEnchantments()
                if (!enchants.isNullOrEmpty()) combinationEnchants.addAll(enchants)
            }

            if (combinationEnchants.isNotEmpty()) {
                val reduction = EnchantmentEffects.calculateDamageReduction(source, combinationEnchants)
                if (reduction > 0f) {
                    val cappedReduction = reduction.coerceAtMost(0.95f)
                    val reducedDamage = amount * (1.0f - cappedReduction)
                    
                    EnchantmentEffects.isCustomDamage = true
                    entity.hurt(source, reducedDamage)
                    EnchantmentEffects.isCustomDamage = false
                    
                    val blastKbReduction = EnchantmentEffects.calculateBlastKbReduction(source, combinationEnchants)
                    if (blastKbReduction > 0.0) {
                        entity.deltaMovement = entity.deltaMovement.scale(1.0 - blastKbReduction)
                    }

                    return@register false // CANCEL original damage
                }
            }

            true // Allow damage
        }

        // =================================================================
        // AFTER_DAMAGE — Offensive effects, defensive effects, mending, multishot crit
        // =================================================================
        ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, source, baseDamage, damageTaken, blocked ->
            if (entity !is LivingEntity) return@register
            val world = entity.level()
            if (world !is ServerLevel) return@register

            val attacker = source.entity

            // --- Attacker's offensive enchantments ---
            if (attacker is Mob && attacker.isAlive) {
                val attackerEnchants = attacker.getMobEnchantments()
                if (!attackerEnchants.isNullOrEmpty()) {
                    EnchantmentEffects.handleOffensiveHit(entity, attacker, attackerEnchants, world, baseDamage, damageTaken, blocked, source)

                    // Mending: heal attacker when dealing damage
                    EnchantmentEffects.handleMending(attacker, damageTaken)

                    // Multishot melee crit: 10% chance for 3x damage
                    EnchantmentEffects.handleMultishotMeleeCrit(entity, attacker, baseDamage, world)
                    
                    NameplateManager.setEnchantedNameplate(attacker, attackerEnchants)
                } else {
                    NameplateManager.updateHealthNameplate(attacker)
                }
            }

            // --- Victim's defensive enchantments ---
            if (entity is Mob) {
                val combinationEnchants = mutableListOf<MobEnchantment>()
                
                val selfEnchants = entity.getMobEnchantments()
                if (!selfEnchants.isNullOrEmpty()) {
                    combinationEnchants.addAll(selfEnchants)
                    NameplateManager.setEnchantedNameplate(entity, selfEnchants)
                } else {
                    NameplateManager.updateHealthNameplate(entity)
                }

                val vehicle = entity.vehicle
                if (vehicle is Mob) {
                    val vEnchants = vehicle.getMobEnchantments()
                    if (!vEnchants.isNullOrEmpty()) combinationEnchants.addAll(vEnchants)
                }
                
                for (passenger in entity.passengers) {
                    if (passenger is Mob) {
                        val pEnchants = passenger.getMobEnchantments()
                        if (!pEnchants.isNullOrEmpty()) combinationEnchants.addAll(pEnchants)
                    }
                }

                if (combinationEnchants.isNotEmpty()) {
                    EnchantmentEffects.handleDefensiveHurt(entity, source, damageTaken, combinationEnchants)
                }
            }
        }

        // =================================================================
        // AFTER_DEATH — Bonus loot & XP for enchanted mobs
        // =================================================================
        ServerLivingEntityEvents.AFTER_DEATH.register { entity, source ->
            val world = entity.level()
            if (world is ServerLevel) {
                // Soul speed: nearby enchanted mobs gain speed when any entity dies
                EnchantmentEffects.handleSoulSpeedDeath(entity, world)
            }

            if (entity !is Mob) return@register
            if (world !is ServerLevel) return@register

            // Clean up tracking maps
            EnchantmentEffects.onEntityDeath(entity.id)
            
            // Clean up "✦ Enchanted" text display passenger
            entity.passengers.forEach {
                if (it is net.minecraft.world.entity.Display.TextDisplay && it.entityTags().contains("mobenchant:header")) {
                    it.discard()
                }
            }

            // Handle bonus loot
            BonusLootHandler.onEnchantedMobDeath(entity, source, world)
        }

        // =================================================================
        // AFTER_EFFECT_ADD — Break Infinity shield on potion hit
        // =================================================================
        net.fabricmc.fabric.api.entity.event.v1.effect.ServerMobEffectEvents.AFTER_ADD.register { effect, entity, context ->
            EnchantmentEffects.onEffectAdded(entity, effect)
        }

        // =================================================================
        // SERVER TICK — Passive refresh, particles, frost walker, scheduled tasks
        // =================================================================
        ServerTickEvents.END_SERVER_TICK.register { server ->
            tickCounter++
            
            // Tick Boss Guards
            BossGuardManager.tick(server)

            // Every tick — Continuous effects (Respiration, Depth Strider)
            for (world in server.allLevels) {
                for (entity: net.minecraft.world.entity.Entity? in world.allEntities) {
                    if (entity == null) continue
                    if (entity is Mob && entity.isAlive) {
                        val enchants = entity.getMobEnchantments()
                        if (!enchants.isNullOrEmpty()) {
                            EnchantmentEffects.tickContinuousEffects(entity, enchants)
                        }
                    }
                }
            }

            // Process scheduled tasks
            if (tasksToAdd.isNotEmpty()) {
                scheduledTasks.addAll(tasksToAdd)
                tasksToAdd.clear()
            }
            
            val taskIterator = scheduledTasks.iterator()
            while (taskIterator.hasNext()) {
                val task = taskIterator.next()
                task.ticksRemaining--
                if (task.ticksRemaining <= 0) {
                    try { task.action() } catch (_: Exception) { }
                    taskIterator.remove()
                }
            }

            // Every 5 ticks — Frost Walker
            if (tickCounter % 5 == 0) {
                for (world in server.allLevels) {
                    FrostWalkerHandler.tick(world)
                }
            }

            // Every 10 ticks — Update health on enchanted nameplates
            if (tickCounter % 10 == 0) {
                for (world in server.allLevels) {
                    for (entity: net.minecraft.world.entity.Entity? in world.allEntities) {
                        if (entity == null) continue
                        if (entity is Mob && entity.isAlive) {
                            val enchants = entity.getMobEnchantments()
                            if (!enchants.isNullOrEmpty()) {
                                NameplateManager.setEnchantedNameplate(entity, enchants)
                            } else {
                                NameplateManager.updateHealthNameplate(entity)
                            }
                        } else if (entity is net.minecraft.world.entity.Display.TextDisplay && entity.entityTags().contains("mobenchant:header")) {
                            // If the text display is no longer riding a mob (e.g. mob transformed or despawned), discard it
                            if (entity.vehicle == null) {
                                entity.discard()
                            }
                        }
                    }
                }
            }

            // Every 40 ticks (2 seconds) — Enchantment particles
            if (tickCounter % 40 == 0) {
                for (world in server.allLevels) {
                    spawnEnchantmentParticles(world)
                }
            }

            // Every 600 ticks (30 seconds) — Refresh passive effects
            if (tickCounter % 600 == 0) {
                for (world in server.allLevels) {
                    refreshPassiveEffects(world)
                }
            }

            // Prevent counter overflow
            if (tickCounter >= 72000) tickCounter = 0
        }

        // =================================================================
        // COMMAND REGISTRATION — /mobenchant subcommands
        // =================================================================
        CommandRegistrationCallback.EVENT.register { dispatcher, registryAccess, environment ->
            MobEnchantCommands.register(dispatcher)
        }

        // =================================================================
        // STARTUP MESSAGE
        // =================================================================
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            val msg = Component.literal("")
                .append(Component.literal("[Mob Enchant] ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal("Addon loaded! Mobs now have a 1/${EnchantmentPool.ENCHANT_CHANCE} chance to spawn enchanted.").withStyle(ChatFormatting.GREEN))

            val helpMsg = Component.literal("")
                .append(Component.literal("[Mob Enchant] ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal("Type ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal("/mobenchant help").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" for manual enchanting commands.").withStyle(ChatFormatting.GRAY))

            server.playerList.broadcastSystemMessage(msg, false)
            server.playerList.broadcastSystemMessage(helpMsg, false)

            logger.info("[Mob Enchant] Successfully loaded! Mobs have a 1/${EnchantmentPool.ENCHANT_CHANCE} chance to spawn enchanted.")
        }

        logger.info("[Mob Enchant] Initialization complete.")
    }

    // ========================================================================
    // PARTICLE EFFECT — Spawn enchantment table particles around enchanted mobs
    // ========================================================================
    private fun spawnEnchantmentParticles(world: ServerLevel) {
        try {
            for (entity: net.minecraft.world.entity.Entity? in world.allEntities) {
                if (entity == null) continue
                if (entity !is Mob) continue
                if (!entity.isAlive) continue

                val enchants = entity.getMobEnchantments()
                if (enchants.isNullOrEmpty()) continue
                
                // Hide the particle for invisible entities
                if (entity.isInvisible) continue

                for (i in 0 until 3) {
                    val ox = (Random.nextDouble() - 0.5) * 1.5
                    val oy = Random.nextDouble() * 1.5
                    val oz = (Random.nextDouble() - 0.5) * 1.5
                    try {
                        world.sendParticles(
                            ParticleTypes.ENCHANT,
                            entity.x + ox, entity.y + oy, entity.z + oz,
                            1, 0.0, 0.0, 0.0, 0.0,
                        )
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
    }

    // ========================================================================
    // PASSIVE REFRESH — Re-apply timed passive effects so they don't expire
    // ========================================================================
    private fun refreshPassiveEffects(world: ServerLevel) {
        try {
            for (entity: net.minecraft.world.entity.Entity? in world.allEntities) {
                if (entity == null) continue
                if (entity !is Mob) continue
                if (!entity.isAlive) continue

                val enchants = entity.getMobEnchantments()
                if (enchants.isNullOrEmpty()) continue
                EnchantmentEffects.applyPassiveBoosts(entity, enchants)
            }
        } catch (_: Exception) { }
    }
}