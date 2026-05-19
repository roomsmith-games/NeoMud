package com.neomud.server.game.party

import com.neomud.server.game.GameConfig
import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.shared.model.GroundItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LootDistributionTest {

    private fun service() = PartyService()

    private fun formParty(ps: PartyService, leader: String, member: String): Party {
        ps.createInvite(leader, member, 0)
        val result = ps.acceptInvite(member, leader, 0)
        assertTrue(result is PartyService.AcceptResult.Formed)
        return result.party
    }

    // ─── Round-robin ────────────────────────────────────────

    @Test
    fun `round-robin cycles through all members`() {
        val ps = service()
        val party = formParty(ps, "Alice", "Bob")
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)

        val recipients = (1..6).map { ps.nextLootRecipient(party.id) }
        assertEquals(listOf("Alice", "Bob", "Charlie", "Alice", "Bob", "Charlie"), recipients)
    }

    @Test
    fun `round-robin with two members`() {
        val ps = service()
        val party = formParty(ps, "Alice", "Bob")

        val first = ps.nextLootRecipient(party.id)
        val second = ps.nextLootRecipient(party.id)
        val third = ps.nextLootRecipient(party.id)
        assertEquals("Alice", first)
        assertEquals("Bob", second)
        assertEquals("Alice", third)
    }

    @Test
    fun `round-robin returns null for invalid party`() {
        val ps = service()
        val result = ps.nextLootRecipient("nonexistent")
        assertEquals(null, result)
    }

    // ─── Priority window ────────────────────────────────────

    @Test
    fun `assigned item blocks other players during priority window`() {
        val rim = RoomItemManager()
        rim.addItemsWithAssignment(
            "room1",
            listOf(GroundItem("item:sword", 1)),
            "Alice",
            priorityExpiresTick = 10
        )

        assertTrue(rim.isItemAssignedToOther("room1", "item:sword", "Bob", currentTick = 5))
        assertFalse(rim.isItemAssignedToOther("room1", "item:sword", "Alice", currentTick = 5))
    }

    @Test
    fun `assigned item becomes free after priority expires`() {
        val rim = RoomItemManager()
        rim.addItemsWithAssignment(
            "room1",
            listOf(GroundItem("item:sword", 1)),
            "Alice",
            priorityExpiresTick = 10
        )

        assertFalse(rim.isItemAssignedToOther("room1", "item:sword", "Bob", currentTick = 11))
    }

    @Test
    fun `assigned item allows assignee to pick up immediately`() {
        val rim = RoomItemManager()
        rim.addItemsWithAssignment(
            "room1",
            listOf(GroundItem("item:sword", 1)),
            "Alice",
            priorityExpiresTick = 10
        )

        assertFalse(rim.isItemAssignedToOther("room1", "item:sword", "Alice", currentTick = 1))
    }

    @Test
    fun `getAssignedTo returns correct assignee`() {
        val rim = RoomItemManager()
        rim.addItemsWithAssignment(
            "room1",
            listOf(GroundItem("item:sword", 1)),
            "Alice",
            priorityExpiresTick = 10
        )

        assertEquals("Alice", rim.getAssignedTo("room1", "item:sword"))
    }

    @Test
    fun `unassigned items have no assignment`() {
        val rim = RoomItemManager()
        rim.addItems("room1", listOf(GroundItem("item:sword", 1)))

        assertEquals(null, rim.getAssignedTo("room1", "item:sword"))
        assertFalse(rim.isItemAssignedToOther("room1", "item:sword", "Bob", currentTick = 0))
    }

    // ─── Solo play unaffected ───────────────────────────────

    @Test
    fun `solo loot has no assignment`() {
        val rim = RoomItemManager()
        rim.addItems("room1", listOf(GroundItem("item:potion", 3)))

        val items = rim.getGroundItems("room1")
        assertEquals(1, items.size)
        assertEquals("item:potion", items[0].itemId)
        assertFalse(rim.isItemAssignedToOther("room1", "item:potion", "anyone", currentTick = 0))
    }

    // ─── Priority at boundary ───────────────────────────────

    @Test
    fun `priority window expires at exact tick`() {
        val rim = RoomItemManager()
        rim.addItemsWithAssignment(
            "room1",
            listOf(GroundItem("item:shield", 1)),
            "Alice",
            priorityExpiresTick = 10
        )

        // Still blocked one tick before expiry
        assertTrue(rim.isItemAssignedToOther("room1", "item:shield", "Bob", currentTick = 9))
        // At exact expiry tick, priority is over (> check, not >=)
        assertFalse(rim.isItemAssignedToOther("room1", "item:shield", "Bob", currentTick = 10))
    }
}
