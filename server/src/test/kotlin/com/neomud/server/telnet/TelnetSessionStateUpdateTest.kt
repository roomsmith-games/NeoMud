package com.neomud.server.telnet

import com.neomud.shared.model.*
import com.neomud.shared.protocol.ServerMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelnetSessionStateUpdateTest {

    private fun state() = TelnetSessionState()

    private fun npc(id: String, name: String) = Npc(
        id = id, name = name, description = "", currentRoomId = "z:r", behaviorType = "HOSTILE"
    )

    private fun player(name: String) = PlayerInfo(
        name = name, characterClass = "Warrior", race = "Human", level = 1
    )

    private fun room(
        id: String = "z:r",
        interactables: List<RoomInteractable> = emptyList()
    ) = Room(
        id = id, name = "Test Room", description = "", exits = emptyMap(),
        zoneId = "z", x = 0, y = 0, interactables = interactables
    )

    // ---- LoginOk ----

    private fun testPlayer(
        name: String = "Aldric", cls: String = "Warrior", level: Int = 5,
        hp: Int = 60, maxHp: Int = 80, mp: Int = 20, maxMp: Int = 30
    ) = Player(
        name = name, characterClass = cls, stats = Stats(),
        currentHp = hp, maxHp = maxHp, currentMp = mp, maxMp = maxMp,
        level = level, currentRoomId = "z:r"
    )

    @Test fun loginOkSetsPlayerName() {
        val s = state()
        s.update(ServerMessage.LoginOk(testPlayer(name = "Aldric")))
        assertEquals("Aldric", s.playerName)
    }

    @Test fun loginOkSetsStats() {
        val s = state()
        s.update(ServerMessage.LoginOk(testPlayer(cls = "Rogue", level = 3, hp = 45, maxHp = 60, mp = 10, maxMp = 15)))
        assertEquals("Rogue", s.playerClass)
        assertEquals(3, s.playerLevel)
        assertEquals(45, s.currentHp)
        assertEquals(60, s.maxHp)
        assertEquals(10, s.currentMp)
        assertEquals(15, s.maxMp)
    }

    // ---- RoomInfo ----

    @Test fun roomInfoUpdatesRoomId() {
        val s = state()
        s.update(ServerMessage.RoomInfo(room("millhaven:square"), emptyList(), emptyList()))
        assertEquals("millhaven:square", s.currentRoomId)
    }

    @Test fun roomInfoUpdatesNpcs() {
        val s = state()
        val npcs = listOf(npc("npc:rat", "Rat"))
        s.update(ServerMessage.RoomInfo(room(), emptyList(), npcs))
        assertEquals(1, s.roomNpcs.size)
        assertEquals("npc:rat", s.roomNpcs[0].id)
    }

    @Test fun roomInfoUpdatesPlayers() {
        val s = state()
        val players = listOf(player("Bob"))
        s.update(ServerMessage.RoomInfo(room(), players, emptyList()))
        assertEquals(1, s.roomPlayers.size)
        assertEquals("Bob", s.roomPlayers[0].name)
    }

    @Test fun roomInfoFiltersOnEnterInteractables() {
        val s = state()
        val interactables = listOf(
            RoomInteractable(id = "feat:lever", label = "Lever", description = "", actionType = "EXIT_OPEN", triggerType = "ON_ACTION"),
            RoomInteractable(id = "feat:trap", label = "Trap", description = "", actionType = "DAMAGE_TRAP", triggerType = "ON_ENTER"),
        )
        s.update(ServerMessage.RoomInfo(room(interactables = interactables), emptyList(), emptyList()))
        assertEquals(1, s.roomInteractables.size)
        assertEquals("feat:lever", s.roomInteractables[0].id)
    }

    @Test fun roomInfoClearsGroundItems() {
        val s = state()
        s.roomGroundItems = listOf(GroundItem("item:sword", 1))
        s.update(ServerMessage.RoomInfo(room(), emptyList(), emptyList()))
        assertTrue(s.roomGroundItems.isEmpty())
    }

    // ---- MoveOk ----

    @Test fun moveOkUpdatesRoomId() {
        val s = state()
        s.update(ServerMessage.MoveOk(Direction.NORTH, room("z:north"), emptyList(), emptyList()))
        assertEquals("z:north", s.currentRoomId)
    }

    @Test fun moveOkFiltersOnEnterInteractables() {
        val s = state()
        val interactables = listOf(
            RoomInteractable(id = "feat:door", label = "Door", description = "", actionType = "EXIT_OPEN", triggerType = "ON_ACTION"),
            RoomInteractable(id = "feat:pitfall", label = "Pit", description = "", actionType = "DAMAGE_TRAP", triggerType = "ON_ENTER"),
        )
        s.update(ServerMessage.MoveOk(Direction.SOUTH, room(interactables = interactables), emptyList(), emptyList()))
        assertEquals(1, s.roomInteractables.size)
        assertEquals("feat:door", s.roomInteractables[0].id)
    }

    // ---- RoomItemsUpdate ----

    @Test fun roomItemsUpdateSetsItems() {
        val s = state()
        val items = listOf(GroundItem("item:sword", 2))
        s.update(ServerMessage.RoomItemsUpdate(items, Coins(copper = 5)))
        assertEquals(1, s.roomGroundItems.size)
        assertEquals("item:sword", s.roomGroundItems[0].itemId)
    }

    @Test fun roomItemsUpdateSetsCoins() {
        val s = state()
        s.update(ServerMessage.RoomItemsUpdate(emptyList(), Coins(silver = 3)))
        assertEquals(3, s.roomGroundCoins.silver)
    }

    // ---- InventoryUpdate ----

    @Test fun inventoryUpdateSetsInventory() {
        val s = state()
        val inv = listOf(InventoryItem("item:sword"), InventoryItem("item:potion"))
        s.update(ServerMessage.InventoryUpdate(inv, emptyMap(), Coins()))
        assertEquals(2, s.inventory.size)
    }

    // ---- CraftingMenu ----

    @Test fun craftingMenuUpdatesCraftingRecipes() {
        val s = state()
        val recipe = RecipeInfo(
            recipe = Recipe("r:sword", "Iron Sword", "", "smithing", emptyList(), Coins(), "item:sword"),
            canCraft = true, materialStatus = emptyList()
        )
        s.update(ServerMessage.CraftingMenu("Smith", listOf(recipe), Coins()))
        assertEquals(1, s.craftingRecipes.size)
        assertEquals("r:sword", s.craftingRecipes[0].recipe.id)
    }

    // ---- ItemCatalogSync ----

    @Test fun itemCatalogSyncBuildsMap() {
        val s = state()
        val items = listOf(
            Item("item:sword", "Iron Sword", "", "weapon", "weapon"),
            Item("item:potion", "Health Potion", "", "consumable", "none"),
        )
        s.update(ServerMessage.ItemCatalogSync(items))
        assertEquals(2, s.itemCatalog.size)
        assertEquals("Iron Sword", s.itemCatalog["item:sword"]?.name)
    }

    // ---- Flags ----

    @Test fun attackModeUpdateSetsFlag() {
        val s = state()
        s.update(ServerMessage.AttackModeUpdate(true))
        assertTrue(s.inAttackMode)
        s.update(ServerMessage.AttackModeUpdate(false))
        assertTrue(!s.inAttackMode)
    }

    @Test fun stealthUpdateSetsHidden() {
        val s = state()
        s.update(ServerMessage.StealthUpdate(true))
        assertTrue(s.isHidden)
        s.update(ServerMessage.StealthUpdate(false))
        assertTrue(!s.isHidden)
    }

    @Test fun meditateUpdateSetsMeditating() {
        val s = state()
        s.update(ServerMessage.MeditateUpdate(true))
        assertTrue(s.isMeditating)
    }

    @Test fun restUpdateSetsResting() {
        val s = state()
        s.update(ServerMessage.RestUpdate(true))
        assertTrue(s.isResting)
    }

    // ---- Effects / Map / Atlas / Party ----

    @Test fun activeEffectsUpdateCachesEffects() {
        val s = state()
        val effects = listOf(ActiveEffect("Poison", EffectType.POISON, 3, 5))
        s.update(ServerMessage.ActiveEffectsUpdate(effects))
        assertEquals(1, s.activeEffects.size)
        assertEquals("Poison", s.activeEffects[0].name)
    }

    @Test fun mapDataCached() {
        val s = state()
        val mapMsg = ServerMessage.MapData(emptyList(), "z:r")
        s.update(mapMsg)
        assertEquals(mapMsg, s.mapData)
    }

    @Test fun atlasDataCached() {
        val s = state()
        val atlasMsg = ServerMessage.AtlasData(emptyList(), "z:r")
        s.update(atlasMsg)
        assertEquals(atlasMsg, s.atlasData)
    }

    @Test fun serverHelloSetsWorldName() {
        val s = state()
        s.update(ServerMessage.ServerHello("1.0", 1, worldName = "NeoMud"))
        assertEquals("NeoMud", s.worldName)
    }

    // ---- Modal state ----

    @Test fun riddlePromptSetsRiddleActive() {
        val s = state()
        s.update(ServerMessage.RiddlePrompt("feat:sphinx", "Sphinx", "What am I?"))
        assertTrue(s.modalState is ModalState.RiddleActive)
        assertEquals("feat:sphinx", (s.modalState as ModalState.RiddleActive).featureId)
    }

    @Test fun choicePromptSetsChoiceActive() {
        val s = state()
        val opts = listOf(ServerMessage.ChoiceOption("opt:a", "Option A"))
        s.update(ServerMessage.ChoicePrompt("feat:dial", "Dialog", "Choose:", opts))
        assertTrue(s.modalState is ModalState.ChoiceActive)
        assertEquals("feat:dial", (s.modalState as ModalState.ChoiceActive).featureId)
    }

    @Test fun choiceActiveStoresOptions() {
        val s = state()
        val opts = listOf(
            ServerMessage.ChoiceOption("opt:a", "Option A"),
            ServerMessage.ChoiceOption("opt:b", "Option B"),
        )
        s.update(ServerMessage.ChoicePrompt("feat:dial", "Dialog", "Choose:", opts))
        assertEquals(2, (s.modalState as ModalState.ChoiceActive).options.size)
    }

    @Test fun placeItemPromptSetsPlaceItemActive() {
        val s = state()
        s.update(ServerMessage.PlaceItemPrompt("feat:altar", "Altar", "Place an offering:", emptyList()))
        assertTrue(s.modalState is ModalState.PlaceItemActive)
        assertEquals("feat:altar", (s.modalState as ModalState.PlaceItemActive).featureId)
    }

    @Test fun interactResultClearsModal() {
        val s = state()
        s.modalState = ModalState.RiddleActive("feat:sphinx")
        s.update(ServerMessage.InteractResult(true, "Sphinx", "Correct!"))
        assertEquals(ModalState.None, s.modalState)
    }

    // ---- CombatHit ----

    @Test fun combatHitUpdatesHpForPlayerDefender() {
        val s = state()
        s.playerName = "Aldric"
        s.currentHp = 60
        s.update(ServerMessage.CombatHit(
            attackerName = "Rat", defenderName = "Aldric",
            damage = 10, defenderHp = 50, defenderMaxHp = 80,
            isPlayerDefender = true
        ))
        assertEquals(50, s.currentHp)
    }

    @Test fun combatHitIgnoresHitOnOtherPlayer() {
        val s = state()
        s.playerName = "Aldric"
        s.currentHp = 60
        s.update(ServerMessage.CombatHit(
            attackerName = "Rat", defenderName = "Bob",
            damage = 10, defenderHp = 30, defenderMaxHp = 50,
            isPlayerDefender = true
        ))
        assertEquals(60, s.currentHp)  // unchanged — not our player
    }

    @Test fun combatHitIgnoresNpcDefender() {
        val s = state()
        s.playerName = "Aldric"
        s.currentHp = 60
        s.update(ServerMessage.CombatHit(
            attackerName = "Aldric", defenderName = "Rat",
            damage = 15, defenderHp = 20, defenderMaxHp = 30,
            isPlayerDefender = false
        ))
        assertEquals(60, s.currentHp)  // unchanged — NPC took damage, not player
    }

    // ---- LevelUp ----

    @Test fun levelUpUpdatesLevel() {
        val s = state()
        s.playerLevel = 4
        s.update(ServerMessage.LevelUp(5, 10, 90, 5, 35, 1, 0))
        assertEquals(5, s.playerLevel)
    }

    @Test fun levelUpUpdatesMaxHpMp() {
        val s = state()
        s.update(ServerMessage.LevelUp(5, 10, 90, 5, 35, 1, 0))
        assertEquals(90, s.maxHp)
        assertEquals(35, s.maxMp)
    }

    // ---- Room presence ----

    @Test fun playerEnteredAddsToRoomPlayers() {
        val s = state()
        val info = player("Bob")
        s.update(ServerMessage.PlayerEntered("Bob", "z:r", info))
        assertEquals(1, s.roomPlayers.size)
        assertEquals("Bob", s.roomPlayers[0].name)
    }

    @Test fun playerEnteredSkipsIfNoInfo() {
        val s = state()
        s.update(ServerMessage.PlayerEntered("Bob", "z:r", playerInfo = null))
        assertTrue(s.roomPlayers.isEmpty())
    }

    @Test fun playerLeftRemovesFromRoomPlayers() {
        val s = state()
        s.roomPlayers = listOf(player("Bob"), player("Alice"))
        s.update(ServerMessage.PlayerLeft("Bob", "z:r", Direction.NORTH))
        assertEquals(1, s.roomPlayers.size)
        assertEquals("Alice", s.roomPlayers[0].name)
    }

    @Test fun npcLeftRemovesFromRoomNpcs() {
        val s = state()
        s.roomNpcs = listOf(npc("npc:rat1", "Rat"), npc("npc:rat2", "Rat"))
        s.update(ServerMessage.NpcLeft("Rat", "z:r", Direction.NORTH, npcId = "npc:rat1"))
        assertEquals(1, s.roomNpcs.size)
        assertEquals("npc:rat2", s.roomNpcs[0].id)
    }

    @Test fun npcDiedRemovesFromRoomNpcs() {
        val s = state()
        s.roomNpcs = listOf(npc("npc:rat", "Rat"))
        s.update(ServerMessage.NpcDied("npc:rat", "Rat", "Aldric", "z:r"))
        assertTrue(s.roomNpcs.isEmpty())
    }

    // ---- Unrelated messages don't crash ----

    @Test fun unknownMessageIsNoop() {
        val s = state()
        s.currentHp = 50
        s.update(ServerMessage.Pong)
        assertEquals(50, s.currentHp)
    }
}
