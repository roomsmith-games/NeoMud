package com.neomud.server

import com.neomud.server.auth.PlatformTokenVerifier
import com.neomud.shared.NeoMudVersion
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import java.io.File
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformAuthTest {

    private val devSecret = "test-platform-secret-minimum-thirty-two-chars"

    private fun testDbUrl(): String {
        val tmpFile = File.createTempFile("neomud_platform_auth_test_", ".db")
        tmpFile.deleteOnExit()
        tmpFile.delete()
        return "jdbc:sqlite:${tmpFile.absolutePath}"
    }

    private fun makeToken(userId: String, role: String = "USER", expiresInMs: Long = 60_000): String {
        val claims = JWTClaimsSet.Builder()
            .issuer("neomud-platform")
            .claim("userId", userId)
            .claim("role", role)
            .expirationTime(Date(System.currentTimeMillis() + expiresInMs))
            .build()
        val header = JWSHeader(JWSAlgorithm.HS256)
        val jwt = SignedJWT(header, claims)
        jwt.sign(MACSigner(devSecret.toByteArray()))
        return jwt.serialize()
    }

    private fun testVerifier() = PlatformTokenVerifier(jwksUrl = null, devSecret = devSecret)

    private suspend fun DefaultClientWebSocketSession.receiveServerMessage(): ServerMessage {
        val frame = incoming.receive()
        assertTrue(frame is Frame.Text, "Expected text frame")
        return MessageSerializer.decodeServerMessage(frame.readText())
    }

    private suspend fun DefaultClientWebSocketSession.consumeCatalogSync() {
        assertIs<ServerMessage.ServerHello>(receiveServerMessage())
        assertIs<ServerMessage.ClassCatalogSync>(receiveServerMessage())
        assertIs<ServerMessage.ItemCatalogSync>(receiveServerMessage())
        assertIs<ServerMessage.SkillCatalogSync>(receiveServerMessage())
        assertIs<ServerMessage.RaceCatalogSync>(receiveServerMessage())
        assertIs<ServerMessage.SpellCatalogSync>(receiveServerMessage())
    }

    /** Drain the messages the server sends after a successful login. */
    private suspend fun DefaultClientWebSocketSession.consumePostLogin() {
        // Tutorial, RoomInfo, MapData, InventoryUpdate, RoomItemsUpdate
        repeat(5) { receiveServerMessage() }
    }

    private val warriorStats = Stats(
        strength = 30, agility = 22, intellect = 18,
        willpower = 18, health = 30, charm = 18
    )

    @Test
    fun testNewPlatformUserSeesCharacterCreation() = testApplication {
        application { module(jdbcUrl = testDbUrl(), platformVerifierOverride = testVerifier()) }
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()

            val token = makeToken("user-abc")
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.ClientHello(clientVersion = NeoMudVersion.ENGINE_VERSION, protocolVersion = NeoMudVersion.PROTOCOL_VERSION, platformToken = token)
            )))

            val authOk = receiveServerMessage()
            assertIs<ServerMessage.PlatformAuthOk>(authOk)
            assertEquals("user-abc", authOk.platformUserId)
            assertTrue(authOk.needsCharacterCreation)
            assertNull(authOk.characterName)
        }
    }

    @Test
    fun testPlatformRegisterAndLogin() = testApplication {
        application { module(jdbcUrl = testDbUrl(), platformVerifierOverride = testVerifier()) }
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()

            val token = makeToken("user-xyz")
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.ClientHello(clientVersion = NeoMudVersion.ENGINE_VERSION, protocolVersion = NeoMudVersion.PROTOCOL_VERSION, platformToken = token)
            )))

            val authOk = receiveServerMessage()
            assertIs<ServerMessage.PlatformAuthOk>(authOk)
            assertTrue(authOk.needsCharacterCreation)

            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.PlatformRegister(
                    characterName = "Alerion",
                    characterClass = "WARRIOR",
                    race = "HUMAN",
                    gender = "male",
                    allocatedStats = warriorStats
                )
            )))

            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())

            val loginOk = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(loginOk)
            assertEquals("Alerion", loginOk.player.name)
            assertFalse(loginOk.player.isGuest)
        }
    }

    @Test
    fun testReturningPlatformUserSeesCharacterName() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, platformVerifierOverride = testVerifier()) }
        val wsClient = createClient { install(WebSockets) }

        // First session — register character
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            val token = makeToken("user-returning")
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.ClientHello(clientVersion = NeoMudVersion.ENGINE_VERSION, protocolVersion = NeoMudVersion.PROTOCOL_VERSION, platformToken = token)
            )))
            assertIs<ServerMessage.PlatformAuthOk>(receiveServerMessage())

            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.PlatformRegister(
                    characterName = "Seraphel",
                    characterClass = "WARRIOR",
                    race = "HUMAN",
                    gender = "female",
                    allocatedStats = warriorStats
                )
            )))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
            assertIs<ServerMessage.LoginOk>(receiveServerMessage())
            consumePostLogin()
        }

        // Second session — same platform user should see their character
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            val token = makeToken("user-returning")
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.ClientHello(clientVersion = NeoMudVersion.ENGINE_VERSION, protocolVersion = NeoMudVersion.PROTOCOL_VERSION, platformToken = token)
            )))

            val authOk = receiveServerMessage()
            assertIs<ServerMessage.PlatformAuthOk>(authOk)
            assertFalse(authOk.needsCharacterCreation)
            assertEquals("Seraphel", authOk.characterName)
            assertEquals("user-returning", authOk.platformUserId)
        }
    }

    @Test
    fun testReturningPlatformUserCanAutoLogin() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, platformVerifierOverride = testVerifier()) }
        val wsClient = createClient { install(WebSockets) }

        // Register first
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.ClientHello(clientVersion = NeoMudVersion.ENGINE_VERSION, protocolVersion = NeoMudVersion.PROTOCOL_VERSION, platformToken = makeToken("user-autologin"))
            )))
            assertIs<ServerMessage.PlatformAuthOk>(receiveServerMessage())
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.PlatformRegister("Brennan", "WARRIOR", "HUMAN", allocatedStats = warriorStats)
            )))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
            assertIs<ServerMessage.LoginOk>(receiveServerMessage())
            consumePostLogin()
        }

        // Reconnect and auto-login
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.ClientHello(clientVersion = NeoMudVersion.ENGINE_VERSION, protocolVersion = NeoMudVersion.PROTOCOL_VERSION, platformToken = makeToken("user-autologin"))
            )))
            val authOk = receiveServerMessage()
            assertIs<ServerMessage.PlatformAuthOk>(authOk)
            assertFalse(authOk.needsCharacterCreation)

            send(Frame.Text(MessageSerializer.encodeClientMessage(ClientMessage.PlatformLogin)))

            val loginOk = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(loginOk)
            assertEquals("Brennan", loginOk.player.name)
        }
    }

    @Test
    fun testInvalidTokenFallsBackToPasswordAuth() = testApplication {
        application { module(jdbcUrl = testDbUrl(), platformVerifierOverride = testVerifier()) }
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()

            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.ClientHello(clientVersion = NeoMudVersion.ENGINE_VERSION, protocolVersion = NeoMudVersion.PROTOCOL_VERSION, platformToken = "not.a.valid.jwt")
            )))

            // No PlatformAuthOk — server silently falls back to password auth
            // Client can still register/login normally — next expected action would be Login or Register
            // Send a login attempt and expect an auth error (no such user) rather than a crash
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("nobody", "wrong")
            )))
            val err = receiveServerMessage()
            assertIs<ServerMessage.AuthError>(err)
        }
    }

    @Test
    fun testExpiredTokenIsRejected() = testApplication {
        application { module(jdbcUrl = testDbUrl(), platformVerifierOverride = testVerifier()) }
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()

            val expiredToken = makeToken("user-expired", expiresInMs = -1000)
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.ClientHello(clientVersion = NeoMudVersion.ENGINE_VERSION, protocolVersion = NeoMudVersion.PROTOCOL_VERSION, platformToken = expiredToken)
            )))

            // No PlatformAuthOk — expired token falls back to password auth
            send(Frame.Text(MessageSerializer.encodeClientMessage(ClientMessage.Login("nobody", "wrong"))))
            val err = receiveServerMessage()
            assertIs<ServerMessage.AuthError>(err)
        }
    }
}
