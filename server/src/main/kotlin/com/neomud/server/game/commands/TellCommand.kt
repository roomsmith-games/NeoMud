package com.neomud.server.game.commands

import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.shared.protocol.ServerMessage

class TellCommand(
    private val sessionManager: SessionManager
) {
    suspend fun executeTell(session: PlayerSession, args: String) {
        val parts = args.trim().split(" ", limit = 2)
        val targetName = parts.getOrNull(0)?.trim() ?: ""
        val message = parts.getOrNull(1)?.trim() ?: ""

        if (targetName.isBlank()) {
            session.send(ServerMessage.SystemMessage("Usage: /tell <player> <message>"))
            return
        }
        if (message.isBlank()) {
            session.send(ServerMessage.SystemMessage("Usage: /tell <player> <message>"))
            return
        }

        sendTell(session, targetName, message)
    }

    suspend fun executeReply(session: PlayerSession, args: String) {
        val message = args.trim()
        if (message.isBlank()) {
            session.send(ServerMessage.SystemMessage("Usage: /reply <message>"))
            return
        }

        val targetName = session.lastTellSender
        if (targetName == null) {
            session.send(ServerMessage.SystemMessage("No one has sent you a tell to reply to."))
            return
        }

        sendTell(session, targetName, message)
    }

    private suspend fun sendTell(session: PlayerSession, targetName: String, message: String) {
        val senderName = session.playerName ?: return

        if (targetName.equals(senderName, ignoreCase = true)) {
            session.send(ServerMessage.SystemMessage("You can't send a tell to yourself."))
            return
        }

        val targetSession = sessionManager.getSession(targetName)
        if (targetSession == null) {
            session.send(ServerMessage.SystemMessage("$targetName is not online."))
            return
        }

        if (senderName.lowercase() in targetSession.ignoredPlayers) {
            session.send(ServerMessage.SystemMessage("$targetName is not online."))
            return
        }

        val sanitized = message.take(500).replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]"), "")

        targetSession.lastTellSender = senderName
        targetSession.send(ServerMessage.TellReceived(senderName, sanitized))
        session.send(ServerMessage.TellSent(targetSession.playerName ?: targetName, sanitized))
    }
}
