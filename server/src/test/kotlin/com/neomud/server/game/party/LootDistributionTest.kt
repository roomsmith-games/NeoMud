package com.neomud.server.game.party

import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.shared.model.Coins
import com.neomud.shared.model.GroundItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LootDistributionTest {

    // ─── All loot drops to ground (no assignments) ─────────

    @Test
    fun `party loot drops to ground with no assignment`() {
        val rim = RoomItemManager()
        rim.addItems("room1", listOf(GroundItem("item:sword", 1), GroundItem("item:potion", 2)))

        val items = rim.getGroundItems("room1")
        assertEquals(2, items.size)
        assertFalse(rim.isItemAssignedToOther("room1", "item:sword", "anyone", currentTick = 0))
        assertFalse(rim.isItemAssignedToOther("room1", "item:potion", "anyone", currentTick = 0))
    }

    @Test
    fun `any player can pick up party loot immediately`() {
        val rim = RoomItemManager()
        rim.addItems("room1", listOf(GroundItem("item:sword", 1)))

        assertFalse(rim.isItemAssignedToOther("room1", "item:sword", "Alice", currentTick = 0))
        assertFalse(rim.isItemAssignedToOther("room1", "item:sword", "Bob", currentTick = 0))
        assertFalse(rim.isItemAssignedToOther("room1", "item:sword", "Charlie", currentTick = 0))
    }

    @Test
    fun `coins drop to ground in party context`() {
        val rim = RoomItemManager()
        val coins = Coins(gold = 1, silver = 2, copper = 3)
        rim.addCoins("room1", coins)

        val groundCoins = rim.getGroundCoins("room1")
        assertEquals(1, groundCoins.gold)
        assertEquals(2, groundCoins.silver)
        assertEquals(3, groundCoins.copper)
    }

    @Test
    fun `solo loot drops to ground identically`() {
        val rim = RoomItemManager()
        rim.addItems("room1", listOf(GroundItem("item:potion", 3)))

        val items = rim.getGroundItems("room1")
        assertEquals(1, items.size)
        assertEquals("item:potion", items[0].itemId)
        assertFalse(rim.isItemAssignedToOther("room1", "item:potion", "anyone", currentTick = 0))
    }
}
