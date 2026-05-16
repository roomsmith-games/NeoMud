package com.neomud.server.world

import com.neomud.server.defaultWorldSource
import com.neomud.shared.model.Direction
import com.neomud.shared.model.Direction.*
import kotlin.test.Test
import kotlin.test.assertTrue

class WorldMapIntegrityTest {

    private val dx = mapOf(
        NORTH to 0, SOUTH to 0, EAST to 1, WEST to -1,
        NORTHEAST to 1, NORTHWEST to -1, SOUTHEAST to 1, SOUTHWEST to -1,
        UP to 0, DOWN to 0,
    )
    private val dy = mapOf(
        NORTH to 1, SOUTH to -1, EAST to 0, WEST to 0,
        NORTHEAST to 1, NORTHWEST to 1, SOUTHEAST to -1, SOUTHWEST to -1,
        UP to 0, DOWN to 0,
    )
    private val dz = mapOf(
        NORTH to 0, SOUTH to 0, EAST to 0, WEST to 0,
        NORTHEAST to 0, NORTHWEST to 0, SOUTHEAST to 0, SOUTHWEST to 0,
        UP to 1, DOWN to -1,
    )
    private val opposites = mapOf(
        NORTH to SOUTH, SOUTH to NORTH, EAST to WEST, WEST to EAST,
        NORTHEAST to SOUTHWEST, SOUTHWEST to NORTHEAST,
        NORTHWEST to SOUTHEAST, SOUTHEAST to NORTHWEST,
        UP to DOWN, DOWN to UP,
    )

    private fun load() = WorldLoader.load(defaultWorldSource())

    @Test
    fun testNoDanglingExits() {
        val world = load().worldGraph
        val allRooms = world.getAllRooms()
        val roomIds = allRooms.map { it.id }.toSet()
        val dangling = mutableListOf<String>()

        for (room in allRooms) {
            for ((dir, targetId) in room.exits) {
                if (targetId !in roomIds) {
                    dangling.add("${room.id} --$dir--> $targetId")
                }
            }
        }

        assertTrue(dangling.isEmpty(), "Dangling exits found:\n${dangling.joinToString("\n")}")
    }

    @Test
    fun testNoDuplicateRoomIds() {
        val world = load().worldGraph
        val allRooms = world.getAllRooms()
        val seen = mutableMapOf<String, Int>()
        for (room in allRooms) {
            seen[room.id] = (seen[room.id] ?: 0) + 1
        }
        val dupes = seen.filter { it.value > 1 }
        assertTrue(dupes.isEmpty(), "Duplicate room IDs: ${dupes.keys}")
    }

    @Test
    fun testReciprocalExits() {
        val world = load().worldGraph
        val allRooms = world.getAllRooms()
        val roomMap = allRooms.associateBy { it.id }
        val missing = mutableListOf<String>()

        for (room in allRooms) {
            for ((dir, targetId) in room.exits) {
                val target = roomMap[targetId] ?: continue
                val hasAnyReturn = target.exits.values.contains(room.id)
                if (!hasAnyReturn) {
                    missing.add("${room.id} --$dir--> $targetId has no return exit")
                }
            }
        }

        assertTrue(missing.isEmpty(), "Missing reciprocal exits:\n${missing.joinToString("\n")}")
    }

    @Test
    fun testReciprocalDirections() {
        val world = load().worldGraph
        val allRooms = world.getAllRooms()
        val roomMap = allRooms.associateBy { it.id }
        val wrong = mutableListOf<String>()

        for (room in allRooms) {
            for ((dir, targetId) in room.exits) {
                val target = roomMap[targetId] ?: continue
                val expectedReturn = opposites[dir] ?: continue
                val returnDirs = target.exits.filter { it.value == room.id }.keys
                if (returnDirs.isNotEmpty() && expectedReturn !in returnDirs) {
                    wrong.add("${room.id} --$dir--> $targetId returns via $returnDirs (expected $expectedReturn)")
                }
            }
        }

        assertTrue(wrong.isEmpty(), "Wrong reciprocal directions:\n${wrong.joinToString("\n")}")
    }

    // Portal exits where direction doesn't match coordinate delta (magical teleport)
    private val portalExits = setOf(
        "first_seal:watcher_perch" to "glass_desert:spire_base",
        "glass_desert:spire_base" to "first_seal:watcher_perch",
    )

    @Test
    fun testGlobalCoordinateAlignment() {
        val world = load().worldGraph
        val allRooms = world.getAllRooms()
        val roomMap = allRooms.associateBy { it.id }
        val mismatches = mutableListOf<String>()

        for (room in allRooms) {
            for ((dir, targetId) in room.exits) {
                val target = roomMap[targetId] ?: continue
                if ((room.id to targetId) in portalExits) continue
                val expectedX = room.x + (dx[dir] ?: 0)
                val expectedY = room.y + (dy[dir] ?: 0)
                val expectedZ = room.z + (dz[dir] ?: 0)
                if (target.x != expectedX || target.y != expectedY || target.z != expectedZ) {
                    mismatches.add(
                        "${room.id} --$dir--> $targetId: " +
                            "expected (${expectedX},${expectedY},${expectedZ}) " +
                            "got (${target.x},${target.y},${target.z})"
                    )
                }
            }
        }

        assertTrue(
            mismatches.isEmpty(),
            "Global coordinate mismatches:\n${mismatches.joinToString("\n")}"
        )
    }

    @Test
    fun testGlobalCoordinateUniqueness() {
        val world = load().worldGraph
        val allRooms = world.getAllRooms()
        val overlaps = mutableListOf<String>()

        val byCoord = allRooms.groupBy { Triple(it.x, it.y, it.z) }
        for ((coord, rooms) in byCoord) {
            if (rooms.size <= 1) continue
            val ids = rooms.map { it.id }
            val zones = rooms.map { it.zoneId }.toSet()
            overlaps.add("(${coord.first},${coord.second},${coord.third}) [${zones.joinToString(",")}]: ${ids.joinToString(", ")}")
        }

        assertTrue(
            overlaps.isEmpty(),
            "Global coordinate collisions found:\n${overlaps.joinToString("\n")}"
        )
    }
}
