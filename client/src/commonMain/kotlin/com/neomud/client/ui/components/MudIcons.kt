package com.neomud.client.ui.components

import neomud.client.generated.resources.Res
import neomud.client.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource

/**
 * Centralized icon registry for the MUD client.
 * All UI icons are generated sprite WebPs stored as DrawableResources.
 */
object MudIcons {

    // ── Toolbar ──
    val Inventory: DrawableResource = Res.drawable.icon_inventory
    val Equipment: DrawableResource = Res.drawable.icon_equipment
    val Map: DrawableResource = Res.drawable.icon_map
    val Settings: DrawableResource = Res.drawable.icon_settings

    // ── Skills ──
    val Bash: DrawableResource = Res.drawable.icon_bash
    val Kick: DrawableResource = Res.drawable.icon_kick
    val Sneak: DrawableResource = Res.drawable.icon_sneak
    val Meditate: DrawableResource = Res.drawable.icon_meditate
    val Track: DrawableResource = Res.drawable.icon_track
    val PickLock: DrawableResource = Res.drawable.icon_picklock
    val Rest: DrawableResource = Res.drawable.icon_rest
    val Attack: DrawableResource = Res.drawable.icon_attack

    // ── Status Effects ──
    val EffectPoison: DrawableResource = Res.drawable.icon_effect_poison
    val EffectHealOverTime: DrawableResource = Res.drawable.icon_effect_heal_over_time
    val EffectBuffStrength: DrawableResource = Res.drawable.icon_effect_buff_strength
    val EffectBuffAgility: DrawableResource = Res.drawable.icon_effect_buff_agility
    val EffectBuffIntellect: DrawableResource = Res.drawable.icon_effect_buff_intellect
    val EffectBuffWillpower: DrawableResource = Res.drawable.icon_effect_buff_willpower
    val EffectHaste: DrawableResource = Res.drawable.icon_effect_haste
    val EffectDamage: DrawableResource = Res.drawable.icon_effect_damage
    val EffectManaRegen: DrawableResource = Res.drawable.icon_effect_mana_regen
    val EffectManaDrain: DrawableResource = Res.drawable.icon_effect_mana_drain
    val EffectBuffDamage: DrawableResource = Res.drawable.icon_buff_damage
    val EffectBuffMaxHp: DrawableResource = Res.drawable.icon_buff_max_hp

    // ── State Indicators (aliases) ──
    val Hidden: DrawableResource = Res.drawable.icon_sneak
    val Meditating: DrawableResource = Res.drawable.icon_meditate

    // ── Room NPCs ──
    val Vendor: DrawableResource = Res.drawable.icon_vendor
    val Trainer: DrawableResource = Res.drawable.icon_trainer
    val Crafter: DrawableResource = Res.drawable.icon_crafter

    // ── Toolbar (cont.) ──
    val CharacterSheet: DrawableResource = Res.drawable.icon_character_sheet
    val Help: DrawableResource = Res.drawable.icon_help

    // ── Room Overlay / Interactables ──
    val ExitOpen: DrawableResource = Res.drawable.icon_exit_open
    val TreasureDrop: DrawableResource = Res.drawable.icon_treasure_drop
    val MonsterSpawn: DrawableResource = Res.drawable.icon_monster_spawn
    val RoomEffect: DrawableResource = Res.drawable.icon_room_effect
    val Teleport: DrawableResource = Res.drawable.icon_teleport
    val InteractDefault: DrawableResource = Res.drawable.icon_interact

    // ── NPC Context Menu (aliases) ──
    val TrackNpc: DrawableResource = Res.drawable.icon_track
    val KickNpc: DrawableResource = Res.drawable.icon_kick

    // ── Spell Schools ──
    val SchoolMage: DrawableResource = Res.drawable.icon_school_mage
    val SchoolPriest: DrawableResource = Res.drawable.icon_school_priest
    val SchoolDruid: DrawableResource = Res.drawable.icon_school_druid
    val SchoolKai: DrawableResource = Res.drawable.icon_school_kai
    val SchoolBard: DrawableResource = Res.drawable.icon_school_bard
    val SchoolDefault: DrawableResource = Res.drawable.icon_school_default

    // ── Per-Spell Icons ──
    // Mage
    val SpellMagicMissile: DrawableResource = Res.drawable.icon_spell_magic_missile
    val SpellArcaneShield: DrawableResource = Res.drawable.icon_spell_arcane_shield
    val SpellFrostBolt: DrawableResource = Res.drawable.icon_spell_frost_bolt
    val SpellFireball: DrawableResource = Res.drawable.icon_spell_fireball
    // Priest
    val SpellSmite: DrawableResource = Res.drawable.icon_spell_smite
    val SpellHolySmite: DrawableResource = Res.drawable.icon_spell_holy_smite
    val SpellMinorHeal: DrawableResource = Res.drawable.icon_spell_minor_heal
    val SpellBlessing: DrawableResource = Res.drawable.icon_spell_blessing
    val SpellCureWounds: DrawableResource = Res.drawable.icon_spell_cure_wounds
    val SpellDivineLight: DrawableResource = Res.drawable.icon_spell_divine_light
    // Druid
    val SpellThornStrike: DrawableResource = Res.drawable.icon_spell_thorn_strike
    val SpellHealingTouch: DrawableResource = Res.drawable.icon_spell_healing_touch
    val SpellPoisonCloud: DrawableResource = Res.drawable.icon_spell_poison_cloud
    val SpellNaturesWrath: DrawableResource = Res.drawable.icon_spell_natures_wrath
    // Kai
    val SpellInnerFire: DrawableResource = Res.drawable.icon_spell_inner_fire
    val SpellChiStrike: DrawableResource = Res.drawable.icon_spell_chi_strike
    val SpellKiBlast: DrawableResource = Res.drawable.icon_spell_ki_blast
    val SpellDiamondBody: DrawableResource = Res.drawable.icon_spell_diamond_body
    // Bard
    val SpellCuttingWords: DrawableResource = Res.drawable.icon_spell_cutting_words
    val SpellInspire: DrawableResource = Res.drawable.icon_spell_inspire
    val SpellSoothingSong: DrawableResource = Res.drawable.icon_spell_soothing_song
    val SpellDiscord: DrawableResource = Res.drawable.icon_spell_discord
    val SpellRallyingCry: DrawableResource = Res.drawable.icon_spell_rallying_cry

    /** Map skill IDs to their icons */
    fun skillIcon(skillId: String): DrawableResource = when (skillId) {
        "BASH" -> Bash
        "KICK" -> Kick
        "SNEAK" -> Sneak
        "MEDITATE" -> Meditate
        "TRACK" -> Track
        "PICK_LOCK" -> PickLock
        "REST" -> Rest
        else -> Attack
    }

    /** Map spell IDs to their unique icons */
    fun spellIcon(spellId: String): DrawableResource = when (spellId) {
        "MAGIC_MISSILE" -> SpellMagicMissile
        "ARCANE_SHIELD" -> SpellArcaneShield
        "FROST_BOLT" -> SpellFrostBolt
        "FIREBALL" -> SpellFireball
        "SMITE" -> SpellSmite
        "HOLY_SMITE" -> SpellHolySmite
        "MINOR_HEAL" -> SpellMinorHeal
        "BLESSING" -> SpellBlessing
        "CURE_WOUNDS" -> SpellCureWounds
        "DIVINE_LIGHT" -> SpellDivineLight
        "THORN_STRIKE" -> SpellThornStrike
        "HEALING_TOUCH" -> SpellHealingTouch
        "POISON_CLOUD" -> SpellPoisonCloud
        "NATURES_WRATH" -> SpellNaturesWrath
        "INNER_FIRE" -> SpellInnerFire
        "CHI_STRIKE" -> SpellChiStrike
        "KI_BLAST" -> SpellKiBlast
        "DIAMOND_BODY" -> SpellDiamondBody
        "CUTTING_WORDS" -> SpellCuttingWords
        "INSPIRE" -> SpellInspire
        "SOOTHING_SONG" -> SpellSoothingSong
        "DISCORD" -> SpellDiscord
        "RALLYING_CRY" -> SpellRallyingCry
        else -> SchoolDefault
    }

    /** Map spell school to its icon */
    fun schoolIcon(school: String): DrawableResource = when (school) {
        "mage" -> SchoolMage
        "priest" -> SchoolPriest
        "druid" -> SchoolDruid
        "kai" -> SchoolKai
        "bard" -> SchoolBard
        else -> SchoolDefault
    }
}
