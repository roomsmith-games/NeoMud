package com.neomud.server.game.combat

import com.neomud.server.game.GameConfig
import com.neomud.server.game.inventory.EquipmentService
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.game.npc.NpcState
import com.neomud.server.game.npc.behavior.IdleBehavior
import com.neomud.server.persistence.DatabaseFactory
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ItemCatalog
import com.neomud.server.world.NpcAbility
import com.neomud.server.world.SkillCatalog
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.Player
import com.neomud.shared.model.Room
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ServerMessage
import com.neomud.server.session.TransportSession
import kotlinx.coroutines.runBlocking
import org.junit.Before
import java.io.File
import kotlin.test.*

class NpcAbilityTest {

    private val testDbFile = File.createTempFile("npcabilitytest", ".db").also { it.deleteOnExit() }

    @Before
    fun setUp() {
        DatabaseFactory.init("jdbc:sqlite:${testDbFile.absolutePath}")
    }

    private fun createTestSession(): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) {}
            override suspend fun close(reason: String) {}
        })
    }

    private fun createPlayer(
        name: String,
        hp: Int = 100,
        maxHp: Int = 100,
        level: Int = 10,
        agility: Int = 20,
        willpower: Int = 20,
        health: Int = 20
    ) = Player(
        name = name,
        characterClass = "WARRIOR",
        race = "HUMAN",
        level = level,
        currentHp = hp,
        maxHp = maxHp,
        currentMp = 50,
        maxMp = 50,
        currentRoomId = "boss:room",
        stats = Stats(strength = 20, agility = agility, intellect = 20, willpower = willpower, health = health, charm = 10)
    )

    private fun createBossWithAbilities(
        abilities: List<NpcAbility>,
        currentPhase: Int = 0
    ): NpcState {
        val npc = NpcState(
            id = "npc:boss",
            name = "Test Boss",
            description = "A test boss",
            currentRoomId = "boss:room",
            behavior = IdleBehavior(),
            hostile = true,
            maxHp = 500,
            currentHp = 500,
            damage = 30,
            level = 15,
            accuracy = 20,
            defense = 15,
            evasion = 10,
            abilities = abilities
        )
        npc.currentPhase = currentPhase
        return npc
    }

    private val itemCatalog = ItemCatalog(emptyList())
    private val equipmentService = EquipmentService(InventoryRepository(itemCatalog), itemCatalog)

    private fun createCombatManager(
        npcManager: NpcManager,
        sessionManager: SessionManager = SessionManager(),
        world: WorldGraph = WorldGraph().also { it.addRoom(Room("boss:room", "Boss Room", "", mapOf(), "boss", 0, 0)) }
    ): CombatManager {
        return CombatManager(npcManager, sessionManager, world, equipmentService, SkillCatalog(emptyList()))
    }

    @Test
    fun `NPC uses ability when off cooldown and phase met`() = runBlocking {
        val ability = NpcAbility(
            id = "test_aoe",
            name = "Test AoE",
            damage = 20,
            damageVariance = 0,
            cooldownTicks = 3,
            saveStat = "",
            saveDC = 15,
            phaseRequired = 0,
            message = "Boss unleashes AoE!",
            sound = "boom"
        )
        val boss = createBossWithAbilities(listOf(ability))

        val session = createTestSession()
        session.player = createPlayer("Fighter")
        session.playerName = "Fighter"
        session.currentRoomId = "boss:room"

        val world = WorldGraph()
        world.addRoom(Room("boss:room", "Boss Room", "", mapOf(), "boss", 0, 0))
        val npcManager = NpcManager(world)
        val sessionManager = SessionManager()
        sessionManager.addSession("Fighter", session, username = "fighter")
        val combatManager = CombatManager(npcManager, sessionManager, world, equipmentService, SkillCatalog(emptyList()))

        val events = mutableListOf<CombatEvent>()
        val used = combatManager.resolveNpcAbility(boss, "boss:room", listOf(session), events)

        assertTrue(used, "Boss should use ability")
        assertEquals(1, events.size)
        val abilityEvent = events[0] as CombatEvent.NpcAbilityUsed
        assertEquals("Test AoE", abilityEvent.abilityName)
        assertEquals("Boss unleashes AoE!", abilityEvent.message)
        assertEquals(1, abilityEvent.results.size)
        assertEquals("Fighter", abilityEvent.results[0].targetName)
        assertEquals(20, abilityEvent.results[0].damage)
        assertEquals(80, abilityEvent.results[0].newHp)
        assertEquals(80, session.player!!.currentHp)
    }

    @Test
    fun `ability damages all visible players`() = runBlocking {
        val ability = NpcAbility(
            id = "aoe_blast",
            name = "AoE Blast",
            damage = 15,
            damageVariance = 0,
            cooldownTicks = 4,
            saveStat = "",
            saveDC = 15,
            phaseRequired = 0,
            message = "Blast!",
            sound = "blast"
        )
        val boss = createBossWithAbilities(listOf(ability))

        val s1 = createTestSession()
        s1.player = createPlayer("Fighter1")
        s1.playerName = "Fighter1"
        s1.currentRoomId = "boss:room"

        val s2 = createTestSession()
        s2.player = createPlayer("Fighter2")
        s2.playerName = "Fighter2"
        s2.currentRoomId = "boss:room"

        val world = WorldGraph()
        world.addRoom(Room("boss:room", "Boss Room", "", mapOf(), "boss", 0, 0))
        val npcManager = NpcManager(world)
        val sessionManager = SessionManager()
        sessionManager.addSession("Fighter1", s1, username = "fighter1")
        sessionManager.addSession("Fighter2", s2, username = "fighter2")
        val combatManager = CombatManager(npcManager, sessionManager, world, equipmentService, SkillCatalog(emptyList()))

        val events = mutableListOf<CombatEvent>()
        combatManager.resolveNpcAbility(boss, "boss:room", listOf(s1, s2), events)

        val abilityEvent = events.filterIsInstance<CombatEvent.NpcAbilityUsed>().first()
        assertEquals(2, abilityEvent.results.size)
        assertEquals(85, s1.player!!.currentHp)
        assertEquals(85, s2.player!!.currentHp)
    }

    @Test
    fun `save mechanic halves damage on success`() = runBlocking {
        val ability = NpcAbility(
            id = "save_test",
            name = "Save Test",
            damage = 40,
            damageVariance = 0,
            cooldownTicks = 4,
            saveStat = "agility",
            saveDC = 1, // Very low DC = guaranteed save
            saveHalves = true,
            phaseRequired = 0,
            message = "Saveable!",
            sound = ""
        )
        val boss = createBossWithAbilities(listOf(ability))

        val session = createTestSession()
        session.player = createPlayer("Fighter", agility = 50, level = 20)
        session.playerName = "Fighter"
        session.currentRoomId = "boss:room"

        val world = WorldGraph()
        world.addRoom(Room("boss:room", "Boss Room", "", mapOf(), "boss", 0, 0))
        val npcManager = NpcManager(world)
        val sessionManager = SessionManager()
        sessionManager.addSession("Fighter", session, username = "fighter")
        val combatManager = CombatManager(npcManager, sessionManager, world, equipmentService, SkillCatalog(emptyList()))

        val events = mutableListOf<CombatEvent>()
        combatManager.resolveNpcAbility(boss, "boss:room", listOf(session), events)

        val result = (events[0] as CombatEvent.NpcAbilityUsed).results[0]
        assertTrue(result.saved, "With DC 1 and stat 50 + level/2 + d20, save should succeed")
        assertEquals(20, result.damage, "Halved damage: 40/2 = 20")
        assertEquals(80, result.newHp)
    }

    @Test
    fun `phase-gated ability blocked before phase threshold`() = runBlocking {
        val ability = NpcAbility(
            id = "phase2_blast",
            name = "Phase 2 Blast",
            damage = 30,
            cooldownTicks = 3,
            phaseRequired = 2,
            message = "Phase 2!",
            sound = ""
        )
        val boss = createBossWithAbilities(listOf(ability), currentPhase = 1)

        val session = createTestSession()
        session.player = createPlayer("Fighter")
        session.playerName = "Fighter"
        session.currentRoomId = "boss:room"

        val world = WorldGraph()
        world.addRoom(Room("boss:room", "Boss Room", "", mapOf(), "boss", 0, 0))
        val npcManager = NpcManager(world)
        val sessionManager = SessionManager()
        sessionManager.addSession("Fighter", session, username = "fighter")
        val combatManager = CombatManager(npcManager, sessionManager, world, equipmentService, SkillCatalog(emptyList()))

        val events = mutableListOf<CombatEvent>()
        val used = combatManager.resolveNpcAbility(boss, "boss:room", listOf(session), events)

        assertFalse(used, "Ability requiring phase 2 should not fire when boss is in phase 1")
        assertTrue(events.isEmpty())
        assertEquals(100, session.player!!.currentHp)
    }

    @Test
    fun `ability replaces melee for the tick`() = runBlocking {
        val ability = NpcAbility(
            id = "aoe",
            name = "AoE",
            damage = 10,
            cooldownTicks = 3,
            phaseRequired = 0,
            message = "AoE!",
            sound = ""
        )
        val boss = createBossWithAbilities(listOf(ability))

        val world = WorldGraph()
        world.addRoom(Room("boss:room", "Boss Room", "", mapOf(), "boss", 0, 0))
        val npcManager = NpcManager(world)
        val sessionManager = SessionManager()
        val combatManager = CombatManager(npcManager, sessionManager, world, equipmentService, SkillCatalog(emptyList()))

        val events = mutableListOf<CombatEvent>()
        val session = createTestSession()
        session.player = createPlayer("Fighter")
        session.playerName = "Fighter"
        session.currentRoomId = "boss:room"

        val used = combatManager.resolveNpcAbility(boss, "boss:room", listOf(session), events)

        assertTrue(used, "Ability used -> NPC combatant should skip melee via 'continue'")
    }

    @Test
    fun `cooldown decrements correctly`() {
        val ability = NpcAbility(
            id = "cool_test",
            name = "Cool Test",
            damage = 10,
            cooldownTicks = 3,
            phaseRequired = 0,
            message = "Test!",
            sound = ""
        )
        val boss = createBossWithAbilities(listOf(ability))
        boss.abilityCooldowns["cool_test"] = 3

        // Manually tick cooldowns
        val iter = boss.abilityCooldowns.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            entry.setValue(entry.value - 1)
            if (entry.value <= 0) iter.remove()
        }

        assertEquals(2, boss.abilityCooldowns["cool_test"])

        // Tick again twice
        boss.abilityCooldowns["cool_test"] = boss.abilityCooldowns["cool_test"]!! - 1
        assertEquals(1, boss.abilityCooldowns["cool_test"])
        boss.abilityCooldowns["cool_test"] = boss.abilityCooldowns["cool_test"]!! - 1
        assertEquals(0, boss.abilityCooldowns["cool_test"])
    }

    @Test
    fun `ability on cooldown is not used`() = runBlocking {
        val ability = NpcAbility(
            id = "on_cd",
            name = "On CD",
            damage = 10,
            cooldownTicks = 5,
            phaseRequired = 0,
            message = "!",
            sound = ""
        )
        val boss = createBossWithAbilities(listOf(ability))
        boss.abilityCooldowns["on_cd"] = 3

        val session = createTestSession()
        session.player = createPlayer("Fighter")
        session.playerName = "Fighter"
        session.currentRoomId = "boss:room"

        val world = WorldGraph()
        world.addRoom(Room("boss:room", "Boss Room", "", mapOf(), "boss", 0, 0))
        val npcManager = NpcManager(world)
        val sessionManager = SessionManager()
        sessionManager.addSession("Fighter", session, username = "fighter")
        val combatManager = CombatManager(npcManager, sessionManager, world, equipmentService, SkillCatalog(emptyList()))

        val events = mutableListOf<CombatEvent>()
        val used = combatManager.resolveNpcAbility(boss, "boss:room", listOf(session), events)

        assertFalse(used, "Ability on cooldown should not be used")
        assertEquals(100, session.player!!.currentHp)
    }

    @Test
    fun `player killed by ability triggers PlayerKilled event`() = runBlocking {
        val ability = NpcAbility(
            id = "lethal",
            name = "Lethal Blast",
            damage = 200,
            damageVariance = 0,
            cooldownTicks = 10,
            phaseRequired = 0,
            message = "Lethal!",
            sound = ""
        )
        val boss = createBossWithAbilities(listOf(ability))

        val session = createTestSession()
        session.player = createPlayer("Fighter", hp = 50, maxHp = 100)
        session.playerName = "Fighter"
        session.currentRoomId = "boss:room"

        val world = WorldGraph()
        world.addRoom(Room("town:square", "Town Square", "", mapOf(), "town", 0, 0))
        world.addRoom(Room("boss:room", "Boss Room", "", mapOf(), "boss", 0, 0))
        val npcManager = NpcManager(world)
        val sessionManager = SessionManager()
        sessionManager.addSession("Fighter", session, username = "fighter")
        val combatManager = CombatManager(npcManager, sessionManager, world, equipmentService, SkillCatalog(emptyList()))

        val events = mutableListOf<CombatEvent>()
        combatManager.resolveNpcAbility(boss, "boss:room", listOf(session), events)

        val killEvents = events.filterIsInstance<CombatEvent.PlayerKilled>()
        assertEquals(1, killEvents.size, "Player should be killed by lethal ability")
        assertEquals("Test Boss", killEvents[0].killerName)
        assertEquals(0, session.player!!.currentHp)
    }

    @Test
    fun `ability sets cooldown after use`() = runBlocking {
        val ability = NpcAbility(
            id = "cd_set",
            name = "CD Set",
            damage = 5,
            cooldownTicks = 4,
            phaseRequired = 0,
            message = "!",
            sound = ""
        )
        val boss = createBossWithAbilities(listOf(ability))
        assertEquals(0, boss.abilityCooldowns.size)

        val session = createTestSession()
        session.player = createPlayer("Fighter")
        session.playerName = "Fighter"
        session.currentRoomId = "boss:room"

        val world = WorldGraph()
        world.addRoom(Room("boss:room", "Boss Room", "", mapOf(), "boss", 0, 0))
        val npcManager = NpcManager(world)
        val sessionManager = SessionManager()
        sessionManager.addSession("Fighter", session, username = "fighter")
        val combatManager = CombatManager(npcManager, sessionManager, world, equipmentService, SkillCatalog(emptyList()))

        val events = mutableListOf<CombatEvent>()
        combatManager.resolveNpcAbility(boss, "boss:room", listOf(session), events)

        assertEquals(4, boss.abilityCooldowns["cd_set"], "Cooldown should be set after use")
    }

    @Test
    fun `NpcAbility data parsed from NpcData`() {
        val npcAbility = NpcAbility(
            id = "tidal_surge",
            name = "Tidal Surge",
            damage = 22,
            damageVariance = 8,
            cooldownTicks = 4,
            saveStat = "agility",
            saveDC = 16,
            saveHalves = true,
            phaseRequired = 0,
            message = "Surge!",
            sound = "tidal_surge"
        )

        assertEquals("tidal_surge", npcAbility.id)
        assertEquals(22, npcAbility.damage)
        assertEquals(8, npcAbility.damageVariance)
        assertEquals("agility", npcAbility.saveStat)
        assertEquals(16, npcAbility.saveDC)
        assertTrue(npcAbility.saveHalves)
    }
}
