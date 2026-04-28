package com.neomud.client.ui.components

import androidx.compose.ui.test.*
import com.neomud.client.testutil.ComposeTestBase
import com.neomud.client.testutil.TestData
import com.neomud.client.testutil.TestThemeWrapper
import com.neomud.shared.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PlaceItemDialogTest : ComposeTestBase() {

    private fun prompt(
        featureId: String = "altar1",
        label: String = "polished black-stone altar",
        prompt: String = "The altar's central depression waits.",
        accepted: List<String> = listOf("item:watchers_honor_stone"),
    ) = ServerMessage.PlaceItemPrompt(
        featureId = featureId,
        label = label,
        prompt = prompt,
        acceptedItems = accepted
    )

    private val catalog = mapOf(
        "item:watchers_honor_stone" to TestData.item(
            id = "item:watchers_honor_stone",
            name = "Watcher's Honor-Stone",
            type = "quest",
            slot = ""
        ),
        "item:warden_token" to TestData.item(
            id = "item:warden_token",
            name = "Warden's Token",
            type = "quest",
            slot = ""
        )
    )

    @Test
    fun renders_prompt_label_and_text() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                PlaceItemDialog(
                    prompt = prompt(),
                    inventory = listOf(TestData.inventoryItem(itemId = "item:watchers_honor_stone")),
                    itemCatalog = catalog,
                    onPlace = {},
                    onDismiss = {}
                )
            }
        }

        onNodeWithText("polished black-stone altar").assertIsDisplayed()
        onNodeWithText("The altar's central depression waits.").assertIsDisplayed()
    }

    @Test
    fun shows_accepted_inventory_item_resolved_to_catalog_name() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                PlaceItemDialog(
                    prompt = prompt(),
                    inventory = listOf(TestData.inventoryItem(itemId = "item:watchers_honor_stone")),
                    itemCatalog = catalog,
                    onPlace = {},
                    onDismiss = {}
                )
            }
        }

        onNodeWithText("Watcher's Honor-Stone").assertIsDisplayed()
    }

    @Test
    fun place_callback_fires_with_chosen_itemId() = runComposeUiTest {
        var placed: String? = null
        setContent {
            TestThemeWrapper {
                PlaceItemDialog(
                    prompt = prompt(),
                    inventory = listOf(TestData.inventoryItem(itemId = "item:watchers_honor_stone")),
                    itemCatalog = catalog,
                    onPlace = { placed = it },
                    onDismiss = {}
                )
            }
        }

        onNodeWithTag("placeItemRow:item:watchers_honor_stone").performClick()
        assertEquals("item:watchers_honor_stone", placed)
    }

    @Test
    fun cancel_button_fires_onDismiss() = runComposeUiTest {
        var dismissed = false
        setContent {
            TestThemeWrapper {
                PlaceItemDialog(
                    prompt = prompt(),
                    inventory = listOf(TestData.inventoryItem(itemId = "item:watchers_honor_stone")),
                    itemCatalog = catalog,
                    onPlace = {},
                    onDismiss = { dismissed = true }
                )
            }
        }

        onNodeWithTag("placeItemCancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun filters_out_equipped_items() = runComposeUiTest {
        var placed: String? = null
        setContent {
            TestThemeWrapper {
                PlaceItemDialog(
                    // Accepts both, but the player has the honor-stone equipped
                    prompt = prompt(accepted = listOf("item:watchers_honor_stone", "item:warden_token")),
                    inventory = listOf(
                        TestData.inventoryItem(itemId = "item:watchers_honor_stone", equipped = true),
                        TestData.inventoryItem(itemId = "item:warden_token", equipped = false)
                    ),
                    itemCatalog = catalog,
                    onPlace = { placed = it },
                    onDismiss = {}
                )
            }
        }

        // Equipped item must not appear as a tappable row
        onNodeWithTag("placeItemRow:item:watchers_honor_stone").assertDoesNotExist()
        onNodeWithTag("placeItemRow:item:warden_token").assertIsDisplayed()
        onNodeWithTag("placeItemRow:item:warden_token").performClick()
        assertEquals("item:warden_token", placed)
    }

    @Test
    fun empty_state_when_no_inventory_matches_acceptedItems() = runComposeUiTest {
        var placed: String? = null
        var dismissed = false
        setContent {
            TestThemeWrapper {
                PlaceItemDialog(
                    prompt = prompt(accepted = listOf("item:watchers_honor_stone")),
                    inventory = listOf(TestData.inventoryItem(itemId = "item:warden_token")),
                    itemCatalog = catalog,
                    onPlace = { placed = it },
                    onDismiss = { dismissed = true }
                )
            }
        }

        onNodeWithTag("placeItemEmptyState", useUnmergedTree = true).assertExists()
        onNodeWithText("Nothing in your pack fits here.", useUnmergedTree = true).assertExists()
        onNodeWithTag("placeItemRow:item:watchers_honor_stone", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("placeItemRow:item:warden_token", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("placeItemCancel").performClick()
        assertTrue(dismissed)
        assertNull(placed)
    }
}
