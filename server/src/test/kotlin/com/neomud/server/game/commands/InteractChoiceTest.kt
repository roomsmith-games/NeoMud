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

class InteractChoiceTest {

    private val testRoom = "test:room"
    private val testPlayerName = "Tester"

    private class FakePlayerFlagsRepository : PlayerFlagsRepository() {
        private val flags = mutableMapOf<Pair<String, String>, String>()
        override fun getFlag(p: String, k: String): String? = flags[p to k]
        override fun setFlag(p: String, k: String, v: String) { flags[p to k] = v }
        override fun clearFlag(p: String, k: String) { flags.remove(p to k) }
        override fun loadAllFlags(p: String): Map<String, String> =
            flags.filter { it.key.first == p }.mapKeys { it.key.second }
        fun peekFlag(player: String, key: String): String? = flags[player to key]
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

    private val fakeFlagsRepo = FakePlayerFlagsRepository()

    private fun buildCommand(worldGraph: WorldGraph): InteractCommand {
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
            playerFlagsRepository = fakeFlagsRepo
        )
    }

    private fun setupWorld(interactable: RoomInteractable): WorldGraph {
        val wg = WorldGraph()
        val exits = mutableMapOf(Direction.WEST to "test:west", Direction.EAST to "test:east")
        val locked = mapOf(Direction.WEST to 99, Direction.EAST to 99)
        wg.addRoom(Room(
            id = testRoom, name = "Test Chamber", description = "A test chamber.",
            exits = exits, zoneId = "test", x = 0, y = 0, lockedExits = locked
        ))
        wg.addRoom(Room(id = "test:west", name = "West", description = "", exits = emptyMap(), zoneId = "test", x = -1, y = 0))
        wg.addRoom(Room(id = "test:east", name = "East", description = "", exits = emptyMap(), zoneId = "test", x = 1, y = 0))
        wg.storeInteractableDefs(testRoom, listOf(interactable))
        return wg
    }

    private fun choiceFeat(
        id: String = "test_choice",
        label: String = "The Seal",
        question: String = "Choose your path.",
        flagKey: String = "choice:test_ending",
        options: String = """[{"id":"seal","label":"Seal It"},{"id":"sunder","label":"Break It"}]""",
        alreadyChosenMessage: String = "Already chosen."
    ) = RoomInteractable(
        id = id, label = label, description = "A choice point.",
        actionType = "CHOICE_PROMPT",
        actionData = mapOf(
            "question" to question,
            "flagKey" to flagKey,
            "options" to options,
            "exitDirection_seal" to "WEST",
            "exitDirection_sunder" to "EAST",
            "successMessage_seal" to "You chose to seal.",
            "successMessage_sunder" to "You chose to sunder.",
            "alreadyChosenMessage" to alreadyChosenMessage
        )
    )

    @Test
    fun choice_tapping_fires_prompt() = runBlocking {
        val feat = choiceFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.execute(session, "test_choice")

        val prompt = received.filterIsInstance<ServerMessage.ChoicePrompt>().firstOrNull()
        assertNotNull(prompt, "Tapping a CHOICE_PROMPT should emit ChoicePrompt")
        assertEquals("test_choice", prompt.featureId)
        assertEquals("Choose your path.", prompt.question)
        assertEquals(2, prompt.options.size)
        assertEquals("seal", prompt.options[0].id)
        assertEquals("sunder", prompt.options[1].id)
    }

    @Test
    fun choice_valid_sets_flag_and_opens_exit() = runBlocking {
        val feat = choiceFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.handleMakeChoice(session, "test_choice", "seal")

        assertEquals("seal", fakeFlagsRepo.peekFlag(testPlayerName, "choice:test_ending"))
        val westLock = wg.getRoom(testRoom)!!.lockedExits[Direction.WEST]
        assertEquals(0, westLock ?: 0, "WEST exit should be unlocked for seal choice")
        val eastLock = wg.getRoom(testRoom)!!.lockedExits[Direction.EAST]
        assertEquals(99, eastLock, "EAST exit should remain locked")
        val res = received.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertTrue(res.success)
        assertTrue(res.message.contains("seal"))
    }

    @Test
    fun choice_sunder_opens_east_exit() = runBlocking {
        val feat = choiceFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.handleMakeChoice(session, "test_choice", "sunder")

        assertEquals("sunder", fakeFlagsRepo.peekFlag(testPlayerName, "choice:test_ending"))
        val eastLock = wg.getRoom(testRoom)!!.lockedExits[Direction.EAST]
        assertEquals(0, eastLock ?: 0, "EAST exit should be unlocked for sunder choice")
        val res = received.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertTrue(res.success)
        assertTrue(res.message.contains("sunder"))
    }

    @Test
    fun choice_invalid_choiceId_rejected() = runBlocking {
        val feat = choiceFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.handleMakeChoice(session, "test_choice", "invalid_option")

        val res = received.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertEquals(false, res.success)
        assertTrue(res.message.contains("not a valid choice"))
    }

    @Test
    fun choice_already_chosen_rejected() = runBlocking {
        val feat = choiceFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        cmd.handleMakeChoice(session, "test_choice", "seal")
        received.clear()

        cmd.handleMakeChoice(session, "test_choice", "sunder")
        val res = received.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(res)
        assertEquals(false, res.success)
    }

    @Test
    fun choice_prompt_blocked_when_already_chosen() = runBlocking {
        val feat = choiceFeat()
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val (session, received) = newSession()

        fakeFlagsRepo.setFlag(testPlayerName, "choice:test_ending", "seal")

        cmd.execute(session, "test_choice")

        val msgs = received
        val prompt = msgs.filterIsInstance<ServerMessage.ChoicePrompt>().firstOrNull()
        assertEquals(null, prompt, "Should NOT send ChoicePrompt when flag already set")
        val result = msgs.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertNotNull(result)
        assertEquals(false, result.success)
        assertTrue(result.message.contains("Already chosen"))
    }
}
