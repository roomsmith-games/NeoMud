package com.neomud.server.game.commands

import com.neomud.server.game.TutorialService
import com.neomud.server.game.party.PartyService
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.shared.model.PartyMember
import com.neomud.shared.protocol.ServerMessage
import org.slf4j.LoggerFactory

class PartyCommand(
    private val partyService: PartyService,
    private val sessionManager: SessionManager,
    private val tickCounter: () -> Long,
    private val tutorialService: TutorialService? = null
) {
    var followCommand: FollowCommand? = null
    private val logger = LoggerFactory.getLogger(PartyCommand::class.java)

    suspend fun handleInvite(session: PlayerSession, targetName: String) {
        val inviterName = session.playerName ?: return
        val targetSession = sessionManager.getSession(targetName)
        if (targetSession == null) {
            session.send(ServerMessage.SystemMessage("$targetName is not online."))
            return
        }

        // Block invite if target is ignoring inviter
        if (inviterName.lowercase() in targetSession.ignoredPlayers) {
            session.send(ServerMessage.SystemMessage("$targetName is not online."))
            return
        }

        when (val result = partyService.createInvite(inviterName, targetName, tickCounter())) {
            is PartyService.InviteResult.Success -> {
                session.send(ServerMessage.SystemMessage("You invited $targetName to your party."))
                val inviterPlayer = session.player
                targetSession.send(ServerMessage.PartyInviteReceived(
                    inviterName = inviterName,
                    partySize = result.partySize,
                    inviterLevel = inviterPlayer?.level ?: 0,
                    inviterClass = inviterPlayer?.characterClass ?: ""
                ))
                tutorialService?.trySend(targetSession, "tut_party_invite")
            }
            is PartyService.InviteResult.CannotInviteSelf ->
                session.send(ServerMessage.SystemMessage("You can't invite yourself."))
            is PartyService.InviteResult.TargetAlreadyInParty ->
                session.send(ServerMessage.SystemMessage("$targetName is already in a party."))
            is PartyService.InviteResult.NotLeader ->
                session.send(ServerMessage.SystemMessage("Only the party leader can invite."))
            is PartyService.InviteResult.PartyFull ->
                session.send(ServerMessage.SystemMessage("Your party is full."))
            is PartyService.InviteResult.Throttled ->
                session.send(ServerMessage.SystemMessage(result.reason))
        }
    }

    suspend fun handleAccept(session: PlayerSession, inviterName: String) {
        val targetName = session.playerName ?: return

        when (val result = partyService.acceptInvite(targetName, inviterName, tickCounter())) {
            is PartyService.AcceptResult.Formed -> {
                val party = result.party
                val members = buildMemberList(party)
                for (name in party.members) {
                    val s = sessionManager.getSession(name)
                    s?.send(ServerMessage.PartyFormed(party.id, members, party.leaderId))
                    if (s != null) tutorialService?.trySend(s, "tut_party_formed")
                }
                logger.info("Party ${party.id} formed: ${party.members}")
            }
            is PartyService.AcceptResult.Joined -> {
                val party = result.party
                val newMember = buildMember(session, party.leaderId)
                if (newMember != null) {
                    for (name in party.members) {
                        if (name != targetName) {
                            sessionManager.getSession(name)?.send(
                                ServerMessage.PartyMemberJoined(newMember)
                            )
                        }
                    }
                }
                val members = buildMemberList(party)
                session.send(ServerMessage.PartyFormed(party.id, members, party.leaderId))
                tutorialService?.trySend(session, "tut_party_formed")
                logger.info("$targetName joined party ${party.id}")
            }
            is PartyService.AcceptResult.NoInvite ->
                session.send(ServerMessage.SystemMessage("No pending invite from $inviterName."))
            is PartyService.AcceptResult.Expired ->
                session.send(ServerMessage.SystemMessage("The invite from $inviterName has expired."))
            is PartyService.AcceptResult.AlreadyInParty ->
                session.send(ServerMessage.SystemMessage("You are already in a party."))
            is PartyService.AcceptResult.PartyFull ->
                session.send(ServerMessage.SystemMessage("That party is full."))
        }
    }

    suspend fun handleDecline(session: PlayerSession, inviterName: String) {
        val targetName = session.playerName ?: return
        val existed = partyService.declineInvite(targetName, inviterName)
        if (!existed) {
            session.send(ServerMessage.SystemMessage("No pending invite from $inviterName."))
            return
        }
        partyService.recordDecline(inviterName, targetName, tickCounter())
        session.send(ServerMessage.SystemMessage("You declined the invite from $inviterName."))
        sessionManager.getSession(inviterName)?.send(
            ServerMessage.SystemMessage("$targetName declined your party invite.")
        )
    }

    suspend fun handleLeave(session: PlayerSession) {
        val playerName = session.playerName ?: return
        val partyMembersBefore = partyService.getPartyMembers(playerName)

        when (val result = partyService.leaveParty(playerName)) {
            is PartyService.LeaveResult.Left -> {
                clearFollowForDeparture(playerName, partyMembersBefore)
                session.send(ServerMessage.SystemMessage("You left the party."))
                for (name in result.party.members) {
                    sessionManager.getSession(name)?.send(
                        ServerMessage.PartyMemberLeft(playerName, "left")
                    )
                }
                if (result.newLeader != null) {
                    for (name in result.party.members) {
                        sessionManager.getSession(name)?.send(
                            ServerMessage.SystemMessage("${result.newLeader} is now the party leader.")
                        )
                    }
                }
            }
            is PartyService.LeaveResult.Disbanded -> {
                clearFollowForDisband(partyMembersBefore)
                session.send(ServerMessage.SystemMessage("You left the party."))
                val remaining = result.remainingMember
                if (remaining != null) {
                    sessionManager.getSession(remaining)?.send(
                        ServerMessage.PartyDisbanded("$playerName left — party too small to continue.")
                    )
                }
                logger.info("Party ${result.partyId} disbanded")
            }
            is PartyService.LeaveResult.NotInParty ->
                session.send(ServerMessage.SystemMessage("You are not in a party."))
        }
    }

    suspend fun handleKick(session: PlayerSession, targetName: String) {
        val kickerName = session.playerName ?: return
        val partyMembersBefore = partyService.getPartyMembers(kickerName)

        when (val result = partyService.kickMember(kickerName, targetName)) {
            is PartyService.KickResult.Kicked -> {
                clearFollowForDeparture(targetName, partyMembersBefore)
                sessionManager.getSession(targetName)?.send(
                    ServerMessage.PartyDisbanded("You were kicked from the party by $kickerName.")
                )
                for (name in result.party.members) {
                    sessionManager.getSession(name)?.send(
                        ServerMessage.PartyMemberLeft(targetName, "kicked")
                    )
                }
            }
            is PartyService.KickResult.Disbanded -> {
                clearFollowForDisband(partyMembersBefore)
                sessionManager.getSession(targetName)?.send(
                    ServerMessage.PartyDisbanded("You were kicked from the party by $kickerName.")
                )
                val remaining = result.remainingMember
                if (remaining != null) {
                    sessionManager.getSession(remaining)?.send(
                        ServerMessage.PartyDisbanded("$targetName was kicked — party too small to continue.")
                    )
                }
            }
            is PartyService.KickResult.NotInParty ->
                session.send(ServerMessage.SystemMessage("You are not in a party."))
            is PartyService.KickResult.NotLeader ->
                session.send(ServerMessage.SystemMessage("Only the party leader can kick members."))
            is PartyService.KickResult.CannotKickSelf ->
                session.send(ServerMessage.SystemMessage("You can't kick yourself. Use /leave instead."))
            is PartyService.KickResult.TargetNotInParty ->
                session.send(ServerMessage.SystemMessage("$targetName is not in your party."))
        }
    }

    suspend fun handleSay(session: PlayerSession, message: String) {
        val senderName = session.playerName ?: return
        val party = partyService.getPartyForPlayer(senderName)
        if (party == null) {
            session.send(ServerMessage.SystemMessage("You are not in a party."))
            return
        }
        for (name in party.members) {
            sessionManager.getSession(name)?.send(
                ServerMessage.PartyChatMessage(senderName, message)
            )
        }
    }

    suspend fun handlePromote(session: PlayerSession, targetName: String) {
        val playerName = session.playerName ?: return
        when (val result = partyService.promoteLeader(playerName, targetName)) {
            is PartyService.PromoteResult.Promoted -> {
                for (name in result.party.members) {
                    sessionManager.getSession(name)?.send(
                        ServerMessage.PartyLeaderChanged(targetName, targetName)
                    )
                    sessionManager.getSession(name)?.send(
                        ServerMessage.SystemMessage("$targetName is now the party leader.")
                    )
                }
            }
            is PartyService.PromoteResult.NotInParty ->
                session.send(ServerMessage.SystemMessage("You are not in a party."))
            is PartyService.PromoteResult.NotLeader ->
                session.send(ServerMessage.SystemMessage("Only the party leader can promote."))
            is PartyService.PromoteResult.TargetNotInParty ->
                session.send(ServerMessage.SystemMessage("$targetName is not in your party."))
            is PartyService.PromoteResult.AlreadyLeader ->
                session.send(ServerMessage.SystemMessage("$targetName is already the leader."))
        }
    }

    private suspend fun clearFollowForDeparture(departingName: String, allMembers: List<String>) {
        val fc = followCommand ?: return
        val departingSession = sessionManager.getSession(departingName)
        if (departingSession != null) {
            fc.clearFollowAndNotify(departingSession, "You stop following — you left the party.")
        }
        for (name in allMembers) {
            val s = sessionManager.getSession(name) ?: continue
            if (s.followTarget == departingName) {
                fc.clearFollowAndNotify(s, "You stop following $departingName — they left the party.")
            }
        }
    }

    private suspend fun clearFollowForDisband(allMembers: List<String>) {
        val fc = followCommand ?: return
        for (name in allMembers) {
            val s = sessionManager.getSession(name) ?: continue
            if (s.followState != com.neomud.shared.model.FollowState.OFF) {
                fc.clearFollowAndNotify(s, "You stop following — the party disbanded.")
            }
        }
    }

    private fun buildMemberList(party: com.neomud.server.game.party.Party): List<PartyMember> {
        return party.members.mapNotNull { name ->
            val s = sessionManager.getSession(name) ?: return@mapNotNull null
            buildMember(s, party.leaderId)
        }
    }

    private fun buildMember(session: PlayerSession, leaderId: String): PartyMember? {
        val p = session.player ?: return null
        return partyService.buildPartyMember(
            name = p.name,
            characterClass = p.characterClass,
            race = p.race,
            level = p.level,
            currentHp = p.currentHp,
            maxHp = p.maxHp,
            currentMp = p.currentMp,
            maxMp = p.maxMp,
            roomId = session.currentRoomId ?: "",
            leaderId = leaderId
        )
    }
}
