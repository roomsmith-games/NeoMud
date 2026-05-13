package com.neomud.server

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AssetEndpointTest {

    private fun testDbUrl(): String {
        val tmpFile = File.createTempFile("neomud_asset_", ".db")
        tmpFile.deleteOnExit()
        tmpFile.delete()
        return "jdbc:sqlite:${tmpFile.absolutePath}"
    }

    @Test
    fun `asset response includes cache-control with max-age`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val response = client.get("/assets/images/rooms/ashwood_academy_approach.webp")
        assertEquals(HttpStatusCode.OK, response.status)

        val cacheControl = response.headers["Cache-Control"]
        assertNotNull(cacheControl, "Cache-Control header must be present")
        assertTrue("max-age=3600" in cacheControl, "Expected max-age=3600, got: $cacheControl")
        assertTrue("public" in cacheControl, "Expected public directive, got: $cacheControl")
        assertTrue("stale-while-revalidate" in cacheControl, "Expected stale-while-revalidate, got: $cacheControl")
    }

    @Test
    fun `asset response includes etag`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val response = client.get("/assets/images/rooms/ashwood_academy_approach.webp")
        assertEquals(HttpStatusCode.OK, response.status)

        val etag = response.headers["ETag"]
        assertNotNull(etag, "ETag header must be present")
        assertTrue(etag.startsWith("\"") && etag.endsWith("\""), "ETag must be quoted: $etag")
    }

    @Test
    fun `conditional request with matching etag returns 304`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val first = client.get("/assets/images/rooms/ashwood_academy_approach.webp")
        val etag = first.headers["ETag"]!!

        val second = client.get("/assets/images/rooms/ashwood_academy_approach.webp") {
            header("If-None-Match", etag)
        }
        assertEquals(HttpStatusCode.NotModified, second.status)
    }

    @Test
    fun `missing asset returns 404`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val response = client.get("/assets/images/rooms/nonexistent_room.webp")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `path traversal attempt returns 400`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val response = client.get("/assets/../manifest.json")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `asset response includes cors header`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val response = client.get("/assets/images/rooms/ashwood_academy_approach.webp")
        assertEquals("*", response.headers["Access-Control-Allow-Origin"])
    }
}
