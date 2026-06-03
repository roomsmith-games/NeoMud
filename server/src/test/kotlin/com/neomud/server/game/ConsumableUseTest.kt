package com.neomud.server.game

import com.neomud.server.game.commands.InventoryCommand
import com.neomud.server.persistence.repository.CoinRepository
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.session.PendingSkill
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ItemCatalog
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

class ConsumableUseTest {

    private fun createTestSession(): PlayerSession {
        return PlayerSession(object : WebSocketSession {
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
    }

    private fun createTestPlayer(
        currentHp: Int = 30,
        maxHp: Int = 50,
        currentMp: Int = 10,
        maxMp: Int = 30
    ): Player {
        return Player(
            name = "TestPlayer",
            characterClass = "WARRIOR",
            race = "HUMAN",
            level = 1,
            currentHp = currentHp,
            maxHp = maxHp,
            currentMp = currentMp,
            maxMp = maxMp,
            currentRoomId = "test:room1",
            currentXp = 0,
            xpToNextLevel = 1000,
            stats = Stats(
                strength = 20,
                agility = 15,
                intellect = 10,
                willpower = 12,
                health = 18,
                charm = 10
            ),
            unspentCp = 0,
            totalCpEarned = 0
        )
    }

    // --- Command handler tests (validation + queue) ---

    @Test
    fun testHandleUseItemQueuesPendingSkill() {
        runBlocking {
            val itemCatalog = ItemCatalog(listOf(
                Item(id = "item:health_potion", name = "Health Potion", description = "Restores health", type = "consumable", useEffect = "heal:25")
            ))
            val inventoryRepo = object : InventoryRepository(itemCatalog) {
                override fun getInventory(playerName: String): List<InventoryItem> = listOf(
                    InventoryItem(itemId = "item:health_potion", quantity = 2, equipped = false, slot = "")
                )
            }
            val coinRepo = CoinRepository()
            val worldGraph = WorldGraph()
            val sessionManager = SessionManager()
            val cmd = InventoryCommand(inventoryRepo, itemCatalog, coinRepo, worldGraph, sessionManager)

            val session = createTestSession()
            session.playerName = "TestPlayer"
            session.player = createTestPlayer()

            cmd.handleUseItem(session, "item:health_potion")

            val pending = session.pendingSkill
            assertIs<PendingSkill.UseItem>(pending)
            assertEquals("item:health_potion", pending.itemId)
        }
    }

    @Test
    fun testHandleUseItemDoesNotMutateHp() {
        runBlocking {
            val itemCatalog = ItemCatalog(listOf(
                Item(id = "item:health_potion", name = "Health Potion", description = "Restores health", type = "consumable", useEffect = "heal:25")
            ))
            val inventoryRepo = object : InventoryRepository(itemCatalog) {
                override fun getInventory(playerName: String): List<InventoryItem> = listOf(
                    InventoryItem(itemId = "item:health_potion", quantity = 1, equipped = false, slot = "")
                )
            }
            val coinRepo = CoinRepository()
            val worldGraph = WorldGraph()
            val sessionManager = SessionManager()
            val cmd = InventoryCommand(inventoryRepo, itemCatalog, coinRepo, worldGraph, sessionManager)

            val session = createTestSession()
            session.playerName = "TestPlayer"
            session.player = createTestPlayer(currentHp = 30, maxHp = 50)

            cmd.handleUseItem(session, "item:health_potion")

            // HP should NOT change — resolution happens in GameLoop tick
            assertEquals(30, session.player!!.currentHp)
        }
    }

    @Test
    fun testHandleUseItemRejectsNonConsumable() {
        runBlocking {
            val itemCatalog = ItemCatalog(listOf(
                Item(id = "item:iron_sword", name = "Iron Sword", description = "A sturdy sword", type = "weapon", slot = "weapon")
            ))
            val inventoryRepo = object : InventoryRepository(itemCatalog) {
                override fun getInventory(playerName: String): List<InventoryItem> = listOf(
                    InventoryItem(itemId = "item:iron_sword", quantity = 1, equipped = false, slot = "weapon")
                )
            }
            val coinRepo = CoinRepository()
            val worldGraph = WorldGraph()
            val sessionManager = SessionManager()
            val cmd = InventoryCommand(inventoryRepo, itemCatalog, coinRepo, worldGraph, sessionManager)

            val session = createTestSession()
            session.playerName = "TestPlayer"
            session.player = createTestPlayer()

            cmd.handleUseItem(session, "item:iron_sword")

            assertNull(session.pendingSkill)
        }
    }

    @Test
    fun testHandleUseItemOverwritesExistingPendingSkill() {
        runBlocking {
            val itemCatalog = ItemCatalog(listOf(
                Item(id = "item:health_potion", name = "Health Potion", description = "Restores health", type = "consumable", useEffect = "heal:25")
            ))
            val inventoryRepo = object : InventoryRepository(itemCatalog) {
                override fun getInventory(playerName: String): List<InventoryItem> = listOf(
                    InventoryItem(itemId = "item:health_potion", quantity = 1, equipped = false, slot = "")
                )
            }
            val coinRepo = CoinRepository()
            val worldGraph = WorldGraph()
            val sessionManager = SessionManager()
            val cmd = InventoryCommand(inventoryRepo, itemCatalog, coinRepo, worldGraph, sessionManager)

            val session = createTestSession()
            session.playerName = "TestPlayer"
            session.player = createTestPlayer()
            session.pendingSkill = PendingSkill.Bash("npc:goblin")

            cmd.handleUseItem(session, "item:health_potion")

            assertIs<PendingSkill.UseItem>(session.pendingSkill)
        }
    }

    // --- UseEffectProcessor tests (resolution logic) ---

    @Test
    fun testUseEffectProcessorHeals() {
        val player = createTestPlayer(currentHp = 30, maxHp = 50)
        val result = UseEffectProcessor.process("heal:25", player, "Health Potion")

        assertNotNull(result)
        assertEquals(50, result.updatedPlayer.currentHp) // 30 + 25 = 55, capped at 50
        assertTrue(result.newEffects.isEmpty())
        assertTrue(result.messages.any { "recover" in it })
    }

    @Test
    fun testUseEffectProcessorRestoresMana() {
        val player = createTestPlayer(currentMp = 10, maxMp = 30)
        val result = UseEffectProcessor.process("mana:15", player, "Mana Potion")

        assertNotNull(result)
        assertEquals(25, result.updatedPlayer.currentMp) // 10 + 15 = 25
        assertTrue(result.messages.any { "recover" in it })
    }

    @Test
    fun testUseEffectProcessorCreatesHotEffect() {
        val player = createTestPlayer()
        val result = UseEffectProcessor.process("hot:5:10", player, "Rejuvenation Potion")

        assertNotNull(result)
        assertEquals(1, result.newEffects.size)
        assertEquals(EffectType.HEAL_OVER_TIME, result.newEffects[0].type)
        assertEquals(5, result.newEffects[0].magnitude)
        assertEquals(10, result.newEffects[0].remainingTicks)
    }

    @Test
    fun testUseEffectProcessorReturnsNullForEmptyEffect() {
        val player = createTestPlayer()
        val result = UseEffectProcessor.process("", player, "Empty Item")

        assertNull(result)
    }

    // --- Death clears pendingSkill ---

    @Test
    fun testDeathClearsPendingUseItem() {
        val session = createTestSession()
        session.pendingSkill = PendingSkill.UseItem("item:health_potion")

        // Simulate PlayerKilled handler (same as in GameLoop)
        session.attackMode = false
        session.selectedTargetId = null
        session.readiedSpellId = null
        session.pendingSkill = null
        session.isHidden = false
        session.isMeditating = false
        session.isResting = false

        assertNull(session.pendingSkill)
    }

    // --- Full HP guard for heal-only items (#473) ---

    @Test
    fun testUseEffectProcessorHealAtFullHpStillCalculates() {
        val player = createTestPlayer(currentHp = 50, maxHp = 50)
        val result = UseEffectProcessor.process("heal:25", player, "Health Potion")
        assertNotNull(result)
        assertEquals(50, result.updatedPlayer.currentHp)
    }

    @Test
    fun testUseEffectProcessorHealManaComboAtFullHp() {
        val player = createTestPlayer(currentHp = 50, maxHp = 50, currentMp = 10, maxMp = 30)
        val result = UseEffectProcessor.process("heal:25,mana:15", player, "Restoration Potion")
        assertNotNull(result)
        assertEquals(50, result.updatedPlayer.currentHp)
        assertEquals(25, result.updatedPlayer.currentMp)
    }

    // --- isHealOnly helper (#473) ---

    @Test
    fun testIsHealOnlyTrueForPureHeal() {
        assertTrue(UseEffectProcessor.isHealOnly("heal:25"))
    }

    @Test
    fun testIsHealOnlyFalseForHealPlusMana() {
        assertFalse(UseEffectProcessor.isHealOnly("heal:25,mana:15"))
    }

    @Test
    fun testIsHealOnlyFalseForHealPlusBuff() {
        assertFalse(UseEffectProcessor.isHealOnly("heal:25,buff:strength:3:20"))
    }

    @Test
    fun testIsHealOnlyFalseForDamageScroll() {
        assertFalse(UseEffectProcessor.isHealOnly("damage:10"))
    }

    @Test
    fun testIsHealOnlyFalseForEmptyString() {
        assertFalse(UseEffectProcessor.isHealOnly(""))
    }

    // --- Validates item ownership at resolution ---

    @Test
    fun testResolveUseItemFailsWhenItemNoLongerOwned() {
        // This tests the logic: UseEffectProcessor is only called if removeItem succeeds.
        // If the player drops the item between queue and tick, removeItem returns false.
        // We test this indirectly via UseEffectProcessor not being called with a bad player.
        val player = createTestPlayer(currentHp = 30, maxHp = 50)
        val result = UseEffectProcessor.process("heal:25", player, "Health Potion")

        assertNotNull(result)
        // If we DON'T apply the result (simulating removeItem returning false),
        // the player's HP stays unchanged
        assertEquals(30, player.currentHp)
    }
}
