package com.neomud.server.game.commands

import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.shared.model.OnlinePlayer
import com.neomud.shared.protocol.ServerMessage

class WhoCommand(
    private val sessionManager: SessionManager,
    private val zoneNames: Map<String, String>
) {
    suspend fun execute(session: PlayerSession) {
        val players = sessionManager.getAllAuthenticatedSessions().mapNotNull { s ->
            val p = s.player ?: return@mapNotNull null
            val zoneId = s.currentRoomId?.substringBefore(":") ?: ""
            val zoneName = zoneNames[zoneId] ?: zoneId
            OnlinePlayer(
                name = p.name,
                level = p.level,
                characterClass = p.characterClass,
                zone = zoneName
            )
        }.sortedBy { it.name.lowercase() }

        session.send(ServerMessage.WhoList(players))
    }
}
