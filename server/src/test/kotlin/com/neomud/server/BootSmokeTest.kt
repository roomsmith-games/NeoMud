package com.neomud.server

import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Defends the local-dev / OSS-clone contract: `Application.module(jdbcUrl = …)`
 * must boot cleanly with no other env vars set, no NPE on the new
 * WORLD_OWNER_PLATFORM_USER_ID path, and a freshly-registered character is NOT
 * accidentally admin.
 *
 * If a future refactor accidentally wires `WORLD_OWNER_PLATFORM_USER_ID` as
 * required, makes the env-var resolution NPE on missing values, or changes the
 * default `module()` call signature in a way that breaks the 35+ existing
 * tests that already use the same shape, T10/T11 will catch it.
 */
class BootSmokeTest {

    private fun testDbUrl(): String {
        val tmpFile = File.createTempFile("neomud_boot_smoke_", ".db")
        tmpFile.deleteOnExit()
        tmpFile.delete()
        return "jdbc:sqlite:${tmpFile.absolutePath}"
    }

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

    // T10 — module() boots cleanly with only jdbcUrl set; a connected client can
    // round-trip ServerHello + catalog syncs (proves world load + websocket route
    // wiring is intact).
    @Test
    fun `module boots with only jdbcUrl set`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
        }
    }

    // T11 — with no admin overrides set, a non-bob password user is NOT admin.
    // Defends against any code path that accidentally promotes everyone when the
    // override is unset (the null == null trap in particular).
    @Test
    fun `non-admin password user is NOT admin when no overrides set`() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("plainjane", "pass1234", "PlainJane", "WARRIOR", allocatedStats = warriorStats)
            )))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(ClientMessage.Login("plainjane", "pass1234"))))
            val loginOk = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(loginOk)
            assertFalse(loginOk.player.isAdmin, "Plain user must not be admin without explicit override")
            // Drain post-login messages
            withTimeout(5000) {
                while (true) {
                    val m = receiveServerMessage()
                    if (m is ServerMessage.RoomItemsUpdate) break
                }
            }
        }
    }
}
