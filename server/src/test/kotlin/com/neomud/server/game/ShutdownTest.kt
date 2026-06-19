package com.neomud.server.game

import com.neomud.server.game.combat.CombatManager
import com.neomud.server.game.inventory.EquipmentService
import com.neomud.server.game.inventory.LootService
import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.persistence.repository.CoinRepository
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.session.TransportSession
import com.neomud.server.world.*
import com.neomud.shared.model.*
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShutdownTest {

    @Test
    fun testShutdownConfigConstants() {
        assertEquals(60, GameConfig.Shutdown.DEFAULT_DELAY_SECONDS)
        assertTrue(GameConfig.Shutdown.WARNING_AT_SECONDS.contains(60))
        assertTrue(GameConfig.Shutdown.WARNING_AT_SECONDS.contains(30))
        assertTrue(GameConfig.Shutdown.WARNING_AT_SECONDS.contains(10))
        assertTrue(GameConfig.Shutdown.WARNING_AT_SECONDS.contains(5))
        assertTrue(GameConfig.Shutdown.WARNING_AT_SECONDS.contains(0))
        // Sorted descending
        assertEquals(
            GameConfig.Shutdown.WARNING_AT_SECONDS.sortedDescending(),
            GameConfig.Shutdown.WARNING_AT_SECONDS
        )
    }

    @Test
    fun testNotShuttingDownByDefault() {
        val gameLoop = createTestGameLoop()
        assertFalse(gameLoop.isShuttingDown)
        assertEquals(-1, gameLoop.shutdownSecondsRemaining)
    }

    @Test
    fun testInitiateShutdownSetsCounter() {
        val gameLoop = createTestGameLoop()
        gameLoop.initiateShutdown(30)
        assertTrue(gameLoop.isShuttingDown)
        assertEquals(30, gameLoop.shutdownSecondsRemaining)
    }

    @Test
    fun testInitiateShutdownClampsNegative() {
        val gameLoop = createTestGameLoop()
        gameLoop.initiateShutdown(-5)
        assertTrue(gameLoop.isShuttingDown)
        assertEquals(0, gameLoop.shutdownSecondsRemaining)
    }

    @Test
    fun testProcessShutdownTickReturnsFalseWhenNotShuttingDown() = runBlocking {
        val gameLoop = createTestGameLoop()
        assertFalse(gameLoop.processShutdownTick())
    }

    @Test
    fun testProcessShutdownTickReturnsTrueAtZero() = runBlocking {
        val gameLoop = createTestGameLoop()
        gameLoop.initiateShutdown(0)
        assertTrue(gameLoop.processShutdownTick())
    }

    @Test
    fun testProcessShutdownTickDecrementsCounter() = runBlocking {
        val gameLoop = createTestGameLoop()
        gameLoop.initiateShutdown(10)

        val result = gameLoop.processShutdownTick()
        assertFalse(result)
        // Tick interval is 1500ms = 1 second (integer division, clamped to 1)
        assertEquals(9, gameLoop.shutdownSecondsRemaining)
    }

    @Test
    fun testProcessShutdownTickCountdownToZero() = runBlocking {
        val gameLoop = createTestGameLoop()
        gameLoop.initiateShutdown(3)

        // Tick 1: 3 -> 2
        assertFalse(gameLoop.processShutdownTick())
        assertEquals(2, gameLoop.shutdownSecondsRemaining)

        // Tick 2: 2 -> 1
        assertFalse(gameLoop.processShutdownTick())
        assertEquals(1, gameLoop.shutdownSecondsRemaining)

        // Tick 3: 1 -> 0
        assertFalse(gameLoop.processShutdownTick())
        assertEquals(0, gameLoop.shutdownSecondsRemaining)

        // Tick 4: at 0, returns true (shutdown complete)
        assertTrue(gameLoop.processShutdownTick())
    }

    @Test
    fun testShutdownBroadcastsToConnectedPlayers() = runBlocking {
        val sessionManager = SessionManager()
        val gameLoop = createTestGameLoop(sessionManager)

        val received = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val session = createTestSession(received)
        session.playerName = "TestPlayer"
        session.player = createTestPlayer()
        session.currentRoomId = "test:room"
        sessionManager.addSession("TestPlayer", session)

        gameLoop.initiateShutdown(5)
        gameLoop.processShutdownTick()

        // Check messages sent to the session's outgoing channel
        val messages = received
        val shutdownMsgs = messages.filterIsInstance<ServerMessage.ServerShutdown>()
        assertTrue(shutdownMsgs.isNotEmpty(), "Expected at least one shutdown broadcast")
        val fiveSecMsg = shutdownMsgs.find { it.secondsRemaining == 5 }
        assertTrue(fiveSecMsg != null, "Expected 5-second warning")
    }

    @Test
    fun testShutdownFinalBroadcast() = runBlocking {
        val sessionManager = SessionManager()
        val gameLoop = createTestGameLoop(sessionManager)

        val received = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val session = createTestSession(received)
        session.playerName = "TestPlayer"
        session.player = createTestPlayer()
        session.currentRoomId = "test:room"
        sessionManager.addSession("TestPlayer", session)

        gameLoop.initiateShutdown(0)
        gameLoop.processShutdownTick()

        val messages = received
        val finalMsg = messages.filterIsInstance<ServerMessage.ServerShutdown>()
            .find { it.secondsRemaining == 0 }
        assertTrue(finalMsg != null, "Expected final shutdown broadcast")
        assertTrue(finalMsg.message.contains("NOW"), "Expected final message to mention NOW")
    }

    @Test
    fun testShutdownDoesNotDuplicateWarnings() = runBlocking {
        val sessionManager = SessionManager()
        val gameLoop = createTestGameLoop(sessionManager)

        val received = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val session = createTestSession(received)
        session.playerName = "TestPlayer"
        session.player = createTestPlayer()
        session.currentRoomId = "test:room"
        sessionManager.addSession("TestPlayer", session)

        gameLoop.initiateShutdown(5)
        gameLoop.processShutdownTick() // 5 -> 4 (broadcasts 5s warning)
        gameLoop.processShutdownTick() // 4 -> 3

        val messages = received
        val fiveSecWarnings = messages.filterIsInstance<ServerMessage.ServerShutdown>()
            .filter { it.secondsRemaining == 5 }
        assertEquals(1, fiveSecWarnings.size, "Should only broadcast 5s warning once")
    }

    // --- Helpers ---

    private fun createTestGameLoop(sessionManager: SessionManager = SessionManager()): GameLoop {
        val worldGraph = WorldGraph()
        val itemCatalog = ItemCatalog(emptyList())
        val skillCatalog = SkillCatalog(emptyList())
        val npcManager = NpcManager(worldGraph, emptyMap(), emptyMap())
        return GameLoop(
            sessionManager = sessionManager,
            npcManager = npcManager,
            combatManager = CombatManager(
                npcManager, sessionManager, worldGraph,
                EquipmentService(InventoryRepository(itemCatalog), itemCatalog),
                skillCatalog, null, SpellCatalog(emptyList()), null
            ),
            worldGraph = worldGraph,
            lootService = LootService(itemCatalog),
            lootTableCatalog = LootTableCatalog(emptyMap()),
            roomItemManager = RoomItemManager(),
            playerRepository = PlayerRepository(),
            skillCatalog = skillCatalog,
            classCatalog = ClassCatalog(emptyList()),
            itemCatalog = itemCatalog,
            inventoryRepository = InventoryRepository(itemCatalog),
            coinRepository = CoinRepository()
        )
    }

    private fun createTestSession(received: MutableList<com.neomud.shared.protocol.ServerMessage> = mutableListOf()): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) { received.add(message) }
            override suspend fun close(reason: String) {}
        })
    }

    private fun createTestPlayer(): Player = Player(
        name = "TestPlayer",
        characterClass = "WARRIOR",
        stats = Stats(strength = 30, agility = 20, intellect = 15, willpower = 15, health = 25, charm = 10),
        currentHp = 50,
        maxHp = 50,
        currentMp = 0,
        maxMp = 0,
        level = 1,
        currentRoomId = "test:room"
    )
}
