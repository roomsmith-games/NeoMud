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
import java.net.Socket
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * End-to-end telnet integration tests over a real TCP socket (the Phase 4 deliverable).
 *
 * Complements [TelnetPlatformAuthTest] (which covers the pre-auth/login handshake) by driving a
 * fully logged-in in-game session: live GMCP negotiation + snapshot, room rendering via `look`,
 * and unknown-command handling. Verifies the Phase 6 GMCP work end-to-end.
 */
class TelnetIntegrationTest {

    private val preAuthSecret = "test-secret-32-bytes-of-entropy!"
    private val devSecret = "test-platform-secret-minimum-thirty-two-chars"
    private val warriorStats = Stats(strength = 30, agility = 22, intellect = 18, willpower = 18, health = 30, charm = 18)

    private fun testDbUrl(): String {
        val tmp = File.createTempFile("neomud_telnet_integ_", ".db")
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

    private fun preAuthBytesWithChar(userId: String, characterName: String): ByteArray {
        val h = hmac("user:$userId:$characterName")
        return byteArrayOf(0x00) + "NEOMUD:user:$userId:$characterName:$h\n".toByteArray(Charsets.US_ASCII)
    }

    /** IAC DO GMCP — the 3 bytes a Mudlet-class client sends to activate GMCP. */
    private val doGmcp = byteArrayOf(Telnet.IAC, Telnet.DO, Telnet.GMCP)

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

    private fun waitForPortBlocking(port: Int, timeoutMs: Long = 4000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try { Socket("127.0.0.1", port).close(); return } catch (_: Exception) {}
            Thread.sleep(50)
        }
        error("Telnet server did not start on port $port within ${timeoutMs}ms")
    }

    /** Accumulated output from a telnet socket: visible text and decoded GMCP package lines. */
    private class Sink {
        val text = StringBuilder()
        val gmcp = mutableListOf<String>()
        fun gmcpPackages() = gmcp.map { it.substringBefore(' ') }
    }

    /**
     * Reads bytes from [socket] until [marker] appears anywhere in the raw stream (GMCP payloads
     * and room text are ASCII, so the marker bytes show up literally) or [timeoutMs] elapses, then
     * parses the whole buffer offline. Buffering first — rather than a streaming parse — is robust
     * against frames arriving interleaved (the server has two independent socket writers) and
     * against mid-frame socket-read timeouts.
     */
    private fun pump(socket: Socket, raw: java.io.ByteArrayOutputStream, marker: String, timeoutMs: Long = 6000): Sink {
        val input = socket.getInputStream()
        socket.soTimeout = 150
        val deadline = System.currentTimeMillis() + timeoutMs
        val markerBytes = marker.toByteArray(Charsets.US_ASCII)

        while (indexOf(raw.toByteArray(), markerBytes) < 0 && System.currentTimeMillis() < deadline) {
            val b = try {
                input.read()
            } catch (_: java.net.SocketTimeoutException) {
                continue
            }
            if (b == -1) break
            raw.write(b)
        }
        // Drain trailing bytes that arrived back-to-back with the marker — e.g. the rest of a
        // multi-frame GMCP snapshot, where the marker is an early frame. Stops at the first quiet gap.
        val drainEnd = System.currentTimeMillis() + 600
        while (System.currentTimeMillis() < drainEnd) {
            val b = try {
                input.read()
            } catch (_: java.net.SocketTimeoutException) {
                break
            }
            if (b == -1) break
            raw.write(b)
        }
        return parse(raw.toByteArray())
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        if (needle.isEmpty() || haystack.size < needle.size) return -1
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    /** Splits a raw telnet byte buffer into visible text and decoded GMCP payloads. */
    private fun parse(bytes: ByteArray): Sink {
        val sink = Sink()
        var i = 0
        val gmcpOpt = Telnet.GMCP.toInt() and 0xFF
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0xFF) { // IAC
                if (i + 1 >= bytes.size) break
                val cmd = bytes[i + 1].toInt() and 0xFF
                if (cmd == 0xFA) { // SB … IAC SE
                    val option = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
                    var j = i + 3
                    val payload = StringBuilder()
                    while (j < bytes.size) {
                        val x = bytes[j].toInt() and 0xFF
                        if (x == 0xFF && j + 1 < bytes.size && (bytes[j + 1].toInt() and 0xFF) == 0xF0) { j += 2; break }
                        payload.append(x.toChar()); j++
                    }
                    if (option == gmcpOpt) sink.gmcp.add(payload.toString())
                    i = j
                } else {
                    i += 3 // IAC + command + option
                }
            } else {
                when {
                    b >= 0x20 -> sink.text.append(b.toChar())
                    b == '\r'.code || b == '\n'.code -> sink.text.append(' ')
                }
                i++
            }
        }
        return sink
    }

    private fun send(socket: Socket, bytes: ByteArray) {
        socket.getOutputStream().write(bytes); socket.getOutputStream().flush()
    }

    private fun send(socket: Socket, line: String) = send(socket, line.toByteArray(Charsets.US_ASCII))

    // ---- Test -----------------------------------------------------------------

    @Test
    fun loggedInSession_negotiatesGmcp_andRendersRoomAndErrors() = testApplication {
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

        val userId = "user-integ"
        val characterName = "IntegHero"

        // Register the character over WebSocket so it exists, then release the session.
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

        withContext(Dispatchers.IO) {
            waitForPortBlocking(port)
            Socket("127.0.0.1", port).use { socket ->
                // One cumulative buffer for the whole session — frames arrive interleaved (two
                // independent server-side writers), so order-insensitive assertions are safer.
                val raw = java.io.ByteArrayOutputStream()

                loginViaPreAuth(socket, raw, userId, characterName)

                // Now negotiate GMCP like a real MUD client. The snapshot must arrive unprompted:
                // Mudlet gets the player's vitals + room without the player doing anything.
                send(socket, doGmcp)
                val afterGmcp = pump(socket, raw, "Char.Vitals {")
                val pkgs = afterGmcp.gmcpPackages()
                assertTrue("Core.Hello" in pkgs, "expected GMCP handshake, saw: $pkgs")
                assertTrue("Char.Vitals" in pkgs, "expected vitals snapshot, saw: $pkgs")
                assertTrue("Room.Info" in pkgs, "expected room snapshot, saw: $pkgs")

                // `look` re-renders the room and re-pushes Room.Info GMCP (drives Mudlet's mapper).
                send(socket, "look\r\n")
                val look = pump(socket, raw, "Room.Info {")
                assertTrue(look.text.contains("[Exits:"), "expected room render, got: ${look.text}")
                assertTrue("Room.Info" in look.gmcpPackages(), "expected Room.Info GMCP, saw: ${look.gmcpPackages()}")

                // Unknown commands produce a clean error line, not a crash or silence.
                send(socket, "florble\r\n")
                val err = pump(socket, raw, "Unknown command")
                assertTrue(err.text.contains("Unknown command"), "expected error, got: ${err.text}")
                assertFalse(err.text.contains("Exception"), "no stack traces to the player")
            }
        }
    }

    @Test
    fun concurrentSessions_keepIsolatedState() = testApplication {
        val port = findFreePort()
        application {
            module(
                jdbcUrl = testDbUrl(),
                telnetEnabled = true,
                telnetPortOverride = port,
                preAuthSecretOverride = preAuthSecret,
                platformVerifierOverride = testVerifier(),
            )
        }
        startApplication()

        registerCharacter("user-alpha", "AlphaHero")
        registerCharacter("user-beta", "BetaHero")

        withContext(Dispatchers.IO) {
            waitForPortBlocking(port)
            Socket("127.0.0.1", port).use { a ->
                Socket("127.0.0.1", port).use { b ->
                    val rawA = java.io.ByteArrayOutputStream()
                    val rawB = java.io.ByteArrayOutputStream()
                    // Send both pre-auth headers up front — awaiting one login blocks for seconds,
                    // which would let the other's 100ms detection window lapse into legacy login.
                    send(a, preAuthBytesWithChar("user-alpha", "AlphaHero"))
                    send(b, preAuthBytesWithChar("user-beta", "BetaHero"))
                    awaitGameEntry(a, rawA, "AlphaHero")
                    awaitGameEntry(b, rawB, "BetaHero")
                    send(a, doGmcp)
                    send(b, doGmcp)

                    // Pump until Char.Vitals (the snapshot frame *after* Char.Stats) so the
                    // Char.Stats frame is guaranteed complete in the buffer before we parse it.
                    val statsA = pump(a, rawA, "Char.Vitals {").gmcp.first { it.startsWith("Char.Stats") }
                    val statsB = pump(b, rawB, "Char.Vitals {").gmcp.first { it.startsWith("Char.Stats") }

                    // Each connection's GMCP must reflect only its own character — no cross-talk.
                    assertTrue(statsA.contains("\"name\":\"AlphaHero\""), "A got: $statsA")
                    assertTrue(statsB.contains("\"name\":\"BetaHero\""), "B got: $statsB")
                    assertFalse(statsA.contains("BetaHero"), "A leaked B's state: $statsA")
                    assertFalse(statsB.contains("AlphaHero"), "B leaked A's state: $statsB")
                }
            }
        }
    }

    @Test
    fun perIpConnectionLimit_rejectsBeyondCap() = testApplication {
        val port = findFreePort()
        application {
            module(jdbcUrl = testDbUrl(), telnetEnabled = true, telnetPortOverride = port)
        }
        startApplication()

        withContext(Dispatchers.IO) {
            waitForPortBlocking(port)
            val held = mutableListOf<Socket>()
            try {
                // Default per-IP cap is 5. Open 5 that stay connected, idling at the login prompt —
                // reading a byte confirms the server accepted (and thus reserved a slot for) each.
                repeat(5) { i ->
                    val s = Socket("127.0.0.1", port)
                    s.soTimeout = 3000
                    assertTrue(s.getInputStream().read() >= 0, "connection ${i + 1} should be accepted")
                    held.add(s)
                }
                // A 6th from the same IP is rejected with the per-IP message, then disconnected.
                Socket("127.0.0.1", port).use { sixth ->
                    val text = readAscii(sixth, "Too many connections")
                    assertTrue(text.contains("Too many connections"), "expected per-IP rejection, got: '$text'")
                }
            } finally {
                held.forEach { runCatching { it.close() } }
            }
        }
    }

    /** Registers a platform character over WebSocket so telnet pre-auth can log in as it. */
    private suspend fun ApplicationTestBuilder.registerCharacter(userId: String, characterName: String) {
        val ws = createClient { install(WebSockets) }
        ws.webSocket("/game") {
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
    }

    /**
     * Drives a pre-auth login for [character] and returns once the game room is rendered.
     * If the WebSocket registration session hasn't fully released yet (a race that widens under
     * suite load), the server prompts "Kick? (y/n)" — we answer yes and force-login. GMCP is left
     * un-negotiated so it can be enabled *after* login, when player state is populated.
     */
    private fun loginViaPreAuth(socket: Socket, raw: java.io.ByteArrayOutputStream, userId: String, character: String) {
        send(socket, preAuthBytesWithChar(userId, character))
        awaitGameEntry(socket, raw, character)
    }

    /**
     * Waits until the pre-auth login reaches the game (room rendered). Pre-auth bytes must already
     * have been sent — kept separate from [loginViaPreAuth] so concurrent connections can send their
     * headers up front, before either blocks on I/O and lets the 100ms detection window lapse.
     */
    private fun awaitGameEntry(socket: Socket, raw: java.io.ByteArrayOutputStream, character: String) {
        var s = pump(socket, raw, "[Exits:", 5000)
        if (!s.text.contains("[Exits:") && s.text.contains("Kick")) {
            send(socket, "y\r\n")
            s = pump(socket, raw, "[Exits:", 5000)
        }
        assertTrue(s.text.contains("[Exits:"), "pre-auth login for $character did not reach the game: ${s.text}")
    }

    /** Reads plain ASCII from [socket] until [marker] appears, EOF, or timeout. */
    private fun readAscii(socket: Socket, marker: String, timeoutMs: Long = 3000): String {
        val input = socket.getInputStream()
        socket.soTimeout = 150
        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!sb.contains(marker) && System.currentTimeMillis() < deadline) {
            val b = try { input.read() } catch (_: java.net.SocketTimeoutException) { continue }
            if (b == -1) break
            if (b >= 0x20) sb.append(b.toChar())
        }
        return sb.toString()
    }
}
