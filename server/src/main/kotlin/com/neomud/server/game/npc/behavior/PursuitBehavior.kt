package com.neomud.server.game.npc.behavior

import com.neomud.server.game.GameConfig
import com.neomud.server.game.MovementTrailManager
import com.neomud.server.game.npc.NpcState
import com.neomud.server.session.SessionManager
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.RoomId

class PursuitBehavior(
    private val targetPlayerId: String,
    private val trailManager: MovementTrailManager,
    private val sessionManager: SessionManager,
    private val maxPursuitTicks: Int = GameConfig.Npc.PURSUIT_MAX_TICKS,
    private val moveEveryNTicks: Int = GameConfig.Npc.PURSUIT_MOVE_TICKS
) : BehaviorNode {
    private var ticksInPursuit = 0
    private var moveTickCounter = 0
    private var lostCounter = 0

    /** Checked by NpcManager after tick to know when to restore original behavior. */
    var pursuitEnded: Boolean = false
        private set

    override fun tick(npc: NpcState, world: WorldGraph, canMoveTo: (RoomId) -> Boolean): NpcAction {
        ticksInPursuit++

        // Give up after max ticks
        if (ticksInPursuit >= maxPursuitTicks) {
            pursuitEnded = true
            return NpcAction.None
        }

        // Check if target player is in current room (combat resumes naturally)
        val sessionsHere = sessionManager.getSessionsInRoom(npc.currentRoomId)
        val targetHere = sessionsHere.any { it.playerName == targetPlayerId }
        if (targetHere) {
            pursuitEnded = true
            return NpcAction.None
        }

        // Check if target player is hidden — if so, lose them
        val targetSession = sessionManager.getSession(targetPlayerId)
        if (targetSession != null && targetSession.isHidden) {
            pursuitEnded = true
            return NpcAction.None
        }

        // Only move every N ticks
        moveTickCounter++
        if (moveTickCounter < moveEveryNTicks) return NpcAction.None
        moveTickCounter = 0

        // Look for player trails in current room
        val trails = trailManager.getTrails(npc.currentRoomId, targetPlayerId)
        if (trails.isNotEmpty()) {
            lostCounter = 0
            val freshestTrail = trails.first()
            val direction = freshestTrail.direction
            val currentRoom = world.getRoom(npc.currentRoomId) ?: run {
                pursuitEnded = true
                return NpcAction.None
            }
            val targetRoomId = currentRoom.exits[direction]
            if (targetRoomId != null) {
                if (!canMoveTo(targetRoomId)) {
                    // Target fled to a sanctuary or blocked room — give up pursuit
                    pursuitEnded = true
                    return NpcAction.None
                }
                return NpcAction.MoveTo(targetRoomId)
            }
        } else {
            lostCounter++
            if (lostCounter >= GameConfig.Npc.PURSUIT_LOST_TRAIL_TICKS) {
                pursuitEnded = true
                return NpcAction.None
            }
        }

        return NpcAction.None
    }
}
