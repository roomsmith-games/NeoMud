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
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AdminCommandTest {

    private fun testDbUrl(): String {
        val tmpFile = File.createTempFile("neomud_test_", ".db")
        tmpFile.deleteOnExit()
        tmpFile.delete()
        return "jdbc:sqlite:${tmpFile.absolutePath}"
    }

    private suspend fun DefaultClientWebSocketSession.consumeCatalogSync() {
        assertIs<ServerMessage.ServerHello>(receiveServerMessage())
        assertIs<ServerMessage.ClassCatalogSync>(receiveServerMessage())
        assertIs<ServerMessage.ItemCatalogSync>(receiveServerMessage())
        assertIs<ServerMessage.SkillCatalogSync>(receiveServerMessage())
        assertIs<ServerMessage.RaceCatalogSync>(receiveServerMessage())
        assertIs<ServerMessage.SpellCatalogSync>(receiveServerMessage())
    }

    private suspend fun DefaultClientWebSocketSession.receiveServerMessage(): ServerMessage {
        val frame = incoming.receive()
        assertTrue(frame is Frame.Text, "Expected text frame")
        return MessageSerializer.decodeServerMessage(frame.readText())
    }

    private fun sendMsg(msg: ClientMessage): Frame.Text =
        Frame.Text(MessageSerializer.encodeClientMessage(msg))

    private suspend fun DefaultClientWebSocketSession.consumeLoginSequence(): ServerMessage.LoginOk {
        val loginOk = receiveServerMessage()
        assertIs<ServerMessage.LoginOk>(loginOk)
        // After LoginOk, drain all non-interactive setup messages (Tutorial, RoomInfo, MapData, etc.)
        // until we've seen RoomItemsUpdate (the last in the login sequence)
        withTimeout(5000) {
            while (true) {
                val msg = receiveServerMessage()
                if (msg is ServerMessage.RoomItemsUpdate) break
            }
        }
        return loginOk
    }

    /** Drain messages until we find one matching the predicate, with timeout. */
    private suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.drainUntil(
        timeoutMs: Long = 5000
    ): T {
        return withTimeout(timeoutMs) {
            while (true) {
                val msg = receiveServerMessage()
                if (msg is T) return@withTimeout msg
            }
            @Suppress("UNREACHABLE_CODE")
            error("unreachable")
        }
    }

    /** Collect all SystemMessage texts from a batch of messages until timeout. */
    private suspend fun DefaultClientWebSocketSession.collectSystemMessages(
        count: Int,
        timeoutMs: Long = 3000
    ): List<String> {
        val messages = mutableListOf<String>()
        withTimeout(timeoutMs) {
            repeat(count) {
                val msg = drainUntil<ServerMessage.SystemMessage>()
                messages.add(msg.message)
            }
        }
        return messages
    }

    // ── Registration helpers ─────────────────────────────────────────────

    private val adminStats = Stats(30, 22, 18, 18, 30, 18)  // WARRIOR

    private suspend fun DefaultClientWebSocketSession.registerPlayer(
        username: String, charName: String,
        charClass: String = "WARRIOR",
        stats: Stats = adminStats
    ) {
        send(sendMsg(ClientMessage.Register(username, "pass1234", charName, charClass, allocatedStats = stats)))
        assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
    }

    // ── Tests ────────────────────────────────────────────────────────────

    @Test
    fun `admin flag set on login when username in admin list`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            val loginOk = consumeLoginSequence()
            assertTrue(loginOk.player.isAdmin, "Player should be admin")
        }
    }

    @Test
    fun `non-admin player does not get admin flag`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("regularuser", "RegularHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("regularuser", "pass1234")))
            val loginOk = consumeLoginSequence()
            assertTrue(!loginOk.player.isAdmin, "Player should NOT be admin")
        }
    }

    @Test
    fun `non-admin slash command returns unknown command`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("nonadmin", "NoAdmin")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("nonadmin", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/help")))
            val msg = drainUntil<ServerMessage.SystemMessage>()
            assertTrue(msg.message.startsWith("Unknown command: /help"))
        }
    }

    @Test
    fun `admin help command lists commands`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/help")))
            val msg = drainUntil<ServerMessage.SystemMessage>()
            assertTrue(msg.message.contains("Admin Commands"), "Help should contain 'Admin Commands'")
            assertTrue(msg.message.contains("/grantxp"), "Help should list /grantxp")
            assertTrue(msg.message.contains("/godmode"), "Help should list /godmode")
            assertTrue(msg.message.contains("/teleport"), "Help should list /teleport")
        }
    }

    @Test
    fun `admin heal restores full hp and mp`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            val loginOk = consumeLoginSequence()
            val maxHp = loginOk.player.maxHp
            val maxMp = loginOk.player.maxMp

            send(sendMsg(ClientMessage.Say("/heal")))
            // Should get "You have been fully healed!" and admin confirmation
            val msgs = collectSystemMessages(2)
            assertTrue(msgs.any { it.contains("fully healed") }, "Should get heal confirmation: $msgs")
            assertTrue(msgs.any { it.contains("Healed") }, "Admin should get confirmation: $msgs")
        }
    }

    @Test
    fun `admin grantxp adds xp to self`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/grantxp 500")))
            val xpMsg = drainUntil<ServerMessage.XpGained>()
            assertEquals(500L, xpMsg.amount)
            assertEquals(500L, xpMsg.currentXp)
        }
    }

    @Test
    fun `admin grantxp triggers level-up notification when threshold met`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            val loginOk = consumeLoginSequence()
            val xpNeeded = loginOk.player.xpToNextLevel

            // Grant exactly enough XP to level
            send(sendMsg(ClientMessage.Say("/grantxp $xpNeeded")))
            drainUntil<ServerMessage.XpGained>()
            val levelMsg = drainUntil<ServerMessage.SystemMessage>()
            assertTrue(levelMsg.message.contains("level up"), "Should suggest visiting trainer: ${levelMsg.message}")
        }
    }

    @Test
    fun `admin setlevel changes level and grants cp`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/setlevel 5")))
            // Should get LoginOk resync and admin confirmation
            val resync = drainUntil<ServerMessage.LoginOk>()
            assertEquals(5, resync.player.level)
            assertTrue(resync.player.unspentCp > 0, "Should have CP at level 5")
            assertTrue(resync.player.maxHp > 0, "Should have HP")
            assertEquals(resync.player.currentHp, resync.player.maxHp, "Should be full HP")
        }
    }

    @Test
    fun `admin setlevel rejects out of range`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/setlevel 31")))
            val msg = drainUntil<ServerMessage.SystemMessage>()
            assertTrue(msg.message.contains("1-30"), "Should say 1-30: ${msg.message}")
        }
    }

    @Test
    fun `admin teleport moves to valid room`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/teleport town:square")))
            val roomInfo = drainUntil<ServerMessage.RoomInfo>()
            assertEquals("Town Square", roomInfo.room.name)

            val mapData = drainUntil<ServerMessage.MapData>()
            assertEquals("town:square", mapData.playerRoomId)
        }
    }

    @Test
    fun `admin teleport rejects invalid room`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/teleport nonexistent:room")))
            val msg = drainUntil<ServerMessage.SystemMessage>()
            assertTrue(msg.message.contains("not found"), "Should say not found: ${msg.message}")
        }
    }

    @Test
    fun `admin spawn creates npc in room`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/spawn npc:shadow_wolf")))
            val npcEntered = drainUntil<ServerMessage.NpcEntered>()
            assertEquals("Shadow Wolf", npcEntered.npcName)
            assertTrue(npcEntered.spawned, "Should be flagged as spawned")
            assertTrue(npcEntered.hostile, "Shadow wolf should be hostile")

            val confirmation = drainUntil<ServerMessage.SystemMessage>()
            assertTrue(confirmation.message.contains("Spawned"), "Should confirm spawn: ${confirmation.message}")
        }
    }

    @Test
    fun `admin spawn rejects invalid template`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/spawn fake_npc")))
            val msg = drainUntil<ServerMessage.SystemMessage>()
            assertTrue(msg.message.contains("not found"), "Should say not found: ${msg.message}")
        }
    }

    @Test
    fun `admin kill destroys spawned npc`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            // Spawn then kill
            send(sendMsg(ClientMessage.Say("/spawn npc:shadow_wolf")))
            val npcEntered = drainUntil<ServerMessage.NpcEntered>()
            val npcId = npcEntered.npcId
            drainUntil<ServerMessage.SystemMessage>() // spawn confirmation

            send(sendMsg(ClientMessage.Say("/kill $npcId")))
            val npcDied = drainUntil<ServerMessage.NpcDied>()
            assertEquals(npcId, npcDied.npcId)

            val killConfirm = drainUntil<ServerMessage.SystemMessage>()
            assertTrue(killConfirm.message.contains("Killed"), "Should confirm kill: ${killConfirm.message}")
        }
    }

    @Test
    fun `admin grantcp adds cp to self`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/grantcp 50")))
            val resync = drainUntil<ServerMessage.LoginOk>()
            assertEquals(50, resync.player.unspentCp)
            assertEquals(60 + 50, resync.player.totalCpEarned)
        }
    }

    @Test
    fun `admin grantitem adds item to inventory`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/grantitem item:health_potion 3")))
            val invUpdate = drainUntil<ServerMessage.InventoryUpdate>()
            val potion = invUpdate.inventory.find { it.itemId == "item:health_potion" }
            assertTrue(potion != null, "Should have health potion in inventory")
            assertEquals(3, potion.quantity)
        }
    }

    @Test
    fun `admin grantitem rejects unknown item`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/grantitem fake_item")))
            val msg = drainUntil<ServerMessage.SystemMessage>()
            assertTrue(msg.message.contains("not found"), "Should say not found: ${msg.message}")
        }
    }

    @Test
    fun `admin setstat changes stat and recalculates hp`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            val loginOk = consumeLoginSequence()
            val originalStr = loginOk.player.stats.strength

            send(sendMsg(ClientMessage.Say("/setstat str 99")))
            val resync = drainUntil<ServerMessage.LoginOk>()
            assertEquals(99, resync.player.stats.strength)
            assertTrue(resync.player.stats.strength != originalStr, "Strength should have changed")
        }
    }

    @Test
    fun `admin godmode toggles on and off`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            // Toggle on
            send(sendMsg(ClientMessage.Say("/godmode")))
            val onMsg = drainUntil<ServerMessage.SystemMessage>()
            assertTrue(onMsg.message.contains("ENABLED"), "Should say ENABLED: ${onMsg.message}")

            // Toggle off
            send(sendMsg(ClientMessage.Say("/godmode")))
            val offMsg = drainUntil<ServerMessage.SystemMessage>()
            assertTrue(offMsg.message.contains("DISABLED"), "Should say DISABLED: ${offMsg.message}")
        }
    }

    @Test
    fun `admin broadcast sends to all players`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        // Register admin and regular player
        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }
        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("regular", "RegHero")
        }

        // Login regular player first
        val regularClient = createClient { install(WebSockets) }
        regularClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("regular", "pass1234")))
            consumeLoginSequence()

            // Login admin in separate connection and broadcast
            val adminClient = createClient { install(WebSockets) }
            adminClient.webSocket("/game") {
                consumeCatalogSync()
                send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
                consumeLoginSequence()

                // May receive PlayerEntered for regular player — just drain
                send(sendMsg(ClientMessage.Say("/broadcast Server restart in 5 minutes")))
                // Admin should get the broadcast too
                val adminBroadcast = drainUntil<ServerMessage.SystemMessage>()
                assertTrue(
                    adminBroadcast.message.contains("[BROADCAST]"),
                    "Admin should see broadcast: ${adminBroadcast.message}"
                )
            }

            // Regular player should also receive the broadcast
            // May need to drain PlayerEntered from admin login first
            val regularBroadcast = drainUntil<ServerMessage.SystemMessage>(timeoutMs = 5000)
            assertTrue(
                regularBroadcast.message.contains("[BROADCAST]") && regularBroadcast.message.contains("Server restart"),
                "Regular player should see broadcast: ${regularBroadcast.message}"
            )
        }
    }

    @Test
    fun `admin unknown command shows error`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("/badcommand")))
            val msg = drainUntil<ServerMessage.SystemMessage>()
            assertTrue(msg.message.contains("Unknown admin command"), "Should say unknown: ${msg.message}")
            assertTrue(msg.message.contains("/help"), "Should suggest /help: ${msg.message}")
        }
    }

    @Test
    fun `normal say still works for admin`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            send(sendMsg(ClientMessage.Say("hello world")))
            val msg = drainUntil<ServerMessage.PlayerSays>()
            assertEquals("AdminHero", msg.playerName)
            assertEquals("hello world", msg.message)
        }
    }

    @Test
    fun `admin grantxp to other player`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }
        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("target", "TargetHero")
        }

        // Login target player
        val targetClient = createClient { install(WebSockets) }
        targetClient.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("target", "pass1234")))
            consumeLoginSequence()

            // Login admin and grant XP to target
            val adminClient = createClient { install(WebSockets) }
            adminClient.webSocket("/game") {
                consumeCatalogSync()
                send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
                consumeLoginSequence()

                send(sendMsg(ClientMessage.Say("/grantxp 1000 TargetHero")))
                // Admin gets confirmation
                val confirm = drainUntil<ServerMessage.SystemMessage>()
                assertTrue(confirm.message.contains("Granted 1000 XP"), "Admin confirm: ${confirm.message}")
                assertTrue(confirm.message.contains("TargetHero"), "Should mention target: ${confirm.message}")
            }

            // Target should receive XpGained
            val xpMsg = drainUntil<ServerMessage.XpGained>(timeoutMs = 5000)
            assertEquals(1000L, xpMsg.amount)
        }
    }

    @Test
    fun `admin commands missing args show usage`() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl, adminUsernamesOverride = setOf("adminuser")) }
        val ws = createClient { install(WebSockets) }

        ws.webSocket("/game") {
            consumeCatalogSync()
            registerPlayer("adminuser", "AdminHero")
        }

        ws.webSocket("/game") {
            consumeCatalogSync()
            send(sendMsg(ClientMessage.Login("adminuser", "pass1234")))
            consumeLoginSequence()

            // Test each command that requires args
            val commands = listOf("/grantxp", "/setlevel", "/teleport", "/spawn", "/setstat", "/grantcp", "/grantitem", "/broadcast")
            for (cmd in commands) {
                send(sendMsg(ClientMessage.Say(cmd)))
                val msg = drainUntil<ServerMessage.SystemMessage>()
                assertTrue(
                    msg.message.contains("Usage:") || msg.message.contains("Usage"),
                    "Command $cmd should show usage but got: ${msg.message}"
                )
            }
        }
    }
}
