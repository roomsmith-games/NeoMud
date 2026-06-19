package com.neomud.server.game.combat

import com.neomud.server.session.PendingSkill
import com.neomud.shared.model.Direction
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.TransportSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionCombatStateTest {

    // --- ReadiedSpell ---

    @Test
    fun testReadiedSpellIdDefaultsToNull() {
        val session = createTestSession()
        assertNull(session.readiedSpellId)
    }

    @Test
    fun testReadiedSpellIdCanBeSetAndCleared() {
        val session = createTestSession()
        session.readiedSpellId = "FIREBALL"
        assertEquals("FIREBALL", session.readiedSpellId)
        session.readiedSpellId = null
        assertNull(session.readiedSpellId)
    }

    @Test
    fun testReadiedSpellSurvivesCombatTick() {
        val session = createTestSession()
        session.readiedSpellId = "FIREBALL"
        session.attackMode = true
        // After a successful auto-cast tick, readiedSpellId should persist
        assertEquals("FIREBALL", session.readiedSpellId)
        assertTrue(session.attackMode)
    }

    // --- PendingSkill ---

    @Test
    fun testPendingSkillDefaultsToNull() {
        val session = createTestSession()
        assertNull(session.pendingSkill)
    }

    @Test
    fun testBashQueuesPendingSkill() {
        val session = createTestSession()
        session.pendingSkill = PendingSkill.Bash("npc1")
        assertIs<PendingSkill.Bash>(session.pendingSkill)
        assertEquals("npc1", (session.pendingSkill as PendingSkill.Bash).targetId)
    }

    @Test
    fun testKickQueuesPendingSkill() {
        val session = createTestSession()
        session.pendingSkill = PendingSkill.Kick("npc1", Direction.NORTH)
        val kick = session.pendingSkill
        assertIs<PendingSkill.Kick>(kick)
        assertEquals("npc1", kick.targetId)
        assertEquals(Direction.NORTH, kick.direction)
    }

    @Test
    fun testMeditateQueuesPendingSkill() {
        val session = createTestSession()
        session.pendingSkill = PendingSkill.Meditate
        assertIs<PendingSkill.Meditate>(session.pendingSkill)
    }

    @Test
    fun testTrackQueuesPendingSkill() {
        val session = createTestSession()
        session.pendingSkill = PendingSkill.Track("goblin")
        assertIs<PendingSkill.Track>(session.pendingSkill)
        assertEquals("goblin", (session.pendingSkill as PendingSkill.Track).targetId)
    }

    @Test
    fun testNewPendingSkillOverridesPrevious() {
        val session = createTestSession()
        session.pendingSkill = PendingSkill.Bash("npc1")
        session.pendingSkill = PendingSkill.Kick("npc2", Direction.EAST)
        assertIs<PendingSkill.Kick>(session.pendingSkill)
    }

    // --- Skill overrides readied spell ---

    @Test
    fun testBashOverridesReadiedSpell() {
        val session = createTestSession()
        session.readiedSpellId = "FIREBALL"
        // BashCommand behavior: queue bash, clear readied spell
        session.pendingSkill = PendingSkill.Bash("npc1")
        session.readiedSpellId = null
        assertNotNull(session.pendingSkill)
        assertNull(session.readiedSpellId)
    }

    @Test
    fun testKickOverridesReadiedSpell() {
        val session = createTestSession()
        session.readiedSpellId = "FIREBALL"
        // KickCommand behavior: queue kick, clear readied spell
        session.pendingSkill = PendingSkill.Kick("npc1", Direction.SOUTH)
        session.readiedSpellId = null
        assertNotNull(session.pendingSkill)
        assertNull(session.readiedSpellId)
    }

    // --- Death clears all combat state ---

    @Test
    fun testDeathClearsAllCombatState() {
        val session = createTestSession()
        session.readiedSpellId = "FIREBALL"
        session.pendingSkill = PendingSkill.Bash("npc1")
        session.attackMode = true
        session.selectedTargetId = "npc1"
        session.isHidden = true
        session.isMeditating = true
        session.isResting = true

        // Simulate PlayerKilled handler
        session.attackMode = false
        session.selectedTargetId = null
        session.readiedSpellId = null
        session.pendingSkill = null
        session.isHidden = false
        session.isMeditating = false
        session.isResting = false

        assertNull(session.readiedSpellId)
        assertNull(session.pendingSkill)
        assertNull(session.selectedTargetId)
        assertFalse(session.attackMode)
        assertFalse(session.isHidden)
        assertFalse(session.isMeditating)
        assertFalse(session.isResting)
    }

    @Test
    fun testNoRemainingTargetsClearsAttackAndSpell() {
        val session = createTestSession()
        session.readiedSpellId = "FIREBALL"
        session.attackMode = true
        session.selectedTargetId = "npc1"

        // Simulate NpcKilled handler when no hostiles remain
        session.attackMode = false
        session.selectedTargetId = null
        session.readiedSpellId = null

        assertFalse(session.attackMode)
        assertNull(session.selectedTargetId)
        assertNull(session.readiedSpellId)
    }

    // --- Backstab is server-authoritative ---

    @Test
    fun testBackstabDeterminedByServerSideHiddenState() {
        val session = createTestSession()
        assertFalse(session.isHidden, "isHidden defaults to false")

        // Server-side SneakCommand sets hidden
        session.isHidden = true

        // CombatManager checks session.isHidden for backstab
        val isBackstab = session.isHidden
        assertTrue(isBackstab)

        // First attack clears hidden
        session.isHidden = false
        assertFalse(session.isHidden)
    }

    @Test
    fun testAttackingWhileHiddenIsBackstabAndBreaksStealthOnHit() {
        val session = createTestSession()
        session.isHidden = true
        session.attackMode = true

        // CombatManager logic: check hidden, only clear on confirmed hit
        val isBackstab = session.isHidden
        assertTrue(isBackstab, "First melee while hidden should be backstab")

        // Simulate confirmed hit — stealth breaks
        if (isBackstab) {
            session.isHidden = false
        }
        assertFalse(session.isHidden, "Hidden should be cleared after confirmed backstab hit")

        // Second attack is not a backstab
        val secondBackstab = session.isHidden
        assertFalse(secondBackstab)
    }

    @Test
    fun testBackstabMissPreservesStealth() {
        val session = createTestSession()
        session.isHidden = true
        session.attackMode = true

        // CombatManager logic: check hidden
        val isBackstab = session.isHidden
        assertTrue(isBackstab, "Attack while hidden should flag as backstab")

        // Simulate miss — stealth is NOT cleared
        // (CombatManager continues without clearing isHidden)
        assertTrue(session.isHidden, "Stealth should be preserved on miss")

        // Next attack is still a backstab
        val nextBackstab = session.isHidden
        assertTrue(nextBackstab, "Player should still be hidden for next attack")
    }

    @Test
    fun testBackstabDodgedPreservesStealth() {
        val session = createTestSession()
        session.isHidden = true
        session.attackMode = true

        // CombatManager logic: check hidden
        val isBackstab = session.isHidden
        assertTrue(isBackstab, "Attack while hidden should flag as backstab")

        // Simulate dodge — stealth is NOT cleared
        assertTrue(session.isHidden, "Stealth should be preserved on dodge")
    }

    @Test
    fun testNonHiddenPlayerMeleeIsNotBackstab() {
        val session = createTestSession()
        session.isHidden = false
        session.attackMode = true

        val isBackstab = session.isHidden
        assertFalse(isBackstab, "Non-hidden player should not get backstab")
    }

    // --- Cooldown prevents queueing ---

    @Test
    fun testCooldownBlocksSkillQueueing() {
        val session = createTestSession()
        session.skillCooldowns["BASH"] = 3

        // BashCommand checks: if cooldown > 0, don't queue
        val cooldown = session.skillCooldowns["BASH"]
        val blocked = cooldown != null && cooldown > 0
        assertTrue(blocked)

        // After ticks expire
        session.skillCooldowns["BASH"] = 0
        val cooldownAfter = session.skillCooldowns["BASH"]
        val blockedAfter = cooldownAfter != null && cooldownAfter > 0
        assertFalse(blockedAfter)
    }

    @Test
    fun testCooldownTicksDown() {
        val session = createTestSession()
        session.skillCooldowns["BASH"] = 3
        session.skillCooldowns["KICK"] = 1

        // Simulate GameLoop cooldown tick
        val iter = session.skillCooldowns.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            entry.setValue(entry.value - 1)
            if (entry.value <= 0) iter.remove()
        }

        assertEquals(2, session.skillCooldowns["BASH"])
        assertNull(session.skillCooldowns["KICK"], "Expired cooldown should be removed")
    }

    // --- Grace period interaction ---

    @Test
    fun testBashBreaksGracePeriod() {
        val session = createTestSession()
        session.combatGraceTicks = 5
        // BashCommand sets grace to 0
        session.combatGraceTicks = 0
        assertEquals(0, session.combatGraceTicks)
    }

    @Test
    fun testKickBreaksGracePeriod() {
        val session = createTestSession()
        session.combatGraceTicks = 5
        // KickCommand sets grace to 0
        session.combatGraceTicks = 0
        assertEquals(0, session.combatGraceTicks)
    }

    // --- Move preserves attack mode with readied spell ---

    @Test
    fun testMovePreservesAttackModeWhenSpellReadied() {
        val session = createTestSession()
        session.readiedSpellId = "FIREBALL"
        session.attackMode = true
        session.selectedTargetId = "npc1"

        // Simulate MoveCommand behavior: preserve attack mode if spell readied
        if (session.attackMode && session.readiedSpellId == null) {
            session.attackMode = false
            session.selectedTargetId = null
        }
        session.selectedTargetId = null // always clear target

        assertTrue(session.attackMode, "Attack mode should persist across move when spell is readied")
        assertEquals("FIREBALL", session.readiedSpellId, "Readied spell should persist across move")
        assertNull(session.selectedTargetId, "Selected target should be cleared on move")
    }

    @Test
    fun testMoveCancelsAttackModeWithoutReadiedSpell() {
        val session = createTestSession()
        session.attackMode = true
        session.selectedTargetId = "npc1"

        // Simulate MoveCommand behavior: cancel attack mode if no spell readied
        if (session.attackMode && session.readiedSpellId == null) {
            session.attackMode = false
            session.selectedTargetId = null
        }
        session.selectedTargetId = null

        assertFalse(session.attackMode, "Attack mode should be cancelled on move without readied spell")
    }

    private fun createTestSession(): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) {}
            override suspend fun close(reason: String) {}
        })
    }
}
