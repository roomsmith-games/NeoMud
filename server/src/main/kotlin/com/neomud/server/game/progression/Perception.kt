package com.neomud.server.game.progression

import com.neomud.shared.model.Stats

/**
 * Derived stat used for trap detection rolls and other passive perception checks.
 *
 * Formula and roll convention chosen to match the existing InteractCommand
 * difficulty check pattern (`stat + level/2 + d20`), so trap DCs and
 * interactable DCs are intuitively comparable.
 */
object Perception {
    fun compute(stats: Stats): Int = (stats.intellect + stats.agility) / 2

    fun roll(stats: Stats, level: Int, random: () -> Int = { (1..20).random() }): Int =
        compute(stats) + level / 2 + random()
}
