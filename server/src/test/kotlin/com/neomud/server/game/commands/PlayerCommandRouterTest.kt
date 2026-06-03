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

class PlayerCommandRouterTest {

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

    private fun setup(): Pair<SessionManager, PlayerCommandRouter> {
        val sm = SessionManager()
        val tc = TellCommand(sm)
        val wc = WhoCommand(sm, mapOf("test" to "Test Zone"))
        val router = PlayerCommandRouter(tc, wc, sm)
        return sm to router
    }

    @Test
    fun `routes tell command`() = runBlocking {
        val (sm, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val bobOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        val bob = createTestSession("Bob", bobOut)
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        val handled = router.tryExecute(alice, "/tell Bob hello world")
        assertTrue(handled)

        val bobMsgs = drainMessages(bobOut)
        assertTrue(bobMsgs.any { it is ServerMessage.TellReceived })
    }

    @Test
    fun `routes t shorthand`() = runBlocking {
        val (sm, router) = setup()
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob")
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        val handled = router.tryExecute(alice, "/t Bob hi")
        assertTrue(handled)
    }

    @Test
    fun `routes reply command`() = runBlocking {
        val (sm, router) = setup()
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob")
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        bob.lastTellSender = "Alice"
        val handled = router.tryExecute(bob, "/reply hi back")
        assertTrue(handled)
    }

    @Test
    fun `routes who command`() = runBlocking {
        val (sm, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        val handled = router.tryExecute(alice, "/who")
        assertTrue(handled)

        val msgs = drainMessages(aliceOut)
        assertTrue(msgs.any { it is ServerMessage.WhoList })
    }

    @Test
    fun `routes ignore command`() = runBlocking {
        val (sm, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        val handled = router.tryExecute(alice, "/ignore Bob")
        assertTrue(handled)
        assertTrue("bob" in alice.ignoredPlayers)

        val msgs = drainMessages(aliceOut)
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "ignoring" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `routes unignore command`() = runBlocking {
        val (sm, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        alice.ignoredPlayers.add("bob")
        val handled = router.tryExecute(alice, "/unignore Bob")
        assertTrue(handled)
        assertFalse("bob" in alice.ignoredPlayers)
    }

    @Test
    fun `returns false for unknown command`() = runBlocking {
        val (sm, router) = setup()
        val alice = createTestSession("Alice")
        sm.addSession("Alice", alice, username = "alice")

        val handled = router.tryExecute(alice, "/unknown foo")
        assertFalse(handled)
    }

    @Test
    fun `suggestCommand returns close match`() {
        val (_, router) = setup()
        assertEquals("/tell", router.suggestCommand("/tel"))
        assertEquals("/tell", router.suggestCommand("/tll"))
        assertEquals("/who", router.suggestCommand("/wh"))
        assertEquals("/reply", router.suggestCommand("/rply"))
    }

    @Test
    fun `suggestCommand returns null for distant input`() {
        val (_, router) = setup()
        assertNull(router.suggestCommand("/xyzabc"))
    }

    @Test
    fun `ignore self is rejected`() = runBlocking {
        val (sm, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        router.tryExecute(alice, "/ignore Alice")

        val msgs = drainMessages(aliceOut)
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "yourself" in (it as ServerMessage.SystemMessage).message })
        assertFalse("alice" in alice.ignoredPlayers)
    }

    @Test
    fun `server-side p routes to party say or shows error`() = runBlocking {
        val (sm, router) = setup()
        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        val handled = router.tryExecute(alice, "/p hello party")
        assertTrue(handled)

        val msgs = drainMessages(aliceOut)
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "not in a party" in (it as ServerMessage.SystemMessage).message })
    }
}
