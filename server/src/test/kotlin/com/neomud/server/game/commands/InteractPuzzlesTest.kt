package com.neomud.server.game.commands

import com.neomud.server.game.npc.NpcManager
import com.neomud.server.game.inventory.LootService
import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.server.persistence.repository.CoinRepository
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.persistence.repository.PlayerFlagsRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ItemCatalog
import com.neomud.server.world.LootTableCatalog
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.Direction
import com.neomud.shared.model.InventoryItem
import com.neomud.shared.model.Player
import com.neomud.shared.model.Room
import com.neomud.shared.model.RoomInteractable
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InteractPuzzlesTest {

    private val testRoom = "test:room"
    private val testPlayerName = "Tester"

    private class FakePlayerFlagsRepository : PlayerFlagsRepository() {
        private val flags = mutableMapOf<Pair<String, String>, String>()
        override fun getFlag(p: String, k: String): String? = flags[p to k]
        override fun setFlag(p: String, k: String, v: String) { flags[p to k] = v }
        override fun clearFlag(p: String, k: String) { flags.remove(p to k) }
        override fun loadAllFlags(p: String): Map<String, String> =
            flags.filter { it.key.first == p }.mapKeys { it.key.second }
    }

    private class FakeInventoryRepository(private val owned: MutableSet<String> = mutableSetOf()) : InventoryRepository(ItemCatalog(emptyList())) {
        val removed = mutableListOf<String>()
        override fun getInventory(playerName: String): List<InventoryItem> =
            owned.map { InventoryItem(itemId = it, quantity = 1, equipped = false, slot = "") }
        override fun removeItem(playerName: String, itemId: String, quantity: Int): Boolean {
            removed.add(itemId)
            return owned.remove(itemId)
        }
        override fun addItem(playerName: String, itemId: String, quantity: Int): Boolean {
            owned.add(itemId)
            return true
        }
    }

    private class FakeInventoryCommand : InventoryCommand(
        FakeInventoryRepository(),
        ItemCatalog(emptyList()),
        CoinRepository(),
        WorldGraph(),
        SessionManager()
    ) {
        var sendCount = 0
        override suspend fun sendInventoryUpdate(session: PlayerSession) { sendCount++ }
    }

    private fun newSession(): PlayerSession {
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val ws = object : WebSocketSession {
            override val coroutineContext: CoroutineContext get() = EmptyCoroutineContext
            override val incoming: Channel<Frame> get() = Channel()
            override val outgoing: Channel<Frame> get() = outgoing
            override val extensions: List<WebSocketExtension<*>> get() = emptyList()
            override var masking: Boolean = false
            override var maxFrameSize: Long = Long.MAX_VALUE
            override suspend fun flush() {}
            @Deprecated("Use cancel instead", replaceWith = ReplaceWith("cancel()"))
            override fun terminate() {}
        }
        val session = PlayerSession(ws)
        session.player = Player(
            name = testPlayerName,
            characterClass = "WARRIOR",
            race = "HUMAN",
            level = 5,
            currentHp = 50, maxHp = 50, currentMp = 0, maxMp = 0,
            currentRoomId = testRoom, currentXp = 0, xpToNextLevel = 100,
            stats = Stats(strength = 15, agility = 10, intellect = 5, willpower = 5, health = 15, charm = 5)
        )
        session.playerName = testPlayerName
        session.currentRoomId = testRoom
        return session
    }

    private fun drainMessages(session: PlayerSession): List<ServerMessage> {
        val out = session.webSocketSession.outgoing as Channel<Frame>
        val msgs = mutableListOf<ServerMessage>()
        while (true) {
            val f = out.tryReceive().getOrNull() ?: break
            if (f is Frame.Text) msgs.add(MessageSerializer.decodeServerMessage(f.readText()))
        }
        return msgs
    }

    private fun buildCommand(
        worldGraph: WorldGraph,
        inv: InventoryRepository = FakeInventoryRepository(),
        flags: PlayerFlagsRepository = FakePlayerFlagsRepository()
    ): InteractCommand {
        val sessionManager = SessionManager()
        val npcManager = NpcManager(worldGraph, emptyMap(), emptyMap())
        val itemCatalog = ItemCatalog(emptyList())
        val roomItemManager = RoomItemManager()
        val lootService = LootService(itemCatalog)
        val lootTableCatalog = LootTableCatalog(emptyMap())
        return InteractCommand(
            worldGraph, sessionManager, npcManager, roomItemManager, lootService, lootTableCatalog,
            inventoryRepository = inv,
            inventoryCommand = FakeInventoryCommand(),
            playerFlagsRepository = flags
        )
    }

    private fun roomWithLockedNorth(target: String? = null): Room {
        val exits = mutableMapOf<Direction, String>()
        target?.let { exits[Direction.NORTH] = it }
        val locked = if (target != null) mapOf(Direction.NORTH to 99) else emptyMap()
        return Room(
            id = testRoom,
            name = "Test Chamber",
            description = "A chamber for tests.",
            exits = exits,
            zoneId = "test",
            x = 0,
            y = 0,
            lockedExits = locked
        )
    }

    private fun setupWorld(interactable: RoomInteractable): WorldGraph {
        val wg = WorldGraph()
        wg.addRoom(roomWithLockedNorth(target = "test:beyond"))
        wg.addRoom(Room(id = "test:beyond", name = "Beyond", description = "", exits = emptyMap(), zoneId = "test", x = 0, y = 1))
        wg.storeInteractableDefs(testRoom, listOf(interactable))
        return wg
    }

    // ─── PLACE_ITEM ─────────────────────────────────────────────────

    @Test
    fun placeItem_tappingFiresPrompt() = runBlocking {
        val feat = RoomInteractable(
            id = "altar", label = "weathered altar", description = "An altar with a slot.",
            actionType = "PLACE_ITEM",
            actionData = mapOf(
                "acceptedItems" to "item:warden_token,item:bronze_key",
                "promptText" to "Place an offering.",
                "successDirection" to "NORTH",
                "successMessage" to "The altar accepts your offering. Stone grinds open."
            )
        )
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val session = newSession()

        cmd.execute(session, "altar")

        val prompt = drainMessages(session).filterIsInstance<ServerMessage.PlaceItemPrompt>().firstOrNull()
        assertNotNull(prompt, "Tapping a PLACE_ITEM should emit PlaceItemPrompt")
        assertEquals("altar", prompt.featureId)
        assertEquals(listOf("item:warden_token", "item:bronze_key"), prompt.acceptedItems)
        assertEquals("Place an offering.", prompt.prompt)
    }

    @Test
    fun placeItem_acceptedItemConsumesAndOpensExit() = runBlocking {
        val feat = RoomInteractable(
            id = "altar", label = "weathered altar", description = "An altar with a slot.",
            actionType = "PLACE_ITEM",
            actionData = mapOf(
                "acceptedItems" to "item:warden_token",
                "consumeItem" to "true",
                "successDirection" to "NORTH",
                "successMessage" to "The seal opens."
            )
        )
        val wg = setupWorld(feat)
        val inv = FakeInventoryRepository(mutableSetOf("item:warden_token"))
        val cmd = buildCommand(wg, inv = inv)
        val session = newSession()

        cmd.handlePlaceItem(session, "altar", "item:warden_token")

        // Item was consumed.
        assertEquals(listOf("item:warden_token"), inv.removed)
        // Exit was unlocked.
        val northLock = wg.getRoom(testRoom)!!.lockedExits[Direction.NORTH]
        assertEquals(0, northLock ?: 0, "NORTH exit should be unlocked (lock removed)")
        // Player got success message.
        val msgs = drainMessages(session)
        val res = msgs.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertTrue(res.success)
        assertTrue(res.message.contains("seal opens"))
    }

    @Test
    fun placeItem_wrongItemRejectsAndKeepsClosed() = runBlocking {
        val feat = RoomInteractable(
            id = "altar", label = "altar", description = "Altar.",
            actionType = "PLACE_ITEM",
            actionData = mapOf(
                "acceptedItems" to "item:warden_token",
                "successDirection" to "NORTH",
                "failureMessage" to "Not the right offering."
            )
        )
        val wg = setupWorld(feat)
        val inv = FakeInventoryRepository(mutableSetOf("item:rustic_dagger"))
        val cmd = buildCommand(wg, inv = inv)
        val session = newSession()

        cmd.handlePlaceItem(session, "altar", "item:rustic_dagger")

        assertTrue(inv.removed.isEmpty(), "Wrong item must NOT be consumed")
        val res = drainMessages(session).filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertEquals(false, res.success)
        assertTrue(res.message.contains("Not the right offering"))
    }

    @Test
    fun placeItem_unownedItemRejectsWithoutLeak() = runBlocking {
        val feat = RoomInteractable(
            id = "altar", label = "altar", description = "Altar.",
            actionType = "PLACE_ITEM",
            actionData = mapOf("acceptedItems" to "item:warden_token", "successDirection" to "NORTH")
        )
        val wg = setupWorld(feat)
        val inv = FakeInventoryRepository(mutableSetOf())  // empty inventory
        val cmd = buildCommand(wg, inv = inv)
        val session = newSession()

        cmd.handlePlaceItem(session, "altar", "item:warden_token")

        assertTrue(inv.removed.isEmpty())
        val res = drainMessages(session).filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertEquals(false, res.success)
    }

    @Test
    fun placeItem_consumeFalseKeepsItem() = runBlocking {
        val feat = RoomInteractable(
            id = "altar", label = "altar", description = "Altar.",
            actionType = "PLACE_ITEM",
            actionData = mapOf(
                "acceptedItems" to "item:warden_token",
                "consumeItem" to "false",
                "successDirection" to "NORTH"
            )
        )
        val wg = setupWorld(feat)
        val inv = FakeInventoryRepository(mutableSetOf("item:warden_token"))
        val cmd = buildCommand(wg, inv = inv)
        val session = newSession()

        cmd.handlePlaceItem(session, "altar", "item:warden_token")

        assertTrue(inv.removed.isEmpty(), "consumeItem=false must NOT consume")
    }

    // ─── PUZZLE_STEP ────────────────────────────────────────────────

    @Test
    fun puzzle_correctSequenceAdvancesAndCompletes() = runBlocking {
        val flags = FakePlayerFlagsRepository()
        // Two-step puzzle: step 0 then step 1, completes.
        val step0 = RoomInteractable(
            id = "pillar_a", label = "pillar A", description = "A glowing pillar.",
            actionType = "PUZZLE_STEP",
            actionData = mapOf("puzzleGroupId" to "ancient_seq", "puzzleStepIndex" to "0", "puzzleTotalSteps" to "2",
                "advanceMessage" to "Pillar A hums.", "successMessage" to "All pillars sing in harmony — the door opens.",
                "successDirection" to "NORTH")
        )
        val step1 = RoomInteractable(
            id = "pillar_b", label = "pillar B", description = "Another glowing pillar.",
            actionType = "PUZZLE_STEP",
            actionData = mapOf("puzzleGroupId" to "ancient_seq", "puzzleStepIndex" to "1", "puzzleTotalSteps" to "2",
                "successMessage" to "All pillars sing in harmony — the door opens.",
                "successDirection" to "NORTH")
        )
        val wg = WorldGraph()
        wg.addRoom(roomWithLockedNorth(target = "test:beyond"))
        wg.addRoom(Room(id = "test:beyond", name = "B", description = "", exits = emptyMap(), zoneId = "test", x = 0, y = 1))
        wg.storeInteractableDefs(testRoom, listOf(step0, step1))

        val cmd = buildCommand(wg, flags = flags)
        val session = newSession()

        // Step 0
        cmd.execute(session, "pillar_a")
        assertEquals("1", flags.getFlag(testPlayerName, "puzzle:ancient_seq:step"))
        // Step 1 (completion)
        cmd.execute(session, "pillar_b")
        // Door now unlocked
        val northLock = wg.getRoom(testRoom)!!.lockedExits[Direction.NORTH]
        assertEquals(0, northLock ?: 0, "After completion, NORTH should be unlocked")
        val msgs = drainMessages(session).filterIsInstance<ServerMessage.InteractResult>()
        assertTrue(msgs.any { it.message.contains("harmony") }, "Should see the success message")
    }

    @Test
    fun puzzle_wrongStepResetsProgress() = runBlocking {
        val flags = FakePlayerFlagsRepository()
        flags.setFlag(testPlayerName, "puzzle:ancient_seq:step", "2")  // already on step 2
        val step0 = RoomInteractable(
            id = "pillar_a", label = "pillar A", description = "Pillar.",
            actionType = "PUZZLE_STEP",
            actionData = mapOf("puzzleGroupId" to "ancient_seq", "puzzleStepIndex" to "0", "puzzleTotalSteps" to "3",
                "resetMessage" to "The pillars dim. The sequence is lost.",
                "successDirection" to "NORTH")
        )
        val wg = WorldGraph()
        wg.addRoom(roomWithLockedNorth(target = "test:beyond"))
        wg.addRoom(Room(id = "test:beyond", name = "B", description = "", exits = emptyMap(), zoneId = "test", x = 0, y = 1))
        wg.storeInteractableDefs(testRoom, listOf(step0))

        val cmd = buildCommand(wg, flags = flags)
        val session = newSession()

        cmd.execute(session, "pillar_a")  // expected step 0, but flags say 2 — wrong

        assertEquals("0", flags.getFlag(testPlayerName, "puzzle:ancient_seq:step"), "Wrong step must reset progress to 0")
        val res = drainMessages(session).filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertEquals(false, res.success)
        assertTrue(res.message.contains("sequence is lost"))
        // Door still locked
        val northLock = wg.getRoom(testRoom)!!.lockedExits[Direction.NORTH]
        assertEquals(99, northLock, "NORTH exit should remain locked after wrong step")
    }
}
