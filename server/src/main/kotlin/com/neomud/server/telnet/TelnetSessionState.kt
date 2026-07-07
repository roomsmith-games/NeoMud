package com.neomud.server.telnet

import com.neomud.shared.model.*
import com.neomud.shared.protocol.ServerMessage

/** Updates cached game state from an incoming ServerMessage. */
fun TelnetSessionState.update(message: ServerMessage) {
    when (message) {
        is ServerMessage.LoginOk -> {
            val p = message.player
            playerName = p.name
            playerClass = p.characterClass
            playerLevel = p.level
            currentHp = p.currentHp
            maxHp = p.maxHp
            currentMp = p.currentMp
            maxMp = p.maxMp
            playerStats = p.stats
        }
        is ServerMessage.PlatformAuthOk -> {
            playerName = message.characterName
        }
        is ServerMessage.RoomInfo -> {
            val r = message.room
            currentRoomId = r.id
            currentRoomName = r.name
            currentRoomZone = r.zoneId
            currentRoomExits = r.exits.keys.toList()
            roomNpcs = message.npcs
            roomPlayers = message.players
            roomGroundItems = emptyList()
            roomGroundCoins = Coins()
            roomInteractables = r.interactables.filter { it.triggerType != "ON_ENTER" }
        }
        is ServerMessage.MoveOk -> {
            val r = message.room
            currentRoomId = r.id
            currentRoomName = r.name
            currentRoomZone = r.zoneId
            currentRoomExits = r.exits.keys.toList()
            roomNpcs = message.npcs
            roomPlayers = message.players
            roomGroundItems = emptyList()
            roomGroundCoins = Coins()
            roomInteractables = r.interactables.filter { it.triggerType != "ON_ENTER" }
        }
        is ServerMessage.RoomItemsUpdate -> {
            roomGroundItems = message.items
            roomGroundCoins = message.coins
        }
        is ServerMessage.InventoryUpdate -> {
            inventory = message.inventory
        }
        is ServerMessage.CraftingMenu -> {
            craftingRecipes = message.recipes
        }
        is ServerMessage.ItemCatalogSync -> {
            itemCatalog = message.items.associateBy { it.id }
        }
        is ServerMessage.AttackModeUpdate -> {
            inAttackMode = message.enabled
        }
        is ServerMessage.StealthUpdate -> {
            isHidden = message.hidden
        }
        is ServerMessage.MeditateUpdate -> {
            isMeditating = message.meditating
        }
        is ServerMessage.RestUpdate -> {
            isResting = message.resting
        }
        is ServerMessage.ActiveEffectsUpdate -> {
            activeEffects = message.effects
        }
        is ServerMessage.MapData -> {
            mapData = message
        }
        is ServerMessage.AtlasData -> {
            atlasData = message
        }
        is ServerMessage.PartyInfo -> {
            partyInfo = message
        }
        is ServerMessage.ServerHello -> {
            worldName = message.worldName
        }
        is ServerMessage.RiddlePrompt -> {
            modalState = ModalState.RiddleActive(message.featureId)
        }
        is ServerMessage.ChoicePrompt -> {
            modalState = ModalState.ChoiceActive(message.featureId, message.options)
        }
        is ServerMessage.PlaceItemPrompt -> {
            modalState = ModalState.PlaceItemActive(message.featureId)
        }
        is ServerMessage.InteractResult -> {
            modalState = ModalState.None
        }
        is ServerMessage.CombatHit -> {
            if (message.isPlayerDefender && playerName != null && message.defenderName == playerName) {
                currentHp = message.defenderHp
            }
        }
        is ServerMessage.LevelUp -> {
            playerLevel = message.newLevel
            maxHp = message.newMaxHp
            maxMp = message.newMaxMp
        }
        is ServerMessage.ItemUsed -> {
            // Potions/food heal the player — keep vitals (and thus prompt + GMCP) in sync.
            currentHp = message.newHp
            currentMp = message.newMp
        }
        is ServerMessage.EffectTick -> {
            currentHp = message.newHp
            if (message.newMp >= 0) currentMp = message.newMp
        }
        is ServerMessage.StatTrained -> {
            if (message.maxHp > 0) {
                currentHp = message.currentHp
                maxHp = message.maxHp
                currentMp = message.currentMp
                maxMp = message.maxMp
            }
            // Keep cached stats current so a re-emitted GMCP Char.Stats isn't stale.
            playerStats = playerStats?.let { s ->
                when (message.stat.lowercase()) {
                    "strength" -> s.copy(strength = message.newValue)
                    "agility" -> s.copy(agility = message.newValue)
                    "intellect" -> s.copy(intellect = message.newValue)
                    "willpower" -> s.copy(willpower = message.newValue)
                    "health" -> s.copy(health = message.newValue)
                    "charm" -> s.copy(charm = message.newValue)
                    else -> s
                }
            }
        }
        is ServerMessage.PlayerEntered -> {
            val existing = roomPlayers.none { it.name == message.playerName }
            if (existing) {
                val info = message.playerInfo
                if (info != null) roomPlayers = roomPlayers + info
            }
        }
        is ServerMessage.PlayerLeft -> {
            roomPlayers = roomPlayers.filter { it.name != message.playerName }
        }
        is ServerMessage.NpcEntered -> {
            // NPC entered; CommandParser resolves from roomNpcs — add a minimal entry
            // (full NPC state arrives in RoomInfo; this is just a presence update)
        }
        is ServerMessage.NpcLeft -> {
            roomNpcs = roomNpcs.filter { it.id != message.npcId }
        }
        is ServerMessage.NpcDied -> {
            roomNpcs = roomNpcs.filter { it.id != message.npcId }
        }
        else -> {}
    }
}

data class TelnetSessionState(
    var playerName: String? = null,
    var playerClass: String? = null,
    var playerLevel: Int = 1,
    var currentHp: Int = 0,
    var maxHp: Int = 0,
    var currentMp: Int = 0,
    var maxMp: Int = 0,
    var inAttackMode: Boolean = false,
    var isHidden: Boolean = false,
    var isMeditating: Boolean = false,
    var isResting: Boolean = false,
    var currentRoomId: String? = null,
    var roomNpcs: List<Npc> = emptyList(),
    var roomPlayers: List<PlayerInfo> = emptyList(),
    var roomGroundItems: List<GroundItem> = emptyList(),
    var roomGroundCoins: Coins = Coins(),
    var roomInteractables: List<RoomInteractable> = emptyList(),
    var inventory: List<InventoryItem> = emptyList(),
    var craftingRecipes: List<RecipeInfo> = emptyList(),
    var partyInfo: ServerMessage.PartyInfo? = null,
    var atlasData: ServerMessage.AtlasData? = null,
    var mapData: ServerMessage.MapData? = null,
    var activeEffects: List<ActiveEffect> = emptyList(),
    var itemCatalog: Map<String, Item> = emptyMap(),
    var terminalWidth: Int = 80,
    var modalState: ModalState = ModalState.None,
    var worldName: String = "",
    // Out-of-band protocol negotiation (GMCP for Mudlet, MSDP for TinTin++)
    var gmcpEnabled: Boolean = false,
    var msdpEnabled: Boolean = false,
    // Cached fields feeding GMCP/MSDP packages
    var playerStats: Stats? = null,
    var currentRoomName: String? = null,
    var currentRoomZone: String? = null,
    var currentRoomExits: List<Direction> = emptyList()
)

sealed class ModalState {
    object None : ModalState()
    data class RiddleActive(val featureId: String) : ModalState()
    data class ChoiceActive(val featureId: String, val options: List<ServerMessage.ChoiceOption>) : ModalState()
    data class PlaceItemActive(val featureId: String) : ModalState()
}
