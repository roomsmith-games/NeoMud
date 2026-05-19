package com.neomud.server.game.progression

import com.neomud.server.game.GameConfig
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XpCalculatorTest {

    private val xpBase = GameConfig.Progression.XP_BASE_MULTIPLIER
    private val xpMin = GameConfig.Progression.XP_MINIMUM
    private val maxLevel = GameConfig.Progression.MAX_LEVEL

    private val mod5Above = GameConfig.Progression.XP_MOD_5_ABOVE
    private val modSame = GameConfig.Progression.XP_MOD_SAME
    private val mod5PlusBelow = GameConfig.Progression.XP_MOD_5_PLUS_BELOW

    private val cpLow = GameConfig.Progression.CP_PER_LEVEL_LOW
    private val cpMid = GameConfig.Progression.CP_PER_LEVEL_MID
    private val cpHigh = GameConfig.Progression.CP_PER_LEVEL_HIGH
    private val cpTier2 = GameConfig.Progression.CP_TIER_2_LEVEL
    private val cpTier3 = GameConfig.Progression.CP_TIER_3_LEVEL

    @Test
    fun testXpForLevel1() {
        val xp = XpCalculator.xpForLevel(1)
        assertEquals(xpMin, xp)
    }

    @Test
    fun testXpCurveIncreases() {
        val xp1 = XpCalculator.xpForLevel(1)
        val xp5 = XpCalculator.xpForLevel(5)
        val xp10 = XpCalculator.xpForLevel(10)
        val xp20 = XpCalculator.xpForLevel(20)
        assertTrue(xp5 > xp1)
        assertTrue(xp10 > xp5)
        assertTrue(xp20 > xp10)
    }

    @Test
    fun testKillXpSameLevel() {
        val baseXp = 50L
        val xp = XpCalculator.xpForKill(5, 5, baseXp)
        assertEquals((baseXp * modSame).roundToLong(), xp)
    }

    @Test
    fun testKillXpHigherNpc() {
        val baseXp = 50L
        val xp = XpCalculator.xpForKill(10, 5, baseXp)
        assertEquals((baseXp * mod5Above).roundToLong(), xp)
    }

    @Test
    fun testKillXpLowerNpc() {
        val baseXp = 50L
        val xp = XpCalculator.xpForKill(1, 10, baseXp)
        assertEquals((baseXp * mod5PlusBelow).roundToLong().coerceAtLeast(1), xp)
    }

    @Test
    fun testKillXpNeverZero() {
        val xp = XpCalculator.xpForKill(1, 30, 1)
        assertTrue(xp >= 1)
    }

    @Test
    fun testAdjustedXpWithModifiers() {
        val base = XpCalculator.xpForLevel(5)
        val adjusted = XpCalculator.adjustedXpForLevel(5, 1.1, 1.15)
        assertTrue(adjusted > base)
    }

    @Test
    fun testCpTiers() {
        assertEquals(cpLow, XpCalculator.cpForLevel(1))
        assertEquals(cpLow, XpCalculator.cpForLevel(cpTier2))
        assertEquals(cpMid, XpCalculator.cpForLevel(cpTier2 + 1))
        assertEquals(cpMid, XpCalculator.cpForLevel(cpTier3))
        assertEquals(cpHigh, XpCalculator.cpForLevel(cpTier3 + 1))
        assertEquals(cpHigh, XpCalculator.cpForLevel(maxLevel))
    }

    @Test
    fun testReadyToLevel() {
        assertTrue(XpCalculator.isReadyToLevel(100, 100, 1))
        assertTrue(XpCalculator.isReadyToLevel(150, 100, 1))
        assertFalse(XpCalculator.isReadyToLevel(50, 100, 1))
    }

    @Test
    fun testCannotExceedMaxLevel() {
        assertFalse(XpCalculator.isReadyToLevel(999999, 100, maxLevel))
    }

    // ─── Party XP ───────────────────────────────────────────

    @Test
    fun testPartyXpSoloUnchanged() {
        val baseXp = 100L
        assertEquals(baseXp, XpCalculator.partyXpPerMember(baseXp, 1))
    }

    @Test
    fun testPartyXpTwoMembers() {
        val baseXp = 100L
        val perMember = XpCalculator.partyXpPerMember(baseXp, 2)
        // 1/2 + 0.20*(2-1) = 0.50 + 0.20 = 0.70 → 70
        assertEquals(70L, perMember)
    }

    @Test
    fun testPartyXpThreeMembers() {
        val baseXp = 100L
        val perMember = XpCalculator.partyXpPerMember(baseXp, 3)
        // 1/3 + 0.20*(3-1) = 0.333 + 0.40 = 0.733 → 73
        assertEquals(73L, perMember)
    }

    @Test
    fun testPartyXpFourMembers() {
        val baseXp = 100L
        val perMember = XpCalculator.partyXpPerMember(baseXp, 4)
        // 1/4 + 0.20*(4-1) = 0.25 + 0.60 = 0.85 → 85
        assertEquals(85L, perMember)
    }

    @Test
    fun testPartyXpNeverZero() {
        assertEquals(1L, XpCalculator.partyXpPerMember(1L, 4))
    }

    @Test
    fun testPartyXpTotalBonusIncreases() {
        val baseXp = 100L
        val total2 = XpCalculator.partyXpPerMember(baseXp, 2) * 2
        val total3 = XpCalculator.partyXpPerMember(baseXp, 3) * 3
        val total4 = XpCalculator.partyXpPerMember(baseXp, 4) * 4
        assertTrue(total2 > baseXp, "2-player total ($total2) should exceed solo ($baseXp)")
        assertTrue(total3 > total2, "3-player total ($total3) should exceed 2-player ($total2)")
        assertTrue(total4 > total3, "4-player total ($total4) should exceed 3-player ($total3)")
    }

    @Test
    fun testPartyXpAlwaysLessThanSolo() {
        val baseXp = 100L
        for (n in 2..4) {
            val perMember = XpCalculator.partyXpPerMember(baseXp, n)
            assertTrue(perMember < baseXp, "$n-member per-person ($perMember) should be less than solo ($baseXp)")
        }
    }
}
