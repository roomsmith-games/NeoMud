package com.neomud.client.ui.components

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import coil3.ColorImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.test.FakeImageLoaderEngine
import coil3.test.default
import com.neomud.client.testutil.PaparazziThemeWrapper
import com.neomud.client.testutil.TestData
import com.neomud.shared.model.Coins
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(DelicateCoilApi::class)
class InventoryPanelScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6
    )

    private val catalog = TestData.itemCatalog()

    @Before
    fun setup() {
        val engine = FakeImageLoaderEngine.Builder()
            .default(ColorImage(android.graphics.Color.GRAY))
            .build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(paparazzi.context)
                .components { add(engine) }
                .build()
        )
    }

    @Test
    fun itemsGrid() {
        val inventory = listOf(
            TestData.inventoryItem(itemId = "health_potion", quantity = 5),
            TestData.inventoryItem(itemId = "gold_ring", quantity = 1)
        )
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                InventoryPanel(
                    inventory = inventory,
                    itemCatalog = catalog,
                    playerCoins = TestData.coins(gold = 3, silver = 15, copper = 42),
                    onUseItem = {},
                    onClose = {}
                )
            }
        }
    }

    @Test
    fun emptyBag() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                InventoryPanel(
                    inventory = emptyList(),
                    itemCatalog = catalog,
                    playerCoins = Coins(),
                    onUseItem = {},
                    onClose = {}
                )
            }
        }
    }

    @Test
    fun withCoins() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                InventoryPanel(
                    inventory = emptyList(),
                    itemCatalog = catalog,
                    playerCoins = TestData.coins(platinum = 1, gold = 50, silver = 25, copper = 99),
                    onUseItem = {},
                    onClose = {}
                )
            }
        }
    }
}
