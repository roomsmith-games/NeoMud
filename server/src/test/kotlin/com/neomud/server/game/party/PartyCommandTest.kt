package com.neomud.server.game.party

import com.neomud.server.game.commands.PartyCommand
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.session.TransportSession
import com.neomud.shared.model.Player
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class PartyCommandTest {

    private fun createTestSession(name: String, received: MutableList<com.neomud.shared.protocol.ServerMessage> = mutableListOf()): PlayerSession {
        val session = PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) { received.add(message) }
            override suspend fun close(reason: String) {}
        })
        session.playerName = name
        session.currentRoomId = "test:room1"
        session.player = Player(
            name = name,
            characterClass = "WARRIOR",
            stats = Stats(strength = 20, agility = 15, intellect = 10, willpower = 10, health = 20, charm = 10),
            race = "HUMAN",
            level = 1,
            currentHp = 100,
            maxHp = 100,
            currentMp = 50,
            maxMp = 50,
            currentRoomId = "test:room1"
        )
        return session
    }

    private fun setup(): Triple<PartyService, SessionManager, PartyCommand> {
        val ps = PartyService()
        val sm = SessionManager()
        val pc = PartyCommand(ps, sm, { 0L })
        return Triple(ps, sm, pc)
    }

    // ─── Decline ────────────────────────────────────────────

    @Test
    fun `decline with no pending invite sends error`() = runBlocking {
        val (_, sm, pc) = setup()
        val received = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val session = createTestSession("Alice", received)
        sm.addSession("Alice", session)

        pc.handleDecline(session, "Nobody")

        val messages = received
        assertEquals(1, messages.size)
        val msg = messages[0]
        assertIs<ServerMessage.SystemMessage>(msg)
        assertTrue(msg.message.contains("No pending invite"))
    }

    @Test
    fun `decline existing invite sends confirmation`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob", bobReceived)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        pc.handleDecline(bob, "Alice")

        val bobMessages = bobReceived
        assertTrue(bobMessages.any {
            it is ServerMessage.SystemMessage && it.message.contains("You declined")
        })

        val aliceMessages = aliceReceived
        assertTrue(aliceMessages.any {
            it is ServerMessage.SystemMessage && it.message.contains("declined your party invite")
        })
    }

    // ─── Invite validation ──────────────────────────────────

    @Test
    fun `invite self sends error`() = runBlocking {
        val (_, sm, pc) = setup()
        val received = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val session = createTestSession("Alice", received)
        sm.addSession("Alice", session)

        pc.handleInvite(session, "Alice")

        val messages = received
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("can't invite yourself")
        })
    }

    @Test
    fun `invite offline player sends error`() = runBlocking {
        val (_, sm, pc) = setup()
        val received = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val session = createTestSession("Alice", received)
        sm.addSession("Alice", session)

        pc.handleInvite(session, "OfflineGuy")

        val messages = received
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("not online")
        })
    }

    @Test
    fun `invite player in different room succeeds (cross-room invites)`() = runBlocking {
        val (_, sm, pc) = setup()
        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob", bobReceived)
        bob.currentRoomId = "test:room2"
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        pc.handleInvite(alice, "Bob")

        val aliceMsgs = aliceReceived
        assertTrue(aliceMsgs.any {
            it is ServerMessage.SystemMessage && it.message.contains("invited")
        })
        val bobMsgs = bobReceived
        assertTrue(bobMsgs.any { it is ServerMessage.PartyInviteReceived })
    }

    @Test
    fun `successful invite sends messages to both players`() = runBlocking {
        val (_, sm, pc) = setup()
        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob", bobReceived)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        pc.handleInvite(alice, "Bob")

        val aliceMessages = aliceReceived
        assertTrue(aliceMessages.any {
            it is ServerMessage.SystemMessage && it.message.contains("You invited Bob")
        })

        val bobMessages = bobReceived
        assertTrue(bobMessages.any { it is ServerMessage.PartyInviteReceived })
    }

    // ─── Party chat ─────────────────────────────────────────

    @Test
    fun `party chat not in party sends error`() = runBlocking {
        val (_, sm, pc) = setup()
        val received = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val session = createTestSession("Alice", received)
        sm.addSession("Alice", session)

        pc.handleSay(session, "Hello!")

        val messages = received
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("not in a party")
        })
    }

    @Test
    fun `party chat delivers to all members`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob", bobReceived)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)

        pc.handleSay(alice, "Hello party!")

        val aliceMessages = aliceReceived
        assertTrue(aliceMessages.any {
            it is ServerMessage.PartyChatMessage && it.senderName == "Alice" && it.message == "Hello party!"
        })

        val bobMessages = bobReceived
        assertTrue(bobMessages.any {
            it is ServerMessage.PartyChatMessage && it.senderName == "Alice" && it.message == "Hello party!"
        })
    }

    // ─── Leader leave sends PartyLeaderChanged ──────────────

    @Test
    fun `leader leaving 3-member party sends PartyLeaderChanged to remaining`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val charlieReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob", bobReceived)
        val charlie = createTestSession("Charlie", charlieReceived)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)
        sm.addSession("Charlie", charlie)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)
        aliceReceived.clear()
        bobReceived.clear()
        charlieReceived.clear()

        pc.handleLeave(alice)

        val bobMessages = bobReceived
        val leaderChanged = bobMessages.filterIsInstance<ServerMessage.PartyLeaderChanged>().firstOrNull()
        assertNotNull(leaderChanged, "Bob should receive PartyLeaderChanged when leader leaves")

        val charlieMessages = charlieReceived
        val charlieLeaderChanged = charlieMessages.filterIsInstance<ServerMessage.PartyLeaderChanged>().firstOrNull()
        assertNotNull(charlieLeaderChanged, "Charlie should receive PartyLeaderChanged when leader leaves")

        assertEquals(leaderChanged.newLeaderId, charlieLeaderChanged.newLeaderId)
    }

    // ─── Invite includes class/level (#475) ──────────────────

    @Test
    fun `invite sends class and level in PartyInviteReceived`() = runBlocking {
        val (_, sm, pc) = setup()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice")
        alice.player = alice.player!!.copy(level = 12, characterClass = "MAGE")
        val bob = createTestSession("Bob", bobReceived)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        pc.handleInvite(alice, "Bob")

        val bobMessages = bobReceived
        val invite = bobMessages.filterIsInstance<ServerMessage.PartyInviteReceived>().firstOrNull()
        assertNotNull(invite)
        assertEquals(12, invite.inviterLevel)
        assertEquals("MAGE", invite.inviterClass)
    }

    // ─── Leave ──────────────────────────────────────────────

    @Test
    fun `leave when not in party sends error`() = runBlocking {
        val (_, sm, pc) = setup()
        val received = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val session = createTestSession("Alice", received)
        sm.addSession("Alice", session)

        pc.handleLeave(session)

        val messages = received
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("not in a party")
        })
    }

    @Test
    fun `leave 2-member party sends disband with leaver name`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob", bobReceived)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        aliceReceived.clear()
        bobReceived.clear()

        pc.handleLeave(bob)

        val bobMessages = bobReceived
        assertTrue(bobMessages.any {
            it is ServerMessage.SystemMessage && it.message.contains("You left")
        })

        val aliceMessages = aliceReceived
        val disbanded = aliceMessages.filterIsInstance<ServerMessage.PartyDisbanded>().firstOrNull()
        assertNotNull(disbanded)
        assertTrue(disbanded.reason.contains("Bob"), "Disband reason should mention leaver: ${disbanded.reason}")
    }

    // ─── Kick ───────────────────────────────────────────────

    @Test
    fun `kick self sends error`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob")
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        aliceReceived.clear()

        pc.handleKick(alice, "Alice")

        val messages = aliceReceived
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("can't kick yourself")
        })
    }

    @Test
    fun `non-leader kick sends error`() = runBlocking {
        val (ps, sm, pc) = setup()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob", bobReceived)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        bobReceived.clear()

        pc.handleKick(bob, "Alice")

        val messages = bobReceived
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("Only the party leader")
        })
    }

    @Test
    fun `kick reason includes kicker name`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val charlieReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob", bobReceived)
        val charlie = createTestSession("Charlie", charlieReceived)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)
        sm.addSession("Charlie", charlie)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)
        aliceReceived.clear()
        bobReceived.clear()
        charlieReceived.clear()

        pc.handleKick(alice, "Bob")

        val bobMessages = bobReceived
        val disbanded = bobMessages.filterIsInstance<ServerMessage.PartyDisbanded>().firstOrNull()
        assertNotNull(disbanded)
        assertTrue(disbanded.reason.contains("Alice"), "Kick reason should mention kicker: ${disbanded.reason}")
    }

    // ─── Promote (#476) ─────────────────────────────────────

    @Test
    fun `promote sends PartyLeaderChanged to all members`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob", bobReceived)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        aliceReceived.clear()
        bobReceived.clear()

        pc.handlePromote(alice, "Bob")

        val aliceMessages = aliceReceived
        val leaderChanged = aliceMessages.filterIsInstance<ServerMessage.PartyLeaderChanged>().firstOrNull()
        assertNotNull(leaderChanged)
        assertEquals("Bob", leaderChanged.newLeaderId)

        val bobMessages = bobReceived
        val bobLeaderChanged = bobMessages.filterIsInstance<ServerMessage.PartyLeaderChanged>().firstOrNull()
        assertNotNull(bobLeaderChanged)
        assertEquals("Bob", bobLeaderChanged.newLeaderId)
    }

    @Test
    fun `non-leader promote sends error`() = runBlocking {
        val (ps, sm, pc) = setup()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob", bobReceived)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        bobReceived.clear()

        pc.handlePromote(bob, "Alice")

        val messages = bobReceived
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("Only the party leader")
        })
    }

    @Test
    fun `promote target not in party sends error`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob")
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        aliceReceived.clear()

        pc.handlePromote(alice, "Charlie")

        val messages = aliceReceived
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("not in your party")
        })
    }
}
