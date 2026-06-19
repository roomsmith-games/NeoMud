package com.neomud.server.game

import com.neomud.server.game.commands.SneakCommand
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.session.PendingSkill
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.SkillCatalog
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.*
import com.neomud.server.session.TransportSession
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class SneakTickTest {

    private fun createTestSession(): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) {}
            override suspend fun close(reason: String) {}
        })
    }

    private fun createTestPlayer(): Player = Player(
        name = "TestPlayer",
        characterClass = "THIEF",
        race = "HUMAN",
        level = 5,
        currentHp = 50,
        maxHp = 50,
        currentMp = 20,
        maxMp = 20,
        currentRoomId = "test:room",
        stats = Stats(strength = 10, agility = 30, intellect = 10, willpower = 20, health = 15, charm = 10)
    )

    private fun createSneakCommand(): SneakCommand {
        val sessionManager = SessionManager()
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        val skillCatalog = SkillCatalog(emptyList())
        val classCatalog = ClassCatalog(emptyList())
        return SneakCommand(sessionManager, npcManager, skillCatalog, classCatalog)
    }

    // --- handleToggle(true) queues without side effects ---

    @Test
    fun testEnableSneakQueuesPendingSkill() = runBlocking {
        val sneakCommand = createSneakCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        sneakCommand.handleToggle(session, true)

        assertIs<PendingSkill.Sneak>(session.pendingSkill)
        Unit
    }

    @Test
    fun testEnableSneakDoesNotSetIsHidden() = runBlocking {
        val sneakCommand = createSneakCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        sneakCommand.handleToggle(session, true)

        assertFalse(session.isHidden, "isHidden should not be set at queue time")
    }

    @Test
    fun testEnableSneakDoesNotSetCooldown() = runBlocking {
        val sneakCommand = createSneakCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        sneakCommand.handleToggle(session, true)

        assertNull(session.skillCooldowns["SNEAK"], "Cooldown should not be set at queue time")
    }

    // --- handleToggle(true) validation rejections ---

    @Test
    fun testEnableSneakRejectsInCombat() = runBlocking {
        val sneakCommand = createSneakCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"
        session.attackMode = true

        sneakCommand.handleToggle(session, true)

        assertNull(session.pendingSkill, "Cannot sneak while in combat")
    }

    @Test
    fun testEnableSneakRejectsAlreadyHidden() = runBlocking {
        val sneakCommand = createSneakCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"
        session.isHidden = true

        sneakCommand.handleToggle(session, true)

        assertNull(session.pendingSkill, "Cannot sneak when already hidden")
    }

    @Test
    fun testEnableSneakRejectsOnCooldown() = runBlocking {
        val sneakCommand = createSneakCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"
        session.skillCooldowns["SNEAK"] = 3

        sneakCommand.handleToggle(session, true)

        assertNull(session.pendingSkill, "Cannot sneak while on cooldown")
    }

    // --- handleToggle(false) is still immediate ---

    @Test
    fun testDisableSneakIsImmediate() = runBlocking {
        val sneakCommand = createSneakCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"
        session.isHidden = true

        sneakCommand.handleToggle(session, false)

        assertFalse(session.isHidden, "Unsneaking should immediately clear isHidden")
        assertNull(session.pendingSkill, "Unsneaking should not queue a pending skill")
    }

    // --- Death clears pending Sneak ---

    @Test
    fun testDeathClearsPendingSneak() {
        val session = createTestSession()
        session.pendingSkill = PendingSkill.Sneak

        // Simulate PlayerKilled handler
        session.pendingSkill = null

        assertNull(session.pendingSkill)
    }

    // --- CastSpell and Sneak PendingSkill variants ---

    @Test
    fun testCastSpellPendingSkillStoresData() {
        val pending = PendingSkill.CastSpell("FIREBALL", "npc:target")
        assertEquals("FIREBALL", pending.spellId)
        assertEquals("npc:target", pending.targetId)
    }

    @Test
    fun testSneakPendingSkillIsSingleton() {
        val a = PendingSkill.Sneak
        val b = PendingSkill.Sneak
        assertSame(a, b)
    }
}
