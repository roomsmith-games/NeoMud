package com.neomud.server.game.combat

import com.neomud.server.game.GameConfig
import com.neomud.shared.model.ActiveEffect
import com.neomud.shared.model.EffectType
import com.neomud.shared.model.Stats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombatUtilsTest {

    private val parryMax = GameConfig.Combat.PARRY_MAX_CHANCE
    private val parryDiv = GameConfig.Combat.PARRY_STAT_DIVISOR
    private val dodgeMax = GameConfig.Combat.DODGE_MAX_CHANCE
    private val dodgeDiv = GameConfig.Combat.DODGE_STAT_DIVISOR
    private val parryRedBase = GameConfig.Combat.PARRY_REDUCTION_BASE
    private val parryRedDiv = GameConfig.Combat.PARRY_REDUCTION_STR_DIVISOR

    // ─── playerParry (STR-scaled, class-gated upstream) ────────────

    @Test
    fun testPlayerParryZeroStrength() {
        val stats = Stats(strength = 0, agility = 30, intellect = 30, willpower = 30, health = 30, charm = 30)
        assertEquals(0.0, CombatUtils.playerParry(stats))
    }

    @Test
    fun testPlayerParryWarriorMinStrength() {
        val stats = Stats(strength = 20, agility = 30, intellect = 30, willpower = 30, health = 30, charm = 30)
        val expected = 20.0 / parryDiv * parryMax
        assertTrue(kotlin.math.abs(CombatUtils.playerParry(stats) - expected) < 1e-9)
    }

    @Test
    fun testPlayerParryMidStrength() {
        val stats = Stats(strength = 50, agility = 30, intellect = 30, willpower = 30, health = 30, charm = 30)
        val expected = 50.0 / parryDiv * parryMax
        assertTrue(kotlin.math.abs(CombatUtils.playerParry(stats) - expected) < 1e-9)
    }

    @Test
    fun testPlayerParryMaxStrength() {
        val stats = Stats(strength = 100, agility = 30, intellect = 30, willpower = 30, health = 30, charm = 30)
        val expected = parryMax
        assertTrue(kotlin.math.abs(CombatUtils.playerParry(stats) - expected) < 1e-9)
    }

    @Test
    fun testPlayerParryScalesLinearly() {
        val low = CombatUtils.playerParry(Stats(strength = 30))
        val mid = CombatUtils.playerParry(Stats(strength = 60))
        val high = CombatUtils.playerParry(Stats(strength = 90))
        assertTrue(low < mid)
        assertTrue(mid < high)
        // Double the STR should give double the parry
        assertTrue(kotlin.math.abs(mid - low * 2) < 1e-9)
    }

    @Test
    fun testPlayerParryWithBuffedStats() {
        val base = Stats(strength = 40, agility = 30, intellect = 30, willpower = 30, health = 30, charm = 30)
        val buffed = CombatUtils.effectiveStats(base, listOf(
            ActiveEffect("Str Buff", EffectType.BUFF_STRENGTH, 10, magnitude = 10)
        ))
        val expected = 50.0 / parryDiv * parryMax
        assertTrue(kotlin.math.abs(CombatUtils.playerParry(buffed) - expected) < 1e-9)
    }

    // ─── parryReduction ─────────────────────────────────────────

    @Test
    fun testParryReductionLowStrength() {
        val stats = Stats(strength = 20, agility = 30, intellect = 30, willpower = 30, health = 30, charm = 30)
        assertEquals(parryRedBase + 20 / parryRedDiv, CombatUtils.parryReduction(stats))
    }

    @Test
    fun testParryReductionHighStrength() {
        val stats = Stats(strength = 100, agility = 30, intellect = 30, willpower = 30, health = 30, charm = 30)
        assertEquals(parryRedBase + 100 / parryRedDiv, CombatUtils.parryReduction(stats))
    }

    @Test
    fun testParryReductionMinimumStrength() {
        val stats = Stats(strength = 0, agility = 30, intellect = 30, willpower = 30, health = 30, charm = 30)
        assertEquals(parryRedBase, CombatUtils.parryReduction(stats))
    }

    // ─── playerEvasion (AGI-scaled, class-gated upstream) ───────

    @Test
    fun testPlayerEvasionZeroAgility() {
        val stats = Stats(strength = 30, agility = 0, intellect = 30, willpower = 30, health = 30, charm = 30)
        assertEquals(0.0, CombatUtils.playerEvasion(stats))
    }

    @Test
    fun testPlayerEvasionLowAgility() {
        val stats = Stats(strength = 30, agility = 20, intellect = 30, willpower = 30, health = 30, charm = 30)
        val expected = 20.0 / dodgeDiv * dodgeMax
        assertTrue(kotlin.math.abs(CombatUtils.playerEvasion(stats) - expected) < 1e-9)
    }

    @Test
    fun testPlayerEvasionMidAgility() {
        val stats = Stats(strength = 30, agility = 50, intellect = 30, willpower = 30, health = 30, charm = 30)
        val expected = 50.0 / dodgeDiv * dodgeMax
        assertTrue(kotlin.math.abs(CombatUtils.playerEvasion(stats) - expected) < 1e-9)
    }

    @Test
    fun testPlayerEvasionMaxAgility() {
        val stats = Stats(strength = 30, agility = 100, intellect = 30, willpower = 30, health = 30, charm = 30)
        val expected = dodgeMax
        assertTrue(kotlin.math.abs(CombatUtils.playerEvasion(stats) - expected) < 1e-9)
    }

    @Test
    fun testPlayerEvasionWithBuffedStats() {
        val base = Stats(strength = 30, agility = 40, intellect = 30, willpower = 30, health = 30, charm = 30)
        val buffed = CombatUtils.effectiveStats(base, listOf(
            ActiveEffect("Agi Buff", EffectType.BUFF_AGILITY, 10, magnitude = 10)
        ))
        val expected = 50.0 / dodgeDiv * dodgeMax
        assertTrue(kotlin.math.abs(CombatUtils.playerEvasion(buffed) - expected) < 1e-9)
    }

    // ─── effectiveStats ─────────────────────────────────────────

    @Test
    fun testEffectiveStatsWithBuffs() {
        val base = Stats(strength = 50, agility = 50, intellect = 50, willpower = 50, health = 50, charm = 50)
        val effects = listOf(
            ActiveEffect("Str Buff", EffectType.BUFF_STRENGTH, 10, magnitude = 5),
            ActiveEffect("Agi Buff", EffectType.BUFF_AGILITY, 10, magnitude = 3)
        )
        val effective = CombatUtils.effectiveStats(base, effects)
        assertEquals(55, effective.strength)
        assertEquals(53, effective.agility)
        assertEquals(50, effective.intellect) // unchanged
    }

    @Test
    fun testEffectiveStatsNoEffects() {
        val base = Stats(strength = 50, agility = 50, intellect = 50, willpower = 50, health = 50, charm = 50)
        val effective = CombatUtils.effectiveStats(base, emptyList())
        assertEquals(base, effective)
    }

    // ─── rollParry (statistical) ────────────────────────────────

    @Test
    fun testRollParryZeroChance() {
        repeat(100) {
            assertEquals(false, CombatUtils.rollParry(0.0))
        }
    }

    @Test
    fun testRollParryFullChance() {
        repeat(100) {
            assertEquals(true, CombatUtils.rollParry(1.0))
        }
    }

    // ─── rollEvasion (statistical) ──────────────────────────────

    @Test
    fun testRollEvasionZeroChance() {
        repeat(100) {
            assertEquals(false, CombatUtils.rollEvasion(0.0))
        }
    }

    @Test
    fun testRollEvasionFullChance() {
        repeat(100) {
            assertEquals(true, CombatUtils.rollEvasion(1.0))
        }
    }

    // ─── Armor damage reduction formula ────────────────────────

    @Test
    fun testArmorReductionDivisorHalvesEffectiveArmor() {
        val divisor = GameConfig.Combat.ARMOR_DAMAGE_REDUCTION_DIVISOR
        assertEquals(2, divisor)

        val totalArmor = 24
        val rawDamage = 22
        val effectiveArmor = totalArmor / divisor
        val damage = (rawDamage - effectiveArmor).coerceAtLeast(1)
        assertEquals(10, damage, "Chain armor (24) should reduce 22 damage to 10, not 1")
    }

    @Test
    fun testArmorReductionStillClampsToMinimumOne() {
        val divisor = GameConfig.Combat.ARMOR_DAMAGE_REDUCTION_DIVISOR
        val totalArmor = 100
        val rawDamage = 5
        val effectiveArmor = totalArmor / divisor
        val damage = (rawDamage - effectiveArmor).coerceAtLeast(1)
        assertEquals(1, damage, "Massive armor should still clamp damage to 1")
    }
}
