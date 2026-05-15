package com.neomud.server.game

object GameConfig {
    object Server {
        const val PORT = 8080
    }
    object WorldFormat {
        const val CURRENT_FORMAT_VERSION = 1
    }
    object Tick {
        const val INTERVAL_MS = 1500L
    }
    object RateLimit {
        const val MAX_MESSAGES_PER_SECOND = 10
        const val BURST_CAPACITY = 20
    }
    object Security {
        const val MAX_FAILED_LOGINS = 5
        const val LOGIN_LOCKOUT_MS = 60_000L // 1 minute lockout after max failures
        const val MAX_CONNECTIONS_PER_IP = 5
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 64
        const val MAX_GUEST_CREATIONS_PER_IP_PER_HOUR = 10
    }
    object Combat {
        const val GRACE_TICKS = 2
        /** Newly spawned hostile NPCs wait this many ticks before attacking. */
        const val NPC_SPAWN_GRACE_TICKS = 2
        const val MIN_HIT_CHANCE = 5
        const val MAX_HIT_CHANCE = 95
        const val BASE_HIT_CHANCE = 50
        const val ACCURACY_STAT_DIVISOR = 2
        const val ACCURACY_LEVEL_MULTIPLIER = 2
        const val NPC_ACCURACY_LEVEL_MULTIPLIER = 2
        const val DEFENSE_AGI_DIVISOR = 2
        const val DEFENSE_ARMOR_DIVISOR = 2
        const val ARMOR_DAMAGE_REDUCTION_DIVISOR = 2
        const val NPC_DEFENSE_LEVEL_MULTIPLIER = 1
        const val DODGE_MAX_CHANCE = 0.30
        const val DODGE_STAT_DIVISOR = 80.0
        const val NPC_EVASION_DIVISOR = 100.0
        const val PARRY_MAX_CHANCE = 0.30
        const val PARRY_STAT_DIVISOR = 80.0
        const val PARRY_REDUCTION_BASE = 2
        const val PARRY_REDUCTION_STR_DIVISOR = 20
        const val MELEE_STR_DIVISOR = 3
        const val UNARMED_DAMAGE_RANGE = 3
        const val CRIT_DAMAGE_MULTIPLIER = 1.5
        const val BACKSTAB_DAMAGE_MULTIPLIER = 3
        const val NPC_VARIANCE_DIVISOR = 3
        const val GUARD_HIT_CHANCE = 85 // Guards are strong but not infallible (out of 100)
    }
    object Thresholds {
        // STR thresholds
        const val STR_90_MELEE_DAMAGE = 5
        const val STR_90_HIT_BONUS = 3
        const val STR_75_MELEE_DAMAGE = 3
        const val STR_75_HIT_BONUS = 2
        const val STR_60_MELEE_DAMAGE = 2
        const val STR_60_HIT_BONUS = 1
        const val STR_40_MELEE_DAMAGE = 1
        // AGI thresholds
        const val AGI_90_CRIT_CHANCE = 0.05
        // INT thresholds
        const val INT_75_CRIT_CHANCE = 0.05
        // Health thresholds
        const val HEA_90_HP_BONUS = 50
        const val HEA_75_HP_BONUS = 25
        const val HEA_60_HP_BONUS = 15
        // Willpower thresholds
        const val WIL_90_MP_BONUS = 25
        const val WIL_75_MP_BONUS = 15
        const val WIL_60_MP_BONUS = 10
    }
    object Progression {
        const val MAX_LEVEL = 30
        const val XP_BASE_MULTIPLIER = 100
        const val XP_CURVE_EXPONENT = 2.2
        const val XP_MINIMUM = 100L
        const val XP_MOD_5_ABOVE = 1.5
        const val XP_MOD_3_ABOVE = 1.25
        const val XP_MOD_1_ABOVE = 1.1
        const val XP_MOD_SAME = 1.0
        const val XP_MOD_2_BELOW = 0.75
        const val XP_MOD_4_BELOW = 0.5
        const val XP_MOD_5_PLUS_BELOW = 0.25
        const val CP_PER_LEVEL_LOW = 10
        const val CP_PER_LEVEL_MID = 15
        const val CP_PER_LEVEL_HIGH = 20
        const val CP_TIER_2_LEVEL = 10
        const val CP_TIER_3_LEVEL = 20
        const val DEATH_XP_LOSS_PERCENT = 0.05
        const val DEATH_XP_PENALTY_MIN_LEVEL = 2  // No XP penalty at level 1 (newbie protection)
    }
    object PlayerCreation {
        const val HP_HEALTH_DIVISOR = 10
        const val HP_HEALTH_MULTIPLIER = 4
        const val MP_WILLPOWER_DIVISOR = 10
        const val MP_WILLPOWER_MULTIPLIER = 2
    }
    object Skills {
        const val BASH_DAMAGE_RANGE = 4
        const val BASH_STUN_CHANCE = 50
        const val BASH_STUN_TICKS = 2
        const val BASH_COOLDOWN_TICKS = 3
        const val KICK_DAMAGE_RANGE = 4
        const val KICK_COOLDOWN_TICKS = 2
        const val KICK_KNOCKBACK_STUN_TICKS = 2
        const val KICK_STR_DIVISOR = 4
        const val KICK_AGI_DIVISOR = 4
        const val SPELL_POWER_STAT_DIVISOR = 2
        const val SPELL_POWER_LEVEL_DIVISOR = 1
        const val SPELL_POWER_DICE_SIZE = 8
        /** Heal/HoT spells use a smaller stat divisor for tighter scaling at low levels */
        const val HEAL_STAT_DIVISOR = 4
        const val DOT_INITIAL_DAMAGE_DIVISOR = 2
        const val PICK_LOCK_COOLDOWN_TICKS = 3
        const val PICK_LOCK_DICE_SIZE = 20
        const val PICK_LOCK_INT_DIVISOR = 2
    }
    object Stealth {
        const val DC_BASE = 10
        const val DC_WIL_DIVISOR = 2
        const val DC_LEVEL_DIVISOR = 2
        const val PERCEPTION_DICE_SIZE = 20
        const val PERCEPTION_INT_DIVISOR = 2
        const val PERCEPTION_LEVEL_DIVISOR = 2
        const val PERCEPTION_SKILL_BONUS = 3
        const val SNEAK_DIFFICULTY = 15
    }
    object Perception {
        // Derived stat: (INT + AGI) / DIVISOR. Same shape as Stealth.PERCEPTION_INT_DIVISOR
        // but kept separate because Perception is the player-facing trap-detection stat
        // and its tuning lever should be independently adjustable from the stealth subsystem.
        const val STAT_DIVISOR = 2
        const val LEVEL_DIVISOR = 2
        const val DICE_SIZE = 20
    }
    object Trap {
        // Save roll: stat + level/DIVISOR + d(DICE_SIZE), matching the InteractCommand
        // difficulty-check convention so trap DCs and interactable DCs are intuitively
        // comparable.
        const val LEVEL_DIVISOR = 2
        const val DICE_SIZE = 20
        // Derived TOUGHNESS save stat = (STR + WIL) / DIVISOR. Independent knob from
        // Perception so the two derived stats can be tuned separately.
        const val TOUGHNESS_DIVISOR = 2
        // Successful RESIST save halves damage; floor at 1 to keep hits non-zero.
        const val HALF_DAMAGE_DIVISOR = 2
        const val HALF_DAMAGE_FLOOR = 1
    }
    object Meditation {
        const val WIL_DIVISOR = 10
        const val RESTORE_BASE = 2
    }
    object Rest {
        const val HEALTH_DIVISOR = 10
        const val RESTORE_BASE = 1
    }
    object Npc {
        const val WANDER_MOVE_TICKS = 15
        const val PATROL_MOVE_TICKS = 20
        const val PURSUIT_MAX_TICKS = 40
        const val PURSUIT_MOVE_TICKS = 5
        const val PURSUIT_LOST_TRAIL_TICKS = 3
    }
    object StarterEquipment {
        const val STARTING_COPPER = 25
        const val ARMOR_ITEM_ID = "item:leather_chest"
        const val ARMOR_SLOT = "chest"

        private val SWORD_CLASSES = setOf("WARRIOR", "PALADIN", "WITCHHUNTER", "CLERIC", "WARLOCK")
        private val DAGGER_CLASSES = setOf("THIEF", "NINJA", "MISSIONARY", "BARD", "GYPSY")
        private val STAFF_CLASSES = setOf("MAGE", "DRUID", "PRIEST", "MYSTIC")
        private val BOW_CLASSES = setOf("RANGER")

        fun weaponForClass(classId: String): String {
            val upper = classId.uppercase()
            return when {
                upper in SWORD_CLASSES -> "item:iron_sword"
                upper in DAGGER_CLASSES -> "item:rustic_dagger"
                upper in STAFF_CLASSES -> "item:wooden_staff"
                upper in BOW_CLASSES -> "item:short_bow"
                else -> "item:rustic_dagger"
            }
        }

        private fun weaponCategory(classId: String): String {
            val upper = classId.uppercase()
            return when {
                upper in SWORD_CLASSES -> "sword"
                upper in DAGGER_CLASSES -> "dagger"
                upper in STAFF_CLASSES -> "staff"
                upper in BOW_CLASSES -> "bow"
                else -> "dagger"
            }
        }

        fun resolveWeapon(classId: String, itemCatalog: com.neomud.server.world.ItemCatalog): String? {
            val defaultId = weaponForClass(classId)
            if (itemCatalog.getItem(defaultId) != null) return defaultId
            val category = weaponCategory(classId)
            return itemCatalog.getAllItems()
                .filter { it.slot == "weapon" && it.levelRequirement <= 1 && it.value > 0 }
                .filter { it.name.lowercase().contains(category) }
                .minByOrNull { it.value }?.id
                ?: itemCatalog.getAllItems()
                    .filter { it.slot == "weapon" && it.levelRequirement <= 1 && it.value > 0 }
                    .minByOrNull { it.value }?.id
        }

        fun resolveArmor(itemCatalog: com.neomud.server.world.ItemCatalog): String? {
            if (itemCatalog.getItem(ARMOR_ITEM_ID) != null) return ARMOR_ITEM_ID
            return itemCatalog.getAllItems()
                .filter { it.slot == ARMOR_SLOT && it.levelRequirement <= 1 && it.value > 0 }
                .minByOrNull { it.value }?.id
        }
    }
    object Vendor {
        const val SELL_BASE_PERCENT = 50
        const val SELL_CHARM_SCALE = 49 // charm contribution: charm * scale / 100
        const val SELL_HAGGLE_BONUS_SCALE = 10
        const val SELL_MAX_PERCENT = 99
        const val BUY_HAGGLE_MAX_DISCOUNT = 15 // max charm-based buy discount percent
        const val MAX_BUY_QUANTITY = 99 // max items per buy transaction
    }
    object Shutdown {
        const val DEFAULT_DELAY_SECONDS = 60
        val WARNING_AT_SECONDS = listOf(60, 30, 10, 5, 0)
    }
    object GroundItems {
        const val EXPIRY_MS = 600_000L // 10 minutes
        const val MAX_ITEMS_PER_ROOM = 50
    }
    object Tutorial {
        const val TOAST_DISPLAY_MS = 8_000L
    }
    object Trails {
        const val MAX_ENTRIES_PER_ROOM = 20
        const val LIFETIME_MS = 90_000L
        const val STALENESS_PENALTY_MAX = 5
        const val TRACK_BASE_DIFFICULTY = 13
        const val TRACK_HIDDEN_EXIT_BONUS = 5
    }
}
