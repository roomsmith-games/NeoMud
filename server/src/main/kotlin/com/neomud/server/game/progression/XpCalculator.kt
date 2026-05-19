package com.neomud.server.game.progression

import com.neomud.server.game.GameConfig
import kotlin.math.pow
import kotlin.math.roundToLong

object XpCalculator {

    fun xpForLevel(level: Int): Long {
        return (GameConfig.Progression.XP_BASE_MULTIPLIER * level.toDouble().pow(GameConfig.Progression.XP_CURVE_EXPONENT)).roundToLong().coerceAtLeast(GameConfig.Progression.XP_MINIMUM)
    }

    fun xpForKill(npcLevel: Int, playerLevel: Int, baseXp: Long): Long {
        val diff = npcLevel - playerLevel
        val modifier = when {
            diff >= 5 -> GameConfig.Progression.XP_MOD_5_ABOVE
            diff >= 3 -> GameConfig.Progression.XP_MOD_3_ABOVE
            diff >= 1 -> GameConfig.Progression.XP_MOD_1_ABOVE
            diff == 0 -> GameConfig.Progression.XP_MOD_SAME
            diff >= -2 -> GameConfig.Progression.XP_MOD_2_BELOW
            diff >= -4 -> GameConfig.Progression.XP_MOD_4_BELOW
            else -> GameConfig.Progression.XP_MOD_5_PLUS_BELOW
        }
        return (baseXp * modifier).roundToLong().coerceAtLeast(1)
    }

    fun adjustedXpForLevel(level: Int, raceXpMod: Double, classXpMod: Double): Long {
        return (xpForLevel(level) * raceXpMod * classXpMod).roundToLong().coerceAtLeast(GameConfig.Progression.XP_MINIMUM)
    }

    fun cpForLevel(level: Int): Int = when {
        level <= GameConfig.Progression.CP_TIER_2_LEVEL -> GameConfig.Progression.CP_PER_LEVEL_LOW
        level <= GameConfig.Progression.CP_TIER_3_LEVEL -> GameConfig.Progression.CP_PER_LEVEL_MID
        else -> GameConfig.Progression.CP_PER_LEVEL_HIGH
    }

    fun isReadyToLevel(currentXp: Long, xpToNextLevel: Long, level: Int): Boolean {
        return level < GameConfig.Progression.MAX_LEVEL && currentXp >= xpToNextLevel
    }

    fun partyXpPerMember(baseXp: Long, membersInRoom: Int): Long {
        if (membersInRoom <= 1) return baseXp
        val n = membersInRoom.toDouble()
        val modifier = 1.0 / n + GameConfig.Party.XP_BONUS_PER_MEMBER * (n - 1)
        return (baseXp * modifier).roundToLong().coerceAtLeast(1)
    }
}
