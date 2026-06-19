package com.neomud.server.telnet

import com.neomud.shared.model.GroundItem
import com.neomud.shared.model.InventoryItem
import com.neomud.shared.model.Item
import com.neomud.shared.model.Npc
import com.neomud.shared.model.RecipeInfo
import com.neomud.shared.model.Recipe
import com.neomud.shared.model.RoomInteractable
import com.neomud.shared.model.Coins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NameResolverTest {

    // ---- Fixtures ----

    private fun npc(id: String, name: String) = Npc(
        id = id, name = name, description = "", currentRoomId = "test:room",
        behaviorType = "HOSTILE"
    )

    private fun item(id: String, name: String) = Item(
        id = id, name = name, description = "", type = "weapon", slot = "weapon"
    )

    private fun groundItem(itemId: String, qty: Int = 1) = GroundItem(itemId, qty)
    private fun invItem(itemId: String) = InventoryItem(itemId)

    private fun interactable(id: String, label: String) = RoomInteractable(
        id = id, label = label, description = "", actionType = "EXIT_OPEN"
    )

    private fun recipe(id: String, name: String) = RecipeInfo(
        recipe = Recipe(
            id = id, name = name, description = "", category = "general",
            materials = emptyList(), cost = Coins(), outputItemId = "item:output"
        ),
        canCraft = true,
        materialStatus = emptyList()
    )

    // ---- NPC resolution ----

    @Test fun npcExactMatch() {
        val npcs = listOf(npc("npc:rat", "Rat"), npc("npc:spider", "Spider"))
        val result = NameResolver.resolveNpc("rat", npcs)
        assertIs<NameResolver.Resolve.Found<Npc>>(result)
        assertEquals("npc:rat", result.entity.id)
    }

    @Test fun npcCaseInsensitive() {
        val npcs = listOf(npc("npc:rat", "Rat"))
        val result = NameResolver.resolveNpc("RAT", npcs)
        assertIs<NameResolver.Resolve.Found<Npc>>(result)
    }

    @Test fun npcPrefixMatch() {
        val npcs = listOf(npc("npc:giant_rat", "Giant Rat"))
        val result = NameResolver.resolveNpc("giant", npcs)
        assertIs<NameResolver.Resolve.Found<Npc>>(result)
    }

    @Test fun npcSubstringMatch() {
        val npcs = listOf(npc("npc:giant_rat", "Giant Rat"))
        val result = NameResolver.resolveNpc("ant", npcs)
        assertIs<NameResolver.Resolve.Found<Npc>>(result)
    }

    @Test fun npcNotFound() {
        val npcs = listOf(npc("npc:rat", "Rat"))
        val result = NameResolver.resolveNpc("dragon", npcs)
        assertIs<NameResolver.Resolve.NotFound>(result)
    }

    @Test fun npcAmbiguous() {
        val npcs = listOf(npc("npc:rat1", "Rat"), npc("npc:rat2", "Rat"))
        val result = NameResolver.resolveNpc("rat", npcs)
        assertIs<NameResolver.Resolve.Ambiguous<Npc>>(result)
        assertEquals(2, result.candidates.size)
    }

    @Test fun npcNumberedIndex() {
        val npcs = listOf(npc("npc:rat1", "Rat"), npc("npc:rat2", "Rat"), npc("npc:rat3", "Rat"))
        val result = NameResolver.resolveNpc("2.rat", npcs)
        assertIs<NameResolver.Resolve.Found<Npc>>(result)
        assertEquals("npc:rat2", result.entity.id)
    }

    @Test fun npcNumberedIndexOutOfRange() {
        val npcs = listOf(npc("npc:rat", "Rat"))
        val result = NameResolver.resolveNpc("5.rat", npcs)
        assertIs<NameResolver.Resolve.NotFound>(result)
    }

    @Test fun npcNumberedIndexFirst() {
        val npcs = listOf(npc("npc:rat1", "Rat"), npc("npc:rat2", "Rat"))
        val result = NameResolver.resolveNpc("1.rat", npcs)
        assertIs<NameResolver.Resolve.Found<Npc>>(result)
        assertEquals("npc:rat1", result.entity.id)
    }

    @Test fun npcEmptyQuery() {
        val npcs = listOf(npc("npc:rat", "Rat"))
        assertIs<NameResolver.Resolve.NotFound>(NameResolver.resolveNpc("", npcs))
    }

    // ---- Ground item resolution ----

    @Test fun groundItemExact() {
        val catalog = mapOf("item:sword" to item("item:sword", "Iron Sword"))
        val items = listOf(groundItem("item:sword"))
        val result = NameResolver.resolveGroundItem("Iron Sword", items, catalog)
        assertIs<NameResolver.Resolve.Found<GroundItem>>(result)
    }

    @Test fun groundItemPrefix() {
        val catalog = mapOf("item:sword" to item("item:sword", "Iron Sword"))
        val items = listOf(groundItem("item:sword"))
        val result = NameResolver.resolveGroundItem("iron", items, catalog)
        assertIs<NameResolver.Resolve.Found<GroundItem>>(result)
    }

    @Test fun groundItemFallsBackToItemId() {
        val catalog = emptyMap<String, Item>()
        val items = listOf(groundItem("item:gold_ring"))
        val result = NameResolver.resolveGroundItem("item:gold_ring", items, catalog)
        assertIs<NameResolver.Resolve.Found<GroundItem>>(result)
    }

    @Test fun groundItemNotFound() {
        val catalog = mapOf("item:sword" to item("item:sword", "Iron Sword"))
        val items = listOf(groundItem("item:sword"))
        val result = NameResolver.resolveGroundItem("shield", items, catalog)
        assertIs<NameResolver.Resolve.NotFound>(result)
    }

    // ---- Inventory item resolution ----

    @Test fun inventoryItemExact() {
        val catalog = mapOf("item:potion" to item("item:potion", "Health Potion"))
        val inv = listOf(invItem("item:potion"))
        val result = NameResolver.resolveInventoryItem("health potion", inv, catalog)
        assertIs<NameResolver.Resolve.Found<InventoryItem>>(result)
    }

    @Test fun inventoryItemAmbiguous() {
        val catalog = mapOf(
            "item:potion1" to item("item:potion1", "Health Potion"),
            "item:potion2" to item("item:potion2", "Health Potion")
        )
        val inv = listOf(invItem("item:potion1"), invItem("item:potion2"))
        val result = NameResolver.resolveInventoryItem("health", inv, catalog)
        assertIs<NameResolver.Resolve.Ambiguous<InventoryItem>>(result)
    }

    @Test fun inventoryItemNumberedIndex() {
        val catalog = mapOf(
            "item:a" to item("item:a", "Sword"),
            "item:b" to item("item:b", "Sword")
        )
        val inv = listOf(invItem("item:a"), invItem("item:b"))
        val result = NameResolver.resolveInventoryItem("2.sword", inv, catalog)
        assertIs<NameResolver.Resolve.Found<InventoryItem>>(result)
        assertEquals("item:b", result.entity.itemId)
    }

    // ---- Interactable resolution ----

    @Test fun interactableExact() {
        val items = listOf(interactable("feat:lever", "Rusted Lever"))
        val result = NameResolver.resolveInteractable("Rusted Lever", items)
        assertIs<NameResolver.Resolve.Found<RoomInteractable>>(result)
        assertEquals("feat:lever", result.entity.id)
    }

    @Test fun interactablePrefix() {
        val items = listOf(interactable("feat:altar", "Ancient Altar"))
        val result = NameResolver.resolveInteractable("anc", items)
        assertIs<NameResolver.Resolve.Found<RoomInteractable>>(result)
    }

    @Test fun interactableNotFound() {
        val items = listOf(interactable("feat:lever", "Rusted Lever"))
        val result = NameResolver.resolveInteractable("dragon", items)
        assertIs<NameResolver.Resolve.NotFound>(result)
    }

    // ---- Recipe resolution ----

    @Test fun recipeExact() {
        val recipes = listOf(recipe("recipe:iron_sword", "Iron Sword"))
        val result = NameResolver.resolveRecipe("Iron Sword", recipes)
        assertIs<NameResolver.Resolve.Found<RecipeInfo>>(result)
        assertEquals("recipe:iron_sword", result.entity.recipe.id)
    }

    @Test fun recipeSubstring() {
        val recipes = listOf(recipe("recipe:iron_sword", "Iron Sword"))
        val result = NameResolver.resolveRecipe("sword", recipes)
        assertIs<NameResolver.Resolve.Found<RecipeInfo>>(result)
    }

    @Test fun recipeNotFound() {
        val recipes = listOf(recipe("recipe:iron_sword", "Iron Sword"))
        val result = NameResolver.resolveRecipe("shield", recipes)
        assertIs<NameResolver.Resolve.NotFound>(result)
    }

    // ---- Ambiguity message ----

    @Test fun ambiguityMessageFormat() {
        val msg = NameResolver.ambiguityMessage("rat", listOf("Rat", "Giant Rat"))
        assertTrue(msg.contains("1. Rat"))
        assertTrue(msg.contains("2. Giant Rat"))
        assertTrue(msg.contains("2.rat"))
    }

    @Test fun ambiguityMessageCapsAt4() {
        val names = listOf("A", "B", "C", "D", "E")
        val msg = NameResolver.ambiguityMessage("x", names)
        assertTrue(msg.contains("1. A"))
        assertTrue(msg.contains("4. D"))
        assertTrue(!msg.contains("5. E"))
    }

    // ---- Empty list edge cases ----

    @Test fun resolveAgainstEmptyList() {
        assertIs<NameResolver.Resolve.NotFound>(NameResolver.resolveNpc("rat", emptyList()))
        assertIs<NameResolver.Resolve.NotFound>(NameResolver.resolveGroundItem("sword", emptyList(), emptyMap()))
        assertIs<NameResolver.Resolve.NotFound>(NameResolver.resolveInventoryItem("potion", emptyList(), emptyMap()))
        assertIs<NameResolver.Resolve.NotFound>(NameResolver.resolveInteractable("lever", emptyList()))
        assertIs<NameResolver.Resolve.NotFound>(NameResolver.resolveRecipe("sword", emptyList()))
    }
}
