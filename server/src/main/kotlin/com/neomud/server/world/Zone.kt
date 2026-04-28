package com.neomud.server.world

import com.neomud.shared.model.CoinDrop
import com.neomud.shared.model.Direction
import com.neomud.shared.model.LootEntry
import com.neomud.shared.model.RoomEffect
import com.neomud.shared.model.RoomId
import com.neomud.shared.model.RoomInteractable
import kotlinx.serialization.Serializable

@Serializable
data class SpawnConfig(
    val maxEntities: Int = 0,
    val maxPerRoom: Int = 0,
    val rateTicks: Int = 0
)

@Serializable
data class ZoneData(
    val id: String,
    val name: String,
    val description: String,
    val safe: Boolean = true,
    val rooms: List<RoomData>,
    val npcs: List<NpcData> = emptyList(),
    val spawnConfig: SpawnConfig = SpawnConfig(),
    val spawnRoom: String? = null,
    val bgm: String = "",
    val imageStyle: String = "",
    val imageNegativePrompt: String = ""
)

@Serializable
data class HiddenExitData(
    val perceptionDC: Int,
    val lockDifficulty: Int = 0,
    val hiddenResetTicks: Int = 0,
    val lockResetTicks: Int = 0
)

@Serializable
data class RoomData(
    val id: RoomId,
    val name: String,
    val description: String,
    val x: Int,
    val y: Int,
    val exits: Map<Direction, RoomId>,
    val backgroundImage: String = "",
    val healPerTick: Int = 0,
    val effects: List<RoomEffect> = emptyList(),
    val bgm: String = "",
    val departSound: String = "",
    val lockedExits: Map<Direction, Int> = emptyMap(),
    val lockResetTicks: Map<Direction, Int> = emptyMap(),
    val hiddenExits: Map<Direction, HiddenExitData> = emptyMap(),
    val imagePrompt: String = "",
    val imageStyle: String = "",
    val imageNegativePrompt: String = "",
    val imageWidth: Int = 1024,
    val imageHeight: Int = 576,
    val interactables: List<RoomInteractable> = emptyList(),
    val unpickableExits: Set<Direction> = emptySet(),
    val maxHostileNpcs: Int? = null
)

@Serializable
data class TrainerConfig(
    val tier: Int = 1,
    val maxLevel: Int,
    val maxStatValue: Int,
    val nextTierTrainerName: String? = null,
    val nextTierLocationHint: String? = null
)

@Serializable
data class NpcData(
    val id: String,
    val name: String,
    val description: String,
    val startRoomId: RoomId,
    val behaviorType: String,
    val patrolRoute: List<RoomId> = emptyList(),
    val hostile: Boolean = false,
    val maxHp: Int = 0,
    val damage: Int = 0,
    val level: Int = 1,
    val perception: Int = 0,
    val xpReward: Long = 0,
    val vendorItems: List<String> = emptyList(),
    val accuracy: Int = 0,
    val defense: Int = 0,
    val evasion: Int = 0,
    val agility: Int = 10,
    val attackSound: String = "",
    val missSound: String = "",
    val deathSound: String = "",
    val interactSound: String = "",
    val exitSound: String = "",
    val lootItems: List<LootEntry> = emptyList(),
    val coinDrop: CoinDrop? = null,
    val spawnPoints: List<RoomId> = emptyList(),
    val crafterRecipes: List<String> = emptyList(),
    val trainerConfig: TrainerConfig? = null,
    val imagePrompt: String = "",
    val imageStyle: String = "",
    val imageNegativePrompt: String = "",
    val imageWidth: Int = 384,
    val imageHeight: Int = 512
)
