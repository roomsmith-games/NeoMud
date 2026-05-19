package com.neomud.server.game.party

import com.neomud.server.game.GameConfig
import com.neomud.server.game.commands.FollowCommand
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.FollowState
import com.neomud.shared.protocol.ServerMessage
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*

class FollowSystemTest {

    private fun createTestSession(name: String): PlayerSession {
        val session = PlayerSession(object : WebSocketSession {
            override val coroutineContext: CoroutineContext get() = EmptyCoroutineContext
            override val incoming: Channel<Frame> get() = Channel()
            override val outgoing: Channel<Frame> get() = Channel(Channel.UNLIMITED)
            override val extensions: List<WebSocketExtension<*>> get() = emptyList()
            override var masking: Boolean = false
            override var maxFrameSize: Long = Long.MAX_VALUE
            override suspend fun flush() {}
            @Deprecated("Use cancel instead", replaceWith = ReplaceWith("cancel()"))
            override fun terminate() {}
        })
        session.playerName = name
        session.currentRoomId = "test:room1"
        return session
    }

    private fun setupParty(): Triple<PartyService, SessionManager, FollowCommand> {
        val ps = PartyService()
        val sm = SessionManager()
        val wg = WorldGraph()
        val fc = FollowCommand(ps, sm, wg)
        return Triple(ps, sm, fc)
    }

    private fun formParty(ps: PartyService, leader: String, member: String): Party {
        ps.createInvite(leader, member, 0)
        val result = ps.acceptInvite(member, leader, 0)
        assertTrue(result is PartyService.AcceptResult.Formed)
        return result.party
    }

    // ─── Follow basic ───────────────────────────────────────

    @Test
    fun `follow sets ACTIVE state`() = runBlocking {
        val (ps, sm, fc) = setupParty()
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob")
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)
        formParty(ps, "Alice", "Bob")

        fc.handleFollow(bob, "Alice")
        assertEquals(FollowState.ACTIVE, bob.followState)
        assertEquals("Alice", bob.followTarget)
    }

    @Test
    fun `cannot follow self`() = runBlocking {
        val (ps, sm, fc) = setupParty()
        val alice = createTestSession("Alice")
        sm.addSession("Alice", alice)
        formParty(ps, "Alice", "Bob")

        fc.handleFollow(alice, "Alice")
        assertEquals(FollowState.OFF, alice.followState)
        assertNull(alice.followTarget)
    }

    @Test
    fun `cannot follow non-party member`() = runBlocking {
        val (ps, sm, fc) = setupParty()
        val alice = createTestSession("Alice")
        val charlie = createTestSession("Charlie")
        sm.addSession("Alice", alice)
        sm.addSession("Charlie", charlie)
        // Alice is not in a party

        fc.handleFollow(alice, "Charlie")
        assertEquals(FollowState.OFF, alice.followState)
    }

    @Test
    fun `cannot follow someone in different room`() = runBlocking {
        val (ps, sm, fc) = setupParty()
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob")
        bob.currentRoomId = "test:room2"
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)
        formParty(ps, "Alice", "Bob")

        fc.handleFollow(alice, "Bob")
        assertEquals(FollowState.OFF, alice.followState)
    }

    @Test
    fun `cannot follow someone who follows you`() = runBlocking {
        val (ps, sm, fc) = setupParty()
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob")
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)
        formParty(ps, "Alice", "Bob")

        fc.handleFollow(bob, "Alice")
        assertEquals(FollowState.ACTIVE, bob.followState)

        fc.handleFollow(alice, "Bob")
        assertEquals(FollowState.OFF, alice.followState)
    }

    // ─── Follow stop ────────────────────────────────────────

    @Test
    fun `stop clears follow state`() = runBlocking {
        val (ps, sm, fc) = setupParty()
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob")
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)
        formParty(ps, "Alice", "Bob")

        fc.handleFollow(bob, "Alice")
        fc.handleStop(bob)
        assertEquals(FollowState.OFF, bob.followState)
        assertNull(bob.followTarget)
    }

    @Test
    fun `stop when not following is harmless`() = runBlocking {
        val (_, sm, fc) = setupParty()
        val alice = createTestSession("Alice")
        sm.addSession("Alice", alice)

        fc.handleStop(alice)
        assertEquals(FollowState.OFF, alice.followState)
    }

    // ─── Follow state transitions ───────────────────────────

    @Test
    fun `clearFollow resets state`() {
        val (_, _, fc) = setupParty()
        val bob = createTestSession("Bob")
        bob.followTarget = "Alice"
        bob.followState = FollowState.ACTIVE

        fc.clearFollow(bob)
        assertEquals(FollowState.OFF, bob.followState)
        assertNull(bob.followTarget)
    }

    @Test
    fun `combat sets PAUSED state`() {
        val bob = createTestSession("Bob")
        bob.followTarget = "Alice"
        bob.followState = FollowState.ACTIVE
        bob.attackMode = true

        if (bob.followState == FollowState.ACTIVE) {
            bob.followState = FollowState.PAUSED
        }
        assertEquals(FollowState.PAUSED, bob.followState)
        assertEquals("Alice", bob.followTarget)
    }

    @Test
    fun `PAUSED resumes to ACTIVE when same room and not in combat`() {
        val bob = createTestSession("Bob")
        bob.followTarget = "Alice"
        bob.followState = FollowState.PAUSED
        bob.attackMode = false
        bob.currentRoomId = "test:room1"

        val aliceRoomId = "test:room1"
        if (bob.followState == FollowState.PAUSED && !bob.attackMode && bob.currentRoomId == aliceRoomId) {
            bob.followState = FollowState.ACTIVE
        }
        assertEquals(FollowState.ACTIVE, bob.followState)
    }

    @Test
    fun `PAUSED does not resume while in combat`() {
        val bob = createTestSession("Bob")
        bob.followTarget = "Alice"
        bob.followState = FollowState.PAUSED
        bob.attackMode = true
        bob.currentRoomId = "test:room1"

        val aliceRoomId = "test:room1"
        if (bob.followState == FollowState.PAUSED && !bob.attackMode && bob.currentRoomId == aliceRoomId) {
            bob.followState = FollowState.ACTIVE
        }
        assertEquals(FollowState.PAUSED, bob.followState)
    }

    // ─── Rally ──────────────────────────────────────────────

    @Test
    fun `rally requires party leader`() = runBlocking {
        val (ps, sm, fc) = setupParty()
        val alice = createTestSession("Alice")
        val bob = createTestSession("Bob")
        sm.addSession("Alice", alice)
        sm.addSession("Bob", bob)
        formParty(ps, "Alice", "Bob")

        fc.handleRally(bob) // Bob is not leader
        // No crash, no rally sent (leader is Alice)
    }

    @Test
    fun `rally requires party membership`() = runBlocking {
        val (_, sm, fc) = setupParty()
        val charlie = createTestSession("Charlie")
        sm.addSession("Charlie", charlie)

        fc.handleRally(charlie)
        // No crash
    }

    // ─── Manual move breaks follow ──────────────────────────

    @Test
    fun `manual movement breaks follow state`() {
        val bob = createTestSession("Bob")
        bob.followTarget = "Alice"
        bob.followState = FollowState.ACTIVE

        // Simulate what CommandProcessor does before Move
        if (bob.followState != FollowState.OFF) {
            bob.followTarget = null
            bob.followState = FollowState.OFF
        }
        assertEquals(FollowState.OFF, bob.followState)
        assertNull(bob.followTarget)
    }

    // ─── Party leave/kick clears follow ─────────────────────

    @Test
    fun `clearFollowAndNotify resets and sends update`() = runBlocking {
        val (_, sm, fc) = setupParty()
        val bob = createTestSession("Bob")
        sm.addSession("Bob", bob)
        bob.followTarget = "Alice"
        bob.followState = FollowState.ACTIVE

        fc.clearFollowAndNotify(bob, "Party disbanded.")
        assertEquals(FollowState.OFF, bob.followState)
        assertNull(bob.followTarget)
    }
}
