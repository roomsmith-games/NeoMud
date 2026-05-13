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
            "DAMAGE_TRAP", "PLACE_ITEM", "PUZZLE_STEP", "RIDDLE_PROMPT", "CONDITIONAL_TRIGGER"
        )
        for (type in actionTypes) {
            assertTrue(type in allowlist, "Unknown actionType '$type' — not in WorldLoader allowlist")
        }

        for (type in listOf("DAMAGE_TRAP", "PLACE_ITEM", "PUZZLE_STEP", "CONDITIONAL_TRIGGER")) {
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
