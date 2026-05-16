package com.neomud.client.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.neomud.client.testutil.ComposeTestBase
import com.neomud.client.testutil.TestThemeWrapper
import com.neomud.shared.model.Direction
import com.neomud.shared.model.MapRoom
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class MiniMapTest : ComposeTestBase() {

    private val squareRoom = MapRoom("r1", "Center", 0, 0, exits = mapOf(
        Direction.NORTH to "r2",
        Direction.SOUTH to "r3",
        Direction.EAST to "r4",
        Direction.WEST to "r5"
    ))

    @Test
    fun renders_without_crash_with_empty_room_list() = runComposeUiTest {
        setContent {
            TestThemeWrapper {
                MiniMap(
                    rooms = emptyList(),
                    playerRoomId = "r1",
                    modifier = Modifier.size(200.dp)
                )
            }
        }
        onRoot().assertIsDisplayed()
    }

    @Test
    fun renders_basic_rooms_with_cardinal_exits() = runComposeUiTest {
        val rooms = listOf(
            squareRoom,
            MapRoom("r2", "North", 0, 1, exits = mapOf(Direction.SOUTH to "r1")),
            MapRoom("r3", "South", 0, -1, exits = mapOf(Direction.NORTH to "r1")),
            MapRoom("r4", "East", 1, 0, exits = mapOf(Direction.WEST to "r1")),
            MapRoom("r5", "West", -1, 0, exits = mapOf(Direction.EAST to "r1"))
        )
        setContent {
            TestThemeWrapper {
                MiniMap(
                    rooms = rooms,
                    playerRoomId = "r1",
                    modifier = Modifier.size(300.dp)
                )
            }
        }
        onRoot().assertIsDisplayed()
    }

    @Test
    fun renders_UP_and_DOWN_exit_triangles_without_crash() = runComposeUiTest {
        val rooms = listOf(
            MapRoom("r1", "Center", 0, 0, exits = mapOf(
                Direction.NORTH to "r2",
                Direction.UP to "r3",
                Direction.DOWN to "r4"
            )),
            MapRoom("r2", "North", 0, 1, exits = mapOf(Direction.SOUTH to "r1")),
            MapRoom("r3", "Above", 0, 0, exits = mapOf(Direction.DOWN to "r1")),
            MapRoom("r4", "Below", 0, 0, exits = mapOf(Direction.UP to "r1"))
        )
        setContent {
            TestThemeWrapper {
                MiniMap(
                    rooms = rooms,
                    playerRoomId = "r1",
                    modifier = Modifier.size(300.dp)
                )
            }
        }
        onRoot().assertIsDisplayed()
    }

    @Test
    fun renders_locked_exits_with_amber_styling_without_crash() = runComposeUiTest {
        val rooms = listOf(
            MapRoom("r1", "Center", 0, 0,
                exits = mapOf(Direction.NORTH to "r2", Direction.EAST to "r3"),
                lockedExits = setOf(Direction.NORTH)
            ),
            MapRoom("r2", "North", 0, 1, exits = mapOf(Direction.SOUTH to "r1")),
            MapRoom("r3", "East", 1, 0, exits = mapOf(Direction.WEST to "r1"))
        )
        setContent {
            TestThemeWrapper {
                MiniMap(
                    rooms = rooms,
                    playerRoomId = "r1",
                    modifier = Modifier.size(300.dp)
                )
            }
        }
        onRoot().assertIsDisplayed()
    }

    @Test
    fun renders_hidden_exits_with_dashed_purple_styling_without_crash() = runComposeUiTest {
        val rooms = listOf(
            MapRoom("r1", "Center", 0, 0,
                exits = mapOf(Direction.NORTH to "r2", Direction.SOUTH to "r3"),
                hiddenExits = setOf(Direction.SOUTH)
            ),
            MapRoom("r2", "North", 0, 1, exits = mapOf(Direction.SOUTH to "r1")),
            MapRoom("r3", "South", 0, -1, exits = mapOf(Direction.NORTH to "r1"))
        )
        setContent {
            TestThemeWrapper {
                MiniMap(
                    rooms = rooms,
                    playerRoomId = "r1",
                    modifier = Modifier.size(300.dp)
                )
            }
        }
        onRoot().assertIsDisplayed()
    }

    @Test
    fun renders_locked_UP_exit_triangle_without_crash() = runComposeUiTest {
        val rooms = listOf(
            MapRoom("r1", "Center", 0, 0,
                exits = mapOf(Direction.UP to "r2"),
                lockedExits = setOf(Direction.UP)
            ),
            MapRoom("r2", "Above", 0, 0, exits = mapOf(Direction.DOWN to "r1"))
        )
        setContent {
            TestThemeWrapper {
                MiniMap(
                    rooms = rooms,
                    playerRoomId = "r1",
                    modifier = Modifier.size(200.dp)
                )
            }
        }
        onRoot().assertIsDisplayed()
    }

    @Test
    fun renders_hidden_DOWN_exit_triangle_without_crash() = runComposeUiTest {
        val rooms = listOf(
            MapRoom("r1", "Center", 0, 0,
                exits = mapOf(Direction.DOWN to "r2"),
                hiddenExits = setOf(Direction.DOWN)
            ),
            MapRoom("r2", "Below", 0, 0, exits = mapOf(Direction.UP to "r1"))
        )
        setContent {
            TestThemeWrapper {
                MiniMap(
                    rooms = rooms,
                    playerRoomId = "r1",
                    modifier = Modifier.size(200.dp)
                )
            }
        }
        onRoot().assertIsDisplayed()
    }

    @Test
    fun renders_with_fog_of_war_and_visited_rooms() = runComposeUiTest {
        val rooms = listOf(
            squareRoom,
            MapRoom("r2", "North", 0, 1, exits = mapOf(Direction.SOUTH to "r1")),
            MapRoom("r3", "South", 0, -1, exits = mapOf(Direction.NORTH to "r1"))
        )
        setContent {
            TestThemeWrapper {
                MiniMap(
                    rooms = rooms,
                    playerRoomId = "r1",
                    visitedRoomIds = setOf("r1", "r2"),
                    fogOfWar = true,
                    modifier = Modifier.size(300.dp)
                )
            }
        }
        onRoot().assertIsDisplayed()
    }

    @Test
    fun renders_fog_of_war_stubs_for_locked_unvisited_exits() = runComposeUiTest {
        val rooms = listOf(
            MapRoom("r1", "Center", 0, 0,
                exits = mapOf(Direction.NORTH to "r2"),
                lockedExits = setOf(Direction.NORTH)
            ),
            MapRoom("r2", "North", 0, 1, exits = mapOf(Direction.SOUTH to "r1"))
        )
        setContent {
            TestThemeWrapper {
                MiniMap(
                    rooms = rooms,
                    playerRoomId = "r1",
                    visitedRoomIds = setOf("r1"),
                    fogOfWar = true,
                    modifier = Modifier.size(300.dp)
                )
            }
        }
        onRoot().assertIsDisplayed()
    }

    @Test
    fun renders_combined_locked_hidden_and_up_down_exits() = runComposeUiTest {
        val rooms = listOf(
            MapRoom("r1", "Center", 0, 0,
                exits = mapOf(
                    Direction.NORTH to "r2",
                    Direction.EAST to "r3",
                    Direction.UP to "r4",
                    Direction.DOWN to "r5"
                ),
                lockedExits = setOf(Direction.NORTH, Direction.UP),
                hiddenExits = setOf(Direction.EAST)
            ),
            MapRoom("r2", "North", 0, 1, exits = mapOf(Direction.SOUTH to "r1")),
            MapRoom("r3", "East", 1, 0, exits = mapOf(Direction.WEST to "r1")),
            MapRoom("r4", "Above", 0, 0, exits = mapOf(Direction.DOWN to "r1")),
            MapRoom("r5", "Below", 0, 0, exits = mapOf(Direction.UP to "r1"))
        )
        setContent {
            TestThemeWrapper {
                MiniMap(
                    rooms = rooms,
                    playerRoomId = "r1",
                    modifier = Modifier.size(300.dp)
                )
            }
        }
        onRoot().assertIsDisplayed()
    }
}
