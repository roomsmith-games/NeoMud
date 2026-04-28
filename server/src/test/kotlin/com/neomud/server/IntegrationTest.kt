package com.neomud.server

import com.neomud.server.game.GameConfig
import com.neomud.shared.model.Direction
import com.neomud.shared.model.PlayerInfo
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IntegrationTest {

    private fun testDbUrl(): String {
        val tmpFile = File.createTempFile("neomud_test_", ".db")
        tmpFile.deleteOnExit()
        tmpFile.delete() // SQLite will create it
        return "jdbc:sqlite:${tmpFile.absolutePath}"
    }

    /** Consume the server hello + catalog sync messages sent on connect */
    private suspend fun DefaultClientWebSocketSession.consumeCatalogSync() {
        val hello = receiveServerMessage()
        assertIs<ServerMessage.ServerHello>(hello)
        val msg1 = receiveServerMessage()
        assertIs<ServerMessage.ClassCatalogSync>(msg1)
        val msg2 = receiveServerMessage()
        assertIs<ServerMessage.ItemCatalogSync>(msg2)
        val msg3 = receiveServerMessage()
        assertIs<ServerMessage.SkillCatalogSync>(msg3)
        val msg4 = receiveServerMessage()
        assertIs<ServerMessage.RaceCatalogSync>(msg4)
        val msg5 = receiveServerMessage()
        assertIs<ServerMessage.SpellCatalogSync>(msg5)
    }

    @Test
    fun testHealthEndpoint() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }

    @Test
    fun testRegisterAndLogin() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient {
            install(WebSockets)
        }

        wsClient.webSocket("/game") {
            consumeCatalogSync()

            // Register — WARRIOR min: str=20,agi=12,int=8,wil=8,hlt=20,chm=8; +10 each = 60CP
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("testuser", "testpass", "TestHero", "WARRIOR",
                    allocatedStats = Stats(strength = 30, agility = 22, intellect = 18, willpower = 18, health = 30, charm = 18))
            )))

            val registerResponse = receiveServerMessage()
            assertIs<ServerMessage.RegisterOk>(registerResponse)
        }

        // Login in new session
        wsClient.webSocket("/game") {
            consumeCatalogSync()

            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("testuser", "testpass")
            )))

            val loginResponse = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(loginResponse)
            assertEquals("TestHero", loginResponse.player.name)
            assertEquals("WARRIOR", loginResponse.player.characterClass)
            assertEquals("town:temple", loginResponse.player.currentRoomId)

            // Should receive per-world intro tutorial first (#272 — fires before welcome when
            // the loaded world manifest has a non-empty introScript). Default world ships with one
            // for the Warden's Reckoning lore.
            val intro = receiveServerMessage()
            assertIs<ServerMessage.Tutorial>(intro)
            assertEquals("tut_world_intro", intro.key)
            assertTrue(intro.blocking)

            // Should receive first-login welcome tutorial
            val welcome = receiveServerMessage()
            assertIs<ServerMessage.Tutorial>(welcome)
            assertEquals("welcome", welcome.key)
            assertTrue(welcome.content.contains("Greetings, TestHero"))

            // Should receive RoomInfo
            val roomInfo = receiveServerMessage()
            assertIs<ServerMessage.RoomInfo>(roomInfo)
            assertEquals("Temple of the Dawn", roomInfo.room.name)

            // Should receive MapData
            val mapData = receiveServerMessage()
            assertIs<ServerMessage.MapData>(mapData)
            assertEquals("town:temple", mapData.playerRoomId)
            assertTrue(mapData.rooms.isNotEmpty())

            // Should receive InventoryUpdate
            val invUpdate = receiveServerMessage()
            assertIs<ServerMessage.InventoryUpdate>(invUpdate)

            // Should receive RoomItemsUpdate (ground items for current room)
            val roomItems = receiveServerMessage()
            assertIs<ServerMessage.RoomItemsUpdate>(roomItems)
        }
    }

    @Test
    fun testStarterEquipmentGrantedOnRegister() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient { install(WebSockets) }

        // Register a MAGE (should get wooden_staff + leather_chest + 25 copper)
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("starter_mage", "pass1234", "StarterMage", "MAGE", race = "ELF", gender = "male",
                    allocatedStats = Stats(strength = 11, agility = 23, intellect = 37, willpower = 25, health = 13, charm = 25))
            )))
            val reg = receiveServerMessage()
            assertIs<ServerMessage.RegisterOk>(reg)
        }

        // Login and verify inventory contains starter equipment
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("starter_mage", "pass1234")
            )))
            receiveServerMessage() // LoginOk
            receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
            receiveServerMessage() // Welcome SystemMessage
            receiveServerMessage() // RoomInfo
            receiveServerMessage() // MapData

            val invUpdate = receiveServerMessage()
            assertIs<ServerMessage.InventoryUpdate>(invUpdate)

            // Should have starter weapon equipped
            val expectedWeapon = GameConfig.StarterEquipment.weaponForClass("MAGE")
            assertEquals(expectedWeapon, invUpdate.equipment["weapon"], "Starter weapon should be equipped")

            // Should have leather chest equipped
            assertEquals(GameConfig.StarterEquipment.ARMOR_ITEM_ID, invUpdate.equipment["chest"], "Starter armor should be equipped")

            // Should have starting copper
            assertEquals(GameConfig.StarterEquipment.STARTING_COPPER, invUpdate.coins.copper, "Should have starting copper")

            // Inventory should contain both items
            val itemIds = invUpdate.inventory.map { it.itemId }
            assertTrue(expectedWeapon in itemIds, "Inventory should contain starter weapon")
            assertTrue(GameConfig.StarterEquipment.ARMOR_ITEM_ID in itemIds, "Inventory should contain starter armor")
        }
    }

    @Test
    fun testStarterEquipmentWarriorGetsSword() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient { install(WebSockets) }

        // Register a WARRIOR (should get iron_sword)
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("starter_warrior", "pass1234", "StarterWarrior", "WARRIOR",
                    allocatedStats = Stats(strength = 30, agility = 22, intellect = 18, willpower = 18, health = 30, charm = 18))
            )))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }

        // Login and verify
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("starter_warrior", "pass1234")
            )))
            receiveServerMessage() // LoginOk
            receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
            receiveServerMessage() // Welcome SystemMessage
            receiveServerMessage() // RoomInfo
            receiveServerMessage() // MapData

            val invUpdate = receiveServerMessage()
            assertIs<ServerMessage.InventoryUpdate>(invUpdate)
            assertEquals("item:iron_sword", invUpdate.equipment["weapon"], "Warrior should get iron_sword")
            assertEquals(GameConfig.StarterEquipment.ARMOR_ITEM_ID, invUpdate.equipment["chest"])
            assertEquals(GameConfig.StarterEquipment.STARTING_COPPER, invUpdate.coins.copper)
        }
    }

    @Test
    fun testMoveCommand() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient {
            install(WebSockets)
        }

        // Register first
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("mover", "pass1234", "Mover", "THIEF",
                    allocatedStats = Stats(strength = 20, agility = 30, intellect = 22, willpower = 18, health = 20, charm = 25))
            )))
            receiveServerMessage() // RegisterOk
        }

        // Login and move
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("mover", "pass1234")
            )))
            receiveServerMessage() // LoginOk
            receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
            receiveServerMessage() // Welcome SystemMessage
            receiveServerMessage() // RoomInfo
            receiveServerMessage() // MapData
            receiveServerMessage() // InventoryUpdate
            receiveServerMessage() // RoomItemsUpdate

            // Move north to town square (from temple)
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Move(Direction.NORTH)
            )))

            val moveOk = receiveServerMessage()
            assertIs<ServerMessage.MoveOk>(moveOk)
            assertEquals(Direction.NORTH, moveOk.direction)
            assertEquals("Town Square", moveOk.room.name)

            val mapData = receiveServerMessage()
            assertIs<ServerMessage.MapData>(mapData)
            assertEquals("town:square", mapData.playerRoomId)

            val roomItems = receiveServerMessage()
            assertIs<ServerMessage.RoomItemsUpdate>(roomItems)
        }
    }

    @Test
    fun testInvalidMove() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient {
            install(WebSockets)
        }

        // Register
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("stuck", "pass1234", "Stuck", "CLERIC",
                    allocatedStats = Stats(strength = 25, agility = 20, intellect = 20, willpower = 28, health = 25, charm = 22))
            )))
            receiveServerMessage() // RegisterOk
        }

        // Login and try invalid move
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("stuck", "pass1234")
            )))
            receiveServerMessage() // LoginOk
            receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
            receiveServerMessage() // Welcome SystemMessage
            receiveServerMessage() // RoomInfo
            receiveServerMessage() // MapData
            receiveServerMessage() // InventoryUpdate
            receiveServerMessage() // RoomItemsUpdate

            // Town Square has no UP exit
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Move(Direction.UP)
            )))

            val moveError = receiveServerMessage()
            assertIs<ServerMessage.MoveError>(moveError)
        }
    }

    @Test
    fun testUnauthenticatedAccess() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient {
            install(WebSockets)
        }

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            // Try to move without logging in
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Move(Direction.NORTH)
            )))

            val error = receiveServerMessage()
            assertIs<ServerMessage.Error>(error)
            assertTrue(error.message.contains("log in"))
        }
    }

    @Test
    fun testRegisterWithGenderPersists() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient { install(WebSockets) }

        // Register with gender
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("genderuser", "testpass", "Elara", "MAGE", race = "ELF", gender = "female",
                    allocatedStats = Stats(strength = 11, agility = 23, intellect = 37, willpower = 25, health = 13, charm = 25))
            )))
            val registerResponse = receiveServerMessage()
            assertIs<ServerMessage.RegisterOk>(registerResponse)
        }

        // Login and verify gender is in LoginOk
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("genderuser", "testpass")
            )))
            val loginOk = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(loginOk)
            assertEquals("female", loginOk.player.gender)
            assertEquals("ELF", loginOk.player.race)
            // Welcome SystemMessage follows but test doesn't need it
        }
    }

    @Test
    fun testRoomInfoIncludesPlayerInfo() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient { install(WebSockets) }

        // Register two players
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("player_a", "pass1234", "Alice", "WARRIOR", race = "HUMAN", gender = "female",
                    allocatedStats = Stats(strength = 30, agility = 22, intellect = 18, willpower = 18, health = 30, charm = 18))
            )))
            receiveServerMessage() // RegisterOk
        }
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("player_b", "pass1234", "Bob", "THIEF", race = "ELF", gender = "male",
                    allocatedStats = Stats(strength = 15, agility = 35, intellect = 27, willpower = 18, health = 15, charm = 30))
            )))
            receiveServerMessage() // RegisterOk
        }

        // Login both players concurrently so A is still in room when B connects
        coroutineScope {
            val sessionA = async {
                wsClient.webSocket("/game") {
                    consumeCatalogSync()
                    send(Frame.Text(MessageSerializer.encodeClientMessage(
                        ClientMessage.Login("player_a", "pass1234")
                    )))
                    receiveServerMessage() // LoginOk
                    receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
                    receiveServerMessage() // Welcome SystemMessage
                    receiveServerMessage() // RoomInfo
                    receiveServerMessage() // MapData
                    receiveServerMessage() // InventoryUpdate
                    receiveServerMessage() // RoomItemsUpdate

                    // Keep session alive until B finishes
                    delay(5000)
                }
            }

            delay(500) // Let A fully log in

            val sessionB = async {
                wsClient.webSocket("/game") {
                    consumeCatalogSync()
                    send(Frame.Text(MessageSerializer.encodeClientMessage(
                        ClientMessage.Login("player_b", "pass1234")
                    )))
                    receiveServerMessage() // LoginOk
                    receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
                    receiveServerMessage() // Welcome SystemMessage
                    val roomInfo = receiveServerMessage()
                    assertIs<ServerMessage.RoomInfo>(roomInfo)
                    val aliceInfo = roomInfo.players.find { it.name == "Alice" }
                    assertTrue(aliceInfo != null, "Alice should be in room players")
                    assertEquals("WARRIOR", aliceInfo.characterClass)
                    assertEquals("HUMAN", aliceInfo.race)
                    assertEquals("female", aliceInfo.gender)
                    assertEquals(1, aliceInfo.level)
                    assertEquals("images/players/human_female_warrior.webp", aliceInfo.spriteUrl)
                }
            }

            sessionB.await()
            sessionA.cancelAndJoin()
        }
    }

    @Test
    fun testPlayerSpriteUrlConvention() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("spriteuser", "pass1234", "SpriteHero", "WARRIOR", race = "HUMAN", gender = "female",
                    allocatedStats = Stats(strength = 30, agility = 22, intellect = 18, willpower = 18, health = 30, charm = 18))
            )))
            receiveServerMessage() // RegisterOk
        }

        // Login a second player so the first appears in RoomInfo
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("spriteuser2", "pass1234", "Other", "MAGE", race = "ELF", gender = "male",
                    allocatedStats = Stats(strength = 11, agility = 23, intellect = 37, willpower = 25, health = 13, charm = 25))
            )))
            receiveServerMessage() // RegisterOk
        }

        // Login both concurrently so SpriteHero is still in room when Other connects
        coroutineScope {
            val sessionA = async {
                wsClient.webSocket("/game") {
                    consumeCatalogSync()
                    send(Frame.Text(MessageSerializer.encodeClientMessage(
                        ClientMessage.Login("spriteuser", "pass1234")
                    )))
                    receiveServerMessage() // LoginOk
                    receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
                    receiveServerMessage() // Welcome SystemMessage
                    receiveServerMessage() // RoomInfo
                    receiveServerMessage() // MapData
                    receiveServerMessage() // InventoryUpdate
                    receiveServerMessage() // RoomItemsUpdate

                    delay(5000)
                }
            }

            delay(500) // Let SpriteHero fully log in

            val sessionB = async {
                wsClient.webSocket("/game") {
                    consumeCatalogSync()
                    send(Frame.Text(MessageSerializer.encodeClientMessage(
                        ClientMessage.Login("spriteuser2", "pass1234")
                    )))
                    receiveServerMessage() // LoginOk
                    receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
                    receiveServerMessage() // Welcome SystemMessage
                    val roomInfo = receiveServerMessage()
                    assertIs<ServerMessage.RoomInfo>(roomInfo)
                    val spriteHero = roomInfo.players.find { it.name == "SpriteHero" }
                    assertTrue(spriteHero != null)
                    assertEquals("images/players/human_female_warrior.webp", spriteHero.spriteUrl)
                }
            }

            sessionB.await()
            sessionA.cancelAndJoin()
        }
    }

    @Test
    fun testMeditateWithFullMpSendsSystemMessage() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient { install(WebSockets) }

        // Register Mystic — min: str=12,agi=18,int=10,wil=15,hlt=12,chm=8
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("mystic_med", "pass1234", "MysticTester", "MYSTIC",
                    allocatedStats = Stats(strength = 22, agility = 28, intellect = 20, willpower = 25, health = 22, charm = 18))
            )))
            val reg = receiveServerMessage()
            assertIs<ServerMessage.RegisterOk>(reg)
        }

        // Login and try to meditate at full MP
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("mystic_med", "pass1234")
            )))
            val loginOk = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(loginOk)
            val player = loginOk.player
            assertEquals(player.currentMp, player.maxMp, "New character should start at full MP")
            assertTrue(player.maxMp > 0, "Mystic should have MP")

            receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
            receiveServerMessage() // Welcome SystemMessage
            receiveServerMessage() // RoomInfo
            receiveServerMessage() // MapData
            receiveServerMessage() // InventoryUpdate
            receiveServerMessage() // RoomItemsUpdate

            // Now send UseSkill("MEDITATE") with full MP
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.UseSkill("MEDITATE")
            )))

            val response = receiveServerMessage()
            assertIs<ServerMessage.SystemMessage>(response, "Should get SystemMessage when meditating at full MP, got: $response")
            assertTrue(response.message.contains("full"), "Should say mana is full: ${response.message}")
        }
    }

    @Test
    fun testDiscoveryPersistedAcrossSessions() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient { install(WebSockets) }

        // Register
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("explorer", "pass1234", "Explorer", "WARRIOR",
                    allocatedStats = Stats(strength = 30, agility = 22, intellect = 18, willpower = 18, health = 30, charm = 18))
            )))
            receiveServerMessage() // RegisterOk
        }

        // Session 1: Login, move north, disconnect
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("explorer", "pass1234")
            )))
            receiveServerMessage() // LoginOk
            receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
            receiveServerMessage() // Welcome SystemMessage (first login)
            receiveServerMessage() // RoomInfo
            val initialMap = receiveServerMessage()
            assertIs<ServerMessage.MapData>(initialMap)
            // First login — visitedRooms should contain at least the spawn room
            assertTrue(initialMap.visitedRooms.contains("town:temple"))
            receiveServerMessage() // InventoryUpdate
            receiveServerMessage() // RoomItemsUpdate

            // Move north to town square
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Move(Direction.NORTH)
            )))
            receiveServerMessage() // MoveOk
            receiveServerMessage() // MapData
            receiveServerMessage() // RoomItemsUpdate
        }
        // WebSocket close triggers disconnect save — allow time for the finally block to complete
        delay(500)

        // Session 2: Login again — visitedRooms should include both rooms
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("explorer", "pass1234")
            )))
            receiveServerMessage() // LoginOk
            receiveServerMessage() // RoomInfo
            val mapData = receiveServerMessage()
            assertIs<ServerMessage.MapData>(mapData)

            // Should have both the spawn room and the room we moved to
            assertTrue(mapData.visitedRooms.contains("town:temple"), "Should remember spawn room")
            assertTrue(mapData.visitedRooms.contains("town:square"), "Should remember visited room from previous session")
        }
    }

    @Test
    fun testPickupCoinsInvalidTypeReturnsError() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient { install(WebSockets) }

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("coin_test", "pass1234", "CoinTester", "WARRIOR",
                    allocatedStats = Stats(strength = 30, agility = 22, intellect = 18, willpower = 18, health = 30, charm = 18))
            )))
            receiveServerMessage() // RegisterOk
        }

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("coin_test", "pass1234")
            )))
            receiveServerMessage() // LoginOk
            receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
            receiveServerMessage() // Welcome SystemMessage
            receiveServerMessage() // RoomInfo
            receiveServerMessage() // MapData
            receiveServerMessage() // InventoryUpdate
            receiveServerMessage() // RoomItemsUpdate

            // Send invalid coin type "all"
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.PickupCoins("all")
            )))

            val response = receiveServerMessage()
            assertIs<ServerMessage.Error>(response)
            assertTrue(response.message.contains("Invalid coin type"), "Should reject invalid coin type: ${response.message}")
        }
    }

    @Test
    fun testUseSkillWithPrefixIsNormalized() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient { install(WebSockets) }

        // Register a Mystic (has MEDITATE)
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("skill_prefix", "pass1234", "SkillTester", "MYSTIC",
                    allocatedStats = Stats(strength = 22, agility = 28, intellect = 20, willpower = 25, health = 22, charm = 18))
            )))
            receiveServerMessage() // RegisterOk
        }

        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("skill_prefix", "pass1234")
            )))
            val loginOk = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(loginOk)
            receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
            receiveServerMessage() // Welcome SystemMessage
            receiveServerMessage() // RoomInfo
            receiveServerMessage() // MapData
            receiveServerMessage() // InventoryUpdate
            receiveServerMessage() // RoomItemsUpdate

            // Send UseSkill with "skill:" prefix — should be stripped and matched
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.UseSkill("skill:meditate")
            )))

            val response = receiveServerMessage()
            // Mystic at full MP should get "mana is full" message, NOT "Unknown skill"
            assertIs<ServerMessage.SystemMessage>(response, "Should recognize prefixed skill ID, got: $response")
            assertTrue(response.message.contains("full"), "Should say mana is full (skill was recognized): ${response.message}")
        }
    }

    @Test
    fun testRestWithFullHpSendsSystemMessage() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient { install(WebSockets) }

        // Register Warrior — min: str=18,agi=12,int=8,wil=10,hlt=15,chm=8
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("warrior_rest", "pass1234", "RestTester", "WARRIOR",
                    allocatedStats = Stats(strength = 28, agility = 22, intellect = 18, willpower = 20, health = 25, charm = 18))
            )))
            val reg = receiveServerMessage()
            assertIs<ServerMessage.RegisterOk>(reg)
        }

        // Login and try to rest at full HP
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("warrior_rest", "pass1234")
            )))
            val loginOk = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(loginOk)
            val player = loginOk.player
            assertEquals(player.currentHp, player.maxHp, "New character should start at full HP")
            assertTrue(player.maxHp > 0, "Warrior should have HP")

            receiveServerMessage() // World intro Tutorial (#272 — fires before welcome)
            receiveServerMessage() // Welcome SystemMessage
            receiveServerMessage() // RoomInfo
            receiveServerMessage() // MapData
            receiveServerMessage() // InventoryUpdate
            receiveServerMessage() // RoomItemsUpdate

            // Now send UseSkill("REST") with full HP
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.UseSkill("REST")
            )))

            val response = receiveServerMessage()
            assertIs<ServerMessage.SystemMessage>(response, "Should get SystemMessage when resting at full HP, got: $response")
            assertTrue(response.message.contains("full"), "Should say health is full: ${response.message}")
        }
    }

    @Test
    fun testWelcomeMessageOnlyOnFirstLogin() = testApplication {
        application { module(jdbcUrl = testDbUrl()) }

        val wsClient = createClient { install(WebSockets) }

        // Register
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Register("welcome_test", "pass1234", "WelcomeHero", "WARRIOR",
                    allocatedStats = Stats(strength = 30, agility = 22, intellect = 18, willpower = 18, health = 30, charm = 18))
            )))
            assertIs<ServerMessage.RegisterOk>(receiveServerMessage())
        }

        // First login — should receive welcome message
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("welcome_test", "pass1234")
            )))
            val loginOk = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(loginOk)

            // Per-world intro tutorial (#272) fires before welcome on first login when the
            // loaded manifest has a non-empty introScript.
            val intro = receiveServerMessage()
            assertIs<ServerMessage.Tutorial>(intro)
            assertEquals("tut_world_intro", intro.key)

            val welcome = receiveServerMessage()
            assertIs<ServerMessage.Tutorial>(welcome)
            assertEquals("welcome", welcome.key, "Should have key 'welcome'")
            assertTrue(welcome.content.contains("WelcomeHero"), "Welcome should include character name: ${welcome.content}")

            receiveServerMessage() // RoomInfo
            receiveServerMessage() // MapData
            receiveServerMessage() // InventoryUpdate
            receiveServerMessage() // RoomItemsUpdate
        }

        // Allow disconnect save to complete
        delay(500)

        // Second login — should NOT receive welcome message
        wsClient.webSocket("/game") {
            consumeCatalogSync()
            send(Frame.Text(MessageSerializer.encodeClientMessage(
                ClientMessage.Login("welcome_test", "pass1234")
            )))
            val loginOk = receiveServerMessage()
            assertIs<ServerMessage.LoginOk>(loginOk)

            // Next message should be RoomInfo, NOT a welcome SystemMessage
            val roomInfo = receiveServerMessage()
            assertIs<ServerMessage.RoomInfo>(roomInfo, "Second login should NOT get welcome, expected RoomInfo but got: $roomInfo")
        }
    }

    private suspend fun DefaultClientWebSocketSession.receiveServerMessage(): ServerMessage {
        val frame = incoming.receive()
        assertTrue(frame is Frame.Text, "Expected text frame")
        return MessageSerializer.decodeServerMessage(frame.readText())
    }
}
