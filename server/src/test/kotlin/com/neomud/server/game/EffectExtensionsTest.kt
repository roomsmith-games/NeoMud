package com.neomud.server.game

import com.neomud.shared.model.*
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.TransportSession
import kotlin.test.*

class EffectExtensionsTest {

    private fun createTestSession(): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) {}
            override suspend fun close(reason: String) {}
        })
    }

    private fun createTestPlayer(
        currentHp: Int = 50,
        maxHp: Int = 100,
        currentMp: Int = 30,
        maxMp: Int = 50
    ): Player = Player(
        name = "TestPlayer",
        characterClass = "WARRIOR",
        race = "HUMAN",
        level = 5,
        currentHp = currentHp,
        maxHp = maxHp,
        currentMp = currentMp,
        maxMp = maxMp,
        currentRoomId = "test:room1",
        currentXp = 0,
        xpToNextLevel = 1000,
        stats = Stats(strength = 20, agility = 15, intellect = 10, willpower = 12, health = 18, charm = 10),
        unspentCp = 0,
        totalCpEarned = 0
    )

    // --- effectiveDamageBonus ---

    @Test
    fun testEffectiveDamageBonusReturnsZeroWithNoEffects() {
        val session = createTestSession()
        session.player = createTestPlayer()
        assertEquals(0, session.effectiveDamageBonus())
    }

    @Test
    fun testEffectiveDamageBonusSumsMultipleBuffDamageEffects() {
        val session = createTestSession()
        session.player = createTestPlayer()
        session.activeEffects.add(ActiveEffect("Wraith Draught (damage)", EffectType.BUFF_DAMAGE, 10, 3))
        session.activeEffects.add(ActiveEffect("Another Buff (damage)", EffectType.BUFF_DAMAGE, 5, 2))
        assertEquals(5, session.effectiveDamageBonus())
    }

    @Test
    fun testEffectiveDamageBonusIgnoresOtherEffectTypes() {
        val session = createTestSession()
        session.player = createTestPlayer()
        session.activeEffects.add(ActiveEffect("Strength Buff", EffectType.BUFF_STRENGTH, 10, 5))
        session.activeEffects.add(ActiveEffect("Damage Buff", EffectType.BUFF_DAMAGE, 10, 3))
        assertEquals(3, session.effectiveDamageBonus())
    }

    // --- effectiveMaxHp ---

    @Test
    fun testEffectiveMaxHpReturnsBaseWithNoEffects() {
        val session = createTestSession()
        session.player = createTestPlayer(maxHp = 100)
        assertEquals(100, session.effectiveMaxHp())
    }

    @Test
    fun testEffectiveMaxHpAddsBuffMaxHpMagnitudes() {
        val session = createTestSession()
        session.player = createTestPlayer(maxHp = 100)
        session.activeEffects.add(ActiveEffect("Fortifying Tonic (max HP)", EffectType.BUFF_MAX_HP, 20, 20))
        assertEquals(120, session.effectiveMaxHp())
    }

    @Test
    fun testEffectiveMaxHpSumsMultipleBuffs() {
        val session = createTestSession()
        session.player = createTestPlayer(maxHp = 100)
        session.activeEffects.add(ActiveEffect("Tonic (max HP)", EffectType.BUFF_MAX_HP, 10, 20))
        session.activeEffects.add(ActiveEffect("Scroll (max HP)", EffectType.BUFF_MAX_HP, 5, 30))
        assertEquals(150, session.effectiveMaxHp())
    }

    @Test
    fun testEffectiveMaxHpReturnsZeroWithNoPlayer() {
        val session = createTestSession()
        assertEquals(0, session.effectiveMaxHp())
    }

    // --- UseEffectProcessor new tokens ---

    @Test
    fun testUseEffectProcessorParsesDamageToken() {
        val player = createTestPlayer()
        val result = UseEffectProcessor.process("damage:35", player, "Scroll of Fireball")
        assertNotNull(result)
        assertEquals(35, result.targetDamage)
        assertFalse(result.cureDot)
        assertTrue(result.messages.any { "unleash" in it.lowercase() })
    }

    @Test
    fun testUseEffectProcessorParsesBuffDamageToken() {
        val player = createTestPlayer()
        val result = UseEffectProcessor.process("buff_damage:3:20", player, "Wraith Draught")
        assertNotNull(result)
        assertEquals(1, result.newEffects.size)
        assertEquals(EffectType.BUFF_DAMAGE, result.newEffects[0].type)
        assertEquals(3, result.newEffects[0].magnitude)
        assertEquals(20, result.newEffects[0].remainingTicks)
    }

    @Test
    fun testUseEffectProcessorParsesBuffMaxHpToken() {
        val player = createTestPlayer()
        val result = UseEffectProcessor.process("buff_max_hp:20:20", player, "Fortifying Tonic")
        assertNotNull(result)
        assertEquals(1, result.newEffects.size)
        assertEquals(EffectType.BUFF_MAX_HP, result.newEffects[0].type)
        assertEquals(20, result.newEffects[0].magnitude)
        assertEquals(20, result.newEffects[0].remainingTicks)
    }

    @Test
    fun testUseEffectProcessorParsesCureDotToken() {
        val player = createTestPlayer()
        val result = UseEffectProcessor.process("cure_dot", player, "Antivenom Vial")
        assertNotNull(result)
        assertTrue(result.cureDot)
        assertEquals(0, result.targetDamage)
    }

    @Test
    fun testCureDotRemovesPoisonAndDamageEffects() {
        val session = createTestSession()
        session.player = createTestPlayer()
        session.activeEffects.add(ActiveEffect("Venom", EffectType.POISON, 10, 5))
        session.activeEffects.add(ActiveEffect("Fire", EffectType.DAMAGE, 8, 3))
        session.activeEffects.add(ActiveEffect("Heal", EffectType.HEAL_OVER_TIME, 5, 10))

        // Simulate cure_dot processing
        session.activeEffects.removeAll { it.type in setOf(EffectType.POISON, EffectType.DAMAGE) }

        assertEquals(1, session.activeEffects.size)
        assertEquals(EffectType.HEAL_OVER_TIME, session.activeEffects[0].type)
    }

    @Test
    fun testBuffMaxHpExpiryCapsCurrentHp() {
        val session = createTestSession()
        // Player has 120 HP with a +20 buff active, base maxHp is 100
        session.player = createTestPlayer(currentHp = 120, maxHp = 100)
        session.activeEffects.add(ActiveEffect("Tonic (max HP)", EffectType.BUFF_MAX_HP, 1, 20))

        // Simulate buff expiry
        session.activeEffects.clear()
        val effectiveMax = session.effectiveMaxHp() // Now 100 (no buff)
        val p = session.player!!
        if (p.currentHp > effectiveMax) {
            session.player = p.copy(currentHp = effectiveMax)
        }

        assertEquals(100, session.player!!.currentHp)
    }

    @Test
    fun testBuffMaxHpExpiryDoesNotCapIfHpBelowMax() {
        val session = createTestSession()
        session.player = createTestPlayer(currentHp = 80, maxHp = 100)
        session.activeEffects.add(ActiveEffect("Tonic (max HP)", EffectType.BUFF_MAX_HP, 1, 20))

        // Simulate buff expiry
        session.activeEffects.clear()
        val effectiveMax = session.effectiveMaxHp()
        val p = session.player!!
        if (p.currentHp > effectiveMax) {
            session.player = p.copy(currentHp = effectiveMax)
        }

        // HP was already below base max, so no capping
        assertEquals(80, session.player!!.currentHp)
    }

    @Test
    fun testUseEffectProcessorParsesMultipleTokens() {
        val player = createTestPlayer(currentHp = 50, maxHp = 100)
        val result = UseEffectProcessor.process("heal:20,buff_damage:3:10", player, "Super Potion")
        assertNotNull(result)
        assertEquals(70, result.updatedPlayer.currentHp) // 50 + 20
        assertEquals(1, result.newEffects.size)
        assertEquals(EffectType.BUFF_DAMAGE, result.newEffects[0].type)
    }
}
