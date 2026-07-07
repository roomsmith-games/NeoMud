package com.neomud.client.ui.components

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.neomud.client.testutil.PaparazziThemeWrapper
import com.neomud.client.testutil.TestData
import org.junit.Rule
import org.junit.Test

class SpellBarScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6
    )

    private val fireball = TestData.spellDef(id = "fireball", name = "Fireball", school = "mage", manaCost = 10)
    private val heal = TestData.spellDef(id = "heal", name = "Heal", school = "priest", manaCost = 15)
    private val catalog = mapOf("fireball" to fireball, "heal" to heal)

    @Test
    fun mixedSlots() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                SpellBar(
                    spellSlots = listOf("fireball", "heal", null, null),
                    spellCatalog = catalog,
                    readiedSpellId = null,
                    currentMp = 50,
                    onReadySpell = {},
                    onOpenSpellPicker = {}
                )
            }
        }
    }

    @Test
    fun readiedSpell() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                SpellBar(
                    spellSlots = listOf("fireball", "heal", null, null),
                    spellCatalog = catalog,
                    readiedSpellId = "fireball",
                    currentMp = 50,
                    onReadySpell = {},
                    onOpenSpellPicker = {}
                )
            }
        }
    }
}
