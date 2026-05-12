package com.neomud.server.world

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BossPhaseDataTest {

    private val json = Json { encodeDefaults = false; ignoreUnknownKeys = true }

    @Test
    fun `BossPhaseData round trips through JSON`() {
        val original = BossPhaseData(
            name = "Lightning Form",
            hpThresholdPercent = 0.5,
            spriteId = "npc:stormcrown_phase2",
            damage = 85,
            accuracy = 65,
            defense = 40,
            evasion = 20,
            transitionMessage = "Lightning crackles!",
            transitionSound = "boss_phase_shift"
        )
        val encoded = json.encodeToString(BossPhaseData.serializer(), original)
        val decoded = json.decodeFromString(BossPhaseData.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `BossPhaseData defaults are correct`() {
        val minimal = BossPhaseData(name = "Enraged", hpThresholdPercent = 0.3)
        assertEquals("", minimal.spriteId)
        assertNull(minimal.damage)
        assertNull(minimal.accuracy)
        assertNull(minimal.defense)
        assertNull(minimal.evasion)
        assertEquals("", minimal.transitionMessage)
        assertEquals("", minimal.transitionSound)
    }

    @Test
    fun `NpcData with phases round trips`() {
        val phases = listOf(
            BossPhaseData(name = "Phase 2", hpThresholdPercent = 0.5, damage = 80),
            BossPhaseData(name = "Phase 3", hpThresholdPercent = 0.2, damage = 120, accuracy = 70)
        )
        val npc = NpcData(
            id = "npc:boss",
            name = "Boss",
            description = "A boss NPC.",
            startRoomId = "zone:room",
            behaviorType = "idle",
            hostile = true,
            maxHp = 1000,
            damage = 50,
            phases = phases
        )
        val encoded = json.encodeToString(NpcData.serializer(), npc)
        val decoded = json.decodeFromString(NpcData.serializer(), encoded)
        assertEquals(2, decoded.phases.size)
        assertEquals("Phase 2", decoded.phases[0].name)
        assertEquals(80, decoded.phases[0].damage)
        assertNull(decoded.phases[0].accuracy)
        assertEquals("Phase 3", decoded.phases[1].name)
        assertEquals(70, decoded.phases[1].accuracy)
    }

    @Test
    fun `NpcData without phases defaults to empty list`() {
        val npcJson = """{"id":"npc:rat","name":"Rat","description":"A rat.","startRoomId":"forest:path","behaviorType":"wander"}"""
        val decoded = json.decodeFromString(NpcData.serializer(), npcJson)
        assertTrue(decoded.phases.isEmpty())
    }

    @Test
    fun `partial stat overrides leave others null`() {
        val phase = BossPhaseData(
            name = "Phase 2",
            hpThresholdPercent = 0.5,
            damage = 100
        )
        assertEquals(100, phase.damage)
        assertNull(phase.accuracy)
        assertNull(phase.defense)
        assertNull(phase.evasion)
    }
}
