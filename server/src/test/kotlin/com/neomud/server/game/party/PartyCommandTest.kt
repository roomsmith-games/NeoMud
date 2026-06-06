package com.neomud.server.game.party

import com.neomud.server.game.commands.PartyCommand
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.shared.model.Player
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

class PartyCommandTest {

    private fun createTestSession(name: String, outgoing: Channel<Frame> = Channel(Channel.UNLIMITED)): PlayerSession {
        val session = PlayerSession(object : WebSocketSession {
            override val coroutineContext: CoroutineContext get() = EmptyCoroutineContext
            override val incoming: Channel<Frame> get() = Channel()
            override val outgoing: Channel<Frame> get() = outgoing
            override val extensions: List<WebSocketExtension<*>> get() = emptyList()
            override var masking: Boolean = false
            override var maxFrameSize: Long = Long.MAX_VALUE
            override suspend fun flush() {}
            @Deprecated("Use cancel instead", replaceWith = ReplaceWith("cancel()"))
            override fun terminate() {}
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

    private fun drainMessages(channel: Channel<Frame>): List<ServerMessage> {
        val messages = mutableListOf<ServerMessage>()
        while (true) {
            val frame = channel.tryReceive().getOrNull() ?: break
            if (frame is Frame.Text) {
                messages.add(MessageSerializer.decodeServerMessage(frame.readText()))
            }
        }
        return messages
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
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession("Alice", outgoing)
        sm.addSession("Alice", session)

        pc.handleDecline(session, "Nobody")

        val messages = drainMessages(outgoing)
        assertEquals(1, messages.size)
        val msg = messages[0]
        assertIs<ServerMessage.SystemMessage>(msg)
        assertTrue(msg.message.contains("No pending invite"))
    }

    @Test
    fun `decline existing invite sends confirmation`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val bobOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        val bob = createTestSession("Bob", bobOut)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        pc.handleDecline(bob, "Alice")

        val bobMessages = drainMessages(bobOut)
        assertTrue(bobMessages.any {
            it is ServerMessage.SystemMessage && it.message.contains("You declined")
        })

        val aliceMessages = drainMessages(aliceOut)
        assertTrue(aliceMessages.any {
            it is ServerMessage.SystemMessage && it.message.contains("declined your party invite")
        })
    }

    // ─── Invite validation ──────────────────────────────────

    @Test
    fun `invite self sends error`() = runBlocking {
        val (_, sm, pc) = setup()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession("Alice", outgoing)
        sm.addSession("Alice", session)

        pc.handleInvite(session, "Alice")

        val messages = drainMessages(outgoing)
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("can't invite yourself")
        })
    }

    @Test
    fun `invite offline player sends error`() = runBlocking {
        val (_, sm, pc) = setup()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession("Alice", outgoing)
        sm.addSession("Alice", session)

        pc.handleInvite(session, "OfflineGuy")

        val messages = drainMessages(outgoing)
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("not online")
        })
    }

    @Test
    fun `invite player in different room succeeds (cross-room invites)`() = runBlocking {
        val (_, sm, pc) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val bobOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        val bob = createTestSession("Bob", bobOut)
        bob.currentRoomId = "test:room2"
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        pc.handleInvite(alice, "Bob")

        val aliceMsgs = drainMessages(aliceOut)
        assertTrue(aliceMsgs.any {
            it is ServerMessage.SystemMessage && it.message.contains("invited")
        })
        val bobMsgs = drainMessages(bobOut)
        assertTrue(bobMsgs.any { it is ServerMessage.PartyInviteReceived })
    }

    @Test
    fun `successful invite sends messages to both players`() = runBlocking {
        val (_, sm, pc) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val bobOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        val bob = createTestSession("Bob", bobOut)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        pc.handleInvite(alice, "Bob")

        val aliceMessages = drainMessages(aliceOut)
        assertTrue(aliceMessages.any {
            it is ServerMessage.SystemMessage && it.message.contains("You invited Bob")
        })

        val bobMessages = drainMessages(bobOut)
        assertTrue(bobMessages.any { it is ServerMessage.PartyInviteReceived })
    }

    // ─── Party chat ─────────────────────────────────────────

    @Test
    fun `party chat not in party sends error`() = runBlocking {
        val (_, sm, pc) = setup()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession("Alice", outgoing)
        sm.addSession("Alice", session)

        pc.handleSay(session, "Hello!")

        val messages = drainMessages(outgoing)
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("not in a party")
        })
    }

    @Test
    fun `party chat delivers to all members`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val bobOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        val bob = createTestSession("Bob", bobOut)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)

        pc.handleSay(alice, "Hello party!")

        val aliceMessages = drainMessages(aliceOut)
        assertTrue(aliceMessages.any {
            it is ServerMessage.PartyChatMessage && it.senderName == "Alice" && it.message == "Hello party!"
        })

        val bobMessages = drainMessages(bobOut)
        assertTrue(bobMessages.any {
            it is ServerMessage.PartyChatMessage && it.senderName == "Alice" && it.message == "Hello party!"
        })
    }

    // ─── Invite includes class/level (#475) ──────────────────

    @Test
    fun `invite sends class and level in PartyInviteReceived`() = runBlocking {
        val (_, sm, pc) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val bobOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        alice.player = alice.player!!.copy(level = 12, characterClass = "MAGE")
        val bob = createTestSession("Bob", bobOut)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        pc.handleInvite(alice, "Bob")

        val bobMessages = drainMessages(bobOut)
        val invite = bobMessages.filterIsInstance<ServerMessage.PartyInviteReceived>().firstOrNull()
        assertNotNull(invite)
        assertEquals(12, invite.inviterLevel)
        assertEquals("MAGE", invite.inviterClass)
    }

    // ─── Leave ──────────────────────────────────────────────

    @Test
    fun `leave when not in party sends error`() = runBlocking {
        val (_, sm, pc) = setup()
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session = createTestSession("Alice", outgoing)
        sm.addSession("Alice", session)

        pc.handleLeave(session)

        val messages = drainMessages(outgoing)
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("not in a party")
        })
    }

    @Test
    fun `leave 2-member party sends disband with leaver name`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val bobOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        val bob = createTestSession("Bob", bobOut)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        drainMessages(aliceOut)
        drainMessages(bobOut)

        pc.handleLeave(bob)

        val bobMessages = drainMessages(bobOut)
        assertTrue(bobMessages.any {
            it is ServerMessage.SystemMessage && it.message.contains("You left")
        })

        val aliceMessages = drainMessages(aliceOut)
        val disbanded = aliceMessages.filterIsInstance<ServerMessage.PartyDisbanded>().firstOrNull()
        assertNotNull(disbanded)
        assertTrue(disbanded.reason.contains("Bob"), "Disband reason should mention leaver: ${disbanded.reason}")
    }

    // ─── Kick ───────────────────────────────────────────────

    @Test
    fun `kick self sends error`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        val bob = createTestSession("Bob")
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        drainMessages(aliceOut)

        pc.handleKick(alice, "Alice")

        val messages = drainMessages(aliceOut)
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("can't kick yourself")
        })
    }

    @Test
    fun `non-leader kick sends error`() = runBlocking {
        val (ps, sm, pc) = setup()
        val bobOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob", bobOut)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        drainMessages(bobOut)

        pc.handleKick(bob, "Alice")

        val messages = drainMessages(bobOut)
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("Only the party leader")
        })
    }

    @Test
    fun `kick reason includes kicker name`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val bobOut = Channel<Frame>(Channel.UNLIMITED)
        val charlieOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        val bob = createTestSession("Bob", bobOut)
        val charlie = createTestSession("Charlie", charlieOut)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)
        sm.addSession("Charlie", charlie)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        ps.createInvite("Alice", "Charlie", 1)
        ps.acceptInvite("Charlie", "Alice", 1)
        drainMessages(aliceOut)
        drainMessages(bobOut)
        drainMessages(charlieOut)

        pc.handleKick(alice, "Bob")

        val bobMessages = drainMessages(bobOut)
        val disbanded = bobMessages.filterIsInstance<ServerMessage.PartyDisbanded>().firstOrNull()
        assertNotNull(disbanded)
        assertTrue(disbanded.reason.contains("Alice"), "Kick reason should mention kicker: ${disbanded.reason}")
    }

    // ─── Promote (#476) ─────────────────────────────────────

    @Test
    fun `promote sends PartyLeaderChanged to all members`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val bobOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        val bob = createTestSession("Bob", bobOut)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        drainMessages(aliceOut)
        drainMessages(bobOut)

        pc.handlePromote(alice, "Bob")

        val aliceMessages = drainMessages(aliceOut)
        val leaderChanged = aliceMessages.filterIsInstance<ServerMessage.PartyLeaderChanged>().firstOrNull()
        assertNotNull(leaderChanged)
        assertEquals("Bob", leaderChanged.newLeaderId)

        val bobMessages = drainMessages(bobOut)
        val bobLeaderChanged = bobMessages.filterIsInstance<ServerMessage.PartyLeaderChanged>().firstOrNull()
        assertNotNull(bobLeaderChanged)
        assertEquals("Bob", bobLeaderChanged.newLeaderId)
    }

    @Test
    fun `non-leader promote sends error`() = runBlocking {
        val (ps, sm, pc) = setup()
        val bobOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob", bobOut)
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        drainMessages(bobOut)

        pc.handlePromote(bob, "Alice")

        val messages = drainMessages(bobOut)
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("Only the party leader")
        })
    }

    @Test
    fun `promote target not in party sends error`() = runBlocking {
        val (ps, sm, pc) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        val bob = createTestSession("Bob")
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)

        ps.createInvite("Alice", "Bob", 0)
        ps.acceptInvite("Bob", "Alice", 0)
        drainMessages(aliceOut)

        pc.handlePromote(alice, "Charlie")

        val messages = drainMessages(aliceOut)
        assertTrue(messages.any {
            it is ServerMessage.SystemMessage && it.message.contains("not in your party")
        })
    }
}
