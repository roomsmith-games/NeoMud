package com.neomud.server.telnet

import com.neomud.shared.model.*
import com.neomud.shared.protocol.ServerMessage

class TextRenderer {

    fun render(message: ServerMessage, state: TelnetSessionState, useColor: Boolean = false): List<String> {
        val uc = useColor
        return when (message) {

            // --- Auth ---
            is ServerMessage.LoginOk -> {
                val p = message.player
                listOf(Ansi.c("Welcome back, ${p.name}! [${p.race} ${p.characterClass} Lv.${p.level}]", Ansi.BOLD_CYAN, uc))
            }
            is ServerMessage.PlatformAuthOk ->
                listOf(Ansi.c("Welcome back, ${message.characterName ?: "Adventurer"}!", Ansi.BOLD_CYAN, uc))
            is ServerMessage.RegisterOk ->
                listOf(Ansi.c("Registration successful! You may now log in.", Ansi.BOLD_GREEN, uc))
            is ServerMessage.AuthError ->
                listOf(Ansi.c("Error: ${message.reason}", Ansi.RED, uc))
            is ServerMessage.NameCheckResult -> emptyList()
            is ServerMessage.SessionConflict ->
                listOf(Ansi.c("A session for ${message.characterName} is already active. Login with 'force' to displace it.", Ansi.YELLOW, uc))
            is ServerMessage.SessionDisplaced ->
                listOf(Ansi.c("*** Session displaced: ${message.reason}. Goodbye. ***", Ansi.BOLD_RED, uc))
            is ServerMessage.ConnectionRejected ->
                listOf(Ansi.c("Connection refused: ${message.reason}", Ansi.RED, uc))

            // --- Room ---
            is ServerMessage.RoomInfo ->
                renderRoom(message.room, message.players, message.npcs, state, uc)
            is ServerMessage.MoveOk -> buildList {
                add(Ansi.c("You head ${dirName(message.direction)}.", Ansi.WHITE, uc))
                add("")
                addAll(renderRoom(message.room, message.players, message.npcs, state, uc))
            }
            is ServerMessage.MoveError ->
                listOf(Ansi.c(message.reason, Ansi.YELLOW, uc))
            is ServerMessage.RoomItemsUpdate -> emptyList()

            // --- Players / NPCs ---
            is ServerMessage.PlayerEntered ->
                listOf(Ansi.c("${message.playerName} has arrived.", Ansi.CYAN, uc))
            is ServerMessage.PlayerLeft ->
                listOf(Ansi.c("${message.playerName} heads ${dirName(message.direction)}.", Ansi.CYAN, uc))
            is ServerMessage.NpcEntered ->
                listOf(Ansi.c("${message.npcName} has arrived.", Ansi.YELLOW, uc))
            is ServerMessage.NpcLeft ->
                listOf(Ansi.c("${message.npcName} heads ${dirName(message.direction)}.", Ansi.YELLOW, uc))

            // --- Chat ---
            is ServerMessage.PlayerSays ->
                listOf(Ansi.c("${message.playerName} says: \"${message.message}\"", Ansi.BOLD_WHITE, uc))
            is ServerMessage.TellReceived ->
                listOf(Ansi.c("${message.senderName} tells you: '${message.message}'", Ansi.YELLOW, uc))
            is ServerMessage.TellSent ->
                listOf(Ansi.c("You tell ${message.targetName}: '${message.message}'", Ansi.YELLOW, uc))
            is ServerMessage.WhoList -> buildList {
                if (message.players.isEmpty()) {
                    add(Ansi.c("No players online.", Ansi.WHITE, uc))
                } else {
                    add(Ansi.c("=== Online Players ===", Ansi.BOLD_WHITE, uc))
                    message.players.forEach { p ->
                        add(Ansi.c("  ${p.name} — ${p.characterClass} Lv.${p.level} [${p.zone}]", Ansi.WHITE, uc))
                    }
                }
            }
            is ServerMessage.NpcDialogue ->
                listOf(Ansi.c("${message.npcName} says: \"${message.content}\"", Ansi.YELLOW, uc))

            // --- Combat ---
            is ServerMessage.CombatHit -> renderCombatHit(message, uc)
            is ServerMessage.NpcDied ->
                listOf(Ansi.c("${message.killerName} has slain ${message.npcName}!", Ansi.BOLD_RED, uc))
            is ServerMessage.PlayerDied ->
                listOf(Ansi.c("You have been slain by ${message.killerName}! Respawning...", Ansi.BOLD_RED, uc))
            is ServerMessage.AttackModeUpdate ->
                listOf(if (message.enabled) Ansi.c("[ATTACK MODE ON]", Ansi.RED, uc) else Ansi.c("[ATTACK MODE OFF]", Ansi.GREEN, uc))
            is ServerMessage.NpcPhaseShift -> buildList {
                add(Ansi.c("*** ${message.npcName} ENTERS ${message.phaseName.uppercase()}! ***", Ansi.BOLD_RED, uc))
                add(Ansi.c("  ${message.message}", Ansi.RED, uc))
                add(Ansi.c("  ${message.npcName}: ${message.currentHp}/${message.maxHp} ${Ansi.hpBar(message.currentHp, message.maxHp, 10, uc)}", Ansi.GRAY, uc))
            }
            is ServerMessage.NpcAbilityEffect -> buildList {
                add(Ansi.c("*** ${message.npcName} uses ${message.abilityName}! ***", Ansi.BOLD_RED, uc))
                if (message.message.isNotBlank()) add(Ansi.c("  ${message.message}", Ansi.RED, uc))
                message.results.forEach { r ->
                    val line = when {
                        r.saved    -> Ansi.c("  ${r.targetName} resists!", Ansi.YELLOW, uc)
                        r.damage > 0 -> Ansi.c("  ${r.targetName} takes ${r.damage} damage. [${r.newHp}/${r.maxHp}]", Ansi.RED, uc)
                        else       -> Ansi.c("  ${r.targetName} is hit.", Ansi.YELLOW, uc)
                    }
                    add(line)
                }
            }
            is ServerMessage.SkillEffect -> buildList {
                add(Ansi.c("${message.userName} uses ${message.skillName} on ${message.targetName}!", Ansi.YELLOW, uc))
                if (message.damage > 0)
                    add(Ansi.c("  ${message.targetName} takes ${message.damage} damage. ${Ansi.hpBar(message.targetHp, message.targetMaxHp, 10, uc)}", Ansi.RED, uc))
                if (message.message.isNotBlank()) add(Ansi.c("  ${message.message}", Ansi.YELLOW, uc))
            }
            is ServerMessage.SpellEffect -> buildList {
                add(Ansi.c("${message.casterName} casts ${message.spellName} on ${message.targetName}.", Ansi.BLUE, uc))
                if (message.effectAmount != 0) {
                    val sign = if (message.effectAmount > 0) "+" else ""
                    add(Ansi.c("  $sign${message.effectAmount} HP. ${Ansi.hpBar(message.targetNewHp, message.targetMaxHp, 10, uc)}", Ansi.BLUE, uc))
                }
            }
            is ServerMessage.SpellCastResult ->
                listOf(Ansi.c(message.message, if (message.success) Ansi.BLUE else Ansi.RED, uc))

            // --- Effects ---
            is ServerMessage.ActiveEffectsUpdate -> emptyList()
            is ServerMessage.EffectTick ->
                listOf(Ansi.c("(${message.effectName}) ${message.message}", Ansi.MAGENTA, uc))

            // --- Inventory & Loot ---
            is ServerMessage.InventoryUpdate -> renderInventory(message, state, uc)
            is ServerMessage.LootReceived -> buildList {
                if (message.items.isNotEmpty()) {
                    val itemStr = message.items.joinToString(", ") { li ->
                        if (li.quantity > 1) "${li.quantity}x ${li.itemName}" else li.itemName
                    }
                    add(Ansi.c("You receive from ${message.npcName}: $itemStr", Ansi.GREEN, uc))
                }
            }
            is ServerMessage.LootDropped -> buildList {
                val parts = mutableListOf<String>()
                message.items.forEach { li ->
                    parts.add(if (li.quantity > 1) "${li.quantity}x ${li.itemName}" else li.itemName)
                }
                val c = message.coins
                if (c.gold > 0 || c.silver > 0 || c.copper > 0 || c.platinum > 0) parts.add(coinStr(c))
                if (parts.isNotEmpty())
                    add(Ansi.c("${message.npcName} drops: ${parts.joinToString(", ")}", Ansi.YELLOW, uc))
            }
            is ServerMessage.PickupResult -> {
                val line = when {
                    message.isCoin -> "You pick up the coins."
                    message.quantity > 1 -> "You pick up ${message.quantity}x ${message.itemName}."
                    else -> "You pick up the ${message.itemName}."
                }
                listOf(Ansi.c(line, Ansi.GREEN, uc))
            }
            is ServerMessage.ItemUsed ->
                listOf(Ansi.c(message.message, Ansi.GREEN, uc))
            is ServerMessage.EquipUpdate -> {
                val line = if (message.itemName != null) "Equipped ${message.itemName} in ${message.slot}."
                           else "Unequipped ${message.slot}."
                listOf(Ansi.c(line, Ansi.WHITE, uc))
            }

            // --- State updates ---
            is ServerMessage.StealthUpdate ->
                listOf(Ansi.c(
                    message.message.ifBlank { if (message.hidden) "You slip into the shadows." else "You step out of the shadows." },
                    Ansi.GRAY, uc))
            is ServerMessage.MeditateUpdate ->
                listOf(Ansi.c(
                    message.message.ifBlank { if (message.meditating) "You begin to meditate." else "You stop meditating." },
                    Ansi.BLUE, uc))
            is ServerMessage.RestUpdate ->
                listOf(Ansi.c(
                    message.message.ifBlank { if (message.resting) "You sit down to rest." else "You stop resting." },
                    Ansi.BLUE, uc))
            is ServerMessage.TrackResult ->
                listOf(Ansi.c(message.message, if (message.success) Ansi.GREEN else Ansi.YELLOW, uc))

            // --- XP & Level ---
            is ServerMessage.XpGained ->
                if (message.amount == 0L) emptyList()
                else listOf(Ansi.c("You gain ${message.amount} XP. (${message.currentXp}/${message.xpToNextLevel})", Ansi.YELLOW, uc))
            is ServerMessage.LevelUp -> buildList {
                add(Ansi.c("*** LEVEL UP! You are now level ${message.newLevel}! ***", Ansi.BOLD_YELLOW, uc))
                add(Ansi.c("  HP: +${message.hpRoll} (max: ${message.newMaxHp})  MP: +${message.mpRoll} (max: ${message.newMaxMp})", Ansi.YELLOW, uc))
                add(Ansi.c("  +${message.cpGained} CP. (${message.totalUnspentCp} unspent)", Ansi.YELLOW, uc))
            }
            is ServerMessage.TrainerInfo -> buildList {
                add(Ansi.c("=== Trainer ===", Ansi.BOLD_CYAN, uc))
                add(Ansi.c("  CP available: ${message.unspentCp}", Ansi.CYAN, uc))
                val s = message.currentStats
                add(Ansi.c("  STR:${s.strength}  AGI:${s.agility}  INT:${s.intellect}  WIL:${s.willpower}  HLT:${s.health}  CHM:${s.charm}", Ansi.WHITE, uc))
                add(Ansi.c("  Type 'train <stat>' to increase a stat (costs 1 CP).", Ansi.CYAN, uc))
            }
            is ServerMessage.StatTrained ->
                listOf(Ansi.c("${message.stat} increased to ${message.newValue}. (${message.remainingCp} CP remaining)", Ansi.CYAN, uc))

            // --- Vendors & Crafting ---
            is ServerMessage.VendorInfo -> buildList {
                add(Ansi.c("=== ${message.vendorName} ===", Ansi.BOLD_CYAN, uc))
                add(Ansi.c("  Your coins: ${coinStr(message.playerCoins)}", Ansi.YELLOW, uc))
                message.items.forEachIndexed { i, vi ->
                    add(Ansi.c("  ${i + 1}. ${vi.item.name} — ${coinStr(vi.price)}", Ansi.CYAN, uc))
                }
                add(Ansi.c("  Type 'buy <item>' to purchase.", Ansi.WHITE, uc))
            }
            is ServerMessage.BuyResult ->
                listOf(Ansi.c(message.message, if (message.success) Ansi.GREEN else Ansi.RED, uc))
            is ServerMessage.SellResult ->
                listOf(Ansi.c(message.message, if (message.success) Ansi.GREEN else Ansi.RED, uc))
            is ServerMessage.CraftingMenu -> buildList {
                add(Ansi.c("=== ${message.crafterName} ===", Ansi.BOLD_CYAN, uc))
                add(Ansi.c("  Your coins: ${coinStr(message.playerCoins)}", Ansi.YELLOW, uc))
                message.recipes.forEachIndexed { i, ri ->
                    val r = ri.recipe
                    val mats = ri.materialStatus.joinToString(", ") { ms -> "${ms.available}/${ms.required} ${ms.itemName}" }
                    val ok = if (ri.canCraft) Ansi.c(" [OK]", Ansi.GREEN, uc) else Ansi.c(" [X]", Ansi.RED, uc)
                    add(Ansi.c("  ${i + 1}. ${r.name} — $mats", Ansi.CYAN, uc) + ok)
                }
                add(Ansi.c("  Type 'craft <recipe>' to craft.", Ansi.WHITE, uc))
            }
            is ServerMessage.CraftResult ->
                listOf(Ansi.c(message.message, if (message.success) Ansi.GREEN else Ansi.RED, uc))
            is ServerMessage.InteractResult ->
                listOf(Ansi.c(message.message, if (message.success) Ansi.YELLOW else Ansi.RED, uc))

            // --- Interactive prompts ---
            is ServerMessage.PlaceItemPrompt -> buildList {
                add(Ansi.c("[${message.label}] ${message.prompt}", Ansi.CYAN, uc))
                add(Ansi.c("  Type 'place <item>' or 'cancel'.", Ansi.WHITE, uc))
            }
            is ServerMessage.RiddlePrompt -> buildList {
                add(Ansi.c("[${message.label}] ${message.question}", Ansi.CYAN, uc))
                if (message.hint != null) add(Ansi.c("  Hint: ${message.hint}", Ansi.GRAY, uc))
                add(Ansi.c("  Type 'answer <text>' or 'cancel'.", Ansi.WHITE, uc))
            }
            is ServerMessage.ChoicePrompt -> buildList {
                add(Ansi.c("[${message.label}] ${message.question}", Ansi.CYAN, uc))
                message.options.forEachIndexed { i, opt ->
                    add(Ansi.c("  ${i + 1}. ${opt.label}", Ansi.WHITE, uc))
                }
                add(Ansi.c("  Type 'choose <n>' or 'cancel'.", Ansi.WHITE, uc))
            }

            // --- Party ---
            is ServerMessage.PartyInviteReceived ->
                listOf(Ansi.c("** ${message.inviterName} invites you to join their party. Type 'party accept ${message.inviterName}' or 'party decline ${message.inviterName}'.", Ansi.BOLD_CYAN, uc))
            is ServerMessage.PartyFormed -> {
                val names = message.members.joinToString(", ") { it.name }
                listOf(Ansi.c("** Party formed: $names", Ansi.CYAN, uc))
            }
            is ServerMessage.PartyMemberJoined ->
                listOf(Ansi.c("** ${message.member.name} joins the party.", Ansi.CYAN, uc))
            is ServerMessage.PartyMemberLeft ->
                listOf(Ansi.c("** ${message.memberName} leaves the party (${message.reason}).", Ansi.CYAN, uc))
            is ServerMessage.PartyDisbanded ->
                listOf(Ansi.c("** The party has disbanded (${message.reason}).", Ansi.CYAN, uc))
            is ServerMessage.PartyMemberUpdate -> emptyList()
            is ServerMessage.PartyChatMessage ->
                listOf(Ansi.c("[Party] ${message.senderName}: ${message.message}", Ansi.CYAN, uc))
            is ServerMessage.PartyInfo -> renderPartyInfo(message, uc)
            is ServerMessage.PartyLeaderChanged ->
                listOf(Ansi.c("** ${message.newLeaderName} is now party leader.", Ansi.CYAN, uc))

            // --- Follow ---
            is ServerMessage.FollowUpdate -> {
                val line = when (message.state) {
                    FollowState.ACTIVE -> "** ${message.followerName} is now following ${message.targetName}."
                    FollowState.OFF    -> "** ${message.followerName} stops following ${message.targetName}."
                    FollowState.PAUSED -> "** ${message.followerName} has paused following ${message.targetName}."
                }
                listOf(Ansi.c(line, Ansi.GRAY, uc))
            }
            is ServerMessage.RallyPing ->
                listOf(Ansi.c("** ${message.leaderName} calls a rally to ${message.roomName} (${message.zoneName})!", Ansi.BOLD_CYAN, uc))
            is ServerMessage.FollowFailed ->
                listOf(Ansi.c("** Follow failed: ${message.reason}.", Ansi.YELLOW, uc))

            // --- System ---
            is ServerMessage.SystemMessage ->
                listOf(Ansi.c(message.message, Ansi.YELLOW, uc))
            is ServerMessage.ServerShutdown ->
                listOf(Ansi.c("*** SERVER SHUTDOWN: ${message.message} (${message.secondsRemaining}s) ***", Ansi.BOLD_RED, uc))
            is ServerMessage.Error ->
                listOf(Ansi.c("ERROR: ${message.message}", Ansi.RED, uc))

            // --- Map ---
            is ServerMessage.MapData   -> AsciiMap.render(message, uc)
            is ServerMessage.AtlasData -> AsciiMap.renderAtlas(message, uc)

            // --- Silent ---
            is ServerMessage.ServerHello      -> emptyList()
            is ServerMessage.Pong             -> emptyList()
            is ServerMessage.ClassCatalogSync -> emptyList()
            is ServerMessage.ItemCatalogSync  -> emptyList()
            is ServerMessage.SkillCatalogSync -> emptyList()
            is ServerMessage.RaceCatalogSync  -> emptyList()
            is ServerMessage.SpellCatalogSync -> emptyList()
            is ServerMessage.Tutorial         -> emptyList()

        }
    }

    fun renderPrompt(state: TelnetSessionState, useColor: Boolean = false): String {
        val uc = useColor
        if (state.playerName == null) return "> "
        val ratio = if (state.maxHp > 0) state.currentHp.toFloat() / state.maxHp else 0f
        val hpColor = when {
            ratio > 0.5f  -> Ansi.GREEN
            ratio > 0.25f -> Ansi.YELLOW
            else          -> Ansi.RED
        }
        val hp = Ansi.c("${state.currentHp}/${state.maxHp}", hpColor, uc)
        val sb = StringBuilder("<HP:$hp")
        if (state.maxMp > 0) sb.append(" MP:${state.currentMp}/${state.maxMp}")
        val flags = buildList {
            if (state.inAttackMode) add(Ansi.c("[Attack]", Ansi.RED, uc))
            if (state.isHidden)     add(Ansi.c("[Hidden]", Ansi.GRAY, uc))
            if (state.isMeditating) add(Ansi.c("[Med]", Ansi.BLUE, uc))
            if (state.isResting)    add(Ansi.c("[Rest]", Ansi.BLUE, uc))
        }
        if (flags.isNotEmpty()) sb.append(" ${flags.joinToString(" ")}")
        sb.append("> ")
        return sb.toString()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun renderRoom(
        room: Room, players: List<PlayerInfo>, npcs: List<Npc>,
        state: TelnetSessionState, uc: Boolean
    ): List<String> = buildList {
        add(Ansi.c("=== ${room.name} ===", Ansi.BOLD_WHITE, uc))
        addAll(wrap(room.description, state.terminalWidth))
        add("")

        val exits = room.exits.entries.joinToString(" ") { (dir, _) ->
            val name = dirName(dir)
            if (room.lockedExits.containsKey(dir)) "$name(locked)" else name
        }
        if (exits.isNotEmpty()) add(Ansi.c("  [Exits: $exits]", Ansi.WHITE, uc))

        if (npcs.isNotEmpty()) {
            val npcStr = npcs.joinToString(", ") { npc ->
                val prefix = if (npc.hostile) "*" else ""
                val bar = if (npc.maxHp > 0) " ${Ansi.hpBar(npc.currentHp, npc.maxHp, 4, uc)}" else ""
                "$prefix${npc.name}$bar"
            }
            add(Ansi.c("  NPCs: $npcStr", Ansi.YELLOW, uc))
        }

        if (players.isNotEmpty()) {
            val pStr = players.joinToString(", ") { p -> "${p.name} (${p.characterClass} Lv${p.level})" }
            add(Ansi.c("  Players: $pStr", Ansi.CYAN, uc))
        }

        if (state.roomGroundItems.isNotEmpty()) {
            val itemStr = state.roomGroundItems.joinToString(", ") { gi ->
                val name = state.itemCatalog[gi.itemId]?.name ?: gi.itemId
                if (gi.quantity > 1) "$name x${gi.quantity}" else name
            }
            add(Ansi.c("  Items: $itemStr", Ansi.WHITE, uc))
        }

        val gc = state.roomGroundCoins
        if (gc.gold > 0 || gc.silver > 0 || gc.copper > 0 || gc.platinum > 0)
            add(Ansi.c("  Coins: ${coinStr(gc)}", Ansi.YELLOW, uc))

        val features = room.interactables.filter { it.triggerType != "ON_ENTER" }
        if (features.isNotEmpty())
            add(Ansi.c("  Features: ${features.joinToString(", ") { it.label }}", Ansi.CYAN, uc))
    }

    private fun renderCombatHit(msg: ServerMessage.CombatHit, uc: Boolean): List<String> {
        if (msg.isMiss)  return listOf(Ansi.c("${msg.attackerName} misses ${msg.defenderName}!", Ansi.GRAY, uc))
        if (msg.isDodge) return listOf(Ansi.c("${msg.defenderName} dodges ${msg.attackerName}'s attack!", Ansi.GREEN, uc))
        if (msg.isParry) return listOf(Ansi.c("${msg.defenderName} parries ${msg.attackerName}'s blow!", Ansi.GREEN, uc))
        val bs = if (msg.isBackstab) " (backstab)" else ""
        val color = if (msg.isPlayerDefender) Ansi.RED else Ansi.YELLOW
        return listOf(Ansi.c("${msg.attackerName} hits ${msg.defenderName}$bs for ${msg.damage}! ${Ansi.hpBar(msg.defenderHp, msg.defenderMaxHp, 10, uc)}", color, uc))
    }

    private fun renderInventory(msg: ServerMessage.InventoryUpdate, state: TelnetSessionState, uc: Boolean): List<String> = buildList {
        add(Ansi.c("=== Inventory ===", Ansi.BOLD_WHITE, uc))
        val unequipped = msg.inventory.filter { !it.equipped }
        val equipped   = msg.inventory.filter { it.equipped }

        if (unequipped.isEmpty()) {
            add(Ansi.c("  (empty)", Ansi.GRAY, uc))
        } else {
            unequipped.forEach { item ->
                val name = state.itemCatalog[item.itemId]?.name ?: item.itemId
                val qty  = if (item.quantity > 1) " x${item.quantity}" else ""
                add(Ansi.c("  $name$qty", Ansi.WHITE, uc))
            }
        }

        if (equipped.isNotEmpty()) {
            add(Ansi.c("  --- Equipped ---", Ansi.GRAY, uc))
            equipped.forEach { item ->
                val name = state.itemCatalog[item.itemId]?.name ?: item.itemId
                add(Ansi.c("  [${item.slot}] $name", Ansi.WHITE, uc))
            }
        }

        val c = msg.coins
        if (c.gold > 0 || c.silver > 0 || c.copper > 0 || c.platinum > 0)
            add(Ansi.c("  Coins: ${coinStr(c)}", Ansi.YELLOW, uc))
    }

    private fun renderPartyInfo(msg: ServerMessage.PartyInfo, uc: Boolean): List<String> = buildList {
        add(Ansi.c("=== Party ===", Ansi.BOLD_CYAN, uc))
        msg.members.forEach { m ->
            val leader = if (m.isLeader) " [Leader]" else ""
            val bar = Ansi.hpBar(m.currentHp, m.maxHp, 8, uc)
            add(Ansi.c("  ${m.name}$leader ${m.characterClass} Lv.${m.level} $bar ${m.currentHp}/${m.maxHp}", Ansi.CYAN, uc))
        }
    }

    private fun dirName(dir: Direction): String = when (dir) {
        Direction.NORTHEAST -> "northeast"
        Direction.NORTHWEST -> "northwest"
        Direction.SOUTHEAST -> "southeast"
        Direction.SOUTHWEST -> "southwest"
        else -> dir.name.lowercase()
    }

    private fun wrap(text: String, maxWidth: Int): List<String> {
        if (text.isBlank()) return listOf(text)
        if (text.length <= maxWidth) return listOf(text)
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        val line = StringBuilder()
        for (word in words) {
            if (line.isNotEmpty() && line.length + 1 + word.length > maxWidth) {
                lines.add(line.toString())
                line.clear()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        if (line.isNotEmpty()) lines.add(line.toString())
        return lines
    }

    private fun coinStr(coins: Coins): String {
        val parts = mutableListOf<String>()
        if (coins.platinum > 0) parts.add("${coins.platinum}p")
        if (coins.gold > 0)     parts.add("${coins.gold}g")
        if (coins.silver > 0)   parts.add("${coins.silver}s")
        if (coins.copper > 0)   parts.add("${coins.copper}c")
        return if (parts.isEmpty()) "0c" else parts.joinToString(" ")
    }
}
