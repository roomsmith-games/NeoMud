package com.neomud.server.game.commands

import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.server.persistence.repository.CoinRepository
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ItemCatalog
import com.neomud.shared.model.*
import com.neomud.server.session.TransportSession
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DropCommandTest {

    @Test
    fun testDropItemAddsToGround() {
        runBlocking {
            val roomItemManager = RoomItemManager()
            val inventoryRepo = FakeInventoryRepository(
                inventory = mutableListOf(InventoryItem("item:potion", 5, false, "")),
                equipped = emptyMap()
            )
            val command = createDropCommand(roomItemManager, inventoryRepo)
            val session = createTestSession()
            session.player = createTestPlayer()
            session.playerName = "TestPlayer"
            session.currentRoomId = "test:room1"

            command.execute(session, "item:potion", 2)

            val ground = roomItemManager.getGroundItems("test:room1")
            assertEquals(1, ground.size)
            assertEquals("item:potion", ground[0].itemId)
            assertEquals(2, ground[0].quantity)
            assertTrue(inventoryRepo.removedItems.contains("item:potion" to 2))
        }
    }

    @Test
    fun testDropClampsToAvailableQuantity() {
        runBlocking {
            val roomItemManager = RoomItemManager()
            val inventoryRepo = FakeInventoryRepository(
                inventory = mutableListOf(InventoryItem("item:potion", 3, false, "")),
                equipped = emptyMap()
            )
            val command = createDropCommand(roomItemManager, inventoryRepo)
            val session = createTestSession()
            session.player = createTestPlayer()
            session.playerName = "TestPlayer"
            session.currentRoomId = "test:room1"

            command.execute(session, "item:potion", 10)

            val ground = roomItemManager.getGroundItems("test:room1")
            assertEquals(1, ground.size)
            assertEquals(3, ground[0].quantity)
        }
    }

    @Test
    fun testDropEquippedItemBlocked() {
        runBlocking {
            val roomItemManager = RoomItemManager()
            val inventoryRepo = FakeInventoryRepository(
                inventory = mutableListOf(InventoryItem("item:sword", 1, true, "weapon")),
                equipped = mapOf("weapon" to "item:sword")
            )
            val command = createDropCommand(roomItemManager, inventoryRepo)
            val session = createTestSession()
            session.player = createTestPlayer()
            session.playerName = "TestPlayer"
            session.currentRoomId = "test:room1"

            command.execute(session, "item:sword", 1)

            val ground = roomItemManager.getGroundItems("test:room1")
            assertTrue(ground.isEmpty(), "Equipped items should not be droppable")
        }
    }

    @Test
    fun testDropItemNotInInventory() {
        runBlocking {
            val roomItemManager = RoomItemManager()
            val inventoryRepo = FakeInventoryRepository(
                inventory = mutableListOf(),
                equipped = emptyMap()
            )
            val command = createDropCommand(roomItemManager, inventoryRepo)
            val session = createTestSession()
            session.player = createTestPlayer()
            session.playerName = "TestPlayer"
            session.currentRoomId = "test:room1"

            command.execute(session, "item:nonexistent", 1)

            val ground = roomItemManager.getGroundItems("test:room1")
            assertTrue(ground.isEmpty())
        }
    }

    private fun createDropCommand(
        roomItemManager: RoomItemManager,
        inventoryRepo: FakeInventoryRepository
    ): DropCommand {
        val itemCatalog = ItemCatalog(listOf(
            Item(id = "item:potion", name = "Health Potion", description = "Heals", type = "consumable", slot = "", stackable = true),
            Item(id = "item:sword", name = "Iron Sword", description = "A sword", type = "weapon", slot = "weapon")
        ))
        val sessionManager = SessionManager()
        val coinRepo = FakeCoinRepository()
        return DropCommand(roomItemManager, inventoryRepo, coinRepo, itemCatalog, sessionManager)
    }

    private fun createTestSession(): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) {}
            override suspend fun close(reason: String) {}
        })
    }

    private fun createTestPlayer(): Player {
        return Player(
            name = "TestPlayer",
            characterClass = "WARRIOR",
            race = "HUMAN",
            level = 1,
            currentHp = 50,
            maxHp = 50,
            currentMp = 20,
            maxMp = 20,
            currentRoomId = "test:room1",
            currentXp = 0,
            xpToNextLevel = 1000,
            stats = Stats(strength = 20, agility = 15, intellect = 10, willpower = 12, health = 18, charm = 10),
            unspentCp = 0,
            totalCpEarned = 0
        )
    }

    /** Fake InventoryRepository that avoids DB access. */
    class FakeInventoryRepository(
        private val inventory: MutableList<InventoryItem>,
        private val equipped: Map<String, String>
    ) : InventoryRepository(ItemCatalog(emptyList())) {
        val removedItems = mutableListOf<Pair<String, Int>>()

        override fun getInventory(playerName: String): List<InventoryItem> = inventory.toList()

        override fun getEquippedItems(playerName: String): Map<String, String> = equipped

        override fun removeItem(playerName: String, itemId: String, quantity: Int): Boolean {
            val item = inventory.find { it.itemId == itemId && !it.equipped } ?: return false
            val actual = minOf(quantity, item.quantity)
            removedItems.add(itemId to actual)
            val remaining = item.quantity - actual
            inventory.remove(item)
            if (remaining > 0) {
                inventory.add(item.copy(quantity = remaining))
            }
            return true
        }
    }

    /** Fake CoinRepository that avoids DB access. */
    class FakeCoinRepository : CoinRepository() {
        override fun getCoins(playerName: String): Coins = Coins()
    }
}
