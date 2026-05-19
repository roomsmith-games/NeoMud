package com.neomud.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PartyMember(
    val name: String,
    val characterClass: String,
    val race: String = "",
    val level: Int,
    val currentHp: Int,
    val maxHp: Int,
    val currentMp: Int = 0,
    val maxMp: Int = 0,
    val roomId: String = "",
    val isLeader: Boolean = false
)
