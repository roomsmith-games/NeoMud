package com.neomud.server.game.commands

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

class IgnoreCommandTest {

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
            name = name, characterClass = "WARRIOR",
            stats = Stats(strength = 20, agility = 15, intellect = 10, willpower = 10, health = 20, charm = 10),
            race = "HUMAN", level = 1, currentHp = 100, maxHp = 100, currentMp = 50, maxMp = 50,
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

    private fun setup(): Triple<SessionManager, TellCommand, PlayerCommandRouter> {
        val sm = SessionManager()
        val tc = TellCommand(sm)
        val router = PlayerCommandRouter(tc, WhoCommand(sm, emptyMap()), sm)
        return Triple(sm, tc, router)
    }

    @Test
    fun `ignore adds player to ignore list`() = runBlocking {
        val (sm, _, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        router.tryExecute(alice, "/ignore Bob")

        assertTrue("bob" in alice.ignoredPlayers)
        val msgs = drainMessages(aliceOut)
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "Now ignoring Bob" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `ignore with no args shows usage`() = runBlocking {
        val (sm, _, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        router.tryExecute(alice, "/ignore")

        val msgs = drainMessages(aliceOut)
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "Usage" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `ignore self is rejected`() = runBlocking {
        val (sm, _, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        router.tryExecute(alice, "/ignore Alice")

        assertFalse("alice" in alice.ignoredPlayers)
        val msgs = drainMessages(aliceOut)
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "yourself" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `unignore removes player from ignore list`() = runBlocking {
        val (sm, _, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        alice.ignoredPlayers.add("bob")
        router.tryExecute(alice, "/unignore Bob")

        assertFalse("bob" in alice.ignoredPlayers)
        val msgs = drainMessages(aliceOut)
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "No longer ignoring Bob" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `unignore player not on list shows message`() = runBlocking {
        val (sm, _, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        router.tryExecute(alice, "/unignore Bob")

        val msgs = drainMessages(aliceOut)
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "not on your ignore list" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `unignore with no args shows usage`() = runBlocking {
        val (sm, _, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        router.tryExecute(alice, "/unignore")

        val msgs = drainMessages(aliceOut)
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "Usage" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `ignored player tell is silently blocked`() = runBlocking {
        val (sm, tc, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val bobOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        val bob = createTestSession("Bob", bobOut)
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        router.tryExecute(bob, "/ignore Alice")
        drainMessages(bobOut)

        tc.executeTell(alice, "Bob hello")

        val aliceMsgs = drainMessages(aliceOut)
        assertTrue(aliceMsgs.any { it is ServerMessage.SystemMessage && "not online" in (it as ServerMessage.SystemMessage).message })

        val bobMsgs = drainMessages(bobOut)
        assertTrue(bobMsgs.filterIsInstance<ServerMessage.TellReceived>().isEmpty())
    }

    @Test
    fun `ignored player party invite is blocked`() = runBlocking {
        val (sm, tc, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        val bob = createTestSession("Bob")
        bob.ignoredPlayers.add("alice")
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        val ps = com.neomud.server.game.party.PartyService()
        val pc = PartyCommand(ps, sm, { 0L })
        pc.handleInvite(alice, "Bob")

        val msgs = drainMessages(aliceOut)
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "not online" in (it as ServerMessage.SystemMessage).message })
    }
}
