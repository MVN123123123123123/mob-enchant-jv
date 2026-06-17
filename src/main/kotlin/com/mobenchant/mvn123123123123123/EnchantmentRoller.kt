package com.mobenchant.mvn123123123123123

import kotlin.random.Random

// ============================================================================
// Random selection & enchantment rolling utilities
// ============================================================================
object EnchantmentRoller {

    /**
     * Weighted random pick from a list of [WeightedEntry] objects.
     */
    fun <T> weightedRandom(entries: List<WeightedEntry<T>>): T {
        val totalWeight = entries.sumOf { it.weight }
        var roll = Random.nextInt(totalWeight)
        for (entry in entries) {
            roll -= entry.weight
            if (roll < 0) return entry.value
        }
        return entries.last().value
    }

    /**
     * Roll a level for an enchantment with the given [maxLevel].
     * Higher levels are exponentially rarer.
     */
    fun rollLevel(maxLevel: Int): Int {
        val candidates = (1..maxLevel).map { lvl ->
            WeightedEntry(lvl, EnchantmentPool.LEVEL_WEIGHTS[lvl - 1])
        }
        return weightedRandom(candidates)
    }

    /**
     * Roll how many enchantments the mob should receive (1–5).
     */
    fun rollEnchantCount(): Int {
        return weightedRandom(EnchantmentPool.COUNT_WEIGHTS)
    }

    /**
     * Pick [count] unique random enchantments from the pool, each with a rolled level.
     * Optionally exclude enchantments already present (by ID).
     */
    fun rollEnchantments(count: Int, excludeIds: Set<String> = emptySet(), onlyDefensive: Boolean = false): List<MobEnchantment> {
        val available = EnchantmentPool.ALL.filter { 
            it.id !in excludeIds && (!onlyDefensive || it.category == "defensive")
        }.toMutableList()
        
        val picked = mutableListOf<MobEnchantment>()
        
        for (i in 0 until count) {
            if (available.isEmpty()) break
            
            val weights = available.map { def ->
                var w = 10
                if (count > 1) {
                    if (def.id == "wind_burst" && picked.any { it.id == "density" }) w += 50
                    else if (def.id == "density" && picked.any { it.id == "wind_burst" }) w += 50
                    else if (def.id == "wind_burst" || def.id == "density") w += 20
                }
                WeightedEntry(def, w)
            }
            
            val pickedDef = weightedRandom(weights)
            available.remove(pickedDef)
            
            picked.add(
                MobEnchantment(
                    id = pickedDef.id,
                    level = rollLevel(pickedDef.maxLevel),
                    maxLevel = pickedDef.maxLevel,
                    category = pickedDef.category,
                )
            )
        }
        return picked
    }

    /**
     * Calculate a "power score" for a set of enchantments.
     * Score = sum of all enchant levels. Used to scale bonus loot and XP.
     */
    fun calculatePowerScore(enchants: List<MobEnchantment>): Int {
        return enchants.sumOf { it.level }
    }

    /**
     * Look up an enchantment definition by ID.
     * Supports exact, prefix, and substring matching.
     */
    fun findEnchantDef(query: String): EnchantDef? {
        val lower = query.lowercase().replace(" ", "_")
        // Exact match first
        EnchantmentPool.ALL.find { it.id == lower }?.let { return it }
        // Prefix match
        EnchantmentPool.ALL.find { it.id.startsWith(lower) }?.let { return it }
        // Contains match
        return EnchantmentPool.ALL.find { it.id.contains(lower) }
    }
}
