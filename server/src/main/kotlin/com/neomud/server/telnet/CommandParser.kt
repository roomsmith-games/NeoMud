package com.neomud.server.telnet

import com.neomud.shared.model.Direction
import com.neomud.shared.protocol.ClientMessage

sealed class ParseResult {
    data class Send(val message: ClientMessage) : ParseResult()
    data class Multi(val messages: List<ClientMessage>) : ParseResult()
    data class Local(val command: LocalCommand) : ParseResult()
    data class Err(val text: String) : ParseResult()
    object Empty : ParseResult()
}

sealed class LocalCommand {
    data class Help(val topic: String? = null) : LocalCommand()
    object ShowMap : LocalCommand()
    object ShowAtlas : LocalCommand()
    object ShowEffects : LocalCommand()
    object ShowHp : LocalCommand()
    object Cancel : LocalCommand()
    object ShowPartyInfo : LocalCommand()
    object GetAll : LocalCommand()
}

class CommandParser {

    fun parse(input: String, state: TelnetSessionState): ParseResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return ParseResult.Empty

        // Modal commands bypass normal dispatch
        when (val modal = state.modalState) {
            is ModalState.RiddleActive -> {
                val (verb, rest) = split2(trimmed)
                return when (verb) {
                    "cancel" -> ParseResult.Local(LocalCommand.Cancel)
                    "answer" -> if (rest.isBlank()) ParseResult.Err("Usage: answer <text>")
                                else ParseResult.Send(ClientMessage.AnswerRiddle(modal.featureId, rest))
                    else -> ParseResult.Err("Type 'answer <text>' or 'cancel'.")
                }
            }
            is ModalState.ChoiceActive -> {
                val (verb, rest) = split2(trimmed)
                return when (verb) {
                    "cancel" -> ParseResult.Local(LocalCommand.Cancel)
                    "choose" -> {
                        val n = rest.trim().toIntOrNull()
                            ?: return ParseResult.Err("Type 'choose <number>'.")
                        if (n < 1 || n > modal.options.size)
                            return ParseResult.Err("No option $n. Choose 1–${modal.options.size}.")
                        ParseResult.Send(ClientMessage.MakeChoice(modal.featureId, modal.options[n - 1].id))
                    }
                    else -> ParseResult.Err("Type 'choose <n>' or 'cancel'.")
                }
            }
            is ModalState.PlaceItemActive -> {
                val (verb, rest) = split2(trimmed)
                return when (verb) {
                    "cancel" -> ParseResult.Local(LocalCommand.Cancel)
                    "place" -> {
                        if (rest.isBlank()) return ParseResult.Err("Usage: place <item>")
                        resolveInventoryForAction(rest, state) { itemId ->
                            ClientMessage.PlaceItem(modal.featureId, itemId)
                        }
                    }
                    else -> ParseResult.Err("Type 'place <item>' or 'cancel'.")
                }
            }
            ModalState.None -> { /* continue normal dispatch */ }
        }

        // Party say shortcut: "; message"
        if (trimmed.startsWith(";")) {
            val msg = trimmed.removePrefix(";").trim()
            return if (msg.isBlank()) ParseResult.Err("Usage: ; <message>")
            else ParseResult.Send(ClientMessage.PartySay(msg))
        }

        val (verb, rest) = split2(trimmed)

        return when (verb) {
            // Movement
            "n", "north"     -> ParseResult.Send(ClientMessage.Move(Direction.NORTH))
            "s", "south"     -> ParseResult.Send(ClientMessage.Move(Direction.SOUTH))
            "e", "east"      -> ParseResult.Send(ClientMessage.Move(Direction.EAST))
            "w", "west"      -> ParseResult.Send(ClientMessage.Move(Direction.WEST))
            "u", "up"        -> ParseResult.Send(ClientMessage.Move(Direction.UP))
            "d", "down"      -> ParseResult.Send(ClientMessage.Move(Direction.DOWN))
            "ne", "northeast" -> ParseResult.Send(ClientMessage.Move(Direction.NORTHEAST))
            "nw", "northwest" -> ParseResult.Send(ClientMessage.Move(Direction.NORTHWEST))
            "se", "southeast" -> ParseResult.Send(ClientMessage.Move(Direction.SOUTHEAST))
            "sw", "southwest" -> ParseResult.Send(ClientMessage.Move(Direction.SOUTHWEST))

            // Look
            "look", "l"      -> ParseResult.Send(ClientMessage.Look)

            // Communication
            "say"            -> if (rest.isBlank()) ParseResult.Err("Usage: say <message>")
                                else ParseResult.Send(ClientMessage.Say(rest))
            "tell", "t"      -> parseTell(rest)
            "reply", "r"     -> if (rest.isBlank()) ParseResult.Err("Usage: reply <message>")
                                else ParseResult.Send(ClientMessage.Say("/reply $rest"))
            "who"            -> ParseResult.Send(ClientMessage.Say("/who"))
            "ignore"         -> if (rest.isBlank()) ParseResult.Err("Usage: ignore <player>")
                                else ParseResult.Send(ClientMessage.Say("/ignore $rest"))
            "unignore"       -> if (rest.isBlank()) ParseResult.Err("Usage: unignore <player>")
                                else ParseResult.Send(ClientMessage.Say("/unignore $rest"))

            // Combat
            "attack", "kill", "k" -> parseAttack(rest, state)
            "select", "target"    -> parseSelect(rest, state)
            "fight", "fightmode"  -> ParseResult.Send(ClientMessage.AttackToggle(true))
            "flee", "stopfight", "peace" -> ParseResult.Send(ClientMessage.AttackToggle(false))

            // Well-known skills
            "bash"           -> parseSkillWithTarget("skill:bash", rest, state)
            "kick"           -> parseSkillWithTarget("skill:kick", rest, state)
            "meditate", "med" -> ParseResult.Send(ClientMessage.UseSkill("skill:meditate"))
            "track"          -> parseSkillOptTarget("skill:track", rest, state)
            "skill"          -> parseSkillCommand(rest, state)

            // Stealth
            "sneak", "hide"               -> ParseResult.Send(ClientMessage.SneakToggle(true))
            "unsneak", "reveal", "unhide" -> ParseResult.Send(ClientMessage.SneakToggle(false))

            // Spells
            "cast", "c"   -> parseSpell(rest, state)
            "ready"        -> if (rest.isBlank()) ParseResult.Err("Usage: ready <spell>")
                              else ParseResult.Send(ClientMessage.ReadySpell(spellId(rest)))
            "unready", "noready", "clearspell" -> ParseResult.Send(ClientMessage.ReadySpell(null))

            // Inventory
            "i", "inv", "inventory" -> ParseResult.Send(ClientMessage.ViewInventory)
            "equip", "wear", "wield" -> parseEquip(rest, state)
            "unequip", "remove"      -> if (rest.isBlank()) ParseResult.Err("Usage: unequip <slot>")
                                        else ParseResult.Send(ClientMessage.UnequipItem(rest.trim().lowercase()))
            "use"           -> parseUse(rest, state)
            "get", "take", "pickup", "pick" -> parseGet(rest, state)
            "drop"          -> parseDrop(rest, state)

            // Trainer
            "trainer", "practice" -> ParseResult.Send(ClientMessage.InteractTrainer)
            "train"               -> parseTrain(rest)
            "levelup"             -> ParseResult.Send(ClientMessage.TrainLevelUp)

            // Vendor
            "shop", "vendor" -> ParseResult.Send(ClientMessage.InteractVendor)
            "buy"            -> if (rest.isBlank()) ParseResult.Send(ClientMessage.InteractVendor)
                                else parseBuy(rest, state)
            "sell"           -> parseSell(rest, state)

            // Interactables
            "interact", "activate" -> parseInteract(rest, state)
            "npc", "talk"          -> parseTalkNpc(rest, state)

            // Crafting
            "crafting", "recipes"  -> ParseResult.Send(ClientMessage.InteractCrafter)
            "craft"                -> if (rest.isBlank()) ParseResult.Send(ClientMessage.InteractCrafter)
                                      else parseCraft(rest, state)

            // Party
            "party" -> if (rest.isBlank()) ParseResult.Local(LocalCommand.ShowPartyInfo)
                       else parseParty(rest, state)
            "p"     -> if (rest.isBlank()) ParseResult.Local(LocalCommand.ShowPartyInfo)
                       else ParseResult.Send(ClientMessage.PartySay(rest))
            "ps"    -> if (rest.isBlank()) ParseResult.Err("Usage: ps <message>")
                       else ParseResult.Send(ClientMessage.PartySay(rest))

            // Follow / Rally
            "follow"                    -> if (rest.isBlank()) ParseResult.Err("Usage: follow <player>")
                                           else ParseResult.Send(ClientMessage.Follow(rest.trim()))
            "unfollow", "stopfollow"    -> ParseResult.Send(ClientMessage.FollowStop)
            "rally"                     -> ParseResult.Send(ClientMessage.Rally)

            // Local info
            "map"              -> ParseResult.Local(LocalCommand.ShowMap)
            "atlas", "world"   -> ParseResult.Local(LocalCommand.ShowAtlas)
            "effects", "buffs" -> ParseResult.Local(LocalCommand.ShowEffects)
            "hp", "health"     -> ParseResult.Local(LocalCommand.ShowHp)
            "help", "?"        -> if (rest.isBlank()) ParseResult.Local(LocalCommand.Help(null))
                                   else ParseResult.Local(LocalCommand.Help(rest.trim()))
            "cancel"           -> ParseResult.Local(LocalCommand.Cancel)

            else -> ParseResult.Err("Unknown command '$verb'. Type 'help' for a list of commands.")
        }
    }

    fun helpLines(topic: String? = null): List<String> {
        if (topic != null) {
            val t = topic.lowercase().trim()
            val detail = COMMAND_DETAIL[t]
            if (detail != null) {
                return buildList {
                    val aliases = if (detail.aliases.isNotEmpty()) " (${detail.aliases})" else ""
                    add("$t$aliases — ${detail.description}")
                    if (detail.usage.isNotEmpty()) add("  Usage: ${detail.usage}")
                }
            }
            return HELP_ENTRIES.filter { t in it.lowercase() }.takeIf { it.isNotEmpty() }
                ?: listOf("No help found for '$t'. Type 'help' to see all commands.")
        }
        return buildList {
            add("Commands:")
            add("")
            HELP_SECTIONS.forEach { (category, lines) ->
                add("  $category:")
                lines.forEach { add("    $it") }
                add("")
            }
            add("Type 'help <command>' for details.")
        }
    }

    // ---- Combat helpers ----

    private fun parseAttack(rest: String, state: TelnetSessionState): ParseResult {
        if (rest.isBlank()) return ParseResult.Err("Usage: attack <npc>")
        return when (val r = NameResolver.resolveNpc(rest, state.roomNpcs)) {
            is NameResolver.Resolve.Found -> ParseResult.Multi(listOf(
                ClientMessage.SelectTarget(r.entity.id),
                ClientMessage.AttackToggle(true)
            ))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(rest, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("No enemy '$rest' here.")
        }
    }

    private fun parseSelect(rest: String, state: TelnetSessionState): ParseResult {
        if (rest.isBlank()) return ParseResult.Send(ClientMessage.SelectTarget(null))
        return when (val r = NameResolver.resolveNpc(rest, state.roomNpcs)) {
            is NameResolver.Resolve.Found -> ParseResult.Send(ClientMessage.SelectTarget(r.entity.id))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(rest, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("No target '$rest' here.")
        }
    }

    // ---- Skill helpers ----

    private fun parseSkillWithTarget(skillId: String, rest: String, state: TelnetSessionState): ParseResult {
        if (rest.isBlank()) return ParseResult.Err("Usage: ${skillId.removePrefix("skill:")} <npc>")
        return when (val r = NameResolver.resolveNpc(rest, state.roomNpcs)) {
            is NameResolver.Resolve.Found -> ParseResult.Send(ClientMessage.UseSkill(skillId, r.entity.id))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(rest, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("No target '$rest' here.")
        }
    }

    private fun parseSkillOptTarget(skillId: String, rest: String, state: TelnetSessionState): ParseResult {
        if (rest.isBlank()) return ParseResult.Send(ClientMessage.UseSkill(skillId))
        return when (val r = NameResolver.resolveNpc(rest, state.roomNpcs)) {
            is NameResolver.Resolve.Found -> ParseResult.Send(ClientMessage.UseSkill(skillId, r.entity.id))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(rest, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("No target '$rest' here.")
        }
    }

    private fun parseSkillCommand(rest: String, state: TelnetSessionState): ParseResult {
        if (rest.isBlank()) return ParseResult.Err("Usage: skill <name> [target]")
        val (skillName, targetArg) = split2(rest)
        val id = skillId(skillName)
        return if (targetArg.isBlank()) ParseResult.Send(ClientMessage.UseSkill(id))
               else parseSkillOptTarget(id, targetArg, state)
    }

    // ---- Spell helpers ----

    private fun parseSpell(rest: String, state: TelnetSessionState): ParseResult {
        if (rest.isBlank()) return ParseResult.Err("Usage: cast <spell> [target]")
        val (spellName, targetArg) = split2(rest)
        val id = spellId(spellName)
        if (targetArg.isBlank()) return ParseResult.Send(ClientMessage.CastSpell(id))
        return when (val r = NameResolver.resolveNpc(targetArg, state.roomNpcs)) {
            is NameResolver.Resolve.Found -> ParseResult.Send(ClientMessage.CastSpell(id, r.entity.id))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(targetArg, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("No target '$targetArg' here.")
        }
    }

    // ---- Inventory helpers ----

    private fun parseEquip(rest: String, state: TelnetSessionState): ParseResult {
        if (rest.isBlank()) return ParseResult.Err("Usage: equip <item>")
        return when (val r = NameResolver.resolveInventoryItem(rest, state.inventory, state.itemCatalog)) {
            is NameResolver.Resolve.Found -> {
                val item = r.entity
                val slot = state.itemCatalog[item.itemId]?.slot ?: ""
                if (slot.isBlank()) ParseResult.Err("Can't determine slot for that item.")
                else ParseResult.Send(ClientMessage.EquipItem(item.itemId, slot))
            }
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(rest, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("You don't have '$rest' in your inventory.")
        }
    }

    private fun parseUse(rest: String, state: TelnetSessionState): ParseResult {
        if (rest.isBlank()) return ParseResult.Err("Usage: use <item>")
        return when (val r = NameResolver.resolveInventoryItem(rest, state.inventory, state.itemCatalog)) {
            is NameResolver.Resolve.Found -> ParseResult.Send(ClientMessage.UseItem(r.entity.itemId))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(rest, r.names))
            NameResolver.Resolve.NotFound -> {
                val interactResult = NameResolver.resolveInteractable(rest, state.roomInteractables)
                val hasInteractable = interactResult is NameResolver.Resolve.Found ||
                    interactResult is NameResolver.Resolve.Ambiguous
                if (hasInteractable) ParseResult.Err("'$rest' is not in your inventory. Did you mean 'interact $rest'?")
                else ParseResult.Err("You don't have '$rest' in your inventory.")
            }
        }
    }

    private fun parseGet(rest: String, state: TelnetSessionState): ParseResult {
        if (rest.isBlank()) return ParseResult.Err("Usage: get <item|all|coins>")
        val lower = rest.lowercase().trim()
        if (lower == "all") return ParseResult.Local(LocalCommand.GetAll)
        val coinTypes = setOf("coins", "copper", "silver", "gold", "platinum")
        if (lower in coinTypes) {
            val coinArg = if (lower == "coins") "all" else lower
            return ParseResult.Send(ClientMessage.PickupCoins(coinArg))
        }
        return when (val r = NameResolver.resolveGroundItem(rest, state.roomGroundItems, state.itemCatalog)) {
            is NameResolver.Resolve.Found -> ParseResult.Send(ClientMessage.PickupItem(r.entity.itemId))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(rest, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("No '$rest' on the ground here.")
        }
    }

    private fun parseDrop(rest: String, state: TelnetSessionState): ParseResult {
        if (rest.isBlank()) return ParseResult.Err("Usage: drop <item> [quantity]")
        val tokens = rest.trim().split("\\s+".toRegex())
        val qty = tokens.last().toIntOrNull()
        val itemQuery = if (qty != null && tokens.size > 1) tokens.dropLast(1).joinToString(" ") else rest
        return when (val r = NameResolver.resolveInventoryItem(itemQuery, state.inventory, state.itemCatalog)) {
            is NameResolver.Resolve.Found -> ParseResult.Send(ClientMessage.DropItem(r.entity.itemId, qty ?: 1))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(itemQuery, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("You don't have '$itemQuery' in your inventory.")
        }
    }

    // ---- Trainer helpers ----

    private fun parseTrain(rest: String): ParseResult {
        if (rest.isBlank()) return ParseResult.Send(ClientMessage.InteractTrainer)
        val tokens = rest.trim().split("\\s+".toRegex())
        val stat = tokens[0].lowercase()
        val pts = tokens.getOrNull(1)?.toIntOrNull() ?: 1
        return ParseResult.Send(ClientMessage.TrainStat(stat, pts))
    }

    // ---- Vendor helpers ----

    private fun parseBuy(rest: String, state: TelnetSessionState): ParseResult {
        val tokens = rest.trim().split("\\s+".toRegex())
        val qty = tokens.last().toIntOrNull()
        val itemQuery = if (qty != null && tokens.size > 1) tokens.dropLast(1).joinToString(" ") else rest
        val itemId = state.itemCatalog.values.firstOrNull {
            it.name.equals(itemQuery, ignoreCase = true)
        }?.id ?: state.itemCatalog.values.firstOrNull {
            it.name.lowercase().startsWith(itemQuery.lowercase())
        }?.id ?: state.itemCatalog.values.firstOrNull {
            it.name.lowercase().contains(itemQuery.lowercase())
        }?.id ?: return ParseResult.Err("Unknown item '$itemQuery'.")
        return ParseResult.Send(ClientMessage.BuyItem(itemId, qty ?: 1))
    }

    private fun parseSell(rest: String, state: TelnetSessionState): ParseResult {
        if (rest.isBlank()) return ParseResult.Err("Usage: sell <item> [quantity]")
        val tokens = rest.trim().split("\\s+".toRegex())
        val qty = tokens.last().toIntOrNull()
        val itemQuery = if (qty != null && tokens.size > 1) tokens.dropLast(1).joinToString(" ") else rest
        return when (val r = NameResolver.resolveInventoryItem(itemQuery, state.inventory, state.itemCatalog)) {
            is NameResolver.Resolve.Found -> ParseResult.Send(ClientMessage.SellItem(r.entity.itemId, qty ?: 1))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(itemQuery, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("You don't have '$itemQuery' in your inventory.")
        }
    }

    // ---- Interactable helpers ----

    private fun parseInteract(rest: String, state: TelnetSessionState): ParseResult {
        val actionInteractables = state.roomInteractables.filter { it.triggerType != "ON_ENTER" }
        if (rest.isBlank()) {
            return if (actionInteractables.size == 1) {
                ParseResult.Send(ClientMessage.InteractFeature(actionInteractables[0].id))
            } else if (actionInteractables.isEmpty()) {
                ParseResult.Err("Nothing to interact with here.")
            } else {
                val names = actionInteractables.map { it.label }
                ParseResult.Err("Which feature? ${names.joinToString(", ")} — type 'interact <name>'.")
            }
        }
        return when (val r = NameResolver.resolveInteractable(rest, actionInteractables)) {
            is NameResolver.Resolve.Found -> ParseResult.Send(ClientMessage.InteractFeature(r.entity.id))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(rest, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("No feature '$rest' here.")
        }
    }

    private fun parseTalkNpc(rest: String, state: TelnetSessionState): ParseResult {
        if (rest.isBlank()) return ParseResult.Err("Usage: talk <npc>")
        return when (val r = NameResolver.resolveNpc(rest, state.roomNpcs)) {
            is NameResolver.Resolve.Found -> ParseResult.Send(ClientMessage.InteractNpc(r.entity.id))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(rest, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("No NPC '$rest' here.")
        }
    }

    // ---- Crafting helpers ----

    private fun parseCraft(rest: String, state: TelnetSessionState): ParseResult {
        return when (val r = NameResolver.resolveRecipe(rest, state.craftingRecipes)) {
            is NameResolver.Resolve.Found -> ParseResult.Send(ClientMessage.CraftItem(r.entity.recipe.id))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(rest, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("No recipe '$rest' found. Type 'crafting' to open the menu.")
        }
    }

    // ---- Party helpers ----

    private fun parseParty(rest: String, state: TelnetSessionState): ParseResult {
        val (sub, args) = split2(rest)
        return when (sub.lowercase()) {
            "invite"             -> if (args.isBlank()) ParseResult.Err("Usage: party invite <player>")
                                    else ParseResult.Send(ClientMessage.PartyInvite(args.trim()))
            "accept"             -> if (args.isBlank()) ParseResult.Err("Usage: party accept <player>")
                                    else ParseResult.Send(ClientMessage.PartyAccept(args.trim()))
            "decline", "reject"  -> if (args.isBlank()) ParseResult.Err("Usage: party decline <player>")
                                    else ParseResult.Send(ClientMessage.PartyDecline(args.trim()))
            "leave"              -> ParseResult.Send(ClientMessage.PartyLeave)
            "kick"               -> if (args.isBlank()) ParseResult.Err("Usage: party kick <player>")
                                    else ParseResult.Send(ClientMessage.PartyKick(args.trim()))
            "say"                -> if (args.isBlank()) ParseResult.Err("Usage: party say <message>")
                                    else ParseResult.Send(ClientMessage.PartySay(args))
            "promote"            -> if (args.isBlank()) ParseResult.Err("Usage: party promote <player>")
                                    else ParseResult.Send(ClientMessage.PartyPromote(args.trim()))
            "info", ""           -> ParseResult.Local(LocalCommand.ShowPartyInfo)
            else -> ParseResult.Err("Unknown party command '$sub'. Try: invite accept decline leave kick say promote info.")
        }
    }

    // ---- Communication helpers ----

    private fun parseTell(rest: String): ParseResult {
        val idx = rest.indexOfFirst { it.isWhitespace() }
        if (idx < 0) return ParseResult.Err("Usage: tell <player> <message>")
        val target = rest.substring(0, idx)
        val msg = rest.substring(idx + 1).trim()
        return if (target.isBlank() || msg.isBlank()) ParseResult.Err("Usage: tell <player> <message>")
               else ParseResult.Send(ClientMessage.Say("/tell $target $msg"))
    }

    // ---- Utility ----

    private fun resolveInventoryForAction(
        query: String,
        state: TelnetSessionState,
        make: (String) -> ClientMessage
    ): ParseResult {
        return when (val r = NameResolver.resolveInventoryItem(query, state.inventory, state.itemCatalog)) {
            is NameResolver.Resolve.Found -> ParseResult.Send(make(r.entity.itemId))
            is NameResolver.Resolve.Ambiguous -> ParseResult.Err(NameResolver.ambiguityMessage(query, r.names))
            NameResolver.Resolve.NotFound -> ParseResult.Err("You don't have '$query' in your inventory.")
        }
    }

    private fun split2(input: String): Pair<String, String> {
        val idx = input.indexOfFirst { it.isWhitespace() }
        return if (idx < 0) input.lowercase() to ""
        else input.substring(0, idx).lowercase() to input.substring(idx + 1).trim()
    }

    private fun skillId(name: String) = if (name.startsWith("skill:")) name else "skill:$name"
    private fun spellId(name: String) = if (name.startsWith("spell:")) name else "spell:$name"

    // ---- Help content ----

    private val HELP_SECTIONS = listOf(
        "Movement" to listOf(
            "n s e w ne nw se sw u d",
            "look (l)"
        ),
        "Combat" to listOf(
            "attack (kill, k) <npc>",
            "select (target) <npc>",
            "fight — toggle attack on",
            "flee (stopfight, peace) — toggle attack off"
        ),
        "Skills" to listOf(
            "bash <npc>   kick <npc>   meditate (med)   track [npc]",
            "skill <name> [target]"
        ),
        "Spells" to listOf(
            "cast (c) <spell> [target]",
            "ready <spell>   unready"
        ),
        "Stealth" to listOf(
            "sneak (hide)   unsneak (reveal, unhide)"
        ),
        "Inventory" to listOf(
            "i (inv, inventory)",
            "equip (wear, wield) <item>   unequip (remove) <slot>",
            "use <item>   drop <item> [qty]",
            "get (take, pickup) <item|all|coins|copper|silver|gold|platinum>"
        ),
        "Trainer" to listOf(
            "trainer (practice)",
            "train <stat> [pts]   levelup"
        ),
        "Vendor" to listOf(
            "shop (vendor)   buy [item] [qty]   sell <item> [qty]"
        ),
        "Crafting" to listOf(
            "crafting (recipes)   craft <recipe>"
        ),
        "Interaction" to listOf(
            "interact (activate) <feature>   talk (npc) <npc>"
        ),
        "Communication" to listOf(
            "say <msg>",
            "tell (t) <player> <msg>   reply (r) <msg>   who",
            "ignore <player>   unignore <player>"
        ),
        "Party" to listOf(
            "party invite/accept/decline/leave/kick/promote <name>",
            "party say <msg>   ps (;) <msg>",
            "party (info)"
        ),
        "Follow" to listOf(
            "follow <player>   unfollow (stopfollow)   rally"
        ),
        "Info" to listOf(
            "map   atlas (world)   hp (health)   effects (buffs)"
        ),
        "System" to listOf(
            "help (?) [command]   cancel"
        )
    )

    private val HELP_ENTRIES: List<String> = HELP_SECTIONS.flatMap { (cat, lines) ->
        lines.map { "$cat: $it" }
    }

    private data class CmdHelp(val description: String, val aliases: String = "", val usage: String = "")

    private val COMMAND_DETAIL = mapOf(
        // Movement
        "n"         to CmdHelp("Move north", "north"),
        "north"     to CmdHelp("Move north", "n"),
        "s"         to CmdHelp("Move south", "south"),
        "south"     to CmdHelp("Move south", "s"),
        "e"         to CmdHelp("Move east", "east"),
        "east"      to CmdHelp("Move east", "e"),
        "w"         to CmdHelp("Move west", "west"),
        "west"      to CmdHelp("Move west", "w"),
        "u"         to CmdHelp("Move up", "up"),
        "up"        to CmdHelp("Move up", "u"),
        "d"         to CmdHelp("Move down", "down"),
        "down"      to CmdHelp("Move down", "d"),
        "ne"        to CmdHelp("Move northeast", "northeast"),
        "northeast" to CmdHelp("Move northeast", "ne"),
        "nw"        to CmdHelp("Move northwest", "northwest"),
        "northwest" to CmdHelp("Move northwest", "nw"),
        "se"        to CmdHelp("Move southeast", "southeast"),
        "southeast" to CmdHelp("Move southeast", "se"),
        "sw"        to CmdHelp("Move southwest", "southwest"),
        "southwest" to CmdHelp("Move southwest", "sw"),
        "look"      to CmdHelp("Look at the current room", "l"),
        "l"         to CmdHelp("Look at the current room", "look"),
        // Combat
        "attack"    to CmdHelp("Attack an NPC and enter fight mode", "kill, k", "attack <npc>"),
        "kill"      to CmdHelp("Attack an NPC and enter fight mode", "attack, k", "kill <npc>"),
        "k"         to CmdHelp("Attack an NPC and enter fight mode", "attack, kill", "k <npc>"),
        "select"    to CmdHelp("Select a combat target without entering fight mode", "target", "select <npc>"),
        "target"    to CmdHelp("Select a combat target without entering fight mode", "select", "target <npc>"),
        "fight"     to CmdHelp("Toggle attack mode on", "fightmode"),
        "fightmode" to CmdHelp("Toggle attack mode on", "fight"),
        "flee"      to CmdHelp("Toggle attack mode off", "stopfight, peace"),
        "stopfight" to CmdHelp("Toggle attack mode off", "flee, peace"),
        "peace"     to CmdHelp("Toggle attack mode off", "flee, stopfight"),
        "bash"      to CmdHelp("Use the bash skill on an NPC", usage = "bash <npc>"),
        "kick"      to CmdHelp("Use the kick skill on an NPC", usage = "kick <npc>"),
        "meditate"  to CmdHelp("Toggle meditation — restores MP over time", "med"),
        "med"       to CmdHelp("Toggle meditation — restores MP over time", "meditate"),
        "track"     to CmdHelp("Use the track skill — reveals nearby NPCs", usage = "track [npc]"),
        "skill"     to CmdHelp("Use a skill by name, optionally with a target", usage = "skill <name> [target]"),
        // Stealth
        "sneak"     to CmdHelp("Enter stealth mode", "hide"),
        "hide"      to CmdHelp("Enter stealth mode", "sneak"),
        "unsneak"   to CmdHelp("Leave stealth mode", "reveal, unhide"),
        "reveal"    to CmdHelp("Leave stealth mode", "unsneak, unhide"),
        "unhide"    to CmdHelp("Leave stealth mode", "unsneak, reveal"),
        // Spells
        "cast"      to CmdHelp("Cast a spell, optionally at a target", "c", "cast <spell> [target]"),
        "c"         to CmdHelp("Cast a spell, optionally at a target", "cast", "c <spell> [target]"),
        "ready"     to CmdHelp("Ready a spell for automatic use each combat tick", usage = "ready <spell>"),
        "unready"   to CmdHelp("Clear your readied spell", "noready, clearspell"),
        "noready"   to CmdHelp("Clear your readied spell", "unready, clearspell"),
        "clearspell" to CmdHelp("Clear your readied spell", "unready, noready"),
        // Inventory
        "i"         to CmdHelp("View your inventory", "inv, inventory"),
        "inv"       to CmdHelp("View your inventory", "i, inventory"),
        "inventory" to CmdHelp("View your inventory", "i, inv"),
        "equip"     to CmdHelp("Equip an item from your inventory", "wear, wield", "equip <item>"),
        "wear"      to CmdHelp("Equip an item from your inventory", "equip, wield", "wear <item>"),
        "wield"     to CmdHelp("Equip an item from your inventory", "equip, wear", "wield <item>"),
        "unequip"   to CmdHelp("Unequip an item from a slot", "remove", "unequip <slot>"),
        "remove"    to CmdHelp("Unequip an item from a slot", "unequip", "remove <slot>"),
        "use"       to CmdHelp("Use an item from your inventory", usage = "use <item>"),
        "get"       to CmdHelp("Pick up an item, all items, or coins from the floor", "take, pickup", "get <item|all|coins>"),
        "take"      to CmdHelp("Pick up an item, all items, or coins from the floor", "get, pickup", "take <item|all|coins>"),
        "pickup"    to CmdHelp("Pick up an item from the floor", "get, take", "pickup <item>"),
        "pick"      to CmdHelp("Pick up an item from the floor", "get, take", "pick <item>"),
        "drop"      to CmdHelp("Drop an item from your inventory", usage = "drop <item> [quantity]"),
        // Trainer
        "trainer"   to CmdHelp("Open the trainer menu to spend CP on stats", "practice"),
        "practice"  to CmdHelp("Open the trainer menu to spend CP on stats", "trainer"),
        "train"     to CmdHelp("Train a stat by spending 1 CP", usage = "train <stat> [points]"),
        "levelup"   to CmdHelp("Level up your character once you have enough XP"),
        // Vendor
        "shop"      to CmdHelp("Open the vendor shop menu", "vendor"),
        "vendor"    to CmdHelp("Open the vendor shop menu", "shop"),
        "buy"       to CmdHelp("Buy an item from the current vendor", usage = "buy <item> [quantity]"),
        "sell"      to CmdHelp("Sell an item to the current vendor", usage = "sell <item> [quantity]"),
        // Crafting
        "crafting"  to CmdHelp("Open the crafting menu at a crafter NPC", "recipes"),
        "recipes"   to CmdHelp("Open the crafting menu at a crafter NPC", "crafting"),
        "craft"     to CmdHelp("Craft an item using a recipe", usage = "craft <recipe>"),
        // Interaction
        "interact"  to CmdHelp("Interact with a room feature (lever, altar, puzzle…)", "activate", "interact [feature]"),
        "activate"  to CmdHelp("Interact with a room feature", "interact", "activate [feature]"),
        "talk"      to CmdHelp("Talk to an NPC", "npc", "talk <npc>"),
        "npc"       to CmdHelp("Talk to an NPC", "talk", "npc <npc>"),
        // Communication
        "say"       to CmdHelp("Say something to everyone in the room", usage = "say <message>"),
        "tell"      to CmdHelp("Send a private message to a player", "t", "tell <player> <message>"),
        "t"         to CmdHelp("Send a private message to a player", "tell", "t <player> <message>"),
        "reply"     to CmdHelp("Reply to the last player who sent you a tell", "r", "reply <message>"),
        "r"         to CmdHelp("Reply to the last player who sent you a tell", "reply", "r <message>"),
        "who"       to CmdHelp("List all currently online players"),
        "ignore"    to CmdHelp("Suppress messages from a player", usage = "ignore <player>"),
        "unignore"  to CmdHelp("Stop suppressing messages from a player", usage = "unignore <player>"),
        // Party
        "party"     to CmdHelp("Show party info, or manage your party", "p", "party [invite|accept|decline|leave|kick|promote|say] [args]"),
        "p"         to CmdHelp("Show party info, or send a party message", "party", "p [message]"),
        "ps"        to CmdHelp("Send a message to your party", ";", "ps <message>"),
        // Follow
        "follow"    to CmdHelp("Follow another player — you move when they move", usage = "follow <player>"),
        "unfollow"  to CmdHelp("Stop following", "stopfollow"),
        "stopfollow" to CmdHelp("Stop following", "unfollow"),
        "rally"     to CmdHelp("Call a rally ping to bring your party to your location"),
        // Info
        "map"       to CmdHelp("Display an ASCII minimap of your current zone level"),
        "atlas"     to CmdHelp("Display a zone index of the world", "world"),
        "world"     to CmdHelp("Display a zone index of the world", "atlas"),
        "effects"   to CmdHelp("List your active effects and buffs", "buffs"),
        "buffs"     to CmdHelp("List your active effects and buffs", "effects"),
        "hp"        to CmdHelp("Show your current HP and MP", "health"),
        "health"    to CmdHelp("Show your current HP and MP", "hp"),
        // System
        "help"      to CmdHelp("Show the command list, or get help on a command", "?", "help [command]"),
        "?"         to CmdHelp("Show the command list, or get help on a command", "help", "? [command]"),
        "cancel"    to CmdHelp("Cancel the current modal action (riddle, choice, or place item)"),
    )
}
