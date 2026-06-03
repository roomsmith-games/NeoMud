package com.neomud.client.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Classic ANSI terminal palette inspired by MajorMUD / MajorBBS.
 */
object MudColors {
    // Room & navigation
    val roomName = Color(0xFF55FFFF)    // bright cyan - room titles
    val roomDesc = Color(0xFFAAAAAA)    // grey - room descriptions
    val exits = Color(0xFF55FF55)       // bright green - exits

    // Entities
    val hostile = Color(0xFFFF5555)     // bright red - hostile NPCs
    val friendly = Color(0xFF55FF55)    // bright green - friendly NPCs
    val playerName = Color(0xFF55FFFF)  // bright cyan - other players

    // Combat
    val combatYou = Color(0xFFFFFF55)   // bright yellow - your attacks
    val combatEnemy = Color(0xFFFF5555) // bright red - enemy attacks
    val death = Color(0xFFFF5555)       // bright red - death messages
    val kill = Color(0xFFFFFF55)        // bright yellow - kill messages

    // Chat & social
    val say = Color(0xFF55FF55)         // bright green - speech
    val playerEvent = Color(0xFF5555FF) // bright blue - enters/leaves

    // System
    val system = Color(0xFFFF55FF)      // bright magenta - system messages
    val error = Color(0xFFFF5555)       // bright red - errors
    val effect = Color(0xFFFF55FF)      // bright magenta - effects/spells
    val selfAction = Color(0xFFFFFFFF)  // white - your own actions

    // Stealth
    val stealth = Color(0xFF888888)     // dim grey - stealth messages

    // Party
    val partyChat = Color(0xFF55AAFF)   // light blue - party messages

    // Tell (direct messages)
    val tellIncoming = Color(0xFFFF8855) // warm orange - incoming tells
    val tellOutgoing = Color(0xFFBB7744) // dim orange - outgoing tells

    // Loot
    val loot = Color(0xFFFFD700)        // gold - loot messages

    // Progression
    val xp = Color(0xFF55FFFF)          // bright cyan - XP gain
    val levelUp = Color(0xFFFFFF55)     // bright yellow - level up

    // Spells
    val spell = Color(0xFF9B59FF)       // purple - spell messages

    // Default
    val default = Color(0xFFCCCCCC)     // light grey
}
