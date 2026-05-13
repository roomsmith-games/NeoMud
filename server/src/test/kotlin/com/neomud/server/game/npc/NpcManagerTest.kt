package com.neomud.server.game.npc

import com.neomud.server.defaultWorldSource
import com.neomud.server.game.MovementTrailManager
import com.neomud.server.game.TrailEntry
import com.neomud.server.game.npc.behavior.PatrolBehavior
import com.neomud.server.game.npc.behavior.PursuitBehavior
import com.neomud.server.game.npc.behavior.WanderBehavior
import com.neomud.server.session.SessionManager
import com.neomud.server.world.NpcData
import com.neomud.server.world.SpawnConfig
import com.neomud.server.world.WorldGraph
import com.neomud.server.world.WorldLoader
import com.neomud.shared.model.Direction
import com.neomud.shared.model.Room
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NpcManagerTest {

    private fun load() = WorldLoader.load(defaultWorldSource())

    @Test
    fun testNpcsAliveAfterLoad() {
        val result = load()
        val npcManager = NpcManager(result.worldGraph)
        npcManager.loadNpcs(result.npcDataList)

        // Town guard starts at town:gate - should be visible (non-combat, maxHp=0)
        val gateNpcs = npcManager.getNpcsInRoom("town:gate")
        val guard = gateNpcs.find { it.id == "npc:town_guard" }
        assertNotNull(guard, "Town Guard should be alive and visible at town:gate")

        // Barkeep starts at town:tavern - non-combat, maxHp=0
        val tavernNpcs = npcManager.getNpcsInRoom("town:tavern")
        val barkeep = tavernNpcs.find { it.id == "npc:barkeep" }
        assertNotNull(barkeep, "Barkeep should be alive and visible at town:tavern")
    }

    @Test
    fun testHostileNpcsAliveAfterLoad() {
        val result = load()
        val npcManager = NpcManager(result.worldGraph)
        npcManager.loadNpcs(result.npcDataList)

        // Hostile NPCs start in forest rooms with maxHp > 0
        val hostiles = npcManager.getLivingHostileNpcsInRoom("forest:path") +
                npcManager.getLivingHostileNpcsInRoom("forest:clearing") +
                npcManager.getLivingHostileNpcsInRoom("forest:deep") +
                npcManager.getLivingHostileNpcsInRoom("forest:edge") +
                npcManager.getLivingHostileNpcsInRoom("forest:stream")

        assertTrue(hostiles.isNotEmpty(), "Should have hostile NPCs alive in the forest")
        for (npc in hostiles) {
            assertTrue(npc.currentHp > 0, "${npc.name} should have HP > 0, got ${npc.currentHp}")
            assertTrue(npc.maxHp > 0, "${npc.name} should have maxHp > 0, got ${npc.maxHp}")
        }
    }

    @Test
    fun testMarkDeadFiltersNpc() {
        val result = load()
        val npcManager = NpcManager(result.worldGraph)
        npcManager.loadNpcs(result.npcDataList)

        // Find the shadow wolf's starting room
        val wolf = result.npcDataList.find { it.first.id == "npc:shadow_wolf" }!!
        val wolfRoom = wolf.first.startRoomId

        val before = npcManager.getNpcsInRoom(wolfRoom)
        assertTrue(before.any { it.id == "npc:shadow_wolf" }, "Wolf should be visible before death")

        npcManager.markDead("npc:shadow_wolf")

        val after = npcManager.getNpcsInRoom(wolfRoom)
        assertTrue(after.none { it.id == "npc:shadow_wolf" }, "Wolf should be gone after death")

        // Non-combat NPCs should still be unaffected
        val tavernNpcs = npcManager.getNpcsInRoom("town:tavern")
        assertTrue(tavernNpcs.any { it.id == "npc:barkeep" }, "Barkeep should still be visible")
    }

    // --- Player detection suppresses hostile NPC wander/patrol ---

    private fun buildSimpleWorld(): WorldGraph {
        val world = WorldGraph()
        world.addRoom(Room("forest:a", "Room A", "A room.", mapOf(Direction.EAST to "forest:b"), "forest", 0, 0))
        world.addRoom(Room("forest:b", "Room B", "B room.", mapOf(Direction.WEST to "forest:a"), "forest", 1, 0))
        return world
    }

    private fun buildNpcManager(world: WorldGraph, vararg npcs: NpcState): NpcManager {
        val manager = NpcManager(world)
        // Use reflection-free approach: load via tick observation
        // We need to access the internal list, so use loadNpcs with NpcData
        // Instead, let's just verify via the public tick() API with a loaded world
        return manager
    }

    @Test
    fun testHostileNpcStaysWhenPlayerDetected() {
        val result = load()
        val npcManager = NpcManager(result.worldGraph)
        npcManager.loadNpcs(result.npcDataList)

        // Find a hostile wandering NPC and its room
        val hostileNpcs = result.npcDataList.filter { it.first.hostile }
        assertTrue(hostileNpcs.isNotEmpty(), "Should have hostile NPCs for testing")

        val testNpc = hostileNpcs.first()
        val npcRoom = testNpc.first.startRoomId

        // Tick many times with the NPC's room containing a visible player
        // The hostile NPC should never generate a movement event
        val roomsWithPlayers = setOf(npcRoom)
        var movedAway = false
        repeat(50) {
            val events = npcManager.tick(roomsWithPlayers)
            if (events.any { it.npcId == testNpc.first.id && it.fromRoomId == npcRoom }) {
                movedAway = true
            }
        }
        assertTrue(!movedAway, "Hostile NPC should not wander away from a room with a visible player")
    }

    @Test
    fun testHostileNpcWandersWhenNoPlayerPresent() {
        val result = load()
        val npcManager = NpcManager(result.worldGraph)
        npcManager.loadNpcs(result.npcDataList)

        // Tick many times with NO rooms containing players
        var movedAtLeastOnce = false
        repeat(50) {
            val events = npcManager.tick(emptySet())
            if (events.any { it.fromRoomId != null }) {
                movedAtLeastOnce = true
            }
        }
        assertTrue(movedAtLeastOnce, "NPCs should wander normally when no players are present")
    }

    @Test
    fun testRoomMaxHostileNpcsBlocksWander() {
        // Build a 3-room world: A <-> B <-> C, all in zone "test"
        val world = WorldGraph()
        world.addRoom(Room("test:a", "A", "", mapOf(Direction.EAST to "test:b"), "test", 0, 0))
        world.addRoom(Room("test:b", "B", "", mapOf(Direction.WEST to "test:a", Direction.EAST to "test:c"), "test", 1, 0))
        world.addRoom(Room("test:c", "C", "", mapOf(Direction.WEST to "test:b"), "test", 2, 0))

        // Room B has a per-room cap of 1 hostile NPC
        val roomCaps = mapOf("test:b" to 1)
        val zoneConfigs = mapOf("test" to SpawnConfig(maxEntities = 10, maxPerRoom = 5, rateTicks = 0))
        val manager = NpcManager(world, zoneConfigs, roomCaps)

        // Load two hostile wanderers, one starting in room A, one in room C
        val wolf = NpcData("npc:wolf", "Wolf", "", startRoomId = "test:a", behaviorType = "wander", hostile = true, maxHp = 20, damage = 3)
        val rat = NpcData("npc:rat", "Rat", "", startRoomId = "test:c", behaviorType = "wander", hostile = true, maxHp = 10, damage = 1)
        manager.loadNpcs(listOf(wolf to "test", rat to "test"))

        // Tick many times — room B should never have more than 1 hostile NPC
        repeat(200) {
            manager.tick()
            val inB = manager.getLivingHostileNpcsInRoom("test:b")
            assertTrue(inB.size <= 1, "Room B should have at most 1 hostile NPC (room cap), but had ${inB.size}")
        }
    }

    @Test
    fun testRoomMaxHostileNpcsBlocksSpawn() {
        val world = WorldGraph()
        world.addRoom(Room("test:a", "A", "", emptyMap(), "test", 0, 0))

        // Room cap of 1, zone allows many
        val roomCaps = mapOf("test:a" to 1)
        val zoneConfigs = mapOf("test" to SpawnConfig(maxEntities = 10, maxPerRoom = 5, rateTicks = 1))
        val manager = NpcManager(world, zoneConfigs, roomCaps)

        // One hostile NPC that spawns at test:a
        val rat = NpcData("npc:rat", "Rat", "", startRoomId = "test:a", behaviorType = "idle", hostile = true, maxHp = 10, damage = 1)
        manager.loadNpcs(listOf(rat to "test"))

        // Tick many times — spawner should not push past the room cap of 1
        repeat(50) {
            manager.tick()
            val inA = manager.getLivingHostileNpcsInRoom("test:a")
            assertTrue(inA.size <= 1, "Room A should have at most 1 hostile NPC (room cap), but had ${inA.size}")
        }
    }

    @Test
    fun testRoomMaxHostileNpcsFallsBackToZoneDefault() {
        val world = WorldGraph()
        world.addRoom(Room("test:a", "A", "", emptyMap(), "test", 0, 0))

        // No room-level cap, zone default is 2
        val zoneConfigs = mapOf("test" to SpawnConfig(maxEntities = 10, maxPerRoom = 2, rateTicks = 1))
        val manager = NpcManager(world, zoneConfigs)

        val rat = NpcData("npc:rat", "Rat", "", startRoomId = "test:a", behaviorType = "idle", hostile = true, maxHp = 10, damage = 1)
        manager.loadNpcs(listOf(rat to "test"))

        // Tick many times — zone default should cap at 2
        repeat(50) {
            manager.tick()
            val inA = manager.getLivingHostileNpcsInRoom("test:a")
            assertTrue(inA.size <= 2, "Room A should have at most 2 hostile NPCs (zone default), but had ${inA.size}")
        }
    }

    @Test
    fun testForestEdgeHasRoomCapInDefaultWorld() {
        val result = load()
        val npcManager = NpcManager(result.worldGraph, result.zoneSpawnConfigs, result.roomMaxHostileNpcs)
        npcManager.loadNpcs(result.npcDataList)

        // forest:edge should have maxHostileNpcs = 1 in the default world
        assertEquals(1, result.roomMaxHostileNpcs["forest:edge"], "forest:edge should have a room-level cap of 1")
    }

    @Test
    fun testNonHostileNpcStillMovesWithPlayerPresent() {
        val result = load()
        val npcManager = NpcManager(result.worldGraph)
        npcManager.loadNpcs(result.npcDataList)

        // Town guard is non-hostile and patrols — should still move even with player in room
        val guardRoom = "town:gate"
        val roomsWithPlayers = setOf(guardRoom)
        var guardMoved = false
        repeat(50) {
            val events = npcManager.tick(roomsWithPlayers)
            if (events.any { it.npcName == "Town Guard" && it.fromRoomId != null }) {
                guardMoved = true
            }
        }
        assertTrue(guardMoved, "Non-hostile NPCs should still patrol even when players are present")
    }

    @Test
    fun testHostilePursuitNpcSuppressedByPlayerPresence() {
        // An NPC actively in pursuit should be suppressed when a player is visible in its room
        val world = WorldGraph()
        world.addRoom(Room("test:a", "A", "", mapOf(Direction.EAST to "test:b"), "test", 0, 0))
        world.addRoom(Room("test:b", "B", "", mapOf(Direction.WEST to "test:a"), "test", 1, 0))

        val zoneConfigs = mapOf("test" to SpawnConfig(maxEntities = 10, maxPerRoom = 5, rateTicks = 0))
        val manager = NpcManager(world, zoneConfigs)

        val npcData = NpcData(
            "npc:wolf", "Wolf", "", startRoomId = "test:a",
            behaviorType = "wander", hostile = true, maxHp = 20, damage = 5
        )
        manager.loadNpcs(listOf(npcData to "test"))

        // Switch the NPC to pursuit behavior via engagePursuit
        val sessionManager = SessionManager()
        val trailManager = MovementTrailManager()
        // Record a trail so pursuit has something to follow
        trailManager.recordTrail("test:a", TrailEntry("TestPlayer", "TestPlayer", Direction.EAST, System.currentTimeMillis(), isPlayer = true))
        manager.engagePursuit("npc:wolf", "TestPlayer", trailManager, sessionManager)

        // Verify it switched to pursuit
        val npc = manager.getLivingHostileNpcsInRoom("test:a").find { it.id == "npc:wolf" }
        assertNotNull(npc)
        assertIs<PursuitBehavior>(npc!!.behavior, "NPC should be in pursuit behavior")

        // Tick with a player visible in room A — pursuing NPC should NOT move
        val roomsWithPlayers = setOf("test:a")
        var movedAway = false
        repeat(50) {
            val events = manager.tick(roomsWithPlayers)
            if (events.any { it.npcId == "npc:wolf" && it.fromRoomId == "test:a" }) {
                movedAway = true
            }
        }
        assertFalse(movedAway, "Hostile pursuit NPC should not move when a player is visible in its room")
    }

    @Test
    fun testWanderNpcEngagesPursuit() {
        // Non-idle hostile NPCs should engage pursuit when engagePursuit is called
        val world = WorldGraph()
        world.addRoom(Room("test:a", "A", "", mapOf(Direction.EAST to "test:b"), "test", 0, 0))
        world.addRoom(Room("test:b", "B", "", mapOf(Direction.WEST to "test:a"), "test", 1, 0))

        val zoneConfigs = mapOf("test" to SpawnConfig(maxEntities = 10, maxPerRoom = 5, rateTicks = 0))
        val manager = NpcManager(world, zoneConfigs)

        val npcData = NpcData(
            "npc:bandit", "Bandit", "", startRoomId = "test:a",
            behaviorType = "wander", hostile = true, maxHp = 15, damage = 3
        )
        manager.loadNpcs(listOf(npcData to "test"))

        val npc = manager.getLivingHostileNpcsInRoom("test:a").find { it.id == "npc:bandit" }
        assertNotNull(npc)
        assertIs<WanderBehavior>(npc!!.behavior, "NPC should start with wander behavior")

        // Engage pursuit
        val sessionManager = SessionManager()
        val trailManager = MovementTrailManager()
        trailManager.recordTrail("test:a", TrailEntry("Hero", "Hero", Direction.EAST, System.currentTimeMillis(), isPlayer = true))
        manager.engagePursuit("npc:bandit", "Hero", trailManager, sessionManager)

        assertIs<PursuitBehavior>(npc.behavior, "NPC should now be in pursuit behavior")
        assertNotNull(npc.originalBehavior, "Original behavior should be saved for restoration")
        assertIs<WanderBehavior>(npc.originalBehavior, "Original behavior should be WanderBehavior")
    }

    @Test
    fun testIdleNpcDoesNotEngagePursuitOnDetection() {
        // Idle hostile NPCs (bosses) should accept engagePursuit calls (they're still hostile),
        // but MoveCommand filters them out by behaviorType — verify the NpcState field is correct
        val world = WorldGraph()
        world.addRoom(Room("test:a", "A", "", mapOf(Direction.EAST to "test:b"), "test", 0, 0))
        world.addRoom(Room("test:b", "B", "", mapOf(Direction.WEST to "test:a"), "test", 1, 0))

        val zoneConfigs = mapOf("test" to SpawnConfig(maxEntities = 10, maxPerRoom = 5, rateTicks = 0))
        val manager = NpcManager(world, zoneConfigs)

        val bossData = NpcData(
            "npc:boss", "Gorge Warden", "", startRoomId = "test:a",
            behaviorType = "idle", hostile = true, maxHp = 100, damage = 15
        )
        val wandererData = NpcData(
            "npc:wolf", "Wolf", "", startRoomId = "test:a",
            behaviorType = "wander", hostile = true, maxHp = 20, damage = 5
        )
        manager.loadNpcs(listOf(bossData to "test", wandererData to "test"))

        // Verify behaviorType is preserved on NpcState for filtering
        val allHostiles = manager.getLivingHostileNpcsInRoom("test:a")
        val boss = allHostiles.find { it.id == "npc:boss" }
        val wolf = allHostiles.find { it.id == "npc:wolf" }
        assertNotNull(boss)
        assertNotNull(wolf)
        assertEquals("idle", boss!!.behaviorType, "Boss should have idle behaviorType")
        assertEquals("wander", wolf!!.behaviorType, "Wolf should have wander behaviorType")

        // Simulate MoveCommand's detection filter: only non-idle NPCs pursue
        val detectingHostiles = manager.getLivingHostileNpcsInRoom("test:a")
            .filter { it.behaviorType != "idle" && it.originalBehavior == null }

        assertEquals(1, detectingHostiles.size, "Only non-idle NPCs should be in the detection list")
        assertEquals("npc:wolf", detectingHostiles[0].id, "Wolf should be the one detecting, not boss")
    }

    @Test
    fun testStunnedHostileNpcSuppressed() {
        // A stunned hostile NPC should not wander even without players present
        val world = WorldGraph()
        world.addRoom(Room("test:a", "A", "", mapOf(Direction.EAST to "test:b"), "test", 0, 0))
        world.addRoom(Room("test:b", "B", "", mapOf(Direction.WEST to "test:a"), "test", 1, 0))

        val zoneConfigs = mapOf("test" to SpawnConfig(maxEntities = 10, maxPerRoom = 5, rateTicks = 0))
        val manager = NpcManager(world, zoneConfigs)

        val npcData = NpcData(
            "npc:stunned_wolf", "Stunned Wolf", "", startRoomId = "test:a",
            behaviorType = "wander", hostile = true, maxHp = 20, damage = 3
        )
        manager.loadNpcs(listOf(npcData to "test"))

        // Stun the NPC
        val npc = manager.getLivingHostileNpcsInRoom("test:a").find { it.id == "npc:stunned_wolf" }
        assertNotNull(npc)
        npc!!.stunTicks = 10

        // Tick with NO players — stunned NPC should still not move
        var movedAway = false
        repeat(10) {
            val events = manager.tick(emptySet())
            if (events.any { it.npcId == "npc:stunned_wolf" && it.fromRoomId == "test:a" }) {
                movedAway = true
            }
        }
        assertTrue(!movedAway, "Stunned hostile NPC should not wander even without players present")
    }

    @Test
    fun testSpawnTimerResetsOnNpcDeath() {
        // #224: When an NPC dies, the zone spawn timer should reset to 0, preventing
        // instant respawns when the timer was about to fire
        val world = WorldGraph()
        world.addRoom(Room("test:a", "A", "", emptyMap(), "test", 0, 0))

        // rateTicks = 5 means a spawn every 5 ticks
        val zoneConfigs = mapOf("test" to SpawnConfig(maxEntities = 5, maxPerRoom = 5, rateTicks = 5))
        val manager = NpcManager(world, zoneConfigs)

        val rat = NpcData("npc:rat", "Rat", "", startRoomId = "test:a", behaviorType = "idle", hostile = true, maxHp = 10, damage = 1)
        manager.loadNpcs(listOf(rat to "test"))

        // Tick 4 times to get the timer close to firing (4 of 5 ticks elapsed)
        repeat(4) { manager.tick() }

        // Kill the original NPC (and any spawned copies)
        val allNpcs = manager.getLivingHostileNpcsInRoom("test:a")
        for (npc in allNpcs) {
            manager.markDead(npc.id)
        }

        // Next tick should clean up the dead NPC AND reset the spawn timer to 0
        manager.tick()

        // Count NPCs — we should NOT see an immediate respawn because the timer was reset
        val npcsAfterDeath = manager.getLivingHostileNpcsInRoom("test:a")

        // Tick one more time — still shouldn't have spawned (only 1 tick since reset, need 5)
        manager.tick()
        val npcsOneTick = manager.getLivingHostileNpcsInRoom("test:a")
        assertTrue(npcsOneTick.size <= 1,
            "NPC should not respawn immediately after death (timer should have reset). Had ${npcsOneTick.size} NPCs")
    }

    @Test
    fun testRoomMaxHostileNpcsZeroBlocksAllHostiles() {
        // A room with maxHostileNpcs=0 should block ALL hostile NPCs from wandering in or spawning
        val world = WorldGraph()
        world.addRoom(Room("test:a", "A", "", mapOf(Direction.EAST to "test:safe"), "test", 0, 0))
        world.addRoom(Room("test:safe", "Safe", "", mapOf(Direction.WEST to "test:a"), "test", 1, 0))

        val roomCaps = mapOf("test:safe" to 0)
        val zoneConfigs = mapOf("test" to SpawnConfig(maxEntities = 10, maxPerRoom = 5, rateTicks = 1))
        val manager = NpcManager(world, zoneConfigs, roomCaps)

        val wolf = NpcData("npc:wolf", "Wolf", "", startRoomId = "test:a", behaviorType = "wander", hostile = true, maxHp = 20, damage = 3)
        manager.loadNpcs(listOf(wolf to "test"))

        repeat(200) {
            manager.tick()
            val inSafe = manager.getLivingHostileNpcsInRoom("test:safe")
            assertTrue(inSafe.isEmpty(), "Room with maxHostileNpcs=0 should have no hostile NPCs, but had ${inSafe.size}")
        }
    }

    @Test
    fun testHostileNpcCannotWanderIntoFriendlyNpcRoom() {
        val world = WorldGraph()
        world.addRoom(Room("test:a", "A", "", mapOf(Direction.EAST to "test:b"), "test", 0, 0))
        world.addRoom(Room("test:b", "B", "", mapOf(Direction.WEST to "test:a"), "test", 1, 0))

        val zoneConfigs = mapOf("test" to SpawnConfig(maxEntities = 10, maxPerRoom = 5, rateTicks = 0))
        val manager = NpcManager(world, zoneConfigs)

        val friendlyData = NpcData(
            "npc:quest_giver", "Quest Giver", "", startRoomId = "test:b",
            behaviorType = "idle", hostile = false, maxHp = 0, damage = 0
        )
        val hostileData = NpcData(
            "npc:wolf", "Wolf", "", startRoomId = "test:a",
            behaviorType = "wander", hostile = true, maxHp = 30, damage = 5
        )
        manager.loadNpcs(listOf(friendlyData to "test", hostileData to "test"))

        // Run many ticks — the wolf should never enter test:b (friendly NPC room)
        repeat(200) { manager.tick() }

        val friendlyRoomHostiles = manager.getLivingHostileNpcsInRoom("test:b")
        assertTrue(friendlyRoomHostiles.isEmpty(),
            "Hostile NPC should not wander into room with friendly NPC")
    }

    @Test
    fun testVendorRoomBlocksHostilesInDefaultWorld() {
        val result = load()
        val npcManager = NpcManager(result.worldGraph, result.zoneSpawnConfigs, result.roomMaxHostileNpcs)
        npcManager.loadNpcs(result.npcDataList)

        assertEquals(0, result.roomMaxHostileNpcs["cracked_plains:rift_spring"],
            "Vendor room rift_spring should have maxHostileNpcs=0")
        assertEquals(0, result.roomMaxHostileNpcs["cracked_plains:signal_cairn"],
            "Quest NPC room signal_cairn should have maxHostileNpcs=0")
    }
}
