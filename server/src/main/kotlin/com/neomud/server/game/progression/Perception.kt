package com.neomud.server.game.progression

import com.neomud.server.game.GameConfig
import com.neomud.shared.model.Stats

/**
 * Derived stat used for trap detection rolls and other passive perception checks.
 *
 * Formula and roll convention chosen to match the existing InteractCommand
 * difficulty check pattern (`stat + level/2 + d20`), so trap DCs and
 * interactable DCs are intuitively comparable. Constants live in
 * `GameConfig.Perception` so the tuning knobs can be adjusted without
 * touching this code.
 */
object Perception {
    fun compute(stats: Stats): Int =
        (stats.intellect + stats.agility) / GameConfig.Perception.STAT_DIVISOR

    fun roll(
        stats: Stats,
        level: Int,
        random: () -> Int = { (1..GameConfig.Perception.DICE_SIZE).random() }
    ): Int =
        compute(stats) + level / GameConfig.Perception.LEVEL_DIVISOR + random()
}
