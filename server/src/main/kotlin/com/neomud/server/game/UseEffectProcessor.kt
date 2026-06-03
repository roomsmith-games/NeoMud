package com.neomud.server.game

import com.neomud.shared.model.ActiveEffect
import com.neomud.shared.model.EffectType
import com.neomud.shared.model.Player

/**
 * Generic processor for item useEffect strings.
 *
 * Effect format: comma-separated effect tokens.
 *   heal:25           → restore 25 HP (capped at maxHp)
 *   mana:20           → restore 20 MP (capped at maxMp)
 *   hot:5:10          → heal-over-time, 5 HP per tick for 10 ticks
 *   buff:strength:3:20 → buff strength by 3 for 20 ticks
 *   buff:agility:3:20  → buff agility by 3 for 20 ticks
 *   buff:intellect:3:20
 *   buff:willpower:3:20
 *
 * Returns null if the effect string is empty or entirely unparseable.
 */
object UseEffectProcessor {

    data class Result(
        val updatedPlayer: Player,
        val newEffects: List<ActiveEffect>,
        val messages: List<String>,
        val cureDot: Boolean = false,
        val targetDamage: Int = 0
    )

    fun process(effectString: String, player: Player, itemName: String, effectiveMaxHp: Int = player.maxHp): Result? {
        if (effectString.isBlank()) return null

        var current = player
        val messages = mutableListOf<String>()
        val newEffects = mutableListOf<ActiveEffect>()
        var anyApplied = false
        var cureDot = false
        var targetDamage = 0

        for (token in effectString.split(',').map { it.trim() }) {
            val parts = token.split(':')
            val effectType = parts.getOrNull(0) ?: continue

            when (effectType) {
                "heal" -> {
                    val amount = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    val newHp = (current.currentHp + amount).coerceAtMost(effectiveMaxHp)
                    current = current.copy(currentHp = newHp)
                    messages.add("You use the $itemName and recover $amount HP.")
                    anyApplied = true
                }
                "mana" -> {
                    val amount = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    val newMp = (current.currentMp + amount).coerceAtMost(current.maxMp)
                    current = current.copy(currentMp = newMp)
                    messages.add("You use the $itemName and recover $amount MP.")
                    anyApplied = true
                }
                "hot" -> {
                    val magnitude = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    val ticks = parts.getOrNull(2)?.toIntOrNull() ?: continue
                    newEffects.add(ActiveEffect(
                        name = itemName,
                        type = EffectType.HEAL_OVER_TIME,
                        remainingTicks = ticks,
                        magnitude = magnitude
                    ))
                    messages.add("You use the $itemName. A warm glow surrounds you.")
                    anyApplied = true
                }
                "buff" -> {
                    val stat = parts.getOrNull(1) ?: continue
                    val magnitude = parts.getOrNull(2)?.toIntOrNull() ?: continue
                    val ticks = parts.getOrNull(3)?.toIntOrNull() ?: continue
                    val buffType = STAT_TO_EFFECT_TYPE[stat] ?: continue
                    newEffects.add(ActiveEffect(
                        name = "$itemName ($stat)",
                        type = buffType,
                        remainingTicks = ticks,
                        magnitude = magnitude
                    ))
                    messages.add("You use the $itemName. You feel your $stat surge!")
                    anyApplied = true
                }
                "damage" -> {
                    val amount = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    targetDamage += amount
                    messages.add("You unleash the $itemName!")
                    anyApplied = true
                }
                "buff_damage" -> {
                    val magnitude = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    val ticks = parts.getOrNull(2)?.toIntOrNull() ?: continue
                    newEffects.add(ActiveEffect(
                        name = "$itemName (damage)",
                        type = EffectType.BUFF_DAMAGE,
                        remainingTicks = ticks,
                        magnitude = magnitude
                    ))
                    messages.add("You use the $itemName. Your attacks surge with power!")
                    anyApplied = true
                }
                "buff_max_hp" -> {
                    val magnitude = parts.getOrNull(1)?.toIntOrNull() ?: continue
                    val ticks = parts.getOrNull(2)?.toIntOrNull() ?: continue
                    newEffects.add(ActiveEffect(
                        name = "$itemName (max HP)",
                        type = EffectType.BUFF_MAX_HP,
                        remainingTicks = ticks,
                        magnitude = magnitude
                    ))
                    messages.add("You use the $itemName. You feel hardier!")
                    anyApplied = true
                }
                "cure_dot" -> {
                    cureDot = true
                    messages.add("You use the $itemName. The poison is purged from your body!")
                    anyApplied = true
                }
            }
        }

        return if (anyApplied) Result(current, newEffects, messages, cureDot, targetDamage) else null
    }

    fun isHealOnly(effectString: String): Boolean {
        val effects = effectString.split(',').map { it.trim().split(':').first() }
        return effects.isNotEmpty() && effects.all { it == "heal" }
    }

    private val STAT_TO_EFFECT_TYPE = mapOf(
        "strength" to EffectType.BUFF_STRENGTH,
        "agility" to EffectType.BUFF_AGILITY,
        "intellect" to EffectType.BUFF_INTELLECT,
        "willpower" to EffectType.BUFF_WILLPOWER
    )
}
