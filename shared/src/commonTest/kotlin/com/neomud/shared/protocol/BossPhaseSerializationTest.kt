package com.neomud.shared.protocol

import com.neomud.shared.model.Npc
import kotlin.test.Test
import kotlin.test.assertEquals

class BossPhaseSerializationTest {

    @Test
    fun testNpcPhaseShiftRoundTrip() {
        val original = ServerMessage.NpcPhaseShift(
            npcId = "npc:stormcrown",
            npcName = "Stormcrown",
            phaseName = "Lightning Form",
            spriteId = "npc:stormcrown_phase2",
            message = "The Stormcrown's body cracks with lightning!",
            currentHp = 500,
            maxHp = 1000,
            sound = "boss_phase_shift"
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testNpcPhaseShiftWithDefaults() {
        val original = ServerMessage.NpcPhaseShift(
            npcId = "npc:boss",
            npcName = "Boss",
            phaseName = "Enraged",
            message = "The boss roars!",
            currentHp = 300,
            maxHp = 600
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json) as ServerMessage.NpcPhaseShift
        assertEquals("", decoded.spriteId)
        assertEquals("", decoded.sound)
        assertEquals("Enraged", decoded.phaseName)
    }

    @Test
    fun testNpcPhaseShiftJsonContainsType() {
        val msg = ServerMessage.NpcPhaseShift(
            npcId = "npc:x", npcName = "X", phaseName = "P2",
            message = "msg", currentHp = 1, maxHp = 2
        )
        val json = MessageSerializer.encodeServerMessage(msg)
        assert(json.contains("\"npc_phase_shift\"")) { "JSON should contain npc_phase_shift type discriminator" }
    }

    @Test
    fun testNpcWithSpriteOverrideRoundTrip() {
        val original = Npc(
            id = "npc:stormcrown",
            name = "Stormcrown",
            description = "A boss NPC.",
            currentRoomId = "keep:throne",
            behaviorType = "idle",
            hostile = true,
            currentHp = 500,
            maxHp = 1000,
            spriteOverride = "npc:stormcrown_phase2"
        )
        val json = kotlinx.serialization.json.Json.encodeToString(Npc.serializer(), original)
        val decoded = kotlinx.serialization.json.Json.decodeFromString(Npc.serializer(), json)
        assertEquals(original, decoded)
        assertEquals("npc:stormcrown_phase2", decoded.spriteOverride)
    }

    @Test
    fun testNpcSpriteOverrideDefaultEmpty() {
        val npc = Npc(
            id = "npc:rat", name = "Rat", description = "A rat.",
            currentRoomId = "forest:path", behaviorType = "wander"
        )
        assertEquals("", npc.spriteOverride)
    }

    @Test
    fun testNpcBackwardCompatibility_missingSpriteOverride() {
        val oldJson = """{"id":"npc:guard","name":"Guard","description":"A guard.","currentRoomId":"town:gate","behaviorType":"patrol"}"""
        val decoded = kotlinx.serialization.json.Json.decodeFromString(Npc.serializer(), oldJson)
        assertEquals("", decoded.spriteOverride)
    }
}
