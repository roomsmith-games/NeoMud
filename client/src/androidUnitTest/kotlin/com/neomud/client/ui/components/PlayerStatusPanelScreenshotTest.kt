package com.neomud.client.ui.components

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.neomud.client.testutil.PaparazziThemeWrapper
import com.neomud.client.testutil.TestData
import com.neomud.shared.model.EffectType
import org.junit.Rule
import org.junit.Test

class PlayerStatusPanelScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6
    )

    @Test
    fun fullHp() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                PlayerStatusPanel(
                    player = TestData.player(currentHp = 100, maxHp = 100, currentMp = 50, maxMp = 50),
                    activeEffects = emptyList(),
                    onClick = {}
                )
            }
        }
    }

    @Test
    fun lowHp() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                PlayerStatusPanel(
                    player = TestData.player(currentHp = 15, maxHp = 100, currentMp = 5, maxMp = 50),
                    activeEffects = emptyList(),
                    onClick = {}
                )
            }
        }
    }

    @Test
    fun withEffects() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                PlayerStatusPanel(
                    player = TestData.player(),
                    activeEffects = listOf(
                        TestData.activeEffect(name = "Poison", type = EffectType.POISON),
                        TestData.activeEffect(name = "Haste", type = EffectType.HASTE),
                        TestData.activeEffect(name = "Strength", type = EffectType.BUFF_STRENGTH)
                    ),
                    onClick = {}
                )
            }
        }
    }

    @Test
    fun hiddenState() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                PlayerStatusPanel(
                    player = TestData.player(),
                    activeEffects = emptyList(),
                    isHidden = true,
                    onClick = {}
                )
            }
        }
    }

    @Test
    fun compactFullHp() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                PlayerStatusPanel(
                    player = TestData.player(currentHp = 100, maxHp = 100, currentMp = 50, maxMp = 50),
                    activeEffects = emptyList(),
                    compact = true,
                    onClick = {}
                )
            }
        }
    }

    @Test
    fun compactLowHp() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                PlayerStatusPanel(
                    player = TestData.player(currentHp = 8, maxHp = 100, currentMp = 3, maxMp = 50),
                    activeEffects = emptyList(),
                    compact = true,
                    onClick = {}
                )
            }
        }
    }

    @Test
    fun compactHiddenWithEffects() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                PlayerStatusPanel(
                    player = TestData.player(),
                    activeEffects = listOf(
                        TestData.activeEffect(name = "Poison", type = EffectType.POISON),
                        TestData.activeEffect(name = "Haste", type = EffectType.HASTE)
                    ),
                    isHidden = true,
                    compact = true,
                    onClick = {}
                )
            }
        }
    }
}
