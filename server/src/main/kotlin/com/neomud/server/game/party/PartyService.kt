package com.neomud.server.game.party

import com.neomud.server.game.GameConfig
import com.neomud.shared.model.FollowState
import com.neomud.shared.model.PartyMember
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PartyService {
    private val logger = LoggerFactory.getLogger(PartyService::class.java)

    private val parties = ConcurrentHashMap<String, Party>()
    private val playerParty = ConcurrentHashMap<String, String>()
    private val pendingInvites = ConcurrentHashMap<String, MutableList<PendingInvite>>()

    private val lootRotation = ConcurrentHashMap<String, Int>()

    fun getParty(partyId: String): Party? = parties[partyId]

    fun getPartyForPlayer(playerName: String): Party? {
        val partyId = playerParty[playerName] ?: return null
        return parties[partyId]
    }

    fun isInParty(playerName: String): Boolean = playerParty.containsKey(playerName)

    fun getPartyMembers(playerName: String): List<String> {
        val party = getPartyForPlayer(playerName) ?: return emptyList()
        return party.members.toList()
    }

    fun getMembersInRoom(playerName: String, roomId: String, roomResolver: (String) -> String?): List<String> {
        val party = getPartyForPlayer(playerName) ?: return listOf(playerName)
        return party.members.filter { roomResolver(it) == roomId }
    }

    fun createInvite(inviterName: String, targetName: String, currentTick: Long): InviteResult {
        if (inviterName == targetName) return InviteResult.CannotInviteSelf
        if (isInParty(targetName)) return InviteResult.TargetAlreadyInParty

        val inviterParty = getPartyForPlayer(inviterName)
        if (inviterParty != null) {
            if (inviterParty.leaderId != inviterName) return InviteResult.NotLeader
            if (inviterParty.members.size >= GameConfig.Party.MAX_SIZE) return InviteResult.PartyFull
        }

        val existing = pendingInvites.getOrPut(targetName) { mutableListOf() }
        existing.removeAll { it.inviterName == inviterName }
        existing.add(PendingInvite(inviterName, targetName, currentTick + GameConfig.Party.INVITE_EXPIRY_TICKS))

        val currentSize = inviterParty?.members?.size ?: 1
        return InviteResult.Success(currentSize)
    }

    fun acceptInvite(targetName: String, inviterName: String, currentTick: Long): AcceptResult {
        val invites = pendingInvites[targetName] ?: return AcceptResult.NoInvite
        val invite = invites.find { it.inviterName == inviterName } ?: return AcceptResult.NoInvite

        if (invite.expiresAtTick <= currentTick) {
            invites.remove(invite)
            return AcceptResult.Expired
        }
        invites.remove(invite)
        if (invites.isEmpty()) pendingInvites.remove(targetName)

        if (isInParty(targetName)) return AcceptResult.AlreadyInParty

        val existingParty = getPartyForPlayer(inviterName)
        if (existingParty != null) {
            if (existingParty.members.size >= GameConfig.Party.MAX_SIZE) return AcceptResult.PartyFull
            existingParty.members.add(targetName)
            playerParty[targetName] = existingParty.id
            return AcceptResult.Joined(existingParty)
        }

        val partyId = "party_${UUID.randomUUID().toString().take(8)}"
        val party = Party(
            id = partyId,
            members = mutableListOf(inviterName, targetName),
            leaderId = inviterName,
            createdAtTick = currentTick
        )
        parties[partyId] = party
        playerParty[inviterName] = partyId
        playerParty[targetName] = partyId
        return AcceptResult.Formed(party)
    }

    fun declineInvite(targetName: String, inviterName: String): Boolean {
        val invites = pendingInvites[targetName] ?: return false
        val removed = invites.removeAll { it.inviterName == inviterName }
        if (invites.isEmpty()) pendingInvites.remove(targetName)
        return removed
    }

    fun leaveParty(playerName: String): LeaveResult {
        val party = getPartyForPlayer(playerName) ?: return LeaveResult.NotInParty
        party.members.remove(playerName)
        party.disconnected.remove(playerName)
        playerParty.remove(playerName)
        lootRotation.remove(party.id)

        if (party.members.size <= 1) {
            val remaining = party.members.firstOrNull()
            if (remaining != null) {
                playerParty.remove(remaining)
            }
            parties.remove(party.id)
            return LeaveResult.Disbanded(party.id, remaining)
        }

        if (party.leaderId == playerName) {
            party.leaderId = party.members.first()
            return LeaveResult.Left(party, newLeader = party.leaderId)
        }
        return LeaveResult.Left(party, newLeader = null)
    }

    fun kickMember(kickerName: String, targetName: String): KickResult {
        val party = getPartyForPlayer(kickerName) ?: return KickResult.NotInParty
        if (party.leaderId != kickerName) return KickResult.NotLeader
        if (targetName == kickerName) return KickResult.CannotKickSelf
        if (targetName !in party.members) return KickResult.TargetNotInParty

        party.members.remove(targetName)
        party.disconnected.remove(targetName)
        playerParty.remove(targetName)
        lootRotation.remove(party.id)

        if (party.members.size <= 1) {
            val remaining = party.members.firstOrNull()
            if (remaining != null) {
                playerParty.remove(remaining)
            }
            parties.remove(party.id)
            return KickResult.Disbanded(party.id, remaining)
        }
        return KickResult.Kicked(party)
    }

    fun markDisconnected(playerName: String, graceTicks: Int = GameConfig.Party.DISCONNECT_GRACE_TICKS) {
        val party = getPartyForPlayer(playerName) ?: return
        party.disconnected[playerName] = graceTicks
    }

    fun tryReconnect(playerName: String): Party? {
        val party = getPartyForPlayer(playerName) ?: return null
        if (playerName in party.disconnected) {
            party.disconnected.remove(playerName)
            return party
        }
        return party
    }

    fun tickExpireInvites(currentTick: Long): List<PendingInvite> {
        val expired = mutableListOf<PendingInvite>()
        val iterator = pendingInvites.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val removed = entry.value.filter { it.expiresAtTick <= currentTick }
            expired.addAll(removed)
            entry.value.removeAll { it.expiresAtTick <= currentTick }
            if (entry.value.isEmpty()) iterator.remove()
        }
        return expired
    }

    fun tickGracePeriods(): List<String> {
        val removed = mutableListOf<String>()
        for (party in parties.values.toList()) {
            val dcIterator = party.disconnected.entries.iterator()
            while (dcIterator.hasNext()) {
                val entry = dcIterator.next()
                entry.setValue(entry.value - 1)
                if (entry.value <= 0) {
                    val playerName = entry.key
                    dcIterator.remove()
                    party.members.remove(playerName)
                    playerParty.remove(playerName)
                    removed.add(playerName)

                    if (party.members.size <= 1) {
                        val remaining = party.members.firstOrNull()
                        if (remaining != null) playerParty.remove(remaining)
                        parties.remove(party.id)
                        lootRotation.remove(party.id)
                    } else if (party.leaderId == playerName) {
                        party.leaderId = party.members.first()
                    }
                }
            }
        }
        return removed
    }

    fun nextLootRecipient(partyId: String): String? {
        val party = parties[partyId] ?: return null
        if (party.members.isEmpty()) return null
        val idx = lootRotation.getOrPut(partyId) { 0 }
        val member = party.members[idx % party.members.size]
        lootRotation[partyId] = (idx + 1) % party.members.size
        return member
    }

    fun buildPartyMember(
        name: String,
        characterClass: String,
        race: String,
        level: Int,
        currentHp: Int,
        maxHp: Int,
        currentMp: Int,
        maxMp: Int,
        roomId: String,
        leaderId: String
    ): PartyMember = PartyMember(
        name = name,
        characterClass = characterClass,
        race = race,
        level = level,
        currentHp = currentHp,
        maxHp = maxHp,
        currentMp = currentMp,
        maxMp = maxMp,
        roomId = roomId,
        isLeader = name == leaderId
    )

    sealed class InviteResult {
        data class Success(val partySize: Int) : InviteResult()
        data object CannotInviteSelf : InviteResult()
        data object TargetAlreadyInParty : InviteResult()
        data object NotLeader : InviteResult()
        data object PartyFull : InviteResult()
    }

    sealed class AcceptResult {
        data class Formed(val party: Party) : AcceptResult()
        data class Joined(val party: Party) : AcceptResult()
        data object NoInvite : AcceptResult()
        data object Expired : AcceptResult()
        data object AlreadyInParty : AcceptResult()
        data object PartyFull : AcceptResult()
    }

    sealed class LeaveResult {
        data class Left(val party: Party, val newLeader: String?) : LeaveResult()
        data class Disbanded(val partyId: String, val remainingMember: String?) : LeaveResult()
        data object NotInParty : LeaveResult()
    }

    sealed class KickResult {
        data class Kicked(val party: Party) : KickResult()
        data class Disbanded(val partyId: String, val remainingMember: String?) : KickResult()
        data object NotInParty : KickResult()
        data object NotLeader : KickResult()
        data object CannotKickSelf : KickResult()
        data object TargetNotInParty : KickResult()
    }
}
