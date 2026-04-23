package com.neomud.client.ui.components

import androidx.compose.ui.test.*
import com.neomud.client.testutil.ComposeTestBase
import com.neomud.client.testutil.TestData
import com.neomud.client.testutil.TestThemeWrapper
import com.neomud.client.testutil.installTestCoil
import com.neomud.client.testutil.resetTestCoil
import com.neomud.shared.model.Coins
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class InventoryPanelTest : ComposeTestBase() {

    @BeforeTest
    fun setup() { installTestCoil() }

    @AfterTest
    fun teardown() { resetTestCoil() }

    private val catalog = TestData.itemCatalog()

    @Test
    fun header_shows_Inventory_title() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                InventoryPanel(
                    inventory = emptyList(),
                    itemCatalog = catalog,
                    playerCoins = Coins(),
                    onUseItem = {},
                    onClose = {}
                )
            }
        }

        onNodeWithText("Inventory", substring = true).assertExists()
    }

    @Test
    fun close_button_fires_onClose() = runComposeUiTest {
        var closed = false
        setContent {
            TestThemeWrapper {
                InventoryPanel(
                    inventory = emptyList(),
                    itemCatalog = catalog,
                    playerCoins = Coins(),
                    onUseItem = {},
                    onClose = { closed = true }
                )
            }
        }

        onNodeWithTag("close_button").performClick()
        assertTrue(closed)
    }

    @Test
    fun shows_coin_badges_when_player_has_coins() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                InventoryPanel(
                    inventory = emptyList(),
                    itemCatalog = catalog,
                    playerCoins = TestData.coins(copper = 50, silver = 10, gold = 2),
                    onUseItem = {},
                    onClose = {}
                )
            }
        }

        onNodeWithText("2 GP").assertIsDisplayed()
        onNodeWithText("10 SP").assertIsDisplayed()
        onNodeWithText("50 CP").assertIsDisplayed()
    }

    @Test
    fun hides_coin_section_when_coins_are_empty() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                InventoryPanel(
                    inventory = emptyList(),
                    itemCatalog = catalog,
                    playerCoins = Coins(),
                    onUseItem = {},
                    onClose = {}
                )
            }
        }

        onNodeWithText("Coins").assertDoesNotExist()
    }

    @Test
    fun shows_empty_bag_message_when_no_non_equipment_items() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                InventoryPanel(
                    inventory = emptyList(),
                    itemCatalog = catalog,
                    playerCoins = Coins(),
                    onUseItem = {},
                    onClose = {}
                )
            }
        }

        onNodeWithText("Your bag is empty.").assertIsDisplayed()
    }

    @Test
    fun shows_consumable_Use_badge_and_fires_onUseItem() = runComposeUiTest {
        var usedItem: String? = null
        val inventory = listOf(
            TestData.inventoryItem(itemId = "health_potion", quantity = 3)
        )

        setContent {
            TestThemeWrapper {
                InventoryPanel(
                    inventory = inventory,
                    itemCatalog = catalog,
                    playerCoins = Coins(),
                    onUseItem = { usedItem = it },
                    onClose = {}
                )
            }
        }

        onNodeWithText("Use").assertIsDisplayed()
        onNodeWithText("Health Potion").performClick()
        assertEquals("health_potion", usedItem)
    }

    @Test
    fun shows_quantity_badge_for_stacked_items() = runComposeUiTest {
        val inventory = listOf(
            TestData.inventoryItem(itemId = "health_potion", quantity = 5)
        )

        setContent {
            TestThemeWrapper {
                InventoryPanel(
                    inventory = inventory,
                    itemCatalog = catalog,
                    playerCoins = Coins(),
                    onUseItem = {},
                    onClose = {}
                )
            }
        }

        onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun excludes_equipped_items_from_bag() = runComposeUiTest {
        val inventory = listOf(
            TestData.inventoryItem(itemId = "gold_ring", quantity = 1, equipped = false),
            TestData.inventoryItem(itemId = "health_potion", quantity = 1, equipped = true)
        )

        setContent {
            TestThemeWrapper {
                InventoryPanel(
                    inventory = inventory,
                    itemCatalog = catalog,
                    playerCoins = Coins(),
                    onUseItem = {},
                    onClose = {}
                )
            }
        }

        onNodeWithText("Gold Ring").assertIsDisplayed()
        onNodeWithText("Health Potion").assertDoesNotExist()
    }
}
