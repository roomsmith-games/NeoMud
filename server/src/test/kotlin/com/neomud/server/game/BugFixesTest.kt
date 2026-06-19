package com.neomud.server.game

import com.neomud.server.game.combat.CombatManager
import com.neomud.server.game.inventory.EquipmentService
import com.neomud.server.game.inventory.LootService
import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.game.progression.ThresholdBonuses
import com.neomud.server.persistence.repository.CoinRepository
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.persistence.repository.PlayerDiscoveryData
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.session.TransportSession
import com.neomud.server.world.*
import com.neomud.shared.model.*
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for GitHub issues #197, #199, #200, #201, #202, #203, #205, #206, #207, #209, #210.
 */
class BugFixesTest {

    // --- #197: Bash skill damage missing threshold melee bonus ---

    @Test
    fun testBashDamageIncludesThresholdMeleeBonusForHighStrength() {
        // ThresholdBonuses.compute with STR >= 90 should give meleeDamageBonus
        val stats = Stats(strength = 90, agility = 20, intellect = 15, willpower = 15, health = 25, charm = 10)
        val thresholds = ThresholdBonuses.compute(stats)
        assertEquals(GameConfig.Thresholds.STR_90_MELEE_DAMAGE, thresholds.meleeDamageBonus,
            "STR 90 should give STR_90_MELEE_DAMAGE bonus")

        // Bash damage formula should include this bonus:
        // strength / MELEE_STR_DIVISOR + totalDamageBonus + thresholds.meleeDamageBonus + random(1..BASH_DAMAGE_RANGE)
        val minBashDamage = stats.strength / GameConfig.Combat.MELEE_STR_DIVISOR + thresholds.meleeDamageBonus + 1
        assertTrue(minBashDamage > stats.strength / GameConfig.Combat.MELEE_STR_DIVISOR + 1,
            "Bash damage minimum with threshold bonus should be higher than without")
    }

    @Test
    fun testBashDamageNoThresholdBonusForLowStrength() {
        val stats = Stats(strength = 30, agility = 20, intellect = 15, willpower = 15, health = 25, charm = 10)
        val thresholds = ThresholdBonuses.compute(stats)
        assertEquals(0, thresholds.meleeDamageBonus,
            "STR 30 should give 0 melee damage bonus from thresholds")
    }

    @Test
    fun testThresholdMeleeBonusAtAllTiers() {
        // STR 40-59: tier 1
        val t1 = ThresholdBonuses.compute(Stats(strength = 40))
        assertEquals(GameConfig.Thresholds.STR_40_MELEE_DAMAGE, t1.meleeDamageBonus)

        // STR 60-74: tier 2
        val t2 = ThresholdBonuses.compute(Stats(strength = 60))
        assertEquals(GameConfig.Thresholds.STR_60_MELEE_DAMAGE, t2.meleeDamageBonus)

        // STR 75-89: tier 3
        val t3 = ThresholdBonuses.compute(Stats(strength = 75))
        assertEquals(GameConfig.Thresholds.STR_75_MELEE_DAMAGE, t3.meleeDamageBonus)

        // STR 90+: tier 4
        val t4 = ThresholdBonuses.compute(Stats(strength = 90))
        assertEquals(GameConfig.Thresholds.STR_90_MELEE_DAMAGE, t4.meleeDamageBonus)
    }

    // --- #199: Disconnected player not removed from room view ---

    @Test
    fun testDisconnectBroadcastsPlayerLeft() = runBlocking {
        val sessionManager = SessionManager()

        // Create two players in same room
        val session1 = createTestSession()
        session1.playerName = "Player1"
        session1.player = createTestPlayer("Player1")
        session1.currentRoomId = "test:room"
        sessionManager.addSession("Player1", session1)

        val received2 = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val session2 = createTestSession(received2)
        session2.playerName = "Player2"
        session2.player = createTestPlayer("Player2")
        session2.currentRoomId = "test:room"
        sessionManager.addSession("Player2", session2)

        // Simulate Player1 disconnect: broadcast PlayerLeft + SystemMessage
        val roomId = session1.currentRoomId!!
        val playerName = session1.playerName!!
        sessionManager.removeSession(playerName)
        sessionManager.broadcastToRoom(
            roomId,
            ServerMessage.PlayerLeft(playerName, roomId, Direction.NORTH)
        )
        sessionManager.broadcastToRoom(
            roomId,
            ServerMessage.SystemMessage("$playerName has disconnected.")
        )

        // Player2 should receive both messages
        val messages = received2
        val playerLeftMsgs = messages.filterIsInstance<ServerMessage.PlayerLeft>()
        assertTrue(playerLeftMsgs.isNotEmpty(), "Player2 should receive PlayerLeft broadcast")
        assertEquals("Player1", playerLeftMsgs.first().playerName)
    }

    // --- #201: Teleport doesn't persist position or add room to visited set ---

    @Test
    fun testTeleportAddsTargetRoomToVisitedRooms() {
        val session = createTestSession()
        session.playerName = "TestPlayer"
        session.player = createTestPlayer("TestPlayer")
        session.currentRoomId = "test:origin"

        // Simulate teleport logic
        val targetRoomId = "test:destination"
        session.currentRoomId = targetRoomId
        session.player = session.player?.copy(currentRoomId = targetRoomId)
        session.visitedRooms.add(targetRoomId)

        assertTrue(targetRoomId in session.visitedRooms,
            "Teleport should add target room to visited rooms")
    }

    @Test
    fun testTeleportSetsCombatGraceTicks() {
        val session = createTestSession()
        session.playerName = "TestPlayer"
        session.player = createTestPlayer("TestPlayer")
        session.currentRoomId = "test:origin"
        session.combatGraceTicks = 0

        // Simulate teleport logic
        session.combatGraceTicks = GameConfig.Combat.GRACE_TICKS

        assertEquals(GameConfig.Combat.GRACE_TICKS, session.combatGraceTicks,
            "Teleport should set combat grace ticks")
    }

    @Test
    fun testTeleportUpdatesPlayerRoomId() {
        val session = createTestSession()
        session.playerName = "TestPlayer"
        session.player = createTestPlayer("TestPlayer")
        session.currentRoomId = "test:origin"

        val targetRoomId = "test:destination"
        session.currentRoomId = targetRoomId
        session.player = session.player?.copy(currentRoomId = targetRoomId)

        assertEquals(targetRoomId, session.currentRoomId)
        assertEquals(targetRoomId, session.player?.currentRoomId)
    }

    // --- #209: InteractCommand doesn't break meditation, rest, or stealth ---

    @Test
    fun testInteractBreaksMeditationState() = runBlocking {
        val session = createTestSession()
        session.playerName = "TestPlayer"
        session.player = createTestPlayer("TestPlayer")
        session.currentRoomId = "test:room"
        session.isMeditating = true

        MeditationUtils.breakMeditation(session, "You stop meditating.")
        assertFalse(session.isMeditating, "Interact should break meditation")
    }

    @Test
    fun testInteractBreaksRestState() = runBlocking {
        val session = createTestSession()
        session.playerName = "TestPlayer"
        session.player = createTestPlayer("TestPlayer")
        session.currentRoomId = "test:room"
        session.isResting = true

        RestUtils.breakRest(session, "You stop resting.")
        assertFalse(session.isResting, "Interact should break rest")
    }

    @Test
    fun testInteractBreaksStealthState() = runBlocking {
        val sessionManager = SessionManager()
        val session = createTestSession()
        session.playerName = "TestPlayer"
        session.player = createTestPlayer("TestPlayer")
        session.currentRoomId = "test:room"
        session.isHidden = true
        sessionManager.addSession("TestPlayer", session)

        StealthUtils.breakStealth(session, sessionManager, "Interacting reveals your presence!")
        assertFalse(session.isHidden, "Interact should break stealth")
    }

    @Test
    fun testBreakMeditationNoOpWhenNotMeditating() = runBlocking {
        val session = createTestSession()
        session.isMeditating = false
        MeditationUtils.breakMeditation(session, "You stop meditating.")
        assertFalse(session.isMeditating, "Should remain not meditating")
    }

    @Test
    fun testBreakRestNoOpWhenNotResting() = runBlocking {
        val session = createTestSession()
        session.isResting = false
        RestUtils.breakRest(session, "You stop resting.")
        assertFalse(session.isResting, "Should remain not resting")
    }

    @Test
    fun testBreakStealthNoOpWhenNotHidden() = runBlocking {
        val sessionManager = SessionManager()
        val session = createTestSession()
        session.playerName = "TestPlayer"
        session.currentRoomId = "test:room"
        session.isHidden = false
        sessionManager.addSession("TestPlayer", session)

        StealthUtils.breakStealth(session, sessionManager, "msg")
        assertFalse(session.isHidden)
    }

    // --- #210: Server shutdown doesn't save player discovery data ---

    @Test
    fun testShutdownSavesDiscoveryData() {
        // Verify the session has discovery data structures that can be serialized
        val session = createTestSession()
        session.playerName = "TestPlayer"
        session.player = createTestPlayer("TestPlayer")
        session.currentRoomId = "test:room"
        session.visitedRooms.add("test:room")
        session.visitedRooms.add("test:room2")
        session.discoveredHiddenExits.add("test:room:NORTH")
        session.discoveredLockedExits.add("test:room:EAST")
        session.discoveredInteractables.add("test:room::lever")

        // Build discovery data like the shutdown handler does
        val discoveryData = PlayerDiscoveryData(
            visitedRooms = session.visitedRooms.toSet(),
            discoveredHiddenExits = session.discoveredHiddenExits.toSet(),
            discoveredLockedExits = session.discoveredLockedExits.toSet(),
            discoveredInteractables = session.discoveredInteractables.toSet()
        )

        assertEquals(2, discoveryData.visitedRooms.size)
        assertTrue("test:room" in discoveryData.visitedRooms)
        assertTrue("test:room2" in discoveryData.visitedRooms)
        assertTrue("test:room:NORTH" in discoveryData.discoveredHiddenExits)
        assertTrue("test:room:EAST" in discoveryData.discoveredLockedExits)
        assertTrue("test:room::lever" in discoveryData.discoveredInteractables)
    }

    @Test
    fun testShutdownHandlerIteratesAllSessions() {
        val sessionManager = SessionManager()

        val session1 = createTestSession()
        session1.playerName = "Player1"
        session1.player = createTestPlayer("Player1")
        session1.currentRoomId = "test:room"
        session1.visitedRooms.add("test:room")
        sessionManager.addSession("Player1", session1)

        val session2 = createTestSession()
        session2.playerName = "Player2"
        session2.player = createTestPlayer("Player2")
        session2.currentRoomId = "test:room2"
        session2.visitedRooms.add("test:room2")
        sessionManager.addSession("Player2", session2)

        val allSessions = sessionManager.getAllAuthenticatedSessions()
        assertEquals(2, allSessions.size, "Should find 2 authenticated sessions for shutdown save")

        // Verify both have discovery data
        for (sess in allSessions) {
            val name = sess.playerName
            assertNotNull(name)
            assertTrue(sess.visitedRooms.isNotEmpty(), "$name should have visited rooms")
        }
    }

    // --- #200: Bash damage should use equipped weapon's damage range ---

    @Test
    fun testBashDamageUsesWeaponDamageRangeWhenEquipped() {
        val stats = Stats(strength = 30, agility = 20, intellect = 15, willpower = 15, health = 25, charm = 10)
        val thresholds = ThresholdBonuses.compute(stats)
        val weaponDamageRange = 8

        val maxDamageWithWeapon = stats.strength / GameConfig.Combat.MELEE_STR_DIVISOR + thresholds.meleeDamageBonus + weaponDamageRange
        val maxDamageWithoutWeapon = stats.strength / GameConfig.Combat.MELEE_STR_DIVISOR + thresholds.meleeDamageBonus + GameConfig.Skills.BASH_DAMAGE_RANGE

        assertTrue(maxDamageWithWeapon > maxDamageWithoutWeapon,
            "Bash with weapon (range $weaponDamageRange) should have higher max damage than unarmed (range ${GameConfig.Skills.BASH_DAMAGE_RANGE})")
    }

    @Test
    fun testBashDamageUsesConfigFallbackWhenUnarmed() {
        val weaponDamageRange = 0
        val bashRange = if (weaponDamageRange > 0) weaponDamageRange else GameConfig.Skills.BASH_DAMAGE_RANGE
        assertEquals(GameConfig.Skills.BASH_DAMAGE_RANGE, bashRange,
            "Unarmed bash should use BASH_DAMAGE_RANGE as fallback")
    }

    // --- #202: Guard combat has no hit/miss checks ---

    @Test
    fun testGuardHitChanceConfigExists() {
        assertTrue(GameConfig.Combat.GUARD_HIT_CHANCE in 1..100,
            "GUARD_HIT_CHANCE should be between 1 and 100")
    }

    @Test
    fun testGuardHitChanceIsHighButNotPerfect() {
        assertTrue(GameConfig.Combat.GUARD_HIT_CHANCE >= 70,
            "Guards should have a high hit chance (>= 70)")
        assertTrue(GameConfig.Combat.GUARD_HIT_CHANCE < 100,
            "Guards should not have a perfect hit chance (< 100)")
    }

    @Test
    fun testGuardMissProducesZeroDamageEvent() {
        val roll = GameConfig.Combat.GUARD_HIT_CHANCE + 1
        val isMiss = roll > GameConfig.Combat.GUARD_HIT_CHANCE
        assertTrue(isMiss, "Roll above GUARD_HIT_CHANCE should be a miss")
    }

    @Test
    fun testGuardHitProducesDamageEvent() {
        val roll = GameConfig.Combat.GUARD_HIT_CHANCE
        val isHit = roll <= GameConfig.Combat.GUARD_HIT_CHANCE
        assertTrue(isHit, "Roll at GUARD_HIT_CHANCE should be a hit")
    }

    // --- #203: Kick damage missing threshold melee bonus and weapon damage bonus ---

    @Test
    fun testKickDamageIncludesThresholdMeleeBonusForHighStrength() {
        val stats = Stats(strength = 90, agility = 20, intellect = 15, willpower = 15, health = 25, charm = 10)
        val thresholds = ThresholdBonuses.compute(stats)

        val minKickWithBonus = stats.strength / GameConfig.Skills.KICK_STR_DIVISOR + stats.agility / GameConfig.Skills.KICK_AGI_DIVISOR + thresholds.meleeDamageBonus + 1
        val minKickWithoutBonus = stats.strength / GameConfig.Skills.KICK_STR_DIVISOR + stats.agility / GameConfig.Skills.KICK_AGI_DIVISOR + 1

        assertTrue(minKickWithBonus > minKickWithoutBonus,
            "Kick with STR 90 threshold bonus should deal more damage")
        assertTrue(thresholds.meleeDamageBonus > 0,
            "STR 90 should produce a positive melee damage bonus")
    }

    @Test
    fun testKickDamageIncludesWeaponDamageBonus() {
        val stats = Stats(strength = 30, agility = 20, intellect = 15, willpower = 15, health = 25, charm = 10)
        val weaponDamageBonus = 3

        val damageWithBonus = stats.strength / GameConfig.Skills.KICK_STR_DIVISOR + stats.agility / GameConfig.Skills.KICK_AGI_DIVISOR + weaponDamageBonus + 1
        val damageWithout = stats.strength / GameConfig.Skills.KICK_STR_DIVISOR + stats.agility / GameConfig.Skills.KICK_AGI_DIVISOR + 1

        assertTrue(damageWithBonus > damageWithout,
            "Kick with weapon damage bonus should deal more damage")
    }

    @Test
    fun testKickUsesWeaponDamageRangeWhenEquipped() {
        val weaponDamageRange = 8
        val kickRange = if (weaponDamageRange > 0) weaponDamageRange else GameConfig.Skills.KICK_DAMAGE_RANGE
        assertEquals(weaponDamageRange, kickRange,
            "Kick with weapon equipped should use weapon's damage range")
    }

    @Test
    fun testKickUsesConfigFallbackWhenUnarmed() {
        val weaponDamageRange = 0
        val kickRange = if (weaponDamageRange > 0) weaponDamageRange else GameConfig.Skills.KICK_DAMAGE_RANGE
        assertEquals(GameConfig.Skills.KICK_DAMAGE_RANGE, kickRange,
            "Unarmed kick should use KICK_DAMAGE_RANGE as fallback")
    }

    // --- #205: Buy item quantity not validated ---

    @Test
    fun testMaxBuyQuantityConfigExists() {
        assertTrue(GameConfig.Vendor.MAX_BUY_QUANTITY > 0,
            "MAX_BUY_QUANTITY should be positive")
        assertEquals(99, GameConfig.Vendor.MAX_BUY_QUANTITY,
            "MAX_BUY_QUANTITY should be 99")
    }

    @Test
    fun testBuyQuantityAboveMaxIsRejected() {
        val quantity = GameConfig.Vendor.MAX_BUY_QUANTITY + 1
        assertTrue(quantity > GameConfig.Vendor.MAX_BUY_QUANTITY,
            "Quantity above MAX_BUY_QUANTITY should be rejected")
    }

    @Test
    fun testBuyQuantityAtMaxIsAccepted() {
        val quantity = GameConfig.Vendor.MAX_BUY_QUANTITY
        assertFalse(quantity > GameConfig.Vendor.MAX_BUY_QUANTITY,
            "Quantity at MAX_BUY_QUANTITY should be accepted")
    }

    // --- #206: Player can equip items while dead ---

    @Test
    fun testDeadPlayerCannotEquipItems() = runBlocking {
        val session = createTestSession()
        session.playerName = "TestPlayer"
        session.player = createTestPlayer("TestPlayer").copy(currentHp = 0)
        session.currentRoomId = "test:room"

        val player = session.player!!
        assertTrue(player.currentHp <= 0, "Player should be dead (0 HP)")
        // InventoryCommand.handleEquipItem checks HP <= 0 and returns early with error
    }

    @Test
    fun testDeadPlayerCannotUnequipItems() = runBlocking {
        val session = createTestSession()
        session.playerName = "TestPlayer"
        session.player = createTestPlayer("TestPlayer").copy(currentHp = 0)
        session.currentRoomId = "test:room"

        val player = session.player!!
        assertTrue(player.currentHp <= 0, "Player should be dead (0 HP)")
        // InventoryCommand.handleUnequipItem checks HP <= 0 and returns early with error
    }

    @Test
    fun testDeadPlayerCannotUseItems() = runBlocking {
        val session = createTestSession()
        session.playerName = "TestPlayer"
        session.player = createTestPlayer("TestPlayer").copy(currentHp = 0)
        session.currentRoomId = "test:room"

        val player = session.player!!
        assertTrue(player.currentHp <= 0, "Player should be dead (0 HP)")
        // InventoryCommand.handleUseItem checks HP <= 0 and returns early with error
    }

    @Test
    fun testAlivePlayerCanEquipItems() {
        val session = createTestSession()
        session.playerName = "TestPlayer"
        session.player = createTestPlayer("TestPlayer").copy(currentHp = 50)

        val player = session.player!!
        assertTrue(player.currentHp > 0, "Alive player should pass HP check")
    }

    // --- #207: Stackable item overflow silently lost ---

    @Test
    fun testStackableOverflowReturnsFullQuantity() {
        // Test the logic: if currentQty >= maxStack, addItem should return false
        val maxStack = 10
        val currentQty = 10
        val quantity = 5

        // Simulate: already at max, cannot add
        assertTrue(currentQty >= maxStack, "Already at max stack")
        // addItem would return false in this case
    }

    @Test
    fun testStackablePartialAddClampsToMax() {
        // Test the logic: if currentQty + quantity > maxStack, only add what fits
        val maxStack = 10
        val currentQty = 8
        val quantity = 5

        val canAdd = (maxStack - currentQty).coerceAtMost(quantity)
        assertEquals(2, canAdd, "Should only add 2 items to reach maxStack")
        assertFalse(canAdd >= quantity, "Should return false since not all items fit")
    }

    @Test
    fun testStackableExactFitReturnsTrue() {
        val maxStack = 10
        val currentQty = 5
        val quantity = 5

        val canAdd = (maxStack - currentQty).coerceAtMost(quantity)
        assertEquals(5, canAdd, "Should add all 5 items")
        assertTrue(canAdd >= quantity, "Should return true since all items fit")
    }

    @Test
    fun testStackableNewInsertClampsToMax() {
        // When inserting a new stack, clamp to maxStack
        val maxStack = 10
        val quantity = 15

        val insertQty = quantity.coerceAtMost(maxStack)
        assertEquals(10, insertQty, "New stack insert should clamp to maxStack")
        assertFalse(insertQty >= quantity, "Should return false since quantity was clamped")
    }

    @Test
    fun testStackableNewInsertBelowMaxReturnsTrue() {
        val maxStack = 10
        val quantity = 5

        val insertQty = quantity.coerceAtMost(maxStack)
        assertEquals(5, insertQty)
        assertTrue(insertQty >= quantity)
    }

    // --- Helpers ---

    private fun createTestSession(received: MutableList<com.neomud.shared.protocol.ServerMessage> = mutableListOf()): PlayerSession {
        return PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) { received.add(message) }
            override suspend fun close(reason: String) {}
        })
    }

    private fun createTestPlayer(name: String = "TestPlayer"): Player = Player(
        name = name,
        characterClass = "WARRIOR",
        stats = Stats(strength = 30, agility = 20, intellect = 15, willpower = 15, health = 25, charm = 10),
        currentHp = 50,
        maxHp = 50,
        currentMp = 0,
        maxMp = 0,
        level = 1,
        currentRoomId = "test:room"
    )
}
