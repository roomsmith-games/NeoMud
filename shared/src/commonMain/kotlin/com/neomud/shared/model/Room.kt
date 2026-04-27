package com.neomud.shared.model

import kotlinx.serialization.Serializable

typealias RoomId = String

@Serializable
data class RoomEffect(
    val type: String,
    val value: Int = 0,
    val message: String = "",
    val sound: String = ""
)

@Serializable
data class RoomInteractable(
    val id: String,
    val label: String,
    val description: String,
    val failureMessage: String = "",
    val icon: String = "",
    val actionType: String,
    val actionData: Map<String, String> = emptyMap(),
    val difficulty: Int = 0,
    val difficultyCheck: String = "",
    val perceptionDC: Int = 0,
    val cooldownTicks: Int = 0,
    val resetTicks: Int = 0,
    val sound: String = "",
    /**
     * When this interactable fires. Defaults to `ON_ACTION` (player must
     * explicitly interact). `ON_ENTER` is a passive trap fired by
     * TrapManager on room entry. Future: `ON_TICK`, `ON_ROOM_ACTION`.
     */
    val triggerType: String = "ON_ACTION"
)

@Serializable
data class Room(
    val id: RoomId,
    val name: String,
    val description: String,
    val exits: Map<Direction, RoomId>,
    val zoneId: String,
    val x: Int,
    val y: Int,
    val backgroundImage: String = "",
    val effects: List<RoomEffect> = emptyList(),
    val bgm: String = "",
    val departSound: String = "",
    val lockedExits: Map<Direction, Int> = emptyMap(),
    val interactables: List<RoomInteractable> = emptyList(),
    val unpickableExits: Set<Direction> = emptySet()
)

@Serializable
data class MapRoom(
    val id: RoomId,
    val name: String,
    val x: Int,
    val y: Int,
    val exits: Map<Direction, RoomId>,
    val hasPlayers: Boolean = false,
    val hasNpcs: Boolean = false,
    val backgroundImage: String = "",
    val zoneId: String = "",
    val lockedExits: Set<Direction> = emptySet(),
    val hiddenExits: Set<Direction> = emptySet()
)
