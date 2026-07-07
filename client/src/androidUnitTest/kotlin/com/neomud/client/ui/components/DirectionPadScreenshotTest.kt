package com.neomud.client.ui.components

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.neomud.client.testutil.PaparazziThemeWrapper
import com.neomud.shared.model.Direction
import org.junit.Rule
import org.junit.Test

class DirectionPadScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6
    )

    @Test
    fun allExits() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                DirectionPad(
                    availableExits = Direction.entries.toSet(),
                    onMove = {},
                    onLook = {}
                )
            }
        }
    }

    @Test
    fun noExits() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                DirectionPad(
                    availableExits = emptySet(),
                    onMove = {},
                    onLook = {}
                )
            }
        }
    }

    @Test
    fun cardinalOnly() {
        paparazzi.snapshot {
            PaparazziThemeWrapper {
                DirectionPad(
                    availableExits = setOf(
                        Direction.NORTH, Direction.SOUTH,
                        Direction.EAST, Direction.WEST
                    ),
                    onMove = {},
                    onLook = {}
                )
            }
        }
    }
}
