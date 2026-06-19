package com.neomud.server.game.commands

import com.neomud.server.game.inventory.LootService
import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ItemCatalog
import com.neomud.server.world.LootTableCatalog
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.Direction
import com.neomud.shared.model.Player
import com.neomud.shared.model.Room
import com.neomud.shared.model.RoomInteractable
import com.neomud.shared.model.Stats
import com.neomud.server.session.TransportSession
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InteractTreasureDropTest {

    private val testRoom = "test:room"
    private val testPlayerName = "Tester"

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

    private fun treasureInteractable(data: Map<String, String>) = RoomInteractable(
        id = "chest_1",
        label = "Stone Chest",
        description = "A moss-covered chest.",
        failureMessage = "",
        icon = "",
        actionType = "TREASURE_DROP",
        actionData = data,
        difficulty = 0,
        difficultyCheck = "",
        perceptionDC = 0,
        cooldownTicks = 0,
        resetTicks = 0,
        sound = "",
        triggerType = "ON_ACTION"
    )

    private fun buildCommand(roomItemManager: RoomItemManager = RoomItemManager()): Pair<InteractCommand, RoomItemManager> {
        val worldGraph = WorldGraph()
        worldGraph.addRoom(Room(
            id = testRoom, name = "Test Cave", description = "A cave.",
            exits = mapOf(Direction.NORTH to "test:beyond"), zoneId = "test", x = 0, y = 0
        ))
        worldGraph.addRoom(Room(id = "test:beyond", name = "Beyond", description = "", exits = emptyMap(), zoneId = "test", x = 0, y = 1))

        val feat = treasureInteractable(mapOf(
            "items" to "item:health_potion,item:spider_silk_gloves",
            "minCopper" to "15", "maxCopper" to "40",
            "minSilver" to "0", "maxSilver" to "1",
            "message" to "The chest creaks open, revealing its contents!"
        ))
        worldGraph.storeInteractableDefs(testRoom, listOf(feat))

        val sessionManager = SessionManager()
        val npcManager = NpcManager(worldGraph, emptyMap(), emptyMap())
        val itemCatalog = ItemCatalog(emptyList())
        val lootService = LootService(itemCatalog)
        val lootTableCatalog = LootTableCatalog(emptyMap())
        val cmd = InteractCommand(
            worldGraph, sessionManager, npcManager, roomItemManager, lootService, lootTableCatalog
        )
        return cmd to roomItemManager
    }

    @Test
    fun `inline items are dropped to room floor`() = runBlocking {
        val roomItemManager = RoomItemManager()
        val (cmd, rim) = buildCommand(roomItemManager)
        val (session, received) = newSession()

        cmd.execute(session, "chest_1")

        val groundItems = rim.getGroundItems(testRoom)
        val itemIds = groundItems.map { it.itemId }
        assertTrue(itemIds.contains("item:health_potion"), "Should drop health_potion")
        assertTrue(itemIds.contains("item:spider_silk_gloves"), "Should drop spider_silk_gloves")
    }

    @Test
    fun `inline coins are dropped to room floor`() = runBlocking {
        val roomItemManager = RoomItemManager()
        val (cmd, rim) = buildCommand(roomItemManager)
        val (session, received) = newSession()

        cmd.execute(session, "chest_1")

        val coins = rim.getGroundCoins(testRoom)
        assertTrue(coins.copper in 15..40, "Copper should be 15-40, was ${coins.copper}")
        assertTrue(coins.silver in 0..1, "Silver should be 0-1, was ${coins.silver}")
    }

    @Test
    fun `custom message is sent to player`() = runBlocking {
        val (cmd, _) = buildCommand()
        val (session, received) = newSession()

        cmd.execute(session, "chest_1")

        val msgs = received
        val sysMsg = msgs.filterIsInstance<ServerMessage.SystemMessage>().firstOrNull()
        assertEquals("The chest creaks open, revealing its contents!", sysMsg?.message)
    }

    @Test
    fun `default message used when none specified`() = runBlocking {
        val worldGraph = WorldGraph()
        worldGraph.addRoom(Room(
            id = testRoom, name = "Test Cave", description = "A cave.",
            exits = emptyMap(), zoneId = "test", x = 0, y = 0
        ))
        val feat = treasureInteractable(mapOf("items" to "item:health_potion"))
        worldGraph.storeInteractableDefs(testRoom, listOf(feat))

        val sessionManager = SessionManager()
        val npcManager = NpcManager(worldGraph, emptyMap(), emptyMap())
        val itemCatalog = ItemCatalog(emptyList())
        val lootService = LootService(itemCatalog)
        val lootTableCatalog = LootTableCatalog(emptyMap())
        val cmd = InteractCommand(worldGraph, sessionManager, npcManager, RoomItemManager(), lootService, lootTableCatalog)
        val (session, received) = newSession()

        cmd.execute(session, "chest_1")

        val msgs = received
        val sysMsg = msgs.filterIsInstance<ServerMessage.SystemMessage>().firstOrNull()
        assertEquals("Items clatter to the ground.", sysMsg?.message)
    }

    @Test
    fun `empty items and no coins returns failure`() = runBlocking {
        val worldGraph = WorldGraph()
        worldGraph.addRoom(Room(
            id = testRoom, name = "Test Cave", description = "A cave.",
            exits = emptyMap(), zoneId = "test", x = 0, y = 0
        ))
        val feat = treasureInteractable(mapOf("items" to ""))
        worldGraph.storeInteractableDefs(testRoom, listOf(feat))

        val sessionManager = SessionManager()
        val npcManager = NpcManager(worldGraph, emptyMap(), emptyMap())
        val itemCatalog = ItemCatalog(emptyList())
        val lootService = LootService(itemCatalog)
        val lootTableCatalog = LootTableCatalog(emptyMap())
        val cmd = InteractCommand(worldGraph, sessionManager, npcManager, RoomItemManager(), lootService, lootTableCatalog)
        val (session, received) = newSession()

        cmd.execute(session, "chest_1")

        val msgs = received
        val result = msgs.filterIsInstance<ServerMessage.InteractResult>().firstOrNull()
        assertTrue(result != null, "Should send InteractResult")
        assertTrue(!result.success, "Should be failure when no items or coins")
        assertEquals("Nothing happens.", result.message)
    }

    @Test
    fun `coins-only treasure drop works without items`() = runBlocking {
        val worldGraph = WorldGraph()
        worldGraph.addRoom(Room(
            id = testRoom, name = "Test Cave", description = "A cave.",
            exits = emptyMap(), zoneId = "test", x = 0, y = 0
        ))
        val feat = treasureInteractable(mapOf(
            "minCopper" to "10", "maxCopper" to "10",
            "minGold" to "1", "maxGold" to "1"
        ))
        worldGraph.storeInteractableDefs(testRoom, listOf(feat))

        val roomItemManager = RoomItemManager()
        val sessionManager = SessionManager()
        val npcManager = NpcManager(worldGraph, emptyMap(), emptyMap())
        val itemCatalog = ItemCatalog(emptyList())
        val lootService = LootService(itemCatalog)
        val lootTableCatalog = LootTableCatalog(emptyMap())
        val cmd = InteractCommand(worldGraph, sessionManager, npcManager, roomItemManager, lootService, lootTableCatalog)
        val (session, received) = newSession()

        cmd.execute(session, "chest_1")

        val coins = roomItemManager.getGroundCoins(testRoom)
        assertEquals(10, coins.copper)
        assertEquals(1, coins.gold)

        val msgs = received
        val sysMsg = msgs.filterIsInstance<ServerMessage.SystemMessage>().firstOrNull()
        assertEquals("Items clatter to the ground.", sysMsg?.message)
    }
}
