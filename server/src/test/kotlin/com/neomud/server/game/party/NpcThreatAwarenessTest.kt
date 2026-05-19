package com.neomud.server.game.party

import com.neomud.server.game.GameConfig
import com.neomud.server.game.combat.CombatManager
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.game.npc.NpcState
import com.neomud.server.game.npc.behavior.IdleBehavior
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.WorldGraph
import com.neomud.server.game.inventory.EquipmentService
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.world.ItemCatalog
import com.neomud.server.world.SkillCatalog
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

class NpcThreatAwarenessTest {

    private fun createTestSession(name: String): PlayerSession {
        val session = PlayerSession(object : WebSocketSession {
            override val coroutineContext: CoroutineContext get() = EmptyCoroutineContext
            override val incoming: Channel<Frame> get() = Channel()
            override val outgoing: Channel<Frame> get() = Channel(Channel.UNLIMITED)
            override val extensions: List<WebSocketExtension<*>> get() = emptyList()
            override var masking: Boolean = false
            override var maxFrameSize: Long = Long.MAX_VALUE
            override suspend fun flush() {}
            @Deprecated("Use cancel instead", replaceWith = ReplaceWith("cancel()"))
            override fun terminate() {}
        })
        session.playerName = name
        session.currentRoomId = "test:room1"
        return session
    }

    private fun createNpc(engagedWith: Set<String> = emptySet()): NpcState {
        val npc = NpcState(
            id = "npc:test_mob",
            name = "Test Mob",
            description = "A test mob",
            currentRoomId = "test:room1",
            behavior = IdleBehavior(),
            hostile = true,
            maxHp = 100,
            currentHp = 100,
            damage = 10,
            level = 1
        )
        npc.engagedPlayerIds.addAll(engagedWith)
        return npc
    }

    private fun createCombatManager(): CombatManager {
        val npcManager = NpcManager(WorldGraph())
        val sessionManager = SessionManager()
        val worldGraph = WorldGraph()
        val itemCatalog = ItemCatalog(emptyList())
        val equipmentService = EquipmentService(InventoryRepository(itemCatalog), itemCatalog)
        val skillCatalog = SkillCatalog(emptyList())
        return CombatManager(npcManager, sessionManager, worldGraph, equipmentService, skillCatalog)
    }

    @Test
    fun `no visible players returns null`() {
        val cm = createCombatManager()
        val npc = createNpc()
        assertNull(cm.selectNpcTarget(npc, emptyList()))
    }

    @Test
    fun `single visible player is always selected`() {
        val cm = createCombatManager()
        val npc = createNpc()
        val player = createTestSession("Alice")
        repeat(10) {
            assertEquals(player, cm.selectNpcTarget(npc, listOf(player)))
        }
    }

    @Test
    fun `no engaged players falls back to random visible`() {
        val cm = createCombatManager()
        val npc = createNpc(engagedWith = emptySet())
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob")
        val players = listOf(alice, bob)

        val selected = mutableSetOf<String>()
        repeat(100) {
            val target = cm.selectNpcTarget(npc, players)
            assertNotNull(target)
            selected.add(target.playerName!!)
        }
        assertTrue(selected.contains("Alice"), "Should sometimes select Alice")
        assertTrue(selected.contains("Bob"), "Should sometimes select Bob")
    }

    @Test
    fun `engaged players are preferred over non-engaged`() {
        val cm = createCombatManager()
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob")
        val npc = createNpc(engagedWith = setOf("Alice"))

        var aliceCount = 0
        val trials = 1000
        repeat(trials) {
            val target = cm.selectNpcTarget(npc, listOf(alice, bob))
            if (target == alice) aliceCount++
        }

        val aliceRate = aliceCount.toDouble() / trials
        assertTrue(aliceRate > 0.7, "Engaged player should be selected >70% of time (was ${aliceRate * 100}%)")
        assertTrue(aliceRate < 1.0, "Non-engaged player should sometimes be selected")
    }

    @Test
    fun `all players engaged distributes evenly`() {
        val cm = createCombatManager()
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob")
        val npc = createNpc(engagedWith = setOf("Alice", "Bob"))

        var aliceCount = 0
        val trials = 1000
        repeat(trials) {
            val target = cm.selectNpcTarget(npc, listOf(alice, bob))
            if (target == alice) aliceCount++
        }

        val aliceRate = aliceCount.toDouble() / trials
        assertTrue(aliceRate > 0.3, "Each engaged player should get fair share (Alice was ${aliceRate * 100}%)")
        assertTrue(aliceRate < 0.7, "Each engaged player should get fair share (Alice was ${aliceRate * 100}%)")
    }

    @Test
    fun `threat weight config is used`() {
        assertEquals(0.80, GameConfig.Party.NPC_THREAT_ENGAGED_WEIGHT,
            "NPC threat engaged weight should be 80%")
    }
}
