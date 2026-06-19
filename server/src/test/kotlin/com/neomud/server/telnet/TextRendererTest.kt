package com.neomud.server.telnet

import com.neomud.shared.model.*
import com.neomud.shared.protocol.ServerMessage
import kotlin.test.*

class TextRendererTest {

    private val renderer = TextRenderer()
    private val RESET = "[0m"
    private val ESC   = "["

    private fun state() = TelnetSessionState(
        playerName = "Aldric",
        playerClass = "WARRIOR",
        playerLevel = 5,
        currentHp = 60, maxHp = 100,
        currentMp = 20, maxMp = 50
    )

    private fun emptyState() = TelnetSessionState()

    private fun player(name: String = "TestPlayer", level: Int = 5, characterClass: String = "WARRIOR") = Player(
        name = name, characterClass = characterClass, race = "HUMAN",
        stats = Stats(), currentHp = 60, maxHp = 100, level = level, currentRoomId = "test:room"
    )

    private fun npc(name: String = "Rat", hostile: Boolean = false, hp: Int = 30, maxHp: Int = 30) = Npc(
        id = "npc:rat", name = name, description = "", currentRoomId = "test:room",
        behaviorType = "HOSTILE", hostile = hostile, currentHp = hp, maxHp = maxHp
    )

    private fun playerInfo(name: String = "Bob", level: Int = 3) = PlayerInfo(
        name = name, characterClass = "MAGE", race = "HUMAN", level = level
    )

    private fun room(name: String = "Town Square", exits: Map<Direction, String> = mapOf(Direction.NORTH to "r2"),
                     interactables: List<RoomInteractable> = emptyList(),
                     lockedExits: Map<Direction, Int> = emptyMap()) = Room(
        id = "test:room", name = name, description = "A busy crossroads.",
        exits = exits, zoneId = "test", x = 0, y = 0,
        lockedExits = lockedExits, interactables = interactables
    )

    private fun partyMember(name: String, hp: Int = 60, maxHp: Int = 100, isLeader: Boolean = false) = PartyMember(
        name = name, characterClass = "WARRIOR", level = 5, currentHp = hp, maxHp = maxHp, isLeader = isLeader
    )

    // ────────────────────────────────────────────────────────────────────────
    // No-throw coverage — one test per ServerMessage type (80 types)
    // ────────────────────────────────────────────────────────────────────────

    private fun noThrow(msg: ServerMessage) {
        assertNotNull(renderer.render(msg, emptyState(), false))
        assertNotNull(renderer.render(msg, emptyState(), true))
    }

    @Test fun `no-throw active_effects_update`() = noThrow(ServerMessage.ActiveEffectsUpdate(emptyList()))
    @Test fun `no-throw atlas_data`() = noThrow(ServerMessage.AtlasData(emptyList(), "r1"))
    @Test fun `no-throw attack_mode_update`() = noThrow(ServerMessage.AttackModeUpdate(true))
    @Test fun `no-throw auth_error`() = noThrow(ServerMessage.AuthError("bad password"))
    @Test fun `no-throw buy_result`() = noThrow(ServerMessage.BuyResult(true, "Bought!", Coins(), emptyList(), emptyMap()))
    @Test fun `no-throw choice_prompt`() = noThrow(ServerMessage.ChoicePrompt("f1", "Riddle", "Choose:", listOf(ServerMessage.ChoiceOption("a", "Option A"))))
    @Test fun `no-throw class_catalog_sync`() = noThrow(ServerMessage.ClassCatalogSync(emptyList()))
    @Test fun `no-throw combat_hit`() = noThrow(ServerMessage.CombatHit("A", "B", 10, 40, 100))
    @Test fun `no-throw connection_rejected`() = noThrow(ServerMessage.ConnectionRejected("too old"))
    @Test fun `no-throw craft_result`() = noThrow(ServerMessage.CraftResult(true, "Sword", "Crafted!", Coins(), emptyList(), emptyMap()))
    @Test fun `no-throw crafting_menu`() = noThrow(ServerMessage.CraftingMenu("Smith", emptyList(), Coins()))
    @Test fun `no-throw effect_tick`() = noThrow(ServerMessage.EffectTick("Poison", "You feel sick.", 40))
    @Test fun `no-throw equip_update`() = noThrow(ServerMessage.EquipUpdate("MAIN_HAND", "sword", "Iron Sword"))
    @Test fun `no-throw error`() = noThrow(ServerMessage.Error("oops"))
    @Test fun `no-throw follow_failed`() = noThrow(ServerMessage.FollowFailed("Aldric", "not in same room"))
    @Test fun `no-throw follow_update`() = noThrow(ServerMessage.FollowUpdate("Aldric", "Bob", FollowState.ACTIVE))
    @Test fun `no-throw interact_result`() = noThrow(ServerMessage.InteractResult(true, "Lever", "Clicked!"))
    @Test fun `no-throw inventory_update`() = noThrow(ServerMessage.InventoryUpdate(emptyList(), emptyMap()))
    @Test fun `no-throw item_catalog_sync`() = noThrow(ServerMessage.ItemCatalogSync(emptyList()))
    @Test fun `no-throw item_used`() = noThrow(ServerMessage.ItemUsed("Health Potion", "You feel better.", 80, 20))
    @Test fun `no-throw level_up`() = noThrow(ServerMessage.LevelUp(6, 5, 105, 3, 53, 2, 4))
    @Test fun `no-throw login_ok`() = noThrow(ServerMessage.LoginOk(player()))
    @Test fun `no-throw loot_dropped`() = noThrow(ServerMessage.LootDropped("Rat", emptyList(), Coins()))
    @Test fun `no-throw loot_received`() = noThrow(ServerMessage.LootReceived("Rat", emptyList()))
    @Test fun `no-throw map_data`() = noThrow(ServerMessage.MapData(emptyList(), "r1"))
    @Test fun `no-throw meditate_update`() = noThrow(ServerMessage.MeditateUpdate(true, "You meditate."))
    @Test fun `no-throw move_error`() = noThrow(ServerMessage.MoveError("Can't go that way."))
    @Test fun `no-throw move_ok`() = noThrow(ServerMessage.MoveOk(Direction.NORTH, room(), emptyList(), emptyList()))
    @Test fun `no-throw name_check_result`() = noThrow(ServerMessage.NameCheckResult(characterNameAvailable = true))
    @Test fun `no-throw npc_ability_effect`() = noThrow(ServerMessage.NpcAbilityEffect("Boss", "npc:boss", "Slam", "Slams!", emptyList()))
    @Test fun `no-throw npc_dialogue`() = noThrow(ServerMessage.NpcDialogue("npc:1", "Old Wren", "Greetings."))
    @Test fun `no-throw npc_died`() = noThrow(ServerMessage.NpcDied("npc:rat", "Rat", "Aldric", "test:room"))
    @Test fun `no-throw npc_entered`() = noThrow(ServerMessage.NpcEntered("Rat", "test:room"))
    @Test fun `no-throw npc_left`() = noThrow(ServerMessage.NpcLeft("Rat", "test:room", Direction.NORTH))
    @Test fun `no-throw npc_phase_shift`() = noThrow(ServerMessage.NpcPhaseShift("npc:boss", "Boss", "Enraged", "", "Glows!", 50, 100))
    @Test fun `no-throw party_chat`() = noThrow(ServerMessage.PartyChatMessage("Bob", "Ready?"))
    @Test fun `no-throw party_disbanded`() = noThrow(ServerMessage.PartyDisbanded("all left"))
    @Test fun `no-throw party_formed`() = noThrow(ServerMessage.PartyFormed("p1", listOf(partyMember("Aldric")), "Aldric"))
    @Test fun `no-throw party_info`() = noThrow(ServerMessage.PartyInfo("p1", listOf(partyMember("Aldric", isLeader = true)), "Aldric"))
    @Test fun `no-throw party_invite_received`() = noThrow(ServerMessage.PartyInviteReceived("Bob", 2))
    @Test fun `no-throw party_leader_changed`() = noThrow(ServerMessage.PartyLeaderChanged("Bob", "Bob"))
    @Test fun `no-throw party_member_joined`() = noThrow(ServerMessage.PartyMemberJoined(partyMember("Bob")))
    @Test fun `no-throw party_member_left`() = noThrow(ServerMessage.PartyMemberLeft("Bob", "left"))
    @Test fun `no-throw party_member_update`() = noThrow(ServerMessage.PartyMemberUpdate("Bob", 60, 100))
    @Test fun `no-throw pickup_result`() = noThrow(ServerMessage.PickupResult("Sword", 1))
    @Test fun `no-throw place_item_prompt`() = noThrow(ServerMessage.PlaceItemPrompt("f1", "Altar", "Place an item.", emptyList()))
    @Test fun `no-throw platform_auth_ok`() = noThrow(ServerMessage.PlatformAuthOk(characterName = "Aldric", platformUserId = "u1", needsCharacterCreation = false))
    @Test fun `no-throw player_died`() = noThrow(ServerMessage.PlayerDied("Rat", "test:spawn", 50))
    @Test fun `no-throw player_entered`() = noThrow(ServerMessage.PlayerEntered("Bob", "test:room"))
    @Test fun `no-throw player_left`() = noThrow(ServerMessage.PlayerLeft("Bob", "test:room", Direction.SOUTH))
    @Test fun `no-throw player_says`() = noThrow(ServerMessage.PlayerSays("Bob", "Hello!"))
    @Test fun `no-throw pong`() = noThrow(ServerMessage.Pong)
    @Test fun `no-throw race_catalog_sync`() = noThrow(ServerMessage.RaceCatalogSync(emptyList()))
    @Test fun `no-throw rally_ping`() = noThrow(ServerMessage.RallyPing("Bob", "test:room", "Town Square", "Millhaven"))
    @Test fun `no-throw register_ok`() = noThrow(ServerMessage.RegisterOk)
    @Test fun `no-throw rest_update`() = noThrow(ServerMessage.RestUpdate(true, "You rest."))
    @Test fun `no-throw riddle_prompt`() = noThrow(ServerMessage.RiddlePrompt("f1", "Sphinx", "What am I?"))
    @Test fun `no-throw room_info`() = noThrow(ServerMessage.RoomInfo(room(), emptyList(), emptyList()))
    @Test fun `no-throw room_items_update`() = noThrow(ServerMessage.RoomItemsUpdate(emptyList(), Coins()))
    @Test fun `no-throw sell_result`() = noThrow(ServerMessage.SellResult(true, "Sold!", Coins(), emptyList(), emptyMap()))
    @Test fun `no-throw server_hello`() = noThrow(ServerMessage.ServerHello("1.0", 1))
    @Test fun `no-throw server_shutdown`() = noThrow(ServerMessage.ServerShutdown("Maintenance", 30))
    @Test fun `no-throw session_conflict`() = noThrow(ServerMessage.SessionConflict("Aldric"))
    @Test fun `no-throw session_displaced`() = noThrow(ServerMessage.SessionDisplaced("forced logout"))
    @Test fun `no-throw skill_catalog_sync`() = noThrow(ServerMessage.SkillCatalogSync(emptyList()))
    @Test fun `no-throw skill_effect`() = noThrow(ServerMessage.SkillEffect("Aldric", "Bash", "Rat", "npc:rat", 15, 45, 60, "Crunching!"))
    @Test fun `no-throw spell_cast_result`() = noThrow(ServerMessage.SpellCastResult(true, "Fireball", "Boom!", 30))
    @Test fun `no-throw spell_catalog_sync`() = noThrow(ServerMessage.SpellCatalogSync(emptyList()))
    @Test fun `no-throw spell_effect`() = noThrow(ServerMessage.SpellEffect("Aldric", "Rat", "Fireball", 25, 35, 60))
    @Test fun `no-throw stat_trained`() = noThrow(ServerMessage.StatTrained("strength", 16, 1, 3))
    @Test fun `no-throw stealth_update`() = noThrow(ServerMessage.StealthUpdate(true, "You hide."))
    @Test fun `no-throw system_message`() = noThrow(ServerMessage.SystemMessage("The wind howls."))
    @Test fun `no-throw tell_received`() = noThrow(ServerMessage.TellReceived("Bob", "Hey there!"))
    @Test fun `no-throw tell_sent`() = noThrow(ServerMessage.TellSent("Bob", "Hey!"))
    @Test fun `no-throw track_result`() = noThrow(ServerMessage.TrackResult(true, Direction.NORTH, "Rat", "Tracks lead north."))
    @Test fun `no-throw trainer_info`() = noThrow(ServerMessage.TrainerInfo(true, 3, 10, Stats(), Stats()))
    @Test fun `no-throw tutorial`() = noThrow(ServerMessage.Tutorial("first_move", "Movement", "Use WASD.", true))
    @Test fun `no-throw vendor_info`() = noThrow(ServerMessage.VendorInfo("Smith", emptyList(), Coins(), emptyList()))
    @Test fun `no-throw who_list`() = noThrow(ServerMessage.WhoList(emptyList()))
    @Test fun `no-throw xp_gained`() = noThrow(ServerMessage.XpGained(50L, 150L, 1000L))

    // ────────────────────────────────────────────────────────────────────────
    // Silent messages — must return empty list
    // ────────────────────────────────────────────────────────────────────────

    @Test fun `server_hello is silent`() = assertTrue(renderer.render(ServerMessage.ServerHello("1.0", 1), emptyState()).isEmpty())
    @Test fun `pong is silent`() = assertTrue(renderer.render(ServerMessage.Pong, emptyState()).isEmpty())
    @Test fun `name_check_result is silent`() = assertTrue(renderer.render(ServerMessage.NameCheckResult(characterNameAvailable = true), emptyState()).isEmpty())
    @Test fun `class_catalog_sync is silent`() = assertTrue(renderer.render(ServerMessage.ClassCatalogSync(emptyList()), emptyState()).isEmpty())
    @Test fun `item_catalog_sync is silent`() = assertTrue(renderer.render(ServerMessage.ItemCatalogSync(emptyList()), emptyState()).isEmpty())
    @Test fun `skill_catalog_sync is silent`() = assertTrue(renderer.render(ServerMessage.SkillCatalogSync(emptyList()), emptyState()).isEmpty())
    @Test fun `race_catalog_sync is silent`() = assertTrue(renderer.render(ServerMessage.RaceCatalogSync(emptyList()), emptyState()).isEmpty())
    @Test fun `spell_catalog_sync is silent`() = assertTrue(renderer.render(ServerMessage.SpellCatalogSync(emptyList()), emptyState()).isEmpty())
    @Test fun `active_effects_update is silent`() = assertTrue(renderer.render(ServerMessage.ActiveEffectsUpdate(emptyList()), emptyState()).isEmpty())
    @Test fun `party_member_update is silent`() = assertTrue(renderer.render(ServerMessage.PartyMemberUpdate("Bob", 60, 100), emptyState()).isEmpty())
    @Test fun `tutorial is silent`() = assertTrue(renderer.render(ServerMessage.Tutorial("k", "T", "C"), emptyState()).isEmpty())
    @Test fun `room_items_update is silent`() = assertTrue(renderer.render(ServerMessage.RoomItemsUpdate(emptyList(), Coins()), emptyState()).isEmpty())

    // ────────────────────────────────────────────────────────────────────────
    // Room display
    // ────────────────────────────────────────────────────────────────────────

    @Test fun `room_info contains room name`() {
        val lines = renderer.render(ServerMessage.RoomInfo(room("The Dungeon"), emptyList(), emptyList()), emptyState())
        assertTrue(lines.any { "The Dungeon" in it })
    }

    @Test fun `room_info contains description`() {
        val lines = renderer.render(ServerMessage.RoomInfo(room(), emptyList(), emptyList()), emptyState())
        assertTrue(lines.any { "A busy crossroads" in it })
    }

    @Test fun `room_info lists exits`() {
        val r = room(exits = mapOf(Direction.NORTH to "r2", Direction.EAST to "r3"))
        val lines = renderer.render(ServerMessage.RoomInfo(r, emptyList(), emptyList()), emptyState())
        val exitLine = lines.first { "Exits" in it }
        assertTrue("north" in exitLine)
        assertTrue("east" in exitLine)
    }

    @Test fun `locked exits show locked annotation`() {
        val r = room(exits = mapOf(Direction.EAST to "r3"), lockedExits = mapOf(Direction.EAST to 10))
        val lines = renderer.render(ServerMessage.RoomInfo(r, emptyList(), emptyList()), emptyState())
        assertTrue(lines.any { "east(locked)" in it })
    }

    @Test fun `room_info lists NPCs from message`() {
        val lines = renderer.render(ServerMessage.RoomInfo(room(), emptyList(), listOf(npc("Tunnel Rat"))), emptyState())
        assertTrue(lines.any { "Tunnel Rat" in it })
    }

    @Test fun `hostile NPCs prefixed with asterisk`() {
        val lines = renderer.render(ServerMessage.RoomInfo(room(), emptyList(), listOf(npc("Boss", hostile = true))), emptyState())
        assertTrue(lines.any { "*Boss" in it })
    }

    @Test fun `room_info shows ground items from state`() {
        val s = emptyState().copy(
            roomGroundItems = listOf(GroundItem("item:sword", 1)),
            itemCatalog = mapOf("item:sword" to Item("item:sword", "Iron Sword", "", "weapon"))
        )
        val lines = renderer.render(ServerMessage.RoomInfo(room(), emptyList(), emptyList()), s)
        assertTrue(lines.any { "Iron Sword" in it })
    }

    @Test fun `room_info shows ground coins from state`() {
        val s = emptyState().copy(roomGroundCoins = Coins(silver = 5))
        val lines = renderer.render(ServerMessage.RoomInfo(room(), emptyList(), emptyList()), s)
        assertTrue(lines.any { "5s" in it })
    }

    @Test fun `ON_ACTION interactables shown, ON_ENTER hidden`() {
        val feats = listOf(
            RoomInteractable("f1", "Altar", "", actionType = "ROOM_EFFECT", triggerType = "ON_ACTION"),
            RoomInteractable("f2", "Trap",  "", actionType = "DAMAGE_TRAP",  triggerType = "ON_ENTER")
        )
        val lines = renderer.render(ServerMessage.RoomInfo(room(interactables = feats), emptyList(), emptyList()), emptyState())
        assertTrue(lines.any { "Altar" in it }, "ON_ACTION should be visible")
        assertFalse(lines.any { "Trap" in it }, "ON_ENTER should be hidden")
    }

    @Test fun `move_ok shows direction and room name`() {
        val lines = renderer.render(ServerMessage.MoveOk(Direction.NORTH, room("Cave"), emptyList(), emptyList()), emptyState())
        assertTrue(lines.any { "north" in it.lowercase() })
        assertTrue(lines.any { "Cave" in it })
    }

    // ────────────────────────────────────────────────────────────────────────
    // Combat
    // ────────────────────────────────────────────────────────────────────────

    @Test fun `combat hit shows attacker, defender, damage`() {
        val lines = renderer.render(ServerMessage.CombatHit("Aldric", "Rat", 12, 20, 60), emptyState())
        val line = lines.first()
        assertTrue("Aldric" in line && "Rat" in line && "12" in line)
    }

    @Test fun `combat miss shows miss line`() {
        val lines = renderer.render(ServerMessage.CombatHit("Aldric", "Rat", 0, 60, 60, isMiss = true), emptyState())
        assertTrue(lines.first().lowercase().contains("miss"))
    }

    @Test fun `combat dodge shows dodge line`() {
        val lines = renderer.render(ServerMessage.CombatHit("Rat", "Aldric", 0, 60, 100, isDodge = true), emptyState())
        assertTrue(lines.first().lowercase().contains("dodge"))
    }

    @Test fun `combat parry shows parry line`() {
        val lines = renderer.render(ServerMessage.CombatHit("Rat", "Aldric", 0, 60, 100, isParry = true), emptyState())
        assertTrue(lines.first().lowercase().contains("parr"))
    }

    @Test fun `backstab noted in hit line`() {
        val lines = renderer.render(ServerMessage.CombatHit("Aldric", "Rat", 30, 30, 60, isBackstab = true), emptyState())
        assertTrue(lines.first().contains("backstab"))
    }

    @Test fun `npc_died shows killer and victim`() {
        val lines = renderer.render(ServerMessage.NpcDied("npc:rat", "Rat", "Aldric", "test:room"), emptyState())
        assertTrue(lines.first().let { "Aldric" in it && "Rat" in it })
    }

    @Test fun `player_died shows killer`() {
        val lines = renderer.render(ServerMessage.PlayerDied("Rat", "test:spawn", 50), emptyState())
        assertTrue("Rat" in lines.first())
    }

    @Test fun `attack_mode ON shows red marker`() {
        val lines = renderer.render(ServerMessage.AttackModeUpdate(true), emptyState())
        assertTrue("ATTACK MODE ON" in lines.first())
    }

    @Test fun `attack_mode OFF shows green marker`() {
        val lines = renderer.render(ServerMessage.AttackModeUpdate(false), emptyState())
        assertTrue("ATTACK MODE OFF" in lines.first())
    }

    @Test fun `npc_phase_shift shows uppercase phase name`() {
        val lines = renderer.render(ServerMessage.NpcPhaseShift("npc:boss", "Boss", "Enraged", "", "Glows!", 50, 100), emptyState())
        assertTrue(lines.any { "ENRAGED" in it })
    }

    // ────────────────────────────────────────────────────────────────────────
    // HP bar thresholds
    // ────────────────────────────────────────────────────────────────────────

    @Test fun `hp bar over 50 percent is green`() {
        val bar = Ansi.hpBar(60, 100, 10, true)
        assertTrue(bar.contains(Ansi.GREEN))
    }

    @Test fun `hp bar exactly 50 percent is yellow`() {
        val bar = Ansi.hpBar(50, 100, 10, true)
        assertTrue(bar.contains(Ansi.YELLOW))
    }

    @Test fun `hp bar 25-50 percent is yellow`() {
        val bar = Ansi.hpBar(30, 100, 10, true)
        assertTrue(bar.contains(Ansi.YELLOW))
    }

    @Test fun `hp bar exactly 25 percent is red`() {
        val bar = Ansi.hpBar(25, 100, 10, true)
        assertTrue(bar.contains(Ansi.RED))
    }

    @Test fun `hp bar under 25 percent is red`() {
        val bar = Ansi.hpBar(10, 100, 10, true)
        assertTrue(bar.contains(Ansi.RED))
    }

    @Test fun `hp bar at 0 is all empty and red`() {
        val bar = Ansi.hpBar(0, 100, 4, true)
        assertTrue(bar.contains("░░░░"))
        assertTrue(bar.contains(Ansi.RED))
    }

    // ────────────────────────────────────────────────────────────────────────
    // ANSI color compliance — every colored line must end with RESET
    // ────────────────────────────────────────────────────────────────────────

    private fun assertColorCompliant(msg: ServerMessage) {
        val lines = renderer.render(msg, emptyState(), true)
        val colored = lines.filter { it.contains(ESC) }
        colored.forEach { line ->
            assertTrue(line.endsWith(RESET), "Line does not end with RESET: $line")
        }
    }

    @Test fun `login_ok color compliant`()         = assertColorCompliant(ServerMessage.LoginOk(player()))
    @Test fun `auth_error color compliant`()        = assertColorCompliant(ServerMessage.AuthError("bad"))
    @Test fun `system_message color compliant`()    = assertColorCompliant(ServerMessage.SystemMessage("Hello."))
    @Test fun `error color compliant`()             = assertColorCompliant(ServerMessage.Error("oops"))
    @Test fun `server_shutdown color compliant`()   = assertColorCompliant(ServerMessage.ServerShutdown("Maintenance", 60))
    @Test fun `player_says color compliant`()       = assertColorCompliant(ServerMessage.PlayerSays("Bob", "Hi"))
    @Test fun `tell_received color compliant`()     = assertColorCompliant(ServerMessage.TellReceived("Bob", "Hi"))
    @Test fun `npc_died color compliant`()          = assertColorCompliant(ServerMessage.NpcDied("npc:rat", "Rat", "Aldric", "r1"))
    @Test fun `player_died color compliant`()       = assertColorCompliant(ServerMessage.PlayerDied("Rat", "r1", 50))
    @Test fun `xp_gained color compliant`()         = assertColorCompliant(ServerMessage.XpGained(50L, 150L, 1000L))
    @Test fun `level_up color compliant`()          = assertColorCompliant(ServerMessage.LevelUp(6, 5, 105, 3, 53, 2, 4))
    @Test fun `loot_received color compliant`()     = assertColorCompliant(ServerMessage.LootReceived("Rat", listOf(LootedItem("item:sword", "Iron Sword", 1))))
    @Test fun `party_chat color compliant`()        = assertColorCompliant(ServerMessage.PartyChatMessage("Bob", "Go!"))
    @Test fun `follow_update color compliant`()     = assertColorCompliant(ServerMessage.FollowUpdate("Aldric", "Bob", FollowState.ACTIVE))
    @Test fun `session_displaced color compliant`() = assertColorCompliant(ServerMessage.SessionDisplaced("forced"))
    @Test fun `connection_rejected color compliant`() = assertColorCompliant(ServerMessage.ConnectionRejected("reason"))

    @Test fun `no ANSI codes when useColor is false`() {
        val lines = renderer.render(ServerMessage.LoginOk(player()), emptyState(), false)
        assertFalse(lines.any { it.contains(ESC) })
    }

    // ────────────────────────────────────────────────────────────────────────
    // Inventory
    // ────────────────────────────────────────────────────────────────────────

    @Test fun `empty inventory shows empty marker`() {
        val lines = renderer.render(ServerMessage.InventoryUpdate(emptyList(), emptyMap()), emptyState())
        assertTrue(lines.any { "(empty)" in it })
    }

    @Test fun `inventory shows item names from catalog`() {
        val s = emptyState().copy(itemCatalog = mapOf("item:sword" to Item("item:sword", "Iron Sword", "", "weapon")))
        val lines = renderer.render(ServerMessage.InventoryUpdate(listOf(InventoryItem("item:sword", 1)), emptyMap()), s)
        assertTrue(lines.any { "Iron Sword" in it })
    }

    @Test fun `stacked items show quantity`() {
        val s = emptyState().copy(itemCatalog = mapOf("item:potion" to Item("item:potion", "Health Potion", "", "consumable")))
        val lines = renderer.render(ServerMessage.InventoryUpdate(listOf(InventoryItem("item:potion", 3)), emptyMap()), s)
        assertTrue(lines.any { "x3" in it || "3" in it })
    }

    @Test fun `equipped items appear in equipped section`() {
        val s = emptyState().copy(itemCatalog = mapOf("item:sword" to Item("item:sword", "Iron Sword", "", "weapon")))
        val inv = listOf(InventoryItem("item:sword", 1, equipped = true, slot = "MAIN_HAND"))
        val lines = renderer.render(ServerMessage.InventoryUpdate(inv, mapOf("MAIN_HAND" to "item:sword")), s)
        assertTrue(lines.any { "Equipped" in it || "MAIN_HAND" in it })
    }

    @Test fun `pickup singular`() {
        val lines = renderer.render(ServerMessage.PickupResult("Iron Sword", 1), emptyState())
        assertTrue("Iron Sword" in lines.first())
    }

    @Test fun `pickup plural shows quantity`() {
        val lines = renderer.render(ServerMessage.PickupResult("Health Potion", 3), emptyState())
        assertTrue("3" in lines.first() && "Health Potion" in lines.first())
    }

    @Test fun `loot_dropped with stacked items shows quantity`() {
        val looted = listOf(LootedItem("item:potion", "Health Potion", 3))
        val lines = renderer.render(ServerMessage.LootDropped("Rat", looted, Coins()), emptyState())
        assertTrue(lines.any { "3" in it && "Health Potion" in it })
    }

    // ────────────────────────────────────────────────────────────────────────
    // Progression
    // ────────────────────────────────────────────────────────────────────────

    @Test fun `xp_gained shows amount`() {
        val lines = renderer.render(ServerMessage.XpGained(50L, 150L, 1000L), emptyState())
        assertTrue(lines.any { "50" in it })
    }

    @Test fun `xp_gained zero returns empty`() {
        val lines = renderer.render(ServerMessage.XpGained(0L, 100L, 1000L), emptyState())
        assertTrue(lines.isEmpty())
    }

    @Test fun `level_up shows new level and bonuses`() {
        val lines = renderer.render(ServerMessage.LevelUp(6, 5, 105, 3, 53, 2, 4), emptyState())
        assertTrue(lines.any { "6" in it })
        assertTrue(lines.any { "CP" in it })
    }

    // ────────────────────────────────────────────────────────────────────────
    // Communication
    // ────────────────────────────────────────────────────────────────────────

    @Test fun `player_says includes name and message`() {
        val lines = renderer.render(ServerMessage.PlayerSays("Bob", "Hello!"), emptyState())
        assertTrue(lines.first().let { "Bob" in it && "Hello" in it })
    }

    @Test fun `tell_received includes sender and message`() {
        val lines = renderer.render(ServerMessage.TellReceived("Bob", "Hey"), emptyState())
        assertTrue(lines.first().let { "Bob" in it && "Hey" in it })
    }

    @Test fun `tell_sent includes target and message`() {
        val lines = renderer.render(ServerMessage.TellSent("Bob", "Hey"), emptyState())
        assertTrue(lines.first().let { "Bob" in it && "Hey" in it })
    }

    @Test fun `who_list empty shows no players message`() {
        val lines = renderer.render(ServerMessage.WhoList(emptyList()), emptyState())
        assertTrue(lines.any { "No players" in it || "online" in it.lowercase() })
    }

    @Test fun `who_list shows player names`() {
        val players = listOf(OnlinePlayer("Alice", 5, "MAGE", "Millhaven"), OnlinePlayer("Bob", 3, "WARRIOR", "Foothills"))
        val lines = renderer.render(ServerMessage.WhoList(players), emptyState())
        assertTrue(lines.any { "Alice" in it })
        assertTrue(lines.any { "Bob" in it })
    }

    // ────────────────────────────────────────────────────────────────────────
    // Interactive prompts
    // ────────────────────────────────────────────────────────────────────────

    @Test fun `riddle_prompt shows question and instructions`() {
        val lines = renderer.render(ServerMessage.RiddlePrompt("f1", "Sphinx", "What am I?"), emptyState())
        assertTrue(lines.any { "What am I" in it })
        assertTrue(lines.any { "answer" in it })
    }

    @Test fun `riddle_prompt without hint omits hint line`() {
        val lines = renderer.render(ServerMessage.RiddlePrompt("f1", "Sphinx", "What?", hint = null), emptyState())
        assertFalse(lines.any { "Hint" in it })
    }

    @Test fun `riddle_prompt with hint shows hint`() {
        val lines = renderer.render(ServerMessage.RiddlePrompt("f1", "Sphinx", "What?", hint = "It grows."), emptyState())
        assertTrue(lines.any { "Hint" in it && "grows" in it })
    }

    @Test fun `choice_prompt numbered options`() {
        val opts = listOf(ServerMessage.ChoiceOption("a", "Fight"), ServerMessage.ChoiceOption("b", "Flee"))
        val lines = renderer.render(ServerMessage.ChoicePrompt("f1", "Guard", "What do you do?", opts), emptyState())
        assertTrue(lines.any { "1." in it && "Fight" in it })
        assertTrue(lines.any { "2." in it && "Flee" in it })
        assertTrue(lines.any { "choose" in it })
    }

    @Test fun `place_item_prompt shows accepted items instruction`() {
        val lines = renderer.render(ServerMessage.PlaceItemPrompt("f1", "Altar", "Place an offering.", listOf("Diamond", "Ruby")), emptyState())
        assertTrue(lines.any { "place" in it.lowercase() })
    }

    // ────────────────────────────────────────────────────────────────────────
    // Party and follow
    // ────────────────────────────────────────────────────────────────────────

    @Test fun `party_invite includes inviter and instructions`() {
        val lines = renderer.render(ServerMessage.PartyInviteReceived("Bob", 2), emptyState())
        assertTrue(lines.first().let { "Bob" in it && "accept" in it })
    }

    @Test fun `party_chat shows Party prefix`() {
        val lines = renderer.render(ServerMessage.PartyChatMessage("Bob", "Ready?"), emptyState())
        assertTrue("[Party]" in lines.first())
    }

    @Test fun `follow_update ACTIVE shows following message`() {
        val lines = renderer.render(ServerMessage.FollowUpdate("Aldric", "Bob", FollowState.ACTIVE), emptyState())
        assertTrue(lines.first().let { "Aldric" in it && "following" in it })
    }

    @Test fun `follow_update OFF shows stops following`() {
        val lines = renderer.render(ServerMessage.FollowUpdate("Aldric", "Bob", FollowState.OFF), emptyState())
        assertTrue("stops" in lines.first())
    }

    @Test fun `rally_ping shows leader and destination`() {
        val lines = renderer.render(ServerMessage.RallyPing("Bob", "test:room", "Town Square", "Millhaven"), emptyState())
        assertTrue(lines.first().let { "Bob" in it && "Town Square" in it })
    }

    // ────────────────────────────────────────────────────────────────────────
    // Vendor and crafting
    // ────────────────────────────────────────────────────────────────────────

    @Test fun `vendor_info shows item list with prices`() {
        val item = Item("item:sword", "Iron Sword", "", "weapon")
        val vendorItems = listOf(VendorItem(item, Coins(silver = 5)))
        val lines = renderer.render(ServerMessage.VendorInfo("Blacksmith", vendorItems, Coins(gold = 1), emptyList()), emptyState())
        assertTrue(lines.any { "Iron Sword" in it })
        assertTrue(lines.any { "5s" in it })
    }

    @Test fun `buy_result success is green`() {
        val lines = renderer.render(ServerMessage.BuyResult(true, "Bought!", Coins(), emptyList(), emptyMap()), emptyState(), true)
        assertTrue(lines.any { it.contains(Ansi.GREEN) })
    }

    @Test fun `buy_result failure is red`() {
        val lines = renderer.render(ServerMessage.BuyResult(false, "Can't afford it.", Coins(), emptyList(), emptyMap()), emptyState(), true)
        assertTrue(lines.any { it.contains(Ansi.RED) })
    }

    @Test fun `crafting_menu shows recipe list`() {
        val mat = MaterialStatus("item:iron", "Iron Bar", 2, 3)
        val recipe = Recipe("r:sword", "Iron Sword", "", "weapon",
            listOf(RecipeIngredient("item:iron", 2)), Coins(silver = 1), "item:sword")
        val ri = RecipeInfo(recipe, true, listOf(mat))
        val lines = renderer.render(ServerMessage.CraftingMenu("Forge", listOf(ri), Coins(silver = 10)), emptyState())
        assertTrue(lines.any { "Iron Sword" in it })
        assertTrue(lines.any { "Iron Bar" in it })
    }

    // ────────────────────────────────────────────────────────────────────────
    // renderPrompt
    // ────────────────────────────────────────────────────────────────────────

    @Test fun `prompt with no player returns arrow`() {
        val p = renderer.renderPrompt(emptyState())
        assertTrue(p.trim().endsWith(">"))
    }

    @Test fun `prompt shows HP and MP`() {
        val p = renderer.renderPrompt(state())
        assertTrue("60/100" in p && "20/50" in p)
    }

    @Test fun `prompt omits MP when maxMp is zero`() {
        val s = state().copy(maxMp = 0, currentMp = 0)
        val p = renderer.renderPrompt(s)
        assertFalse("MP" in p)
    }

    @Test fun `prompt shows Attack flag`() {
        val p = renderer.renderPrompt(state().copy(inAttackMode = true))
        assertTrue("[Attack]" in p)
    }

    @Test fun `prompt shows Hidden flag`() {
        val p = renderer.renderPrompt(state().copy(isHidden = true))
        assertTrue("[Hidden]" in p)
    }

    @Test fun `prompt shows Med flag`() {
        val p = renderer.renderPrompt(state().copy(isMeditating = true))
        assertTrue("[Med]" in p)
    }

    @Test fun `prompt shows Rest flag`() {
        val p = renderer.renderPrompt(state().copy(isResting = true))
        assertTrue("[Rest]" in p)
    }

    @Test fun `prompt in color mode contains ANSI codes`() {
        val p = renderer.renderPrompt(state(), true)
        assertTrue(p.contains(ESC))
    }

    @Test fun `prompt HP color green when over 50 percent`() {
        val p = renderer.renderPrompt(state().copy(currentHp = 80, maxHp = 100), true)
        assertTrue(p.contains(Ansi.GREEN))
    }

    @Test fun `prompt HP color yellow when 25-50 percent`() {
        val p = renderer.renderPrompt(state().copy(currentHp = 30, maxHp = 100), true)
        assertTrue(p.contains(Ansi.YELLOW))
    }

    @Test fun `prompt HP color red when under 25 percent`() {
        val p = renderer.renderPrompt(state().copy(currentHp = 10, maxHp = 100), true)
        assertTrue(p.contains(Ansi.RED))
    }

    // ────────────────────────────────────────────────────────────────────────
    // Edge cases
    // ────────────────────────────────────────────────────────────────────────

    @Test fun `room with no exits, NPCs, players, items, or coins`() {
        val r = room(exits = emptyMap())
        val lines = renderer.render(ServerMessage.RoomInfo(r, emptyList(), emptyList()), emptyState())
        assertTrue(lines.isNotEmpty())
        assertFalse(lines.any { "Exits" in it })
        assertFalse(lines.any { "NPCs" in it })
        assertFalse(lines.any { "Items" in it })
    }

    @Test fun `0 HP bar is all empty`() {
        val bar = Ansi.hpBar(0, 100, 4, false)
        assertTrue("░░░░" in bar)
        assertFalse("█" in bar)
    }

    @Test fun `loot_dropped with nothing returns empty`() {
        val lines = renderer.render(ServerMessage.LootDropped("Rat", emptyList(), Coins()), emptyState())
        assertTrue(lines.isEmpty())
    }

    @Test fun `item name falls back to itemId when not in catalog`() {
        val lines = renderer.render(
            ServerMessage.InventoryUpdate(listOf(InventoryItem("item:mystery")), emptyMap()),
            emptyState()
        )
        assertTrue(lines.any { "item:mystery" in it })
    }

    // ---- Description wrapping ----

    private fun roomWithDesc(desc: String) = Room(
        id = "test:room", name = "Test Room", description = desc,
        exits = emptyMap(), zoneId = "test", x = 0, y = 0
    )

    private fun descLines(lines: List<String>): List<String> {
        val nameIdx = lines.indexOfFirst { "Test Room" in it }
        val emptyIdx = lines.subList(nameIdx + 1, lines.size).indexOfFirst { it.isBlank() }
            .let { if (it < 0) lines.size else nameIdx + 1 + it }
        return if (emptyIdx > nameIdx + 1) lines.subList(nameIdx + 1, emptyIdx) else emptyList()
    }

    @Test fun `short description fits on one line`() {
        val desc = "A cozy cave."
        val s = emptyState().copy(terminalWidth = 80)
        val lines = renderer.render(ServerMessage.RoomInfo(roomWithDesc(desc), emptyList(), emptyList()), s)
        val dl = descLines(lines)
        assertEquals(1, dl.size)
        assertEquals(desc, dl[0])
    }

    @Test fun `long description wraps at terminal width`() {
        val desc = "A vast chamber carved from ancient black stone. The walls are etched with runes that glow faintly in the darkness, and a cold wind howls from unseen passages far above."
        val s = emptyState().copy(terminalWidth = 60)
        val lines = renderer.render(ServerMessage.RoomInfo(roomWithDesc(desc), emptyList(), emptyList()), s)
        val dl = descLines(lines)
        assertTrue(dl.size > 1, "Long description should wrap to multiple lines at width 60")
        dl.forEach { line -> assertTrue(line.length <= 60, "Line exceeds 60: '$line' (${line.length})") }
    }

    @Test fun `wrapped lines preserve all words`() {
        val desc = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10"
        val s = emptyState().copy(terminalWidth = 30)
        val lines = renderer.render(ServerMessage.RoomInfo(roomWithDesc(desc), emptyList(), emptyList()), s)
        val joined = descLines(lines).joinToString(" ")
        for (i in 1..10) assertTrue("word$i" in joined, "word$i missing from wrapped output")
    }

    @Test fun `narrow terminal produces more lines than wide`() {
        val desc = "A long description that should wrap differently depending on the terminal width setting."
        val wide = emptyState().copy(terminalWidth = 80)
        val narrow = emptyState().copy(terminalWidth = 40)
        val wideLines  = descLines(renderer.render(ServerMessage.RoomInfo(roomWithDesc(desc), emptyList(), emptyList()), wide))
        val narrowLines = descLines(renderer.render(ServerMessage.RoomInfo(roomWithDesc(desc), emptyList(), emptyList()), narrow))
        assertTrue(narrowLines.size >= wideLines.size, "Narrow terminal should produce >= lines vs wide")
    }

    @Test fun `wrap respects terminal width at 40`() {
        val desc = "Each word is exactly seven characters: aaaaaaa bbbbbbb ccccccc ddddddd eeeeeee fffffff ggggggg"
        val s = emptyState().copy(terminalWidth = 40)
        val lines = renderer.render(ServerMessage.RoomInfo(roomWithDesc(desc), emptyList(), emptyList()), s)
        descLines(lines).forEach { line ->
            assertTrue(line.length <= 40, "Line exceeds 40 chars: '$line' (${line.length})")
        }
    }
}
