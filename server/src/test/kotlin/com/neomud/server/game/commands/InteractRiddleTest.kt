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
import com.neomud.server.session.TransportSession
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InteractRiddleTest {

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

    private class FakeInventoryRepository : InventoryRepository(ItemCatalog(emptyList())) {
        override fun getInventory(playerName: String): List<InventoryItem> = emptyList()
        override fun removeItem(playerName: String, itemId: String, quantity: Int): Boolean = false
        override fun addItem(playerName: String, itemId: String, quantity: Int): Boolean = true
    }

    private class FakeInventoryCommand : InventoryCommand(
        FakeInventoryRepository(),
        ItemCatalog(emptyList()),
        CoinRepository(),
        WorldGraph(),
        SessionManager()
    ) {
        override suspend fun sendInventoryUpdate(session: PlayerSession) {}
    }

    private fun newSession(): Pair<PlayerSession, MutableList<com.neomud.shared.protocol.ServerMessage>> {
        val received = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val session = PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) { received.add(message) }
            override suspend fun close(reason: String) {}
        })
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
        return session to received
    }

    private fun buildCommand(
        worldGraph: WorldGraph,
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
            inventoryRepository = FakeInventoryRepository(),
            inventoryCommand = FakeInventoryCommand(),
            playerFlagsRepository = flags
        )
    }

    private fun setupWorld(interactable: RoomInteractable): WorldGraph {
        val wg = WorldGraph()
        val exits = mutableMapOf<Direction, String>(Direction.NORTH to "test:beyond")
        val locked = mapOf(Direction.NORTH to 99)
        wg.addRoom(Room(
            id = testRoom, name = "Test Chamber", description = "A chamber for tests.",
            exits = exits, zoneId = "test", x = 0, y = 0, lockedExits = locked
        ))
        wg.addRoom(Room(id = "test:beyond", name = "Beyond", description = "", exits = emptyMap(), zoneId = "test", x = 0, y = 1))
        wg.storeInteractableDefs(testRoom, listOf(interactable))
        return wg
    }

    private fun riddleFeat(
        id: String = "door_riddle",
        label: String = "inscribed archway",
        question: String = "What has keys but no locks?",
        hint: String? = "Think musically.",
        acceptedAnswers: String = "piano,keyboard",
        synonyms: String = "a piano",
        successDirection: String = "NORTH",
        successMessage: String = "The archway shimmers and opens.",
        failureMessage: String = "The inscription pulses red."
    ) = RoomInteractable(
        id = id, label = label, description = "An archway covered in runes.",
        actionType = "RIDDLE_PROMPT",
        actionData = buildMap {
            put("question", question)
            hint?.let { put("hint", it) }
            put("acceptedAnswers", acceptedAnswers)
            if (synonyms.isNotBlank()) put("synonyms", synonyms)
            put("successDirection", successDirection)
            put("successMessage", successMessage)
            put("failureMessage", failureMessage)
        }
    )

    @Test
    fun riddle_tappingFiresPrompt() = runBlocking {
        val feat = riddleFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.execute(session, "door_riddle")

        val prompt = received.filterIsInstance<ServerMessage.RiddlePrompt>().firstOrNull()
        assertNotNull(prompt, "Tapping a RIDDLE_PROMPT should emit RiddlePrompt")
        assertEquals("door_riddle", prompt.featureId)
        assertEquals("What has keys but no locks?", prompt.question)
        assertEquals("Think musically.", prompt.hint)
        assertEquals("inscribed archway", prompt.label)
    }

    @Test
    fun riddle_correctAnswer_opensExitAndMarksUsed() = runBlocking {
        val feat = riddleFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.handleRiddleAnswer(session, "door_riddle", "piano")

        val northLock = wg.getRoom(testRoom)!!.lockedExits[Direction.NORTH]
        assertEquals(0, northLock ?: 0, "NORTH exit should be unlocked")
        assertTrue(wg.isInteractableUsed(testRoom, "door_riddle"), "Interactable should be marked used")
        val res = received.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertTrue(res.success)
        assertTrue(res.message.contains("shimmers"))
    }

    @Test
    fun riddle_wrongAnswer_failsWithMessage() = runBlocking {
        val feat = riddleFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.handleRiddleAnswer(session, "door_riddle", "guitar")

        val northLock = wg.getRoom(testRoom)!!.lockedExits[Direction.NORTH]
        assertEquals(99, northLock, "NORTH exit should remain locked")
        assertTrue(!wg.isInteractableUsed(testRoom, "door_riddle"), "Interactable should NOT be marked used")
        val res = received.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertEquals(false, res.success)
        assertTrue(res.message.contains("pulses red"))
    }

    @Test
    fun riddle_caseInsensitiveMatch() = runBlocking {
        val feat = riddleFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.handleRiddleAnswer(session, "door_riddle", "PIANO")

        val res = received.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertTrue(res.success, "Case-insensitive match should succeed")
    }

    @Test
    fun riddle_synonymMatch() = runBlocking {
        val feat = riddleFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.handleRiddleAnswer(session, "door_riddle", "a piano")

        val res = received.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertTrue(res.success, "Synonym should match")
    }

    @Test
    fun riddle_alreadySolved_rejectsRetry() = runBlocking {
        val feat = riddleFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.handleRiddleAnswer(session, "door_riddle", "piano")
        received.clear()

        cmd.handleRiddleAnswer(session, "door_riddle", "piano")
        val res = received.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertEquals(false, res.success)
        assertTrue(res.message.contains("already been answered"))
    }

    @Test
    fun riddle_emptyAnswer_fails() = runBlocking {
        val feat = riddleFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.handleRiddleAnswer(session, "door_riddle", "   ")

        val res = received.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertEquals(false, res.success)
    }

    @Test
    fun riddle_leadingTrailingWhitespace_trimmed() = runBlocking {
        val feat = riddleFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.handleRiddleAnswer(session, "door_riddle", "  piano  ")

        val res = received.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertTrue(res.success, "Whitespace-trimmed answer should match")
    }

    @Test
    fun riddle_completionFlagSet_onCorrectAnswer() = runBlocking {
        val flags = FakePlayerFlagsRepository()
        val feat = RoomInteractable(
            id = "gate_riddle", label = "gate inscription", description = "Runes on a gate.",
            actionType = "RIDDLE_PROMPT",
            actionData = mapOf(
                "question" to "What walks on four legs?",
                "acceptedAnswers" to "man,human",
                "successDirection" to "NORTH",
                "successMessage" to "The gate opens.",
                "failureMessage" to "Wrong.",
                "completionFlagKey" to "riddle:gate:solved",
                "completionFlagValue" to "done"
            )
        )
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg, flags = flags)
        val (session, received) = newSession()

        cmd.handleRiddleAnswer(session, "gate_riddle", "man")

        assertEquals("done", flags.getFlag(testPlayerName, "riddle:gate:solved"),
            "Completion flag should be set after correct riddle answer")
    }
}
