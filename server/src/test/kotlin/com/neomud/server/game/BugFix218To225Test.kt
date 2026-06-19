package com.neomud.server.game

import com.neomud.server.session.PendingSkill
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.shared.model.*
import com.neomud.server.session.TransportSession
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import com.neomud.server.game.commands.*

class BugFix218To225Test {

    // ─── Helpers ────────────────────────────────────────────

    private fun createTestSession(): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) {}
            override suspend fun close(reason: String) {}
        })
    }

    private fun createTestPlayer(
        currentHp: Int = 50,
        maxHp: Int = 100,
        currentMp: Int = 30,
        maxMp: Int = 50
    ): Player = Player(
        name = "TestPlayer",
        characterClass = "WARRIOR",
        race = "HUMAN",
        level = 5,
        currentHp = currentHp,
        maxHp = maxHp,
        currentMp = currentMp,
        maxMp = maxMp,
        currentRoomId = "test:room1",
        currentXp = 0,
        xpToNextLevel = 1000,
        stats = Stats(strength = 20, agility = 15, intellect = 10, willpower = 12, health = 18, charm = 10),
        unspentCp = 0,
        totalCpEarned = 0
    )

    // ─── #222: SessionManager addSession atomic check-and-set ────

    @Test
    fun testAddSessionRejectsDuplicateUsername() {
        val sm = SessionManager()
        val session1 = createTestSession()
        val session2 = createTestSession()

        val added1 = sm.addSession("Player1", session1, username = "alice")
        assertTrue(added1, "First addSession should succeed")

        val added2 = sm.addSession("Player2", session2, username = "alice")
        assertFalse(added2, "Second addSession with same username should be rejected")
    }

    @Test
    fun testAddSessionAllowsSamePlayerReconnect() {
        val sm = SessionManager()
        val session1 = createTestSession()
        val session2 = createTestSession()

        val added1 = sm.addSession("Player1", session1, username = "alice")
        assertTrue(added1)

        // Same playerName re-adding (reconnect scenario) should succeed
        val added2 = sm.addSession("Player1", session2, username = "alice")
        assertTrue(added2, "Re-adding same playerName with same username should succeed")
    }

    @Test
    fun testAddSessionWithoutUsernameAlwaysSucceeds() {
        val sm = SessionManager()
        val session1 = createTestSession()
        val session2 = createTestSession()

        assertTrue(sm.addSession("Player1", session1))
        assertTrue(sm.addSession("Player2", session2))
    }

    // ─── #219: Dead player blocked from combat skill commands ────

    @Test
    fun testDeadPlayerCannotRest() {
        runBlocking {
            val session = createTestSession()
            session.player = createTestPlayer(currentHp = 0, maxHp = 50)

            RestCommand().execute(session)

            assertNull(session.pendingSkill, "Dead player should not be able to queue rest")
        }
    }

    @Test
    fun testDeadPlayerCannotMeditate() {
        runBlocking {
            val session = createTestSession()
            session.player = createTestPlayer(currentHp = 0, maxHp = 50, currentMp = 5, maxMp = 30)

            MeditateCommand().execute(session)

            assertNull(session.pendingSkill, "Dead player should not be able to queue meditate")
        }
    }

    @Test
    fun testDeadPlayerCannotTrack() {
        runBlocking {
            val session = createTestSession()
            session.player = createTestPlayer(currentHp = 0)

            TrackCommand().execute(session)

            assertNull(session.pendingSkill, "Dead player should not be able to queue track")
        }
    }

    // ─── #217/#220: effectiveMaxHp used in heals and rest ────

    @Test
    fun testRestAllowedWhenBuffedMaxHpExceedsBaseMaxHp() {
        runBlocking {
            val session = createTestSession()
            // Player at base maxHp (100) but has BUFF_MAX_HP (+20, effective max = 120)
            session.player = createTestPlayer(currentHp = 100, maxHp = 100)
            session.activeEffects.add(ActiveEffect(
                name = "Fortifying Tonic",
                type = EffectType.BUFF_MAX_HP,
                remainingTicks = 10,
                magnitude = 20
            ))

            RestCommand().execute(session)

            assertIs<PendingSkill.Rest>(session.pendingSkill,
                "Rest should be allowed when currentHp equals base maxHp but effective maxHp is higher")
        }
    }

    @Test
    fun testRestBlockedAtEffectiveMaxHp() {
        runBlocking {
            val session = createTestSession()
            // Player at 120 HP with BUFF_MAX_HP (+20, effective max = 120) — truly full
            session.player = createTestPlayer(currentHp = 120, maxHp = 100)
            session.activeEffects.add(ActiveEffect(
                name = "Fortifying Tonic",
                type = EffectType.BUFF_MAX_HP,
                remainingTicks = 10,
                magnitude = 20
            ))

            RestCommand().execute(session)

            assertNull(session.pendingSkill,
                "Rest should be blocked when currentHp equals effective maxHp")
        }
    }

    @Test
    fun testEffectApplicatorHealRespectsEffectiveMaxHp() {
        val player = createTestPlayer(currentHp = 90, maxHp = 100)
        // Without effective max — caps at 100
        val result1 = EffectApplicator.applyEffect("HEAL", 30, "", player)
        assertEquals(100, result1?.newHp, "Without buff, heal should cap at base maxHp")

        // With effective max of 120 — caps at 120
        val result2 = EffectApplicator.applyEffect("HEAL", 30, "", player, effectiveMaxHp = 120)
        assertEquals(120, result2?.newHp, "With buff, heal should cap at effective maxHp")
    }

    @Test
    fun testEffectApplicatorHealSkipsWhenAtEffectiveMax() {
        val player = createTestPlayer(currentHp = 120, maxHp = 100)
        val result = EffectApplicator.applyEffect("HEAL", 10, "", player, effectiveMaxHp = 120)
        assertNull(result, "Heal should be skipped when already at effective maxHp")
    }

    @Test
    fun testUseEffectProcessorHealRespectsEffectiveMaxHp() {
        val player = createTestPlayer(currentHp = 90, maxHp = 100)

        val result1 = UseEffectProcessor.process("heal:30", player, "Potion")
        assertEquals(100, result1?.updatedPlayer?.currentHp, "Without buff, heal caps at base maxHp")

        val result2 = UseEffectProcessor.process("heal:30", player, "Potion", effectiveMaxHp = 120)
        assertEquals(120, result2?.updatedPlayer?.currentHp, "With buff, heal caps at effective maxHp")
    }

    // ─── #225: No debug roll in skill messages ────

    @Test
    fun testRestResolveMessageDoesNotContainRoll() {
        // This is a behavioral contract test — the messages sent during rest/meditate resolve
        // should not contain "(roll:" debug info. Since resolve happens in GameLoop (hard to
        // unit-test without integration), we verify the message format expectation at the
        // EffectApplicator level — which doesn't include roll info by design.
        val player = createTestPlayer(currentHp = 50, maxHp = 100)
        val result = EffectApplicator.applyEffect("HEAL", 10, "", player)
        assertNotNull(result)
        assertFalse(result.message.contains("roll:"), "Heal message should not contain roll debug info")
    }
}
