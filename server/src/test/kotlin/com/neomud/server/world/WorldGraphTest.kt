package com.neomud.server.world

import com.neomud.shared.model.Direction
import com.neomud.shared.model.Room
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class WorldGraphTest {

    private fun buildTestWorld(): WorldGraph {
        val graph = WorldGraph()

        graph.addRoom(Room("town:square", "Town Square", "Center of town", mapOf(Direction.NORTH to "town:gate", Direction.EAST to "town:market"), "town", 0, 0))
        graph.addRoom(Room("town:gate", "North Gate", "The gate", mapOf(Direction.SOUTH to "town:square", Direction.NORTH to "forest:edge"), "town", 0, 1))
        graph.addRoom(Room("town:market", "Market", "The market", mapOf(Direction.WEST to "town:square"), "town", 1, 0))
        graph.addRoom(Room("forest:edge", "Forest Edge", "Forest start", mapOf(Direction.SOUTH to "town:gate", Direction.NORTH to "forest:path"), "forest", 0, 2))
        graph.addRoom(Room("forest:path", "Forest Path", "A winding path", mapOf(Direction.SOUTH to "forest:edge"), "forest", 0, 3))

        graph.setDefaultSpawn("town:square")
        return graph
    }

    @Test
    fun testGetRoom() {
        val graph = buildTestWorld()
        val room = graph.getRoom("town:square")
        assertNotNull(room)
        assertEquals("Town Square", room.name)
    }

    @Test
    fun testGetRoomReturnsNullForInvalidId() {
        val graph = buildTestWorld()
        assertNull(graph.getRoom("nonexistent:room"))
    }

    @Test
    fun testDefaultSpawnRoom() {
        val graph = buildTestWorld()
        assertEquals("town:square", graph.defaultSpawnRoom)
    }

    @Test
    fun testGetRoomsNearRadius1() {
        val graph = buildTestWorld()
        val nearby = graph.getRoomsNear("town:square", radius = 1)
        val ids = nearby.map { it.id }.toSet()

        assert("town:square" in ids) { "Should include center room" }
        assert("town:gate" in ids) { "Should include north neighbor" }
        assert("town:market" in ids) { "Should include east neighbor" }
        assertEquals(3, nearby.size, "Should find exactly 3 rooms at radius 1")
    }

    @Test
    fun testGetRoomsNearCrossesZoneBoundary() {
        val graph = buildTestWorld()
        val nearby = graph.getRoomsNear("town:gate", radius = 2)
        val ids = nearby.map { it.id }.toSet()

        assert("forest:edge" in ids) { "Should include cross-zone room forest:edge" }
        assert("town:square" in ids) { "Should include town:square" }
        assert("forest:path" in ids) { "Should include forest:path at radius 2" }
    }

    @Test
    fun testGetRoomsNearFullWorld() {
        val graph = buildTestWorld()
        val nearby = graph.getRoomsNear("town:square", radius = 10)
        assertEquals(5, nearby.size, "Should find all rooms in the world")
    }

    @Test
    fun testRoomCount() {
        val graph = buildTestWorld()
        assertEquals(5, graph.roomCount)
    }

    // --- Lock reset timer tests ---

    @Test
    fun testUnlockExitStartsResetTimer() {
        val graph = WorldGraph()
        graph.addRoom(Room(
            "r1", "Room 1", "desc",
            mapOf(Direction.NORTH to "r2"), "zone", 0, 0,
            lockedExits = mapOf(Direction.NORTH to 15)
        ))
        graph.addRoom(Room("r2", "Room 2", "desc", mapOf(Direction.SOUTH to "r1"), "zone", 0, 1))
        graph.setOriginalLockedExits("r1", mapOf(Direction.NORTH to 15))
        graph.setLockResetDurations("r1", mapOf(Direction.NORTH to 3))

        // Unlock
        graph.unlockExit("r1", Direction.NORTH)
        val room = graph.getRoom("r1")!!
        assertTrue(room.lockedExits.isEmpty(), "Exit should be unlocked")

        // Tick 1
        var events = graph.tickResetTimers()
        assertTrue(events.isEmpty())

        // Tick 2
        events = graph.tickResetTimers()
        assertTrue(events.isEmpty())

        // Tick 3 — should re-lock
        events = graph.tickResetTimers()
        assertEquals(1, events.size)
        val relock = events[0] as ExitResetEvent.Relocked
        assertEquals("r1", relock.roomId)
        assertEquals(Direction.NORTH, relock.direction)
        assertEquals(15, relock.difficulty)

        val roomAfter = graph.getRoom("r1")!!
        assertEquals(15, roomAfter.lockedExits[Direction.NORTH])
    }

    @Test
    fun testHiddenExitRevealAndRehide() {
        val graph = WorldGraph()
        graph.addRoom(Room(
            "r1", "Room 1", "desc",
            mapOf(Direction.WEST to "r2"), "zone", 0, 0
        ))
        graph.addRoom(Room("r2", "Room 2", "desc", mapOf(Direction.EAST to "r1"), "zone", -1, 0))

        val hiddenData = HiddenExitData(perceptionDC = 15, hiddenResetTicks = 2)
        graph.setHiddenExitDefs("r1", mapOf(Direction.WEST to hiddenData))

        // Initially hidden
        assertTrue(graph.isExitCurrentlyHidden("r1", Direction.WEST))

        // Reveal
        graph.revealHiddenExit("r1", Direction.WEST)
        assertFalse(graph.isExitCurrentlyHidden("r1", Direction.WEST))

        // Tick 1
        var events = graph.tickResetTimers()
        assertTrue(events.isEmpty())

        // Tick 2 — should re-hide
        events = graph.tickResetTimers()
        assertEquals(1, events.size)
        val rehide = events[0] as ExitResetEvent.Rehidden
        assertEquals("r1", rehide.roomId)
        assertEquals(Direction.WEST, rehide.direction)

        // Should be hidden again
        assertTrue(graph.isExitCurrentlyHidden("r1", Direction.WEST))
    }

    @Test
    fun testPermanentReveal() {
        val graph = WorldGraph()
        graph.addRoom(Room(
            "r1", "Room 1", "desc",
            mapOf(Direction.WEST to "r2"), "zone", 0, 0
        ))
        graph.addRoom(Room("r2", "Room 2", "desc", mapOf(Direction.EAST to "r1"), "zone", -1, 0))

        val hiddenData = HiddenExitData(perceptionDC = 15, hiddenResetTicks = 0) // permanent
        graph.setHiddenExitDefs("r1", mapOf(Direction.WEST to hiddenData))

        graph.revealHiddenExit("r1", Direction.WEST)
        assertFalse(graph.isExitCurrentlyHidden("r1", Direction.WEST))

        // Tick many times — should never re-hide
        repeat(10) {
            val events = graph.tickResetTimers()
            assertTrue(events.none { it is ExitResetEvent.Rehidden })
        }
        assertFalse(graph.isExitCurrentlyHidden("r1", Direction.WEST))
    }

    @Test
    fun testHiddenLockedExitFullLifecycle() {
        val graph = WorldGraph()
        graph.addRoom(Room(
            "r1", "Room 1", "desc",
            mapOf(Direction.WEST to "r2"), "zone", 0, 0,
            lockedExits = mapOf(Direction.WEST to 12)
        ))
        graph.addRoom(Room("r2", "Room 2", "desc", mapOf(Direction.EAST to "r1"), "zone", -1, 0))

        val hiddenData = HiddenExitData(
            perceptionDC = 18,
            lockDifficulty = 12,
            hiddenResetTicks = 4,
            lockResetTicks = 2
        )
        graph.setHiddenExitDefs("r1", mapOf(Direction.WEST to hiddenData))
        graph.setOriginalLockedExits("r1", mapOf(Direction.WEST to 12))

        // Reveal hidden exit
        graph.revealHiddenExit("r1", Direction.WEST)
        assertFalse(graph.isExitCurrentlyHidden("r1", Direction.WEST))

        // Unlock the lock
        graph.unlockExit("r1", Direction.WEST)
        assertTrue(graph.getRoom("r1")!!.lockedExits.isEmpty())

        // Tick 1 — nothing yet
        var events = graph.tickResetTimers()
        assertTrue(events.isEmpty())

        // Tick 2 — lock re-locks (lockResetTicks=2)
        events = graph.tickResetTimers()
        assertEquals(1, events.size)
        assertTrue(events[0] is ExitResetEvent.Relocked)
        assertEquals(12, graph.getRoom("r1")!!.lockedExits[Direction.WEST])

        // Tick 3 — nothing
        events = graph.tickResetTimers()
        assertTrue(events.isEmpty())

        // Tick 4 — hidden exit re-hides (hiddenResetTicks=4)
        events = graph.tickResetTimers()
        assertEquals(1, events.size)
        assertTrue(events[0] is ExitResetEvent.Rehidden)
        assertTrue(graph.isExitCurrentlyHidden("r1", Direction.WEST))
    }

    @Test
    fun testIsExitCurrentlyHiddenForNonHiddenExit() {
        val graph = buildTestWorld()
        assertFalse(graph.isExitCurrentlyHidden("town:square", Direction.NORTH))
    }

    // --- Z-plane filtering tests ---

    private fun buildMultiLevelWorld(): WorldGraph {
        val graph = WorldGraph()
        // Surface (z=0)
        graph.addRoom(Room("zone:surface_a", "Surface A", "desc",
            mapOf(Direction.EAST to "zone:surface_b", Direction.DOWN to "zone:underground_a"), "zone", 0, 0))
        graph.addRoom(Room("zone:surface_b", "Surface B", "desc",
            mapOf(Direction.WEST to "zone:surface_a", Direction.UP to "zone:upper_a"), "zone", 1, 0))
        // Underground (z=-1)
        graph.addRoom(Room("zone:underground_a", "Underground A", "desc",
            mapOf(Direction.UP to "zone:surface_a", Direction.EAST to "zone:underground_b"), "zone", 0, 0, z = -1))
        graph.addRoom(Room("zone:underground_b", "Underground B", "desc",
            mapOf(Direction.WEST to "zone:underground_a"), "zone", 1, 0, z = -1))
        // Upper level (z=1)
        graph.addRoom(Room("zone:upper_a", "Upper A", "desc",
            mapOf(Direction.DOWN to "zone:surface_b"), "zone", 1, 0, z = 1))
        graph.setDefaultSpawn("zone:surface_a")
        return graph
    }

    @Test
    fun testGetRoomsNearFiltersToSameZLevel() {
        val graph = buildMultiLevelWorld()
        val nearby = graph.getRoomsNear("zone:surface_a", radius = 5)
        val ids = nearby.map { it.id }.toSet()

        assertTrue("zone:surface_a" in ids, "Should include center room")
        assertTrue("zone:surface_b" in ids, "Should include same-z neighbor")
        assertFalse("zone:underground_a" in ids, "Should NOT include z=-1 room")
        assertFalse("zone:underground_b" in ids, "Should NOT include z=-1 room")
        assertFalse("zone:upper_a" in ids, "Should NOT include z=1 room")
        assertEquals(2, nearby.size)
    }

    @Test
    fun testGetRoomsNearFromUnderground() {
        val graph = buildMultiLevelWorld()
        val nearby = graph.getRoomsNear("zone:underground_a", radius = 5)
        val ids = nearby.map { it.id }.toSet()

        assertTrue("zone:underground_a" in ids)
        assertTrue("zone:underground_b" in ids)
        assertFalse("zone:surface_a" in ids, "Should NOT include z=0 room")
        assertFalse("zone:surface_b" in ids, "Should NOT include z=0 room")
        assertEquals(2, nearby.size)
    }

    @Test
    fun testGetRoomsNearFromUpperLevel() {
        val graph = buildMultiLevelWorld()
        val nearby = graph.getRoomsNear("zone:upper_a", radius = 5)
        val ids = nearby.map { it.id }.toSet()

        assertTrue("zone:upper_a" in ids)
        assertFalse("zone:surface_b" in ids, "Should NOT include z=0 room")
        assertEquals(1, nearby.size)
    }

    @Test
    fun testGetRoomsNearPreservesVerticalExits() {
        val graph = buildMultiLevelWorld()
        val nearby = graph.getRoomsNear("zone:surface_a", radius = 5)
        val surfaceA = nearby.find { it.id == "zone:surface_a" }!!

        assertTrue(Direction.DOWN in surfaceA.exits, "DOWN exit should be in exits map for indicator rendering")
        assertEquals("zone:underground_a", surfaceA.exits[Direction.DOWN])
    }
}
