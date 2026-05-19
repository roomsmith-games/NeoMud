package com.neomud.server.game.party

data class Party(
    val id: String,
    val members: MutableList<String>,
    var leaderId: String,
    val createdAtTick: Long,
    val disconnected: MutableMap<String, Int> = mutableMapOf()
)

data class PendingInvite(
    val inviterName: String,
    val targetName: String,
    val expiresAtTick: Long
)
