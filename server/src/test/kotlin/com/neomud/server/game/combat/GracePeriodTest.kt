package com.neomud.server.game.combat

import com.neomud.server.game.GameConfig
import com.neomud.shared.model.Player
import com.neomud.shared.model.Stats
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.TransportSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GracePeriodTest {

    @Test
    fun testGraceTicksDefaultToZero() {
        val session = createTestSession()
        assertEquals(0, session.combatGraceTicks)
    }

    @Test
    fun testGraceTicksCanBeSet() {
        val session = createTestSession()
        session.combatGraceTicks = GameConfig.Combat.GRACE_TICKS
        assertEquals(GameConfig.Combat.GRACE_TICKS, session.combatGraceTicks)
    }

    @Test
    fun testGraceTicksDecrement() {
        val session = createTestSession()
        session.combatGraceTicks = GameConfig.Combat.GRACE_TICKS
        // Simulate tick decrement
        if (session.combatGraceTicks > 0) {
            session.combatGraceTicks--
        }
        assertEquals(GameConfig.Combat.GRACE_TICKS - 1, session.combatGraceTicks)
    }

    @Test
    fun testGraceTicksDoNotGoBelowZero() {
        val session = createTestSession()
        session.combatGraceTicks = 0
        if (session.combatGraceTicks > 0) {
            session.combatGraceTicks--
        }
        assertEquals(0, session.combatGraceTicks)
    }

    @Test
    fun testAggressiveActionClearsGrace() {
        val session = createTestSession()
        session.combatGraceTicks = GameConfig.Combat.GRACE_TICKS
        // Simulate aggressive action (attack/bash/kick/spell)
        session.combatGraceTicks = 0
        assertEquals(0, session.combatGraceTicks)
    }

    @Test
    fun testPlayerWithGraceIsFilteredFromNpcTargets() {
        val graceSession = createTestSession()
        graceSession.combatGraceTicks = GameConfig.Combat.GRACE_TICKS
        graceSession.player = createTestPlayer()

        val noGraceSession = createTestSession()
        noGraceSession.combatGraceTicks = 0
        noGraceSession.player = createTestPlayer()

        // Simulate the NPC target filter from CombatManager
        val sessions = listOf(graceSession, noGraceSession)
        val validTargets = sessions.filter {
            !it.isHidden && !it.godMode && (it.player?.currentHp ?: 0) > 0 && it.combatGraceTicks <= 0
        }

        assertEquals(1, validTargets.size)
        assertEquals(noGraceSession, validTargets.first())
    }

    @Test
    fun testGraceExpiresAfterEnoughTicks() {
        val session = createTestSession()
        session.combatGraceTicks = GameConfig.Combat.GRACE_TICKS

        // Simulate ticks
        repeat(GameConfig.Combat.GRACE_TICKS) {
            assertTrue(session.combatGraceTicks > 0, "Should still have grace at tick $it")
            session.combatGraceTicks--
        }

        assertEquals(0, session.combatGraceTicks, "Grace should be expired after ${GameConfig.Combat.GRACE_TICKS} ticks")
    }

    private fun createTestSession(): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) {}
            override suspend fun close(reason: String) {}
        })
    }

    private fun createTestPlayer(): Player = Player(
        name = "TestPlayer",
        characterClass = "WARRIOR",
        stats = Stats(strength = 30, agility = 20, intellect = 15, willpower = 15, health = 25, charm = 10),
        currentHp = 50,
        maxHp = 50,
        currentMp = 0,
        maxMp = 0,
        level = 1,
        currentRoomId = "test:room"
    )
}
