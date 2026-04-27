package com.neomud.server.game.trap

import com.neomud.server.game.GameConfig
import com.neomud.server.game.progression.Perception
import com.neomud.shared.model.Stats

/**
 * Pure resolution logic for trap detection, saves, and damage.
 *
 * Separated from TrapManager so the math is testable in isolation and so
 * player-triggered traps (via InteractCommand) and passive traps (via
 * TrapManager) share identical behavior.
 *
 * Save stats supported:
 *   AGILITY  — dodge save (typically `saveType=DODGE` for full avoid)
 *   TOUGHNESS — derived as (strength + willpower) / 2; physical/poison resist
 *               (typically `saveType=RESIST` for half damage)
 *
 * NeoMud has no HEALTH stat; what design docs call a "health resist" maps
 * to TOUGHNESS here.
 */
object TrapResolver {

    enum class SaveOutcome { FAIL, HALF, AVOID }
    enum class SaveType { DODGE, RESIST, NONE }

    /** Player rolled at-or-above the trap's perception DC → trap is detected, won't fire. */
    fun resolveDetection(perceptionRoll: Int, perceptionDC: Int): Boolean =
        perceptionDC > 0 && perceptionRoll >= perceptionDC

    /** Save stat → integer value. TOUGHNESS = (STR + WIL) / DIVISOR, no native stat. */
    fun saveStatValue(stats: Stats, saveStat: String): Int = when (saveStat.uppercase()) {
        "AGILITY" -> stats.agility
        "TOUGHNESS" -> (stats.strength + stats.willpower) / GameConfig.Trap.TOUGHNESS_DIVISOR
        else -> 0
    }

    /**
     * Save resolution. No save stat → always FAIL (full damage).
     * Roll = saveStatValue + level/2 + d20 (matches Perception.roll convention).
     * Outcome maps from saveType: DODGE → AVOID on success, RESIST → HALF on success.
     */
    fun resolveSave(
        stats: Stats,
        level: Int,
        saveStat: String,
        saveDC: Int,
        saveType: SaveType,
        random: () -> Int = { (1..GameConfig.Trap.DICE_SIZE).random() }
    ): SaveOutcome {
        if (saveStat.isBlank() || saveType == SaveType.NONE || saveDC <= 0) return SaveOutcome.FAIL
        val statValue = saveStatValue(stats, saveStat)
        if (statValue == 0) return SaveOutcome.FAIL
        val roll = statValue + level / GameConfig.Trap.LEVEL_DIVISOR + random()
        if (roll < saveDC) return SaveOutcome.FAIL
        return when (saveType) {
            SaveType.DODGE -> SaveOutcome.AVOID
            SaveType.RESIST -> SaveOutcome.HALF
            SaveType.NONE -> SaveOutcome.FAIL
        }
    }

    /** Apply save outcome to base damage. Half-damage floors to HALF_DAMAGE_FLOOR to avoid 0-damage hits feeling like nothing. */
    fun computeDamage(baseDamage: Int, outcome: SaveOutcome): Int = when (outcome) {
        SaveOutcome.AVOID -> 0
        SaveOutcome.HALF -> maxOf(GameConfig.Trap.HALF_DAMAGE_FLOOR, baseDamage / GameConfig.Trap.HALF_DAMAGE_DIVISOR)
        SaveOutcome.FAIL -> baseDamage
    }

    /** Convenience: roll perception against a trap DC using session's effective stats. */
    fun rollDetection(stats: Stats, level: Int, random: () -> Int = { (1..20).random() }): Int =
        Perception.roll(stats, level, random)
}
