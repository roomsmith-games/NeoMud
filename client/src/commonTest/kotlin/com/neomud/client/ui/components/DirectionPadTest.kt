package com.neomud.client.ui.components

import androidx.compose.ui.test.*
import com.neomud.client.testutil.ComposeTestBase
import com.neomud.client.testutil.TestThemeWrapper
import com.neomud.shared.model.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DirectionPadTest : ComposeTestBase() {

    @Test
    fun north_button_fires_onMove_NORTH_when_available() = runComposeUiTest {
        var moved: Direction? = null
        setContent {
            TestThemeWrapper {
                DirectionPad(
                    availableExits = setOf(Direction.NORTH),
                    onMove = { moved = it },
                    onLook = {}
                )
            }
        }

        onNodeWithText("N").performClick()
        assertEquals(Direction.NORTH, moved)
    }

    @Test
    fun south_button_fires_onMove_SOUTH_when_available() = runComposeUiTest {
        var moved: Direction? = null
        setContent {
            TestThemeWrapper {
                DirectionPad(
                    availableExits = setOf(Direction.SOUTH),
                    onMove = { moved = it },
                    onLook = {}
                )
            }
        }

        onNodeWithText("S").performClick()
        assertEquals(Direction.SOUTH, moved)
    }

    @Test
    fun east_and_west_buttons_fire_correct_directions() = runComposeUiTest {
        val moves = mutableListOf<Direction>()
        setContent {
            TestThemeWrapper {
                DirectionPad(
                    availableExits = setOf(Direction.EAST, Direction.WEST),
                    onMove = { moves.add(it) },
                    onLook = {}
                )
            }
        }

        onNodeWithText("E").performClick()
        onNodeWithText("W").performClick()
        assertEquals(listOf(Direction.EAST, Direction.WEST), moves)
    }

    @Test
    fun diagonal_buttons_fire_correct_directions() = runComposeUiTest {
        val moves = mutableListOf<Direction>()
        setContent {
            TestThemeWrapper {
                DirectionPad(
                    availableExits = Direction.entries.toSet(),
                    onMove = { moves.add(it) },
                    onLook = {}
                )
            }
        }

        onNodeWithText("\u2196").performClick() // NW ↖
        onNodeWithText("\u2197").performClick() // NE ↗
        onNodeWithText("\u2199").performClick() // SW ↙
        onNodeWithText("\u2198").performClick() // SE ↘
        assertEquals(
            listOf(Direction.NORTHWEST, Direction.NORTHEAST, Direction.SOUTHWEST, Direction.SOUTHEAST),
            moves
        )
    }

    @Test
    fun up_and_down_stair_buttons_fire_correct_directions() = runComposeUiTest {
        val moves = mutableListOf<Direction>()
        setContent {
            TestThemeWrapper {
                DirectionPad(
                    availableExits = setOf(Direction.UP, Direction.DOWN),
                    onMove = { moves.add(it) },
                    onLook = {}
                )
            }
        }

        onNodeWithText("\u25B2 UP", substring = true).performClick()
        onNodeWithText("\u25BC DN", substring = true).performClick()
        assertEquals(listOf(Direction.UP, Direction.DOWN), moves)
    }

    @Test
    fun disabled_direction_does_not_fire_onMove() = runComposeUiTest {
        var moved: Direction? = null
        setContent {
            TestThemeWrapper {
                DirectionPad(
                    availableExits = emptySet(),
                    onMove = { moved = it },
                    onLook = {}
                )
            }
        }

        onNodeWithText("N").performClick()
        onNodeWithText("S").performClick()
        onNodeWithText("E").performClick()
        assertNull(moved)
    }

    @Test
    fun look_button_always_fires() = runComposeUiTest {
        var looked = false
        setContent {
            TestThemeWrapper {
                DirectionPad(
                    availableExits = emptySet(),
                    onMove = {},
                    onLook = { looked = true }
                )
            }
        }

        onNodeWithTag("look_button").performClick()
        assertTrue(looked)
    }

    @Test
    fun locked_exit_still_fires_onMove_when_clicked() = runComposeUiTest {
        var moved: Direction? = null
        setContent {
            TestThemeWrapper {
                DirectionPad(
                    availableExits = setOf(Direction.NORTH),
                    onMove = { moved = it },
                    onLook = {},
                    lockedExits = setOf(Direction.NORTH)
                )
            }
        }

        onNodeWithText("N").performClick()
        assertEquals(Direction.NORTH, moved)
    }

    @Test
    fun locked_stair_buttons_still_fire_onMove_when_clicked() = runComposeUiTest {
        val moves = mutableListOf<Direction>()
        setContent {
            TestThemeWrapper {
                DirectionPad(
                    availableExits = setOf(Direction.UP, Direction.DOWN),
                    onMove = { moves.add(it) },
                    onLook = {},
                    lockedExits = setOf(Direction.UP, Direction.DOWN)
                )
            }
        }

        onNodeWithText("\u25B2 UP", substring = true).performClick()
        onNodeWithText("\u25BC DN", substring = true).performClick()
        assertEquals(listOf(Direction.UP, Direction.DOWN), moves)
    }

    @Test
    fun renders_with_mixed_locked_and_unlocked_exits() = runComposeUiTest {
        var moved: Direction? = null
        setContent {
            TestThemeWrapper {
                DirectionPad(
                    availableExits = setOf(Direction.NORTH, Direction.SOUTH, Direction.EAST),
                    onMove = { moved = it },
                    onLook = {},
                    lockedExits = setOf(Direction.NORTH)
                )
            }
        }

        onNodeWithText("S").performClick()
        assertEquals(Direction.SOUTH, moved)
    }

    @Test
    fun renders_with_all_diagonal_exits_locked() = runComposeUiTest {
        val moves = mutableListOf<Direction>()
        setContent {
            TestThemeWrapper {
                DirectionPad(
                    availableExits = setOf(Direction.NORTHWEST, Direction.NORTHEAST, Direction.SOUTHWEST, Direction.SOUTHEAST),
                    onMove = { moves.add(it) },
                    onLook = {},
                    lockedExits = setOf(Direction.NORTHWEST, Direction.NORTHEAST, Direction.SOUTHWEST, Direction.SOUTHEAST)
                )
            }
        }

        onNodeWithText("\u2196").performClick() // NW
        onNodeWithText("\u2197").performClick() // NE
        assertEquals(listOf(Direction.NORTHWEST, Direction.NORTHEAST), moves)
    }
}
