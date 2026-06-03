package com.neomud.server.game.party

import com.neomud.server.game.GameConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PartyServiceTest {

    private fun service() = PartyService()

    private fun formParty(ps: PartyService, leader: String, member: String, tick: Long = 0L): Party {
        ps.createInvite(leader, member, tick)
        val result = ps.acceptInvite(member, leader, tick)
        assertTrue(result is PartyService.AcceptResult.Formed)
        return result.party
    }

    // ─── Invite ─────────────────────────────────────────────

    @Test
    fun `invite creates pending invite`() {
        val ps = service()
        val result = ps.createInvite("Alice", "Bob", 0)
        assertTrue(result is PartyService.InviteResult.Success)
        assertEquals(1, (result as PartyService.InviteResult.Success).partySize)
    }

    @Test
    fun `cannot invite self`() {
        val ps = service()
        val result = ps.createInvite("Alice", "Alice", 0)
        assertTrue(result is PartyService.InviteResult.CannotInviteSelf)
    }

    @Test
    fun `cannot invite someone already in a party`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        val result = ps.createInvite("Charlie", "Bob", 1)
        assertTrue(result is PartyService.InviteResult.TargetAlreadyInParty)
    }

    @Test
    fun `non-leader cannot invite`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        val result = ps.createInvite("Bob", "Charlie", 1)
        assertTrue(result is PartyService.InviteResult.NotLeader)
    }

    @Test
    fun `party full prevents invite`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)
        ps.createInvite("Alice", "Dave", 2)
        ps.acceptInvite("Dave", "Alice", 2)
        // Party is now at MAX_SIZE (4)
        val result = ps.createInvite("Alice", "Eve", 3)
        assertTrue(result is PartyService.InviteResult.PartyFull)
    }

    @Test
    fun `invite reports current party size`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        val result = ps.createInvite("Alice", "Charlie", 1)
        assertTrue(result is PartyService.InviteResult.Success)
        assertEquals(2, (result as PartyService.InviteResult.Success).partySize)
    }

    // ─── Accept ─────────────────────────────────────────────

    @Test
    fun `accept forms party with two members`() {
        val ps = service()
        ps.createInvite("Alice", "Bob", 0)
        val result = ps.acceptInvite("Bob", "Alice", 0)
        assertTrue(result is PartyService.AcceptResult.Formed)
        val party = (result as PartyService.AcceptResult.Formed).party
        assertEquals(2, party.members.size)
        assertEquals("Alice", party.leaderId)
        assertTrue(ps.isInParty("Alice"))
        assertTrue(ps.isInParty("Bob"))
    }

    @Test
    fun `accept joins existing party`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        ps.createInvite("Alice", "Charlie", 1)
        val result = ps.acceptInvite("Charlie", "Alice", 1)
        assertTrue(result is PartyService.AcceptResult.Joined)
        val party = (result as PartyService.AcceptResult.Joined).party
        assertEquals(3, party.members.size)
    }

    @Test
    fun `accept with no invite fails`() {
        val ps = service()
        val result = ps.acceptInvite("Bob", "Alice", 0)
        assertTrue(result is PartyService.AcceptResult.NoInvite)
    }

    @Test
    fun `expired invite fails`() {
        val ps = service()
        ps.createInvite("Alice", "Bob", 0)
        val result = ps.acceptInvite("Bob", "Alice", GameConfig.Party.INVITE_EXPIRY_TICKS + 1L)
        assertTrue(result is PartyService.AcceptResult.Expired)
    }

    @Test
    fun `accept when already in party fails`() {
        val ps = service()
        // Create invite before Bob joins a party
        ps.createInvite("Charlie", "Bob", 0)
        formParty(ps, "Alice", "Bob")
        // Bob is now in Alice's party, tries to accept Charlie's stale invite
        val result = ps.acceptInvite("Bob", "Charlie", 0)
        assertTrue(result is PartyService.AcceptResult.AlreadyInParty)
    }

    // ─── Decline ────────────────────────────────────────────

    @Test
    fun `decline removes invite`() {
        val ps = service()
        ps.createInvite("Alice", "Bob", 0)
        val removed = ps.declineInvite("Bob", "Alice")
        assertTrue(removed)
        val result = ps.acceptInvite("Bob", "Alice", 0)
        assertTrue(result is PartyService.AcceptResult.NoInvite)
    }

    @Test
    fun `decline with no pending invite returns false`() {
        val ps = service()
        assertFalse(ps.declineInvite("Bob", "Alice"))
    }

    @Test
    fun `decline wrong inviter returns false`() {
        val ps = service()
        ps.createInvite("Alice", "Bob", 0)
        assertFalse(ps.declineInvite("Bob", "Charlie"))
        // Original invite still valid
        val result = ps.acceptInvite("Bob", "Alice", 0)
        assertTrue(result is PartyService.AcceptResult.Formed)
    }

    // ─── Leave ──────────────────────────────────────────────

    @Test
    fun `leave two-person party disbands`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        val result = ps.leaveParty("Bob")
        assertTrue(result is PartyService.LeaveResult.Disbanded)
        assertFalse(ps.isInParty("Alice"))
        assertFalse(ps.isInParty("Bob"))
    }

    @Test
    fun `leave three-person party stays intact`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)

        val result = ps.leaveParty("Charlie")
        assertTrue(result is PartyService.LeaveResult.Left)
        assertNull((result as PartyService.LeaveResult.Left).newLeader)
        assertTrue(ps.isInParty("Alice"))
        assertTrue(ps.isInParty("Bob"))
        assertFalse(ps.isInParty("Charlie"))
    }

    @Test
    fun `leader leaves promotes next member`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)

        val result = ps.leaveParty("Alice")
        assertTrue(result is PartyService.LeaveResult.Left)
        assertEquals("Bob", (result as PartyService.LeaveResult.Left).newLeader)
        val party = ps.getPartyForPlayer("Bob")
        assertNotNull(party)
        assertEquals("Bob", party.leaderId)
    }

    @Test
    fun `leave when not in party`() {
        val ps = service()
        val result = ps.leaveParty("Alice")
        assertTrue(result is PartyService.LeaveResult.NotInParty)
    }

    @Test
    fun `double leave is idempotent`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        ps.leaveParty("Bob")
        val result = ps.leaveParty("Bob")
        assertTrue(result is PartyService.LeaveResult.NotInParty)
    }

    // ─── Kick ───────────────────────────────────────────────

    @Test
    fun `leader kicks member`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)

        val result = ps.kickMember("Alice", "Bob")
        assertTrue(result is PartyService.KickResult.Kicked)
        assertFalse(ps.isInParty("Bob"))
        assertTrue(ps.isInParty("Alice"))
        assertTrue(ps.isInParty("Charlie"))
    }

    @Test
    fun `kick in two-person party disbands`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        val result = ps.kickMember("Alice", "Bob")
        assertTrue(result is PartyService.KickResult.Disbanded)
        assertFalse(ps.isInParty("Alice"))
        assertFalse(ps.isInParty("Bob"))
    }

    @Test
    fun `non-leader cannot kick`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        val result = ps.kickMember("Bob", "Alice")
        assertTrue(result is PartyService.KickResult.NotLeader)
    }

    @Test
    fun `cannot kick self`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        val result = ps.kickMember("Alice", "Alice")
        assertTrue(result is PartyService.KickResult.CannotKickSelf)
    }

    @Test
    fun `cannot kick non-member`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        val result = ps.kickMember("Alice", "Charlie")
        assertTrue(result is PartyService.KickResult.TargetNotInParty)
    }

    // ─── Disconnect ───────────────────────────────────────────

    @Test
    fun `disconnect immediately disbands two-person party`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        val result = ps.markDisconnected("Bob")

        assertTrue(result is PartyService.DisconnectResult.Disbanded)
        assertEquals(listOf("Alice"), (result as PartyService.DisconnectResult.Disbanded).otherMembers)
        assertFalse(ps.isInParty("Alice"))
        assertFalse(ps.isInParty("Bob"))
    }

    @Test
    fun `disconnect in 3-person party starts grace period`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)

        val result = ps.markDisconnected("Bob", 3)
        assertTrue(result is PartyService.DisconnectResult.GracePeriod)
        assertTrue(ps.isInParty("Bob"))

        ps.tickGracePeriods() // 3→2
        assertTrue(ps.isInParty("Bob"))
        ps.tickGracePeriods() // 2→1
        assertTrue(ps.isInParty("Bob"))
        ps.tickGracePeriods() // 1→0, removed
        assertFalse(ps.isInParty("Bob"))
    }

    @Test
    fun `reconnect during grace period preserves party`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)

        ps.markDisconnected("Bob", 10)
        val reconnected = ps.tryReconnect("Bob")
        assertNotNull(reconnected)
        assertTrue(ps.isInParty("Bob"))
    }

    @Test
    fun `grace expiry promotes leader if leader disconnects`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)

        val result = ps.markDisconnected("Alice", 1)
        assertTrue(result is PartyService.DisconnectResult.GracePeriod)
        assertEquals("Bob", (result as PartyService.DisconnectResult.GracePeriod).newLeader)

        ps.tickGracePeriods()
        val party = ps.getPartyForPlayer("Bob")
        assertNotNull(party)
        assertEquals("Bob", party.leaderId)
    }

    @Test
    fun `grace expiry disbands when party drops to one member`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)

        ps.markDisconnected("Bob", 1)
        ps.markDisconnected("Charlie", 1)
        val expiries = ps.tickGracePeriods()

        assertFalse(ps.isInParty("Alice"))
        assertTrue(expiries.any { it.lastRemainingMember == "Alice" })
    }

    @Test
    fun `disconnect returns NotInParty when player has no party`() {
        val ps = service()
        val result = ps.markDisconnected("Alice")
        assertTrue(result is PartyService.DisconnectResult.NotInParty)
    }

    @Test
    fun `leader disconnect in 3-person party promotes next member`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)

        val result = ps.markDisconnected("Alice")
        assertTrue(result is PartyService.DisconnectResult.GracePeriod)
        val gp = result as PartyService.DisconnectResult.GracePeriod
        assertNotNull(gp.newLeader)
        assertEquals("Bob", gp.newLeader)
    }

    // ─── Invite expiry ──────────────────────────────────────

    @Test
    fun `tick expires stale invites`() {
        val ps = service()
        ps.createInvite("Alice", "Bob", 0)
        val expired = ps.tickExpireInvites(GameConfig.Party.INVITE_EXPIRY_TICKS + 1L)
        assertEquals(1, expired.size)
        assertEquals("Bob", expired[0].targetName)
    }

    // ─── getMembersInRoom ───────────────────────────────────

    @Test
    fun `getMembersInRoom returns members at same location`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        val rooms = mapOf("Alice" to "town:square", "Bob" to "town:market")
        val inRoom = ps.getMembersInRoom("Alice", "town:square") { rooms[it] }
        assertEquals(listOf("Alice"), inRoom)
    }

    // ─── getPartyMembers ────────────────────────────────────

    @Test
    fun `getPartyMembers returns all members`() {
        val ps = service()
        formParty(ps, "Alice", "Bob")
        assertEquals(listOf("Alice", "Bob"), ps.getPartyMembers("Alice"))
        assertEquals(listOf("Alice", "Bob"), ps.getPartyMembers("Bob"))
    }

    @Test
    fun `getPartyMembers returns empty for non-party player`() {
        val ps = service()
        assertEquals(emptyList(), ps.getPartyMembers("Alice"))
    }

    // ─── Loot rotation ──────────────────────────────────────

    @Test
    fun `nextLootRecipient rotates through members`() {
        val ps = service()
        val party = formParty(ps, "Alice", "Bob")
        val first = ps.nextLootRecipient(party.id)
        val second = ps.nextLootRecipient(party.id)
        val third = ps.nextLootRecipient(party.id)
        assertEquals("Alice", first)
        assertEquals("Bob", second)
        assertEquals("Alice", third)
    }

    // ─── Max party size ─────────────────────────────────────

    @Test
    fun `party cannot exceed MAX_SIZE`() {
        val ps = service()
        formParty(ps, "A", "B", tick = 0)
        // Space invites across throttle windows to avoid rate limiting
        ps.createInvite("A", "C", 100)
        ps.acceptInvite("C", "A", 100)
        ps.createInvite("A", "D", 200)
        ps.acceptInvite("D", "A", 200)
        // Party now at max (4). Create invite for E — should fail at invite time.
        val inviteResult = ps.createInvite("A", "E", 300)
        assertTrue(inviteResult is PartyService.InviteResult.PartyFull)
    }
}
