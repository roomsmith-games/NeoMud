package com.neomud.server.persistence.repository

import com.neomud.server.persistence.tables.PlayerFlagsTable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/**
 * Per-character flag storage. Set/clear are upserts; get/load are
 * read-through, no caching. Sessions should preload all flags on login
 * and write through on changes (pattern matches DiscoveryRepository).
 */
open class PlayerFlagsRepository {

    open fun getFlag(playerName: String, flagKey: String): String? = transaction {
        PlayerFlagsTable.selectAll().where {
            (PlayerFlagsTable.playerName eq playerName) and (PlayerFlagsTable.flagKey eq flagKey)
        }.singleOrNull()?.get(PlayerFlagsTable.flagValue)
    }

    open fun setFlag(playerName: String, flagKey: String, flagValue: String): Unit = transaction {
        val updated = PlayerFlagsTable.update({
            (PlayerFlagsTable.playerName eq playerName) and (PlayerFlagsTable.flagKey eq flagKey)
        }) {
            it[PlayerFlagsTable.flagValue] = flagValue
        }
        if (updated == 0) {
            PlayerFlagsTable.insert {
                it[PlayerFlagsTable.playerName] = playerName
                it[PlayerFlagsTable.flagKey] = flagKey
                it[PlayerFlagsTable.flagValue] = flagValue
            }
        }
    }

    open fun clearFlag(playerName: String, flagKey: String): Unit = transaction {
        PlayerFlagsTable.deleteWhere {
            (PlayerFlagsTable.playerName eq playerName) and (PlayerFlagsTable.flagKey eq flagKey)
        }
    }

    open fun loadAllFlags(playerName: String): Map<String, String> = transaction {
        PlayerFlagsTable.selectAll().where {
            PlayerFlagsTable.playerName eq playerName
        }.associate {
            it[PlayerFlagsTable.flagKey] to it[PlayerFlagsTable.flagValue]
        }
    }
}
