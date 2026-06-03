package com.neomud.server.game.commands

import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.shared.protocol.ServerMessage

class PlayerCommandRouter(
    private val tellCommand: TellCommand,
    private val whoCommand: WhoCommand,
    private val sessionManager: SessionManager,
    private val partyCommand: PartyCommand? = null,
    private val followCommand: FollowCommand? = null
) {
    companion object {
        private val KNOWN_COMMANDS = listOf(
            "/tell", "/t", "/reply", "/r", "/who",
            "/invite", "/ignore", "/unignore",
            "/p", "/follow", "/unfollow", "/rally", "/leave", "/party"
        )
    }

    suspend fun tryExecute(session: PlayerSession, rawMessage: String): Boolean {
        val trimmed = rawMessage.trimStart('/')
        val parts = trimmed.split(" ", limit = 2)
        val command = parts[0].lowercase()
        val args = parts.getOrElse(1) { "" }

        return when (command) {
            "tell", "t" -> { tellCommand.executeTell(session, args); true }
            "reply", "r" -> { tellCommand.executeReply(session, args); true }
            "who" -> { whoCommand.execute(session); true }
            "invite" -> {
                if (args.isBlank()) {
                    session.send(ServerMessage.SystemMessage("Usage: /invite <player>"))
                } else {
                    partyCommand?.handleInvite(session, args.trim())
                        ?: session.send(ServerMessage.SystemMessage("Party system is not available."))
                }
                true
            }
            "ignore" -> { handleIgnore(session, args); true }
            "unignore" -> { handleUnignore(session, args); true }
            // Server-side fallbacks for commands normally parsed client-side
            "p" -> {
                if (args.isBlank()) {
                    session.send(ServerMessage.SystemMessage("Usage: /p <message>"))
                } else {
                    partyCommand?.handleSay(session, args)
                        ?: session.send(ServerMessage.SystemMessage("You are not in a party."))
                }
                true
            }
            "follow" -> {
                val followArgs = args.trim()
                if (followArgs.equals("stop", ignoreCase = true)) {
                    followCommand?.handleStop(session)
                } else if (followArgs.isNotBlank()) {
                    followCommand?.handleFollow(session, followArgs)
                } else {
                    session.send(ServerMessage.SystemMessage("Usage: /follow <player> or /follow stop"))
                }
                true
            }
            "unfollow" -> { followCommand?.handleStop(session); true }
            "rally" -> { followCommand?.handleRally(session); true }
            "leave" -> { partyCommand?.handleLeave(session); true }
            "party" -> {
                when (args.trim().lowercase()) {
                    "leave" -> partyCommand?.handleLeave(session)
                    else -> session.send(ServerMessage.SystemMessage("Usage: /party leave"))
                }
                true
            }
            else -> false
        }
    }

    fun suggestCommand(rawMessage: String): String? {
        val input = "/" + rawMessage.trimStart('/').split(" ")[0].lowercase()
        var bestMatch: String? = null
        var bestDist = 3
        for (cmd in KNOWN_COMMANDS) {
            val d = levenshtein(input, cmd)
            if (d in 1 until bestDist) {
                bestDist = d
                bestMatch = cmd
            }
        }
        return bestMatch
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                )
            }
        }
        return dp[m][n]
    }

    private suspend fun handleIgnore(session: PlayerSession, args: String) {
        val targetName = args.trim()
        if (targetName.isBlank()) {
            session.send(ServerMessage.SystemMessage("Usage: /ignore <player>"))
            return
        }
        val senderName = session.playerName ?: return
        if (targetName.equals(senderName, ignoreCase = true)) {
            session.send(ServerMessage.SystemMessage("You can't ignore yourself."))
            return
        }
        session.ignoredPlayers.add(targetName.lowercase())
        session.send(ServerMessage.SystemMessage("Now ignoring $targetName."))
    }

    private suspend fun handleUnignore(session: PlayerSession, args: String) {
        val targetName = args.trim()
        if (targetName.isBlank()) {
            session.send(ServerMessage.SystemMessage("Usage: /unignore <player>"))
            return
        }
        val removed = session.ignoredPlayers.remove(targetName.lowercase())
        if (removed) {
            session.send(ServerMessage.SystemMessage("No longer ignoring $targetName."))
        } else {
            session.send(ServerMessage.SystemMessage("$targetName is not on your ignore list."))
        }
    }
}
