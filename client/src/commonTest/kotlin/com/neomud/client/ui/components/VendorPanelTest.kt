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

    @Test
    fun upgrade_indicator_shown_when_vendor_item_is_better() = runComposeUiTest {
        val equippedArmor = TestData.item(id = "old_chest", name = "Old Chestplate", type = "armor", slot = "chest", armorValue = 8, damageBonus = 0)
        val vendorArmor = TestData.item(id = "new_chest", name = "New Chestplate", type = "armor", slot = "chest", armorValue = 12, damageBonus = 0)
        val vendorItem = TestData.vendorItem(item = vendorArmor, price = Coins(gold = 1))
        val equipped = TestData.inventoryItem(itemId = "old_chest", equipped = true, slot = "chest")

        setContent {
            TestThemeWrapper {
                VendorPanel(
                    vendorInfo = TestData.vendorInfo(
                        items = listOf(vendorItem),
                        playerInventory = listOf(equipped),
                        playerCoins = Coins(gold = 100)
                    ),
                    playerLevel = 10,
                    itemCatalog = catalog + mapOf("old_chest" to equippedArmor, "new_chest" to vendorArmor),
                    onBuy = {}, onSell = {}, onClose = {}
                )
            }
        }

        onNodeWithText("ARM +4 upgrade", substring = true).assertIsDisplayed()
    }

    @Test
    fun downgrade_indicator_shown_when_vendor_item_is_worse() = runComposeUiTest {
        val equippedWeapon = TestData.item(id = "great_sword", name = "Great Sword", damageBonus = 15, damageRange = 5)
        val vendorWeapon = TestData.item(id = "rusty_sword", name = "Rusty Sword", damageBonus = 5, damageRange = 2)
        val vendorItem = TestData.vendorItem(item = vendorWeapon, price = Coins(copper = 50))
        val equipped = TestData.inventoryItem(itemId = "great_sword", equipped = true, slot = "weapon")

        setContent {
            TestThemeWrapper {
                VendorPanel(
                    vendorInfo = TestData.vendorInfo(
                        items = listOf(vendorItem),
                        playerInventory = listOf(equipped),
                        playerCoins = Coins(gold = 100)
                    ),
                    playerLevel = 10,
                    itemCatalog = catalog + mapOf("great_sword" to equippedWeapon, "rusty_sword" to vendorWeapon),
                    onBuy = {}, onSell = {}, onClose = {}
                )
            }
        }

        onNodeWithText("DMG -10 downgrade", substring = true).assertIsDisplayed()
    }

    @Test
    fun sidegrade_indicator_shown_when_stats_are_equal() = runComposeUiTest {
        val equippedArmor = TestData.item(id = "armor_a", name = "Armor A", type = "armor", slot = "chest", armorValue = 10, damageBonus = 0)
        val vendorArmor = TestData.item(id = "armor_b", name = "Armor B", type = "armor", slot = "chest", armorValue = 10, damageBonus = 0)
        val vendorItem = TestData.vendorItem(item = vendorArmor, price = Coins(silver = 5))
        val equipped = TestData.inventoryItem(itemId = "armor_a", equipped = true, slot = "chest")

        setContent {
            TestThemeWrapper {
                VendorPanel(
                    vendorInfo = TestData.vendorInfo(
                        items = listOf(vendorItem),
                        playerInventory = listOf(equipped),
                        playerCoins = Coins(gold = 100)
                    ),
                    playerLevel = 10,
                    itemCatalog = catalog + mapOf("armor_a" to equippedArmor, "armor_b" to vendorArmor),
                    onBuy = {}, onSell = {}, onClose = {}
                )
            }
        }

        onNodeWithText("ARM same as equipped", substring = true).assertIsDisplayed()
    }

    @Test
    fun empty_slot_indicator_shown_when_nothing_equipped() = runComposeUiTest {
        val vendorArmor = TestData.item(id = "new_helm", name = "New Helm", type = "armor", slot = "head", armorValue = 6, damageBonus = 0)
        val vendorItem = TestData.vendorItem(item = vendorArmor, price = Coins(silver = 2))

        setContent {
            TestThemeWrapper {
                VendorPanel(
                    vendorInfo = TestData.vendorInfo(
                        items = listOf(vendorItem),
                        playerInventory = emptyList(),
                        playerCoins = Coins(gold = 100)
                    ),
                    playerLevel = 10,
                    itemCatalog = catalog + mapOf("new_helm" to vendorArmor),
                    onBuy = {}, onSell = {}, onClose = {}
                )
            }
        }

        onNodeWithText("No gear equipped", substring = true).assertIsDisplayed()
    }

    @Test
    fun no_comparison_shown_for_consumable_items() = runComposeUiTest {
        val potion = TestData.item(id = "mana_pot", name = "Mana Potion", type = "consumable", slot = "", damageBonus = 0, armorValue = 0, value = 25)
        val vendorItem = TestData.vendorItem(item = potion, price = Coins(copper = 25))

        setContent {
            TestThemeWrapper {
                VendorPanel(
                    vendorInfo = TestData.vendorInfo(
                        items = listOf(vendorItem),
                        playerCoins = Coins(gold = 100)
                    ),
                    playerLevel = 10,
                    itemCatalog = catalog + mapOf("mana_pot" to potion),
                    onBuy = {}, onSell = {}, onClose = {}
                )
            }
        }

        onAllNodesWithTag("comparison_indicator").assertCountEquals(0)
    }
}
