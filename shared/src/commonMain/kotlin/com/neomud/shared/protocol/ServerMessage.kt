package com.neomud.shared.protocol

import com.neomud.shared.model.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ServerMessage {
    // Auth
    @Serializable
    @SerialName("register_ok")
    data object RegisterOk : ServerMessage()

    @Serializable
    @SerialName("login_ok")
    data class LoginOk(val player: Player) : ServerMessage()

    @Serializable
    @SerialName("auth_error")
    data class AuthError(val reason: String) : ServerMessage()

    @Serializable
    @SerialName("platform_auth_ok")
    data class PlatformAuthOk(
        val characterName: String? = null,
        val characterNames: List<String> = emptyList(),
        val platformUserId: String,
        val needsCharacterCreation: Boolean,
        /**
         * Platform role from the verified JWT. "GUEST" means the user is on
         * an anonymous session (Platform's /api/v1/auth/anonymous endpoint
         * mints these for marketplace visitors who haven't signed in). The
         * client should route GUEST sessions to the ephemeral guestRegister
         * flow instead of the persistent platform-register flow — anon
         * platform users have no Platform DB record and can't own a saved
         * character. Default null preserves backward compat with old
         * clients/servers that didn't send this field.
         */
        val role: String? = null
    ) : ServerMessage()

    @Serializable
    @SerialName("name_check_result")
    data class NameCheckResult(
        val usernameAvailable: Boolean = true,
        val characterNameAvailable: Boolean
    ) : ServerMessage()

    // Room
    @Serializable
    @SerialName("room_info")
    data class RoomInfo(
        val room: Room,
        val players: List<PlayerInfo>,
        val npcs: List<Npc>
    ) : ServerMessage()

    @Serializable
    @SerialName("move_ok")
    data class MoveOk(
        val direction: Direction,
        val room: Room,
        val players: List<PlayerInfo>,
        val npcs: List<Npc>
    ) : ServerMessage()

    @Serializable
    @SerialName("move_error")
    data class MoveError(val reason: String) : ServerMessage()

    // Map
    @Serializable
    @SerialName("map_data")
    data class MapData(
        val rooms: List<MapRoom>,
        val playerRoomId: RoomId,
        val visitedRooms: Set<RoomId> = emptySet()
    ) : ServerMessage()

    @Serializable
    @SerialName("atlas_data")
    data class AtlasData(
        val rooms: List<MapRoom>,
        val playerRoomId: RoomId,
        val zoneNames: Map<String, String> = emptyMap()
    ) : ServerMessage()

    // Events
    @Serializable
    @SerialName("player_entered")
    data class PlayerEntered(val playerName: String, val roomId: RoomId, val playerInfo: PlayerInfo? = null) : ServerMessage()

    @Serializable
    @SerialName("player_left")
    data class PlayerLeft(val playerName: String, val roomId: RoomId, val direction: Direction) : ServerMessage()

    @Serializable
    @SerialName("npc_entered")
    data class NpcEntered(
        val npcName: String,
        val roomId: RoomId,
        val npcId: String = "",
        val hostile: Boolean = false,
        val currentHp: Int = 0,
        val maxHp: Int = 0,
        val spawned: Boolean = false,
        val templateId: String = "",
        val attackSound: String = "",
        val missSound: String = "",
        val deathSound: String = "",
        val interactSound: String = "",
        val exitSound: String = ""
    ) : ServerMessage()

    @Serializable
    @SerialName("npc_left")
    data class NpcLeft(
        val npcName: String,
        val roomId: RoomId,
        val direction: Direction,
        val npcId: String = ""
    ) : ServerMessage()

    @Serializable
    @SerialName("player_says")
    data class PlayerSays(val playerName: String, val message: String) : ServerMessage()

    // Combat
    @Serializable
    @SerialName("combat_hit")
    data class CombatHit(
        val attackerName: String,
        val defenderName: String,
        val damage: Int,
        val defenderHp: Int,
        val defenderMaxHp: Int,
        val isPlayerDefender: Boolean = false,
        val isBackstab: Boolean = false,
        val isMiss: Boolean = false,
        val isDodge: Boolean = false,
        val isParry: Boolean = false,
        val defenderId: String = ""
    ) : ServerMessage()

    @Serializable
    @SerialName("npc_died")
    data class NpcDied(
        val npcId: String,
        val npcName: String,
        val killerName: String,
        val roomId: RoomId
    ) : ServerMessage()

    @Serializable
    @SerialName("player_died")
    data class PlayerDied(
        val killerName: String,
        val respawnRoomId: RoomId,
        val respawnHp: Int,
        val respawnMp: Int = 0
    ) : ServerMessage()

    @Serializable
    @SerialName("attack_mode_update")
    data class AttackModeUpdate(val enabled: Boolean) : ServerMessage()

    // Effects
    @Serializable
    @SerialName("active_effects_update")
    data class ActiveEffectsUpdate(val effects: List<ActiveEffect>) : ServerMessage()

    @Serializable
    @SerialName("effect_tick")
    data class EffectTick(
        val effectName: String,
        val message: String,
        val newHp: Int,
        val sound: String = "",
        val newMp: Int = -1
    ) : ServerMessage()

    // System
    @Serializable
    @SerialName("system_message")
    data class SystemMessage(val message: String) : ServerMessage()

    @Serializable
    @SerialName("tutorial")
    data class Tutorial(
        val key: String,
        val title: String,
        val content: String,
        val blocking: Boolean = true,
        @SerialName("target_element")
        val targetElement: String? = null
    ) : ServerMessage()

    /**
     * Quest/lore NPC dialogue. Distinct from [Tutorial] so it can re-fire on
     * every interaction without polluting the per-character dedup set in
     * `PlayerDiscoveryTable`. The client renders this in the same blocking
     * modal slot as Tutorial — it is the same UX, different persistence.
     */
    @Serializable
    @SerialName("npc_dialogue")
    data class NpcDialogue(
        @SerialName("npc_id") val npcId: String,
        @SerialName("npc_name") val npcName: String,
        val content: String
    ) : ServerMessage()

    /**
     * Server tells the client to open an item-placement dialog for a Phase 8 puzzle
     * interactable. Client filters inventory to [acceptedItems] and lets the player
     * pick one, replying with [com.neomud.shared.protocol.ClientMessage.PlaceItem].
     */
    @Serializable
    @SerialName("place_item_prompt")
    data class PlaceItemPrompt(
        @SerialName("feature_id") val featureId: String,
        val label: String,
        val prompt: String,
        @SerialName("accepted_items") val acceptedItems: List<String>
    ) : ServerMessage()

    @Serializable
    @SerialName("riddle_prompt")
    data class RiddlePrompt(
        @SerialName("feature_id") val featureId: String,
        val label: String,
        val question: String,
        val hint: String? = null
    ) : ServerMessage()

    @Serializable
    data class ChoiceOption(val id: String, val label: String)

    @Serializable
    @SerialName("choice_prompt")
    data class ChoicePrompt(
        @SerialName("feature_id") val featureId: String,
        val label: String,
        val question: String,
        val options: List<ChoiceOption>
    ) : ServerMessage()

    @Serializable
    @SerialName("pong")
    data object Pong : ServerMessage()

    @Serializable
    @SerialName("server_shutdown")
    data class ServerShutdown(val message: String, val secondsRemaining: Int) : ServerMessage()

    @Serializable
    @SerialName("session_conflict")
    data class SessionConflict(
        val characterName: String,
        val message: String = "You already have an active session. Take over and log in here?"
    ) : ServerMessage()

    @Serializable
    @SerialName("session_displaced")
    data class SessionDisplaced(val reason: String = "Another session logged in") : ServerMessage()

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : ServerMessage()

    // Catalog sync
    @Serializable
    @SerialName("class_catalog_sync")
    data class ClassCatalogSync(val classes: List<CharacterClassDef>) : ServerMessage()

    @Serializable
    @SerialName("item_catalog_sync")
    data class ItemCatalogSync(val items: List<Item>) : ServerMessage()

    // Inventory
    @Serializable
    @SerialName("inventory_update")
    data class InventoryUpdate(
        val inventory: List<InventoryItem>,
        val equipment: Map<String, String>,
        val coins: Coins = Coins()
    ) : ServerMessage()

    @Serializable
    @SerialName("loot_received")
    data class LootReceived(
        val npcName: String,
        val items: List<LootedItem>
    ) : ServerMessage()

    // Ground items
    @Serializable
    @SerialName("room_items_update")
    data class RoomItemsUpdate(
        val items: List<GroundItem>,
        val coins: Coins
    ) : ServerMessage()

    @Serializable
    @SerialName("loot_dropped")
    data class LootDropped(
        val npcName: String,
        val items: List<LootedItem>,
        val coins: Coins
    ) : ServerMessage()

    @Serializable
    @SerialName("pickup_result")
    data class PickupResult(
        val itemName: String,
        val quantity: Int,
        val isCoin: Boolean = false
    ) : ServerMessage()

    @Serializable
    @SerialName("item_used")
    data class ItemUsed(
        val itemName: String,
        val message: String,
        val newHp: Int,
        val newMp: Int
    ) : ServerMessage()

    @Serializable
    @SerialName("equip_update")
    data class EquipUpdate(
        val slot: String,
        val itemId: String?,
        val itemName: String?
    ) : ServerMessage()

    @Serializable
    @SerialName("stealth_update")
    data class StealthUpdate(val hidden: Boolean, val message: String = "") : ServerMessage()

    @Serializable
    @SerialName("meditate_update")
    data class MeditateUpdate(val meditating: Boolean, val message: String = "") : ServerMessage()

    @Serializable
    @SerialName("rest_update")
    data class RestUpdate(val resting: Boolean, val message: String = "") : ServerMessage()

    @Serializable
    @SerialName("track_result")
    data class TrackResult(
        val success: Boolean,
        val direction: Direction? = null,
        val targetName: String? = null,
        val message: String
    ) : ServerMessage()

    @Serializable
    @SerialName("skill_catalog_sync")
    data class SkillCatalogSync(val skills: List<SkillDef>) : ServerMessage()

    @Serializable
    @SerialName("race_catalog_sync")
    data class RaceCatalogSync(val races: List<RaceDef>) : ServerMessage()

    @Serializable
    @SerialName("xp_gained")
    data class XpGained(val amount: Long, val currentXp: Long, val xpToNextLevel: Long) : ServerMessage()

    @Serializable
    @SerialName("level_up")
    data class LevelUp(
        val newLevel: Int,
        val hpRoll: Int,
        val newMaxHp: Int,
        val mpRoll: Int,
        val newMaxMp: Int,
        val cpGained: Int,
        val totalUnspentCp: Int,
        val xpToNextLevel: Long = 0
    ) : ServerMessage()

    @Serializable
    @SerialName("trainer_info")
    data class TrainerInfo(
        val canLevelUp: Boolean,
        val unspentCp: Int,
        val totalCpEarned: Int,
        val baseStats: Stats,
        val currentStats: Stats,
        val interactSound: String = "",
        val exitSound: String = ""
    ) : ServerMessage()

    @Serializable
    @SerialName("stat_trained")
    data class StatTrained(
        val stat: String,
        val newValue: Int,
        val cpSpent: Int,
        val remainingCp: Int,
        val currentHp: Int = 0,
        val maxHp: Int = 0,
        val currentMp: Int = 0,
        val maxMp: Int = 0
    ) : ServerMessage()

    @Serializable
    @SerialName("spell_catalog_sync")
    data class SpellCatalogSync(val spells: List<SpellDef>) : ServerMessage()

    @Serializable
    @SerialName("spell_cast_result")
    data class SpellCastResult(
        val success: Boolean,
        val spellName: String,
        val message: String,
        val newMp: Int,
        val newHp: Int? = null
    ) : ServerMessage()

    @Serializable
    @SerialName("skill_effect")
    data class SkillEffect(
        val userName: String,
        val skillName: String,
        val targetName: String,
        val targetId: String,
        val damage: Int,
        val targetHp: Int,
        val targetMaxHp: Int,
        val message: String
    ) : ServerMessage()

    @Serializable
    @SerialName("crafting_menu")
    data class CraftingMenu(
        val crafterName: String,
        val recipes: List<RecipeInfo>,
        val playerCoins: Coins,
        val interactSound: String = "",
        val exitSound: String = ""
    ) : ServerMessage()

    @Serializable
    @SerialName("craft_result")
    data class CraftResult(
        val success: Boolean,
        val itemName: String,
        val message: String,
        val updatedCoins: Coins,
        val updatedInventory: List<InventoryItem>,
        val equipment: Map<String, String>
    ) : ServerMessage()

    @Serializable
    @SerialName("spell_effect")
    data class SpellEffect(
        val casterName: String,
        val targetName: String,
        val spellName: String,
        val effectAmount: Int,
        val targetNewHp: Int,
        val targetMaxHp: Int,
        val isPlayerTarget: Boolean = false,
        val targetId: String = ""
    ) : ServerMessage()

    @Serializable
    @SerialName("vendor_info")
    data class VendorInfo(
        val vendorName: String,
        val items: List<VendorItem>,
        val playerCoins: Coins,
        val playerInventory: List<InventoryItem>,
        val playerCharm: Int = 0,
        val interactSound: String = "",
        val exitSound: String = "",
        val hasHaggle: Boolean = false
    ) : ServerMessage()

    @Serializable
    @SerialName("buy_result")
    data class BuyResult(
        val success: Boolean,
        val message: String,
        val updatedCoins: Coins,
        val updatedInventory: List<InventoryItem>,
        val equipment: Map<String, String>
    ) : ServerMessage()

    @Serializable
    @SerialName("sell_result")
    data class SellResult(
        val success: Boolean,
        val message: String,
        val updatedCoins: Coins,
        val updatedInventory: List<InventoryItem>,
        val equipment: Map<String, String>
    ) : ServerMessage()

    @Serializable
    @SerialName("interact_result")
    data class InteractResult(
        val success: Boolean,
        val featureName: String,
        val message: String,
        val sound: String = ""
    ) : ServerMessage()

    @Serializable
    @SerialName("npc_phase_shift")
    data class NpcPhaseShift(
        val npcId: String,
        val npcName: String,
        val phaseName: String,
        val spriteId: String = "",
        val message: String,
        val currentHp: Int,
        val maxHp: Int,
        val sound: String = ""
    ) : ServerMessage()

    // Party
    @Serializable
    @SerialName("party_invite_received")
    data class PartyInviteReceived(
        val inviterName: String,
        val partySize: Int
    ) : ServerMessage()

    @Serializable
    @SerialName("party_formed")
    data class PartyFormed(
        val partyId: String,
        val members: List<PartyMember>,
        val leaderId: String
    ) : ServerMessage()

    @Serializable
    @SerialName("party_member_joined")
    data class PartyMemberJoined(val member: PartyMember) : ServerMessage()

    @Serializable
    @SerialName("party_member_left")
    data class PartyMemberLeft(
        val memberName: String,
        val reason: String = "left"
    ) : ServerMessage()

    @Serializable
    @SerialName("party_disbanded")
    data class PartyDisbanded(val reason: String = "disbanded") : ServerMessage()

    @Serializable
    @SerialName("party_member_update")
    data class PartyMemberUpdate(
        val memberName: String,
        val currentHp: Int,
        val maxHp: Int,
        val currentMp: Int = 0,
        val maxMp: Int = 0,
        val roomId: String = ""
    ) : ServerMessage()

    @Serializable
    @SerialName("party_chat")
    data class PartyChatMessage(
        val senderName: String,
        val message: String
    ) : ServerMessage()

    @Serializable
    @SerialName("party_info")
    data class PartyInfo(
        val partyId: String,
        val members: List<PartyMember>,
        val leaderId: String
    ) : ServerMessage()

    // Follow
    @Serializable
    @SerialName("follow_update")
    data class FollowUpdate(
        val followerName: String,
        val targetName: String,
        val state: FollowState
    ) : ServerMessage()

    @Serializable
    @SerialName("rally_ping")
    data class RallyPing(
        val leaderName: String,
        val roomId: String,
        val roomName: String,
        val zoneName: String = ""
    ) : ServerMessage()

    @Serializable
    @SerialName("follow_failed")
    data class FollowFailed(
        val followerName: String,
        val reason: String
    ) : ServerMessage()

    // Handshake — sent as the first message on WebSocket connect
    @Serializable
    @SerialName("server_hello")
    data class ServerHello(
        val engineVersion: String,
        val protocolVersion: Int,
        val worldName: String = "",
        val worldVersion: String = "",
        val minClientVersion: String = ""
    ) : ServerMessage()

    // Connection rejected — sent when client version is too old or other connection issues
    @Serializable
    @SerialName("connection_rejected")
    data class ConnectionRejected(
        val reason: String,
        val minClientVersion: String = "",
        val updateUrl: String = ""
    ) : ServerMessage()
}
