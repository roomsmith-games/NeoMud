package com.neomud.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class SpellType {
    DAMAGE, HEAL, BUFF, DOT, HOT
}

@Serializable
enum class TargetType {
    ENEMY, SELF, ALLY, PARTY_ROOM
}

@Serializable
data class SpellDef(
    val id: String,
    val name: String,
    val description: String,
    val school: String,
    val spellType: SpellType,
    val manaCost: Int,
    val cooldownTicks: Int = 0,
    val levelRequired: Int = 1,
    val primaryStat: String = "intellect",
    val basePower: Int = 0,
    val schoolLevel: Int = 1,
    val targetType: TargetType = TargetType.ENEMY,
    val effectType: String = "",
    val effectDuration: Int = 0,
    val tickPower: Int = 0,
    val castMessage: String = "",
    val castSound: String = "",
    val impactSound: String = "",
    val missSound: String = ""
)
