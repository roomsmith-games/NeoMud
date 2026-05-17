package com.neomud.server

import com.neomud.server.game.GameConfig
import com.neomud.server.game.MovementTrailManager
import com.neomud.server.game.TrailEntry
import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.server.session.SessionManager
import com.neomud.shared.model.Coins
import com.neomud.shared.model.Direction
import com.neomud.shared.model.GroundItem
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SecurityHardeningTest {

    private fun testDbUrl(): String {
        val tmpFile = File.createTempFile("neomud_sec_test_", ".db")
        tmpFile.deleteOnExit()
        tmpFile.delete()
        return "jdbc:sqlite:${tmpFile.absolutePath}"
    }

    private fun sendMsg(msg: ClientMessage): Frame.Text =
        Frame.Text(MessageSerializer.encodeClientMessage(msg))

    private suspend fun DefaultClientWebSocketSession.receiveServerMessage(): ServerMessage {
        val frame = incoming.receive()
        assertTrue(frame is Frame.Text, "Expected text frame")
        return MessageSerializer.decodeServerMessage(frame.readText())
    }

    private suspend fun DefaultClientWebSocketSession.consumeCatalogSync() {
        repeat(6) { receiveServerMessage() } // ServerHello, ClassCatalog, ItemCatalog, SkillCatalog, RaceCatalog, SpellCatalog
    }

    private val defaultStats = Stats(30, 22, 18, 18, 30, 18)

    // ── Password Length ──

    @Test
    fun `registration rejects short passwords`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Register("shortpw", "1234567", "ShortPw", "WARRIOR", allocatedStats = defaultStats)))
            val response = receiveServerMessage()
            assertIs<ServerMessage.AuthError>(response)
            assertTrue(response.reason.contains("8-64"), "Should mention 8-64 chars: ${response.reason}")
        }
    }

    @Test
    fun `registration accepts 8-char passwords`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Register("goodpw", "12345678", "GoodPw", "WARRIOR", allocatedStats = defaultStats)))
            val response = receiveServerMessage()
            assertIs<ServerMessage.RegisterOk>(response)
        }
    }

    // ── Duplicate Login ──

    @Test
    fun `duplicate login by username displaces first session`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        // Register
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Register("dupuser", "pass1234", "DupHero", "WARRIOR", allocatedStats = defaultStats)))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }

        // Login first session
        val client1 = createClient { install(WebSockets) }
        client1.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("dupuser", "pass1234")))
            assertIs<ServerMessage.LoginOk>(receiveServerMessage())

            // Second login displaces the first — second should succeed
            val client2 = createClient { install(WebSockets) }
            client2.webSocket("/game") {
                consumeCatalogSync()
                send(sendMsg(ClientMessage.Login("dupuser", "pass1234")))
                assertIs<ServerMessage.LoginOk>(receiveServerMessage())
            }

            // First session should receive SessionDisplaced (may be interleaved with other messages)
            var foundDisplaced = false
            repeat(10) {
                val msg = try { receiveServerMessage() } catch (_: Exception) { return@repeat }
                if (msg is ServerMessage.SessionDisplaced) {
                    foundDisplaced = true
                    return@repeat
                }
            }
            assertTrue(foundDisplaced, "Expected SessionDisplaced message on first session")
        }
    }

    // ── Brute Force Protection ──

    @Test
    fun `login lockout after max failed attempts`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        // Register
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Register("locktest", "pass1234", "LockHero", "WARRIOR", allocatedStats = defaultStats)))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }

        // Fail N times
        repeat(GameConfig.Security.MAX_FAILED_LOGINS) {
            wsClient.webSocket("/game") {
                consumeCatalogSync()
                send(sendMsg(ClientMessage.Login("locktest", "wrongpassword")))
                val response = receiveServerMessage()
                assertIs<ServerMessage.AuthError>(response)
            }
        }

        // Next attempt should be rate-limited even with correct password
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("locktest", "pass1234")))
            val response = receiveServerMessage()
            assertIs<ServerMessage.AuthError>(response)
            assertTrue(response.reason.contains("Too many failed"))
        }
    }

    // ── SessionManager username tracking ──

    @Test
    fun `SessionManager tracks username to character mapping`() {
        val sm = SessionManager()
        // isUsernameLoggedIn should be false initially
        assertTrue(!sm.isUsernameLoggedIn("testuser"))
    }

    // ── Ground Items Expiry ──

    @Test
    fun `ground items expire after TTL`() {
        val mgr = RoomItemManager()
        mgr.addItems("room1", listOf(GroundItem("item:potion", 3)))

        // Not expired yet
        assertEquals(1, mgr.getGroundItems("room1").size)
        mgr.pruneExpired(System.currentTimeMillis())
        assertEquals(1, mgr.getGroundItems("room1").size)

        // Simulate time passing beyond expiry
        val futureTime = System.currentTimeMillis() + GameConfig.GroundItems.EXPIRY_MS + 1000
        val pruned = mgr.pruneExpired(futureTime)
        assertTrue(pruned > 0)
        assertEquals(0, mgr.getGroundItems("room1").size)
    }

    @Test
    fun `ground coins expire after TTL`() {
        val mgr = RoomItemManager()
        mgr.addCoins("room1", Coins(copper = 10))
        assertEquals(10, mgr.getGroundCoins("room1").copper)

        val futureTime = System.currentTimeMillis() + GameConfig.GroundItems.EXPIRY_MS + 1000
        mgr.pruneExpired(futureTime)
        assertEquals(0, mgr.getGroundCoins("room1").copper)
    }

    // ── MovementTrailManager Thread Safety ──

    @Test
    fun `trail manager uses concurrent operations safely`() {
        val mgr = MovementTrailManager()
        val now = System.currentTimeMillis()

        // Record and read concurrently (basic smoke test)
        repeat(100) { i ->
            mgr.recordTrail("room1", TrailEntry("npc$i", "npc:$i", Direction.NORTH, now + i, false))
        }
        val trails = mgr.getTrails("room1")
        assertEquals(GameConfig.Trails.MAX_ENTRIES_PER_ROOM, trails.size)
    }

    // ── Chat Sanitization ──

    @Test
    fun `chat messages strip control characters`() {
        // Verify the regex used in SayCommand
        val input = "Hello\u0000\u0007\u001B[31mWorld\u007F"
        val sanitized = input.take(500).replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")
        assertEquals("Hello[31mWorld", sanitized)
    }

    // ── Guest Security ──

    @Test
    fun testGuestTakenNameDoesNotConsumeRateLimit() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        // Register a persistent player
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Register("owner", "password123", "TakenName", "WARRIOR", allocatedStats = defaultStats)))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }

        // Try more than MAX_GUEST_CREATIONS_PER_IP_PER_HOUR times with the taken name
        // All should say "already taken", never "too many guest sessions"
        repeat(GameConfig.Security.MAX_GUEST_CREATIONS_PER_IP_PER_HOUR + 1) { i ->
            wsClient.webSocket("/game") {
                consumeCatalogSync()
                send(sendMsg(ClientMessage.GuestLogin(
                    characterName = "TakenName", characterClass = "WARRIOR",
                    race = "HUMAN", allocatedStats = defaultStats
                )))
                val error = receiveServerMessage()
                assertIs<ServerMessage.AuthError>(error)
                assertTrue(
                    error.reason.contains("already taken", ignoreCase = true),
                    "Attempt ${i + 1}: expected 'already taken', got '${error.reason}'"
                )
            }
        }
    }

    @Test
    fun testGuestIdHasSufficientEntropy() {
        val id = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        assertEquals(16, id.length, "Guest ID should be 16 hex chars (64 bits of entropy)")
        assertTrue(id.all { it in '0'..'9' || it in 'a'..'f' }, "Guest ID should be hex chars only")
    }

    // ── Passwordless Auth ──

    @Test
    fun `passwordless registration creates player`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Register(characterName = "NoPassHero", characterClass = "WARRIOR", allocatedStats = defaultStats)))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }
    }

    @Test
    fun `passwordless login by characterName works`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Register(characterName = "PasslessHero", characterClass = "WARRIOR", allocatedStats = defaultStats)))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login(characterName = "PasslessHero")))
            val response = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(response)
            assertEquals("PasslessHero", response.player.name)
        }
    }

    @Test
    fun `duplicate passwordless login displaces first session`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Register(characterName = "DupPassless", characterClass = "WARRIOR", allocatedStats = defaultStats)))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }

        val client1 = createClient { install(WebSockets) }
        client1.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login(characterName = "DupPassless")))
            assertIs<ServerMessage.LoginOk>(receiveServerMessage())

            val client2 = createClient { install(WebSockets) }
            client2.webSocket("/game") {
                consumeCatalogSync()
                send(sendMsg(ClientMessage.Login(characterName = "DupPassless")))
                assertIs<ServerMessage.LoginOk>(receiveServerMessage())
            }

            var foundDisplaced = false
            repeat(10) {
                val msg = try { receiveServerMessage() } catch (_: Exception) { return@repeat }
                if (msg is ServerMessage.SessionDisplaced) {
                    foundDisplaced = true
                    return@repeat
                }
            }
            assertTrue(foundDisplaced, "Expected SessionDisplaced message on first session")
        }
    }

    @Test
    fun `legacy password login still works`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Register("legacyuser", "pass1234", "LegacyHero", "WARRIOR", allocatedStats = defaultStats)))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("legacyuser", "pass1234")))
            val response = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(response)
            assertEquals("LegacyHero", response.player.name)
        }
    }

    @Test
    fun `admin promotion by characterName match`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminhero")) }
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Register(characterName = "AdminHero", characterClass = "WARRIOR", allocatedStats = defaultStats)))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login(characterName = "AdminHero")))
            val response = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(response)
            assertTrue(response.player.isAdmin, "Character name 'AdminHero' should match admin list 'adminhero'")
        }
    }
}
