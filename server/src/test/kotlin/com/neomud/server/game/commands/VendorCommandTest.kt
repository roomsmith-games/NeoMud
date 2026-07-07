package com.neomud.server.game.commands

import com.neomud.server.game.npc.NpcManager
import com.neomud.server.persistence.DatabaseFactory
import com.neomud.server.persistence.repository.CoinRepository
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.ItemCatalog
import com.neomud.server.world.NpcData
import com.neomud.server.world.SkillCatalog
import com.neomud.server.world.WorldGraph
import com.neomud.server.session.TransportSession
import com.neomud.shared.model.CharacterClassDef
import com.neomud.shared.model.Coins
import com.neomud.shared.model.Item
import com.neomud.shared.model.Player
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.runBlocking
import org.junit.Before
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VendorCommandTest {

    private val testDbFile = File.createTempFile("vendorcmdtest", ".db").also { it.deleteOnExit() }
    private val testRoomId = "town:vendor_room"
    private val testCharacterName = "VendorTester"

    private val testItem = Item(
        id = "item:health_potion",
        name = "Health Potion",
        description = "Restores health.",
        type = "consumable",
        value = 100,
        stackable = true,
        maxStack = 99
    )

    private val warriorClass = CharacterClassDef(
        id = "WARRIOR",
        name = "Warrior",
        description = "Sword and shield",
        minimumStats = Stats(strength = 15, agility = 10, intellect = 5, willpower = 5, health = 15, charm = 5),
        skills = emptyList(),
        hpPerLevelMin = 6,
        hpPerLevelMax = 6,
        mpPerLevelMin = 0,
        mpPerLevelMax = 0,
        xpModifier = 1.0
    )

    private val classCatalog = ClassCatalog(listOf(warriorClass))
    private val itemCatalog = ItemCatalog(listOf(testItem))
    private val skillCatalog = SkillCatalog(emptyList())
    private val sessionManager = SessionManager()

    @Before
    fun setUp() {
        DatabaseFactory.init("jdbc:sqlite:${testDbFile.absolutePath}")
    }

    private fun seedPlayer(): PlayerRepository {
        val repo = PlayerRepository()
        repo.createPlayer(
            username = "tester",
            password = "pw",
            characterName = testCharacterName,
            characterClass = "WARRIOR",
            race = "HUMAN",
            allocatedStats = Stats(strength = 15, agility = 10, intellect = 5, willpower = 5, health = 15, charm = 5),
            spawnRoomId = testRoomId,
            classCatalog = classCatalog,
            raceCatalog = null
        )
        return repo
    }

    private fun newSession(player: Player): Pair<PlayerSession, MutableList<com.neomud.shared.protocol.ServerMessage>> {
        val received = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val session = PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) { received.add(message) }
            override suspend fun close(reason: String) {}
        })
        session.player = player
        session.playerName = player.name
        session.currentRoomId = testRoomId
        return session to received
    }

    private fun buildNpcManager(): NpcManager {
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        npcManager.loadNpcs(
            listOf(
                NpcData(
                    id = "npc:test_vendor",
                    name = "Test Vendor",
                    description = "Sells and buys goods.",
                    startRoomId = testRoomId,
                    behaviorType = "vendor",
                    vendorItems = listOf("item:health_potion")
                ) to "town"
            )
        )
        return npcManager
    }

    private fun buildCommand(npcManager: NpcManager, inventoryRepo: InventoryRepository, coinRepo: CoinRepository): VendorCommand {
        val inventoryCommand = InventoryCommand(inventoryRepo, itemCatalog, coinRepo, WorldGraph(), sessionManager)
        return VendorCommand(npcManager, itemCatalog, inventoryRepo, coinRepo, inventoryCommand, sessionManager, skillCatalog)
    }

    private fun testPlayer(): Player = Player(
        name = testCharacterName,
        characterClass = "WARRIOR",
        race = "HUMAN",
        level = 1,
        currentHp = 50,
        maxHp = 50,
        currentMp = 0,
        maxMp = 0,
        currentRoomId = testRoomId,
        stats = Stats(strength = 15, agility = 10, intellect = 5, willpower = 5, health = 15, charm = 5)
    )


    @Test
    fun `buy at stack limit returns specific error message`() = runBlocking {
        seedPlayer()
        val inventoryRepo = InventoryRepository(itemCatalog)
        val coinRepo = CoinRepository()
        coinRepo.addCoins(testCharacterName, Coins(silver = 10))
        val npcManager = buildNpcManager()
        val command = buildCommand(npcManager, inventoryRepo, coinRepo)

        inventoryRepo.addItem(testCharacterName, testItem.id, testItem.maxStack)

        val (session, outgoing) = newSession(testPlayer())
        command.handleBuy(session, testItem.id, quantity = 1)

        val errors = outgoing.filterIsInstance<ServerMessage.Error>()
        assertEquals(1, errors.size, "Should receive one Error message")
        assertTrue(errors[0].message.contains("maximum amount"), "Error should mention stack limit")
        assertTrue(errors[0].message.contains(testItem.name), "Error should name the item")
        assertTrue(errors[0].message.contains(testItem.maxStack.toString()), "Error should include max stack count")

        val qty = inventoryRepo.getInventory(testCharacterName).find { it.itemId == testItem.id }?.quantity
        assertEquals(testItem.maxStack, qty, "Inventory should be unchanged")

        val coins = coinRepo.getCoins(testCharacterName)
        assertEquals(Coins(silver = 10).totalCopper(), coins.totalCopper(), "Coins should be refunded")
    }

    @Test
    fun `sell single item from stack of 5`() = runBlocking {
        seedPlayer()
        val inventoryRepo = InventoryRepository(itemCatalog)
        val coinRepo = CoinRepository()
        val npcManager = buildNpcManager()
        val command = buildCommand(npcManager, inventoryRepo, coinRepo)

        inventoryRepo.addItem(testCharacterName, testItem.id, 5)
        val initialCoins = coinRepo.getCoins(testCharacterName)

        val (session, outgoing) = newSession(testPlayer())
        command.handleSell(session, testItem.id, quantity = 1)

        val inventory = inventoryRepo.getInventory(testCharacterName)
        val remaining = inventory.filter { it.itemId == testItem.id }.sumOf { it.quantity }
        assertEquals(4, remaining, "Should have 4 remaining after selling 1")

        val finalCoins = coinRepo.getCoins(testCharacterName)
        assertTrue(finalCoins.totalCopper() > initialCoins.totalCopper(), "Player should have gained coins")

        val messages = outgoing
        val sellResult = messages.filterIsInstance<ServerMessage.SellResult>().firstOrNull()
        assertTrue(sellResult != null, "Should receive SellResult")
        assertTrue(sellResult.success, "Sell should succeed")
        assertTrue(sellResult.message.contains("Health Potion"), "Message should mention item name")
    }

    @Test
    fun `sell entire stack`() = runBlocking {
        seedPlayer()
        val inventoryRepo = InventoryRepository(itemCatalog)
        val coinRepo = CoinRepository()
        val npcManager = buildNpcManager()
        val command = buildCommand(npcManager, inventoryRepo, coinRepo)

        inventoryRepo.addItem(testCharacterName, testItem.id, 5)

        val (session, outgoing) = newSession(testPlayer())
        command.handleSell(session, testItem.id, quantity = 5)

        val inventory = inventoryRepo.getInventory(testCharacterName)
        val remaining = inventory.filter { it.itemId == testItem.id }.sumOf { it.quantity }
        assertEquals(0, remaining, "Should have 0 remaining after selling all 5")

        val messages = outgoing
        val sellResult = messages.filterIsInstance<ServerMessage.SellResult>().firstOrNull()
        assertTrue(sellResult != null, "Should receive SellResult")
        assertTrue(sellResult.message.contains("x5"), "Message should mention quantity")
    }

    @Test
    fun `sell quantity clamped to available`() = runBlocking {
        seedPlayer()
        val inventoryRepo = InventoryRepository(itemCatalog)
        val coinRepo = CoinRepository()
        val npcManager = buildNpcManager()
        val command = buildCommand(npcManager, inventoryRepo, coinRepo)

        inventoryRepo.addItem(testCharacterName, testItem.id, 3)

        val (session, outgoing) = newSession(testPlayer())
        command.handleSell(session, testItem.id, quantity = 10)

        val inventory = inventoryRepo.getInventory(testCharacterName)
        val remaining = inventory.filter { it.itemId == testItem.id }.sumOf { it.quantity }
        assertEquals(0, remaining, "Should sell all 3 even though 10 requested")

        val messages = outgoing
        val sellResult = messages.filterIsInstance<ServerMessage.SellResult>().firstOrNull()
        assertTrue(sellResult != null, "Should receive SellResult")
        assertTrue(sellResult.success, "Sell should succeed")
        assertTrue(sellResult.message.contains("x3"), "Message should reflect actual quantity sold (3)")
    }
}
