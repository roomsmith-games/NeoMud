package com.neomud.client.ui.components

import androidx.compose.ui.test.*
import com.neomud.client.testutil.ComposeTestBase
import com.neomud.client.testutil.TestData
import com.neomud.client.testutil.TestThemeWrapper
import com.neomud.client.testutil.installTestCoil
import com.neomud.client.testutil.resetTestCoil
import com.neomud.shared.model.Coins
import com.neomud.shared.model.Item
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class VendorPanelTest : ComposeTestBase() {

    @BeforeTest
    fun setup() { installTestCoil() }

    @AfterTest
    fun teardown() { resetTestCoil() }

    private val catalog = TestData.itemCatalog()

    @Test
    fun shows_vendor_name() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                VendorPanel(
                    vendorInfo = TestData.vendorInfo(vendorName = "Grimjaw's Weapons"),
                    playerLevel = 5,
                    itemCatalog = catalog,
                    onBuy = {},
                    onSell = {},
                    onClose = {}
                )
            }
        }

        onNodeWithText("Grimjaw's Weapons").assertIsDisplayed()
    }

    @Test
    fun buy_tab_shows_vendor_items() = runComposeUiTest {
        val vendorItem = TestData.vendorItem(
            item = Item(
                id = "magic_staff",
                name = "Magic Staff",
                description = "A magical staff",
                type = "weapon",
                slot = "weapon",
                damageBonus = 8,
                value = 200
            ),
            price = Coins(gold = 2)
        )

        setContent {
            TestThemeWrapper {
                VendorPanel(
                    vendorInfo = TestData.vendorInfo(items = listOf(vendorItem)),
                    playerLevel = 5,
                    itemCatalog = catalog,
                    onBuy = {},
                    onSell = {},
                    onClose = {}
                )
            }
        }

        onNodeWithText("Magic Staff").assertIsDisplayed()
    }

    @Test
    fun buy_callback_fires_on_buy_button_click() = runComposeUiTest {
        var boughtItem: String? = null
        val vendorItem = TestData.vendorItem(
            item = TestData.item(id = "cheap_sword", name = "Cheap Sword", value = 10),
            price = Coins(copper = 10)
        )

        setContent {
            TestThemeWrapper {
                VendorPanel(
                    vendorInfo = TestData.vendorInfo(
                        items = listOf(vendorItem),
                        playerCoins = Coins(gold = 100)
                    ),
                    playerLevel = 10,
                    itemCatalog = catalog,
                    onBuy = { boughtItem = it },
                    onSell = {},
                    onClose = {}
                )
            }
        }

        // "Buy" appears as both a Tab and a Button; the Button is the second match
        onAllNodesWithText("Buy")[1].performClick()
        assertEquals("cheap_sword", boughtItem)
    }

    @Test
    fun sell_tab_shows_player_inventory_items() = runComposeUiTest {
        val inventory = listOf(
            TestData.inventoryItem(itemId = "health_potion", quantity = 3)
        )

        setContent {
            TestThemeWrapper {
                VendorPanel(
                    vendorInfo = TestData.vendorInfo(playerInventory = inventory),
                    playerLevel = 5,
                    itemCatalog = catalog,
                    onBuy = {},
                    onSell = {},
                    onClose = {}
                )
            }
        }

        onNodeWithText("Sell").performClick()
        onNodeWithText("Health Potion").assertIsDisplayed()
    }

    @Test
    fun close_button_fires_onClose() = runComposeUiTest {
        var closed = false
        setContent {
            TestThemeWrapper {
                VendorPanel(
                    vendorInfo = TestData.vendorInfo(),
                    playerLevel = 5,
                    itemCatalog = catalog,
                    onBuy = {},
                    onSell = {},
                    onClose = { closed = true }
                )
            }
        }

        onNodeWithTag("close_button").performClick()
        assertTrue(closed)
    }

    @Test
    fun haggle_active_badge_displayed_when_hasHaggle_is_true() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                VendorPanel(
                    vendorInfo = TestData.vendorInfo(hasHaggle = true),
                    playerLevel = 5,
                    itemCatalog = catalog,
                    onBuy = {},
                    onSell = {},
                    onClose = {}
                )
            }
        }

        onNodeWithText("Haggle Active", substring = true).assertIsDisplayed()
    }

    @Test
    fun haggle_badge_not_shown_when_hasHaggle_is_false() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                VendorPanel(
                    vendorInfo = TestData.vendorInfo(hasHaggle = false),
                    playerLevel = 5,
                    itemCatalog = catalog,
                    onBuy = {},
                    onSell = {},
                    onClose = {}
                )
            }
        }

        onNodeWithText("Haggle Active", substring = true).assertDoesNotExist()
    }
}
