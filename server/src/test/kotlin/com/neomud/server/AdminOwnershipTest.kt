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
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Covers the world-ownership admin promotion path (WORLD_OWNER_PLATFORM_USER_ID).
 *
 * The existing `NEOMUD_ADMINS` username-allowlist path is covered by AdminCommandTest;
 * this file proves the new path works AND that it composes additively with the old one
 * without breaking the `null == null` corner case (worldOwner unset + session.platformUserId
 * unset must NOT promote — equality of two nulls would silently grant admin to everyone).
 */
class AdminOwnershipTest {

    private val devSecret = "test-platform-secret-minimum-thirty-two-chars"

    private fun testDbUrl(): String {
        val tmpFile = File.createTempFile("neomud_ownership_test_", ".db")
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

    private val warriorStats = Stats(30, 22, 18, 18, 30, 18)

    /** Register via password path (returns RegisterOk). Doesn't log in. */
    private suspend fun DefaultClientWebSocketSession.passwordRegister(username: String, characterName: String) {
        send(Frame.Text(MessageSerializer.encodeClientMessage(
            ClientMessage.Register(username, "pass1234", characterName, "WARRIOR", allocatedStats = warriorStats)
        )))
        assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
    }

    /** Login via password path. Returns LoginOk; the post-login messages are drained. */
    private suspend fun DefaultClientWebSocketSession.passwordLoginAndCaptureLoginOk(username: String): ServerMessage.LoginOk {
        send(Frame.Text(MessageSerializer.encodeClientMessage(ClientMessage.Login(username, "pass1234"))))
        val loginOk = receiveServerMessage()
        assertIs<ServerMessage.LoginOk>(loginOk)
        // Drain post-login messages until RoomItemsUpdate (the last in the sequence)
        withTimeout(5000) {
            while (true) {
                val m = receiveServerMessage()
                if (m is ServerMessage.RoomItemsUpdate) break
            }
        }
        return loginOk
    }

    /** Two-session helper: register in session 1, log in in session 2. Returns LoginOk. */
    private suspend fun HttpClient.passwordRegisterThenLogin(username: String, characterName: String): ServerMessage.LoginOk {
        webSocket("/game") {
            consumeCatalogSync()
            passwordRegister(username, characterName)
        }
        var loginOk: ServerMessage.LoginOk? = null
        webSocket("/game") {
            consumeCatalogSync()
            loginOk = passwordLoginAndCaptureLoginOk(username)
        }
        return loginOk!!
    }

    /** PlatformRegister auto-logs in: ClientHello → PlatformAuthOk → PlatformRegister → RegisterOk → LoginOk. */
    private suspend fun DefaultClientWebSocketSession.platformRegisterAndCaptureLoginOk(
        platformUserId: String,
        characterName: String
    ): ServerMessage.LoginOk {
        val token = makeToken(platformUserId)
        send(Frame.Text(MessageSerializer.encodeClientMessage(
            ClientMessage.ClientHello(
                clientVersion = NeoMudVersion.ENGINE_VERSION,
                protocolVersion = NeoMudVersion.PROTOCOL_VERSION,
                platformToken = token
            )
        )))
        assertIs<ServerMessage.PlatformAuthOk>(receiveServerMessage())

        send(Frame.Text(MessageSerializer.encodeClientMessage(
            ClientMessage.PlatformRegister(
                characterName = characterName,
                characterClass = "WARRIOR",
                race = "HUMAN",
                gender = "male",
                allocatedStats = warriorStats
            )
        )))
        assertIs<ServerMessage.RegisterOk>(receiveServerMessage())

        val loginOk = receiveServerMessage()
        assertIs<ServerMessage.LoginOk>(loginOk)
        return loginOk
    }

    // T1 — owner promoted to admin via platform path
    @Test
    fun `world owner platform user is auto-promoted to admin on platform register`() = testApplication {
        application {
            module(
                jdbcUrl = testDbUrl(),
                platformVerifierOverride = testVerifier(),
                worldOwnerPlatformUserIdOverride = "user-owner"
            )
        }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            val loginOk = platformRegisterAndCaptureLoginOk("user-owner", "OwnerHero")
            assertTrue(loginOk.player.isAdmin, "World owner should be auto-admin")
        }
    }

    // T2 — non-owner platform user is NOT promoted
    @Test
    fun `non-owner platform user is NOT auto-promoted`() = testApplication {
        application {
            module(
                jdbcUrl = testDbUrl(),
                platformVerifierOverride = testVerifier(),
                worldOwnerPlatformUserIdOverride = "user-owner"
            )
        }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            val loginOk = platformRegisterAndCaptureLoginOk("user-stranger", "StrangerHero")
            assertFalse(loginOk.player.isAdmin, "Non-owner platform user must not be auto-admin")
        }
    }

    // T3 — null worldOwnerOverride + null session.platformUserId must NOT match-promote
    // (the null == null trap)
    @Test
    fun `null worldOwnerOverride and null platformUserId do not match-promote`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        val loginOk = ws.passwordRegisterThenLogin("regularuser", "RegularHero")
        assertFalse(loginOk.player.isAdmin, "null==null must NOT promote")
    }

    // T4 — both NEOMUD_ADMINS (username allowlist) and worldOwner (platform path)
    // compose: each path independently promotes
    @Test
    fun `both username allowlist and worldOwner paths compose`() = testApplication {
        application {
            module(
                jdbcUrl = testDbUrl(),
                adminUsernamesOverride = setOf("classicadmin"),
                platformVerifierOverride = testVerifier(),
                worldOwnerPlatformUserIdOverride = "user-owner"
            )
        }
        val ws = createClient { install(WebSockets) }

        // Path A: classic NEOMUD_ADMINS username match
        val classicLoginOk = ws.passwordRegisterThenLogin("classicadmin", "ClassicHero")
        assertTrue(classicLoginOk.player.isAdmin, "classicadmin must be admin via username allowlist")

        // Path B: platform world-owner match
        ws.webSocket("/game") {
            consumeCatalogSync()
            val ownerLoginOk = platformRegisterAndCaptureLoginOk("user-owner", "OwnerHero")
            assertTrue(ownerLoginOk.player.isAdmin, "world owner must be admin via platform path")
        }
    }

    // T5 — worldOwner override is inert when platform verifier is disabled
    // (no JWT can verify → no session.platformUserId → no comparison → no promotion)
    @Test
    fun `worldOwner override is inert without platform verifier configured`() = testApplication {
        application {
            module(
                jdbcUrl = testDbUrl(),
                // NEOMUD_ADMINS allowlist intentionally NOT set — proves worldOwner alone
                // can't grant admin when the platform path is closed.
                worldOwnerPlatformUserIdOverride = "user_owner"
                // NOTE: no platformVerifierOverride — verifier is inert
            )
        }
        val ws = createClient { install(WebSockets) }

        // Even if a username happens to match the worldOwner ID string, they must
        // NOT get admin — the platform path is closed (verifier inert, no platformUserId).
        val loginOk = ws.passwordRegisterThenLogin("user_owner", "OwnerNamedHero")
        assertFalse(
            loginOk.player.isAdmin,
            "Without platform verifier, worldOwner override cannot grant admin"
        )
    }

    // T6 — module() with worldOwnerOverride unset boots cleanly: no NPE, no throw,
    // and a regular user is not accidentally promoted.
    @Test
    fun `module boots silently when worldOwner is unset`() = testApplication {
        // Just calling module() and registering must not throw. The test framework will
        // surface any startup exception as a test failure.
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        val loginOk = ws.passwordRegisterThenLogin("anyuser", "AnyHero")
        assertFalse(loginOk.player.isAdmin)
    }
}
