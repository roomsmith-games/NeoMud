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
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for GET /internal/character-summary — the endpoint the telnet router
 * calls to show per-world character info in the world selection menu (Phase 2).
 */
class InternalCharacterSummaryTest {

    private val devSecret = "test-platform-secret-minimum-thirty-two-chars"
    private val warriorStats = Stats(strength = 30, agility = 22, intellect = 18, willpower = 18, health = 30, charm = 18)

    private fun testDbUrl(): String {
        val tmp = File.createTempFile("neomud_char_summary_test_", ".db")
        tmp.deleteOnExit(); tmp.delete()
        return "jdbc:sqlite:${tmp.absolutePath}"
    }

    private fun makeToken(userId: String, role: String = "USER"): String {
        val claims = JWTClaimsSet.Builder()
            .issuer("neomud-platform")
            .claim("userId", userId)
            .claim("role", role)
            .expirationTime(Date(System.currentTimeMillis() + 60_000))
            .build()
        val header = JWSHeader(JWSAlgorithm.HS256)
        val jwt = SignedJWT(header, claims)
        jwt.sign(MACSigner(devSecret.toByteArray()))
        return jwt.serialize()
    }

    private fun testVerifier() = PlatformTokenVerifier(jwksUrl = null, devSecret = devSecret)

    private suspend fun DefaultClientWebSocketSession.receiveServerMessage(): ServerMessage {
        val frame = incoming.receive()
        assertTrue(frame is Frame.Text)
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

    // ---- Tests ---------------------------------------------------------------

    @Test
    fun missingPlatformUserId_returns400() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val response = client.get("/internal/character-summary")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun unknownPlatformUserId_returnsNullCharacterName() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val response = client.get("/internal/character-summary?platformUserId=user-does-not-exist")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val body = Json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertTrue(body["characterName"]?.toString() == "null" || body["characterName"]?.jsonPrimitive?.isString == false)
    }

    @Test
    fun registeredCharacter_returnsNameAndLevel() = testApplication {
        val jdbcUrl = testDbUrl()
        application { module(jdbcUrl = jdbcUrl, platformVerifierOverride = testVerifier()) }
        val wsClient = createClient { install(WebSockets) }

        // Register a character via PlatformRegister so it's linked to the platformUserId
        val userId = "user-summary-test-abc"
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.ClientHello(
                    clientVersion = NeoMudVersion.ENGINE_VERSION,
                    protocolVersion = NeoMudVersion.PROTOCOL_VERSION,
                    platformToken = makeToken(userId),
                )
            )))
            assertIs<ServerMessage.PlatformAuthOk>(receiveServerMessage())
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.PlatformRegister(
                    characterName = "SummaryTestChar",
                    characterClass = "WARRIOR",
                    race = "HUMAN",
                    gender = "male",
                    allocatedStats = warriorStats,
                )
            )))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
            assertIs<ServerMessage.LoginOk>(receiveServerMessage())
        }

        // Now the character exists — query the summary endpoint
        val response = client.get("/internal/character-summary?platformUserId=$userId")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertEquals("SummaryTestChar", body["characterName"]?.jsonPrimitive?.content)
        assertEquals(1, body["level"]?.jsonPrimitive?.content?.toInt())
    }

    // ─── /internal/characters tests (Phase 3) ─────────────────────────────────

    @Test
    fun characters_missingPlatformUserId_returns400() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val response = client.get("/internal/characters")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun characters_unknownUser_returnsEmptyArray() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val response = client.get("/internal/characters?platformUserId=nobody")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()) as JsonArray
        assertEquals(0, body.size)
    }

    @Test
    fun characters_registeredUser_returnsCharacterList() = testApplication {
        val jdbcUrl = testDbUrl()
        application { module(jdbcUrl = jdbcUrl, platformVerifierOverride = testVerifier()) }
        val wsClient = createClient { install(WebSockets) }
        val userId = "user-charlist-test"

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.ClientHello(
                    clientVersion = NeoMudVersion.ENGINE_VERSION,
                    protocolVersion = NeoMudVersion.PROTOCOL_VERSION,
                    platformToken = makeToken(userId),
                )
            )))
            assertIs<ServerMessage.PlatformAuthOk>(receiveServerMessage())
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.PlatformRegister(
                    characterName = "CharListHero",
                    characterClass = "WARRIOR",
                    race = "HUMAN",
                    gender = "male",
                    allocatedStats = warriorStats,
                )
            )))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
            assertIs<ServerMessage.LoginOk>(receiveServerMessage())
        }

        val response = client.get("/internal/characters?platformUserId=$userId")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val body = Json.parseToJsonElement(response.bodyAsText()) as JsonArray
        assertEquals(1, body.size)
        val char = body[0] as JsonObject
        assertEquals("CharListHero", char["name"]?.jsonPrimitive?.content)
        assertEquals(1, char["level"]?.jsonPrimitive?.content?.toInt())
        assertEquals("WARRIOR", char["characterClass"]?.jsonPrimitive?.content)
    }
}
