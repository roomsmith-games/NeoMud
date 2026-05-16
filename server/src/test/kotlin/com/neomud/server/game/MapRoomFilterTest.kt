package com.neomud.server.game

import com.neomud.server.game.npc.NpcManager
import com.neomud.server.session.SessionManager
import com.neomud.server.world.HiddenExitData
import com.neomud.server.world.NpcData
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.Direction
import com.neomud.shared.model.MapRoom
import com.neomud.shared.model.Room
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapRoomFilterTest {

    private fun buildGraph(): WorldGraph {
        val graph = WorldGraph()
        graph.addRoom(Room(
            "r1", "Room 1", "desc",
            mapOf(Direction.NORTH to "r2", Direction.EAST to "r3", Direction.UP to "r4"),
            "zone", 0, 0,
            lockedExits = mapOf(Direction.EAST to 12)
        ))
        graph.addRoom(Room("r2", "Room 2", "desc", mapOf(Direction.SOUTH to "r1"), "zone", 0, 1))
        graph.addRoom(Room(
            "r3", "Room 3", "desc",
            mapOf(Direction.WEST to "r1"),
            "zone", 1, 0,
            lockedExits = mapOf(Direction.WEST to 10)
        ))
        graph.addRoom(Room("r4", "Above Room 1", "desc", mapOf(Direction.DOWN to "r1"), "zone", 0, 0, z = 1))
        // r1's EAST exit is also hidden
        graph.setHiddenExitDefs("r1", mapOf(Direction.EAST to HiddenExitData(perceptionDC = 15, lockDifficulty = 12)))
        return graph
    }

    /**
     * Minimal session stub that mirrors PlayerSession's discovery methods.
     * Uses the same key format as the real implementation.
     */
    private class FakeSession {
        val discoveredHiddenExits = mutableSetOf<String>()
        val discoveredLockedExits = mutableSetOf<String>()

        fun hasDiscoveredExit(roomId: String, direction: Direction): Boolean =
            "$roomId:$direction" in discoveredHiddenExits

        fun discoverExit(roomId: String, direction: Direction) {
            discoveredHiddenExits.add("$roomId:$direction")
        }

        fun hasDiscoveredLock(roomId: String, direction: Direction): Boolean =
            "$roomId:$direction" in discoveredLockedExits

        fun discoverLock(roomId: String, direction: Direction) {
            discoveredLockedExits.add("$roomId:$direction")
        }
    }

    /**
     * Runs the same enrichment algorithm as MapRoomFilter.enrichForPlayer
     * but using a FakeSession (avoids needing a WebSocketSession).
     */
    private fun enrichForFake(
        mapRooms: List<MapRoom>,
        fake: FakeSession,
        worldGraph: WorldGraph,
        sessionManager: SessionManager,
        npcManager: NpcManager
    ): List<MapRoom> = mapRooms.map { mapRoom ->
        val room = worldGraph.getRoom(mapRoom.id)
        val hiddenDefs = worldGraph.getHiddenExitDefs(mapRoom.id)

        val visibleExits = if (hiddenDefs.isEmpty()) mapRoom.exits else mapRoom.exits.filter { (dir, _) ->
            dir !in hiddenDefs || fake.hasDiscoveredExit(mapRoom.id, dir)
        }

        val locked = room?.lockedExits?.keys?.filter {
            it in visibleExits && fake.hasDiscoveredLock(mapRoom.id, it)
        }?.toSet() ?: emptySet()

        val hidden = hiddenDefs.keys.filter {
            fake.hasDiscoveredExit(mapRoom.id, it) && it in visibleExits
        }.toSet()

        mapRoom.copy(
            exits = visibleExits,
            hasPlayers = sessionManager.getPlayerNamesInRoom(mapRoom.id).isNotEmpty(),
            hasNpcs = npcManager.getNpcsInRoom(mapRoom.id).any { !it.hostile },
            lockedExits = locked,
            hiddenExits = hidden
        )
    }

    @Test
    fun testUndiscoveredHiddenExitFilteredFromMap() {
        val graph = buildGraph()
        val session = FakeSession()
        val sm = SessionManager()
        val nm = NpcManager(graph)

        val rawRooms = graph.getRoomsNear("r1")
        val enriched = enrichForFake(rawRooms, session, graph, sm, nm)
        val r1 = enriched.find { it.id == "r1" }!!

        // EAST is hidden and not discovered, so should be filtered out
        assertFalse(Direction.EAST in r1.exits, "Undiscovered hidden exit EAST should be filtered")
        assertTrue(Direction.NORTH in r1.exits, "NORTH should still be visible")
        assertTrue(Direction.UP in r1.exits, "UP should still be visible")
    }

    @Test
    fun testDiscoveredHiddenExitVisibleOnMap() {
        val graph = buildGraph()
        val session = FakeSession()
        session.discoverExit("r1", Direction.EAST)
        val sm = SessionManager()
        val nm = NpcManager(graph)

        val rawRooms = graph.getRoomsNear("r1")
        val enriched = enrichForFake(rawRooms, session, graph, sm, nm)
        val r1 = enriched.find { it.id == "r1" }!!

        assertTrue(Direction.EAST in r1.exits, "Discovered hidden exit EAST should be visible")
        assertTrue(Direction.EAST in r1.hiddenExits, "EAST should be marked as hidden exit")
    }

    @Test
    fun testLockedExitNotShownUntilDiscovered() {
        val graph = buildGraph()
        val session = FakeSession()
        val sm = SessionManager()
        val nm = NpcManager(graph)

        val rawRooms = graph.getRoomsNear("r3")
        val enriched = enrichForFake(rawRooms, session, graph, sm, nm)
        val r3 = enriched.find { it.id == "r3" }!!

        // r3 has a locked WEST exit, but player hasn't discovered the lock
        assertTrue(r3.lockedExits.isEmpty(), "Lock should not show until discovered")
    }

    @Test
    fun testLockedExitShownAfterDiscovery() {
        val graph = buildGraph()
        val session = FakeSession()
        session.discoverLock("r3", Direction.WEST)
        val sm = SessionManager()
        val nm = NpcManager(graph)

        val rawRooms = graph.getRoomsNear("r3")
        val enriched = enrichForFake(rawRooms, session, graph, sm, nm)
        val r3 = enriched.find { it.id == "r3" }!!

        assertTrue(Direction.WEST in r3.lockedExits, "Discovered lock should show on map")
    }

    @Test
    fun testHiddenLockedExitRequiresBothDiscoveries() {
        val graph = buildGraph()
        val session = FakeSession()
        val sm = SessionManager()
        val nm = NpcManager(graph)

        // r1's EAST is both hidden and locked
        // Discover only the lock, not the hidden exit
        session.discoverLock("r1", Direction.EAST)

        val rawRooms = graph.getRoomsNear("r1")
        val enriched = enrichForFake(rawRooms, session, graph, sm, nm)
        val r1 = enriched.find { it.id == "r1" }!!

        // Exit is still hidden (not discovered as hidden), so it shouldn't appear at all
        assertFalse(Direction.EAST in r1.exits, "Hidden exit should still be filtered")
        assertTrue(r1.lockedExits.isEmpty(), "Lock on invisible exit shouldn't show")

        // Now discover the hidden exit too
        session.discoverExit("r1", Direction.EAST)
        val enriched2 = enrichForFake(rawRooms, session, graph, sm, nm)
        val r1b = enriched2.find { it.id == "r1" }!!

        assertTrue(Direction.EAST in r1b.exits, "Exit should now be visible")
        assertTrue(Direction.EAST in r1b.hiddenExits, "Should be marked as hidden")
        assertTrue(Direction.EAST in r1b.lockedExits, "Should be marked as locked")
    }

    @Test
    fun testUpDownExitsPreservedButTargetFilteredByZLevel() {
        val graph = buildGraph()
        val session = FakeSession()
        val sm = SessionManager()
        val nm = NpcManager(graph)

        val rawRooms = graph.getRoomsNear("r1")
        val enriched = enrichForFake(rawRooms, session, graph, sm, nm)
        val r1 = enriched.find { it.id == "r1" }!!

        assertTrue(Direction.UP in r1.exits, "UP exit should be preserved for indicator rendering")
        assertFalse(enriched.any { it.id == "r4" }, "Room at different z-level should be filtered out of minimap")
    }

    @Test
    fun testHostileNpcsNotShownOnMinimap() {
        val graph = buildGraph()
        val session = FakeSession()
        val sm = SessionManager()
        val nm = NpcManager(graph)

        // Spawn a hostile NPC in r1
        val hostileNpc = NpcData(
            id = "npc:wolf",
            name = "Wolf",
            description = "A snarling wolf",
            startRoomId = "r1",
            behaviorType = "wander",
            hostile = true,
            maxHp = 50,
            damage = 5
        )
        nm.loadNpcs(listOf(hostileNpc to "zone"))

        val rawRooms = graph.getRoomsNear("r1")
        val enriched = enrichForFake(rawRooms, session, graph, sm, nm)
        val r1 = enriched.find { it.id == "r1" }!!

        assertFalse(r1.hasNpcs, "Hostile NPCs should not show on minimap")
    }

    @Test
    fun testFriendlyNpcsShownOnMinimap() {
        val graph = buildGraph()
        val session = FakeSession()
        val sm = SessionManager()
        val nm = NpcManager(graph)

        // Spawn a vendor (non-hostile) NPC in r1
        val vendorNpc = NpcData(
            id = "npc:shopkeeper",
            name = "Shopkeeper",
            description = "A friendly shopkeeper",
            startRoomId = "r1",
            behaviorType = "vendor",
            hostile = false,
            maxHp = 100,
            damage = 0
        )
        nm.loadNpcs(listOf(vendorNpc to "zone"))

        val rawRooms = graph.getRoomsNear("r1")
        val enriched = enrichForFake(rawRooms, session, graph, sm, nm)
        val r1 = enriched.find { it.id == "r1" }!!

        assertTrue(r1.hasNpcs, "Friendly NPCs (vendors/trainers) should show on minimap")
    }

    @Test
    fun testMixedNpcsOnlyShowsFriendly() {
        val graph = buildGraph()
        val session = FakeSession()
        val sm = SessionManager()
        val nm = NpcManager(graph)

        // Spawn both hostile and friendly NPCs in r1
        val hostileNpc = NpcData(
            id = "npc:wolf",
            name = "Wolf",
            description = "A snarling wolf",
            startRoomId = "r1",
            behaviorType = "wander",
            hostile = true,
            maxHp = 50,
            damage = 5
        )
        val trainerNpc = NpcData(
            id = "npc:trainer",
            name = "Trainer",
            description = "A combat trainer",
            startRoomId = "r1",
            behaviorType = "trainer",
            hostile = false,
            maxHp = 200,
            damage = 0
        )
        nm.loadNpcs(listOf(hostileNpc to "zone", trainerNpc to "zone"))

        val rawRooms = graph.getRoomsNear("r1")
        val enriched = enrichForFake(rawRooms, session, graph, sm, nm)
        val r1 = enriched.find { it.id == "r1" }!!

        assertTrue(r1.hasNpcs, "Room with friendly NPC should show hasNpcs even if hostile NPCs also present")
    }

    @Test
    fun testRoomWithNoHiddenOrLockedExitsClean() {
        val graph = buildGraph()
        val session = FakeSession()
        val sm = SessionManager()
        val nm = NpcManager(graph)

        val rawRooms = graph.getRoomsNear("r2")
        val enriched = enrichForFake(rawRooms, session, graph, sm, nm)
        val r2 = enriched.find { it.id == "r2" }!!

        assertEquals(1, r2.exits.size)
        assertTrue(Direction.SOUTH in r2.exits)
        assertTrue(r2.lockedExits.isEmpty())
        assertTrue(r2.hiddenExits.isEmpty())
    }
}
