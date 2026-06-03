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

class WhoCommandTest {

    private fun createTestSession(
        name: String,
        roomId: String = "test:room1",
        level: Int = 1,
        characterClass: String = "WARRIOR",
        outgoing: Channel<Frame> = Channel(Channel.UNLIMITED)
    ): PlayerSession {
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
        session.currentRoomId = roomId
        session.player = Player(
            name = name, characterClass = characterClass,
            stats = Stats(strength = 20, agility = 15, intellect = 10, willpower = 10, health = 20, charm = 10),
            race = "HUMAN", level = level, currentHp = 100, maxHp = 100, currentMp = 50, maxMp = 50,
            currentRoomId = roomId
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

    @Test
    fun `who lists online players`() = runBlocking {
        val sm = SessionManager()
        val zoneNames = mapOf("millhaven" to "Millhaven", "gorge" to "Shadow Gorge")
        val wc = WhoCommand(sm, zoneNames)

        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", "millhaven:town_square", 5, "MAGE", aliceOut)
        val bob = createTestSession("Bob", "gorge:entrance", 3, "WARRIOR")
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        wc.execute(alice)

        val msgs = drainMessages(aliceOut)
        val whoList = msgs.filterIsInstance<ServerMessage.WhoList>()
        assertEquals(1, whoList.size)
        assertEquals(2, whoList[0].players.size)
        val names = whoList[0].players.map { it.name }
        assertTrue("Alice" in names)
        assertTrue("Bob" in names)
    }

    @Test
    fun `who list is sorted alphabetically`() = runBlocking {
        val sm = SessionManager()
        val wc = WhoCommand(sm, emptyMap())

        val charlieOut = Channel<Frame>(Channel.UNLIMITED)
        val charlie = createTestSession("Charlie", outgoing = charlieOut)
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob")
        sm.addSession("Charlie", charlie, username = "charlie")
        sm.addSession("Alice", alice, username = "alice")
        sm.addSession("Bob", bob, username = "bob")

        wc.execute(charlie)

        val msgs = drainMessages(charlieOut)
        val players = msgs.filterIsInstance<ServerMessage.WhoList>().first().players
        assertEquals(listOf("Alice", "Bob", "Charlie"), players.map { it.name })
    }

    @Test
    fun `who resolves zone display names`() = runBlocking {
        val sm = SessionManager()
        val zoneNames = mapOf("millhaven" to "Millhaven")
        val wc = WhoCommand(sm, zoneNames)

        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", "millhaven:square", outgoing = aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        wc.execute(alice)

        val msgs = drainMessages(aliceOut)
        val player = msgs.filterIsInstance<ServerMessage.WhoList>().first().players.first()
        assertEquals("Millhaven", player.zone)
    }

    @Test
    fun `who falls back to zone id when no display name`() = runBlocking {
        val sm = SessionManager()
        val wc = WhoCommand(sm, emptyMap())

        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", "unknown_zone:room1", outgoing = aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        wc.execute(alice)

        val msgs = drainMessages(aliceOut)
        val player = msgs.filterIsInstance<ServerMessage.WhoList>().first().players.first()
        assertEquals("unknown_zone", player.zone)
    }

    @Test
    fun `who includes player level and class`() = runBlocking {
        val sm = SessionManager()
        val wc = WhoCommand(sm, emptyMap())

        val aliceOut = Channel<Frame>(Channel.UNLIMITED)
        val alice = createTestSession("Alice", level = 10, characterClass = "MAGE", outgoing = aliceOut)
        sm.addSession("Alice", alice, username = "alice")

        wc.execute(alice)

        val msgs = drainMessages(aliceOut)
        val player = msgs.filterIsInstance<ServerMessage.WhoList>().first().players.first()
        assertEquals(10, player.level)
        assertEquals("MAGE", player.characterClass)
    }
}
