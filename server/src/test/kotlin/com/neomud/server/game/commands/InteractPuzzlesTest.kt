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
    fun puzzle_completedPuzzleStaysSolvedOnReentry() = runBlocking {
        // After completion, the flag is set to totalSteps. Re-tapping any step should
        // be idempotent (no re-fire of success message, no accidental reset).
        val flags = FakePlayerFlagsRepository()
        flags.setFlag(testPlayerName, "puzzle:ancient_seq:step", "2")  // already solved
        val finalStep = RoomInteractable(
            id = "pillar_b", label = "pillar B", description = "Pillar.",
            actionType = "PUZZLE_STEP",
            actionData = mapOf("puzzleGroupId" to "ancient_seq", "puzzleStepIndex" to "1", "puzzleTotalSteps" to "2",
                "successMessage" to "Pillars sing.",
                "alreadySolvedMessage" to "The pillars are still." )
        )
        val wg = WorldGraph()
        wg.addRoom(roomWithLockedNorth(target = "test:beyond"))
        wg.addRoom(Room(id = "test:beyond", name = "B", description = "", exits = emptyMap(), zoneId = "test", x = 0, y = 1))
        wg.storeInteractableDefs(testRoom, listOf(finalStep))
        val cmd = buildCommand(wg, flags = flags)
        val session = newSession()

        cmd.execute(session, "pillar_b")

        // Flag should remain at totalSteps (still "2"), NOT reset.
        assertEquals("2", flags.getFlag(testPlayerName, "puzzle:ancient_seq:step"),
            "Re-tap on completed puzzle must NOT reset the flag")
        // Should see the already-solved message, not the success message.
        val res = drainMessages(session).filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertTrue(res.message.contains("still"),
            "Expected alreadySolvedMessage; got: ${res.message}")
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

    // ─── PUZZLE_STEP requiredFlagKey ────────────────────────────────

    @Test
    fun puzzle_requiredFlagMissing_rejectsWithMessage() = runBlocking {
        val flags = FakePlayerFlagsRepository()
        val step = RoomInteractable(
            id = "bell_dawn", label = "tide-bell of Dawn", description = "A bell.",
            actionType = "PUZZLE_STEP",
            actionData = mapOf(
                "puzzleGroupId" to "tide_bells", "puzzleStepIndex" to "0", "puzzleTotalSteps" to "2",
                "requiredFlagKey" to "mara_hymn_given", "requiredFlagValue" to "1",
                "requiredFlagMessage" to "You don't know the hymn.",
                "advanceMessage" to "The bell sings.",
                "successDirection" to "NORTH"
            )
        )
        val wg = setupWorld(step)
        val cmd = buildCommand(wg, flags = flags)
        val session = newSession()

        cmd.execute(session, "bell_dawn")

        val res = drainMessages(session).filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertEquals(false, res.success)
        assertTrue(res.message.contains("don't know the hymn"))
        assertEquals(null, flags.getFlag(testPlayerName, "puzzle:tide_bells:step"),
            "Progress should not advance when requiredFlag is missing")
    }

    @Test
    fun puzzle_requiredFlagPresent_allowsNormalProgress() = runBlocking {
        val flags = FakePlayerFlagsRepository()
        flags.setFlag(testPlayerName, "mara_hymn_given", "1")
        val step = RoomInteractable(
            id = "bell_dawn", label = "tide-bell of Dawn", description = "A bell.",
            actionType = "PUZZLE_STEP",
            actionData = mapOf(
                "puzzleGroupId" to "tide_bells", "puzzleStepIndex" to "0", "puzzleTotalSteps" to "2",
                "requiredFlagKey" to "mara_hymn_given", "requiredFlagValue" to "1",
                "requiredFlagMessage" to "You don't know the hymn.",
                "advanceMessage" to "The bell sings."
            )
        )
        val wg = setupWorld(step)
        val cmd = buildCommand(wg, flags = flags)
        val session = newSession()

        cmd.execute(session, "bell_dawn")

        assertEquals("1", flags.getFlag(testPlayerName, "puzzle:tide_bells:step"),
            "Progress should advance when requiredFlag is present")
        val res = drainMessages(session).filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertTrue(res.success)
        assertTrue(res.message.contains("bell sings"))
    }

    // ─── completionFlagKey ──────────────────────────────────────────

    @Test
    fun puzzle_completionFlagSet_onSolve() = runBlocking {
        val flags = FakePlayerFlagsRepository()
        val step0 = RoomInteractable(
            id = "lever_a", label = "lever A", description = "Lever.",
            actionType = "PUZZLE_STEP",
            actionData = mapOf(
                "puzzleGroupId" to "gate_seq", "puzzleStepIndex" to "0", "puzzleTotalSteps" to "1",
                "completionFlagKey" to "puzzle:gate_seq:complete", "completionFlagValue" to "done",
                "successMessage" to "The gate grinds open.",
                "successDirection" to "NORTH"
            )
        )
        val wg = setupWorld(step0)
        val cmd = buildCommand(wg, flags = flags)
        val session = newSession()

        cmd.execute(session, "lever_a")

        assertEquals("done", flags.getFlag(testPlayerName, "puzzle:gate_seq:complete"),
            "Completion flag should be set after solving the puzzle")
        assertEquals("1", flags.getFlag(testPlayerName, "puzzle:gate_seq:step"),
            "Puzzle step flag should also be set")
    }

    @Test
    fun puzzle_alreadySolved_withRequiredFlag_showsAlreadySolvedMessage() = runBlocking {
        val flags = FakePlayerFlagsRepository()
        flags.setFlag(testPlayerName, "puzzle:gated_seq:step", "1")  // already solved (totalSteps=1)
        // Player does NOT have the required flag — but it shouldn't matter because puzzle is already solved
        val step = RoomInteractable(
            id = "gated_lever", label = "gated lever", description = "A lever.",
            actionType = "PUZZLE_STEP",
            actionData = mapOf(
                "puzzleGroupId" to "gated_seq", "puzzleStepIndex" to "0", "puzzleTotalSteps" to "1",
                "requiredFlagKey" to "some_prerequisite", "requiredFlagValue" to "1",
                "requiredFlagMessage" to "You lack the prerequisite.",
                "alreadySolvedMessage" to "The lever is already pulled.",
                "successDirection" to "NORTH"
            )
        )
        val wg = setupWorld(step)
        val cmd = buildCommand(wg, flags = flags)
        val session = newSession()

        cmd.execute(session, "gated_lever")

        val res = drainMessages(session).filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertTrue(res.success, "Already-solved result should be success=true, not a flag-gate failure")
        assertTrue(res.message.contains("already pulled"),
            "Already-solved guard should fire before requiredFlagKey check; got: ${res.message}")
    }

    @Test
    fun placeItem_completionFlagSet_onSuccess() = runBlocking {
        val flags = FakePlayerFlagsRepository()
        val feat = RoomInteractable(
            id = "altar", label = "altar", description = "An altar.",
            actionType = "PLACE_ITEM",
            actionData = mapOf(
                "acceptedItems" to "item:warden_token",
                "consumeItem" to "true",
                "completionFlagKey" to "altar:offering_placed",
                "successDirection" to "NORTH",
                "successMessage" to "The altar accepts."
            )
        )
        val wg = setupWorld(feat)
        val inv = FakeInventoryRepository(mutableSetOf("item:warden_token"))
        val cmd = buildCommand(wg, inv = inv, flags = flags)
        val session = newSession()

        cmd.handlePlaceItem(session, "altar", "item:warden_token")

        assertEquals("true", flags.getFlag(testPlayerName, "altar:offering_placed"),
            "Completion flag should be set after successful placement")
    }
}
