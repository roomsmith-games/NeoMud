package com.neomud.server.game.commands

import com.neomud.server.game.MeditationUtils
import com.neomud.server.game.RestUtils
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.session.PlayerSession
import com.neomud.server.world.WorldGraph
import com.neomud.shared.protocol.ServerMessage

class AttackCommand(
    private val npcManager: NpcManager,
    private val worldGraph: WorldGraph
) {
    suspend fun handleToggle(session: PlayerSession, enabled: Boolean) {
        val roomId = session.currentRoomId ?: return

        if (enabled) {
            // Check for SANCTUARY room effect
            val room = worldGraph.getRoom(roomId)
            if (room != null && room.effects.any { it.type == "SANCTUARY" }) {
                val sanctuaryEffect = room.effects.first { it.type == "SANCTUARY" }
                val msg = sanctuaryEffect.message.ifEmpty { "A divine sanctuary protects this place. Combat is not allowed here." }
                session.send(ServerMessage.AttackModeUpdate(false))
                session.send(ServerMessage.SystemMessage(msg))
                return
            }

            val hostiles = npcManager.getLivingHostileNpcsInRoom(roomId)
            if (hostiles.isEmpty()) {
                session.send(ServerMessage.AttackModeUpdate(false))
                session.send(ServerMessage.SystemMessage("No hostile targets here."))
                return
            }

            // Entering attack mode breaks meditation, rest, and grace period
            MeditationUtils.breakMeditation(session, "You stop meditating.")
            RestUtils.breakRest(session, "You stop resting.")
            session.combatGraceTicks = 0

            session.attackMode = true
            session.send(ServerMessage.AttackModeUpdate(true))

            if (session.followState == com.neomud.shared.model.FollowState.ACTIVE) {
                session.followState = com.neomud.shared.model.FollowState.PAUSED
                session.send(com.neomud.shared.protocol.ServerMessage.FollowUpdate(
                    session.playerName ?: "", session.followTarget ?: "", com.neomud.shared.model.FollowState.PAUSED
                ))
                session.send(com.neomud.shared.protocol.ServerMessage.SystemMessage("Combat prevents you from following."))
            }
        } else {
            session.attackMode = false
            session.selectedTargetId = null
            session.send(ServerMessage.AttackModeUpdate(false))
        }
    }

    suspend fun handleSelectTarget(session: PlayerSession, npcId: String?) {
        if (npcId == null) {
            session.selectedTargetId = null
            return
        }

        val roomId = session.currentRoomId ?: return
        val npc = npcManager.getNpcState(npcId)

        if (npc != null && npc.currentRoomId == roomId && npc.hostile) {
            session.selectedTargetId = npcId
            val playerName = session.playerName
            if (playerName != null && session.attackMode) {
                npc.engagedPlayerIds.add(playerName)
            }
        } else {
            session.selectedTargetId = null
            session.send(ServerMessage.SystemMessage("Invalid target."))
        }
    }
}
