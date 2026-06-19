package com.neomud.server.game.commands

import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.session.TransportSession
import com.neomud.shared.model.Player
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class TellCommandTest {

    private fun createTestSession(name: String, received: MutableList<com.neomud.shared.protocol.ServerMessage> = mutableListOf()): PlayerSession {
        val session = PlayerSession(object : TransportSession {
            override suspend fun sendMessage(message: com.neomud.shared.protocol.ServerMessage) { received.add(message) }
            override suspend fun close(reason: String) {}
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

    @Test
    fun `tell sends message to target and echo to sender`() = runBlocking {
        val sm = SessionManager()
        val tc = TellCommand(sm)

        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob", bobReceived)
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        tc.executeTell(alice, "Bob hello there")

        val bobMsgs = bobReceived
        val aliceMsgs = aliceReceived

        val tellReceived = bobMsgs.filterIsInstance<ServerMessage.TellReceived>()
        assertEquals(1, tellReceived.size)
        assertEquals("Alice", tellReceived[0].senderName)
        assertEquals("hello there", tellReceived[0].message)

        val tellSent = aliceMsgs.filterIsInstance<ServerMessage.TellSent>()
        assertEquals(1, tellSent.size)
        assertEquals("Bob", tellSent[0].targetName)
        assertEquals("hello there", tellSent[0].message)
    }

    @Test
    fun `tell sets lastTellSender on target`() = runBlocking {
        val sm = SessionManager()
        val tc = TellCommand(sm)

        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob")
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        assertNull(bob.lastTellSender)
        tc.executeTell(alice, "Bob hey")
        assertEquals("Alice", bob.lastTellSender)
    }

    @Test
    fun `tell to offline player shows error`() = runBlocking {
        val sm = SessionManager()
        val tc = TellCommand(sm)

        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        sm.addSession("Alice", alice, username = "alice")

        tc.executeTell(alice, "Nobody hello")

        val msgs = aliceReceived
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "not online" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `tell to self is rejected`() = runBlocking {
        val sm = SessionManager()
        val tc = TellCommand(sm)

        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        sm.addSession("Alice", alice, username = "alice")

        tc.executeTell(alice, "Alice hello")

        val msgs = aliceReceived
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "yourself" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `tell with empty message shows usage`() = runBlocking {
        val sm = SessionManager()
        val tc = TellCommand(sm)

        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        sm.addSession("Alice", alice, username = "alice")

        tc.executeTell(alice, "Bob")

        val msgs = aliceReceived
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "Usage" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `tell with no args shows usage`() = runBlocking {
        val sm = SessionManager()
        val tc = TellCommand(sm)

        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        sm.addSession("Alice", alice, username = "alice")

        tc.executeTell(alice, "")

        val msgs = aliceReceived
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "Usage" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `tell blocked by ignore shows not online`() = runBlocking {
        val sm = SessionManager()
        val tc = TellCommand(sm)

        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob", bobReceived)
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        bob.ignoredPlayers.add("alice")

        tc.executeTell(alice, "Bob hello")

        val aliceMsgs = aliceReceived
        assertTrue(aliceMsgs.any { it is ServerMessage.SystemMessage && "not online" in (it as ServerMessage.SystemMessage).message })

        val bobMsgs = bobReceived
        assertTrue(bobMsgs.filterIsInstance<ServerMessage.TellReceived>().isEmpty())
    }

    @Test
    fun `reply sends to last tell sender`() = runBlocking {
        val sm = SessionManager()
        val tc = TellCommand(sm)

        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        val bob = createTestSession("Bob", bobReceived)
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        // Alice tells Bob
        tc.executeTell(alice, "Bob hey")
        aliceReceived.clear()
        bobReceived.clear()

        // Bob replies
        tc.executeReply(bob, "hey back")

        val aliceMsgs = aliceReceived
        val tellReceived = aliceMsgs.filterIsInstance<ServerMessage.TellReceived>()
        assertEquals(1, tellReceived.size)
        assertEquals("Bob", tellReceived[0].senderName)
        assertEquals("hey back", tellReceived[0].message)
    }

    @Test
    fun `reply with no prior tell shows error`() = runBlocking {
        val sm = SessionManager()
        val tc = TellCommand(sm)

        val aliceReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice", aliceReceived)
        sm.addSession("Alice", alice, username = "alice")

        tc.executeReply(alice, "hello")

        val msgs = aliceReceived
        assertTrue(msgs.any { it is ServerMessage.SystemMessage && "No one has sent you" in (it as ServerMessage.SystemMessage).message })
    }

    @Test
    fun `tell sanitizes control characters`() = runBlocking {
        val sm = SessionManager()
        val tc = TellCommand(sm)

        val bobReceived = mutableListOf<com.neomud.shared.protocol.ServerMessage>()
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob", bobReceived)
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        tc.executeTell(alice, "Bob hello world")

        val bobMsgs = bobReceived
        val tell = bobMsgs.filterIsInstance<ServerMessage.TellReceived>().first()
        assertFalse(tell.message.contains(' '))
        assertEquals("helloworld", tell.message)
    }
}
