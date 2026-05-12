package com.neomud.shared.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ConditionalTriggerSerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun testConditionalTriggerInteractableRoundTrip() {
        val original = RoomInteractable(
            id = "gate_1",
            label = "Storm Gate",
            description = "A sealed gate crackling with energy.",
            failureMessage = "You lack what is needed.",
            actionType = "CONDITIONAL_TRIGGER",
            actionData = mapOf(
                "conditionType" to "ITEM",
                "requiredItemId" to "item:storm_key",
                "successDirection" to "NORTH",
                "successMessage" to "The gate recognizes the key and opens."
            ),
            perceptionDC = 0,
            cooldownTicks = 0,
            resetTicks = 0,
            sound = "gate_open"
        )
        val encoded = json.encodeToString(RoomInteractable.serializer(), original)
        val decoded = json.decodeFromString(RoomInteractable.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals("CONDITIONAL_TRIGGER", decoded.actionType)
        assertEquals("ITEM", decoded.actionData["conditionType"])
        assertEquals("item:storm_key", decoded.actionData["requiredItemId"])
    }

    @Test
    fun testFlagConditionRoundTrip() {
        val original = RoomInteractable(
            id = "puzzle_gate",
            label = "Puzzle Lock",
            description = "The lock clicks open.",
            actionType = "CONDITIONAL_TRIGGER",
            actionData = mapOf(
                "conditionType" to "FLAG",
                "requiredFlagKey" to "puzzle:storm:step",
                "requiredFlagValue" to "5",
                "successDirection" to "EAST",
                "successMessage" to "The puzzle lock releases.",
                "failureMessage" to "The lock remains sealed."
            )
        )
        val encoded = json.encodeToString(RoomInteractable.serializer(), original)
        val decoded = json.decodeFromString(RoomInteractable.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals("FLAG", decoded.actionData["conditionType"])
        assertEquals("puzzle:storm:step", decoded.actionData["requiredFlagKey"])
        assertEquals("5", decoded.actionData["requiredFlagValue"])
    }

    @Test
    fun testLevelConditionRoundTrip() {
        val original = RoomInteractable(
            id = "ascension_gate",
            label = "Ascension Gate",
            description = "The gate recognizes your power.",
            actionType = "CONDITIONAL_TRIGGER",
            actionData = mapOf(
                "conditionType" to "LEVEL",
                "requiredLevel" to "25",
                "successDirection" to "UP",
                "successMessage" to "You are worthy.",
                "failureMessage" to "You are not yet powerful enough."
            )
        )
        val encoded = json.encodeToString(RoomInteractable.serializer(), original)
        val decoded = json.decodeFromString(RoomInteractable.serializer(), encoded)
        assertEquals("LEVEL", decoded.actionData["conditionType"])
        assertEquals("25", decoded.actionData["requiredLevel"])
    }
}
