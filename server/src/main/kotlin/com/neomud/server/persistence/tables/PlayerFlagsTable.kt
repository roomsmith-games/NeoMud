package com.neomud.server.persistence.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * Per-character key/value flag storage.
 *
 * General-purpose persistent state for gameplay decisions and progression
 * markers that don't fit `PlayerDiscoveryTable`'s presence-only model.
 * Flag values are strings; callers parse as needed.
 *
 * First consumer is the Phase 8 endgame `CHOICE_PROMPT` (8J Seal vs Sunder),
 * but the table is intentionally generic for future flags.
 */
object PlayerFlagsTable : Table("player_flags") {
    val id = integer("id").autoIncrement()
    val playerName = varchar("player_name", 50).index()
    val flagKey = varchar("flag_key", 100)
    val flagValue = varchar("flag_value", 200)

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = true, playerName, flagKey)
    }
}
