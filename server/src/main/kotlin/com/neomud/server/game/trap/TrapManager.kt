package com.neomud.server.game.trap

import com.neomud.server.game.EffectApplicator
import com.neomud.server.session.PlayerSession
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.RoomId
import com.neomud.shared.model.RoomInteractable
import com.neomud.shared.protocol.ServerMessage

/**
 * Resolves passive `ON_ENTER` traps when a player enters a room.
 *
 * Stateless — per-character "tripped" state lives on `PlayerSession`,
 * global "used" state lives on `WorldGraph` (shared with `InteractCommand`).
 * Each call to [onPlayerEntered] handles one room-enter event and may
 * return multiple outcomes if the room has multiple traps.
 *
 * Detection: rolls perception against each trap's `perceptionDC`. On
 * success, the trap is marked discovered (the player will see it in the
 * next room description) and is NOT triggered. On failure, the trap fires.
 *
 * Damage: routed through [TrapResolver] (save mechanic) and
 * [EffectApplicator] (HP floor at 1 — same as all other damage).
 *
 * Player feedback: reuses [ServerMessage.InteractResult] so trap-fired and
 * player-triggered DAMAGE_TRAPs render identically on the client.
 */
class TrapManager(
    private val worldGraph: WorldGraph,
    private val random: () -> Int = { (1..20).random() }
) {
    sealed class TrapOutcome {
        object NoTrap : TrapOutcome()
        data class Detected(val featureId: String, val label: String) : TrapOutcome()
        data class Triggered(
            val featureId: String,
            val label: String,
            val damage: Int,
            val saveOutcome: TrapResolver.SaveOutcome
        ) : TrapOutcome()
    }

    suspend fun onPlayerEntered(session: PlayerSession, roomId: RoomId): List<TrapOutcome> {
        val player = session.player ?: return emptyList()
        val candidates = worldGraph.getInteractableDefs(roomId)
            .filter { it.triggerType == "ON_ENTER" && it.actionType == "DAMAGE_TRAP" }
        if (candidates.isEmpty()) return listOf(TrapOutcome.NoTrap)

        val outcomes = mutableListOf<TrapOutcome>()
        val effectiveStats = session.effectiveStats()

        for (trap in candidates) {
            // Skip if globally cooling down (e.g. recently fired and `resetTicks` not elapsed).
            if (worldGraph.isInteractableUsed(roomId, trap.id)) continue
            // Skip if this character has already tripped this specific trap.
            if (session.hasTrippedTrap(roomId, trap.id)) continue

            // If the player has previously detected this trap, treat it as
            // ON_ACTION-only — they know it's there and step around it. They can
            // still trigger it intentionally via /interact (the existing ON_ACTION
            // path) or fail to detect a trap on first entry. Per Phase 8 plan §5:
            // "If detected, the trap description appears in room text and the
            // player can choose to step around it."
            if (trap.perceptionDC > 0 && session.hasDiscoveredInteractable(roomId, trap.id)) {
                outcomes.add(TrapOutcome.Detected(trap.id, trap.label))
                continue
            }

            // Detection roll: only if trap has a perception DC.
            if (trap.perceptionDC > 0) {
                val perceptionRoll = TrapResolver.rollDetection(effectiveStats, player.level, random)
                if (TrapResolver.resolveDetection(perceptionRoll, trap.perceptionDC)) {
                    session.discoverInteractable(roomId, trap.id)
                    val detectMsg = trap.actionData["detectMessage"]
                        ?: "You spot a trap: ${trap.label}."
                    session.send(ServerMessage.SystemMessage(detectMsg))
                    outcomes.add(TrapOutcome.Detected(trap.id, trap.label))
                    continue
                }
            }

            // Detection failed (or trap is undetectable) → fire it.
            outcomes.add(fireTrap(session, roomId, trap))
        }

        return outcomes.ifEmpty { listOf(TrapOutcome.NoTrap) }
    }

    private suspend fun fireTrap(
        session: PlayerSession,
        roomId: RoomId,
        trap: RoomInteractable
    ): TrapOutcome.Triggered {
        val player = session.player!!
        val effectiveStats = session.effectiveStats()

        val baseDamage = trap.actionData["damage"]?.toIntOrNull() ?: 0
        val saveStat = trap.actionData["saveStat"] ?: ""
        val saveDC = trap.actionData["saveDC"]?.toIntOrNull() ?: 0
        val saveType = parseSaveType(trap.actionData["saveType"])

        val outcome = TrapResolver.resolveSave(
            stats = effectiveStats,
            level = player.level,
            saveStat = saveStat,
            saveDC = saveDC,
            saveType = saveType,
            random = random
        )
        val damage = TrapResolver.computeDamage(baseDamage, outcome)

        val flavor = when (outcome) {
            TrapResolver.SaveOutcome.AVOID -> trap.actionData["dodgeMessage"]
                ?: "You narrowly avoid the ${trap.label}!"
            TrapResolver.SaveOutcome.HALF -> trap.actionData["resistMessage"]
                ?: "You partially resist the ${trap.label}. (-$damage HP)"
            TrapResolver.SaveOutcome.FAIL -> trap.actionData["damageMessage"]
                ?: "${trap.label} hits you! (-$damage HP)"
        }

        if (damage > 0) {
            val effect = EffectApplicator.applyEffect(
                type = "DAMAGE",
                magnitude = damage,
                customMessage = flavor,
                player = player,
                effectiveMaxHp = session.effectiveMaxHp()
            )
            if (effect != null) {
                session.player = player.copy(currentHp = effect.newHp)
                session.send(
                    ServerMessage.EffectTick(
                        effectName = "DAMAGE",
                        message = effect.message,
                        newHp = effect.newHp,
                        newMp = effect.newMp
                    )
                )
            }
        } else {
            // No-damage outcome (clean dodge) — still surface flavor so the player knows something happened.
            session.send(ServerMessage.SystemMessage(flavor))
        }

        // Mark the trap as tripped for THIS character so it doesn't re-fire on every entry.
        session.markTrapTripped(roomId, trap.id)
        // Mark globally used ONLY if the trap actually fired damage. A clean
        // dodge (AVOID) means the trap is still primed for the next victim;
        // it would be perverse for a single dodger to disarm the trap for the
        // whole party.
        if (outcome != TrapResolver.SaveOutcome.AVOID) {
            worldGraph.markInteractableUsed(roomId, trap.id, trap.resetTicks)
        }

        return TrapOutcome.Triggered(trap.id, trap.label, damage, outcome)
    }

    private fun parseSaveType(raw: String?): TrapResolver.SaveType = when (raw?.uppercase()) {
        "DODGE" -> TrapResolver.SaveType.DODGE
        "RESIST" -> TrapResolver.SaveType.RESIST
        else -> TrapResolver.SaveType.NONE
    }
}
