package com.neomud.server.game.trap

import com.neomud.server.session.PlayerSession
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

class TrapManagerTest {

    private val testRoomId: RoomId = "test:room"

    private fun stubSession(stats: Stats = Stats(), level: Int = 5, hp: Int = 100): PlayerSession {
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
        session.player = Player(
            name = "TrapTester",
            characterClass = "WARRIOR",
            race = "HUMAN",
            level = level,
            currentHp = hp,
            maxHp = hp,
            currentMp = 0,
            maxMp = 0,
            currentRoomId = testRoomId,
            stats = stats
        )
        session.currentRoomId = testRoomId
        return session
    }

    private fun roomWith(vararg interactables: RoomInteractable): WorldGraph {
        val graph = WorldGraph()
        graph.addRoom(Room(
            id = testRoomId,
            name = "Test Room",
            description = "A test room.",
            exits = emptyMap(),
            zoneId = "test",
            x = 0,
            y = 0,
            interactables = interactables.toList()
        ))
        graph.storeInteractableDefs(testRoomId, interactables.toList())
        return graph
    }

    private fun damageTrap(
        id: String = "trap_1",
        damage: Int = 10,
        perceptionDC: Int = 0,
        saveStat: String = "",
        saveDC: Int = 0,
        saveType: String = "",
        triggerType: String = "ON_ENTER"
    ): RoomInteractable = RoomInteractable(
        id = id,
        label = "Spike Trap",
        description = "A trap.",
        actionType = "DAMAGE_TRAP",
        actionData = buildMap {
            put("damage", damage.toString())
            if (saveStat.isNotEmpty()) put("saveStat", saveStat)
            if (saveDC > 0) put("saveDC", saveDC.toString())
            if (saveType.isNotEmpty()) put("saveType", saveType)
        },
        perceptionDC = perceptionDC,
        triggerType = triggerType
    )

    @Test
    fun `room with no interactables returns NoTrap`() = runBlocking {
        val graph = WorldGraph().apply {
            addRoom(Room(testRoomId, "Empty", "", emptyMap(), "test", 0, 0))
        }
        val tm = TrapManager(graph) { 10 }
        val result = tm.onPlayerEntered(stubSession(), testRoomId)
        assertEquals(listOf(TrapManager.TrapOutcome.NoTrap), result)
    }

    @Test
    fun `ON_ACTION interactables are ignored on enter`() = runBlocking {
        val nonTrap = damageTrap(triggerType = "ON_ACTION", damage = 50)
        val graph = roomWith(nonTrap)
        val tm = TrapManager(graph) { 1 } // would fire if triggerType were ON_ENTER

        val session = stubSession(hp = 100)
        val result = tm.onPlayerEntered(session, testRoomId)

        assertEquals(listOf(TrapManager.TrapOutcome.NoTrap), result)
        assertEquals(100, session.player!!.currentHp) // no damage
        assertFalse(session.hasTrippedTrap(testRoomId, nonTrap.id))
    }

    @Test
    fun `non-DAMAGE_TRAP ON_ENTER actions are ignored by trap manager`() = runBlocking {
        // ON_ENTER lever puzzles aren't traps — TrapManager only resolves DAMAGE_TRAP.
        val onEnterLever = RoomInteractable(
            id = "lever_1", label = "Lever", description = "",
            actionType = "EXIT_OPEN",
            actionData = mapOf("direction" to "NORTH"),
            triggerType = "ON_ENTER"
        )
        val graph = roomWith(onEnterLever)
        val tm = TrapManager(graph) { 1 }
        val result = tm.onPlayerEntered(stubSession(), testRoomId)
        assertEquals(listOf(TrapManager.TrapOutcome.NoTrap), result)
    }

    @Test
    fun `successful detection prevents trap firing and marks discovered`() = runBlocking {
        // perception = (intellect 60 + agility 60) / 2 = 60. roll = 60 + level/2 (5/2=2) + 20 = 82, vs DC 12 → detected.
        val trap = damageTrap(damage = 30, perceptionDC = 12)
        val graph = roomWith(trap)
        val tm = TrapManager(graph) { 20 }

        val session = stubSession(stats = Stats(intellect = 60, agility = 60), hp = 100)
        val result = tm.onPlayerEntered(session, testRoomId)

        assertEquals(1, result.size)
        assertTrue(result.first() is TrapManager.TrapOutcome.Detected)
        assertEquals(100, session.player!!.currentHp) // no damage
        assertTrue(session.hasDiscoveredInteractable(testRoomId, trap.id))
        assertFalse(session.hasTrippedTrap(testRoomId, trap.id))
    }

    @Test
    fun `failed detection causes trap to fire`() = runBlocking {
        // perception = (intellect 0 + agility 0) / 2 = 0. roll = 0 + 0 + 1 = 1, vs DC 18 → undetected → fires.
        val trap = damageTrap(damage = 25, perceptionDC = 18)
        val graph = roomWith(trap)
        val tm = TrapManager(graph) { 1 }

        val session = stubSession(stats = Stats(intellect = 0, agility = 0), hp = 100)
        val result = tm.onPlayerEntered(session, testRoomId)

        assertEquals(1, result.size)
        val triggered = result.first() as TrapManager.TrapOutcome.Triggered
        assertEquals(trap.id, triggered.featureId)
        assertEquals(25, triggered.damage)
        assertEquals(75, session.player!!.currentHp) // 100 - 25
        assertTrue(session.hasTrippedTrap(testRoomId, trap.id))
    }

    @Test
    fun `trap with no perceptionDC always fires`() = runBlocking {
        val trap = damageTrap(damage = 10, perceptionDC = 0)
        val graph = roomWith(trap)
        val tm = TrapManager(graph) { 20 } // even max roll wouldn't help

        val session = stubSession(stats = Stats(intellect = 100, agility = 100), hp = 100)
        val result = tm.onPlayerEntered(session, testRoomId)

        assertTrue(result.first() is TrapManager.TrapOutcome.Triggered)
        assertEquals(90, session.player!!.currentHp)
    }

    @Test
    fun `tripped trap does not refire on second entry`() = runBlocking {
        val trap = damageTrap(damage = 10, perceptionDC = 0)
        val graph = roomWith(trap)
        val tm = TrapManager(graph) { 1 }

        val session = stubSession(hp = 100)

        val first = tm.onPlayerEntered(session, testRoomId)
        assertTrue(first.first() is TrapManager.TrapOutcome.Triggered)
        assertEquals(90, session.player!!.currentHp)

        val second = tm.onPlayerEntered(session, testRoomId)
        assertEquals(listOf(TrapManager.TrapOutcome.NoTrap), second)
        assertEquals(90, session.player!!.currentHp) // no further damage
    }

    @Test
    fun `globally used trap is skipped even for fresh character`() = runBlocking {
        val trap = damageTrap(damage = 50, perceptionDC = 0)
        val graph = roomWith(trap)
        graph.markInteractableUsed(testRoomId, trap.id, resetTicks = 100)

        val tm = TrapManager(graph) { 1 }
        val session = stubSession(hp = 100)
        val result = tm.onPlayerEntered(session, testRoomId)

        assertEquals(listOf(TrapManager.TrapOutcome.NoTrap), result)
        assertEquals(100, session.player!!.currentHp)
        assertFalse(session.hasTrippedTrap(testRoomId, trap.id))
    }

    @Test
    fun `successful AGILITY DODGE save avoids all damage`() = runBlocking {
        // agility 80 + level/2 (5/2=2) + roll 20 = 102, vs DC 30 → AVOID
        val trap = damageTrap(damage = 50, perceptionDC = 0,
            saveStat = "AGILITY", saveDC = 30, saveType = "DODGE")
        val graph = roomWith(trap)
        val tm = TrapManager(graph) { 20 }

        val session = stubSession(stats = Stats(agility = 80), hp = 100)
        val result = tm.onPlayerEntered(session, testRoomId)

        val triggered = result.first() as TrapManager.TrapOutcome.Triggered
        assertEquals(0, triggered.damage)
        assertEquals(TrapResolver.SaveOutcome.AVOID, triggered.saveOutcome)
        assertEquals(100, session.player!!.currentHp) // unchanged
    }

    @Test
    fun `successful TOUGHNESS RESIST save halves damage`() = runBlocking {
        // toughness (str 80 + wil 60)/2 = 70 + level/2 (5/2=2) + roll 18 = 90, vs DC 30 → HALF
        val trap = damageTrap(damage = 30, perceptionDC = 0,
            saveStat = "TOUGHNESS", saveDC = 30, saveType = "RESIST")
        val graph = roomWith(trap)
        val tm = TrapManager(graph) { 18 }

        val session = stubSession(stats = Stats(strength = 80, willpower = 60), hp = 100)
        val result = tm.onPlayerEntered(session, testRoomId)

        val triggered = result.first() as TrapManager.TrapOutcome.Triggered
        assertEquals(15, triggered.damage) // 30 / 2
        assertEquals(TrapResolver.SaveOutcome.HALF, triggered.saveOutcome)
        assertEquals(85, session.player!!.currentHp)
    }

    @Test
    fun `multiple traps in same room all resolve`() = runBlocking {
        val trapA = damageTrap(id = "trap_a", damage = 10, perceptionDC = 0)
        val trapB = damageTrap(id = "trap_b", damage = 15, perceptionDC = 0)
        val graph = roomWith(trapA, trapB)
        val tm = TrapManager(graph) { 1 }

        val session = stubSession(hp = 100)
        val result = tm.onPlayerEntered(session, testRoomId)

        assertEquals(2, result.size)
        assertTrue(result.all { it is TrapManager.TrapOutcome.Triggered })
        assertEquals(75, session.player!!.currentHp) // 100 - 10 - 15
        assertTrue(session.hasTrippedTrap(testRoomId, "trap_a"))
        assertTrue(session.hasTrippedTrap(testRoomId, "trap_b"))
    }

    @Test
    fun `trap damage cannot kill player (HP floors at 1)`() = runBlocking {
        // Existing EffectApplicator floors HP at 1 — trap damage inherits this.
        // Real death flow is a follow-up PR.
        val trap = damageTrap(damage = 9999, perceptionDC = 0)
        val graph = roomWith(trap)
        val tm = TrapManager(graph) { 1 }

        val session = stubSession(hp = 50)
        val result = tm.onPlayerEntered(session, testRoomId)

        assertTrue(result.first() is TrapManager.TrapOutcome.Triggered)
        assertEquals(1, session.player!!.currentHp)
    }

    @Test
    fun `previously discovered trap fires without re-rolling detection`() = runBlocking {
        // If player already saw the trap (from a prior detection roll, maybe with buffs since dropped),
        // they should still be able to walk past — discovery is sticky. They have to opt-in via interact
        // to trigger the trap, which is the ON_ACTION model. So a discovered ON_ENTER trap... hmm.
        // Current TrapManager logic: skips detection re-roll if discovered, but still fires the trap.
        // This is intentional — ON_ENTER means "fires on enter unless detected and avoided"; once detected
        // the player CAN avoid by not entering, but if they enter anyway, it fires.
        val trap = damageTrap(damage = 10, perceptionDC = 12)
        val graph = roomWith(trap)
        val tm = TrapManager(graph) { 20 }

        val session = stubSession(hp = 100)
        session.discoverInteractable(testRoomId, trap.id)

        val result = tm.onPlayerEntered(session, testRoomId)

        assertTrue(result.first() is TrapManager.TrapOutcome.Triggered)
        assertEquals(90, session.player!!.currentHp)
    }
}
