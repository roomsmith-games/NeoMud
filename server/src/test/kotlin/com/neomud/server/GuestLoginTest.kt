package com.neomud.server

import com.neomud.server.game.GameConfig
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.client.plugins.websocket.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GuestLoginTest {

    private fun testDbUrl(): String {
        val tmpFile = File.createTempFile("neomud_guest_test_", ".db")
        tmpFile.deleteOnExit()
        tmpFile.delete()
        return "jdbc:sqlite:${tmpFile.absolutePath}"
    }

    private suspend fun DefaultClientWebSocketSession.consumeCatalogSync() {
        val hello = receiveServerMessage()
        assertIs<ServerMessage.ServerHello>(hello)
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

    // WARRIOR stats: str=20,agi=12,int=8,wil=8,hlt=20,chm=8 base + 60CP
    private val warriorStats = Stats(
        strength = 30, agility = 22, intellect = 18,
        willpower = 18, health = 30, charm = 18
    )

    @Test
    fun testGuestLoginCreatesAndAutoLogins() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()

            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.GuestLogin(
                    characterName = "GuestWarrior",
                    characterClass = "WARRIOR",
                    race = "HUMAN",
                    gender = "male",
                    allocatedStats = warriorStats
                )
            )))

            // Should get RegisterOk followed by LoginOk
            val registerOk = receiveServerMessage()
            assertIs<ServerMessage.RegisterOk>(registerOk)

            val loginOk = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(loginOk)
            assertEquals("GuestWarrior", loginOk.player.name)
            assertEquals("WARRIOR", loginOk.player.characterClass)
            assertTrue(loginOk.player.isGuest, "Player should be flagged as guest")

            // World intro tutorial first (#272 — fires before welcome when manifest has introScript)
            val intro = receiveServerMessage()
            assertIs<ServerMessage.Tutorial>(intro)
            assertEquals("tut_world_intro", intro.key)

            // Should receive welcome tutorial
            val tutorial = receiveServerMessage()
            assertIs<ServerMessage.Tutorial>(tutorial)

            // Should receive RoomInfo
            val roomInfo = receiveServerMessage()
            assertIs<ServerMessage.RoomInfo>(roomInfo)

            // Should receive MapData
            assertIs<ServerMessage.MapData>(receiveServerMessage())

            // Should receive InventoryUpdate with starter equipment
            val invUpdate = receiveServerMessage()
            assertIs<ServerMessage.InventoryUpdate>(invUpdate)
            val expectedWeapon = GameConfig.StarterEquipment.weaponForClass("WARRIOR")
            assertEquals(expectedWeapon, invUpdate.equipment["weapon"], "Guest should have starter weapon")
            assertEquals(GameConfig.StarterEquipment.STARTING_COPPER, invUpdate.coins.copper, "Guest should have starting copper")

            // Should receive RoomItemsUpdate
            assertIs<ServerMessage.RoomItemsUpdate>(receiveServerMessage())
        }
    }

    @Test
    fun testGuestDisconnectDeletesCharacter() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl) }
        val wsClient = createClient { install(WebSockets) }

        // Create guest and disconnect
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.GuestLogin(
                    characterName = "EphemeralHero",
                    characterClass = "WARRIOR",
                    race = "HUMAN",
                    allocatedStats = warriorStats
                )
            )))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
            assertIs<ServerMessage.LoginOk>(receiveServerMessage())
            // Consume remaining post-login messages
            receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
            receiveServerMessage() // welcome tutorial
            receiveServerMessage() // RoomInfo
            receiveServerMessage() // MapData
            receiveServerMessage() // InventoryUpdate
            receiveServerMessage() // RoomItemsUpdate
        }
        // WebSocket closed — guest data should be deleted

        // Verify: the character name should be available again (re-usable)
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.GuestLogin(
                    characterName = "EphemeralHero",
                    characterClass = "WARRIOR",
                    race = "HUMAN",
                    allocatedStats = warriorStats
                )
            )))
            // Should succeed — name was freed on disconnect
            val registerOk = receiveServerMessage()
            assertIs<ServerMessage.RegisterOk>(registerOk)
            val loginOk = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(loginOk)
            assertEquals("EphemeralHero", loginOk.player.name)
        }
    }

    @Test
    fun testGuestNameCollisionWithPersistentPlayer() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        // Register a persistent player
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register(
                    "realuser", "password123", "TakenName", "WARRIOR",
                    race = "HUMAN", allocatedStats = warriorStats
                )
            )))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }

        // Try to create a guest with the same character name
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.GuestLogin(
                    characterName = "TakenName",
                    characterClass = "WARRIOR",
                    race = "HUMAN",
                    allocatedStats = warriorStats
                )
            )))
            val error = receiveServerMessage()
            assertIs<ServerMessage.AuthError>(error)
            assertTrue(error.reason.contains("already taken", ignoreCase = true))
        }
    }

    @Test
    fun testGuestInvalidCharacterName() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }
        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()

            // Name starting with a number
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.GuestLogin(
                    characterName = "123BadName",
                    characterClass = "WARRIOR",
                    race = "HUMAN",
                    allocatedStats = warriorStats
                )
            )))
            val error = receiveServerMessage()
            assertIs<ServerMessage.AuthError>(error)
            assertTrue(error.reason.contains("Character name"))
        }
    }

    @Test
    fun testGuestCannotLoginWithPassword() = testApplication {
        val dbUrl = testDbUrl()
        application { module(jdbcUrl = dbUrl) }
        val wsClient = createClient { install(WebSockets) }

        // Create a guest
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.GuestLogin(
                    characterName = "GuestNoRelogin",
                    characterClass = "WARRIOR",
                    race = "HUMAN",
                    allocatedStats = warriorStats
                )
            )))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
            assertIs<ServerMessage.LoginOk>(receiveServerMessage())
            // Consume rest
            receiveServerMessage() // tutorial
            receiveServerMessage() // RoomInfo
            receiveServerMessage() // MapData
            receiveServerMessage() // InventoryUpdate
            receiveServerMessage() // RoomItemsUpdate
        }
        // Guest disconnected — data deleted. But even if it weren't,
        // no one knows the random password so they can't login normally.

        // Try to login with a guessed username pattern — should fail
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("guest_00000000", "anything")
            )))
            val error = receiveServerMessage()
            assertIs<ServerMessage.AuthError>(error)
        }
    }
}
