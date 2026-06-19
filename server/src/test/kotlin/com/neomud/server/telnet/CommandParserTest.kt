package com.neomud.server.telnet

import com.neomud.shared.model.Coins
import com.neomud.shared.model.GroundItem
import com.neomud.shared.model.InventoryItem
import com.neomud.shared.model.Item
import com.neomud.shared.model.Npc
import com.neomud.shared.model.Recipe
import com.neomud.shared.model.RecipeInfo
import com.neomud.shared.model.RoomInteractable
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.ServerMessage
import com.neomud.shared.model.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CommandParserTest {

    private val parser = CommandParser()

    // ---- Fixtures ----

    private fun state() = TelnetSessionState(
        roomNpcs = listOf(npc("npc:rat", "Rat"), npc("npc:spider", "Spider")),
        roomGroundItems = listOf(GroundItem("item:sword", 1), GroundItem("item:potion", 3)),
        roomInteractables = listOf(
            interactable("feat:lever", "Rusted Lever", "ON_ACTION"),
            interactable("feat:altar", "Ancient Altar", "ON_ACTION"),
            interactable("feat:trap", "Stone Tile", "ON_ENTER")
        ),
        inventory = listOf(InventoryItem("item:sword"), InventoryItem("item:potion")),
        craftingRecipes = listOf(recipe("recipe:iron_sword", "Iron Sword")),
        itemCatalog = mapOf(
            "item:sword" to item("item:sword", "Iron Sword", "weapon"),
            "item:potion" to item("item:potion", "Health Potion", "")
        )
    )

    private fun npc(id: String, name: String) = Npc(
        id = id, name = name, description = "", currentRoomId = "test:room",
        behaviorType = "HOSTILE"
    )

    private fun item(id: String, name: String, slot: String) = Item(
        id = id, name = name, description = "", type = "weapon", slot = slot
    )

    private fun groundItem(itemId: String) = GroundItem(itemId, 1)

    private fun interactable(id: String, label: String, trigger: String = "ON_ACTION") =
        RoomInteractable(id = id, label = label, description = "", actionType = "EXIT_OPEN", triggerType = trigger)

    private fun recipe(id: String, name: String) = RecipeInfo(
        recipe = Recipe(id = id, name = name, description = "", category = "general",
            materials = emptyList(), cost = Coins(), outputItemId = "item:output"),
        canCraft = true, materialStatus = emptyList()
    )

    private fun send(input: String, st: TelnetSessionState = state()) =
        parser.parse(input, st) as ParseResult.Send

    private fun local(input: String, st: TelnetSessionState = state()) =
        parser.parse(input, st) as ParseResult.Local

    private fun multi(input: String, st: TelnetSessionState = state()) =
        parser.parse(input, st) as ParseResult.Multi

    private fun err(input: String, st: TelnetSessionState = state()) =
        parser.parse(input, st) as ParseResult.Err

    // ---- Blank / empty ----

    @Test fun blankInputIsEmpty() {
        assertIs<ParseResult.Empty>(parser.parse("   ", state()))
    }

    @Test fun emptyStringIsEmpty() {
        assertIs<ParseResult.Empty>(parser.parse("", state()))
    }

    // ---- Movement ----

    @Test fun moveNorth() = assertEquals(ClientMessage.Move(Direction.NORTH), send("n").message)
    @Test fun moveNorthFull() = assertEquals(ClientMessage.Move(Direction.NORTH), send("north").message)
    @Test fun moveSouth() = assertEquals(ClientMessage.Move(Direction.SOUTH), send("s").message)
    @Test fun moveEast() = assertEquals(ClientMessage.Move(Direction.EAST), send("e").message)
    @Test fun moveWest() = assertEquals(ClientMessage.Move(Direction.WEST), send("w").message)
    @Test fun moveUp() = assertEquals(ClientMessage.Move(Direction.UP), send("u").message)
    @Test fun moveDown() = assertEquals(ClientMessage.Move(Direction.DOWN), send("d").message)
    @Test fun moveNE() = assertEquals(ClientMessage.Move(Direction.NORTHEAST), send("ne").message)
    @Test fun moveNW() = assertEquals(ClientMessage.Move(Direction.NORTHWEST), send("nw").message)
    @Test fun moveSE() = assertEquals(ClientMessage.Move(Direction.SOUTHEAST), send("se").message)
    @Test fun moveSW() = assertEquals(ClientMessage.Move(Direction.SOUTHWEST), send("sw").message)
    @Test fun moveCaseInsensitive() = assertEquals(ClientMessage.Move(Direction.NORTH), send("N").message)

    // ---- Look ----

    @Test fun lookCommand() { assertIs<ClientMessage.Look>(send("look").message) }
    @Test fun lookAlias() { assertIs<ClientMessage.Look>(send("l").message) }

    // ---- Say / Tell / Who ----

    @Test fun sayMessage() {
        val msg = send("say hello world").message as ClientMessage.Say
        assertEquals("hello world", msg.message)
    }

    @Test fun sayBlankError() {
        assertIs<ParseResult.Err>(parser.parse("say", state()))
    }

    @Test fun tellCommand() {
        val msg = send("tell Aldric hello there").message as ClientMessage.Say
        assertEquals("/tell Aldric hello there", msg.message)
    }

    @Test fun tellAlias() {
        val msg = send("t Bob hey").message as ClientMessage.Say
        assertEquals("/tell Bob hey", msg.message)
    }

    @Test fun tellMissingTarget() {
        assertIs<ParseResult.Err>(parser.parse("tell", state()))
    }

    @Test fun tellMissingMessage() {
        assertIs<ParseResult.Err>(parser.parse("tell Bob", state()))
    }

    @Test fun replyCommand() {
        val msg = send("reply how are you").message as ClientMessage.Say
        assertEquals("/reply how are you", msg.message)
    }

    @Test fun replyAlias() {
        val msg = send("r good thanks").message as ClientMessage.Say
        assertEquals("/reply good thanks", msg.message)
    }

    @Test fun whoCommand() {
        val msg = send("who").message as ClientMessage.Say
        assertEquals("/who", msg.message)
    }

    @Test fun ignoreCommand() {
        val msg = send("ignore Bob").message as ClientMessage.Say
        assertEquals("/ignore Bob", msg.message)
    }

    @Test fun unignoreCommand() {
        val msg = send("unignore Bob").message as ClientMessage.Say
        assertEquals("/unignore Bob", msg.message)
    }

    // ---- Combat ----

    @Test fun attackKnownNpc() {
        val result = multi("attack rat")
        assertEquals(2, result.messages.size)
        assertIs<ClientMessage.SelectTarget>(result.messages[0])
        val toggle = result.messages[1] as ClientMessage.AttackToggle
        assertTrue(toggle.enabled)
    }

    @Test fun killAliasForAttack() {
        assertIs<ParseResult.Multi>(parser.parse("kill rat", state()))
    }

    @Test fun kAliasForAttack() {
        assertIs<ParseResult.Multi>(parser.parse("k rat", state()))
    }

    @Test fun attackUnknownNpc() {
        assertIs<ParseResult.Err>(parser.parse("attack dragon", state()))
    }

    @Test fun attackBlankError() {
        assertIs<ParseResult.Err>(parser.parse("attack", state()))
    }

    @Test fun selectNpc() {
        val msg = send("select rat").message as ClientMessage.SelectTarget
        assertEquals("npc:rat", msg.npcId)
    }

    @Test fun selectDeselect() {
        val msg = send("select").message as ClientMessage.SelectTarget
        assertEquals(null, msg.npcId)
    }

    @Test fun fightToggle() {
        val msg = send("fight").message as ClientMessage.AttackToggle
        assertTrue(msg.enabled)
    }

    @Test fun fleeToggle() {
        val msg = send("flee").message as ClientMessage.AttackToggle
        assertTrue(!msg.enabled)
    }

    @Test fun stopfightAlias() {
        assertIs<ParseResult.Send>(parser.parse("stopfight", state()))
    }

    // ---- Skills ----

    @Test fun bashCommand() {
        val msg = send("bash rat").message as ClientMessage.UseSkill
        assertEquals("skill:bash", msg.skillId)
        assertEquals("npc:rat", msg.targetId)
    }

    @Test fun kickCommand() {
        val msg = send("kick rat").message as ClientMessage.UseSkill
        assertEquals("skill:kick", msg.skillId)
    }

    @Test fun meditateCommand() {
        val msg = send("meditate").message as ClientMessage.UseSkill
        assertEquals("skill:meditate", msg.skillId)
    }

    @Test fun medAlias() {
        val msg = send("med").message as ClientMessage.UseSkill
        assertEquals("skill:meditate", msg.skillId)
    }

    @Test fun trackNoTarget() {
        val msg = send("track").message as ClientMessage.UseSkill
        assertEquals("skill:track", msg.skillId)
        assertEquals(null, msg.targetId)
    }

    @Test fun trackWithTarget() {
        val msg = send("track rat").message as ClientMessage.UseSkill
        assertEquals("skill:track", msg.skillId)
        assertEquals("npc:rat", msg.targetId)
    }

    @Test fun skillCommandAddsPrefixIfAbsent() {
        val msg = send("skill bash rat").message as ClientMessage.UseSkill
        assertEquals("skill:bash", msg.skillId)
    }

    @Test fun skillCommandKeepsExistingPrefix() {
        val msg = send("skill skill:bash rat").message as ClientMessage.UseSkill
        assertEquals("skill:bash", msg.skillId)
    }

    // ---- Stealth ----

    @Test fun sneakOn() { assertIs<ClientMessage.SneakToggle>(send("sneak").message) }
    @Test fun sneakHideAlias() { assertIs<ClientMessage.SneakToggle>(send("hide").message) }
    @Test fun unsneakOff() {
        val msg = send("unsneak").message as ClientMessage.SneakToggle
        assertTrue(!msg.enabled)
    }
    @Test fun revealAlias() { assertIs<ClientMessage.SneakToggle>(send("reveal").message) }

    // ---- Spells ----

    @Test fun castNoTarget() {
        val msg = send("cast fireball").message as ClientMessage.CastSpell
        assertEquals("spell:fireball", msg.spellId)
        assertEquals(null, msg.targetId)
    }

    @Test fun castWithTarget() {
        val msg = send("cast fireball rat").message as ClientMessage.CastSpell
        assertEquals("spell:fireball", msg.spellId)
        assertEquals("npc:rat", msg.targetId)
    }

    @Test fun castAlias() {
        assertIs<ClientMessage.CastSpell>(send("c fireball").message)
    }

    @Test fun castAddsSpellPrefix() {
        val msg = send("cast heal").message as ClientMessage.CastSpell
        assertEquals("spell:heal", msg.spellId)
    }

    @Test fun readySpell() {
        val msg = send("ready fireball").message as ClientMessage.ReadySpell
        assertEquals("spell:fireball", msg.spellId)
    }

    @Test fun unreadySpell() {
        val msg = send("unready").message as ClientMessage.ReadySpell
        assertEquals(null, msg.spellId)
    }

    // ---- Inventory ----

    @Test fun inventoryCommand() { assertIs<ClientMessage.ViewInventory>(send("i").message) }
    @Test fun invAlias() { assertIs<ClientMessage.ViewInventory>(send("inv").message) }
    @Test fun inventoryAlias() { assertIs<ClientMessage.ViewInventory>(send("inventory").message) }

    @Test fun equipItem() {
        val msg = send("equip iron sword").message as ClientMessage.EquipItem
        assertEquals("item:sword", msg.itemId)
        assertEquals("weapon", msg.slot)
    }

    @Test fun wearAlias() {
        assertIs<ClientMessage.EquipItem>(send("wear iron sword").message)
    }

    @Test fun equipUnknownItem() {
        assertIs<ParseResult.Err>(parser.parse("equip dragon scales", state()))
    }

    @Test fun unequipSlot() {
        val msg = send("unequip weapon").message as ClientMessage.UnequipItem
        assertEquals("weapon", msg.slot)
    }

    @Test fun removeAlias() {
        assertIs<ClientMessage.UnequipItem>(send("remove head").message)
    }

    @Test fun useItem() {
        val msg = send("use health potion").message as ClientMessage.UseItem
        assertEquals("item:potion", msg.itemId)
    }

    @Test fun useItemNotInInventory() {
        assertIs<ParseResult.Err>(parser.parse("use dragon blood", state()))
    }

    @Test fun useItemSuggestsInteract() {
        val st = state()
        // "Rusted Lever" is an ON_ACTION interactable and not in inventory
        val result = parser.parse("use lever", st)
        assertIs<ParseResult.Err>(result)
        assertTrue(result.text.contains("interact"))
    }

    @Test fun getItemFromGround() {
        val msg = send("get iron sword").message as ClientMessage.PickupItem
        assertEquals("item:sword", msg.itemId)
    }

    @Test fun takeAlias() {
        assertIs<ClientMessage.PickupItem>(send("take iron sword").message)
    }

    @Test fun getAll() {
        assertIs<LocalCommand.GetAll>(local("get all").command)
    }

    @Test fun takeAll() {
        assertIs<LocalCommand.GetAll>(local("take all").command)
    }

    @Test fun getCoins() {
        val msg = send("get coins").message as ClientMessage.PickupCoins
        assertEquals("all", msg.coinType)
    }

    @Test fun getCopper() {
        val msg = send("get copper").message as ClientMessage.PickupCoins
        assertEquals("copper", msg.coinType)
    }

    @Test fun getGold() {
        val msg = send("get gold").message as ClientMessage.PickupCoins
        assertEquals("gold", msg.coinType)
    }

    @Test fun getSilver() {
        val msg = send("get silver").message as ClientMessage.PickupCoins
        assertEquals("silver", msg.coinType)
    }

    @Test fun getPlatinum() {
        val msg = send("get platinum").message as ClientMessage.PickupCoins
        assertEquals("platinum", msg.coinType)
    }

    @Test fun dropItemNoQty() {
        val msg = send("drop iron sword").message as ClientMessage.DropItem
        assertEquals("item:sword", msg.itemId)
        assertEquals(1, msg.quantity)
    }

    @Test fun dropItemWithQty() {
        val msg = send("drop health potion 2").message as ClientMessage.DropItem
        assertEquals("item:potion", msg.itemId)
        assertEquals(2, msg.quantity)
    }

    @Test fun dropUnknownItem() {
        assertIs<ParseResult.Err>(parser.parse("drop dragon scales", state()))
    }

    // ---- Trainer ----

    @Test fun trainerCommand() { assertIs<ClientMessage.InteractTrainer>(send("trainer").message) }
    @Test fun practiceAlias() { assertIs<ClientMessage.InteractTrainer>(send("practice").message) }
    @Test fun trainNoArgs() { assertIs<ClientMessage.InteractTrainer>(send("train").message) }

    @Test fun trainStat() {
        val msg = send("train str").message as ClientMessage.TrainStat
        assertEquals("str", msg.stat)
        assertEquals(1, msg.points)
    }

    @Test fun trainStatWithPoints() {
        val msg = send("train agi 3").message as ClientMessage.TrainStat
        assertEquals("agi", msg.stat)
        assertEquals(3, msg.points)
    }

    @Test fun levelUp() { assertIs<ClientMessage.TrainLevelUp>(send("levelup").message) }

    // ---- Vendor ----

    @Test fun shopCommand() { assertIs<ClientMessage.InteractVendor>(send("shop").message) }
    @Test fun vendorAlias() { assertIs<ClientMessage.InteractVendor>(send("vendor").message) }
    @Test fun buyNoArgs() { assertIs<ClientMessage.InteractVendor>(send("buy").message) }

    @Test fun buyItem() {
        val msg = send("buy iron sword").message as ClientMessage.BuyItem
        assertEquals("item:sword", msg.itemId)
        assertEquals(1, msg.quantity)
    }

    @Test fun buyItemWithQty() {
        val msg = send("buy health potion 5").message as ClientMessage.BuyItem
        assertEquals("item:potion", msg.itemId)
        assertEquals(5, msg.quantity)
    }

    @Test fun sellItem() {
        val msg = send("sell iron sword").message as ClientMessage.SellItem
        assertEquals("item:sword", msg.itemId)
        assertEquals(1, msg.quantity)
    }

    @Test fun sellItemWithQty() {
        val msg = send("sell health potion 2").message as ClientMessage.SellItem
        assertEquals("item:potion", msg.itemId)
        assertEquals(2, msg.quantity)
    }

    @Test fun sellUnknown() {
        assertIs<ParseResult.Err>(parser.parse("sell dragon scales", state()))
    }

    // ---- Interactables ----

    @Test fun interactByName() {
        val msg = send("interact lever").message as ClientMessage.InteractFeature
        assertEquals("feat:lever", msg.featureId)
    }

    @Test fun activateAlias() {
        assertIs<ClientMessage.InteractFeature>(send("activate lever").message)
    }

    @Test fun interactAutoTargetSingleFeature() {
        val st = state().copy(roomInteractables = listOf(interactable("feat:only", "Altar")))
        val msg = send("interact", st).message as ClientMessage.InteractFeature
        assertEquals("feat:only", msg.featureId)
    }

    @Test fun interactBlankMultipleFeatures() {
        assertIs<ParseResult.Err>(parser.parse("interact", state()))
    }

    @Test fun interactIgnoresOnEnter() {
        // "Stone Tile" is ON_ENTER — should not be reachable via interact
        assertIs<ParseResult.Err>(parser.parse("interact stone tile", state()))
    }

    @Test fun talkNpc() {
        val msg = send("talk rat").message as ClientMessage.InteractNpc
        assertEquals("npc:rat", msg.npcId)
    }

    @Test fun npcAlias() {
        assertIs<ClientMessage.InteractNpc>(send("npc rat").message)
    }

    // ---- Crafting ----

    @Test fun craftingMenuCommand() { assertIs<ClientMessage.InteractCrafter>(send("crafting").message) }
    @Test fun recipesAlias() { assertIs<ClientMessage.InteractCrafter>(send("recipes").message) }
    @Test fun craftNoArgs() { assertIs<ClientMessage.InteractCrafter>(send("craft").message) }

    @Test fun craftRecipe() {
        val msg = send("craft iron sword").message as ClientMessage.CraftItem
        assertEquals("recipe:iron_sword", msg.recipeId)
    }

    @Test fun craftUnknownRecipe() {
        assertIs<ParseResult.Err>(parser.parse("craft mithril armor", state()))
    }

    // ---- Party ----

    @Test fun partyInfoNoArgs() { assertIs<LocalCommand.ShowPartyInfo>(local("party").command) }
    @Test fun partyInfoSub() { assertIs<LocalCommand.ShowPartyInfo>(local("party info").command) }

    @Test fun partyInvite() {
        val msg = send("party invite Aldric").message as ClientMessage.PartyInvite
        assertEquals("Aldric", msg.targetName)
    }

    @Test fun partyAccept() {
        val msg = send("party accept Bob").message as ClientMessage.PartyAccept
        assertEquals("Bob", msg.inviterName)
    }

    @Test fun partyDecline() {
        val msg = send("party decline Bob").message as ClientMessage.PartyDecline
        assertEquals("Bob", msg.inviterName)
    }

    @Test fun partyRejectAlias() {
        assertIs<ClientMessage.PartyDecline>(send("party reject Bob").message)
    }

    @Test fun partyLeave() { assertIs<ClientMessage.PartyLeave>(send("party leave").message) }

    @Test fun partyKick() {
        val msg = send("party kick Bob").message as ClientMessage.PartyKick
        assertEquals("Bob", msg.targetName)
    }

    @Test fun partySay() {
        val msg = send("party say hello").message as ClientMessage.PartySay
        assertEquals("hello", msg.message)
    }

    @Test fun partySayPs() {
        val msg = send("ps hey team").message as ClientMessage.PartySay
        assertEquals("hey team", msg.message)
    }

    @Test fun partySaySemicolon() {
        val msg = send("; charge!").message as ClientMessage.PartySay
        assertEquals("charge!", msg.message)
    }

    @Test fun partyPromote() {
        val msg = send("party promote Bob").message as ClientMessage.PartyPromote
        assertEquals("Bob", msg.targetName)
    }

    @Test fun partyUnknownSub() { assertIs<ParseResult.Err>(parser.parse("party fly", state())) }

    @Test fun pAliasPartyInfo() { assertIs<LocalCommand.ShowPartyInfo>(local("p").command) }

    @Test fun pAliasPartySay() {
        val msg = send("p hello").message as ClientMessage.PartySay
        assertEquals("hello", msg.message)
    }

    // ---- Follow ----

    @Test fun followPlayer() {
        val msg = send("follow Aldric").message as ClientMessage.Follow
        assertEquals("Aldric", msg.targetName)
    }

    @Test fun unfollowCommand() { assertIs<ClientMessage.FollowStop>(send("unfollow").message) }
    @Test fun stopfollowAlias() { assertIs<ClientMessage.FollowStop>(send("stopfollow").message) }
    @Test fun rallyCommand() { assertIs<ClientMessage.Rally>(send("rally").message) }

    // ---- Map / info ----

    @Test fun mapCommand() { assertIs<LocalCommand.ShowMap>(local("map").command) }
    @Test fun atlasCommand() { assertIs<LocalCommand.ShowAtlas>(local("atlas").command) }
    @Test fun worldAlias() { assertIs<LocalCommand.ShowAtlas>(local("world").command) }
    @Test fun effectsCommand() { assertIs<LocalCommand.ShowEffects>(local("effects").command) }
    @Test fun buffsAlias() { assertIs<LocalCommand.ShowEffects>(local("buffs").command) }
    @Test fun hpCommand() { assertIs<LocalCommand.ShowHp>(local("hp").command) }
    @Test fun healthAlias() { assertIs<LocalCommand.ShowHp>(local("health").command) }

    // ---- Help ----

    @Test fun helpNoTopic() {
        val cmd = local("help").command as LocalCommand.Help
        assertEquals(null, cmd.topic)
    }

    @Test fun helpWithTopic() {
        val cmd = local("help attack").command as LocalCommand.Help
        assertEquals("attack", cmd.topic)
    }

    @Test fun questionMarkAlias() {
        assertIs<LocalCommand.Help>(local("?").command)
    }

    @Test fun helpLinesNotEmpty() {
        val lines = parser.helpLines()
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.any { it.contains("Movement") })
        assertTrue(lines.any { it.contains("Combat") })
        assertTrue(lines.any { it.contains("help") })
    }

    @Test fun helpLinesTopicMatch() {
        val lines = parser.helpLines("attack")
        assertTrue(lines.isNotEmpty())
    }

    @Test fun helpLinesTopicNotFound() {
        val lines = parser.helpLines("zzznomatch")
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("No help found"))
    }

    // ---- help <command> detail (COMMAND_DETAIL lookup) ----

    @Test fun helpDetailReturnsCommandDescription() {
        val lines = parser.helpLines("n")
        assertTrue(lines.isNotEmpty())
        assertTrue(lines[0].contains("Move north"))
    }

    @Test fun helpDetailIncludesAliasInFirstLine() {
        val lines = parser.helpLines("n")
        // "north" should appear as the alias
        assertTrue(lines[0].contains("north"))
    }

    @Test fun helpDetailAttackHasUsageLine() {
        val lines = parser.helpLines("attack")
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("Usage:"))
        assertTrue(lines[1].contains("<npc>"))
    }

    @Test fun helpDetailAttackAliasesInFirstLine() {
        val lines = parser.helpLines("attack")
        assertTrue(lines[0].contains("kill"))
        assertTrue(lines[0].contains("k"))
    }

    @Test fun helpDetailCommandWithNoUsageHasOneLine() {
        // "fight" has alias "fightmode" but no usage — only description line
        val lines = parser.helpLines("fight")
        assertEquals(1, lines.size)
        assertFalse(lines[0].contains("Usage:"))
    }

    @Test fun helpDetailCommandWithNoAliasNoUsageHasOneLine() {
        val lines = parser.helpLines("who")
        assertEquals(1, lines.size)
        assertFalse(lines[0].contains("Usage:"))
    }

    @Test fun helpDetailAliasKeyLooksUpOwnEntry() {
        // "k" has its own entry with description "Attack an NPC..."
        val lines = parser.helpLines("k")
        assertTrue(lines.isNotEmpty())
        assertTrue(lines[0].contains("Attack"))
    }

    @Test fun helpDetailCaseInsensitive() {
        val lower = parser.helpLines("attack")
        val upper = parser.helpLines("ATTACK")
        assertEquals(lower, upper)
    }

    @Test fun helpDetailLookupTakesPriorityOverSectionSearch() {
        // "attack" exists in COMMAND_DETAIL — should get the precise detail, not the section fallback
        val lines = parser.helpLines("attack")
        // Detail format: "attack (kill, k) — Attack..."  not "Combat: attack (kill, k) <npc>"
        assertFalse(lines[0].startsWith("Combat:"))
    }

    @Test fun helpSectionFallbackForCategoryName() {
        // "movement" is a category, not a command — falls through to section search
        val lines = parser.helpLines("movement")
        assertTrue(lines.isNotEmpty())
        assertFalse(lines[0].contains("No help found"))
    }

    @Test fun helpDetailCancelHasNoAliasNoUsage() {
        val lines = parser.helpLines("cancel")
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("cancel"))
        assertTrue(lines[0].contains("Cancel"))
    }

    @Test fun helpDetailTellIncludesAlias() {
        val lines = parser.helpLines("tell")
        assertTrue(lines[0].contains("t"))  // alias
        assertTrue(lines[0].contains("private message"))
    }

    @Test fun helpDetailTellIncludesUsage() {
        val lines = parser.helpLines("tell")
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("<player>"))
        assertTrue(lines[1].contains("<message>"))
    }

    // ---- Cancel ----

    @Test fun cancelCommand() { assertIs<LocalCommand.Cancel>(local("cancel").command) }

    // ---- Unknown command ----

    @Test fun unknownCommandError() {
        val result = parser.parse("xyzzy", state())
        assertIs<ParseResult.Err>(result)
        assertTrue(result.text.contains("help"))
    }

    // ---- Modal: RiddleActive ----

    @Test fun modalRiddleAnswer() {
        val st = state().copy(modalState = ModalState.RiddleActive("feat:sphinx"))
        val msg = send("answer time", st).message as ClientMessage.AnswerRiddle
        assertEquals("feat:sphinx", msg.featureId)
        assertEquals("time", msg.answer)
    }

    @Test fun modalRiddleAnswerMultiWord() {
        val st = state().copy(modalState = ModalState.RiddleActive("feat:sphinx"))
        val msg = send("answer the answer is yes", st).message as ClientMessage.AnswerRiddle
        assertEquals("the answer is yes", msg.answer)
    }

    @Test fun modalRiddleCancel() {
        val st = state().copy(modalState = ModalState.RiddleActive("feat:sphinx"))
        assertIs<LocalCommand.Cancel>(local("cancel", st).command)
    }

    @Test fun modalRiddleOtherCommandBlocked() {
        val st = state().copy(modalState = ModalState.RiddleActive("feat:sphinx"))
        assertIs<ParseResult.Err>(parser.parse("north", st))
    }

    @Test fun modalRiddleAnswerBlankError() {
        val st = state().copy(modalState = ModalState.RiddleActive("feat:sphinx"))
        assertIs<ParseResult.Err>(parser.parse("answer", st))
    }

    // ---- Modal: ChoiceActive ----

    private val choiceOptions = listOf(
        ServerMessage.ChoiceOption("opt_a", "Help the merchant"),
        ServerMessage.ChoiceOption("opt_b", "Demand a reward")
    )

    @Test fun modalChoiceSelect() {
        val st = state().copy(modalState = ModalState.ChoiceActive("feat:npc", choiceOptions))
        val msg = send("choose 2", st).message as ClientMessage.MakeChoice
        assertEquals("feat:npc", msg.featureId)
        assertEquals("opt_b", msg.choiceId)
    }

    @Test fun modalChoiceFirst() {
        val st = state().copy(modalState = ModalState.ChoiceActive("feat:npc", choiceOptions))
        val msg = send("choose 1", st).message as ClientMessage.MakeChoice
        assertEquals("opt_a", msg.choiceId)
    }

    @Test fun modalChoiceOutOfRange() {
        val st = state().copy(modalState = ModalState.ChoiceActive("feat:npc", choiceOptions))
        assertIs<ParseResult.Err>(parser.parse("choose 5", st))
    }

    @Test fun modalChoiceNonNumeric() {
        val st = state().copy(modalState = ModalState.ChoiceActive("feat:npc", choiceOptions))
        assertIs<ParseResult.Err>(parser.parse("choose abc", st))
    }

    @Test fun modalChoiceCancel() {
        val st = state().copy(modalState = ModalState.ChoiceActive("feat:npc", choiceOptions))
        assertIs<LocalCommand.Cancel>(local("cancel", st).command)
    }

    @Test fun modalChoiceOtherCommandBlocked() {
        val st = state().copy(modalState = ModalState.ChoiceActive("feat:npc", choiceOptions))
        assertIs<ParseResult.Err>(parser.parse("north", st))
    }

    // ---- Modal: PlaceItemActive ----

    @Test fun modalPlaceItem() {
        val st = state().copy(modalState = ModalState.PlaceItemActive("feat:altar"))
        val msg = send("place iron sword", st).message as ClientMessage.PlaceItem
        assertEquals("feat:altar", msg.featureId)
        assertEquals("item:sword", msg.itemId)
    }

    @Test fun modalPlaceItemNotInInventory() {
        val st = state().copy(modalState = ModalState.PlaceItemActive("feat:altar"))
        assertIs<ParseResult.Err>(parser.parse("place dragon scales", st))
    }

    @Test fun modalPlaceItemBlank() {
        val st = state().copy(modalState = ModalState.PlaceItemActive("feat:altar"))
        assertIs<ParseResult.Err>(parser.parse("place", st))
    }

    @Test fun modalPlaceItemCancel() {
        val st = state().copy(modalState = ModalState.PlaceItemActive("feat:altar"))
        assertIs<LocalCommand.Cancel>(local("cancel", st).command)
    }

    @Test fun modalPlaceItemOtherCommandBlocked() {
        val st = state().copy(modalState = ModalState.PlaceItemActive("feat:altar"))
        assertIs<ParseResult.Err>(parser.parse("look", st))
    }

    // ---- Edge cases ----

    @Test fun leadingTrailingWhitespace() {
        assertEquals(ClientMessage.Move(Direction.NORTH), send("  n  ").message)
    }

    @Test fun mixedCaseVerb() {
        assertIs<ClientMessage.Look>(send("LOOK").message)
    }

    @Test fun semicolonBlankError() {
        assertIs<ParseResult.Err>(parser.parse(";", state()))
    }
}
