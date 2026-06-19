package com.neomud.server.game.commands

import com.neomud.server.game.GameConfig
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.game.party.PartyService
import com.neomud.server.persistence.DatabaseFactory
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.SpellCatalog
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.*
import com.neomud.server.session.TransportSession
import kotlinx.coroutines.runBlocking
import org.junit.Before
import java.io.File
import kotlin.test.*

class SpellCommandAllyTest {

    private val testDbFile = File.createTempFile("spellallytest", ".db").also { it.deleteOnExit() }

    @Before
    fun setUp() {
        DatabaseFactory.init("jdbc:sqlite:${testDbFile.absolutePath}")
    }

    private val allyHealSpell = SpellDef(
        id = "MEND_WOUNDS",
        name = "Mend Wounds",
        description = "Heal an ally",
        school = "restoration",
        schoolLevel = 1,
        spellType = SpellType.HEAL,
        basePower = 8,
        manaCost = 7,
        primaryStat = "willpower",
        cooldownTicks = 2,
        levelRequired = 1,
        targetType = TargetType.ALLY,
        castMessage = "channels mending light"
    )

    private val allyBuffSpell = SpellDef(
        id = "BARK_SKIN",
        name = "Bark Skin",
        description = "Buff an ally's agility",
        school = "restoration",
        schoolLevel = 1,
        spellType = SpellType.BUFF,
        basePower = 6,
        manaCost = 10,
        primaryStat = "willpower",
        cooldownTicks = 6,
        levelRequired = 1,
        targetType = TargetType.ALLY,
        effectType = "BUFF_AGILITY",
        effectDuration = 8,
        castMessage = "invokes bark skin"
    )

    private val groupHealSpell = SpellDef(
        id = "PRAYER_OF_MENDING",
        name = "Prayer of Mending",
        description = "Heal all party in room",
        school = "restoration",
        schoolLevel = 1,
        spellType = SpellType.HEAL,
        basePower = 6,
        manaCost = 14,
        primaryStat = "willpower",
        cooldownTicks = 4,
        levelRequired = 1,
        targetType = TargetType.PARTY_ROOM,
        castMessage = "utters a prayer of mending"
    )

    private val groupBuffSpell = SpellDef(
        id = "WAR_HYMN",
        name = "War Hymn",
        description = "Buff all party in room",
        school = "restoration",
        schoolLevel = 1,
        spellType = SpellType.BUFF,
        basePower = 4,
        manaCost = 12,
        primaryStat = "willpower",
        cooldownTicks = 8,
        levelRequired = 1,
        targetType = TargetType.PARTY_ROOM,
        effectType = "BUFF_STRENGTH",
        effectDuration = 8,
        castMessage = "sings a war hymn"
    )

    private val groupHotSpell = SpellDef(
        id = "REJUVENATION",
        name = "Rejuvenation",
        description = "HoT all party in room",
        school = "restoration",
        schoolLevel = 1,
        spellType = SpellType.HOT,
        basePower = 3,
        manaCost = 12,
        primaryStat = "willpower",
        cooldownTicks = 5,
        levelRequired = 1,
        targetType = TargetType.PARTY_ROOM,
        effectType = "HEAL_OVER_TIME",
        effectDuration = 5,
        tickPower = 3,
        castMessage = "channels rejuvenation"
    )

    private val testClassDef = CharacterClassDef(
        id = "PRIEST",
        name = "Priest",
        description = "A healer",
        minimumStats = Stats(),
        skills = listOf(),
        magicSchools = mapOf("restoration" to 3)
    )

    private fun createTestSession(): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) {}
            override suspend fun close(reason: String) {}
        })
    }

    private fun createTestPlayer(
        name: String = "Healer",
        currentMp: Int = 50,
        maxMp: Int = 50,
        currentHp: Int = 50,
        maxHp: Int = 50
    ): Player = Player(
        name = name,
        characterClass = "PRIEST",
        race = "HUMAN",
        level = 5,
        currentHp = currentHp,
        maxHp = maxHp,
        currentMp = currentMp,
        maxMp = maxMp,
        currentRoomId = "test:room",
        stats = Stats(strength = 10, agility = 10, intellect = 20, willpower = 30, health = 15, charm = 10)
    )

    private fun createSpellCommand(
        partyService: PartyService? = null,
        sessionManager: SessionManager = SessionManager()
    ): SpellCommand {
        val spellCatalog = SpellCatalog(listOf(allyHealSpell, allyBuffSpell, groupHealSpell, groupBuffSpell, groupHotSpell))
        val classCatalog = ClassCatalog(listOf(testClassDef))
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        val playerRepository = PlayerRepository()
        return SpellCommand(spellCatalog, classCatalog, npcManager, sessionManager, playerRepository, partyService)
    }

    // --- ALLY heal on party member ---

    @Test
    fun testAllyHealOnPartyMember() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer")
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Healer", caster, username = "healer")

        val target = createTestSession()
        target.player = createTestPlayer(name = "Fighter", currentHp = 20, maxHp = 50, currentMp = 10, maxMp = 10)
        target.playerName = "Fighter"
        target.currentRoomId = "test:room"
        sessionManager.addSession("Fighter", target, username = "fighter")

        // Form a party
        partyService.createInvite("Healer", "Fighter", 0)
        partyService.acceptInvite("Fighter", "Healer", 0)

        val result = spellCommand.resolve(caster, "MEND_WOUNDS", "Fighter")

        assertNull(result, "ALLY heal should not return NPC target")
        assertTrue(target.player!!.currentHp > 20, "Target should be healed")
        assertTrue(caster.player!!.currentMp < 50, "Caster MP should be deducted")
    }

    // --- Reject non-party member ---

    @Test
    fun testAllyHealRejectsNonPartyMember() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer")
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Healer", caster, username = "healer")

        val stranger = createTestSession()
        stranger.player = createTestPlayer(name = "Stranger", currentHp = 20, maxHp = 50)
        stranger.playerName = "Stranger"
        stranger.currentRoomId = "test:room"
        sessionManager.addSession("Stranger", stranger, username = "stranger")

        // No party — resolveAllyTarget should fall back to self
        val resolved = spellCommand.resolveAllyTarget(caster, "Stranger", "test:room")
        assertEquals(caster, resolved, "Non-party target should fall back to self")
    }

    // --- Reject different room ---

    @Test
    fun testAllyHealRejectsDifferentRoom() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer")
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Healer", caster, username = "healer")

        val ally = createTestSession()
        ally.player = createTestPlayer(name = "Fighter", currentHp = 20, maxHp = 50)
        ally.playerName = "Fighter"
        ally.currentRoomId = "test:other_room"
        sessionManager.addSession("Fighter", ally, username = "fighter")

        partyService.createInvite("Healer", "Fighter", 0)
        partyService.acceptInvite("Fighter", "Healer", 0)

        val resolved = spellCommand.resolveAllyTarget(caster, "Fighter", "test:room")
        assertEquals(caster, resolved, "Different-room party member should fall back to self")
    }

    // --- Self-fallback when no target specified ---

    @Test
    fun testAllyHealSelfFallbackNoTarget() = runBlocking {
        val sessionManager = SessionManager()
        val spellCommand = createSpellCommand(sessionManager = sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer")
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"

        val resolved = spellCommand.resolveAllyTarget(caster, null, "test:room")
        assertEquals(caster, resolved, "No target should fall back to self")
    }

    // --- Threat registration ---

    @Test
    fun testAllyHealRegistersThreat() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val worldGraph = WorldGraph()
        worldGraph.addRoom(Room("test:room", "Test Room", "A test room", exits = emptyMap(), zoneId = "test", x = 0, y = 0))
        val npcManager = NpcManager(worldGraph, emptyMap(), emptyMap())

        // Spawn a hostile NPC
        val npcData = com.neomud.server.world.NpcData(
            id = "npc:rat", name = "Rat", description = "test",
            startRoomId = "test:room", behaviorType = "idle",
            hostile = true, maxHp = 50, damage = 5,
            level = 1, xpReward = 10L
        )
        npcManager.loadNpcs(listOf(npcData to "test"))

        val spellCatalog = SpellCatalog(listOf(allyHealSpell))
        val classCatalog = ClassCatalog(listOf(testClassDef))
        val playerRepository = PlayerRepository()
        val spellCommand = SpellCommand(spellCatalog, classCatalog, npcManager, sessionManager, playerRepository, partyService)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer")
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Healer", caster, username = "healer")

        val fighter = createTestSession()
        fighter.player = createTestPlayer(name = "Fighter", currentHp = 20, maxHp = 50, currentMp = 10, maxMp = 10)
        fighter.playerName = "Fighter"
        fighter.currentRoomId = "test:room"
        sessionManager.addSession("Fighter", fighter, username = "fighter")

        partyService.createInvite("Healer", "Fighter", 0)
        partyService.acceptInvite("Fighter", "Healer", 0)

        // Engage the fighter with the NPC
        val npc = npcManager.getLivingHostileNpcsInRoom("test:room").first()
        npc.engagedPlayerIds.add("Fighter")

        spellCommand.resolve(caster, "MEND_WOUNDS", "Fighter")

        assertTrue("Healer" in npc.engagedPlayerIds, "Healer should be added to NPC's engaged list after healing Fighter")
    }

    // --- Auto-cast targets lowest HP% ---

    @Test
    fun testAutoCastTargetsLowestHpPercent() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer", currentHp = 40, maxHp = 50)
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Healer", caster, username = "healer")

        val ally1 = createTestSession()
        ally1.player = createTestPlayer(name = "Ally1", currentHp = 30, maxHp = 50, currentMp = 10, maxMp = 10)
        ally1.playerName = "Ally1"
        ally1.currentRoomId = "test:room"
        sessionManager.addSession("Ally1", ally1, username = "ally1")

        val ally2 = createTestSession()
        ally2.player = createTestPlayer(name = "Ally2", currentHp = 10, maxHp = 50, currentMp = 10, maxMp = 10)
        ally2.playerName = "Ally2"
        ally2.currentRoomId = "test:room"
        sessionManager.addSession("Ally2", ally2, username = "ally2")

        partyService.createInvite("Healer", "Ally1", 0)
        partyService.acceptInvite("Ally1", "Healer", 0)
        partyService.createInvite("Healer", "Ally2", 1)
        partyService.acceptInvite("Ally2", "Healer", 1)

        val ally2HpBefore = ally2.player!!.currentHp
        spellCommand.autoCast(caster, "MEND_WOUNDS", null, "test:room")

        assertTrue(ally2.player!!.currentHp > ally2HpBefore, "Lowest HP% ally should be healed")
    }

    // --- Auto-cast self-priority below threshold ---

    @Test
    fun testAutoCastSelfPriorityBelowThreshold() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        // Caster at 10% HP (well below 25% threshold)
        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer", currentHp = 5, maxHp = 50)
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Healer", caster, username = "healer")

        val ally = createTestSession()
        ally.player = createTestPlayer(name = "Ally", currentHp = 30, maxHp = 50, currentMp = 10, maxMp = 10)
        ally.playerName = "Ally"
        ally.currentRoomId = "test:room"
        sessionManager.addSession("Ally", ally, username = "ally")

        partyService.createInvite("Healer", "Ally", 0)
        partyService.acceptInvite("Ally", "Healer", 0)

        val casterHpBefore = caster.player!!.currentHp
        spellCommand.autoCast(caster, "MEND_WOUNDS", null, "test:room")

        assertTrue(caster.player!!.currentHp > casterHpBefore, "Caster below threshold should heal self first")
    }

    // --- Auto-cast skips when all allies full HP ---

    @Test
    fun testAutoCastSkipsWhenAllFullHp() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer", currentHp = 50, maxHp = 50)
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Healer", caster, username = "healer")

        val ally = createTestSession()
        ally.player = createTestPlayer(name = "Ally", currentHp = 50, maxHp = 50, currentMp = 10, maxMp = 10)
        ally.playerName = "Ally"
        ally.currentRoomId = "test:room"
        sessionManager.addSession("Ally", ally, username = "ally")

        partyService.createInvite("Healer", "Ally", 0)
        partyService.acceptInvite("Ally", "Healer", 0)

        val result = spellCommand.autoCast(caster, "MEND_WOUNDS", null, "test:room")

        assertFalse(result, "Auto-cast should skip when all allies at full HP")
        assertEquals(50, caster.player!!.currentMp, "MP should not be deducted when skipped")
    }

    // --- ALLY buff on target ---

    @Test
    fun testAllyBuffOnPartyMember() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer")
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Healer", caster, username = "healer")

        val target = createTestSession()
        target.player = createTestPlayer(name = "Fighter", currentMp = 10, maxMp = 10)
        target.playerName = "Fighter"
        target.currentRoomId = "test:room"
        sessionManager.addSession("Fighter", target, username = "fighter")

        partyService.createInvite("Healer", "Fighter", 0)
        partyService.acceptInvite("Fighter", "Healer", 0)

        spellCommand.resolve(caster, "BARK_SKIN", "Fighter")

        assertTrue(target.activeEffects.any { it.name == "Bark Skin" }, "Target should have Bark Skin effect")
        assertTrue(caster.player!!.currentMp < 50, "Caster MP should be deducted")
    }

    // --- PARTY_ROOM heal hits all party in room ---

    @Test
    fun testPartyRoomHealHitsAllInRoom() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer", currentHp = 30, maxHp = 50)
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Healer", caster, username = "healer")

        val ally1 = createTestSession()
        ally1.player = createTestPlayer(name = "Ally1", currentHp = 20, maxHp = 50, currentMp = 10, maxMp = 10)
        ally1.playerName = "Ally1"
        ally1.currentRoomId = "test:room"
        sessionManager.addSession("Ally1", ally1, username = "ally1")

        val ally2 = createTestSession()
        ally2.player = createTestPlayer(name = "Ally2", currentHp = 15, maxHp = 50, currentMp = 10, maxMp = 10)
        ally2.playerName = "Ally2"
        ally2.currentRoomId = "test:room"
        sessionManager.addSession("Ally2", ally2, username = "ally2")

        partyService.createInvite("Healer", "Ally1", 0)
        partyService.acceptInvite("Ally1", "Healer", 0)
        partyService.createInvite("Healer", "Ally2", 1)
        partyService.acceptInvite("Ally2", "Healer", 1)

        spellCommand.resolve(caster, "PRAYER_OF_MENDING", null)

        assertTrue(caster.player!!.currentHp > 30, "Caster should be healed")
        assertTrue(ally1.player!!.currentHp > 20, "Ally1 should be healed")
        assertTrue(ally2.player!!.currentHp > 15, "Ally2 should be healed")
        assertTrue(caster.player!!.currentMp < 50, "Caster MP should be deducted")
    }

    // --- PARTY_ROOM skips members in other rooms ---

    @Test
    fun testPartyRoomSkipsOtherRooms() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer", currentHp = 30, maxHp = 50)
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Healer", caster, username = "healer")

        val nearAlly = createTestSession()
        nearAlly.player = createTestPlayer(name = "NearAlly", currentHp = 20, maxHp = 50, currentMp = 10, maxMp = 10)
        nearAlly.playerName = "NearAlly"
        nearAlly.currentRoomId = "test:room"
        sessionManager.addSession("NearAlly", nearAlly, username = "nearally")

        val farAlly = createTestSession()
        farAlly.player = createTestPlayer(name = "FarAlly", currentHp = 10, maxHp = 50, currentMp = 10, maxMp = 10)
        farAlly.playerName = "FarAlly"
        farAlly.currentRoomId = "test:other_room"
        sessionManager.addSession("FarAlly", farAlly, username = "farally")

        partyService.createInvite("Healer", "NearAlly", 0)
        partyService.acceptInvite("NearAlly", "Healer", 0)
        partyService.createInvite("Healer", "FarAlly", 1)
        partyService.acceptInvite("FarAlly", "Healer", 1)

        spellCommand.resolve(caster, "PRAYER_OF_MENDING", null)

        assertTrue(nearAlly.player!!.currentHp > 20, "Near ally should be healed")
        assertEquals(10, farAlly.player!!.currentHp, "Far ally should NOT be healed")
    }

    // --- PARTY_ROOM power scaled by GROUP_HEAL_POWER_RATIO ---

    @Test
    fun testGroupHealPowerScaled() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer", currentHp = 1, maxHp = 200)
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Healer", caster, username = "healer")

        // Solo cast — still gets reduced power
        spellCommand.resolve(caster, "PRAYER_OF_MENDING", null)

        val healed = caster.player!!.currentHp - 1
        // basePower=6, wil=30, statDiv=4, level=5, levelDiv=1, dice=1..8
        // raw power = 6 + 30/4 + 5/1 + rand(1..8) = 6 + 7 + 5 + [1..8] = 19..26
        // scaled = raw * 0.55 = 10..14
        assertTrue(healed > 0, "Should heal something")
        assertTrue(healed <= 26, "Healed amount should be scaled down by GROUP_HEAL_POWER_RATIO")
    }

    // --- PARTY_ROOM buff applies to all targets ---

    @Test
    fun testPartyRoomBuffAppliesToAll() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Bard")
        caster.playerName = "Bard"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Bard", caster, username = "bard")

        val ally = createTestSession()
        ally.player = createTestPlayer(name = "Fighter", currentMp = 10, maxMp = 10)
        ally.playerName = "Fighter"
        ally.currentRoomId = "test:room"
        sessionManager.addSession("Fighter", ally, username = "fighter")

        partyService.createInvite("Bard", "Fighter", 0)
        partyService.acceptInvite("Fighter", "Bard", 0)

        spellCommand.resolve(caster, "WAR_HYMN", null)

        assertTrue(caster.activeEffects.any { it.name == "War Hymn" }, "Caster should have War Hymn")
        assertTrue(ally.activeEffects.any { it.name == "War Hymn" }, "Ally should have War Hymn")
    }

    // --- PARTY_ROOM HoT applies to all targets ---

    @Test
    fun testPartyRoomHotAppliesToAll() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Druid")
        caster.playerName = "Druid"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Druid", caster, username = "druid")

        val ally = createTestSession()
        ally.player = createTestPlayer(name = "Fighter", currentMp = 10, maxMp = 10)
        ally.playerName = "Fighter"
        ally.currentRoomId = "test:room"
        sessionManager.addSession("Fighter", ally, username = "fighter")

        partyService.createInvite("Druid", "Fighter", 0)
        partyService.acceptInvite("Fighter", "Druid", 0)

        spellCommand.resolve(caster, "REJUVENATION", null)

        assertTrue(caster.activeEffects.any { it.name == "Rejuvenation" }, "Caster should have Rejuvenation HoT")
        assertTrue(ally.activeEffects.any { it.name == "Rejuvenation" }, "Ally should have Rejuvenation HoT")
    }

    // --- PARTY_ROOM heal skips full-HP members ---

    @Test
    fun testPartyRoomHealSkipsFullHp() = runBlocking {
        val sessionManager = SessionManager()
        val partyService = PartyService()
        val spellCommand = createSpellCommand(partyService, sessionManager)

        val caster = createTestSession()
        caster.player = createTestPlayer(name = "Healer", currentHp = 50, maxHp = 50)
        caster.playerName = "Healer"
        caster.currentRoomId = "test:room"
        sessionManager.addSession("Healer", caster, username = "healer")

        val injured = createTestSession()
        injured.player = createTestPlayer(name = "Injured", currentHp = 10, maxHp = 50, currentMp = 10, maxMp = 10)
        injured.playerName = "Injured"
        injured.currentRoomId = "test:room"
        sessionManager.addSession("Injured", injured, username = "injured")

        partyService.createInvite("Healer", "Injured", 0)
        partyService.acceptInvite("Injured", "Healer", 0)

        spellCommand.resolve(caster, "PRAYER_OF_MENDING", null)

        assertEquals(50, caster.player!!.currentHp, "Full-HP caster should not be over-healed")
        assertTrue(injured.player!!.currentHp > 10, "Injured ally should be healed")
    }

    // --- Serialization round-trip: SpellCastResult with targetName ---

    @Test
    fun testSpellCastResultTargetNameSerialization() {
        val msg = com.neomud.shared.protocol.ServerMessage.SpellCastResult(
            success = true,
            spellName = "Mend Wounds",
            message = "Healer heals Fighter for 15 HP!",
            newMp = 43,
            newHp = null,
            targetName = "Fighter"
        )
        val json = com.neomud.shared.protocol.MessageSerializer.encodeServerMessage(msg)
        val decoded = com.neomud.shared.protocol.MessageSerializer.decodeServerMessage(json)
        assertIs<com.neomud.shared.protocol.ServerMessage.SpellCastResult>(decoded)
        assertEquals("Fighter", decoded.targetName)
    }

    @Test
    fun testSpellCastResultTargetNameNullSerialization() {
        val msg = com.neomud.shared.protocol.ServerMessage.SpellCastResult(
            success = true,
            spellName = "Minor Heal",
            message = "Healer heals self!",
            newMp = 43
        )
        val json = com.neomud.shared.protocol.MessageSerializer.encodeServerMessage(msg)
        val decoded = com.neomud.shared.protocol.MessageSerializer.decodeServerMessage(json)
        assertIs<com.neomud.shared.protocol.ServerMessage.SpellCastResult>(decoded)
        assertNull(decoded.targetName)
    }
}
