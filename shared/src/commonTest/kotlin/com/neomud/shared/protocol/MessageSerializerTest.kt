package com.neomud.shared.protocol

import com.neomud.shared.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageSerializerTest {

    @Test
    fun testMoveMessageRoundTrip() {
        val original = ClientMessage.Move(Direction.NORTH)
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testLoginMessageRoundTrip() {
        val original = ClientMessage.Login("user1", "pass123")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRegisterMessageRoundTrip() {
        val original = ClientMessage.Register("user1", "pass123", "Gandalf", "MAGE")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRegisterWithRaceRoundTrip() {
        val original = ClientMessage.Register("user1", "pass123", "Gandalf", "MAGE", race = "ELF")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRegisterWithAllocatedStatsRoundTrip() {
        val stats = Stats(strength = 20, agility = 15, intellect = 30, willpower = 25, health = 15, charm = 18)
        val original = ClientMessage.Register("user1", "pass123", "Gandalf", "MAGE", race = "ELF", allocatedStats = stats)
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testSayMessageRoundTrip() {
        val original = ClientMessage.Say("Hello, world!")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPingRoundTrip() {
        val original = ClientMessage.Ping
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testLookRoundTrip() {
        val original = ClientMessage.Look
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRoomInfoRoundTrip() {
        val room = Room(
            id = "town:square",
            name = "Town Square",
            description = "A bustling town square.",
            exits = mapOf(Direction.NORTH to "town:gate", Direction.EAST to "town:market"),
            zoneId = "town",
            x = 0,
            y = 0
        )
        val npc = Npc("npc:guard", "Town Guard", "A stern guard.", "town:square", "patrol")
        val original = ServerMessage.RoomInfo(room, listOf(PlayerInfo("Gandalf", "MAGE", "ELF", "male", 5, "images/players/elf_male_mage.webp")), listOf(npc))
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testHostileNpcRoundTrip() {
        val room = Room(
            id = "forest:path",
            name = "Forest Path",
            description = "A winding path.",
            exits = mapOf(Direction.NORTH to "forest:deep"),
            zoneId = "forest",
            x = 0,
            y = 3
        )
        val hostileNpc = Npc("npc:shadow_wolf", "Shadow Wolf", "A dark wolf.", "forest:path", "wander", hostile = true)
        val friendlyNpc = Npc("npc:guard", "Town Guard", "A stern guard.", "forest:path", "patrol", hostile = false)
        val original = ServerMessage.RoomInfo(room, emptyList(), listOf(hostileNpc, friendlyNpc))
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testMoveOkRoundTrip() {
        val room = Room("forest:edge", "Forest Edge", "Trees everywhere.", mapOf(Direction.SOUTH to "town:gate"), "forest", 0, 2)
        val original = ServerMessage.MoveOk(Direction.NORTH, room, emptyList(), emptyList())
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testMoveOkWithUpExitRoundTrip() {
        // Verifies Direction.UP works as a map key in Room exits
        val room = Room(
            "town:cellar", "Tavern Cellar", "A damp cellar.",
            mapOf(Direction.UP to "town:tavern"), "town", 0, -1,
            lockedExits = mapOf(Direction.UP to 99),
            unpickableExits = setOf(Direction.UP)
        )
        val original = ServerMessage.MoveOk(Direction.DOWN, room, emptyList(), emptyList())
        val json = MessageSerializer.encodeServerMessage(original)
        assertTrue(json.contains("\"UP\""), "Serialized JSON should contain UP direction key")
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
        val decodedRoom = (decoded as ServerMessage.MoveOk).room
        assertEquals("town:tavern", decodedRoom.exits[Direction.UP])
        assertEquals(99, decodedRoom.lockedExits[Direction.UP])
        assertTrue(Direction.UP in decodedRoom.unpickableExits)
    }

    @Test
    fun testMapDataRoundTrip() {
        val rooms = listOf(
            MapRoom("town:square", "Town Square", 0, 0, exits = mapOf(Direction.NORTH to "town:gate"), hasPlayers = true),
            MapRoom("town:gate", "North Gate", 0, 1, exits = mapOf(Direction.SOUTH to "town:square"))
        )
        val original = ServerMessage.MapData(rooms, "town:square")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testMapDataWithLockedAndHiddenExitsRoundTrip() {
        val rooms = listOf(
            MapRoom(
                "town:square", "Town Square", 0, 0,
                exits = mapOf(Direction.NORTH to "town:gate", Direction.EAST to "town:shop"),
                lockedExits = setOf(Direction.NORTH),
                hiddenExits = setOf(Direction.EAST)
            ),
            MapRoom("town:gate", "North Gate", 0, 1, exits = mapOf(Direction.SOUTH to "town:square"))
        )
        val original = ServerMessage.MapData(rooms, "town:square")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testMapDataWithVisitedRoomsRoundTrip() {
        val rooms = listOf(
            MapRoom("town:square", "Town Square", 0, 0, exits = mapOf(Direction.NORTH to "town:gate")),
            MapRoom("town:gate", "North Gate", 0, 1, exits = mapOf(Direction.SOUTH to "town:square"))
        )
        val visited = setOf("town:square", "town:gate", "town:market")
        val original = ServerMessage.MapData(rooms, "town:square", visited)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
        val decodedMap = decoded as ServerMessage.MapData
        assertEquals(visited, decodedMap.visitedRooms)
    }

    @Test
    fun testMapDataVisitedRoomsDefaultEmpty() {
        val rooms = listOf(
            MapRoom("town:square", "Town Square", 0, 0, exits = mapOf(Direction.NORTH to "town:gate"))
        )
        val original = ServerMessage.MapData(rooms, "town:square")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        val decodedMap = decoded as ServerMessage.MapData
        assertTrue(decodedMap.visitedRooms.isEmpty())
    }

    @Test
    fun testMapDataDefaultFieldsBackwardCompatible() {
        // MapRoom with no lockedExits/hiddenExits should deserialize with empty defaults
        val room = MapRoom("r1", "Room", 0, 0, exits = mapOf(Direction.NORTH to "r2"))
        val original = ServerMessage.MapData(listOf(room), "r1")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        val decodedMap = decoded as ServerMessage.MapData
        assertTrue(decodedMap.rooms[0].lockedExits.isEmpty())
        assertTrue(decodedMap.rooms[0].hiddenExits.isEmpty())
    }

    @Test
    fun testPlayerEventsRoundTrip() {
        val entered = ServerMessage.PlayerEntered("Gandalf", "town:square",
            PlayerInfo("Gandalf", "MAGE", "ELF", "male", 5, "images/players/elf_male_mage.webp"))
        val left = ServerMessage.PlayerLeft("Frodo", "town:square", Direction.NORTH)

        assertEquals(entered, MessageSerializer.decodeServerMessage(MessageSerializer.encodeServerMessage(entered)))
        assertEquals(left, MessageSerializer.decodeServerMessage(MessageSerializer.encodeServerMessage(left)))
    }

    @Test
    fun testSystemMessagesRoundTrip() {
        val sys = ServerMessage.SystemMessage("Welcome to NeoMud!")
        val err = ServerMessage.Error("Something went wrong")
        val pong = ServerMessage.Pong

        assertEquals(sys, MessageSerializer.decodeServerMessage(MessageSerializer.encodeServerMessage(sys)))
        assertEquals(err, MessageSerializer.decodeServerMessage(MessageSerializer.encodeServerMessage(err)))
        assertEquals(pong, MessageSerializer.decodeServerMessage(MessageSerializer.encodeServerMessage(pong)))
    }

    @Test
    fun testTutorialRoundTrip() {
        val tutorial = ServerMessage.Tutorial(
            key = "welcome",
            title = "Welcome to NeoMud!",
            content = "Greetings, adventurer!\n\nYour journey begins here."
        )
        val json = MessageSerializer.encodeServerMessage(tutorial)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(tutorial, decoded)
        assertTrue(json.contains("\"type\":\"tutorial\""), "Expected type discriminator: $json")
        assertTrue(json.contains("\"key\":\"welcome\""), "Expected key in JSON: $json")
        // Default blocking=true should be present
        assertEquals(true, (decoded as ServerMessage.Tutorial).blocking)
        assertEquals(null, decoded.targetElement)
    }

    @Test
    fun testTutorialNonBlockingRoundTrip() {
        val tutorial = ServerMessage.Tutorial(
            key = "tut_hostile_npc",
            title = "Hostile Creatures",
            content = "Watch out!",
            blocking = false,
            targetElement = "npc_sprites"
        )
        val json = MessageSerializer.encodeServerMessage(tutorial)
        val decoded = MessageSerializer.decodeServerMessage(json) as ServerMessage.Tutorial
        assertEquals(tutorial, decoded)
        assertEquals(false, decoded.blocking)
        assertEquals("npc_sprites", decoded.targetElement)
        assertTrue(json.contains("\"target_element\":\"npc_sprites\""), "Expected target_element in JSON: $json")
    }

    @Test
    fun testMoveMessageJsonFormat() {
        val msg = ClientMessage.Move(Direction.NORTH)
        val json = MessageSerializer.encodeClientMessage(msg)
        assertTrue(json.contains("\"type\":\"move\""), "Expected type discriminator in JSON: $json")
        assertTrue(json.contains("\"direction\":\"NORTH\""), "Expected direction in JSON: $json")
    }

    @Test
    fun testAttackToggleRoundTrip() {
        val original = ClientMessage.AttackToggle(true)
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testSelectTargetRoundTrip() {
        val original = ClientMessage.SelectTarget("npc:shadow_wolf")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testSelectTargetNullRoundTrip() {
        val original = ClientMessage.SelectTarget(null)
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testCombatHitRoundTrip() {
        val original = ServerMessage.CombatHit("Hero", "Shadow Wolf", 12, 38, 50, isPlayerDefender = false)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testCombatHitPlayerDefenderRoundTrip() {
        val original = ServerMessage.CombatHit("Shadow Wolf", "Hero", 8, 92, 100, isPlayerDefender = true)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testNpcDiedRoundTrip() {
        val original = ServerMessage.NpcDied("npc:shadow_wolf", "Shadow Wolf", "Hero", "forest:path")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPlayerDiedRoundTrip() {
        val original = ServerMessage.PlayerDied("Shadow Wolf", "town:square", 100)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testAttackModeUpdateRoundTrip() {
        val original = ServerMessage.AttackModeUpdate(true)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testNpcEnteredWithCombatFieldsRoundTrip() {
        val original = ServerMessage.NpcEntered("Shadow Wolf", "forest:path", "npc:shadow_wolf", true, 45, 50)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testNpcEnteredWithSoundFieldsRoundTrip() {
        val original = ServerMessage.NpcEntered(
            "Shadow Wolf", "forest:path", "npc:shadow_wolf", true, 45, 50,
            spawned = true, templateId = "npc:shadow_wolf",
            attackSound = "wolf_bite", missSound = "wolf_miss", deathSound = "wolf_death",
            interactSound = "", exitSound = ""
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
        val npcEntered = decoded as ServerMessage.NpcEntered
        assertEquals("wolf_bite", npcEntered.attackSound)
        assertEquals("wolf_miss", npcEntered.missSound)
        assertEquals("wolf_death", npcEntered.deathSound)
    }

    @Test
    fun testNpcLeftWithIdRoundTrip() {
        val original = ServerMessage.NpcLeft("Shadow Wolf", "forest:path", Direction.NORTH, "npc:shadow_wolf")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testNpcWithHpRoundTrip() {
        val npc = Npc("npc:wolf", "Wolf", "A grey wolf.", "forest:path", "wander", hostile = true, currentHp = 30, maxHp = 50)
        val room = Room("forest:path", "Forest Path", "A winding path.", mapOf(Direction.NORTH to "forest:deep"), "forest", 0, 3)
        val original = ServerMessage.RoomInfo(room, emptyList(), listOf(npc))
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testDirectionOpposite() {
        assertEquals(Direction.SOUTH, Direction.NORTH.opposite())
        assertEquals(Direction.NORTH, Direction.SOUTH.opposite())
        assertEquals(Direction.WEST, Direction.EAST.opposite())
        assertEquals(Direction.EAST, Direction.WEST.opposite())
        assertEquals(Direction.DOWN, Direction.UP.opposite())
        assertEquals(Direction.UP, Direction.DOWN.opposite())
        assertEquals(Direction.SOUTHWEST, Direction.NORTHEAST.opposite())
        assertEquals(Direction.NORTHEAST, Direction.SOUTHWEST.opposite())
        assertEquals(Direction.SOUTHEAST, Direction.NORTHWEST.opposite())
        assertEquals(Direction.NORTHWEST, Direction.SOUTHEAST.opposite())
    }

    @Test
    fun testStatsSerializationRoundTrip() {
        val stats = Stats(strength = 40, agility = 35, intellect = 30, willpower = 25, health = 45, charm = 20)
        val player = Player(
            name = "TestHero",
            characterClass = "WARRIOR",
            stats = stats,
            currentHp = 100,
            maxHp = 120,
            currentMp = 50,
            maxMp = 60,
            level = 1,
            currentRoomId = "town:square"
        )
        val original = ServerMessage.LoginOk(player)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testActiveEffectsUpdateRoundTrip() {
        val effects = listOf(
            ActiveEffect("Poison", EffectType.POISON, 3, 5),
            ActiveEffect("Regeneration", EffectType.HEAL_OVER_TIME, 5, 3)
        )
        val original = ServerMessage.ActiveEffectsUpdate(effects)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testActiveEffectsUpdateEmptyRoundTrip() {
        val original = ServerMessage.ActiveEffectsUpdate(emptyList())
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPlayerDiedWithRespawnMpRoundTrip() {
        val original = ServerMessage.PlayerDied("Shadow Wolf", "town:square", 100, 72)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPlayerWithMpFieldsRoundTrip() {
        val stats = Stats(strength = 40, agility = 35, intellect = 30, willpower = 25, health = 45, charm = 20)
        val player = Player(
            name = "TestHero",
            characterClass = "WARRIOR",
            stats = stats,
            currentHp = 100,
            maxHp = 120,
            currentMp = 50,
            maxMp = 60,
            level = 1,
            currentRoomId = "town:square"
        )
        val original = ServerMessage.LoginOk(player)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testClassCatalogSyncRoundTrip() {
        val classes = listOf(
            CharacterClassDef("WARRIOR", "Warrior", "A master of martial combat",
                minimumStats = Stats(strength = 20, agility = 12, intellect = 8, willpower = 8, health = 20, charm = 8),
                hpPerLevelMin = 6, hpPerLevelMax = 10),
            CharacterClassDef("MAGE", "Mage", "A scholarly mage",
                minimumStats = Stats(strength = 6, agility = 8, intellect = 22, willpower = 15, health = 8, charm = 10),
                hpPerLevelMin = 3, hpPerLevelMax = 6, mpPerLevelMin = 5, mpPerLevelMax = 10,
                magicSchools = mapOf("mage" to 3))
        )
        val original = ServerMessage.ClassCatalogSync(classes)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testItemCatalogSyncRoundTrip() {
        val items = listOf(
            Item("item:sword", "Sword", "A blade.", "weapon", "weapon", damageBonus = 3, damageRange = 6),
            Item("item:potion", "Potion", "Heals.", "consumable", useEffect = "heal:25", stackable = true, maxStack = 10)
        )
        val original = ServerMessage.ItemCatalogSync(items)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testViewInventoryRoundTrip() {
        val original = ClientMessage.ViewInventory
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testEquipItemRoundTrip() {
        val original = ClientMessage.EquipItem("item:iron_sword", "weapon")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testUnequipItemRoundTrip() {
        val original = ClientMessage.UnequipItem("weapon")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testUseItemRoundTrip() {
        val original = ClientMessage.UseItem("item:health_potion")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testInventoryUpdateRoundTrip() {
        val inventory = listOf(
            InventoryItem("item:iron_sword", 1, true, "weapon"),
            InventoryItem("item:health_potion", 3)
        )
        val equipment = mapOf("weapon" to "item:iron_sword")
        val original = ServerMessage.InventoryUpdate(inventory, equipment)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testLootReceivedRoundTrip() {
        val items = listOf(
            LootedItem("item:wolf_pelt", "Wolf Pelt", 1),
            LootedItem("item:health_potion", "Health Potion", 1)
        )
        val original = ServerMessage.LootReceived("Shadow Wolf", items)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testItemUsedRoundTrip() {
        val original = ServerMessage.ItemUsed("Health Potion", "You drink the potion.", 95, 50)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testEquipUpdateRoundTrip() {
        val original = ServerMessage.EquipUpdate("weapon", "item:iron_sword", "Iron Sword")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testEquipUpdateUnequipRoundTrip() {
        val original = ServerMessage.EquipUpdate("weapon", null, null)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testInventoryUpdateWithCoinsRoundTrip() {
        val inventory = listOf(InventoryItem("item:iron_sword", 1, true, "weapon"))
        val equipment = mapOf("weapon" to "item:iron_sword")
        val coins = Coins(copper = 50, silver = 10, gold = 2, platinum = 0)
        val original = ServerMessage.InventoryUpdate(inventory, equipment, coins)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRoomItemsUpdateRoundTrip() {
        val items = listOf(GroundItem("item:wolf_pelt", 2), GroundItem("item:health_potion", 1))
        val coins = Coins(copper = 15, silver = 1)
        val original = ServerMessage.RoomItemsUpdate(items, coins)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testLootDroppedRoundTrip() {
        val items = listOf(LootedItem("item:wolf_pelt", "Wolf Pelt", 1))
        val coins = Coins(copper = 12, silver = 1)
        val original = ServerMessage.LootDropped("Shadow Wolf", items, coins)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPickupResultRoundTrip() {
        val original = ServerMessage.PickupResult("Wolf Pelt", 1)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPickupResultCoinRoundTrip() {
        val original = ServerMessage.PickupResult("copper", 15, isCoin = true)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPickupItemRoundTrip() {
        val original = ClientMessage.PickupItem("item:wolf_pelt", 2)
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPickupCoinsRoundTrip() {
        val original = ClientMessage.PickupCoins("gold")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testDropItemRoundTrip() {
        val original = ClientMessage.DropItem("item:health_potion", 2)
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testDropItemDefaultQuantity() {
        val original = ClientMessage.DropItem("item:wolf_pelt")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
        assertEquals(1, (decoded as ClientMessage.DropItem).quantity)
    }

    @Test
    fun testSneakToggleRoundTrip() {
        val original = ClientMessage.SneakToggle(true)
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testSneakToggleDisableRoundTrip() {
        val original = ClientMessage.SneakToggle(false)
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testUseSkillRoundTrip() {
        val original = ClientMessage.UseSkill("BACKSTAB", "npc:shadow_wolf")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testUseSkillNoTargetRoundTrip() {
        val original = ClientMessage.UseSkill("SNEAK")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testStealthUpdateRoundTrip() {
        val original = ServerMessage.StealthUpdate(true, "You slip into the shadows.")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testStealthUpdateNoMessageRoundTrip() {
        val original = ServerMessage.StealthUpdate(false)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testSkillCatalogSyncRoundTrip() {
        val skills = listOf(
            SkillDef("SNEAK", "Sneak", "Enter stealth.", "stealth", "agility", "willpower", cooldownTicks = 2, difficulty = 15),
            SkillDef("BACKSTAB", "Backstab", "First attack from stealth = 3x.", "combat", "agility", "strength", isPassive = true, properties = mapOf("damageMultiplier" to "3"))
        )
        val original = ServerMessage.SkillCatalogSync(skills)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testCharacterClassDefWithSkillsRoundTrip() {
        val classDef = CharacterClassDef(
            "THIEF", "Thief", "A scoundrel",
            minimumStats = Stats(strength = 10, agility = 20, intellect = 12, willpower = 8, health = 10, charm = 15),
            skills = listOf("SNEAK", "BACKSTAB"),
            hpPerLevelMin = 4, hpPerLevelMax = 7
        )
        val original = ServerMessage.ClassCatalogSync(listOf(classDef))
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testCastSpellRoundTrip() {
        val original = ClientMessage.CastSpell("FIREBALL", "npc:shadow_wolf")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testCastSpellNoTargetRoundTrip() {
        val original = ClientMessage.CastSpell("MINOR_HEAL")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testSpellCatalogSyncRoundTrip() {
        val spells = listOf(
            SpellDef("FIREBALL", "Fireball", "A fireball.", "mage", SpellType.DAMAGE, 18,
                cooldownTicks = 4, levelRequired = 5, basePower = 22, targetType = TargetType.ENEMY,
                castMessage = "hurls a fireball at"),
            SpellDef("MINOR_HEAL", "Minor Heal", "Heals.", "priest", SpellType.HEAL, 5,
                cooldownTicks = 2, primaryStat = "willpower", basePower = 10, targetType = TargetType.SELF)
        )
        val original = ServerMessage.SpellCatalogSync(spells)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testSpellCastResultRoundTrip() {
        val original = ServerMessage.SpellCastResult(true, "Fireball", "Hero hurls a fireball!", 42, null)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testSpellCastResultWithHpRoundTrip() {
        val original = ServerMessage.SpellCastResult(true, "Minor Heal", "Hero heals!", 38, 95)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testSpellEffectRoundTrip() {
        val original = ServerMessage.SpellEffect("Hero", "Shadow Wolf", "Fireball", 25, 15, 50)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testSpellEffectPlayerTargetRoundTrip() {
        val original = ServerMessage.SpellEffect("Hero", "Hero", "Minor Heal", 15, 90, 100, isPlayerTarget = true)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testSkillEffectRoundTrip() {
        val original = ServerMessage.SkillEffect("Hero", "BASH", "Shadow Wolf", "npc_1", 15, 35, 50, "Hero bashes Shadow Wolf for 15 damage, stunning them!")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testSkillEffectKickRoundTrip() {
        val original = ServerMessage.SkillEffect("Hero", "KICK", "Goblin", "npc_2", 8, 0, 30, "Hero kicks Goblin for 8 damage, finishing them off!")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRaceDefRoundTrip() {
        val raceDef = RaceDef(
            id = "HUMAN",
            name = "Human",
            description = "Balanced and adaptable.",
            statModifiers = Stats(0, 0, 0, 0, 0, 0),
            xpModifier = 1.0
        )
        val original = ServerMessage.RaceCatalogSync(listOf(raceDef))
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPlayerInfoRoundTrip() {
        val info = PlayerInfo("Gandalf", "MAGE", "ELF", "male", 5, "images/players/elf_male_mage.webp")
        val room = Room("town:square", "Town Square", "A bustling town square.",
            mapOf(Direction.NORTH to "town:gate"), "town", 0, 0)
        val original = ServerMessage.RoomInfo(room, listOf(info), emptyList())
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRoomInfoWithPlayerInfoRoundTrip() {
        val room = Room("town:square", "Town Square", "A bustling town square.",
            mapOf(Direction.NORTH to "town:gate"), "town", 0, 0)
        val players = listOf(
            PlayerInfo("Gandalf", "MAGE", "ELF", "male", 5, "images/players/elf_male_mage.webp"),
            PlayerInfo("Frodo", "THIEF", "HALFLING", "male", 3, "images/players/halfling_male_thief.webp")
        )
        val original = ServerMessage.RoomInfo(room, players, emptyList())
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testMoveOkWithPlayerInfoRoundTrip() {
        val room = Room("forest:edge", "Forest Edge", "Trees everywhere.",
            mapOf(Direction.SOUTH to "town:gate"), "forest", 0, 2)
        val players = listOf(
            PlayerInfo("Aragorn", "WARRIOR", "HUMAN", "male", 10, "images/players/human_male_warrior.webp")
        )
        val original = ServerMessage.MoveOk(Direction.NORTH, room, players, emptyList())
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPlayerEnteredWithInfoRoundTrip() {
        val info = PlayerInfo("Gandalf", "MAGE", "ELF", "male", 5, "images/players/elf_male_mage.webp")
        val original = ServerMessage.PlayerEntered("Gandalf", "town:square", info)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testPlayerEnteredBackwardCompatRoundTrip() {
        val original = ServerMessage.PlayerEntered("Gandalf", "town:square", null)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testMeditateUpdateRoundTrip() {
        val original = ServerMessage.MeditateUpdate(true, "You enter a meditative state.")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testMeditateUpdateNoMessageRoundTrip() {
        val original = ServerMessage.MeditateUpdate(false)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRestUpdateRoundTrip() {
        val original = ServerMessage.RestUpdate(true, "You begin resting.")
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRestUpdateNoMessageRoundTrip() {
        val original = ServerMessage.RestUpdate(false)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testRegisterWithGenderRoundTrip() {
        val original = ClientMessage.Register("user1", "pass123", "Elara", "MAGE", race = "ELF", gender = "female")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testCombatHitParryRoundTrip() {
        val original = ServerMessage.CombatHit(
            "Shadow Wolf", "Hero", 3, 90, 100,
            isPlayerDefender = true, isParry = true
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testTrackResultSuccessRoundTrip() {
        val original = ServerMessage.TrackResult(
            success = true,
            direction = Direction.NORTH,
            targetName = "Shadow Wolf",
            message = "You find tracks leading north..."
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testTrackResultFailureRoundTrip() {
        val original = ServerMessage.TrackResult(
            success = false,
            message = "You don't find any tracks here."
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testInteractFeatureRoundTrip() {
        val original = ClientMessage.InteractFeature("lever_1")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testInteractResultRoundTrip() {
        val original = ServerMessage.InteractResult(
            success = true,
            featureName = "Ancient Lever",
            message = "You pull the ancient lever...",
            sound = "lever_pull"
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testInteractResultNoSoundRoundTrip() {
        val original = ServerMessage.InteractResult(
            success = false,
            featureName = "Rusty Button",
            message = "It doesn't seem to do anything."
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testReadySpellRoundTrip() {
        val original = ClientMessage.ReadySpell("FIREBALL")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testReadySpellClearRoundTrip() {
        val original = ClientMessage.ReadySpell(null)
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testVendorInfoWithHaggleRoundTrip() {
        val items = listOf(
            VendorItem(
                Item("item:sword", "Sword", "A blade.", "weapon", "weapon", damageBonus = 3, value = 100),
                Coins(gold = 1)
            )
        )
        val original = ServerMessage.VendorInfo(
            vendorName = "Test Vendor",
            items = items,
            playerCoins = Coins(copper = 50, silver = 10),
            playerInventory = emptyList(),
            playerCharm = 75,
            hasHaggle = true
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testVendorInfoWithExitSoundRoundTrip() {
        val original = ServerMessage.VendorInfo(
            vendorName = "Barkeep Grom",
            items = emptyList(),
            playerCoins = Coins(),
            playerInventory = emptyList(),
            interactSound = "barkeep_intro",
            exitSound = "barkeep_exit"
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
        assertTrue(json.contains("barkeep_exit"), "JSON should contain exitSound value")
    }

    @Test
    fun testServerShutdownRoundTrip() {
        val original = ServerMessage.ServerShutdown("Server shutting down in 30 seconds!", 30)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testServerShutdownZeroSecondsRoundTrip() {
        val original = ServerMessage.ServerShutdown("Server is shutting down NOW. Goodbye!", 0)
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testServerShutdownJsonFormat() {
        val msg = ServerMessage.ServerShutdown("Shutting down!", 10)
        val json = MessageSerializer.encodeServerMessage(msg)
        assertTrue(json.contains("\"type\":\"server_shutdown\""), "Expected type discriminator: $json")
        assertTrue(json.contains("\"secondsRemaining\":10"), "Expected secondsRemaining in JSON: $json")
    }

    @Test
    fun testTrainerInfoWithExitSoundRoundTrip() {
        val original = ServerMessage.TrainerInfo(
            canLevelUp = false,
            unspentCp = 5,
            totalCpEarned = 10,
            baseStats = Stats(),
            currentStats = Stats(),
            interactSound = "guildmaster_intro",
            exitSound = "guildmaster_exit"
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
        assertTrue(json.contains("guildmaster_exit"), "JSON should contain exitSound value")
    }

    @Test
    fun testStatTrainedWithHpMpRoundTrip() {
        val original = ServerMessage.StatTrained(
            stat = "willpower",
            newValue = 33,
            cpSpent = 5,
            remainingCp = 5,
            currentHp = 55,
            maxHp = 55,
            currentMp = 42,
            maxMp = 42
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
        val msg = decoded as ServerMessage.StatTrained
        assertEquals(55, msg.maxHp)
        assertEquals(42, msg.maxMp)
    }

    @Test
    fun testStatTrainedBackwardCompatible() {
        // Old format without HP/MP fields should deserialize with defaults
        val oldJson = """{"type":"stat_trained","stat":"strength","newValue":20,"cpSpent":2,"remainingCp":8}"""
        val decoded = MessageSerializer.decodeServerMessage(oldJson) as ServerMessage.StatTrained
        assertEquals("strength", decoded.stat)
        assertEquals(20, decoded.newValue)
        assertEquals(0, decoded.currentHp, "Default HP should be 0 for backward compatibility")
        assertEquals(0, decoded.maxHp)
    }

    // ─── Handshake messages ──────────────────────────────

    @Test
    fun testServerHelloRoundTrip() {
        val original = ServerMessage.ServerHello(
            engineVersion = "0.1.0.0",
            protocolVersion = 1,
            worldName = "Test World",
            worldVersion = "0.1.0"
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testClientHelloRoundTrip() {
        val original = ClientMessage.ClientHello(
            clientVersion = "0.1.0.0",
            protocolVersion = 1
        )
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    // ─── Platform auth messages ─────────────────────────────

    @Test
    fun clientHelloWithPlatformToken() {
        val original = ClientMessage.ClientHello(
            clientVersion = "0.1.0.0",
            protocolVersion = 1,
            platformToken = "eyJhbGciOiJSUzI1NiJ9.test"
        )
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json) as ClientMessage.ClientHello
        assertEquals("eyJhbGciOiJSUzI1NiJ9.test", decoded.platformToken)
    }

    @Test
    fun clientHelloWithoutPlatformToken() {
        val original = ClientMessage.ClientHello(
            clientVersion = "0.1.0.0",
            protocolVersion = 1
        )
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json) as ClientMessage.ClientHello
        assertNull(decoded.platformToken)
    }

    @Test
    fun platformAuthOkReturningPlayer() {
        val original = ServerMessage.PlatformAuthOk(
            characterName = "Aragorn",
            platformUserId = "cltest123456789012345",
            needsCharacterCreation = false
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json) as ServerMessage.PlatformAuthOk
        assertEquals("Aragorn", decoded.characterName)
        assertEquals(false, decoded.needsCharacterCreation)
    }

    @Test
    fun platformAuthOkNewPlayer() {
        val original = ServerMessage.PlatformAuthOk(
            characterName = null,
            platformUserId = "cltest123456789012345",
            needsCharacterCreation = true
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json) as ServerMessage.PlatformAuthOk
        assertNull(decoded.characterName)
        assertEquals(true, decoded.needsCharacterCreation)
    }

    @Test
    fun platformLoginRoundTrip() {
        val original = ClientMessage.PlatformLogin
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun platformRegisterRoundTrip() {
        val original = ClientMessage.PlatformRegister(
            characterName = "TestHero",
            characterClass = "WARRIOR",
            race = "HUMAN",
            gender = "male"
        )
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json) as ClientMessage.PlatformRegister
        assertEquals("TestHero", decoded.characterName)
        assertEquals("WARRIOR", decoded.characterClass)
    }

    // ─── Guest login messages ─────────────────────────────

    @Test
    fun guestLoginRoundTrip() {
        val stats = Stats(strength = 30, agility = 22, intellect = 18, willpower = 18, health = 30, charm = 18)
        val original = ClientMessage.GuestLogin(
            characterName = "GuestHero",
            characterClass = "WARRIOR",
            race = "HUMAN",
            gender = "male",
            allocatedStats = stats
        )
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json) as ClientMessage.GuestLogin
        assertEquals("GuestHero", decoded.characterName)
        assertEquals("WARRIOR", decoded.characterClass)
        assertEquals("HUMAN", decoded.race)
        assertEquals("male", decoded.gender)
        assertEquals(stats, decoded.allocatedStats)
    }

    @Test
    fun guestLoginMinimalRoundTrip() {
        val original = ClientMessage.GuestLogin(
            characterName = "MinGuest",
            characterClass = "WARRIOR"
        )
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json) as ClientMessage.GuestLogin
        assertEquals("MinGuest", decoded.characterName)
        assertEquals("", decoded.race)
        assertEquals("neutral", decoded.gender)
    }

    @Test
    fun playerIsGuestFieldSerialization() {
        val player = Player(
            name = "TestGuest",
            characterClass = "WARRIOR",
            stats = Stats(),
            currentHp = 100,
            maxHp = 100,
            level = 1,
            currentRoomId = "test:room",
            isGuest = true
        )
        val json = MessageSerializer.encodeServerMessage(ServerMessage.LoginOk(player))
        assertTrue(json.contains("\"isGuest\":true") || json.contains("\"is_guest\":true"),
            "Serialized JSON should contain isGuest field")

        val decoded = MessageSerializer.decodeServerMessage(json) as ServerMessage.LoginOk
        assertTrue(decoded.player.isGuest, "Deserialized player should have isGuest=true")
    }

    @Test
    fun playerIsGuestDefaultsFalse() {
        val player = Player(
            name = "NormalPlayer",
            characterClass = "WARRIOR",
            stats = Stats(),
            currentHp = 100,
            maxHp = 100,
            level = 1,
            currentRoomId = "test:room"
        )
        val json = MessageSerializer.encodeServerMessage(ServerMessage.LoginOk(player))
        val decoded = MessageSerializer.decodeServerMessage(json) as ServerMessage.LoginOk
        assertTrue(!decoded.player.isGuest, "Default player should not be guest")
    }

    @Test
    fun testChoicePromptRoundTrip() {
        val original = ServerMessage.ChoicePrompt(
            featureId = "fracture_choice",
            label = "The First Seal",
            question = "Choose your path.",
            options = listOf(
                ServerMessage.ChoiceOption("seal", "Restore the Seal"),
                ServerMessage.ChoiceOption("sunder", "Shatter the Seal")
            )
        )
        val json = MessageSerializer.encodeServerMessage(original)
        val decoded = MessageSerializer.decodeServerMessage(json)
        assertEquals(original, decoded)
    }

    @Test
    fun testMakeChoiceRoundTrip() {
        val original = ClientMessage.MakeChoice(featureId = "fracture_choice", choiceId = "seal")
        val json = MessageSerializer.encodeClientMessage(original)
        val decoded = MessageSerializer.decodeClientMessage(json)
        assertEquals(original, decoded)
    }
}
