package com.neomud.server.game.commands

import com.neomud.server.game.GameConfig
import com.neomud.server.game.MapRoomFilter
import com.neomud.server.game.MeditationUtils
import com.neomud.server.game.RestUtils
import com.neomud.server.game.RoomFilter
import com.neomud.server.game.StealthUtils

import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.SkillCatalog
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.Direction
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

class MoveCommand(
    private val worldGraph: WorldGraph,
    private val sessionManager: SessionManager,
    private val npcManager: NpcManager,
    private val playerRepository: PlayerRepository,
    private val roomItemManager: RoomItemManager,
    private val skillCatalog: SkillCatalog,
    private val classCatalog: ClassCatalog,
    private val movementTrailManager: com.neomud.server.game.MovementTrailManager? = null,
    private val tutorialService: com.neomud.server.game.TutorialService? = null,
    private val trapManager: com.neomud.server.game.trap.TrapManager? = null
) {
    var departureRecorder: ((String, String, Direction) -> Unit)? = null
    suspend fun execute(session: PlayerSession, direction: Direction) {
        MeditationUtils.breakMeditation(session, "You stop meditating.")
        RestUtils.breakRest(session, "You stop resting.")

        val currentRoomId = session.currentRoomId ?: return
        val currentRoom = worldGraph.getRoom(currentRoomId)

        if (currentRoom == null) {
            session.send(ServerMessage.MoveError("You are in an invalid location."))
            return
        }

        // Check if exit is hidden and player hasn't discovered it
        val hiddenDefs = worldGraph.getHiddenExitDefs(currentRoomId)
        if (direction in hiddenDefs && !session.hasDiscoveredExit(currentRoomId, direction)) {
            session.send(ServerMessage.MoveError("You cannot go ${direction.name.lowercase()}."))
            return
        }

        val targetRoomId = currentRoom.exits[direction]
        if (targetRoomId == null) {
            session.send(ServerMessage.MoveError("You cannot go ${direction.name.lowercase()}."))
            return
        }

        // Check if exit is locked
        if (currentRoom.lockedExits[direction] != null) {
            // Mark this lock as discovered so direction pad and map show it
            if (!session.hasDiscoveredLock(currentRoomId, direction)) {
                session.discoverLock(currentRoomId, direction)
                // Resend room info + map so UI updates with lock indicator
                val filteredRoom = RoomFilter.forPlayer(currentRoom, session, worldGraph)
                val playersInRoom = sessionManager.getVisiblePlayerInfosInRoom(currentRoomId)
                    .filter { it.name != session.playerName }
                val npcsInRoom = npcManager.getNpcsInRoom(currentRoomId)
                session.send(ServerMessage.RoomInfo(filteredRoom, playersInRoom, npcsInRoom))
                val mapRooms = MapRoomFilter.enrichForPlayer(
                    worldGraph.getRoomsNear(currentRoomId), session, worldGraph, sessionManager, npcManager
                )
                session.send(ServerMessage.MapData(mapRooms, currentRoomId))
            }
            val isUnpickable = direction in currentRoom.unpickableExits
            val hint = if (isUnpickable) {
                " Perhaps a key or quest will grant access."
            } else {
                " A skilled lockpick might be able to open it."
            }
            session.send(ServerMessage.MoveError("${direction.lockedExitPhrase.replaceFirstChar { it.uppercase() }} is locked.$hint"))
            tutorialService?.trySend(session, "tut_locked_exit")
            return
        }

        val targetRoom = worldGraph.getRoom(targetRoomId)
        if (targetRoom == null) {
            session.send(ServerMessage.MoveError("That exit leads nowhere."))
            return
        }

        val playerName = session.playerName!!
        val player = session.player

        // Stealth check on room transition
        var sneaking = false
        if (session.isHidden && player != null) {
            val stats = session.effectiveStats()
            val roll = (1..GameConfig.Stealth.PERCEPTION_DICE_SIZE).random()
            val check = stats.agility + stats.willpower / GameConfig.Stealth.DC_WIL_DIVISOR + player.level / GameConfig.Stealth.DC_LEVEL_DIVISOR + roll
            val difficulty = GameConfig.Stealth.SNEAK_DIFFICULTY

            if (check >= difficulty) {
                // Sneak successful - stay hidden, no broadcasts
                sneaking = true
                session.send(ServerMessage.StealthUpdate(true, "Sneaking..."))
            } else {
                // Sneak failed - stealth breaks
                session.isHidden = false
                session.send(ServerMessage.StealthUpdate(false, "Your movement gives you away!"))
            }
        }

        // Record movement trail before leaving room
        movementTrailManager?.recordTrail(currentRoomId, com.neomud.server.game.TrailEntry(
            playerName, playerName, direction, System.currentTimeMillis(), isPlayer = true
        ))

        // Broadcast leave to old room (only if not sneaking)
        if (!sneaking) {
            sessionManager.broadcastToRoom(
                currentRoomId,
                ServerMessage.PlayerLeft(playerName, currentRoomId, direction),
                exclude = playerName
            )
        }

        // Update session
        session.visitedRooms.add(targetRoomId)
        session.currentRoomId = targetRoomId
        session.player = session.player?.copy(currentRoomId = targetRoomId)
        session.combatGraceTicks = GameConfig.Combat.GRACE_TICKS

        // Cancel attack mode on move — but preserve it if a spell is readied,
        // allowing casters to enter rooms already armed for combat
        if (session.attackMode && session.readiedSpellId == null) {
            session.attackMode = false
            session.selectedTargetId = null
            session.send(ServerMessage.AttackModeUpdate(false))
        }
        // Always clear the selected target — auto-targeting will pick a new one
        session.selectedTargetId = null

        // Record departure for tick-based NPC pursuit processing.
        // Pursuit engagement is deferred to the game tick so NPCs evaluate
        // pursuit during their behavior phase, not between ticks.
        if (!sneaking) {
            departureRecorder?.invoke(playerName, currentRoomId, direction)
        }

        // Broadcast enter to new room (only if not sneaking)
        if (!sneaking) {
            sessionManager.broadcastToRoom(
                targetRoomId,
                ServerMessage.PlayerEntered(playerName, targetRoomId, session.toPlayerInfo()),
                exclude = playerName
            )
        } else {
            // Even if the player's sneak check passed, NPCs in the new room get perception rolls
            if (player != null) {
                val stats = session.effectiveStats()
                val stealthDc = stats.agility + stats.willpower / GameConfig.Stealth.DC_WIL_DIVISOR + player.level / GameConfig.Stealth.DC_LEVEL_DIVISOR + GameConfig.Stealth.DC_BASE

                val npcsHere = npcManager.getLivingNpcsInRoom(targetRoomId)
                for (npc in npcsHere) {
                    if (!session.isHidden) break
                    val npcRoll = npc.perception + npc.level + (1..GameConfig.Stealth.PERCEPTION_DICE_SIZE).random()
                    if (npcRoll >= stealthDc) {
                        session.isHidden = false
                        sneaking = false
                        session.send(ServerMessage.StealthUpdate(false, "${npc.name} notices you sneaking in!"))
                        sessionManager.broadcastToRoom(
                            targetRoomId,
                            ServerMessage.PlayerEntered(playerName, targetRoomId, session.toPlayerInfo()),
                            exclude = playerName
                        )
                        break
                    }
                }

                // Non-hidden players in new room get perception checks
                if (session.isHidden) {
                    for (otherSession in sessionManager.getSessionsInRoom(targetRoomId)) {
                        if (otherSession == session || otherSession.isHidden) continue
                        if (!session.isHidden) break
                        val otherPlayer = otherSession.player ?: continue
                        val otherStats = otherSession.effectiveStats()
                        val bonus = StealthUtils.perceptionBonus(otherPlayer.characterClass, classCatalog)
                        val observerRoll = otherStats.willpower + otherStats.intellect / GameConfig.Stealth.PERCEPTION_INT_DIVISOR + otherPlayer.level / GameConfig.Stealth.PERCEPTION_LEVEL_DIVISOR + bonus + (1..GameConfig.Stealth.PERCEPTION_DICE_SIZE).random()
                        if (observerRoll >= stealthDc) {
                            session.isHidden = false
                            sneaking = false
                            session.send(ServerMessage.StealthUpdate(false, "${otherPlayer.name} notices you sneaking in!"))
                            sessionManager.broadcastToRoom(
                                targetRoomId,
                                ServerMessage.PlayerEntered(playerName, targetRoomId, session.toPlayerInfo()),
                                exclude = playerName
                            )
                            break
                        }
                    }
                }
            }
        }

        // Passive perception check for hidden exits in new room
        if (player != null) {
            checkHiddenExits(session, targetRoomId)
        }

        // Send MoveOk + MapData to the player (filtered for hidden exits)
        val filteredTargetRoom = RoomFilter.forPlayer(
            worldGraph.getRoom(targetRoomId) ?: targetRoom, session, worldGraph
        )
        val playersInRoom = sessionManager.getVisiblePlayerInfosInRoom(targetRoomId)
            .filter { it.name != playerName }
        val npcsInRoom = npcManager.getNpcsInRoom(targetRoomId)

        session.send(ServerMessage.MoveOk(direction, filteredTargetRoom, playersInRoom, npcsInRoom))

        val mapRooms = MapRoomFilter.enrichForPlayer(
            worldGraph.getRoomsNear(targetRoomId), session, worldGraph, sessionManager, npcManager
        )
        session.send(ServerMessage.MapData(mapRooms, targetRoomId))

        // Send ground items for new room
        val groundItems = roomItemManager.getGroundItems(targetRoomId)
        val groundCoins = roomItemManager.getGroundCoins(targetRoomId)
        session.send(ServerMessage.RoomItemsUpdate(groundItems, groundCoins))

        // Resolve passive ON_ENTER traps. Fires perception checks and damage; player
        // already has the room rendered so the damage feedback lands in context.
        trapManager?.onPlayerEntered(session, targetRoomId)

        // Auto-detect trainer in room
        if (npcManager.getTrainerInRoom(targetRoomId) != null) {
            session.send(ServerMessage.SystemMessage("A trainer is here. You can interact to train your skills."))
        }

        // Auto-detect vendor in room
        val vendor = npcManager.getVendorInRoom(targetRoomId)
        if (vendor != null) {
            session.send(ServerMessage.SystemMessage("${vendor.name} is here. You can interact to buy and sell items."))
        }

        // Auto-detect crafter in room
        val crafter = npcManager.getCrafterInRoom(targetRoomId)
        if (crafter != null) {
            session.send(ServerMessage.SystemMessage("${crafter.name} is here. You can interact to craft items."))
        }

        // Tutorial triggers on room enter
        if (tutorialService != null && player != null) {
            val hostileNpcs = npcManager.getLivingHostileNpcsInRoom(targetRoomId)
            val hasHostilesHere = hostileNpcs.isNotEmpty()

            // tut_hostile_npc: first time entering a room with hostiles
            if (hasHostilesHere) {
                tutorialService.trySend(session, "tut_hostile_npc")
            }

            // tut_vendor: room has a vendor
            if (npcManager.getVendorInRoom(targetRoomId) != null) {
                tutorialService.trySend(session, "tut_vendor")
            }

            // tut_crafter: room has a crafter
            if (npcManager.getCrafterInRoom(targetRoomId) != null) {
                tutorialService.trySend(session, "tut_crafter")
            }

            // tut_magic: magic class enters first combat zone
            if (hasHostilesHere && tutorialService.classHasMagic(player.characterClass)) {
                tutorialService.trySend(session, "tut_magic")
            }

            // tut_danger_ahead: entering a danger zone from a safe zone
            val currentZone = currentRoom.zoneId
            val targetZone = targetRoom.zoneId
            if (currentZone != targetZone && targetZone in tutorialService.dangerZones) {
                tutorialService.trySend(session, "tut_danger_ahead")
            }
        }

        // Persist position async
        val playerState = session.player
        if (playerState != null) {
            coroutineScope {
                launch(Dispatchers.IO) {
                    try {
                        playerRepository.savePlayerState(playerState)
                    } catch (_: Exception) {
                        // Fire-and-forget
                    }
                }
            }
        }
    }

    private suspend fun checkHiddenExits(session: PlayerSession, roomId: String) {
        val player = session.player ?: return
        val hiddenDefs = worldGraph.getHiddenExitDefs(roomId)
        if (hiddenDefs.isEmpty()) return

        val effStats = session.effectiveStats()
        val bonus = StealthUtils.perceptionBonus(player.characterClass, classCatalog)
        val roll = effStats.willpower + effStats.intellect / GameConfig.Stealth.PERCEPTION_INT_DIVISOR + player.level / GameConfig.Stealth.PERCEPTION_LEVEL_DIVISOR + bonus + (1..GameConfig.Stealth.PERCEPTION_DICE_SIZE).random()

        for ((dir, data) in hiddenDefs) {
            if (session.hasDiscoveredExit(roomId, dir)) continue
            if (roll >= data.perceptionDC) {
                session.discoverExit(roomId, dir)
                worldGraph.revealHiddenExit(roomId, dir)
                session.send(ServerMessage.SystemMessage("You notice a hidden passage to the ${dir.name.lowercase()}!"))
                tutorialService?.trySend(session, "tut_hidden_exit")
            }
        }
    }
}
