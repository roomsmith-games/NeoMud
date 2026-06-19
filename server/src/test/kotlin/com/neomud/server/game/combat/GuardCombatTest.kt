package com.neomud.server.game.combat

import com.neomud.server.game.GameConfig
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.game.npc.NpcState
import com.neomud.server.game.npc.behavior.IdleBehavior
import com.neomud.server.game.npc.behavior.PatrolBehavior
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.Room
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuardCombatTest {

    @Test
    fun testGuardNpcsDetectedInRoom() {
        val world = WorldGraph()
        world.addRoom(Room("town:square", "Square", "", mapOf(), "town", 0, 0))
        val manager = NpcManager(world)

        val guard = createGuardNpc("guard1", "town:square")
        val hostile = createHostileNpc("spider1", "town:square")
        manager.loadNpcs(listOf()) // empty load, we'll add manually via reflection or test helper

        // Use getLivingGuardNpcsInRoom directly with loaded NPCs
        val guardManager = createNpcManagerWithNpcs(world, listOf(guard, hostile))
        val guards = guardManager.getLivingGuardNpcsInRoom("town:square")
        assertEquals(1, guards.size)
        assertEquals("Town Guard", guards[0].name)
    }

    @Test
    fun testGuardNotReturnedIfNoStats() {
        val world = WorldGraph()
        world.addRoom(Room("town:square", "Square", "", mapOf(), "town", 0, 0))

        // NPC without combat stats should not be considered a guard
        val npc = NpcState(
            id = "npc:barkeep",
            name = "Barkeep",
            description = "A barkeep",
            currentRoomId = "town:square",
            behavior = IdleBehavior(),
            hostile = false,
            maxHp = 0,
            damage = 0
        )
        val manager = createNpcManagerWithNpcs(world, listOf(npc))
        val guards = manager.getLivingGuardNpcsInRoom("town:square")
        assertTrue(guards.isEmpty(), "NPCs without combat stats should not be guards")
    }

    @Test
    fun testHostileNpcNotReturnedAsGuard() {
        val world = WorldGraph()
        world.addRoom(Room("town:square", "Square", "", mapOf(), "town", 0, 0))

        val hostile = createHostileNpc("spider1", "town:square")
        val manager = createNpcManagerWithNpcs(world, listOf(hostile))
        val guards = manager.getLivingGuardNpcsInRoom("town:square")
        assertTrue(guards.isEmpty(), "Hostile NPCs should not be returned as guards")
    }

    @Test
    fun testGetRoomsWithGuards() {
        val world = WorldGraph()
        world.addRoom(Room("town:square", "Square", "", mapOf(), "town", 0, 0))
        world.addRoom(Room("town:gate", "Gate", "", mapOf(), "town", 0, 1))
        world.addRoom(Room("forest:clearing", "Clearing", "", mapOf(), "forest", 0, 2))

        val guard1 = createGuardNpc("guard1", "town:square")
        val guard2 = createGuardNpc("guard2", "town:gate")
        val hostile = createHostileNpc("spider1", "forest:clearing")

        val manager = createNpcManagerWithNpcs(world, listOf(guard1, guard2, hostile))
        val guardRooms = manager.getRoomsWithGuards()

        assertEquals(2, guardRooms.size)
        assertTrue("town:square" in guardRooms)
        assertTrue("town:gate" in guardRooms)
        assertTrue("forest:clearing" !in guardRooms)
    }

    @Test
    fun testGuardDamageCalculation() {
        // Guard with damage 10: variance = max(10/3, 1) = 3
        // Damage range: 10 + random(1..3) = 11-13
        val guardDamage = 10
        val variance = maxOf(guardDamage / GameConfig.Combat.NPC_VARIANCE_DIVISOR, 1)
        assertEquals(3, variance)

        // Verify damage range
        val minDamage = guardDamage + 1
        val maxDamage = guardDamage + variance
        assertEquals(11, minDamage)
        assertEquals(13, maxDamage)
    }

    @Test
    fun testGuardKillsHostileNpc() {
        // Simulate guard attacking a low-HP hostile
        val guard = createGuardNpc("guard1", "town:square")
        val hostile = createHostileNpc("rat1", "town:square", maxHp = 5, currentHp = 5)

        // Guard deals at least 11 damage (10 + 1), which exceeds rat's 5 HP
        val variance = maxOf(guard.damage / GameConfig.Combat.NPC_VARIANCE_DIVISOR, 1)
        val minDamage = guard.damage + 1
        hostile.currentHp -= minDamage

        assertTrue(hostile.currentHp <= 0, "Guard's minimum damage ($minDamage) should kill a 5 HP rat")
    }

    @Test
    fun testHostileRetaliationDamagesGuard() {
        val guard = createGuardNpc("guard1", "town:square")
        val hostile = createHostileNpc("spider1", "town:square", damage = 5)

        // Spider attacks guard: damage = 5 + random(1..max(5/3,1)) = 5 + random(1..1) = 6
        val variance = maxOf(hostile.damage / GameConfig.Combat.NPC_VARIANCE_DIVISOR, 1)
        val damage = hostile.damage + 1 // minimum
        guard.currentHp -= damage

        assertEquals(120 - damage, guard.currentHp)
        assertTrue(guard.currentHp > 0, "Guard should survive a single spider hit")
    }

    @Test
    fun testDeadGuardNotReturnedAsGuard() {
        val world = WorldGraph()
        world.addRoom(Room("town:square", "Square", "", mapOf(), "town", 0, 0))

        val guard = createGuardNpc("guard1", "town:square")
        guard.currentHp = 0
        val manager = createNpcManagerWithNpcs(world, listOf(guard))

        val guards = manager.getLivingGuardNpcsInRoom("town:square")
        assertTrue(guards.isEmpty(), "Dead guards should not be returned")
    }

    // Helper: create a guard NPC with the recommended stats
    private fun createGuardNpc(id: String, roomId: String): NpcState {
        return NpcState(
            id = id,
            name = "Town Guard",
            description = "A guard",
            currentRoomId = roomId,
            behavior = PatrolBehavior(listOf(roomId)),
            hostile = false,
            maxHp = 120,
            currentHp = 120,
            damage = 10,
            level = 5,
            accuracy = 20,
            defense = 15,
            evasion = 5,
            agility = 12,
            perception = 30,
            templateId = "npc:town_guard"
        )
    }

    // Helper: create a hostile NPC
    private fun createHostileNpc(
        id: String,
        roomId: String,
        maxHp: Int = 20,
        currentHp: Int = maxHp,
        damage: Int = 5
    ): NpcState {
        return NpcState(
            id = id,
            name = "Giant Spider",
            description = "A spider",
            currentRoomId = roomId,
            behavior = IdleBehavior(),
            hostile = true,
            maxHp = maxHp,
            currentHp = currentHp,
            damage = damage,
            level = 3,
            xpReward = 50,
            templateId = "npc:giant_spider"
        )
    }

    // Helper: create NpcManager with pre-loaded NPCs (bypassing loadNpcs which needs NpcData)
    private fun createNpcManagerWithNpcs(world: WorldGraph, npcs: List<NpcState>): NpcManager {
        val manager = NpcManager(world)
        // Use reflection to add NPCs directly to the internal list
        val npcsField = NpcManager::class.java.getDeclaredField("npcs")
        npcsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val npcList = npcsField.get(manager) as MutableList<NpcState>
        npcList.addAll(npcs)
        return manager
    }
}
