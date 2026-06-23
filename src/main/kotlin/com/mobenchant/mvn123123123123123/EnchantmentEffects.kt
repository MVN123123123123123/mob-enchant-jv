package com.mobenchant.mvn123123123123123

import com.mobenchant.mvn123123123123123.MobEnchantData.getMobEnchant
import com.mobenchant.mvn123123123123123.MobEnchantData.getMobEnchantments
import com.mobenchant.mvn123123123123123.MobEnchantData.getRespirationTicks
import com.mobenchant.mvn123123123123123.MobEnchantData.isInfinityBroken
import com.mobenchant.mvn123123123123123.MobEnchantData.setInfinityBroken
import com.mobenchant.mvn123123123123123.MobEnchantData.setRespirationTicks
import com.mobenchant.mvn123123123123123.MobEnchantData.getLungeCooldown
import com.mobenchant.mvn123123123123123.MobEnchantData.setLungeCooldown
import com.mobenchant.mvn123123123123123.MobEnchantData.getLungePrepTicks
import com.mobenchant.mvn123123123123123.MobEnchantData.setLungePrepTicks
import com.mobenchant.mvn123123123123123.MobEnchantData.isLunging
import com.mobenchant.mvn123123123123123.MobEnchantData.setLunging
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.phys.Vec3
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

// ============================================================================
// All enchantment effect handlers for enchanted mobs
// ============================================================================
object EnchantmentEffects {

    // Recursion guard — prevents infinite loops from bonus damage triggering AFTER_DAMAGE again
    private val processingDamage = mutableSetOf<Int>()

    // Infinity shield alert cooldown per entity ID
    private val lastInfinityAlert = mutableMapOf<Int, Long>()

    // Infinity hit tracking
    private val infinityHits = mutableMapOf<Int, Int>()
    private val infinityWindowStart = mutableMapOf<Int, Long>()

    // Multishot melee crit guard
    private val activeMultishots = mutableSetOf<Int>()

    // Suppresses infinity-break check while we're applying passive boosts
    @JvmStatic
    var suppressEffectCheck = false
        private set

    // ========================================================================
    // PASSIVE BOOSTS — One-time effects applied on spawn (and periodically refreshed)
    // ========================================================================
    fun applyPassiveBoosts(entity: LivingEntity, enchantList: List<MobEnchantment>) {
        suppressEffectCheck = true
        try {
            for (enchant in enchantList) {
                when (enchant.id) {
                    "infinity" -> {
                        // Infinity shield is handled by ALLOW_DAMAGE, no effect needed here
                    }
                    "fire_protection" -> {
                        // Handled in calculateDamageReduction
                    }
                    "respiration" -> {
                        // Handled in tickContinuousEffects
                    }
                    "depth_strider" -> {
                        // Handled in tickContinuousEffects
                    }
                    "soul_speed" -> {
                        // Handled in handleSoulSpeedDeath
                    }
                    "protection" -> {
                        // Handled in handleDefensiveHurt
                    }
                    "feather_falling" -> {
                        val durations = intArrayOf(20000000, 20000000, 20000000, 20000000)
                        val dur = durations[min(enchant.level - 1, durations.size - 1)]
                        entity.addEffect(MobEffectInstance(MobEffects.SLOW_FALLING, dur, 0, false, false))
                    }
                    "swift_sneak" -> {
                        entity.addEffect(MobEffectInstance(MobEffects.SPEED, 20000000, enchant.level, false, false))
                        entity.isSilent = true
                    }
                    "lunge" -> {
                        val attr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)
                        if (attr != null) {
                            val modId = net.minecraft.resources.Identifier.fromNamespaceAndPath("mob-enchant", "lunge_range_boost")
                            if (attr.getModifier(modId) == null) {
                                attr.addPermanentModifier(net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                    modId, (enchant.level * 4).toDouble(), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                                ))
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Entity may not support effects
        } finally {
            suppressEffectCheck = false
        }
    }

    // ========================================================================
    // OFFENSIVE EFFECTS — When an enchanted mob hits a victim
    // Called from AFTER_DAMAGE with the attacker's enchantments
    // ========================================================================
    fun handleOffensiveHit(
        victim: LivingEntity,
        attacker: LivingEntity,
        enchants: List<MobEnchantment>,
        world: ServerLevel,
        baseDamage: Float,
        damageTaken: Float,
        blocked: Boolean,
        source: net.minecraft.world.damagesource.DamageSource
    ) {
        // Recursion guard: prevent bonus damage from re-triggering offensive processing
        if (victim.id in processingDamage) return
        processingDamage.add(victim.id)

        try {
            for (enchant in enchants) {
                if (!victim.isAlive) break
                try {
                    when (enchant.id) {
                        // --- WIND BURST: Launch attacker into the air ---
                        "wind_burst" -> {
                            val power = 0.5 + 0.25 * enchant.level
                            attacker.deltaMovement = Vec3(attacker.deltaMovement.x, power, attacker.deltaMovement.z)
                            attacker.hurtMarked = true
                        }

                        // --- DENSITY: Extra damage based on fall distance ---
                        "density" -> {
                            val fallDist = attacker.fallDistance
                            if (fallDist > 0) {
                                val extra = (fallDist * 0.5 * enchant.level).toFloat()
                                victim.hurt(victim.damageSources().mobAttack(attacker), extra)
                            }
                        }

                        // --- BREACH: Reduce armor effectiveness ---
                        "breach" -> {
                            if (!blocked && damageTaken > 0f && baseDamage > damageTaken) {
                                val armorEffectiveness = 1.0f - (damageTaken / baseDamage)
                                val newEffectiveness = maxOf(0.0f, armorEffectiveness - (enchant.level * 0.15f))
                                val newDamageTaken = baseDamage * (1.0f - newEffectiveness)
                                val extra = newDamageTaken - damageTaken
                                if (extra > 0.0f) {
                                    victim.hurt(victim.damageSources().magic(), extra)
                                }
                            }
                        }

                        // --- SWEEPING EDGE: AoE damage ---
                        "sweeping_edge" -> {
                            val range = 1.0 + enchant.level * 0.5
                            val nearby = world.getEntitiesOfClass(LivingEntity::class.java, victim.boundingBox.inflate(range)) { 
                                it != attacker && it != victim && it.isAlive 
                            }
                            val sweepDamage = baseDamage * (0.5f + 0.1f * enchant.level)
                            for (target in nearby) {
                                target.hurt(victim.damageSources().mobAttack(attacker), sweepDamage)
                            }
                        }

                        // --- CURSE OF BINDING: Massive slowness ---
                        "curse_of_binding" -> {
                            victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 100, 4, false, true))
                        }

                        // --- CURSE OF VANISHING: Teleport around victim while invisible ---
                        "curse_of_vanishing" -> {
                            if (attacker.hasEffect(MobEffects.INVISIBILITY)) {
                                val random = attacker.random
                                val oldX = attacker.x
                                val oldY = attacker.y
                                val oldZ = attacker.z
                                for (i in 0..15) {
                                    val dx = (random.nextDouble() - 0.5) * 16.0
                                    val dy = (random.nextInt(5) - 2).toDouble()
                                    val dz = (random.nextDouble() - 0.5) * 16.0
                                    val targetX = victim.x + dx
                                    val targetY = victim.y + dy
                                    val targetZ = victim.z + dz
                                    
                                    val vehicle = attacker.vehicle
                                    if (vehicle != null) attacker.stopRiding()
                                    
                                    if (attacker.randomTeleport(targetX, targetY, targetZ, true)) {
                                        world.playSound(null, oldX, oldY, oldZ, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f)
                                        world.playSound(null, attacker.x, attacker.y, attacker.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f)
                                        
                                        if (vehicle != null) {
                                            vehicle.setPos(attacker.x, attacker.y, attacker.z)
                                            attacker.startRiding(vehicle, true, false)
                                        }
                                        break
                                    } else {
                                        if (vehicle != null) attacker.startRiding(vehicle, true, false)
                                    }
                                }
                            }
                        }

                        // --- AQUA AFFINITY: 5x damage when in water ---
                        "aqua_affinity" -> {
                            if (attacker.isInWater) {
                                val extra = baseDamage * 4.0f
                                victim.hurt(victim.damageSources().mobAttack(attacker), extra)
                            }
                        }

                        // --- SHARPNESS: Extra flat damage ---
                        "sharpness" -> {
                            val extra = (0.5 + 0.5 * enchant.level).toFloat()
                            victim.hurt(victim.damageSources().mobAttack(attacker), extra)
                        }

                        // --- SMITE: Extra damage to undead ---
                        "smite" -> {
                            if (victim.type in EnchantmentPool.UNDEAD_TYPES) {
                                val extra = (2.5 * enchant.level).toFloat()
                                victim.hurt(victim.damageSources().mobAttack(attacker), extra)
                            }
                        }

                        // --- BANE OF ARTHROPODS: Extra damage + slowness to arthropods ---
                        "bane_of_arthropods" -> {
                            if (victim.type in EnchantmentPool.ARTHROPOD_TYPES) {
                                val extra = (2.5 * enchant.level).toFloat()
                                victim.hurt(victim.damageSources().mobAttack(attacker), extra)
                                val slowDur = (20 * (1 + Random.nextDouble() * 1.5 * enchant.level)).toInt()
                                victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, slowDur, 3, false, true))
                            }
                        }

                        // --- FIRE ASPECT: Set target on fire ---
                        "fire_aspect" -> {
                            victim.igniteForSeconds((4 * enchant.level).toFloat())
                        }

                        // --- KNOCKBACK: Extra knockback ---
                        "knockback" -> {
                            val dx = victim.x - attacker.x
                            val dz = victim.z - attacker.z
                            val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(1.0)
                            val power = 0.67 * enchant.level
                            victim.knockback(power, dx / dist, dz / dist, victim.damageSources().mobAttack(attacker), baseDamage)
                        }

                        // --- POWER: Extra arrow damage ---
                        "power" -> {
                            val isRangedShooter = EnchantmentPool.RANGED_SHOOTER_TYPES.contains(attacker.type)
                            if (isRangedShooter) {
                                val isProjectile = source.directEntity is net.minecraft.world.entity.projectile.Projectile
                                if (isProjectile) {
                                    val extra = kotlin.math.ceil(baseDamage * 0.25f * (enchant.level + 1)).toFloat()
                                    victim.hurt(victim.damageSources().mobAttack(attacker), extra)
                                } else {
                                    val mainItem = attacker.mainHandItem.item
                                    val hasBow = mainItem is net.minecraft.world.item.BowItem || mainItem is net.minecraft.world.item.CrossbowItem
                                    if (!hasBow) {
                                        // Full charge damage base is 6.0
                                        val fullChargeBase = 6.0f
                                        val extra = kotlin.math.ceil(fullChargeBase * 0.25f * (enchant.level + 1)).toFloat()
                                        victim.hurt(victim.damageSources().mobAttack(attacker), extra)
                                    }
                                }
                            }
                        }

                        // --- PUNCH: Extra knockback ---
                        "punch" -> {
                            val isRangedShooter = EnchantmentPool.RANGED_SHOOTER_TYPES.contains(attacker.type)
                            if (isRangedShooter) {
                                val dx = victim.x - attacker.x
                                val dz = victim.z - attacker.z
                                val dist = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(1.0)
                                val power = 0.5 * enchant.level
                                victim.knockback(power, dx / dist, dz / dist, victim.damageSources().mobAttack(attacker), baseDamage)
                            }
                        }

                        // --- FLAME: Set target on fire ---
                        "flame" -> {
                            val isRangedShooter = EnchantmentPool.RANGED_SHOOTER_TYPES.contains(attacker.type)
                            if (isRangedShooter) {
                                victim.igniteForSeconds(5f)
                            }
                        }

                        // --- IMPALING: Extra damage to aquatic mobs ---
                        "impaling" -> {
                            if (victim.type in EnchantmentPool.AQUATIC_TYPES) {
                                val extra = (2.5 * enchant.level).toFloat()
                                victim.hurt(victim.damageSources().mobAttack(attacker), extra)
                            }
                        }

                        // --- CHANNELING: Lightning strike ---
                        "channeling" -> {
                            try {
                                val lightning = EntityTypes.LIGHTNING_BOLT.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND)
                                if (lightning != null) {
                                    lightning.setPos(victim.x, victim.y, victim.z)
                                    world.addFreshEntity(lightning)
                                }
                            } catch (_: Exception) { }
                        }

                        // --- QUICK CHARGE: Melee follow-up strike ---
                        "quick_charge" -> {
                            val level = enchant.level
                            val delay = 15 - level * 2
                            MobEnchant.scheduleTask(delay) {
                                if (!attacker.isAlive || !victim.isAlive) return@scheduleTask
                                val distSq = attacker.distanceToSqr(victim)
                                if (distSq < 16.0) {
                                    val strikeDamage = (2 + level).toFloat()
                                    victim.hurt(victim.damageSources().mobAttack(attacker), strikeDamage)
                                    world.sendParticles(ParticleTypes.CRIT, victim.x, victim.y + 1.0, victim.z, 5, 0.3, 0.3, 0.3, 0.02)
                                    world.playSound(null, victim.x, victim.y, victim.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 0.8f, 1.0f)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Entity removed mid-processing
                }
            }
        } finally {
            processingDamage.remove(victim.id)
        }
    }

    // ========================================================================
    // DEFENSIVE EFFECTS — When an enchanted mob takes damage
    // ========================================================================
    @JvmStatic
    var isCustomDamage = false

    fun calculateDamageReduction(source: DamageSource, enchants: List<MobEnchantment>): Float {
        var totalDamageReduction = 0f
        for (enchant in enchants) {
            when (enchant.id) {
                "protection" -> totalDamageReduction += enchant.level * 0.15f
                "fire_protection" -> if (source.`is`(DamageTypeTags.IS_FIRE)) totalDamageReduction += enchant.level * 0.15f
                "blast_protection" -> if (source.`is`(DamageTypeTags.IS_EXPLOSION)) totalDamageReduction += (enchant.level * 0.15f).coerceAtMost(0.60f)
                "projectile_protection" -> if (source.`is`(DamageTypeTags.IS_PROJECTILE)) totalDamageReduction += (enchant.level * 0.15f).coerceAtMost(0.60f)
            }
        }
        return totalDamageReduction
    }

    fun calculateBlastKbReduction(source: DamageSource, enchants: List<MobEnchantment>): Double {
        var blastKbReduction = 0.0
        if (source.`is`(DamageTypeTags.IS_EXPLOSION)) {
            for (enchant in enchants) {
                if (enchant.id == "blast_protection") {
                    blastKbReduction += (enchant.level * 0.15).coerceAtMost(0.60)
                }
            }
        }
        return blastKbReduction
    }

    fun handleDefensiveHurt(
        victim: LivingEntity,
        source: DamageSource,
        damageTaken: Float,
        enchants: List<MobEnchantment>,
    ) {
        val attacker = source.entity as? LivingEntity

        for (enchant in enchants) {
            try {
                when (enchant.id) {
                    // --- THORNS: Reflect damage back to attacker ---
                    "thorns" -> {
                        if (attacker == null || !attacker.isAlive) continue
                        val thornsChance = enchant.level * 0.15
                        if (Random.nextDouble() < thornsChance) {
                            val thornsDamage = (Random.nextInt(4) + 1).toFloat()
                            MobEnchant.scheduleTask(1) {
                                if (attacker.isAlive && victim.isAlive) {
                                    attacker.hurt(victim.damageSources().thorns(victim), thornsDamage)
                                    if (victim.level() is ServerLevel) {
                                        (victim.level() as ServerLevel).sendParticles(
                                            ParticleTypes.DAMAGE_INDICATOR, 
                                            attacker.x, attacker.y + attacker.bbHeight / 2.0, attacker.z, 
                                            3, 0.2, 0.2, 0.2, 0.0
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- CURSE OF VANISHING: Go invisible when hit ---
                    "curse_of_vanishing" -> {
                        if (Random.nextDouble() < 0.5) {
                            victim.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false))
                            victim.addEffect(MobEffectInstance(MobEffects.SPEED, 200, 9, false, false))
                            
                            val vehicle = victim.vehicle
                            if (vehicle is LivingEntity) {
                                vehicle.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false))
                                vehicle.addEffect(MobEffectInstance(MobEffects.SPEED, 200, 9, false, false))
                            }
                            
                            for (passenger in victim.passengers) {
                                if (passenger is LivingEntity && passenger !is net.minecraft.world.entity.Display.TextDisplay) {
                                    passenger.addEffect(MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, false, false))
                                    passenger.addEffect(MobEffectInstance(MobEffects.SPEED, 200, 9, false, false))
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Silently handle
            }
        }
    }

    // ========================================================================
    // MENDING — Heal attacker on dealing damage
    // ========================================================================
    fun handleMending(attacker: LivingEntity, damageTaken: Float) {
        val mending = attacker.getMobEnchant("mending") ?: return
        val healAmount = (damageTaken * 0.20f).coerceIn(2.0f, 40.0f)
        attacker.heal(healAmount)
        if (attacker.level() is ServerLevel) {
            (attacker.level() as ServerLevel).sendParticles(
                ParticleTypes.HEART, attacker.x, attacker.y + 1.0, attacker.z, 2, 0.3, 0.3, 0.3, 0.0
            )
        }
    }

    // ========================================================================
    // MULTISHOT MELEE CRIT — 10% chance for 3x damage on melee hits
    // ========================================================================
    fun handleMultishotMeleeCrit(
        victim: LivingEntity,
        attacker: LivingEntity,
        baseDamage: Float,
        world: ServerLevel,
    ) {
        if (victim.id in activeMultishots) return
        val multishot = attacker.getMobEnchant("multishot") ?: return

        // Only melee mobs (not ranged shooters) get the crit
        if (attacker.type in EnchantmentPool.RANGED_SHOOTER_TYPES) return

        if (Random.nextDouble() < 0.10) {
            activeMultishots.add(victim.id)
            try {
                val extraDamage = baseDamage * 2.0f
                victim.hurt(victim.damageSources().mobAttack(attacker), extraDamage)
                world.sendParticles(ParticleTypes.CRIT, victim.x, victim.y + 1.0, victim.z, 10, 0.3, 0.5, 0.3, 0.05)
                world.playSound(null, victim.x, victim.y, victim.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0f, 1.0f)

                // Broadcast critical hit message
                val msg = net.minecraft.network.chat.Component.literal("")
                    .append(net.minecraft.network.chat.Component.literal("✦ ").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.BOLD))
                    .append(net.minecraft.network.chat.Component.literal("Critical Multishot! ").withStyle(net.minecraft.ChatFormatting.RED))
                    .append(net.minecraft.network.chat.Component.literal("An enchanted mob dealt ").withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append(net.minecraft.network.chat.Component.literal("3x damage").withStyle(net.minecraft.ChatFormatting.YELLOW))
                    .append(net.minecraft.network.chat.Component.literal("!").withStyle(net.minecraft.ChatFormatting.GRAY))
                world.server.playerList.broadcastSystemMessage(msg, false)
            } catch (_: Exception) { }
            activeMultishots.remove(victim.id)
        }
    }

    // ========================================================================
    // INFINITY SHIELD — Blocks all damage until broken
    // Returns true if damage should be CANCELLED
    // ========================================================================
    fun handleInfinityShield(
        victim: LivingEntity,
        source: DamageSource,
        world: ServerLevel,
    ): Boolean {
        val infinity = victim.getMobEnchant("infinity") ?: return false
        if (victim.isInfinityBroken()) return false

        val attacker = source.entity
        val now = System.currentTimeMillis()

        // Handle window for critical hit
        val windowStart = infinityWindowStart[victim.id]
        if (windowStart != null) {
            if (now - windowStart <= 10000L) {
                // Window is active, check for crit
                val isCrit = attacker is net.minecraft.world.entity.player.Player &&
                             attacker.fallDistance > 0.0f &&
                             !attacker.onGround()
                if (isCrit) {
                    // Break the shield
                    victim.setInfinityBroken(true)
                    infinityHits.remove(victim.id)
                    infinityWindowStart.remove(victim.id)
                    lastInfinityAlert.remove(victim.id)

                    world.playSound(null, victim.x, victim.y, victim.z, SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1.0f, 1.0f)
                    world.sendParticles(ParticleTypes.ENCHANTED_HIT, victim.x, victim.y + 1.0, victim.z, 30, 0.5, 0.5, 0.5, 0.2)
                    
                    if (attacker is net.minecraft.server.level.ServerPlayer) {
                        val msg = net.minecraft.network.chat.Component.literal("")
                            .append(net.minecraft.network.chat.Component.literal("✦ ").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.BOLD))
                            .append(net.minecraft.network.chat.Component.literal("Infinity Shield Shattered!").withStyle(net.minecraft.ChatFormatting.GREEN))
                        attacker.sendSystemMessage(msg)
                    }

                    return false // Let the damage through
                } else {
                    // Tell player they need a crit
                    if (attacker is net.minecraft.server.level.ServerPlayer) {
                        val lastAlert = lastInfinityAlert[victim.id] ?: 0L
                        if (now - lastAlert > 2000L) {
                            lastInfinityAlert[victim.id] = now
                            val remaining = 10 - (now - windowStart) / 1000
                            val msg = net.minecraft.network.chat.Component.literal("")
                                .append(net.minecraft.network.chat.Component.literal("✦ ").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.BOLD))
                                .append(net.minecraft.network.chat.Component.literal("Shield is unstable! Land a Critical Hit in ${remaining}s!").withStyle(net.minecraft.ChatFormatting.GOLD))
                            attacker.sendSystemMessage(msg)
                        }
                    } else if (attacker is LivingEntity && attacker !is net.minecraft.world.entity.player.Player) {
                        val hits = (infinityHits[victim.id] ?: 0) + 1
                        if (hits >= 10) {
                            victim.setInfinityBroken(true)
                            infinityHits.remove(victim.id)
                            infinityWindowStart.remove(victim.id)
                            lastInfinityAlert.remove(victim.id)

                            world.playSound(null, victim.x, victim.y, victim.z, SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1.0f, 1.0f)
                            world.sendParticles(ParticleTypes.ENCHANTED_HIT, victim.x, victim.y + 1.0, victim.z, 30, 0.5, 0.5, 0.5, 0.2)
                            
                            return false
                        } else {
                            infinityHits[victim.id] = hits
                            val lastAlert = lastInfinityAlert[victim.id] ?: 0L
                            if (now - lastAlert > 2000L) {
                                lastInfinityAlert[victim.id] = now
                                world.playSound(null, victim.x, victim.y, victim.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.5f, 2.0f)
                            }
                        }
                    }
                }
            } else {
                // Window expired, reset hits
                infinityWindowStart.remove(victim.id)
                infinityHits.remove(victim.id)
                if (attacker is net.minecraft.server.level.ServerPlayer) {
                    val msg = net.minecraft.network.chat.Component.literal("")
                        .append(net.minecraft.network.chat.Component.literal("✦ ").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.BOLD))
                        .append(net.minecraft.network.chat.Component.literal("Window missed. Infinity Shield restabilized.").withStyle(net.minecraft.ChatFormatting.RED))
                    attacker.sendSystemMessage(msg)
                }
            }
        } else {
            // Not in window, track hits
            if (attacker is net.minecraft.world.entity.player.Player) {
                val hits = (infinityHits[victim.id] ?: 0) + 1
                if (hits >= 5) {
                    // Open window
                    infinityWindowStart[victim.id] = now
                    infinityHits.remove(victim.id)
                    world.playSound(null, victim.x, victim.y, victim.z, SoundEvents.ANVIL_USE, SoundSource.HOSTILE, 0.5f, 1.0f)
                    
                    if (attacker is net.minecraft.server.level.ServerPlayer) {
                        val msg = net.minecraft.network.chat.Component.literal("")
                            .append(net.minecraft.network.chat.Component.literal("✦ ").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.BOLD))
                            .append(net.minecraft.network.chat.Component.literal("Infinity Shield is vulnerable! Land a Critical Hit within 10 seconds!").withStyle(net.minecraft.ChatFormatting.GOLD))
                        attacker.sendSystemMessage(msg)
                    }
                } else {
                    infinityHits[victim.id] = hits
                    val lastAlert = lastInfinityAlert[victim.id] ?: 0L
                    if (now - lastAlert > 2000L) {
                        lastInfinityAlert[victim.id] = now
                        if (attacker is net.minecraft.server.level.ServerPlayer) {
                            val msg = net.minecraft.network.chat.Component.literal("")
                                .append(net.minecraft.network.chat.Component.literal("✦ ").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE, net.minecraft.ChatFormatting.BOLD))
                                .append(net.minecraft.network.chat.Component.literal("Infinity Shield").withStyle(net.minecraft.ChatFormatting.YELLOW))
                                .append(net.minecraft.network.chat.Component.literal(" absorbed the hit! (${hits}/5 to destablize)").withStyle(net.minecraft.ChatFormatting.GRAY))
                            attacker.sendSystemMessage(msg)
                        }
                    }
                }
            } else if (attacker is LivingEntity) {
                val hits = (infinityHits[victim.id] ?: 0) + 1
                if (hits >= 10) {
                    victim.setInfinityBroken(true)
                    infinityHits.remove(victim.id)
                    infinityWindowStart.remove(victim.id)
                    lastInfinityAlert.remove(victim.id)

                    world.playSound(null, victim.x, victim.y, victim.z, SoundEvents.SHIELD_BREAK, SoundSource.HOSTILE, 1.0f, 1.0f)
                    world.sendParticles(ParticleTypes.ENCHANTED_HIT, victim.x, victim.y + 1.0, victim.z, 30, 0.5, 0.5, 0.5, 0.2)
                    
                    return false
                } else {
                    infinityHits[victim.id] = hits
                    val lastAlert = lastInfinityAlert[victim.id] ?: 0L
                    if (now - lastAlert > 2000L) {
                        lastInfinityAlert[victim.id] = now
                        world.playSound(null, victim.x, victim.y, victim.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.5f, 2.0f)
                    }
                }
            } else {
                // Environment damage or other non-entity sources
                val lastAlert = lastInfinityAlert[victim.id] ?: 0L
                if (now - lastAlert > 2000L) {
                    lastInfinityAlert[victim.id] = now
                    world.playSound(null, victim.x, victim.y, victim.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.5f, 2.0f)
                }
            }
        }

        // Cancel the damage (if we haven't returned false above)
        world.sendParticles(ParticleTypes.WITCH, victim.x, victim.y + 1.0, victim.z, 8, 0.4, 0.5, 0.4, 0.02)
        return true // CANCEL the damage
    }

    // ========================================================================
    // UNBREAKING REVIVE — Chance to revive on fatal damage
    // Returns true if damage should be CANCELLED (revived)
    // ========================================================================
    fun handleUnbreakingRevive(
        victim: LivingEntity,
        damage: Float,
        world: ServerLevel,
    ): Boolean {
        val unbreaking = victim.getMobEnchant("unbreaking") ?: return false
        if (damage < victim.health) return false // Not fatal

        val surviveChance = unbreaking.level * 0.10
        if (Random.nextDouble() >= surviveChance) return false

        // Revive: heal to max
        victim.health = victim.maxHealth
        world.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, victim.x, victim.y + 1.0, victim.z, 30, 0.5, 1.0, 0.5, 0.1)
        world.playSound(null, victim.x, victim.y, victim.z, SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 1.0f, 1.0f)

        return true // CANCEL the damage (mob survived)
    }

    // ========================================================================
    // EFFECT ADDED — Called from Mixin when any effect is added to a mob.
    // ========================================================================
    @JvmStatic
    fun onEffectAdded(entity: LivingEntity, effect: MobEffectInstance) {
        // No longer breaks infinity shield
    }

    // ========================================================================
    // MULTISHOT RANGED — Spawn extra projectiles when a ranged mob fires
    // ========================================================================
    fun handleMultishotRanged(
        projectile: Projectile,
        owner: Mob,
        world: ServerLevel,
    ) {
        val multishot = owner.getMobEnchant("multishot") ?: return

        // Only ranged shooter types are eligible
        if (owner.type !in EnchantmentPool.RANGED_SHOOTER_TYPES &&
            projectile.type != EntityTypes.ARROW) return

        val vel = projectile.deltaMovement
        val speed = vel.length()
        if (speed < 0.05) return

        val angle = 0.174 // ~10 degrees in radians

        val velLeft = rotateY(vel, -angle)
        val velRight = rotateY(vel, angle)

        for (rotatedVel in listOf(velLeft, velRight)) {
            try {
                val extra = projectile.type.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND) ?: continue
                extra.setPos(projectile.x, projectile.y, projectile.z)
                extra.deltaMovement = rotatedVel
                if (extra is Projectile) {
                    extra.owner = owner
                }
                extra.addTag("mobenchant_extra_projectile")
                world.addFreshEntity(extra)
            } catch (_: Exception) { }
        }
    }

    // ========================================================================
    // QUICK CHARGE RANGED — Simulate faster reload by shooting spaced-out extra projectiles
    // ========================================================================
    fun handleQuickChargeRanged(
        projectile: Projectile,
        owner: Mob,
        world: ServerLevel,
    ) {
        val quickCharge = owner.getMobEnchant("quick_charge") ?: return
        val level = quickCharge.level
        val vel = projectile.deltaMovement
        val typeId = projectile.type

        // To simulate a faster reload (and not a "minigun"), we space the extra shots out 
        // over the duration of their normal attack cooldown.
        // Level 1: +1 shot (15 ticks later)
        // Level 2: +2 shots (10, 20 ticks later)
        // Level 3: +3 shots (8, 16, 24 ticks later)
        
        val spacing = when (level) {
            1 -> 15
            2 -> 10
            else -> 8
        }

        for (j in 1..level) {
            val delay = spacing * j
            MobEnchant.scheduleTask(delay) {
                if (!owner.isAlive) return@scheduleTask
                try {
                    val extra = typeId.create(world, net.minecraft.world.entity.EntitySpawnReason.COMMAND) ?: return@scheduleTask
                    extra.setPos(owner.x, owner.eyeY, owner.z)
                    
                    // We also slightly adjust the aim towards the target if they still have one, 
                    // otherwise just fire in the same direction as the original
                    val target = owner.target
                    if (target != null && target.isAlive) {
                        val dx = target.x - owner.x
                        val dy = target.eyeY - 0.2 - owner.eyeY
                        val dz = target.z - owner.z
                        val dist = kotlin.math.sqrt(dx * dx + dz * dz)
                        
                        val speed = vel.length()
                        
                        // Basic ballistic trajectory adjustment
                        extra.deltaMovement = net.minecraft.world.phys.Vec3(
                            (dx / dist) * speed,
                            (dy / dist) * speed + 0.1,
                            (dz / dist) * speed
                        )
                    } else {
                        extra.deltaMovement = vel
                    }
                    
                    if (extra is Projectile) {
                        extra.owner = owner
                    }
                    extra.addTag("mobenchant_extra_projectile")
                    world.addFreshEntity(extra)
                } catch (_: Exception) { }
            }
        }
    }

    // ========================================================================
    // Cleanup — remove entity from tracking maps when they die
    // ========================================================================
    fun onEntityDeath(entityId: Int) {
        lastInfinityAlert.remove(entityId)
        activeMultishots.remove(entityId)
        processingDamage.remove(entityId)
        infinityHits.remove(entityId)
        infinityWindowStart.remove(entityId)
    }

    // ========================================================================
    // UTILITY — Rotate a Vec3 around the Y axis
    // ========================================================================
    private fun rotateY(vec: Vec3, angleRad: Double): Vec3 {
        val cos = Math.cos(angleRad)
        val sin = Math.sin(angleRad)
        return Vec3(
            vec.x * cos - vec.z * sin,
            vec.y,
            vec.x * sin + vec.z * cos,
        )
    }

    // ========================================================================
    // CONTINUOUS EFFECTS — Respiration and Depth Strider
    // ========================================================================
    fun tickContinuousEffects(entity: Mob, enchants: List<MobEnchantment>) {
        val respiration = enchants.find { it.id == "respiration" }
        val depthStrider = enchants.find { it.id == "depth_strider" }
        val windBurst = enchants.find { it.id == "wind_burst" }
        val lunge = enchants.find { it.id == "lunge" }
        
        var waterSpeedBoost = 0.0

        if (lunge != null) {
            val target = entity.target
            var cooldown = entity.getLungeCooldown()
            var prepTicks = entity.getLungePrepTicks()
            var lunging = entity.isLunging()

            if (cooldown > 0) {
                cooldown--
                entity.setLungeCooldown(cooldown)
            }

            if (target != null && target.isAlive) {
                if (!lunging && cooldown == 0 && prepTicks == 0) {
                    val dist = entity.distanceToSqr(target)
                    val range = 4.0 + (lunge.level * 12.0)
                    if (dist in 9.0..(range * range) && entity.hasLineOfSight(target)) {
                        entity.setLungePrepTicks(20)
                        entity.addEffect(MobEffectInstance(MobEffects.SLOWNESS, 20, 10, false, false))
                    }
                }
            }

            if (prepTicks > 0) {
                if (target != null && entity.distanceToSqr(target) < 6.25) {
                    // Target got too close during prep (within 2.5 blocks). Cancel lunge and attack normally.
                    entity.setLungePrepTicks(0)
                    entity.setLungeCooldown(20)
                    entity.removeEffect(MobEffects.SLOWNESS)
                    prepTicks = 0
                } else {
                    prepTicks--
                    entity.setLungePrepTicks(prepTicks)
                    
                    if (entity.level() is ServerLevel) {
                        val sl = entity.level() as ServerLevel
                        sl.sendParticles(ParticleTypes.ENCHANT, entity.x, entity.y + 1.0, entity.z, 2, 0.5, 0.5, 0.5, 0.1)
                    }

                    if (prepTicks == 0 && target != null && target.isAlive) {
                        lunging = true
                        entity.setLunging(true)
                        
                        val velocity = kotlin.math.ceil(2.0 * lunge.level).toDouble()
                        
                        // Predict target movement based on their current velocity
                        val dx0 = target.x - entity.x
                        val dz0 = target.z - entity.z
                        val initialDist = kotlin.math.sqrt(dx0 * dx0 + dz0 * dz0)
                        val travelTicks = (initialDist / (velocity * 0.5)).coerceIn(5.0, 20.0)
                        
                        val predictedX = target.x + target.deltaMovement.x * travelTicks
                        val predictedZ = target.z + target.deltaMovement.z * travelTicks
                        
                        val dx = predictedX - entity.x
                        val dz = predictedZ - entity.z
                        val dist = kotlin.math.sqrt(dx * dx + dz * dz)
                        
                        if (dist > 0) {
                            // Calculate parabolic trajectory accounting for horizontal air drag (0.91)
                            val drag = 0.91
                            val maxDist = velocity / (1.0 - drag)
                            val realDist = dist.coerceAtMost(maxDist * 0.95)
                            val travelTime = kotlin.math.ln(1.0 - (1.0 - drag) * realDist / velocity) / kotlin.math.ln(drag)
                            
                            val dy = target.y - entity.y
                            val gravity = 0.08
                            val calculatedYVelocity = (dy / travelTime) + 0.5 * gravity * travelTime
                            val yVelocity = calculatedYVelocity.coerceIn(0.2, 1.5) // Allow bigger jumps for long distances

                            entity.deltaMovement = Vec3(
                                (dx / dist) * velocity,
                                yVelocity,
                                (dz / dist) * velocity
                            )
                            val rotY = (kotlin.math.atan2(dz, dx) * (180.0 / kotlin.math.PI)).toFloat() - 90.0f
                            val horizDist = kotlin.math.sqrt(dx * dx + dz * dz)
                            val rotX = (-(kotlin.math.atan2(yVelocity, horizDist) * (180.0 / kotlin.math.PI))).toFloat()
                            entity.yRot = rotY
                            entity.yHeadRot = rotY
                            entity.yBodyRot = rotY
                            entity.xRot = rotX
                        }
                        
                        entity.level().playSound(null, entity.x, entity.y, entity.z, SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1.0f, 1.0f)
                    }
                }
            }

            if (lunging) {
                val horizSpeed = kotlin.math.sqrt(entity.deltaMovement.x * entity.deltaMovement.x + entity.deltaMovement.z * entity.deltaMovement.z)
                if (horizSpeed < 0.2 && entity.onGround()) {
                    entity.setLunging(false)
                    entity.setLungeCooldown(1)
                } else {
                    val velocity = kotlin.math.ceil(2.0 * lunge.level).toDouble()
                    // Handle attributes safely
                    val baseDamageAttr = entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    val baseDamage = baseDamageAttr?.value?.toFloat() ?: 2.0f
                    val damage = (velocity * baseDamage * (lunge.level / 2.0)).toFloat()

                    val hitBox = entity.boundingBox.inflate(0.5)
                    val victims = entity.level().getEntitiesOfClass(LivingEntity::class.java, hitBox) { it != entity && it.isAlive }
                    var hitSomeone = false
                    
                    for (victim in victims) {
                        if (victim is Mob && entity is Mob && victim.target == entity.target && victim != entity.target) continue // Skip allies hitting same target, but hurt actual enemies
                        victim.hurt(entity.damageSources().mobAttack(entity), damage)
                    }
                    
                    if (entity.level() is ServerLevel) {
                        val sl = entity.level() as ServerLevel
                        val look = entity.lookAngle
                        val dirX = look.x
                        val dirY = look.y
                        val dirZ = look.z
                            val length = 3.0
                            val steps = 10
                            for (i in 1..steps) {
                                val fraction = i.toDouble() / steps
                                val dist = fraction * length
                                val spread = 0.25 * (1.0 - fraction) // Thick at base, sharp at tip
                                
                                val px = entity.x + dirX * dist
                                val py = entity.y + entity.eyeHeight / 2.0 + dirY * dist
                                val pz = entity.z + dirZ * dist
                                
                                sl.sendParticles(ParticleTypes.CRIT, px, py, pz, 2, spread, spread, spread, 0.0)
                                if (i == steps) {
                                    sl.sendParticles(ParticleTypes.ENCHANTED_HIT, px, py, pz, 2, 0.0, 0.0, 0.0, 0.05)
                                }
                            }
                    }
                }
            }
        }
        
        if (windBurst != null) {
            val fallDist = entity.fallDistance
            if (fallDist > 0) {
                val target = entity.target
                if (target != null && target.isAlive) {
                    val dx = target.x - entity.x
                    val dz = target.z - entity.z
                    val dist = kotlin.math.sqrt(dx * dx + dz * dz)
                    
                    if (dist > 0.5) {
                        val speed = 0.03
                        var newDx = entity.deltaMovement.x + (dx / dist) * speed
                        var newDz = entity.deltaMovement.z + (dz / dist) * speed
                        
                        val currentHzSpeed = kotlin.math.sqrt(newDx * newDx + newDz * newDz)
                        if (currentHzSpeed > 0.4) {
                            newDx = (newDx / currentHzSpeed) * 0.4
                            newDz = (newDz / currentHzSpeed) * 0.4
                        }
                        
                        entity.deltaMovement = Vec3(newDx, entity.deltaMovement.y, newDz)
                    }
                    
                    val dy = target.eyeY - entity.eyeY
                    val rotY = (kotlin.math.atan2(dz, dx) * (180.0 / kotlin.math.PI)).toFloat() - 90.0f
                    entity.yRot = rotY
                    entity.yHeadRot = rotY
                    entity.yBodyRot = rotY
                    
                    val horizDist = kotlin.math.sqrt(dx * dx + dz * dz)
                    val rotX = (-(kotlin.math.atan2(dy, horizDist) * (180.0 / kotlin.math.PI))).toFloat()
                    entity.xRot = rotX
                    
                    val dist3D = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
                    if (dist3D <= 2.5 && fallDist > 1.0) {
                        val world = entity.level()
                        if (world is ServerLevel && entity.doHurtTarget(world, target)) {
                            entity.fallDistance = 0.0
                        }
                    }
                }
            }
        }
        
        if (respiration != null) {
            waterSpeedBoost += 0.1 * respiration.level
            if (entity.onGround()) {
                val durations = intArrayOf(1200, 3600, 72000)
                val maxDur = durations[min(respiration.level - 1, durations.size - 1)]
                entity.setRespirationTicks(maxDur)
            } else if (entity.isInWater) {
                var ticks = entity.getRespirationTicks()
                if (ticks > 0) {
                    ticks--
                    entity.setRespirationTicks(ticks)
                    entity.addEffect(MobEffectInstance(MobEffects.WATER_BREATHING, 10, 0, false, false, false))
                }
            }
        }
        
        if (depthStrider != null) {
            waterSpeedBoost += 0.3 * depthStrider.level
        }
        
        if (waterSpeedBoost > 0.0 && entity.isInWater && !entity.isSwimming) {
            val vel = entity.deltaMovement
            val mult = 1.0 + (waterSpeedBoost * 0.05)
            entity.deltaMovement = Vec3(vel.x * mult, vel.y, vel.z * mult)
        }
    }

    // ========================================================================
    // SOUL SPEED — Gain speed on nearby entity death
    // ========================================================================
    fun handleSoulSpeedDeath(deadEntity: LivingEntity, world: ServerLevel) {
        val box = deadEntity.boundingBox.inflate(200.0)
        val nearbyMobs = world.getEntitiesOfClass(Mob::class.java, box) { it.isAlive }
        
        for (mob in nearbyMobs) {
            val soulSpeed = mob.getMobEnchant("soul_speed") ?: continue
            val level = soulSpeed.level
            val range = if (level == 1) 100.0 else 200.0
            
            if (mob.distanceToSqr(deadEntity) <= range * range) {
                val speedGain = if (level == 1) 1 else 2
                val cap = if (level == 1) 10 else 20
                
                val currentEffect = mob.getEffect(MobEffects.SPEED)
                val currentAmplifier = currentEffect?.amplifier ?: -1
                
                val newAmplifier = min(currentAmplifier + speedGain, cap - 1)
                
                mob.addEffect(MobEffectInstance(MobEffects.SPEED, 20000000, newAmplifier, false, false))
            }
        }
    }
}
