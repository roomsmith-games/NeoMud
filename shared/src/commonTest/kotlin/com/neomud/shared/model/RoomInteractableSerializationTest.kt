package com.neomud.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoomInteractableSerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun testBasicInteractableRoundTrip() {
        val original = RoomInteractable(
            id = "lever_1",
            label = "Rusty Lever",
            description = "You pull the lever and hear a grinding sound.",
            actionType = "EXIT_OPEN",
            actionData = mapOf("direction" to "NORTH"),
            perceptionDC = 15,
            cooldownTicks = 10,
            resetTicks = 20,
            sound = "lever_pull"
        )
        val encoded = json.encodeToString(RoomInteractable.serializer(), original)
        val decoded = json.decodeFromString(RoomInteractable.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun testInteractableWithDifficultyRoundTrip() {
        val original = RoomInteractable(
            id = "heavy_door",
            label = "Heavy Stone Door",
            description = "You force the door open with a mighty heave!",
            failureMessage = "The door won't budge. You're not strong enough.",
            actionType = "EXIT_OPEN",
            actionData = mapOf("direction" to "WEST"),
            difficulty = 25,
            difficultyCheck = "STRENGTH"
        )
        val encoded = json.encodeToString(RoomInteractable.serializer(), original)
        val decoded = json.decodeFromString(RoomInteractable.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals("STRENGTH", decoded.difficultyCheck)
        assertEquals(25, decoded.difficulty)
        assertEquals("The door won't budge. You're not strong enough.", decoded.failureMessage)
    }

    @Test
    fun testInteractableDefaultsRoundTrip() {
        val original = RoomInteractable(
            id = "button_1",
            label = "Button",
            description = "Click.",
            actionType = "ROOM_EFFECT"
        )
        val encoded = json.encodeToString(RoomInteractable.serializer(), original)
        val decoded = json.decodeFromString(RoomInteractable.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals(0, decoded.difficulty)
        assertEquals("", decoded.difficultyCheck)
        assertEquals("", decoded.failureMessage)
        assertEquals("", decoded.icon)
        assertEquals("", decoded.sound)
        assertEquals(0, decoded.perceptionDC)
        assertEquals(0, decoded.cooldownTicks)
        assertEquals(0, decoded.resetTicks)
    }

    @Test
    fun testBackwardCompatibility_missingNewFields() {
        // Simulate JSON from before the new fields were added
        val oldJson = """{"id":"old_lever","label":"Old Lever","description":"It works.","actionType":"EXIT_OPEN","actionData":{"direction":"SOUTH"}}"""
        val decoded = json.decodeFromString(RoomInteractable.serializer(), oldJson)
        assertEquals("old_lever", decoded.id)
        assertEquals(0, decoded.difficulty)
        assertEquals("", decoded.difficultyCheck)
        assertEquals("", decoded.failureMessage)
    }

    @Test
    fun testAllDifficultyCheckTypes() {
        val checks = listOf("STRENGTH", "AGILITY", "INTELLECT", "WILLPOWER")
        for (check in checks) {
            val original = RoomInteractable(
                id = "test_$check",
                label = "Test",
                description = "Pass",
                failureMessage = "Fail",
                actionType = "ROOM_EFFECT",
                difficulty = 20,
                difficultyCheck = check
            )
            val encoded = json.encodeToString(RoomInteractable.serializer(), original)
            val decoded = json.decodeFromString(RoomInteractable.serializer(), encoded)
            assertEquals(check, decoded.difficultyCheck)
        }
    }

    @Test
    fun testTeleportInteractableRoundTrip() {
        val original = RoomInteractable(
            id = "portal_1",
            label = "Mystic Portal",
            description = "You step through the shimmering portal!",
            failureMessage = "The portal flickers and rejects you.",
            icon = "🌀",
            actionType = "TELEPORT",
            actionData = mapOf("targetRoomId" to "dungeon:entrance", "message" to "Whoosh!"),
            difficulty = 15,
            difficultyCheck = "INTELLECT",
            perceptionDC = 10,
            cooldownTicks = 30,
            resetTicks = 0,
            sound = "portal_open"
        )
        val encoded = json.encodeToString(RoomInteractable.serializer(), original)
        val decoded = json.decodeFromString(RoomInteractable.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun testDifficultyFieldsPresentInJson() {
        val interactable = RoomInteractable(
            id = "test",
            label = "Test",
            description = "desc",
            failureMessage = "fail",
            actionType = "EXIT_OPEN",
            difficulty = 20,
            difficultyCheck = "AGILITY"
        )
        val encoded = json.encodeToString(RoomInteractable.serializer(), interactable)
        assertTrue(encoded.contains("\"difficulty\":20"))
        assertTrue(encoded.contains("\"difficultyCheck\":\"AGILITY\""))
        assertTrue(encoded.contains("\"failureMessage\":\"fail\""))
    }

    @Test
    fun testLegacyJsonParsesWithoutTriggerType() {
        // Existing zone JSONs were authored before triggerType existed.
        // They must continue to deserialize cleanly with the ON_ACTION default.
        val legacyJson = """{"id":"old_lever","label":"Old Lever","description":"It works.","actionType":"EXIT_OPEN","actionData":{"direction":"NORTH"}}"""
        val decoded = json.decodeFromString(RoomInteractable.serializer(), legacyJson)
        assertEquals("ON_ACTION", decoded.triggerType)
    }

    @Test
    fun testOnEnterTriggerTypeRoundTrip() {
        val original = RoomInteractable(
            id = "trap_dart_1",
            label = "Hidden Dart Trap",
            description = "A dart whistles out of the wall!",
            actionType = "DAMAGE_TRAP",
            actionData = mapOf("damage" to "8", "damageType" to "physical"),
            perceptionDC = 12,
            triggerType = "ON_ENTER"
        )
        val encoded = json.encodeToString(RoomInteractable.serializer(), original)
        val decoded = json.decodeFromString(RoomInteractable.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals("ON_ENTER", decoded.triggerType)
        assertTrue(encoded.contains("\"triggerType\":\"ON_ENTER\""))
    }

    @Test
    fun testDamageTrapWithSaveActionDataRoundTrip() {
        val original = RoomInteractable(
            id = "trap_glyph_fire",
            label = "Fire Glyph",
            description = "Flames erupt from the floor!",
            actionType = "DAMAGE_TRAP",
            actionData = mapOf(
                "damage" to "15",
                "damageType" to "fire",
                "saveStat" to "AGILITY",
                "saveDC" to "14",
                "saveType" to "DODGE",
                "damageMessage" to "You are scorched!",
                "dodgeMessage" to "You leap back just in time!"
            ),
            perceptionDC = 16,
            triggerType = "ON_ENTER"
        )
        val encoded = json.encodeToString(RoomInteractable.serializer(), original)
        val decoded = json.decodeFromString(RoomInteractable.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals("AGILITY", decoded.actionData["saveStat"])
        assertEquals("DODGE", decoded.actionData["saveType"])
    }
}
