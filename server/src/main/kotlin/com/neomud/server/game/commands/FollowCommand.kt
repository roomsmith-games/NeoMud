package com.neomud.server.game.commands

import com.neomud.server.game.party.PartyService
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.FollowState
import com.neomud.shared.protocol.ServerMessage

class FollowCommand(
    private val partyService: PartyService,
    private val sessionManager: SessionManager,
    private val worldGraph: WorldGraph
) {
    suspend fun handleFollow(session: PlayerSession, targetName: String) {
        val playerName = session.playerName ?: return
        if (playerName.equals(targetName, ignoreCase = true)) {
            session.send(ServerMessage.FollowFailed(playerName, "You cannot follow yourself."))
            return
        }
        if (!partyService.isInParty(playerName)) {
            session.send(ServerMessage.FollowFailed(playerName, "You must be in a party to follow someone."))
            return
        }
        val partyMembers = partyService.getPartyMembers(playerName)
        val resolvedTarget = partyMembers.find { it.equals(targetName, ignoreCase = true) }
        if (resolvedTarget == null) {
            session.send(ServerMessage.FollowFailed(playerName, "$targetName is not in your party."))
            return
        }
        val targetSession = sessionManager.getSession(resolvedTarget)
        if (targetSession == null) {
            session.send(ServerMessage.FollowFailed(playerName, "$resolvedTarget is not online."))
            return
        }
        val roomId = session.currentRoomId
        if (roomId == null || targetSession.currentRoomId != roomId) {
            session.send(ServerMessage.FollowFailed(playerName, "$resolvedTarget is not in this room."))
            return
        }
        if (targetSession.followTarget == playerName) {
            session.send(ServerMessage.FollowFailed(playerName, "$resolvedTarget is already following you."))
            return
        }

        session.followTarget = resolvedTarget
        session.followState = FollowState.ACTIVE

        session.send(ServerMessage.FollowUpdate(playerName, resolvedTarget, FollowState.ACTIVE))
        session.send(ServerMessage.SystemMessage("You begin following $resolvedTarget."))
        targetSession.send(ServerMessage.SystemMessage("$playerName is now following you."))
    }

    suspend fun handleStop(session: PlayerSession) {
        val playerName = session.playerName ?: return
        val target = session.followTarget
        if (target == null || session.followState == FollowState.OFF) {
            session.send(ServerMessage.SystemMessage("You are not following anyone."))
            return
        }
        clearFollow(session)
        session.send(ServerMessage.FollowUpdate(playerName, target, FollowState.OFF))
        session.send(ServerMessage.SystemMessage("You stop following $target."))
        sessionManager.getSession(target)?.send(
            ServerMessage.SystemMessage("$playerName is no longer following you.")
        )
    }

    suspend fun handleRally(session: PlayerSession) {
        val playerName = session.playerName ?: return
        val party = partyService.getPartyForPlayer(playerName)
        if (party == null) {
            session.send(ServerMessage.SystemMessage("You are not in a party."))
            return
        }
        if (party.leaderId != playerName) {
            session.send(ServerMessage.SystemMessage("Only the party leader can rally."))
            return
        }
        val roomId = session.currentRoomId ?: return
        val room = worldGraph.getRoom(roomId)
        val roomName = room?.name ?: "Unknown"
        val zoneName = room?.zoneId ?: "Unknown"

        val msg = ServerMessage.RallyPing(playerName, roomId, roomName, zoneName)
        for (member in party.members) {
            if (member == playerName) continue
            sessionManager.getSession(member)?.send(msg)
        }
        session.send(ServerMessage.SystemMessage("Rally sent to your party."))
    }

    fun clearFollow(session: PlayerSession) {
        session.followTarget = null
        session.followState = FollowState.OFF
    }

    suspend fun clearFollowAndNotify(session: PlayerSession, reason: String? = null) {
        val target = session.followTarget ?: return
        clearFollow(session)
        session.send(ServerMessage.FollowUpdate(session.playerName ?: return, target, FollowState.OFF))
        if (reason != null) {
            session.send(ServerMessage.SystemMessage(reason))
        }
    }
}
