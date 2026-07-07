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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(DelicateCoilApi::class)
class EquipmentPanelScreenshotTest {

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
    fun paperdollPopulated() {
        val equipment = mapOf(
            "weapon" to "iron_sword",
            "head" to "iron_helm",
            "chest" to "chain_chest"
        )
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                EquipmentPanel(
                    inventory = listOf(
                        TestData.inventoryItem(itemId = "iron_sword", equipped = true, slot = "weapon"),
                        TestData.inventoryItem(itemId = "iron_helm", equipped = true, slot = "head"),
                        TestData.inventoryItem(itemId = "chain_chest", equipped = true, slot = "chest")
                    ),
                    equipment = equipment,
                    itemCatalog = catalog,
                    onEquipItem = { _, _ -> },
                    onUnequipItem = {},
                    onClose = {}
                )
            }
        }
    }

    @Test
    fun paperdollEmpty() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                EquipmentPanel(
                    inventory = emptyList(),
                    equipment = emptyMap(),
                    itemCatalog = catalog,
                    onEquipItem = { _, _ -> },
                    onUnequipItem = {},
                    onClose = {}
                )
            }
        }
    }
}
