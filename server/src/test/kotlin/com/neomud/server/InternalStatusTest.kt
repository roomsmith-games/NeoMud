package com.neomud.server

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises `GET /internal/status` — the endpoint the platform's idle
 * reaper polls over the Docker network to decide whether an ephemeral
 * draft container is still in use.
 */
class InternalStatusTest {

    private fun testDbUrl(): String {
        val tmpFile = File.createTempFile("neomud_status_", ".db")
        tmpFile.deleteOnExit()
        tmpFile.delete()
        return "jdbc:sqlite:${tmpFile.absolutePath}"
    }

    @Test
    fun `internal status returns expected JSON shape`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val response = client.get("/internal/status")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())

        val body = Json.parseToJsonElement(response.bodyAsText()) as JsonObject
        assertTrue("activeConnections" in body, "missing activeConnections field in $body")
        assertTrue("lastActivityAt" in body, "missing lastActivityAt field in $body")

        val count = body["activeConnections"]!!.jsonPrimitive.content.toInt()
        assertEquals(0, count, "expected no sessions on fresh server")

        val lastActivityAt = Instant.parse(body["lastActivityAt"]!!.jsonPrimitive.content)
        // Should be recent (within the last minute)
        val age = java.time.Duration.between(lastActivityAt, Instant.now()).seconds
        assertTrue(age < 60, "lastActivityAt should be recent; age=${age}s")
    }

    @Test
    fun `internal status requires no authentication`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        // No Authorization header, no platform secret — must still succeed.
        val response = client.get("/internal/status")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
