package com.neomud.server.game

import com.neomud.server.game.commands.SpellCommand
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.persistence.DatabaseFactory
import java.io.File

import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.session.PendingSkill
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.SpellCatalog
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.*
import com.neomud.server.session.TransportSession
import kotlinx.coroutines.runBlocking
import org.junit.Before
import kotlin.test.*

class SpellCastTickTest {

    private val testDbFile = File.createTempFile("spellcasttest", ".db").also { it.deleteOnExit() }

    @Before
    fun setUp() {
        DatabaseFactory.init("jdbc:sqlite:${testDbFile.absolutePath}")
    }

    private val testSpell = SpellDef(
        id = "FIREBALL",
        name = "Fireball",
        description = "A ball of fire",
        school = "destruction",
        schoolLevel = 1,
        spellType = SpellType.DAMAGE,
        basePower = 10,
        manaCost = 5,
        primaryStat = "intellect",
        cooldownTicks = 2,
        levelRequired = 1,
        castMessage = "hurls a fireball at"
    )

    private val healSpell = SpellDef(
        id = "HEAL",
        name = "Heal",
        description = "A healing spell",
        school = "restoration",
        schoolLevel = 1,
        spellType = SpellType.HEAL,
        basePower = 10,
        manaCost = 5,
        primaryStat = "willpower",
        cooldownTicks = 2,
        levelRequired = 1,
        castMessage = "channels healing energy"
    )

    private val testClassDef = CharacterClassDef(
        id = "MAGE",
        name = "Mage",
        description = "A spellcaster",
        minimumStats = Stats(),
        skills = listOf(),
        magicSchools = mapOf("destruction" to 3, "restoration" to 3)
    )

    private fun createTestSession(): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) {}
            override suspend fun close(reason: String) {}
        })
    }

    private fun createTestPlayer(
        currentMp: Int = 50,
        maxMp: Int = 50,
        currentHp: Int = 30,
        maxHp: Int = 50
    ): Player = Player(
        name = "TestPlayer",
        characterClass = "MAGE",
        race = "HUMAN",
        level = 5,
        currentHp = currentHp,
        maxHp = maxHp,
        currentMp = currentMp,
        maxMp = maxMp,
        currentRoomId = "test:room",
        stats = Stats(strength = 10, agility = 10, intellect = 30, willpower = 20, health = 15, charm = 10)
    )

    private fun createSpellCommand(
        spellCatalog: SpellCatalog = SpellCatalog(listOf(testSpell, healSpell)),
        npcManager: NpcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
    ): SpellCommand {
        val classCatalog = ClassCatalog(listOf(testClassDef))
        val sessionManager = SessionManager()
        val playerRepository = PlayerRepository()
        return SpellCommand(spellCatalog, classCatalog, npcManager, sessionManager, playerRepository)
    }

    // --- execute() queues without side effects ---

    @Test
    fun testExecuteQueuesCastSpellPendingSkill() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        spellCommand.execute(session, "FIREBALL", null)

        val pending = session.pendingSkill
        assertIs<PendingSkill.CastSpell>(pending)
        assertEquals("FIREBALL", pending.spellId)
        assertNull(pending.targetId)
    }

    @Test
    fun testExecuteQueuesCastSpellWithTargetId() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        spellCommand.execute(session, "FIREBALL", "npc:target")

        val pending = session.pendingSkill
        assertIs<PendingSkill.CastSpell>(pending)
        assertEquals("npc:target", pending.targetId)
    }

    @Test
    fun testExecuteDoesNotDeductMp() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer(currentMp = 50)
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        spellCommand.execute(session, "FIREBALL", null)

        assertEquals(50, session.player!!.currentMp, "MP should not be deducted at queue time")
    }

    @Test
    fun testExecuteDoesNotSetCooldown() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        spellCommand.execute(session, "FIREBALL", null)

        assertNull(session.skillCooldowns["FIREBALL"], "Cooldown should not be set at queue time")
    }

    @Test
    fun testExecuteOverwritesExistingPendingSkill() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"
        session.pendingSkill = PendingSkill.Bash("npc:old")

        spellCommand.execute(session, "FIREBALL", null)

        assertIs<PendingSkill.CastSpell>(session.pendingSkill)
        Unit
    }

    // --- execute() validation rejections ---

    @Test
    fun testExecuteRejectsUnknownSpell() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        spellCommand.execute(session, "NONEXISTENT", null)

        assertNull(session.pendingSkill, "Unknown spell should not queue")
    }

    @Test
    fun testExecuteRejectsInsufficientMp() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer(currentMp = 2) // Fireball costs 5
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        spellCommand.execute(session, "FIREBALL", null)

        assertNull(session.pendingSkill, "Insufficient MP should not queue")
    }

    @Test
    fun testExecuteRejectsOnCooldown() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"
        session.skillCooldowns["FIREBALL"] = 3

        spellCommand.execute(session, "FIREBALL", null)

        assertNull(session.pendingSkill, "Spell on cooldown should not queue")
    }

    @Test
    fun testExecuteRejectsWrongClass() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer().copy(characterClass = "WARRIOR") // no magic schools
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        spellCommand.execute(session, "FIREBALL", null)

        assertNull(session.pendingSkill, "Wrong class should not queue")
    }

    // --- resolve() applies effects ---

    private fun createNpcData(
        id: String = "npc:target",
        roomId: String = "test:room"
    ) = com.neomud.server.world.NpcData(
        id = id, name = "Target NPC", description = "test",
        startRoomId = roomId, behaviorType = "idle",
        hostile = true, maxHp = 50, damage = 5,
        level = 3, xpReward = 100L
    )

    private fun createWorldWithNpc(): Pair<NpcManager, WorldGraph> {
        val worldGraph = WorldGraph()
        worldGraph.addRoom(Room("test:room", "Test Room", "A test room", exits = emptyMap(), zoneId = "test", x = 0, y = 0))
        val npcManager = NpcManager(worldGraph, emptyMap(), emptyMap())
        npcManager.loadNpcs(listOf(createNpcData() to "test"))
        return npcManager to worldGraph
    }

    @Test
    fun testResolveDeductsMp() = runBlocking {
        val (npcManager, _) = createWorldWithNpc()
        val spellCommand = createSpellCommand(npcManager = npcManager)
        val session = createTestSession()
        session.player = createTestPlayer(currentMp = 50)
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        spellCommand.resolve(session, "FIREBALL", "npc:target")

        assertTrue(session.player!!.currentMp < 50, "MP should be deducted after resolve")
        assertEquals(50 - testSpell.manaCost, session.player!!.currentMp)
    }

    @Test
    fun testResolveSetsCooldown() = runBlocking {
        val (npcManager, _) = createWorldWithNpc()
        val spellCommand = createSpellCommand(npcManager = npcManager)
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        spellCommand.resolve(session, "FIREBALL", "npc:target")

        assertEquals(testSpell.cooldownTicks, session.skillCooldowns["FIREBALL"], "Cooldown should be set after resolve")
    }

    @Test
    fun testResolveReturnsTargetForDamageSpell() = runBlocking {
        val (npcManager, _) = createWorldWithNpc()
        val spellCommand = createSpellCommand(npcManager = npcManager)
        val session = createTestSession()
        session.player = createTestPlayer()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        val target = spellCommand.resolve(session, "FIREBALL", "npc:target")

        assertNotNull(target, "Damage spell should return NPC target")
        assertTrue(target.currentHp < 50, "Target should have taken damage")
    }

    @Test
    fun testResolveReturnsNullForHealSpell() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer(currentHp = 30, maxHp = 50)
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        val target = spellCommand.resolve(session, "HEAL", null)

        assertNull(target, "Heal spell should return null (no NPC target)")
        assertTrue(session.player!!.currentHp > 30, "Player should be healed")
    }

    // --- No valid target does not consume MP ---

    @Test
    fun testResolveDoesNotDeductMpWhenNoValidTarget() = runBlocking {
        // No NPCs in the room — resolveTarget returns null
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer(currentMp = 50)
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        val target = spellCommand.resolve(session, "FIREBALL", "npc:nonexistent")

        assertNull(target, "Should return null when no valid target")
        assertEquals(50, session.player!!.currentMp, "MP should not be deducted when target is invalid")
    }

    @Test
    fun testResolveDoesNotSetCooldownWhenNoValidTarget() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer(currentMp = 50)
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        spellCommand.resolve(session, "FIREBALL", "npc:nonexistent")

        assertNull(session.skillCooldowns["FIREBALL"], "Cooldown should not be set when target is invalid")
    }

    // --- Heal at full HP ---

    @Test
    fun testResolveHealAtFullHpDoesNotDeductMp() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer(currentHp = 50, maxHp = 50, currentMp = 50)
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        val target = spellCommand.resolve(session, "HEAL", null)

        assertNull(target)
        assertEquals(50, session.player!!.currentMp, "MP should not be deducted when at full HP")
    }

    @Test
    fun testResolveHealBelowMaxHpDeductsMp() = runBlocking {
        val spellCommand = createSpellCommand()
        val session = createTestSession()
        session.player = createTestPlayer(currentHp = 30, maxHp = 50, currentMp = 50)
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"

        spellCommand.resolve(session, "HEAL", null)

        assertEquals(50 - healSpell.manaCost, session.player!!.currentMp, "MP should be deducted when below max HP")
    }

    // --- Death clears pending CastSpell ---

    @Test
    fun testDeathClearsPendingCastSpell() {
        val session = createTestSession()
        session.pendingSkill = PendingSkill.CastSpell("FIREBALL", "npc:target")

        // Simulate PlayerKilled handler
        session.pendingSkill = null

        assertNull(session.pendingSkill)
    }
}
