package com.neomud.server.telnet

import com.neomud.server.auth.PlatformTokenVerifier
import com.neomud.server.module
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration tests for the telnet pre-auth routing logic.
 *
 * Starts a real TelnetServer on a free port inside testApplication, then connects
 * via java.net.Socket to assert on the text the server sends.
 *
 * Three paths are covered:
 *  - valid HMAC header -> character creation prompt (not login prompt)
 *  - invalid HMAC header -> falls back to legacy login prompt
 *  - no preAuthSecret configured -> shows legacy login prompt immediately
 */
class TelnetPlatformAuthTest {

    private val preAuthSecret = "test-secret-32-bytes-of-entropy!"
    private val devSecret = "test-platform-secret-minimum-thirty-two-chars"
    private val warriorStats = Stats(strength = 30, agility = 22, intellect = 18, willpower = 18, health = 30, charm = 18)

    private fun testDbUrl(): String {
        val tmp = File.createTempFile("neomud_telnet_auth_test_", ".db")
        tmp.deleteOnExit(); tmp.delete()
        return "jdbc:sqlite:${tmp.absolutePath}"
    }

    private fun findFreePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    private fun hmac(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(preAuthSecret.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun makeToken(userId: String): String {
        val claims = JWTClaimsSet.Builder()
            .issuer("neomud-platform")
            .claim("userId", userId)
            .claim("role", "USER")
            .expirationTime(Date(System.currentTimeMillis() + 60_000))
            .build()
        val jwt = SignedJWT(JWSHeader(JWSAlgorithm.HS256), claims)
        jwt.sign(MACSigner(devSecret.toByteArray()))
        return jwt.serialize()
    }

    private fun testVerifier() = PlatformTokenVerifier(jwksUrl = null, devSecret = devSecret)

    private fun preAuthBytes(type: String, userId: String): ByteArray {
        val h = hmac("$type:$userId")
        return byteArrayOf(0x00) + "NEOMUD:$type:$userId:$h\n".toByteArray(Charsets.US_ASCII)
    }

    private fun preAuthBytesWithChar(userId: String, characterName: String): ByteArray {
        val h = hmac("user:$userId:$characterName")
        return byteArrayOf(0x00) + "NEOMUD:user:$userId:$characterName:$h\n".toByteArray(Charsets.US_ASCII)
    }

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

    /** Retry TCP connect until port accepts, up to [timeoutMs]. Must run on IO thread. */
    private fun waitForPortBlocking(port: Int, timeoutMs: Long = 4000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try { java.net.Socket("127.0.0.1", port).close(); return } catch (_: Exception) {}
            Thread.sleep(50)
        }
        error("Telnet server did not start on port $port within ${timeoutMs}ms")
    }

    /**
     * Read decoded telnet text until [marker] appears or [timeoutMs] elapses.
     * Strips IAC 3-byte negotiation sequences and subnegotiations.
     */
    private fun readUntilBlocking(socket: java.net.Socket, marker: String, timeoutMs: Long = 5000): String {
        val input = socket.getInputStream()
        val buf = StringBuilder()
        socket.soTimeout = 200
        val deadline = System.currentTimeMillis() + timeoutMs

        loop@ while (System.currentTimeMillis() < deadline) {
            val b = try {
                input.read()
            } catch (_: java.net.SocketTimeoutException) {
                if (buf.contains(marker)) break@loop
                continue@loop
            }
            if (b == -1) break

            if (b == 0xFF) { // IAC
                val cmd = try { input.read() } catch (_: Exception) { break }
                if (cmd == 0xFA) { // SB: skip until IAC SE
                    while (true) {
                        val x = try { input.read() } catch (_: Exception) { break }
                        if (x == 0xFF) { try { input.read() } catch (_: Exception) {}; break }
                    }
                } else {
                    try { input.read() } catch (_: Exception) {} // skip option byte
                }
                continue
            }

            when {
                b >= 0x20 -> buf.append(b.toChar())
                b == '\r'.code || b == '\n'.code -> buf.append(' ')
            }
            if (buf.contains(marker)) break
        }
        return buf.toString()
    }

    // ---- Tests ---------------------------------------------------------------

    @Test
    fun validPreAuthHeader_bypassesLoginAndShowsCharacterCreation() = testApplication {
        val port = findFreePort()
        application {
            module(
                jdbcUrl = testDbUrl(),
                telnetEnabled = true,
                telnetPortOverride = port,
                preAuthSecretOverride = preAuthSecret,
            )
        }
        startApplication()

        withContext(Dispatchers.IO) {
            waitForPortBlocking(port)
            java.net.Socket("127.0.0.1", port).use { socket ->
                socket.getOutputStream().write(preAuthBytes("user", "user-abc"))
                socket.getOutputStream().flush()
                val text = readUntilBlocking(socket, "Character name:")
                assertTrue(
                    text.contains("Character name:"),
                    "Expected character creation prompt, got: $text",
                )
                assertFalse(
                    text.contains("Username") || text.contains("email"),
                    "Login prompt must not appear after valid pre-auth, got: $text",
                )
            }
        }
    }

    @Test
    fun invalidPreAuthHmac_fallsBackToLegacyLoginPrompt() = testApplication {
        val port = findFreePort()
        application {
            module(
                jdbcUrl = testDbUrl(),
                telnetEnabled = true,
                telnetPortOverride = port,
                preAuthSecretOverride = preAuthSecret,
            )
        }
        startApplication()

        withContext(Dispatchers.IO) {
            waitForPortBlocking(port)
            java.net.Socket("127.0.0.1", port).use { socket ->
                // Syntactically valid structure but wrong HMAC — server must fall back to login
                val badHeader = byteArrayOf(0x00) +
                    "NEOMUD:user:user-abc:deadbeef12345678\n".toByteArray(Charsets.US_ASCII)
                socket.getOutputStream().write(badHeader)
                socket.getOutputStream().flush()
                val text = readUntilBlocking(socket, "Username:")
                assertTrue(
                    text.contains("Username:"),
                    "Expected legacy login prompt after bad HMAC, got: $text",
                )
            }
        }
    }

    @Test
    fun noPreAuthSecretConfigured_showsLegacyLoginPromptImmediately() = testApplication {
        val port = findFreePort()
        application {
            module(
                jdbcUrl = testDbUrl(),
                telnetEnabled = true,
                telnetPortOverride = port,
                // No preAuthSecretOverride: detectPreAuth() returns null immediately
            )
        }
        startApplication()

        withContext(Dispatchers.IO) {
            waitForPortBlocking(port)
            java.net.Socket("127.0.0.1", port).use { socket ->
                // Send nothing; server skips the 100ms detection window and shows login immediately
                val text = readUntilBlocking(socket, "Username:")
                assertTrue(
                    text.contains("Username:"),
                    "Expected immediate legacy prompt with no secret configured, got: $text",
                )
            }
        }
    }

    @Test
    fun preAuthWithCharacterName_logsInDirectlyWithoutAnyPrompts() = testApplication {
        val jdbcUrl = testDbUrl()
        val port = findFreePort()
        application {
            module(
                jdbcUrl = jdbcUrl,
                telnetEnabled = true,
                telnetPortOverride = port,
                preAuthSecretOverride = preAuthSecret,
                platformVerifierOverride = testVerifier(),
            )
        }
        startApplication()

        val userId = "user-phase3-direct"
        val characterName = "PhaseThreeHero"

        // Register the character via WebSocket so it exists in the DB.
        val wsClient = createClient { install(WebSockets) }
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
                    characterName = characterName,
                    characterClass = "WARRIOR",
                    race = "HUMAN",
                    gender = "male",
                    allocatedStats = warriorStats,
                )
            )))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
            assertIs<ServerMessage.LoginOk>(receiveServerMessage())
        }

        // Connect via telnet with a pre-auth header that includes the character name.
        // The game container must go straight to login — no "Character name:" prompt.
        withContext(Dispatchers.IO) {
            waitForPortBlocking(port)
            java.net.Socket("127.0.0.1", port).use { socket ->
                socket.getOutputStream().write(preAuthBytesWithChar(userId, characterName))
                socket.getOutputStream().flush()
                val text = readUntilBlocking(socket, "Logging in as $characterName")
                assertTrue(
                    text.contains("Logging in as $characterName"),
                    "Expected direct login message, got: $text",
                )
                assertFalse(
                    text.contains("Character name:"),
                    "Character creation wizard must not appear when character is pre-selected, got: $text",
                )
            }
        }
    }
}
