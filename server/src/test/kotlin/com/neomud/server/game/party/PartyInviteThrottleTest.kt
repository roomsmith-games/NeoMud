package com.neomud.server.game.party

import com.neomud.server.game.GameConfig
import com.neomud.server.game.commands.PartyCommand
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.session.TransportSession
import com.neomud.shared.model.Player
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PartyInviteThrottleTest {

    private fun createTestSession(name: String, roomId: String = "test:room1", received: MutableList<com.neomud.shared.protocol.ServerMessage> = mutableListOf()): PlayerSession {
        val session = PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) { received.add(message) }
            override suspend fun close(reason: String) {}
        })
        session.playerName = name
        session.currentRoomId = roomId
        session.player = Player(
            name = name, characterClass = "WARRIOR",
            stats = Stats(strength = 20, agility = 15, intellect = 10, willpower = 10, health = 20, charm = 10),
            race = "HUMAN", level = 1, currentHp = 100, maxHp = 100, currentMp = 50, maxMp = 50,
            currentRoomId = roomId
        )
        return session
    }

    @Test
    fun `cross-room invite succeeds`() = runBlocking {
        val ps = PartyService()
        val sm = SessionManager()
        var tick = 0L
        val pc = PartyCommand(ps, sm, { tick })

        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", "zone:room1", aliceReceived)
        val bob = createTestSession("Bob", "zone:room2", bobReceived)
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        pc.handleInvite(alice, "Bob")

        val aliceMsgs = aliceReceived
        assertTrue(aliceMsgs.any { it is ServerMessage.SystemMessage && "invited" in (it as ServerMessage.SystemMessage).message })
        val bobMsgs = bobReceived
        assertTrue(bobMsgs.any { it is ServerMessage.PartyInviteReceived })
    }

    @Test
    fun `per-target cooldown blocks rapid re-invite`() {
        val ps = PartyService()
        var tick = 0L

        val result1 = ps.createInvite("Alice", "Bob", tick)
        assertTrue(result1 is PartyService.InviteResult.Success)

        tick = 5L
        val result2 = ps.createInvite("Alice", "Bob", tick)
        assertTrue(result2 is PartyService.InviteResult.Throttled)
        assertTrue("wait" in (result2 as PartyService.InviteResult.Throttled).reason.lowercase())

        tick = GameConfig.Party.INVITE_PER_TARGET_COOLDOWN_TICKS + 1
        val result3 = ps.createInvite("Alice", "Bob", tick)
        assertTrue(result3 is PartyService.InviteResult.Success)
    }

    @Test
    fun `decline cooldown blocks re-invite after decline`() {
        val ps = PartyService()
        var tick = 0L

        ps.createInvite("Alice", "Bob", tick)
        ps.recordDecline("Alice", "Bob", tick)

        // Advance past per-target cooldown but within decline cooldown
        tick = GameConfig.Party.INVITE_PER_TARGET_COOLDOWN_TICKS + 1
        val result = ps.createInvite("Alice", "Bob", tick)
        assertTrue(result is PartyService.InviteResult.Throttled)
        assertTrue("declined" in (result as PartyService.InviteResult.Throttled).reason.lowercase())

        tick = GameConfig.Party.INVITE_DECLINE_COOLDOWN_TICKS + 1
        val result2 = ps.createInvite("Alice", "Bob", tick)
        assertTrue(result2 is PartyService.InviteResult.Success)
    }

    @Test
    fun `global rate limit blocks after max invites per window`() {
        val ps = PartyService()
        val tick = 0L

        for (i in 1..GameConfig.Party.INVITE_GLOBAL_MAX_PER_WINDOW) {
            val result = ps.createInvite("Alice", "Target$i", tick)
            assertTrue(result is PartyService.InviteResult.Success, "Invite $i should succeed")
        }

        val result = ps.createInvite("Alice", "TargetExtra", tick)
        assertTrue(result is PartyService.InviteResult.Throttled)
        assertTrue("quickly" in (result as PartyService.InviteResult.Throttled).reason.lowercase())
    }

    @Test
    fun `global rate limit resets after window`() {
        val ps = PartyService()
        var tick = 0L

        for (i in 1..GameConfig.Party.INVITE_GLOBAL_MAX_PER_WINDOW) {
            ps.createInvite("Alice", "Target$i", tick)
        }

        tick = GameConfig.Party.INVITE_GLOBAL_WINDOW_TICKS + 1
        val result = ps.createInvite("Alice", "NewTarget", tick)
        assertTrue(result is PartyService.InviteResult.Success)
    }

    @Test
    fun `ignore blocks party invite`() = runBlocking {
        val ps = PartyService()
        val sm = SessionManager()
        val pc = PartyCommand(ps, sm, { 0L })

        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", "zone:room1", aliceReceived)
        val bob = createTestSession("Bob", "zone:room2")
        bob.ignoredPlayers.add("alice")
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        pc.handleInvite(alice, "Bob")

        val msgs = aliceReceived
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "not online" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `decline calls recordDecline for throttle`() = runBlocking {
        val ps = PartyService()
        val sm = SessionManager()
        var tick = 0L
        val pc = PartyCommand(ps, sm, { tick })

        val alice = createTestSession("Alice")
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bob = createTestSession("Bob", received = bobReceived)
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        ps.createInvite("Alice", "Bob", tick)
        tick = GameConfig.Party.INVITE_PER_TARGET_COOLDOWN_TICKS + 1
        pc.handleDecline(bob, "Alice")

        // Now Alice tries to re-invite immediately — should be throttled by decline cooldown
        tick += 5
        val result = ps.createInvite("Alice", "Bob", tick)
        assertTrue(result is PartyService.InviteResult.Throttled)
    }
}
