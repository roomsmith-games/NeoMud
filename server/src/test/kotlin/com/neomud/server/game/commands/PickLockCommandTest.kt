package com.neomud.server.game.commands

import com.neomud.server.session.PendingSkill
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.*
import com.neomud.server.session.TransportSession
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertEquals

class PickLockCommandTest {

    private fun createTestWorldGraph(): WorldGraph {
        val graph = WorldGraph()
        graph.addRoom(Room(
            id = "test:room1",
            name = "Test Room",
            description = "A test room.",
            exits = mapOf(Direction.NORTH to "test:room2"),
            lockedExits = mapOf(Direction.NORTH to 15),
            zoneId = "test",
            x = 0, y = 0
        ))
        graph.addRoom(Room(
            id = "test:room2",
            name = "North Room",
            description = "Room to the north.",
            exits = mapOf(Direction.SOUTH to "test:room1"),
            zoneId = "test",
            x = 0, y = 1
        ))
        return graph
    }

    private fun createPickLockCommand(): PickLockCommand {
        val worldGraph = createTestWorldGraph()
        val sessionManager = SessionManager()
        val npcManager = NpcManager(worldGraph, emptyMap(), emptyMap())
        return PickLockCommand(worldGraph, sessionManager, npcManager)
    }

    @Test
    fun testPickLockQueuesPendingSkill() {
        runBlocking {
            val command = createPickLockCommand()
            val session = createTestSession()
            session.player = createTestPlayer()
            session.currentRoomId = "test:room1"

            command.execute(session, "exit:NORTH")

            assertIs<PendingSkill.PickLock>(session.pendingSkill)
            assertEquals("exit:NORTH", (session.pendingSkill as PendingSkill.PickLock).targetId)
        }
    }

    @Test
    fun testPickLockAutoTargetSingleLock() {
        runBlocking {
            val command = createPickLockCommand()
            val session = createTestSession()
            session.player = createTestPlayer()
            session.currentRoomId = "test:room1"

            // No target specified, but only one lock exists — should auto-target
            command.execute(session, null)

            assertIs<PendingSkill.PickLock>(session.pendingSkill)
            assertEquals("exit:NORTH", (session.pendingSkill as PendingSkill.PickLock).targetId)
        }
    }

    @Test
    fun testPickLockBlockedOnCooldown() {
        runBlocking {
            val command = createPickLockCommand()
            val session = createTestSession()
            session.player = createTestPlayer()
            session.currentRoomId = "test:room1"
            session.skillCooldowns["PICK_LOCK"] = 3

            command.execute(session, "exit:NORTH")

            assertNull(session.pendingSkill)
        }
    }

    @Test
    fun testPickLockNoLockedExits() {
        runBlocking {
            val worldGraph = WorldGraph()
            worldGraph.addRoom(Room(
                id = "test:open_room",
                name = "Open Room",
                description = "No locks here.",
                exits = mapOf(Direction.NORTH to "test:room2"),
                zoneId = "test",
                x = 0, y = 0
            ))
            val sessionManager = SessionManager()
            val npcManager = NpcManager(worldGraph, emptyMap(), emptyMap())
            val command = PickLockCommand(worldGraph, sessionManager, npcManager)

            val session = createTestSession()
            session.player = createTestPlayer()
            session.currentRoomId = "test:open_room"

            command.execute(session, null)

            assertNull(session.pendingSkill)
        }
    }

    private fun createTestSession(): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) {}
            override suspend fun close(reason: String) {}
        })
    }

    private fun createTestPlayer(): Player {
        return Player(
            name = "TestRogue",
            characterClass = "ROGUE",
            race = "HUMAN",
            level = 5,
            currentHp = 50,
            maxHp = 50,
            currentMp = 20,
            maxMp = 20,
            currentRoomId = "test:room1",
            currentXp = 0,
            xpToNextLevel = 1000,
            stats = Stats(strength = 15, agility = 20, intellect = 14, willpower = 12, health = 16, charm = 10),
            unspentCp = 0,
            totalCpEarned = 0
        )
    }
}
