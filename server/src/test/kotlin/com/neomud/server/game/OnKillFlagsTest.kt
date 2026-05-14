package com.neomud.server.game

import com.neomud.server.game.npc.NpcManager
import com.neomud.server.game.npc.NpcState
import com.neomud.server.game.npc.behavior.IdleBehavior
import com.neomud.server.persistence.repository.PlayerFlagsRepository
import com.neomud.server.persistence.tables.PlayerFlagsTable
import com.neomud.server.world.NpcData
import com.neomud.server.world.RelockExitData
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.Direction
import com.neomud.shared.model.Room
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnKillFlagsTest {

    private fun withTestDb(block: (PlayerFlagsRepository) -> Unit) {
        val tmpFile = File.createTempFile("neomud_killflags_test_", ".db")
        tmpFile.deleteOnExit()
        tmpFile.delete()
        Database.connect("jdbc:sqlite:${tmpFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction { SchemaUtils.create(PlayerFlagsTable) }
        block(PlayerFlagsRepository())
    }

    @Test
    fun `onKillFlags are copied from NpcData to NpcState`() {
        val data = NpcData(
            id = "npc:test_boss",
            name = "Test Boss",
            description = "A test boss",
            startRoomId = "test:room",
            behaviorType = "stationary",
            hostile = true,
            maxHp = 100,
            damage = 10,
            level = 5,
            onKillFlags = mapOf("kill:test_boss" to "true")
        )

        val worldGraph = WorldGraph()
        worldGraph.addRoom(Room(
            id = "test:room",
            name = "Test Room",
            description = "",
            zoneId = "test",
            x = 0, y = 0,
            exits = emptyMap()
        ))
        val npcManager = NpcManager(worldGraph)
        npcManager.loadNpcs(listOf(data to "test"))

        val npcState = npcManager.getNpcState("npc:test_boss")
        assertTrue(npcState != null)
        assertEquals(mapOf("kill:test_boss" to "true"), npcState.onKillFlags)
    }

    @Test
    fun `NPC without onKillFlags has empty map`() {
        val data = NpcData(
            id = "npc:normal",
            name = "Normal NPC",
            description = "A normal NPC",
            startRoomId = "test:room",
            behaviorType = "stationary",
            hostile = true,
            maxHp = 50,
            damage = 5,
            level = 1
        )

        val worldGraph = WorldGraph()
        worldGraph.addRoom(Room(
            id = "test:room",
            name = "Test Room",
            description = "",
            zoneId = "test",
            x = 0, y = 0,
            exits = emptyMap()
        ))
        val npcManager = NpcManager(worldGraph)
        npcManager.loadNpcs(listOf(data to "test"))

        val npcState = npcManager.getNpcState("npc:normal")
        assertTrue(npcState != null)
        assertTrue(npcState.onKillFlags.isEmpty())
    }

    @Test
    fun `getTemplateData returns NpcData for known template`() {
        val data = NpcData(
            id = "npc:boss",
            name = "Boss",
            description = "",
            startRoomId = "test:room",
            behaviorType = "stationary",
            hostile = true,
            maxHp = 100,
            damage = 10,
            level = 5,
            onKillFlags = mapOf("kill:boss" to "true")
        )

        val worldGraph = WorldGraph()
        worldGraph.addRoom(Room(
            id = "test:room",
            name = "Test Room",
            description = "",
            zoneId = "test",
            x = 0, y = 0,
            exits = emptyMap()
        ))
        val npcManager = NpcManager(worldGraph)
        npcManager.loadNpcs(listOf(data to "test"))

        val template = npcManager.getTemplateData("npc:boss")
        assertTrue(template != null)
        assertEquals(mapOf("kill:boss" to "true"), template.onKillFlags)
    }

    @Test
    fun `getTemplateData returns null for unknown template`() {
        val worldGraph = WorldGraph()
        val npcManager = NpcManager(worldGraph)
        assertNull(npcManager.getTemplateData("npc:nonexistent"))
    }

    @Test
    fun `onSpawnRelockExits re-locks exit on NPC load`() {
        val worldGraph = WorldGraph()
        worldGraph.addRoom(Room(
            id = "test:boss_room",
            name = "Boss Room",
            description = "",
            zoneId = "test",
            x = 0, y = 0,
            exits = mapOf(Direction.SOUTH to "test:reward_room"),
            lockedExits = mapOf(Direction.SOUTH to 99)
        ))
        worldGraph.addRoom(Room(
            id = "test:reward_room",
            name = "Reward Room",
            description = "",
            zoneId = "test",
            x = 0, y = 1,
            exits = mapOf(Direction.NORTH to "test:boss_room")
        ))
        worldGraph.setOriginalLockedExits("test:boss_room", mapOf(Direction.SOUTH to 99))

        // Unlock the exit (simulating a previous player's kill)
        worldGraph.unlockExit("test:boss_room", Direction.SOUTH)
        val roomUnlocked = worldGraph.getRoom("test:boss_room")!!
        assertFalse(Direction.SOUTH in roomUnlocked.lockedExits)

        // Mark the interactable as used
        worldGraph.markInteractableUsed("test:boss_room", "boss_gate", 0)
        assertTrue(worldGraph.isInteractableUsed("test:boss_room", "boss_gate"))

        // Load an NPC with onSpawnRelockExits — should re-lock the exit and reset the interactable
        val data = NpcData(
            id = "npc:boss",
            name = "Boss",
            description = "",
            startRoomId = "test:boss_room",
            behaviorType = "stationary",
            hostile = true,
            maxHp = 100,
            damage = 10,
            level = 5,
            onSpawnRelockExits = listOf(
                RelockExitData(
                    roomId = "test:boss_room",
                    direction = "SOUTH",
                    interactableId = "boss_gate"
                )
            )
        )

        val npcManager = NpcManager(worldGraph)
        npcManager.loadNpcs(listOf(data to "test"))

        val roomAfterSpawn = worldGraph.getRoom("test:boss_room")!!
        assertTrue(Direction.SOUTH in roomAfterSpawn.lockedExits, "SOUTH should be re-locked after NPC spawn")
        assertEquals(99, roomAfterSpawn.lockedExits[Direction.SOUTH])
        assertFalse(worldGraph.isInteractableUsed("test:boss_room", "boss_gate"), "Interactable should be reset after NPC spawn")
    }

    @Test
    fun `onKillFlags set via PlayerFlagsRepository`() = withTestDb { repo ->
        val flags = mapOf("kill:sealed_one_seal" to "true", "quest:threshold_complete" to "done")
        val playerName = "TestPlayer"

        for ((key, value) in flags) {
            repo.setFlag(playerName, key, value)
        }

        assertEquals("true", repo.getFlag(playerName, "kill:sealed_one_seal"))
        assertEquals("done", repo.getFlag(playerName, "quest:threshold_complete"))
        assertNull(repo.getFlag("OtherPlayer", "kill:sealed_one_seal"))
    }

    @Test
    fun `kill event integration - template lookup plus flag persistence`() = withTestDb { repo ->
        val worldGraph = WorldGraph()
        worldGraph.addRoom(Room(
            id = "test:boss_room",
            name = "Boss Room",
            description = "",
            zoneId = "test",
            x = 0, y = 0,
            exits = emptyMap()
        ))

        val data = NpcData(
            id = "npc:test_boss",
            name = "Test Boss",
            description = "",
            startRoomId = "test:boss_room",
            behaviorType = "stationary",
            hostile = true,
            maxHp = 100,
            damage = 10,
            level = 5,
            onKillFlags = mapOf("kill:test_boss" to "true", "quest:dungeon_clear" to "done")
        )

        val npcManager = NpcManager(worldGraph)
        npcManager.loadNpcs(listOf(data to "test"))

        val killerName = "HeroPlayer"
        val templateId = "npc:test_boss"

        val templateData = npcManager.getTemplateData(templateId)
        assertTrue(templateData != null)
        assertTrue(templateData.onKillFlags.isNotEmpty())

        for ((flagKey, flagValue) in templateData.onKillFlags) {
            repo.setFlag(killerName, flagKey, flagValue)
        }

        assertEquals("true", repo.getFlag(killerName, "kill:test_boss"))
        assertEquals("done", repo.getFlag(killerName, "quest:dungeon_clear"))
        assertNull(repo.getFlag("OtherPlayer", "kill:test_boss"))
    }

    @Test
    fun `kill event with no onKillFlags does not set flags`() = withTestDb { repo ->
        val worldGraph = WorldGraph()
        worldGraph.addRoom(Room(
            id = "test:room",
            name = "Test Room",
            description = "",
            zoneId = "test",
            x = 0, y = 0,
            exits = emptyMap()
        ))

        val data = NpcData(
            id = "npc:normal_mob",
            name = "Normal Mob",
            description = "",
            startRoomId = "test:room",
            behaviorType = "stationary",
            hostile = true,
            maxHp = 50,
            damage = 5,
            level = 1
        )

        val npcManager = NpcManager(worldGraph)
        npcManager.loadNpcs(listOf(data to "test"))

        val templateData = npcManager.getTemplateData("npc:normal_mob")
        assertTrue(templateData != null)
        assertTrue(templateData.onKillFlags.isEmpty())

        assertNull(repo.getFlag("SomePlayer", "kill:normal_mob"))
    }
}
