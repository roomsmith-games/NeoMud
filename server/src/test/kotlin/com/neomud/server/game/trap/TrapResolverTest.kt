package com.neomud.server.game.trap

import com.neomud.shared.model.Stats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrapResolverTest {

    @Test
    fun `detection fails when DC is zero`() {
        // perceptionDC=0 means "always trip; no detection possible"
        assertFalse(TrapResolver.resolveDetection(perceptionRoll = 50, perceptionDC = 0))
    }

    @Test
    fun `detection passes at exactly DC`() {
        assertTrue(TrapResolver.resolveDetection(perceptionRoll = 12, perceptionDC = 12))
    }

    @Test
    fun `detection passes above DC`() {
        assertTrue(TrapResolver.resolveDetection(perceptionRoll = 25, perceptionDC = 12))
    }

    @Test
    fun `detection fails below DC`() {
        assertFalse(TrapResolver.resolveDetection(perceptionRoll = 11, perceptionDC = 12))
    }

    @Test
    fun `saveStatValue maps TOUGHNESS to (str + wil) average`() {
        val stats = Stats(strength = 80, willpower = 40)
        assertEquals(60, TrapResolver.saveStatValue(stats, "TOUGHNESS"))
    }

    @Test
    fun `saveStatValue maps AGILITY directly`() {
        assertEquals(45, TrapResolver.saveStatValue(Stats(agility = 45), "AGILITY"))
    }

    @Test
    fun `saveStatValue is case-insensitive`() {
        assertEquals(50, TrapResolver.saveStatValue(Stats(agility = 50), "agility"))
    }

    @Test
    fun `saveStatValue returns 0 for unknown stat`() {
        assertEquals(0, TrapResolver.saveStatValue(Stats(strength = 99), "STRENGTH"))
    }

    @Test
    fun `resolveSave returns FAIL when saveStat is blank`() {
        val outcome = TrapResolver.resolveSave(
            stats = Stats(agility = 99), level = 10,
            saveStat = "", saveDC = 10, saveType = TrapResolver.SaveType.DODGE
        ) { 20 }
        assertEquals(TrapResolver.SaveOutcome.FAIL, outcome)
    }

    @Test
    fun `resolveSave returns FAIL when saveType is NONE`() {
        val outcome = TrapResolver.resolveSave(
            stats = Stats(agility = 99), level = 10,
            saveStat = "AGILITY", saveDC = 10, saveType = TrapResolver.SaveType.NONE
        ) { 20 }
        assertEquals(TrapResolver.SaveOutcome.FAIL, outcome)
    }

    @Test
    fun `resolveSave returns FAIL when saveDC is zero`() {
        val outcome = TrapResolver.resolveSave(
            stats = Stats(agility = 99), level = 10,
            saveStat = "AGILITY", saveDC = 0, saveType = TrapResolver.SaveType.DODGE
        ) { 20 }
        assertEquals(TrapResolver.SaveOutcome.FAIL, outcome)
    }

    @Test
    fun `resolveSave AGILITY DODGE success returns AVOID`() {
        // agility 50 + level 10/2 + roll 20 = 75 vs DC 30 → pass → AVOID
        val outcome = TrapResolver.resolveSave(
            stats = Stats(agility = 50), level = 10,
            saveStat = "AGILITY", saveDC = 30, saveType = TrapResolver.SaveType.DODGE
        ) { 20 }
        assertEquals(TrapResolver.SaveOutcome.AVOID, outcome)
    }

    @Test
    fun `resolveSave TOUGHNESS RESIST success returns HALF`() {
        // toughness (60+40)/2 = 50 + level 4/2 + roll 15 = 67 vs DC 50 → pass → HALF
        val outcome = TrapResolver.resolveSave(
            stats = Stats(strength = 60, willpower = 40), level = 4,
            saveStat = "TOUGHNESS", saveDC = 50, saveType = TrapResolver.SaveType.RESIST
        ) { 15 }
        assertEquals(TrapResolver.SaveOutcome.HALF, outcome)
    }

    @Test
    fun `resolveSave failed roll returns FAIL even on dodge`() {
        // agility 10 + level 0/2 + roll 1 = 11 vs DC 30 → fail
        val outcome = TrapResolver.resolveSave(
            stats = Stats(agility = 10), level = 0,
            saveStat = "AGILITY", saveDC = 30, saveType = TrapResolver.SaveType.DODGE
        ) { 1 }
        assertEquals(TrapResolver.SaveOutcome.FAIL, outcome)
    }

    @Test
    fun `computeDamage AVOID returns zero`() {
        assertEquals(0, TrapResolver.computeDamage(20, TrapResolver.SaveOutcome.AVOID))
    }

    @Test
    fun `computeDamage HALF rounds down`() {
        assertEquals(7, TrapResolver.computeDamage(15, TrapResolver.SaveOutcome.HALF))
    }

    @Test
    fun `computeDamage HALF floors at 1`() {
        // 1 base, half = 0 by integer div, must floor at 1 so the hit isn't a no-op
        assertEquals(1, TrapResolver.computeDamage(1, TrapResolver.SaveOutcome.HALF))
    }

    @Test
    fun `computeDamage FAIL returns full damage`() {
        assertEquals(25, TrapResolver.computeDamage(25, TrapResolver.SaveOutcome.FAIL))
    }
}
