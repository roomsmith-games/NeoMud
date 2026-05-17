package com.neomud.server

import com.neomud.shared.model.Direction
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for Tier 1 multiplayer hardening:
 * - Game state mutex (concurrent clients don't corrupt state)
 * - Per-session rate limiter (spam is rejected)
 * - WebSocket frame size cap (oversized frames rejected)
 */
class MultiplayerHardeningTest {

    private fun testDbUrl(): String {
        val tmpFile = File.createTempFile("neomud_test_", ".db")
        tmpFile.deleteOnExit()
        tmpFile.delete()
        return "jdbc:sqlite:${tmpFile.absolutePath}"
    }

    private suspend fun DefaultClientWebSocketSession.consumeCatalogSync() {
        val hello = receiveServerMessage()
        assertIs<ServerMessage.ServerHello>(hello)
        val msg1 = receiveServerMessage()
        assertIs<ServerMessage.ClassCatalogSync>(msg1)
        val msg2 = receiveServerMessage()
        assertIs<ServerMessage.ItemCatalogSync>(msg2)
        val msg3 = receiveServerMessage()
        assertIs<ServerMessage.SkillCatalogSync>(msg3)
        val msg4 = receiveServerMessage()
        assertIs<ServerMessage.RaceCatalogSync>(msg4)
        val msg5 = receiveServerMessage()
        assertIs<ServerMessage.SpellCatalogSync>(msg5)
    }

    private suspend fun DefaultClientWebSocketSession.receiveServerMessage(): ServerMessage {
        val frame = incoming.receive()
        assertTrue(frame is Frame.Text, "Expected text frame")
        return MessageSerializer.decodeServerMessage(frame.readText())
    }

    private fun sendMsg(msg: ClientMessage): Frame.Text =
        Frame.Text(MessageSerializer.encodeClientMessage(msg))

    /** Consume the login response sequence: LoginOk, Welcome, RoomInfo, MapData, InventoryUpdate, RoomItemsUpdate */
    private suspend fun DefaultClientWebSocketSession.consumeLoginSequence(): ServerMessage.LoginOk {
        val loginOk = receiveServerMessage()
        assertIs<ServerMessage.LoginOk>(loginOk)
        receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
        receiveServerMessage() // Welcome SystemMessage (first login on fresh DB)
        receiveServerMessage() // RoomInfo
        receiveServerMessage() // MapData
        receiveServerMessage() // InventoryUpdate
        receiveServerMessage() // RoomItemsUpdate
        return loginOk
    }

    // ── Rate Limiter Tests ──────────────────────────────────────────────

    @Test
    fun `rate limiter allows normal command rate`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        // Register
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Register(
                "ratelimit1", "pass1234", "RateHero", "WARRIOR",
                allocatedStats = Stats(30, 22, 18, 18, 30, 18)
            )))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }

        // Login and send a few commands — all should succeed
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("ratelimit1", "pass1234")))
            consumeLoginSequence()

            // Send 5 pings — well within burst capacity of 20
            repeat(5) {
                send(sendMsg(ClientMessage.Ping))
                val response = receiveServerMessage()
                assertIs<ServerMessage.Pong>(response)
            }
        }
    }

    @Test
    fun `rate limiter rejects burst exceeding capacity`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync() // 5 catalog messages consumed, doesn't count toward rate limit

            // Spam 25 pings rapidly — burst capacity is 20, so some should be rejected
            for (i in 1..25) {
                send(sendMsg(ClientMessage.Ping))
            }

            // Collect all responses — should be a mix of Pong and Error
            var pongCount = 0
            var rateLimitCount = 0
            withTimeout(5000) {
                repeat(25) {
                    val msg = receiveServerMessage()
                    when (msg) {
                        is ServerMessage.Pong -> pongCount++
                        is ServerMessage.Error -> {
                            assertTrue(msg.message.contains("Too many commands"))
                            rateLimitCount++
                        }
                        else -> {} // unexpected but don't fail — just count what matters
                    }
                }
            }

            // Should have gotten some pongs (up to ~20) and some rate limit errors
            assertTrue(pongCount in 15..21, "Expected 15-21 pongs but got $pongCount")
            assertTrue(rateLimitCount >= 4, "Expected at least 4 rate limit errors but got $rateLimitCount")
            assertEquals(25, pongCount + rateLimitCount, "All 25 messages should produce a response")
        }
    }

    // ── Concurrent Client Mutex Tests ───────────────────────────────────

    @Test
    fun `concurrent clients can register and login without corruption`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val classes = listOf("WARRIOR", "THIEF", "CLERIC", "MAGE")
        // Each class gets exactly +10 to each stat above its minimum = 60 CP
        val statSets = listOf(
            Stats(30, 22, 18, 18, 30, 18), // WARRIOR min: 20,12,8,8,20,8
            Stats(20, 30, 22, 18, 20, 25), // THIEF   min: 10,20,12,8,10,15
            Stats(25, 20, 20, 28, 25, 22), // CLERIC  min: 15,10,10,18,15,12
            Stats(16, 18, 32, 25, 18, 20), // MAGE    min: 6,8,22,15,8,10
        )

        // Register 4 players sequentially (registration touches its own session only)
        val wsClient = createClient { install(WebSockets) }
        for (i in 0..3) {
            wsClient.webSocket("/game") {
                consumeCatalogSync()
                send(sendMsg(ClientMessage.Register(
                    "concurrent$i", "pass1234", "CPlayer$i", classes[i],
                    allocatedStats = statSets[i]
                )))
                val response = receiveServerMessage()
                if (response is ServerMessage.AuthError) {
                    throw AssertionError("Registration failed for concurrent$i (${classes[i]}): ${response.reason}")
                }
                assertIs<ServerMessage.RegisterOk>(response)
            }
        }

        // Login all 4 concurrently and have each one move
        coroutineScope {
            val jobs = (0..3).map { i ->
                async {
                    val client = createClient { install(WebSockets) }
                    client.webSocket("/game") {
                        consumeCatalogSync()
                        send(sendMsg(ClientMessage.Login("concurrent$i", "pass1234")))
                        val loginOk = consumeLoginSequence()
                        assertEquals("CPlayer$i", loginOk.player.name)

                        // All start in town:temple — move north to town:square
                        send(sendMsg(ClientMessage.Move(Direction.NORTH)))

                        // Collect messages until we get our MoveOk
                        // (may also receive PlayerEntered from other concurrent clients)
                        var moveOk: ServerMessage.MoveOk? = null
                        withTimeout(5000) {
                            while (moveOk == null) {
                                val msg = receiveServerMessage()
                                if (msg is ServerMessage.MoveOk) moveOk = msg
                            }
                        }
                        assertEquals("Town Square", moveOk!!.room.name)
                        assertEquals(Direction.NORTH, moveOk!!.direction)
                    }
                }
            }
            jobs.awaitAll()
        }
    }

    @Test
    fun `concurrent commands from two clients serialize correctly`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        // Register two players
        for (i in 0..1) {
            wsClient.webSocket("/game") {
                consumeCatalogSync()
                send(sendMsg(ClientMessage.Register(
                    "mutex$i", "pass1234", "MutexPlayer$i",
                    if (i == 0) "WARRIOR" else "THIEF",
                    allocatedStats = if (i == 0) Stats(30, 22, 18, 18, 30, 18) else Stats(20, 30, 22, 18, 20, 25)
                )))
                assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
            }
        }

        // Both login and rapidly send interleaved commands
        coroutineScope {
            val jobs = (0..1).map { i ->
                async {
                    val client = createClient { install(WebSockets) }
                    client.webSocket("/game") {
                        consumeCatalogSync()
                        send(sendMsg(ClientMessage.Login("mutex$i", "pass1234")))
                        consumeLoginSequence()

                        // Rapid-fire: move north, then look, then move south back
                        send(sendMsg(ClientMessage.Move(Direction.NORTH)))
                        // Drain until MoveOk
                        withTimeout(5000) {
                            while (true) {
                                val msg = receiveServerMessage()
                                if (msg is ServerMessage.MoveOk) {
                                    assertEquals("Town Square", msg.room.name)
                                    break
                                }
                            }
                        }

                        send(sendMsg(ClientMessage.Look))
                        withTimeout(5000) {
                            while (true) {
                                val msg = receiveServerMessage()
                                if (msg is ServerMessage.RoomInfo) {
                                    assertEquals("Town Square", msg.room.name)
                                    break
                                }
                            }
                        }

                        send(sendMsg(ClientMessage.Move(Direction.SOUTH)))
                        withTimeout(5000) {
                            while (true) {
                                val msg = receiveServerMessage()
                                if (msg is ServerMessage.MoveOk) {
                                    assertEquals("Temple of the Dawn", msg.room.name)
                                    break
                                }
                            }
                        }
                    }
                }
            }
            jobs.awaitAll()
        }
    }

    // Removed: "mutex contention occurs under concurrent command load"
    // This test asserted contentionCount > 0, which is a probabilistic implementation detail
    // that depends on CPU scheduling. On fast CI runners, commands may serialize without
    // contention, causing random failures. The concurrent correctness tests above verify
    // the actual behavioral contract (no corruption, correct serialization).

    // ── Frame Size Cap Test ─────────────────────────────────────────────

    @Test
    fun `oversized frame closes connection`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()

            // Send a frame well over 128KB
            val oversized = "x".repeat(200_000)
            send(Frame.Text(oversized))

            // The server should close the connection — next receive should fail or return Close
            val result = withTimeout(5000) {
                try {
                    val frame = incoming.receive()
                    // If we get a close frame, that's expected
                    frame is Frame.Close
                } catch (_: Exception) {
                    // Connection closed/reset — also expected
                    true
                }
            }
            assertTrue(result, "Connection should be closed after oversized frame")
        }
    }
}
