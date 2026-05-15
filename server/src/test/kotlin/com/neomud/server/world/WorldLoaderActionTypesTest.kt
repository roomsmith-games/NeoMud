package com.neomud.server.world

import com.neomud.server.defaultWorldSource
import com.neomud.server.game.trap.TrapResolver
import com.neomud.shared.model.Stats
import kotlin.test.Test
import kotlin.test.assertTrue

class WorldLoaderActionTypesTest {

    private val result = WorldLoader.load(defaultWorldSource())

    @Test
    fun testNewActionTypesLoadThroughWorldLoader() {
        val allInteractables = result.worldGraph.getAllRooms().flatMap { it.interactables }
        val actionTypes = allInteractables.map { it.actionType }.toSet()

        val allowlist = setOf(
            "EXIT_OPEN", "TELEPORT", "TREASURE_DROP", "MONSTER_SPAWN", "ROOM_EFFECT",
            "DAMAGE_TRAP", "PLACE_ITEM", "PUZZLE_STEP", "RIDDLE_PROMPT", "CONDITIONAL_TRIGGER",
            "CHOICE_PROMPT"
        )
        for (type in actionTypes) {
            assertTrue(type in allowlist, "Unknown actionType '$type' — not in WorldLoader allowlist")
        }

        for (type in listOf("DAMAGE_TRAP", "PLACE_ITEM", "PUZZLE_STEP", "CONDITIONAL_TRIGGER", "CHOICE_PROMPT")) {
            assertTrue(type in actionTypes, "Expected actionType '$type' in world data")
        }
    }

    @Test
    fun testDamageTrapActionTypesExist() {
        val traps = result.worldGraph.getAllRooms()
            .flatMap { it.interactables }
            .filter { it.actionType == "DAMAGE_TRAP" }
        assertTrue(traps.isNotEmpty(), "World should have at least one DAMAGE_TRAP")
    }

    @Test
    fun testPlaceItemActionTypesExist() {
        val items = result.worldGraph.getAllRooms()
            .flatMap { it.interactables }
            .filter { it.actionType == "PLACE_ITEM" }
        assertTrue(items.isNotEmpty(), "World should have at least one PLACE_ITEM")
    }

    @Test
    fun testPuzzleStepActionTypesExist() {
        val steps = result.worldGraph.getAllRooms()
            .flatMap { it.interactables }
            .filter { it.actionType == "PUZZLE_STEP" }
        assertTrue(steps.isNotEmpty(), "World should have at least one PUZZLE_STEP")
    }

    @Test
    fun testConditionalTriggerActionTypesExist() {
        val triggers = result.worldGraph.getAllRooms()
            .flatMap { it.interactables }
            .filter { it.actionType == "CONDITIONAL_TRIGGER" }
        assertTrue(triggers.isNotEmpty(), "World should have at least one CONDITIONAL_TRIGGER")
    }

    @Test
    fun testPuzzleStepGroupCompleteness() {
        val puzzleGroups = mutableMapOf<String, MutableList<Map<String, String>>>()
        for (room in result.worldGraph.getAllRooms()) {
            for (feat in room.interactables) {
                if (feat.actionType == "PUZZLE_STEP") {
                    val groupId = feat.actionData["puzzleGroupId"] ?: continue
                    puzzleGroups.getOrPut(groupId) { mutableListOf() }.add(feat.actionData)
                }
            }
        }
        assertTrue(puzzleGroups.isNotEmpty(), "World should have at least one PUZZLE_STEP group")
        for ((groupId, members) in puzzleGroups) {
            val totalStepsSet = members.mapNotNull { it["puzzleTotalSteps"]?.toIntOrNull() }.toSet()
            assertTrue(
                totalStepsSet.size == 1,
                "PUZZLE_STEP group '$groupId' has inconsistent puzzleTotalSteps: $totalStepsSet"
            )
            val totalSteps = totalStepsSet.first()
            val stepIndices = members.mapNotNull { it["puzzleStepIndex"]?.toIntOrNull() }.sorted()
            val expected = (0 until totalSteps).toList()
            assertTrue(
                stepIndices == expected,
                "PUZZLE_STEP group '$groupId' expected steps $expected but found $stepIndices"
            )
        }
    }

    @Test
    fun testOnSpawnRelockExitsReferenceValidRooms() {
        for ((npcData, _) in result.npcDataList) {
            for (relock in npcData.onSpawnRelockExits) {
                val targetRoom = result.worldGraph.getRoom(relock.roomId)
                assertTrue(
                    targetRoom != null,
                    "NPC '${npcData.id}' onSpawnRelockExits roomId '${relock.roomId}' not found"
                )
                val validDir = try { com.neomud.shared.model.Direction.valueOf(relock.direction); true } catch (_: IllegalArgumentException) { false }
                assertTrue(
                    validDir,
                    "NPC '${npcData.id}' onSpawnRelockExits invalid direction '${relock.direction}'"
                )
                if (relock.interactableId.isNotBlank() && targetRoom != null) {
                    assertTrue(
                        targetRoom.interactables.any { it.id == relock.interactableId },
                        "NPC '${npcData.id}' onSpawnRelockExits interactableId '${relock.interactableId}' not found on room '${relock.roomId}'"
                    )
                }
            }
        }
    }

    @Test
    fun testOnKillFlagsCrossRefConditionalTriggers() {
        val allKillFlagKeys = result.npcDataList
            .flatMap { (npc, _) -> npc.onKillFlags.keys }
            .toSet()
        assertTrue(allKillFlagKeys.isNotEmpty(), "World must contain at least one NPC with onKillFlags")

        // Collect all flag consumers: CONDITIONAL_TRIGGER FLAG + PUZZLE_STEP requiredFlagKey
        val allFlagConsumers = mutableSetOf<String>()
        for (room in result.worldGraph.getAllRooms()) {
            for (feat in room.interactables) {
                if (feat.actionType == "CONDITIONAL_TRIGGER" && feat.actionData["conditionType"] == "FLAG") {
                    feat.actionData["requiredFlagKey"]?.let { allFlagConsumers.add(it) }
                }
                if (feat.actionType == "PUZZLE_STEP") {
                    feat.actionData["requiredFlagKey"]?.let { allFlagConsumers.add(it) }
                }
            }
        }

        for (key in allKillFlagKeys) {
            assertTrue(
                key in allFlagConsumers,
                "NPC onKillFlags key '$key' has no matching flag consumer (orphaned)"
            )
        }
        for (key in allFlagConsumers) {
            if (key.startsWith("kill:")) {
                assertTrue(
                    key in allKillFlagKeys,
                    "Flag consumer requires '$key' but no NPC produces it via onKillFlags"
                )
            }
        }
    }

    @Test
    fun testAllDamageTrapSaveStatsResolveToNonZero() {
        val defaultStats = Stats(
            strength = 30, agility = 30, intellect = 30,
            willpower = 30, health = 30, charm = 30
        )
        val traps = result.worldGraph.getAllRooms()
            .flatMap { room -> room.interactables.map { room to it } }
            .filter { (_, feat) -> feat.actionType == "DAMAGE_TRAP" }

        assertTrue(traps.isNotEmpty(), "World should have DAMAGE_TRAP interactables")

        for ((room, feat) in traps) {
            val saveStat = feat.actionData["saveStat"] ?: continue
            if (saveStat.isBlank()) continue
            val value = TrapResolver.saveStatValue(defaultStats, saveStat)
            assertTrue(
                value > 0,
                "DAMAGE_TRAP in room '${room.id}' (interactable '${feat.id}') has saveStat " +
                    "'$saveStat' that resolves to 0 — TrapResolver.saveStatValue doesn't handle it"
            )
        }
    }
}
