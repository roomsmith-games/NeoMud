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
import kotlin.test.assertTrue

class InteractConditionalTest {

    private val testRoom = "test:room"
    private val testPlayerName = "Tester"

    private class FakePlayerFlagsRepository : PlayerFlagsRepository() {
        private val flags = mutableMapOf<Pair<String, String>, String>()
        override fun getFlag(p: String, k: String): String? = flags[p to k]
        override fun setFlag(p: String, k: String, v: String) { flags[p to k] = v }
        override fun clearFlag(p: String, k: String) { flags.remove(p to k) }
        override fun loadAllFlags(p: String): Map<String, String> =
            flags.filter { it.key.first == p }.mapKeys { it.key.second }
        fun seed(player: String, key: String, value: String) { flags[player to key] = value }
    }

    private class FakeInventoryRepository(private val owned: MutableSet<String> = mutableSetOf()) : InventoryRepository(ItemCatalog(emptyList())) {
        override fun getInventory(playerName: String): List<InventoryItem> =
            owned.map { InventoryItem(itemId = it, quantity = 1, equipped = false, slot = "") }
    }

    private fun newSession(level: Int = 5): PlayerSession {
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
            level = level,
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
            inventoryCommand = null,
            playerFlagsRepository = flags
        )
    }

    private fun setupWorld(interactable: RoomInteractable): WorldGraph {
        val wg = WorldGraph()
        val exits = mutableMapOf(Direction.NORTH to "test:beyond")
        val locked = mapOf(Direction.NORTH to 99)
        wg.addRoom(Room(
            id = testRoom, name = "Test Chamber", description = "A chamber.",
            exits = exits, zoneId = "test", x = 0, y = 0, lockedExits = locked
        ))
        wg.addRoom(Room(id = "test:beyond", name = "Beyond", description = "", exits = emptyMap(), zoneId = "test", x = 0, y = 1))
        wg.storeInteractableDefs(testRoom, listOf(interactable))
        return wg
    }

    private fun conditionalInteractable(data: Map<String, String>) = RoomInteractable(
        id = "gate_1",
        label = "Storm Gate",
        description = "A sealed gate.",
        failureMessage = "",
        icon = "",
        actionType = "CONDITIONAL_TRIGGER",
        actionData = data,
        difficulty = 0,
        difficultyCheck = "",
        perceptionDC = 0,
        cooldownTicks = 0,
        resetTicks = 0,
        sound = "",
        triggerType = "ON_ACTION"
    )

    @Test
    fun `ITEM condition passes when player has the item`() = runBlocking {
        val inv = FakeInventoryRepository(mutableSetOf("item:storm_key"))
        val feat = conditionalInteractable(mapOf(
            "conditionType" to "ITEM",
            "requiredItemId" to "item:storm_key",
            "successDirection" to "NORTH",
            "successMessage" to "The gate opens."
        ))
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg, inv = inv)
        val session = newSession()

        cmd.execute(session, "gate_1")

        val msgs = drainMessages(session)
        val result = msgs.filterIsInstance<ServerMessage.InteractResult>().first()
        assertTrue(result.success)
        assertEquals("The gate opens.", result.message)
    }

    @Test
    fun `ITEM condition fails when player lacks the item`() = runBlocking {
        val inv = FakeInventoryRepository(mutableSetOf())
        val feat = conditionalInteractable(mapOf(
            "conditionType" to "ITEM",
            "requiredItemId" to "item:storm_key",
            "failureMessage" to "You need the Storm Key."
        ))
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg, inv = inv)
        val session = newSession()

        cmd.execute(session, "gate_1")

        val msgs = drainMessages(session)
        val result = msgs.filterIsInstance<ServerMessage.InteractResult>().first()
        assertTrue(!result.success)
        assertEquals("You need the Storm Key.", result.message)
    }

    @Test
    fun `FLAG condition passes when flag matches`() = runBlocking {
        val flags = FakePlayerFlagsRepository()
        flags.seed(testPlayerName, "puzzle:storm:step", "5")
        val feat = conditionalInteractable(mapOf(
            "conditionType" to "FLAG",
            "requiredFlagKey" to "puzzle:storm:step",
            "requiredFlagValue" to "5",
            "successDirection" to "NORTH",
            "successMessage" to "The puzzle lock releases."
        ))
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg, flags = flags)
        val session = newSession()

        cmd.execute(session, "gate_1")

        val msgs = drainMessages(session)
        val result = msgs.filterIsInstance<ServerMessage.InteractResult>().first()
        assertTrue(result.success)
    }

    @Test
    fun `FLAG condition fails when flag is wrong`() = runBlocking {
        val flags = FakePlayerFlagsRepository()
        flags.seed(testPlayerName, "puzzle:storm:step", "3")
        val feat = conditionalInteractable(mapOf(
            "conditionType" to "FLAG",
            "requiredFlagKey" to "puzzle:storm:step",
            "requiredFlagValue" to "5"
        ))
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg, flags = flags)
        val session = newSession()

        cmd.execute(session, "gate_1")

        val msgs = drainMessages(session)
        val result = msgs.filterIsInstance<ServerMessage.InteractResult>().first()
        assertTrue(!result.success)
    }

    @Test
    fun `FLAG condition fails when flag is missing`() = runBlocking {
        val flags = FakePlayerFlagsRepository()
        val feat = conditionalInteractable(mapOf(
            "conditionType" to "FLAG",
            "requiredFlagKey" to "puzzle:storm:step",
            "requiredFlagValue" to "5"
        ))
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg, flags = flags)
        val session = newSession()

        cmd.execute(session, "gate_1")

        val msgs = drainMessages(session)
        val result = msgs.filterIsInstance<ServerMessage.InteractResult>().first()
        assertTrue(!result.success)
    }

    @Test
    fun `LEVEL condition passes when player is high enough`() = runBlocking {
        val feat = conditionalInteractable(mapOf(
            "conditionType" to "LEVEL",
            "requiredLevel" to "5",
            "successDirection" to "NORTH",
            "successMessage" to "You are worthy."
        ))
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val session = newSession(level = 10)

        cmd.execute(session, "gate_1")

        val msgs = drainMessages(session)
        val result = msgs.filterIsInstance<ServerMessage.InteractResult>().first()
        assertTrue(result.success)
    }

    @Test
    fun `LEVEL condition fails when player is too low`() = runBlocking {
        val feat = conditionalInteractable(mapOf(
            "conditionType" to "LEVEL",
            "requiredLevel" to "25",
            "failureMessage" to "Too weak."
        ))
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val session = newSession(level = 10)

        cmd.execute(session, "gate_1")

        val msgs = drainMessages(session)
        val result = msgs.filterIsInstance<ServerMessage.InteractResult>().first()
        assertTrue(!result.success)
        assertEquals("Too weak.", result.message)
    }

    @Test
    fun `unknown conditionType returns failure gracefully`() = runBlocking {
        val feat = conditionalInteractable(mapOf(
            "conditionType" to "MAGIC_AURA"
        ))
        val wg = setupWorld(feat)
        val cmd = buildCommand(wg)
        val session = newSession()

        cmd.execute(session, "gate_1")

        val msgs = drainMessages(session)
        val result = msgs.filterIsInstance<ServerMessage.InteractResult>().first()
        assertTrue(!result.success)
    }
}
