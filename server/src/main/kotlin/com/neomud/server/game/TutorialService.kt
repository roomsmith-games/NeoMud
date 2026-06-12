package com.neomud.server.game

import com.neomud.server.persistence.repository.DiscoveryRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.WorldManifest
import com.neomud.shared.protocol.ServerMessage
import org.slf4j.LoggerFactory

/**
 * Centralized tutorial service. Holds all tutorial content definitions,
 * handles throttling, queueing, and persistence of tutorial seen state.
 */
class TutorialService(
    private val discoveryRepository: DiscoveryRepository,
    private val classCatalog: ClassCatalog,
    private val worldManifest: WorldManifest? = null
) {
    private val logger = LoggerFactory.getLogger(TutorialService::class.java)

    data class TutorialDef(
        val key: String,
        val title: String,
        val content: String,
        val blocking: Boolean,
        val targetElement: String? = null
    )

    private val tutorials: Map<String, TutorialDef> = mapOf(
        "welcome" to TutorialDef(
            key = "welcome",
            title = "Welcome to NeoMud!",
            content = "Greetings, adventurer!\n\n" +
                "Use the directional pad to move between rooms. " +
                "Tap hostile NPCs to select a target, then toggle attack mode (crossed swords) to fight.\n\n" +
                "Open the Adventurer's Tome (\u2753) in the toolbar for a full guide to all game systems.\n\n" +
                "May your blade stay sharp and your mana never run dry!",
            blocking = true,

        ),
        "tut_hostile_npc" to TutorialDef(
            key = "tut_hostile_npc",
            title = "Hostile Creatures Nearby",
            content = "Tap an NPC to target it, then tap the crossed swords to fight.",
            blocking = false,
            targetElement = "npc_sprites",
        ),
        "tut_low_hp" to TutorialDef(
            key = "tut_low_hp",
            title = "Health Low!",
            content = "Flee to a safe room and Rest to recover, or use a healing potion.",
            blocking = false,

        ),
        "tut_first_kill" to TutorialDef(
            key = "tut_first_kill",
            title = "First Kill!",
            content = "Loot and coins drop on the ground \u2014 tap them in the sprite area to pick up.",
            blocking = false,
            targetElement = "loot_sidebar",

        ),
        "tut_vendor" to TutorialDef(
            key = "tut_vendor",
            title = "Merchant Available",
            content = "A vendor is present in this area. " +
                "Tap the shop bag icon in your toolbar to browse their wares.\n\n" +
                "You can buy better equipment and sell items you no longer need. " +
                "Higher Charm stats may get you better prices!",
            blocking = true,

        ),
        "tut_level_up" to TutorialDef(
            key = "tut_level_up",
            title = "Level Up Ready!",
            content = "Find a trainer NPC to level up and gain CP for stat boosts.",
            blocking = false
        ),
        "tut_crafter" to TutorialDef(
            key = "tut_crafter",
            title = "Crafter Available",
            content = "A crafter resides here who can forge powerful items from raw materials. " +
                "Tap the anvil icon in your toolbar to see available recipes.\n\n" +
                "Gather crafting materials from defeated enemies and explore the world to find rare components.",
            blocking = true,

        ),
        "tut_magic" to TutorialDef(
            key = "tut_magic",
            title = "Spell Bar",
            content = "Tap a spell slot to assign a spell \u2014 it auto-casts each round in combat.",
            blocking = false,
            targetElement = "spell_bar",

        ),
        "tut_stealth" to TutorialDef(
            key = "tut_stealth",
            title = "Sneak Available",
            content = "Use Sneak before entering rooms \u2014 your first attack from hiding deals backstab damage.",
            blocking = false,
            targetElement = "sneak_button"
        ),
        "tut_death" to TutorialDef(
            key = "tut_death",
            title = "Fallen in Battle",
            content = "", // Content is dynamically generated based on player level
            blocking = false,

        ),
        "tut_danger_ahead" to TutorialDef(
            key = "tut_danger_ahead",
            title = "The Air Grows Thick...",
            content = "The creatures here are stronger. Tread carefully \u2014 don't be afraid to retreat.",
            blocking = false,

        ),
        "tut_cp_spend" to TutorialDef(
            key = "tut_cp_spend",
            title = "Unspent Character Points",
            content = "You have Character Points (CP) available to spend! " +
                "Use the trainer's stat allocation panel to improve your attributes.\n\n" +
                "Strength increases melee damage, Agility helps dodge attacks, " +
                "Intellect boosts magic power, Willpower increases mana, " +
                "Health improves max HP, and Charm affects vendor prices.",
            blocking = true
        ),
        "tut_status_effect" to TutorialDef(
            key = "tut_status_effect",
            title = "Status Effect Active",
            content = "Check the icons near your health bar \u2014 buffs help, debuffs hurt. They wear off over time.",
            blocking = false,
            targetElement = "status_effects"
        ),
        "tut_inventory" to TutorialDef(
            key = "tut_inventory",
            title = "Item Collected",
            content = "Open your Inventory (bag icon) to view, equip, use, or drop items. Keep healing potions handy!",
            blocking = false
        ),
        "tut_locked_exit" to TutorialDef(
            key = "tut_locked_exit",
            title = "Locked Passage",
            content = "Rogues with Pick Lock can attempt to force locked doors. Others must find a key or hidden mechanism.",
            blocking = false
        ),
        "tut_hidden_exit" to TutorialDef(
            key = "tut_hidden_exit",
            title = "Hidden Passage Found",
            content = "Sharp eyes! The Perception skill helps reveal concealed routes. Not every secret is found on the first visit.",
            blocking = false
        ),
        "tut_trainer_cap" to TutorialDef(
            key = "tut_trainer_cap",
            title = "Trainer Limit Reached",
            content = "This trainer has taught you all they can. Higher-tier trainers wait in distant lands — each one farther afield, " +
                "each one demanding more of you to reach. Travel further to unlock greater levels and stats.",
            blocking = false
        ),
        "tut_party_invite" to TutorialDef(
            key = "tut_party_invite",
            title = "Party Invite!",
            content = "Another adventurer wants to join forces! Accept to form a party of up to 4 players. " +
                "Party members share XP bonuses and can see each other's health. " +
                "Use /p to chat with your party, or /follow <name> to auto-follow a party member.",
            blocking = false
        ),
        "tut_party_formed" to TutorialDef(
            key = "tut_party_formed",
            title = "Party Formed",
            content = "You're now in a party! Your party's health bars appear in the top-left corner. " +
                "Tap 'P' in the toolbar to manage your party. Party members in the same room earn bonus XP from kills. " +
                "Loot from party kills drops to the ground for anyone to grab — first come, first served. " +
                "The leader can promote another member to lead from the party panel.",
            blocking = false
        ),
        "tut_slash_commands" to TutorialDef(
            key = "tut_slash_commands",
            title = "Slash Commands",
            content = "Start a message with / to use commands — an autocomplete menu appears as you type. " +
                "/tell <name> whispers to a player anywhere in the world, /p talks to your party, " +
                "/invite <name> asks someone to join your party, and /help lists everything.",
            blocking = false
        )
    )

    /**
     * Send a per-world intro Tutorial (#272) on first character login.
     *
     * Content comes from the loaded world's `manifest.json` `introScript` field
     * rather than the hardcoded [tutorials] map — this is the engine primitive
     * that any world can populate to set the scene before the generic [welcome]
     * tutorial. Empty introScript or missing manifest is a no-op (worlds opt in).
     *
     * Persistence and dedup follow the same pattern as [trySend]: keyed
     * `tut_world_intro` in `PlayerDiscoveryTable`, fires at most once per character.
     */
    suspend fun trySendWorldIntro(session: PlayerSession): Boolean {
        val key = "tut_world_intro"
        if (key in session.seenTutorials) return false
        val script = worldManifest?.introScript?.takeIf { it.isNotBlank() } ?: return false
        val title = worldManifest.name.ifBlank { "Welcome" }

        val tutorial = ServerMessage.Tutorial(
            key = key,
            title = title,
            content = script,
            blocking = true,
            targetElement = null
        )

        session.seenTutorials.add(key)

        val playerName = session.playerName
        if (playerName != null) {
            try {
                discoveryRepository.markTutorialSeen(playerName, key)
            } catch (e: Exception) {
                logger.warn("Failed to persist world-intro tutorial for $playerName: ${e.message}")
            }
        }

        try {
            session.send(tutorial)
        } catch (e: Exception) {
            logger.warn("Failed to send world-intro tutorial: ${e.message}")
        }

        return true
    }

    /**
     * Try to send a tutorial. Checks if already seen — each tutorial fires at most once
     * per character lifetime. No throttling: coach marks are passive banners that auto-fade,
     * and modals only fire in safe contexts.
     */
    suspend fun trySend(session: PlayerSession, key: String, contentOverride: String? = null): Boolean {
        if (key in session.seenTutorials) return false

        val def = tutorials[key] ?: return false

        val tutorial = ServerMessage.Tutorial(
            key = def.key,
            title = def.title,
            content = contentOverride ?: def.content,
            blocking = def.blocking,
            targetElement = def.targetElement
        )

        // Mark as seen and send immediately
        session.seenTutorials.add(key)

        val playerName = session.playerName
        if (playerName != null) {
            try {
                discoveryRepository.markTutorialSeen(playerName, key)
            } catch (e: Exception) {
                logger.warn("Failed to persist tutorial $key for $playerName: ${e.message}")
            }
        }

        try {
            session.send(tutorial)
        } catch (e: Exception) {
            logger.warn("Failed to send tutorial $key: ${e.message}")
        }

        return true
    }

    /**
     * Check if a player's class has magic (any magic schools).
     */
    fun classHasMagic(characterClass: String): Boolean {
        val classDef = classCatalog.getClass(characterClass) ?: return false
        return classDef.magicSchools.isNotEmpty()
    }

    /**
     * Check if a player's class has stealth ability.
     */
    fun classHasStealth(characterClass: String): Boolean {
        val classDef = classCatalog.getClass(characterClass) ?: return false
        return classDef.skills.any { it.uppercase() == "SNEAK" }
    }

    /**
     * Generate death tutorial content that is level-aware.
     */
    fun deathContent(playerLevel: Int): String {
        return if (playerLevel >= GameConfig.Progression.DEATH_XP_PENALTY_MIN_LEVEL) {
            "You respawned in town with a small XP penalty. Try weaker enemies to level up safely."
        } else {
            "You respawned in town with no XP penalty. Try weaker enemies to level up safely."
        }
    }

    /** Danger zone IDs that trigger tut_danger_ahead */
    val dangerZones = setOf("marsh", "deep_forest", "caves", "ruins")
    /** Safe zone IDs (starting areas) */
    val safeZones = setOf("millhaven")
}
