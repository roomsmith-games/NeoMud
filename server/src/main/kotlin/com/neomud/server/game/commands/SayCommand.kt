package com.neomud.server.game.commands

import com.neomud.server.game.MeditationUtils
import com.neomud.server.game.RestUtils
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.shared.protocol.ServerMessage

class SayCommand(
    private val sessionManager: SessionManager,
    private val adminCommand: AdminCommand,
    private val playerCommandRouter: PlayerCommandRouter? = null
) {
    suspend fun execute(session: PlayerSession, message: String) {
        val roomId = session.currentRoomId ?: return
        val playerName = session.playerName ?: return

        // Intercept slash commands: player commands first, then admin
        if (message.startsWith("/")) {
            val handled = playerCommandRouter?.tryExecute(session, message) ?: false
            if (!handled) {
                if (session.player?.isAdmin == true) {
                    adminCommand.execute(session, message)
                } else {
                    val suggestion = playerCommandRouter?.suggestCommand(message)
                    val hint = if (suggestion != null) " Did you mean: $suggestion?" else " Type /help for commands."
                    val cmd = message.trimStart('/').split(" ")[0]
                    session.send(ServerMessage.SystemMessage("Unknown command: /$cmd.$hint"))
                }
            }
            return
        }

        // Break meditation and rest on say
        MeditationUtils.breakMeditation(session, "You stop meditating.")
        RestUtils.breakRest(session, "You stop resting.")

        // Break stealth on say
        if (session.isHidden) {
            session.isHidden = false
            session.send(ServerMessage.StealthUpdate(false, "Speaking reveals your presence!"))
            sessionManager.broadcastToRoom(
                roomId,
                ServerMessage.PlayerEntered(playerName, roomId, session.toPlayerInfo()),
                exclude = playerName
            )
        }

        val sanitized = message.take(500).replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")

        sessionManager.broadcastToRoom(
            roomId,
            ServerMessage.PlayerSays(playerName, sanitized)
        )
    }
}
