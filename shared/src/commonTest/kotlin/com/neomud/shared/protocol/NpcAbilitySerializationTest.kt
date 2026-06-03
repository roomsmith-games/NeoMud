package com.neomud.shared.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NpcAbilitySerializationTest {

    @Test
    fun testNpcAbilityEffectRoundTrip() {
        val original = ServerMessage.NpcAbilityEffect(
            npcName = "Tidewarden Korlach",
            npcId = "npc:tidewarden_korlach",
            abilityName = "Tidal Surge",
            message = "Korlach sweeps its many arms through the water!",
            results = listOf(
                ServerMessage.AbilityHitResult(
                    targetName = "Fighter",
                    damage = 22,
                    saved = false,
                    newHp = 78,
                    maxHp = 100
                ),
                ServerMessage.AbilityHitResult(
                    targetName = "Healer",
                    damage = 11,
                    saved = true,
                    newHp = 89,
                    maxHp = 100
                )
            ),
            sound = "tidal_surge"
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testNpcAbilityEffectJsonContainsType() {
        val msg = ServerMessage.NpcAbilityEffect(
            npcName = "Boss",
            npcId = "npc:boss",
            abilityName = "Blast",
            message = "Blast!",
            results = emptyList()
        )
        val json = MessageSerializer.encodeServerMessage(msg)
        assertTrue(json.contains("\"npc_ability_effect\""), "JSON should contain npc_ability_effect type discriminator")
    }

    @Test
    fun testNpcAbilityEffectDefaultSound() {
        val msg = ServerMessage.NpcAbilityEffect(
            npcName = "Boss",
            npcId = "npc:boss",
            abilityName = "Hit",
            message = "Hit!",
            results = listOf(
                ServerMessage.AbilityHitResult("Player", 10, false, 90, 100)
            )
        )
        val json = MessageSerializer.encodeServerMessage(msg)
        val decoded = MessageSerializer.decodeServerMessage(json) as ServerMessage.NpcAbilityEffect
        assertEquals("", decoded.sound)
        assertEquals(1, decoded.results.size)
    }

    @Test
    fun testAbilityHitResultFields() {
        val result = ServerMessage.AbilityHitResult(
            targetName = "TestPlayer",
            damage = 25,
            saved = true,
            newHp = 75,
            maxHp = 100
        )
        assertEquals("TestPlayer", result.targetName)
        assertEquals(25, result.damage)
        assertTrue(result.saved)
        assertEquals(75, result.newHp)
        assertEquals(100, result.maxHp)
    }
}
