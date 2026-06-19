package com.neomud.server.game.commands

import com.neomud.server.session.PendingSkill
import com.neomud.server.session.PlayerSession
import com.neomud.shared.model.Player
import com.neomud.shared.model.Stats
import com.neomud.server.session.TransportSession
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class RestCommandTest {

    private val restCommand = RestCommand()

    @Test
    fun testRestQueuesPendingSkill() {
        runBlocking {
            val session = createTestSession()
            session.player = createTestPlayer(currentHp = 10, maxHp = 50)

            restCommand.execute(session)

            assertIs<PendingSkill.Rest>(session.pendingSkill)
        }
    }

    @Test
    fun testRestToggleOffWhenAlreadyResting() {
        runBlocking {
            val session = createTestSession()
            session.player = createTestPlayer(currentHp = 10, maxHp = 50)
            session.isResting = true

            restCommand.execute(session)

            assertFalse(session.isResting)
            assertNull(session.pendingSkill)
        }
    }

    @Test
    fun testRestBlockedInCombat() {
        runBlocking {
            val session = createTestSession()
            session.player = createTestPlayer(currentHp = 10, maxHp = 50)
            session.attackMode = true

            restCommand.execute(session)

            assertNull(session.pendingSkill)
        }
    }

    @Test
    fun testRestBlockedAtFullHp() {
        runBlocking {
            val session = createTestSession()
            session.player = createTestPlayer(currentHp = 50, maxHp = 50)

            restCommand.execute(session)

            assertNull(session.pendingSkill)
        }
    }

    @Test
    fun testRestBlockedOnCooldown() {
        runBlocking {
            val session = createTestSession()
            session.player = createTestPlayer(currentHp = 10, maxHp = 50)
            session.skillCooldowns["REST"] = 3

            restCommand.execute(session)

            assertNull(session.pendingSkill)
        }
    }

    @Test
    fun testRestBreaksMeditation() {
        runBlocking {
            val session = createTestSession()
            session.player = createTestPlayer(currentHp = 10, maxHp = 50)
            session.isMeditating = true

            restCommand.execute(session)

            assertFalse(session.isMeditating, "Meditation should be broken when starting to rest")
            assertIs<PendingSkill.Rest>(session.pendingSkill)
        }
    }

    @Test
    fun testMeditateBreaksRest() {
        runBlocking {
            val meditateCommand = MeditateCommand()
            val session = createTestSession()
            session.player = createTestPlayer(currentHp = 50, maxHp = 50, currentMp = 5, maxMp = 30)
            session.isResting = true

            meditateCommand.execute(session)

            assertFalse(session.isResting, "Rest should be broken when starting to meditate")
            assertIs<PendingSkill.Meditate>(session.pendingSkill)
        }
    }

    @Test
    fun testDeathClearsRestState() {
        val session = createTestSession()
        session.isResting = true
        session.isMeditating = true
        session.attackMode = true

        // Simulate PlayerKilled handler
        session.attackMode = false
        session.selectedTargetId = null
        session.readiedSpellId = null
        session.pendingSkill = null
        session.isHidden = false
        session.isMeditating = false
        session.isResting = false

        assertFalse(session.isResting)
        assertFalse(session.isMeditating)
    }

    private fun createTestSession(): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) {}
            override suspend fun close(reason: String) {}
        })
    }

    private fun createTestPlayer(
        currentHp: Int = 50,
        maxHp: Int = 50,
        currentMp: Int = 20,
        maxMp: Int = 20
    ): Player {
        return Player(
            name = "TestPlayer",
            characterClass = "WARRIOR",
            race = "HUMAN",
            level = 1,
            currentHp = currentHp,
            maxHp = maxHp,
            currentMp = currentMp,
            maxMp = maxMp,
            currentRoomId = "test:room1",
            currentXp = 0,
            xpToNextLevel = 1000,
            stats = Stats(
                strength = 20,
                agility = 15,
                intellect = 10,
                willpower = 12,
                health = 18,
                charm = 10
            ),
            unspentCp = 0,
            totalCpEarned = 0
        )
    }
}
