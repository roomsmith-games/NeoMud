package com.neomud.server.platform

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlatformApiClientTest {

    private lateinit var mockServer: HttpServer
    private lateinit var client: PlatformApiClient
    private var loginHandler: (HttpExchange) -> Unit = { it.sendJson(500, "{}") }
    private var anonymousHandler: (HttpExchange) -> Unit = { it.sendJson(500, "{}") }

    @BeforeTest
    fun setup() {
        mockServer = HttpServer.create(InetSocketAddress(0), 0)
        mockServer.createContext("/api/v1/auth/login") { loginHandler(it) }
        mockServer.createContext("/api/v1/auth/anonymous") { anonymousHandler(it) }
        mockServer.start()
        val port = mockServer.address.port
        client = PlatformApiClient("http://localhost:$port")
    }

    @AfterTest
    fun teardown() {
        mockServer.stop(0)
    }

    // ─── verifyCredentials ────────────────────────────────────────────────────

    @Test fun verifyCredentials_200_returnsSuccess() = runBlocking {
        loginHandler = { ex ->
            ex.sendJson(200, """{"user":{"id":"user-123","role":"USER","displayName":"Alice"}}""")
        }
        val result = client.verifyCredentials("alice@example.com", "password")
        assertIs<PlatformCredResult.Success>(result)
        assertEquals("user-123", result.userId)
        assertEquals("USER", result.role)
    }

    @Test fun verifyCredentials_401_returnsAuthFailureInvalidCredentials() = runBlocking {
        loginHandler = { ex ->
            ex.sendJson(401, """{"error":"Invalid email or password"}""")
        }
        val result = client.verifyCredentials("alice@example.com", "wrong")
        assertIs<PlatformCredResult.AuthFailure>(result)
        assertEquals("INVALID_CREDENTIALS", result.code)
    }

    @Test fun verifyCredentials_403EmailNotVerified_returnsAuthFailure() = runBlocking {
        loginHandler = { ex ->
            ex.sendJson(403, """{"error":"Email not verified","code":"EMAIL_NOT_VERIFIED"}""")
        }
        val result = client.verifyCredentials("alice@example.com", "password")
        assertIs<PlatformCredResult.AuthFailure>(result)
        assertEquals("EMAIL_NOT_VERIFIED", result.code)
    }

    @Test fun verifyCredentials_403AccountSuspended_returnsAuthFailure() = runBlocking {
        loginHandler = { ex ->
            ex.sendJson(403, """{"error":"Account suspended","code":"ACCOUNT_SUSPENDED"}""")
        }
        val result = client.verifyCredentials("alice@example.com", "password")
        assertIs<PlatformCredResult.AuthFailure>(result)
        assertEquals("ACCOUNT_SUSPENDED", result.code)
    }

    @Test fun verifyCredentials_500_returnsServiceError() = runBlocking {
        loginHandler = { ex ->
            ex.sendJson(500, """{"error":"Internal error"}""")
        }
        val result = client.verifyCredentials("alice@example.com", "password")
        assertIs<PlatformCredResult.ServiceError>(result); Unit
    }

    @Test fun verifyCredentials_connectionRefused_returnsServiceError() = runBlocking {
        val unreachable = PlatformApiClient("http://localhost:1")
        val result = unreachable.verifyCredentials("a@b.com", "pw")
        assertIs<PlatformCredResult.ServiceError>(result); Unit
    }

    // ─── createGuestSession ───────────────────────────────────────────────────

    @Test fun createGuestSession_200_returnsSuccessWithGuestRole() = runBlocking {
        anonymousHandler = { ex ->
            ex.sendJson(200, """{"userId":"anon-deadbeef"}""")
        }
        val result = client.createGuestSession()
        assertIs<PlatformCredResult.Success>(result)
        assertEquals("anon-deadbeef", result.userId)
        assertEquals("GUEST", result.role)
    }

    @Test fun createGuestSession_500_returnsServiceError() = runBlocking {
        anonymousHandler = { ex ->
            ex.sendJson(500, """{"error":"Internal error"}""")
        }
        val result = client.createGuestSession()
        assertIs<PlatformCredResult.ServiceError>(result); Unit
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun HttpExchange.sendJson(status: Int, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
