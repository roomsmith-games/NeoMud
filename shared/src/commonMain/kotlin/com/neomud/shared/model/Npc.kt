package com.neomud.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class Npc(
    val id: String,
    val name: String,
    val description: String,
    val currentRoomId: RoomId,
    val behaviorType: String,
    val hostile: Boolean = false,
    val currentHp: Int = 0,
    val maxHp: Int = 0,
    val attackSound: String = "",
    val missSound: String = "",
    val deathSound: String = "",
    val interactSound: String = "",
    val exitSound: String = "",
    val activeEffects: List<ActiveEffect> = emptyList(),
    val spriteOverride: String = ""
)
