package com.neomud.shared.protocol

import com.neomud.shared.model.Direction
import com.neomud.shared.model.Stats
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class ClientMessage {
    @Serializable
    @SerialName("register")
    data class Register(
        val username: String = "",
        val password: String = "",
        val characterName: String,
        val characterClass: String,
        val race: String = "",
        val gender: String = "neutral",
        val allocatedStats: Stats = Stats()
    ) : ClientMessage()

    @Serializable
    @SerialName("login")
    data class Login(
        val username: String = "",
        val password: String = "",
        val characterName: String = ""
    ) : ClientMessage()

    @Serializable
    @SerialName("check_name")
    data class CheckName(
        val username: String = "",
        val characterName: String
    ) : ClientMessage()

    @Serializable
    @SerialName("move")
    data class Move(
        val direction: Direction
    ) : ClientMessage()

    @Serializable
    @SerialName("look")
    data object Look : ClientMessage()

    @Serializable
    @SerialName("say")
    data class Say(
        val message: String
    ) : ClientMessage()

    @Serializable
    @SerialName("ping")
    data object Ping : ClientMessage()

    @Serializable
    @SerialName("attack_toggle")
    data class AttackToggle(val enabled: Boolean) : ClientMessage()

    @Serializable
    @SerialName("select_target")
    data class SelectTarget(val npcId: String?) : ClientMessage()

    // Inventory
    @Serializable
    @SerialName("view_inventory")
    data object ViewInventory : ClientMessage()

    @Serializable
    @SerialName("equip_item")
    data class EquipItem(val itemId: String, val slot: String) : ClientMessage()

    @Serializable
    @SerialName("unequip_item")
    data class UnequipItem(val slot: String) : ClientMessage()

    @Serializable
    @SerialName("use_item")
    data class UseItem(val itemId: String) : ClientMessage()

    // Pickup
    @Serializable
    @SerialName("pickup_item")
    data class PickupItem(val itemId: String, val quantity: Int = 1) : ClientMessage()

    @Serializable
    @SerialName("pickup_coins")
    data class PickupCoins(val coinType: String) : ClientMessage()

    @Serializable
    @SerialName("sneak_toggle")
    data class SneakToggle(val enabled: Boolean) : ClientMessage()

    @Serializable
    @SerialName("use_skill")
    data class UseSkill(val skillId: String, val targetId: String? = null) : ClientMessage()

    @Serializable
    @SerialName("interact_trainer")
    data object InteractTrainer : ClientMessage()

    @Serializable
    @SerialName("train_level_up")
    data object TrainLevelUp : ClientMessage()

    @Serializable
    @SerialName("train_stat")
    data class TrainStat(val stat: String, val points: Int = 1) : ClientMessage()

    @Serializable
    @SerialName("allocate_trained_stats")
    data class AllocateTrainedStats(val stats: Stats) : ClientMessage()

    @Serializable
    @SerialName("cast_spell")
    data class CastSpell(val spellId: String, val targetId: String? = null) : ClientMessage()

    @Serializable
    @SerialName("interact_vendor")
    data object InteractVendor : ClientMessage()

    @Serializable
    @SerialName("buy_item")
    data class BuyItem(val itemId: String, val quantity: Int = 1) : ClientMessage()

    @Serializable
    @SerialName("sell_item")
    data class SellItem(val itemId: String, val quantity: Int = 1) : ClientMessage()

    @Serializable
    @SerialName("interact_feature")
    data class InteractFeature(val featureId: String) : ClientMessage()

    @Serializable
    @SerialName("drop_item")
    data class DropItem(val itemId: String, val quantity: Int = 1) : ClientMessage()

    @Serializable
    @SerialName("ready_spell")
    data class ReadySpell(val spellId: String? = null) : ClientMessage()

    @Serializable
    @SerialName("interact_crafter")
    data object InteractCrafter : ClientMessage()

    @Serializable
    @SerialName("interact_npc")
    data class InteractNpc(@SerialName("npc_id") val npcId: String) : ClientMessage()

    /**
     * Player's response to a [ServerMessage.PlaceItemPrompt] — names which inventory item
     * they're placing into the prompt's interactable. Phase 8 puzzle infra.
     */
    @Serializable
    @SerialName("place_item")
    data class PlaceItem(
        @SerialName("feature_id") val featureId: String,
        @SerialName("item_id") val itemId: String
    ) : ClientMessage()

    @Serializable
    @SerialName("answer_riddle")
    data class AnswerRiddle(
        @SerialName("feature_id") val featureId: String,
        val answer: String
    ) : ClientMessage()

    @Serializable
    @SerialName("make_choice")
    data class MakeChoice(
        @SerialName("feature_id") val featureId: String,
        @SerialName("choice_id") val choiceId: String
    ) : ClientMessage()

    @Serializable
    @SerialName("craft_item")
    data class CraftItem(val recipeId: String) : ClientMessage()

    // Handshake — sent in response to ServerHello
    @Serializable
    @SerialName("client_hello")
    data class ClientHello(
        val clientVersion: String,
        val protocolVersion: Int,
        val platformToken: String? = null
    ) : ClientMessage()

    // Platform auth — auto-login with verified platform session (no credentials needed)
    @Serializable
    @SerialName("platform_login")
    data object PlatformLogin : ClientMessage()

    // Platform auth — create character for platform user (no username/password)
    @Serializable
    @SerialName("platform_register")
    data class PlatformRegister(
        val characterName: String,
        val characterClass: String,
        val race: String = "",
        val gender: String = "neutral",
        val allocatedStats: Stats = Stats()
    ) : ClientMessage()

    // Guest play — ephemeral character, deleted on disconnect
    @Serializable
    @SerialName("guest_login")
    data class GuestLogin(
        val characterName: String,
        val characterClass: String,
        val race: String = "",
        val gender: String = "neutral",
        val allocatedStats: Stats = Stats()
    ) : ClientMessage()
}
