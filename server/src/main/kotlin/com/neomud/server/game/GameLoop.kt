package com.neomud.server.game

import com.neomud.server.game.combat.CombatEvent
import com.neomud.server.game.combat.CombatManager
import com.neomud.server.game.inventory.LootService
import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.game.npc.NpcState
import com.neomud.server.game.progression.XpCalculator
import com.neomud.server.game.skills.SkillCheck
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.session.PendingSkill
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.persistence.repository.CoinRepository
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.game.commands.SpellCommand
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.ItemCatalog
import com.neomud.server.world.LootTableCatalog
import com.neomud.server.world.SkillCatalog
import com.neomud.server.world.SpellCatalog
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.ActiveEffect
import com.neomud.shared.model.Direction
import com.neomud.shared.model.EffectType
import com.neomud.shared.model.GroundItem
import com.neomud.shared.model.RoomId
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue

class GameLoop(
    private val sessionManager: SessionManager,
    private val npcManager: NpcManager,
    private val combatManager: CombatManager,
    private val worldGraph: WorldGraph,
    private val lootService: LootService,
    private val lootTableCatalog: LootTableCatalog,
    private val roomItemManager: RoomItemManager,
    private val playerRepository: PlayerRepository,
    private val skillCatalog: SkillCatalog,
    private val classCatalog: ClassCatalog,
    private val itemCatalog: ItemCatalog,
    private val inventoryRepository: InventoryRepository,
    private val coinRepository: CoinRepository,
    private val movementTrailManager: MovementTrailManager? = null,
    private val spellCommand: SpellCommand? = null,
    private val spellCatalog: SpellCatalog? = null,
    private val tutorialService: TutorialService? = null,
    private val playerFlagsRepository: com.neomud.server.persistence.repository.PlayerFlagsRepository? = null,
    private val partyService: com.neomud.server.game.party.PartyService? = null
) {
    private val logger = LoggerFactory.getLogger(GameLoop::class.java)

    @Volatile
    var tickCount: Long = 0L
        private set

    /** Remaining seconds until shutdown, or -1 if not shutting down. */
    @Volatile
    var shutdownSecondsRemaining: Int = -1
        private set

    /** Set of warning thresholds already broadcast, to avoid duplicates. */
    private val broadcastedWarnings = mutableSetOf<Int>()

    val isShuttingDown: Boolean get() = shutdownSecondsRemaining >= 0

    // Pending player departures for NPC pursuit processing during tick
    data class PendingDeparture(
        val playerName: String,
        val fromRoomId: String,
        val direction: Direction
    )

    private val pendingDepartures = ConcurrentLinkedQueue<PendingDeparture>()

    fun recordDeparture(playerName: String, fromRoomId: String, direction: Direction) {
        pendingDepartures.add(PendingDeparture(playerName, fromRoomId, direction))
    }

    /**
     * Initiate a graceful shutdown countdown.
     * @param delaySeconds seconds until server stops (clamped to 0 minimum)
     */
    fun initiateShutdown(delaySeconds: Int) {
        shutdownSecondsRemaining = delaySeconds.coerceAtLeast(0)
        broadcastedWarnings.clear()
        logger.info("Server shutdown initiated: ${shutdownSecondsRemaining}s remaining")
    }

    suspend fun run() {
        logger.info("Game loop started (${GameConfig.Tick.INTERVAL_MS}ms ticks)")
        while (true) {
            delay(GameConfig.Tick.INTERVAL_MS)
            tick()
            if (processShutdownTick()) {
                logger.info("Shutdown countdown complete. Exiting game loop.")
                break
            }
        }
    }

    /**
     * Process shutdown countdown each tick. Returns true when the server should stop.
     * Broadcasts warnings at configured intervals and decrements the counter.
     */
    internal suspend fun processShutdownTick(): Boolean {
        if (!isShuttingDown) return false

        val secondsNow = shutdownSecondsRemaining

        // Broadcast warnings at configured thresholds
        for (threshold in GameConfig.Shutdown.WARNING_AT_SECONDS) {
            if (secondsNow <= threshold && threshold !in broadcastedWarnings) {
                broadcastedWarnings.add(threshold)
                val message = if (threshold == 0) {
                    "Server is shutting down NOW. Goodbye!"
                } else {
                    "Server shutting down in $threshold seconds!"
                }
                sessionManager.broadcastToAll(
                    ServerMessage.ServerShutdown(message, threshold)
                )
                logger.info("Shutdown warning broadcast: $message")
            }
        }

        if (secondsNow <= 0) {
            return true
        }

        // Decrement by the tick interval (in seconds)
        val tickSeconds = (GameConfig.Tick.INTERVAL_MS / 1000.0).toInt().coerceAtLeast(1)
        shutdownSecondsRemaining = (secondsNow - tickSeconds).coerceAtLeast(0)
        return false
    }

    private suspend fun tick() = GameStateLock.withLock {
        // 0. Safety net: force-respawn any player stuck at 0 HP
        for (session in sessionManager.getAllAuthenticatedSessions()) {
            val player = session.player ?: continue
            if (player.currentHp <= 0) {
                val playerName = session.playerName ?: continue
                val respawnRoomId = worldGraph.defaultSpawnRoom
                logger.warn("Safety respawn: $playerName had ${player.currentHp} HP, forcing respawn")

                session.attackMode = false
                session.selectedTargetId = null
                session.readiedSpellId = null
                session.pendingSkill = null
                session.isHidden = false
                session.isMeditating = false
                session.isResting = false
                session.activeEffects.clear()

                val oldRoomId = session.currentRoomId
                session.currentRoomId = respawnRoomId
                session.player = player.copy(
                    currentHp = player.maxHp,
                    currentMp = player.maxMp,
                    currentRoomId = respawnRoomId
                )

                try {
                    session.send(ServerMessage.PlayerDied("unknown", respawnRoomId, player.maxHp, player.maxMp))
                    if (oldRoomId != null && oldRoomId != respawnRoomId) {
                        sessionManager.broadcastToRoom(
                            oldRoomId,
                            ServerMessage.PlayerLeft(playerName, oldRoomId, com.neomud.shared.model.Direction.NORTH),
                            exclude = playerName
                        )
                    }
                    sessionManager.broadcastToRoom(
                        respawnRoomId,
                        ServerMessage.PlayerEntered(playerName, respawnRoomId, session.toPlayerInfo()),
                        exclude = playerName
                    )
                    val room = worldGraph.getRoom(respawnRoomId)
                    if (room != null) {
                        val playersInRoom = sessionManager.getVisiblePlayerInfosInRoom(respawnRoomId).filter { it.name != playerName }
                        val npcsInRoom = npcManager.getNpcsInRoom(respawnRoomId)
                        session.send(ServerMessage.RoomInfo(room, playersInRoom, npcsInRoom))
                    }
                    session.player?.let { p -> playerRepository.savePlayerState(p) }
                } catch (_: Exception) { }
            }
        }

        // 0b. Process pending departures — trigger NPC pursuit before NPC behavior
        val departures = mutableListOf<PendingDeparture>()
        while (true) { departures.add(pendingDepartures.poll() ?: break) }
        if (movementTrailManager != null) {
            for (departure in departures) {
                val detectingHostiles = npcManager.getLivingHostileNpcsInRoom(departure.fromRoomId)
                    .filter { it.behaviorType != "idle" && it.originalBehavior == null }
                for (npc in detectingHostiles) {
                    npcManager.engagePursuit(npc.id, departure.playerName, movementTrailManager, sessionManager)
                }
            }
        }

        // 1. NPC behavior
        // Collect rooms with visible players so hostile NPCs stay to fight
        val roomsWithVisiblePlayers = sessionManager.getAllAuthenticatedSessions()
            .filter { !it.isHidden && it.combatGraceTicks <= 0 && (it.player?.currentHp ?: 0) > 0 }
            .mapNotNull { it.currentRoomId }
            .toSet()
        val npcEvents = npcManager.tick(roomsWithVisiblePlayers)
        for (event in npcEvents) {
            // Record NPC movement trail
            if (event.fromRoomId != null && event.direction != null) {
                movementTrailManager?.recordTrail(event.fromRoomId, TrailEntry(
                    event.npcName, event.npcId, event.direction, System.currentTimeMillis(), isPlayer = false
                ))
            }
            // Broadcast NPC left old room
            if (event.fromRoomId != null && event.direction != null) {
                sessionManager.broadcastToRoom(
                    event.fromRoomId,
                    ServerMessage.NpcLeft(event.npcName, event.fromRoomId, event.direction, event.npcId)
                )
                // Auto-disable attack mode for players who lost their target
                for (session in sessionManager.getSessionsInRoom(event.fromRoomId)) {
                    if (session.attackMode) {
                        if (session.selectedTargetId == event.npcId) {
                            session.selectedTargetId = null
                        }
                        val remaining = npcManager.getLivingHostileNpcsInRoom(event.fromRoomId)
                        if (remaining.isEmpty()) {
                            session.attackMode = false
                            session.selectedTargetId = null
                            session.readiedSpellId = null
                            try {
                                session.send(ServerMessage.AttackModeUpdate(false))
                            } catch (_: Exception) { }
                        }
                    }
                }
            }
            // Broadcast NPC entered new room
            val isSpawn = event.fromRoomId == null
            if (event.toRoomId != null) {
                sessionManager.broadcastToRoom(
                    event.toRoomId,
                    ServerMessage.NpcEntered(
                        event.npcName, event.toRoomId,
                        event.npcId, event.hostile, event.currentHp, event.maxHp,
                        spawned = isSpawn,
                        templateId = event.templateId,
                        attackSound = event.attackSound,
                        missSound = event.missSound,
                        deathSound = event.deathSound,
                        interactSound = event.interactSound,
                        exitSound = event.exitSound
                    )
                )

                // Any NPC entering a room does a perception check against hidden players
                val npc = npcManager.getNpcState(event.npcId)
                if (npc != null) {
                    npcPerceptionScan(npc, event.toRoomId)
                }
            }
        }

        // 1b. Decrement combat grace ticks
        for (session in sessionManager.getAllAuthenticatedSessions()) {
            if (session.combatGraceTicks > 0) {
                session.combatGraceTicks--
            }
        }

        // 1c. Resolve non-combat pending skills
        val scrollKills = mutableListOf<CombatEvent.NpcKilled>()
        for (session in sessionManager.getAllAuthenticatedSessions()) {
            when (val skill = session.pendingSkill) {
                is PendingSkill.Meditate -> {
                    session.pendingSkill = null
                    resolveMeditate(session)
                }
                is PendingSkill.Rest -> {
                    session.pendingSkill = null
                    resolveRest(session)
                }
                is PendingSkill.Track -> {
                    session.pendingSkill = null
                    resolveTrack(session, skill.targetId)
                }
                is PendingSkill.UseItem -> {
                    session.pendingSkill = null
                    resolveUseItem(session, skill.itemId, scrollKills)
                }
                is PendingSkill.CastSpell -> {
                    session.pendingSkill = null
                    resolveCastSpell(session, skill.spellId, skill.targetId)
                }
                is PendingSkill.Sneak -> {
                    session.pendingSkill = null
                    resolveSneak(session)
                }
                is PendingSkill.PickLock -> {
                    session.pendingSkill = null
                    resolvePickLock(session, skill.targetId)
                }
                else -> {} // Bash/Kick handled by CombatManager
            }
        }

        // 2. Combat
        val combatEvents = combatManager.processCombatTick()
        for (event in combatEvents) {
            when (event) {
                is CombatEvent.Hit -> {
                    // Only reveal on a CONNECTED backstab (not miss/dodge/parry — stealth preserved)
                    if (event.isBackstab && !event.isMiss && !event.isDodge && !event.isParry) {
                        val attackerSession = sessionManager.getSession(event.attackerName)
                        if (attackerSession != null) {
                            try {
                                attackerSession.send(ServerMessage.StealthUpdate(false, "You strike from the shadows!"))
                            } catch (_: Exception) { }
                            // Reveal to other players
                            sessionManager.broadcastToRoom(
                                event.roomId,
                                ServerMessage.PlayerEntered(event.attackerName, event.roomId, attackerSession.toPlayerInfo()),
                                exclude = event.attackerName
                            )
                        }
                    }
                    sessionManager.broadcastToRoom(
                        event.roomId,
                        ServerMessage.CombatHit(
                            attackerName = event.attackerName,
                            defenderName = event.defenderName,
                            damage = event.damage,
                            defenderHp = event.defenderHp,
                            defenderMaxHp = event.defenderMaxHp,
                            isPlayerDefender = event.isPlayerDefender,
                            isBackstab = event.isBackstab,
                            isMiss = event.isMiss,
                            isDodge = event.isDodge,
                            isParry = event.isParry,
                            defenderId = event.defenderId
                        )
                    )

                    // Track combat state for deferred tutorials
                    if (event.isPlayerDefender) {
                        val defenderSession = sessionManager.getSession(event.defenderName)
                        if (defenderSession != null) {
                            defenderSession.inCombat = true
                            // tut_low_hp: fire immediately as a non-blocking coach mark
                            if (tutorialService != null && event.defenderHp > 0 &&
                                event.defenderHp < (defenderSession.player?.maxHp ?: 0) / 2) {
                                tutorialService.trySend(defenderSession, "tut_low_hp")
                            }
                        }
                    }
                    // Mark attackers as in combat too
                    val atkSession = sessionManager.getSession(event.attackerName)
                    if (atkSession != null) atkSession.inCombat = true
                }
                is CombatEvent.NpcKilled -> {
                    handleNpcKillEvent(event)
                }
                is CombatEvent.NpcKnockedBack -> {
                    // Broadcast NPC left old room
                    sessionManager.broadcastToRoom(
                        event.fromRoomId,
                        ServerMessage.NpcLeft(event.npcName, event.fromRoomId, event.direction, event.npcId)
                    )

                    // Auto-disable attack mode for players who lost their target in old room
                    for (session in sessionManager.getSessionsInRoom(event.fromRoomId)) {
                        if (session.attackMode && session.selectedTargetId == event.npcId) {
                            session.selectedTargetId = null
                            val remaining = npcManager.getLivingHostileNpcsInRoom(event.fromRoomId)
                            if (remaining.isEmpty()) {
                                session.attackMode = false
                                session.selectedTargetId = null
                                session.readiedSpellId = null
                                try { session.send(ServerMessage.AttackModeUpdate(false)) } catch (_: Exception) {}
                            }
                        }
                    }

                    // Broadcast NPC entered new room
                    sessionManager.broadcastToRoom(
                        event.toRoomId,
                        ServerMessage.NpcEntered(
                            event.npcName, event.toRoomId,
                            event.npcId, event.hostile, event.npcCurrentHp, event.npcMaxHp,
                            spawned = false,
                            templateId = event.templateId,
                            attackSound = event.attackSound,
                            missSound = event.missSound,
                            deathSound = event.deathSound,
                            interactSound = event.interactSound,
                            exitSound = event.exitSound
                        )
                    )

                    // Update map data for players in both rooms
                    updateMapForPlayersInRoom(event.fromRoomId)
                    updateMapForPlayersInRoom(event.toRoomId)
                }
                is CombatEvent.NpcKilledByNpc -> {
                    // A guard was killed by a hostile NPC — no loot, no XP
                    if (!npcManager.markDead(event.killedNpcId)) continue
                    sessionManager.broadcastToRoom(
                        event.roomId,
                        ServerMessage.NpcDied(event.killedNpcId, event.killedNpcName, event.killerNpcName, event.roomId)
                    )
                    logger.info("Guard ${event.killedNpcName} was killed by ${event.killerNpcName} in ${event.roomId}")
                }

                is CombatEvent.NpcPhaseShift -> {
                    sessionManager.broadcastToRoom(
                        event.roomId,
                        ServerMessage.NpcPhaseShift(
                            npcId = event.npcId,
                            npcName = event.npcName,
                            phaseName = event.phaseName,
                            spriteId = event.spriteId,
                            message = event.message,
                            currentHp = event.currentHp,
                            maxHp = event.maxHp,
                            sound = event.sound
                        )
                    )
                }

                is CombatEvent.NpcAbilityUsed -> {
                    sessionManager.broadcastToRoom(
                        event.roomId,
                        ServerMessage.NpcAbilityEffect(
                            npcName = event.npcName,
                            npcId = event.npcId,
                            abilityName = event.abilityName,
                            message = event.message,
                            results = event.results,
                            sound = event.sound
                        )
                    )
                }

                is CombatEvent.PlayerKilled -> {
                    val session = event.playerSession
                    val playerName = session.playerName ?: continue
                    val oldRoomId = session.currentRoomId ?: continue

                    // Send death message to player
                    try {
                        session.send(ServerMessage.PlayerDied(event.killerName, event.respawnRoomId, event.respawnHp, event.respawnMp))
                    } catch (_: Exception) { }

                    // Disable attack mode, stealth, meditation, rest, readied spell, and pending skill
                    session.attackMode = false
                    session.selectedTargetId = null
                    session.readiedSpellId = null
                    session.pendingSkill = null
                    session.isHidden = false
                    session.isMeditating = false
                    session.isResting = false

                    // Broadcast leave from death room
                    sessionManager.broadcastToRoom(
                        oldRoomId,
                        ServerMessage.PlayerLeft(playerName, oldRoomId, com.neomud.shared.model.Direction.NORTH),
                        exclude = playerName
                    )

                    // XP penalty on death (exempt at low levels for newbie protection)
                    val player = session.player
                    val xpPenalty = if (player != null && player.level >= GameConfig.Progression.DEATH_XP_PENALTY_MIN_LEVEL) {
                        (player.currentXp * GameConfig.Progression.DEATH_XP_LOSS_PERCENT).toLong()
                    } else 0L
                    val newXp = ((player?.currentXp ?: 0L) - xpPenalty).coerceAtLeast(0L)

                    // Respawn
                    session.currentRoomId = event.respawnRoomId
                    session.player = session.player?.copy(
                        currentHp = event.respawnHp,
                        currentMp = event.respawnMp,
                        currentRoomId = event.respawnRoomId,
                        currentXp = newXp
                    )
                    session.activeEffects.clear()

                    // Notify client of XP loss so it stays in sync
                    if (xpPenalty > 0 && session.player != null) {
                        try {
                            session.send(ServerMessage.XpGained(
                                amount = -xpPenalty,
                                currentXp = newXp,
                                xpToNextLevel = session.player!!.xpToNextLevel
                            ))
                        } catch (_: Exception) { }
                    }

                    // Broadcast enter to spawn room
                    sessionManager.broadcastToRoom(
                        event.respawnRoomId,
                        ServerMessage.PlayerEntered(playerName, event.respawnRoomId, session.toPlayerInfo()),
                        exclude = playerName
                    )

                    // Send room info and map to respawned player
                    val room = worldGraph.getRoom(event.respawnRoomId)
                    if (room != null) {
                        val playersInRoom = sessionManager.getVisiblePlayerInfosInRoom(event.respawnRoomId)
                            .filter { it.name != playerName }
                        val npcsInRoom = npcManager.getNpcsInRoom(event.respawnRoomId)
                        try {
                            session.send(ServerMessage.RoomInfo(room, playersInRoom, npcsInRoom))
                            val mapRooms = MapRoomFilter.enrichForPlayer(
                                worldGraph.getRoomsNear(event.respawnRoomId), session, worldGraph, sessionManager, npcManager
                            )
                            session.send(ServerMessage.MapData(mapRooms, event.respawnRoomId))

                            // Send ground items for respawn room
                            val groundItems = roomItemManager.getGroundItems(event.respawnRoomId)
                            val groundCoins = roomItemManager.getGroundCoins(event.respawnRoomId)
                            session.send(ServerMessage.RoomItemsUpdate(groundItems, groundCoins))
                        } catch (_: Exception) { }
                    }

                    // tut_death: first death tutorial
                    if (tutorialService != null) {
                        val lvl = session.player?.level ?: 1
                        tutorialService.trySend(session, "tut_death",
                            contentOverride = tutorialService.deathContent(lvl))
                    }

                    // Persist death state
                    session.player?.let { p ->
                        try { playerRepository.savePlayerState(p) } catch (_: Exception) { }
                    }
                }
            }
        }

        // 2b. Process scroll/consumable NPC kills (from resolveUseItem targetDamage)
        for (kill in scrollKills) {
            handleNpcKillEvent(kill)
        }

        // 3. Tick down skill cooldowns
        for (session in sessionManager.getAllAuthenticatedSessions()) {
            val iter = session.skillCooldowns.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                entry.setValue(entry.value - 1)
                if (entry.value <= 0) iter.remove()
            }
        }

        // 3a. Tick down NPC ability cooldowns
        combatManager.tickNpcAbilityCooldowns()

        // 3b. Tick exit reset timers (lock re-lock, hidden re-hide)
        val resetEvents = worldGraph.tickResetTimers()
        for (event in resetEvents) {
            when (event) {
                is com.neomud.server.world.ExitResetEvent.Relocked -> {
                    sessionManager.broadcastToRoom(event.roomId,
                        ServerMessage.SystemMessage("You hear a click as the ${event.direction.name.lowercase()} door locks."))
                    resendRoomInfoToPlayersInRoom(event.roomId)
                }
                is com.neomud.server.world.ExitResetEvent.Rehidden -> {
                    for (session in sessionManager.getSessionsInRoom(event.roomId)) {
                        session.forgetExit(event.roomId, event.direction)
                    }
                    sessionManager.broadcastToRoom(event.roomId,
                        ServerMessage.SystemMessage("The passage to the ${event.direction.name.lowercase()} seems to vanish..."))
                    resendRoomInfoToPlayersInRoom(event.roomId)
                }
            }
        }

        // 3c. Tick interactable reset timers
        val interactableResets = worldGraph.tickInteractableTimers()
        for (event in interactableResets) {
            sessionManager.broadcastToRoom(event.roomId,
                ServerMessage.SystemMessage("The ${event.featureLabel} resets..."))
            resendRoomInfoToPlayersInRoom(event.roomId)
        }

        // 3d. Tick per-player interactable cooldowns
        for (session in sessionManager.getAllAuthenticatedSessions()) {
            val iter = session.interactableCooldowns.iterator()
            while (iter.hasNext()) {
                val entry = iter.next()
                entry.setValue(entry.value - 1)
                if (entry.value <= 0) iter.remove()
            }
        }

        // 4. NPC perception scans for hidden players
        npcPerceptionPhase()

        // 4b. Player perception scans for hidden players
        playerPerceptionPhase()

        // 4c. Meditation MP restoration
        meditationPhase()

        // 4d. Rest HP restoration
        restPhase()

        // 5. Process active effects on all authenticated players
        for (session in sessionManager.getAllAuthenticatedSessions()) {
            val effects = session.activeEffects.toList()
            if (effects.isEmpty()) continue
            val expired = mutableListOf<ActiveEffect>()

            for (effect in effects) {
                val player = session.player ?: continue

                val result = EffectApplicator.applyEffect(effect.type.name, effect.magnitude, "", player, effectiveMaxHp = session.effectiveMaxHp())
                if (result != null) {
                    session.player = player.copy(currentHp = result.newHp, currentMp = result.newMp)
                    try {
                        session.send(ServerMessage.EffectTick(effect.name, result.message, result.newHp, newMp = result.newMp))
                    } catch (_: Exception) { /* session closing */ }
                } else {
                    // Stat buff effects — show what stat is boosted
                    val statName = when (effect.type) {
                        com.neomud.shared.model.EffectType.BUFF_STRENGTH -> "strength"
                        com.neomud.shared.model.EffectType.BUFF_AGILITY -> "agility"
                        com.neomud.shared.model.EffectType.BUFF_INTELLECT -> "intellect"
                        com.neomud.shared.model.EffectType.BUFF_WILLPOWER -> "willpower"
                        com.neomud.shared.model.EffectType.HASTE -> "haste"
                        com.neomud.shared.model.EffectType.BUFF_DAMAGE -> "damage"
                        com.neomud.shared.model.EffectType.BUFF_MAX_HP -> "maximum HP"
                        else -> null
                    }
                    val message = if (statName != null) {
                        "${effect.name} boosts your $statName by ${effect.magnitude}. (${effect.remainingTicks - 1} ticks remaining)"
                    } else {
                        "${effect.name} continues to affect you."
                    }
                    try {
                        session.send(ServerMessage.EffectTick(effect.name, message, player.currentHp))
                    } catch (_: Exception) { /* session closing */ }
                }

                val updated = effect.copy(remainingTicks = effect.remainingTicks - 1)
                if (updated.remainingTicks <= 0) {
                    expired.add(effect)
                } else {
                    val idx = session.activeEffects.indexOf(effect)
                    if (idx >= 0) session.activeEffects[idx] = updated
                }
            }

            session.activeEffects.removeAll(expired)

            // Notify player about expired effects
            for (effect in expired) {
                try {
                    session.send(ServerMessage.SystemMessage("${effect.name} has worn off."))
                } catch (_: Exception) { /* session closing */ }
            }

            // When BUFF_MAX_HP expires, cap currentHp to new effective max
            if (expired.any { it.type == EffectType.BUFF_MAX_HP }) {
                val p = session.player
                if (p != null) {
                    val effectiveMax = session.effectiveMaxHp()
                    if (p.currentHp > effectiveMax) {
                        session.player = p.copy(currentHp = effectiveMax)
                    }
                }
            }

            // Send updated active effects list to client
            try {
                session.send(ServerMessage.ActiveEffectsUpdate(session.activeEffects.toList()))
            } catch (_: Exception) { /* session closing */ }

            // tut_status_effect: first buff/debuff applied
            if (tutorialService != null && session.activeEffects.isNotEmpty()) {
                tutorialService.trySend(session, "tut_status_effect")
            }
        }

        // 5b. Process active effects on NPCs (DoT, HoT, etc.)
        val npcEffectKills = mutableListOf<CombatEvent.NpcKilled>()
        for (npc in npcManager.getLivingNpcsWithEffects()) {
            val effects = npc.activeEffects.toList()
            val expired = mutableListOf<ActiveEffect>()

            for (effect in effects) {
                when (effect.type) {
                    EffectType.POISON, EffectType.DAMAGE, EffectType.MANA_DRAIN -> {
                        npc.currentHp = (npc.currentHp - effect.magnitude).coerceAtLeast(0)
                        sessionManager.broadcastToRoom(
                            npc.currentRoomId,
                            ServerMessage.SpellEffect(
                                casterName = effect.casterId,
                                targetName = npc.name,
                                spellName = effect.name,
                                effectAmount = effect.magnitude,
                                targetNewHp = npc.currentHp,
                                targetMaxHp = npc.maxHp,
                                targetId = npc.id
                            )
                        )
                    }
                    EffectType.HEAL_OVER_TIME -> {
                        npc.currentHp = (npc.currentHp + effect.magnitude).coerceAtMost(npc.maxHp)
                    }
                    else -> { /* Buff types: no per-tick action needed */ }
                }

                val updated = effect.copy(remainingTicks = effect.remainingTicks - 1)
                if (updated.remainingTicks <= 0) {
                    expired.add(effect)
                } else {
                    val idx = npc.activeEffects.indexOf(effect)
                    if (idx >= 0) npc.activeEffects[idx] = updated
                }
            }

            npc.activeEffects.removeAll(expired)

            // Check if NPC died from effect damage
            if (npc.currentHp <= 0 && npc.isAlive) {
                val killerName = effects
                    .filter { it.type in setOf(EffectType.POISON, EffectType.DAMAGE, EffectType.MANA_DRAIN) }
                    .firstOrNull()?.casterId ?: "unknown"
                npcEffectKills.add(
                    CombatEvent.NpcKilled(
                        npcId = npc.id,
                        npcName = npc.name,
                        killerName = killerName,
                        roomId = npc.currentRoomId,
                        npcLevel = npc.level,
                        xpReward = npc.xpReward,
                        templateId = npc.templateId
                    )
                )
            }
        }

        // Process NPC effect kills through existing kill handling
        for (event in npcEffectKills) {
            handleNpcKillEvent(event)
        }

        // 6. Room effects
        for (session in sessionManager.getAllAuthenticatedSessions()) {
            val player = session.player ?: continue
            val roomId = session.currentRoomId ?: continue
            val room = worldGraph.getRoom(roomId) ?: continue
            for (effect in room.effects) {
                val p = session.player ?: continue
                val result = EffectApplicator.applyEffect(effect.type, effect.value, effect.message, p, effectiveMaxHp = session.effectiveMaxHp()) ?: continue
                session.player = p.copy(currentHp = result.newHp, currentMp = result.newMp)
                val effectName = effect.type.lowercase().replaceFirstChar { it.uppercase() } + " Aura"
                try {
                    session.send(ServerMessage.EffectTick(effectName, result.message, result.newHp, effect.sound, result.newMp))
                } catch (_: Exception) { /* session closing */ }
            }
        }

        // 7. Post-combat tutorials
        if (tutorialService != null) {
            for (session in sessionManager.getAllAuthenticatedSessions()) {
                val roomId = session.currentRoomId ?: continue
                val hostiles = npcManager.getLivingHostileNpcsInRoom(roomId)

                // Check if combat just ended (was in combat, no more hostiles, not attacking)
                if (session.inCombat && hostiles.isEmpty() && !session.attackMode) {
                    session.inCombat = false

                    // tut_stealth: deferred to post-first-combat for stealth classes
                    val player = session.player
                    if (player != null && tutorialService.classHasStealth(player.characterClass)) {
                        tutorialService.trySend(session, "tut_stealth")
                    }
                }
            }
        }

        // 8. Prune stale movement trails
        movementTrailManager?.pruneStale()

        // 9. Prune expired ground items
        roomItemManager.pruneExpired()

        // 10. Party tick: expire invites, grace periods, periodic member updates
        tickCount++
        if (partyService != null) {
            partyService.tickExpireInvites(tickCount)
            val graceExpiries = partyService.tickGracePeriods()
            for (expiry in graceExpiries) {
                sessionManager.getSession(expiry.lastRemainingMember)?.send(
                    ServerMessage.PartyDisbanded("${expiry.expiredPlayer} disconnected — party too small to continue.")
                )
            }
            // Auto-resume PAUSED followers who are now in the same room as their target and not in combat
            for (session in sessionManager.getAllAuthenticatedSessions()) {
                if (session.followState != com.neomud.shared.model.FollowState.PAUSED) continue
                if (session.attackMode) continue
                val target = session.followTarget ?: continue
                val targetSession = sessionManager.getSession(target) ?: continue
                if (session.currentRoomId == targetSession.currentRoomId) {
                    session.followState = com.neomud.shared.model.FollowState.ACTIVE
                    try {
                        session.send(ServerMessage.FollowUpdate(
                            session.playerName ?: continue, target,
                            com.neomud.shared.model.FollowState.ACTIVE
                        ))
                        session.send(ServerMessage.SystemMessage("You resume following $target."))
                    } catch (_: Exception) { }
                }
            }

            if (tickCount % GameConfig.Party.MEMBER_UPDATE_TICKS == 0L) {
                for (session in sessionManager.getAllAuthenticatedSessions()) {
                    val party = partyService.getPartyForPlayer(session.playerName ?: continue) ?: continue
                    val p = session.player ?: continue
                    val update = ServerMessage.PartyMemberUpdate(
                        memberName = p.name,
                        currentHp = p.currentHp,
                        maxHp = p.maxHp,
                        currentMp = p.currentMp,
                        maxMp = p.maxMp,
                        roomId = session.currentRoomId ?: ""
                    )
                    sessionManager.broadcastToParty(party.members, update, exclude = p.name)
                }
            }
        }
    }

    /**
     * Periodic scan: each NPC in a room with hidden players rolls perception.
     * Only one check per NPC per tick to avoid spam.
     */
    private suspend fun npcPerceptionPhase() {
        // Collect rooms that have hidden players
        val hiddenByRoom = mutableMapOf<RoomId, MutableList<PlayerSession>>()
        for (session in sessionManager.getAllAuthenticatedSessions()) {
            if (session.isHidden) {
                val roomId = session.currentRoomId ?: continue
                hiddenByRoom.getOrPut(roomId) { mutableListOf() }.add(session)
            }
        }

        for ((roomId, hiddenPlayers) in hiddenByRoom) {
            val npcsInRoom = npcManager.getLivingNpcsInRoom(roomId)
            for (npc in npcsInRoom) {
                for (session in hiddenPlayers) {
                    if (!session.isHidden) continue // may have been revealed by a prior NPC this tick
                    npcPerceptionCheck(npc, session, roomId)
                }
            }
        }
    }

    /**
     * Immediate scan when an NPC enters a room - checks all hidden players there.
     */
    private suspend fun npcPerceptionScan(npc: NpcState, roomId: RoomId) {
        val hiddenPlayers = sessionManager.getSessionsInRoom(roomId).filter { it.isHidden }
        for (session in hiddenPlayers) {
            npcPerceptionCheck(npc, session, roomId)
        }
    }

    /**
     * NPC perception vs player stealth.
     * NPC roll: perception + level + d20
     * Player passive stealth DC: AGI + WIL/2 + level/2 + 10
     */
    private suspend fun npcPerceptionCheck(npc: NpcState, session: PlayerSession, roomId: RoomId) {
        val player = session.player ?: return
        val playerName = session.playerName ?: return

        val npcRoll = npc.perception + npc.level + (1..GameConfig.Stealth.PERCEPTION_DICE_SIZE).random()
        val effStats = session.effectiveStats()
        val stealthDc = effStats.agility + effStats.willpower / GameConfig.Stealth.DC_WIL_DIVISOR + player.level / GameConfig.Stealth.DC_LEVEL_DIVISOR + GameConfig.Stealth.DC_BASE

        if (npcRoll >= stealthDc) {
            // Detected!
            session.isHidden = false
            try {
                session.send(ServerMessage.StealthUpdate(false, "${npc.name} spots you lurking in the shadows!"))
            } catch (_: Exception) { }

            // Reveal to other players in the room
            sessionManager.broadcastToRoom(
                roomId,
                ServerMessage.PlayerEntered(playerName, roomId, session.toPlayerInfo()),
                exclude = playerName
            )
        }
    }

    private suspend fun resendRoomInfoToPlayersInRoom(roomId: RoomId) {
        val room = worldGraph.getRoom(roomId) ?: return
        for (session in sessionManager.getSessionsInRoom(roomId)) {
            val playerName = session.playerName ?: continue
            val filteredRoom = RoomFilter.forPlayer(room, session, worldGraph)
            val playersInRoom = sessionManager.getVisiblePlayerInfosInRoom(roomId)
                .filter { it.name != playerName }
            val npcsInRoom = npcManager.getNpcsInRoom(roomId)
            try {
                session.send(ServerMessage.RoomInfo(filteredRoom, playersInRoom, npcsInRoom))
                val mapRooms = MapRoomFilter.enrichForPlayer(
                    worldGraph.getRoomsNear(roomId), session, worldGraph, sessionManager, npcManager
                )
                session.send(ServerMessage.MapData(mapRooms, roomId))
            } catch (_: Exception) { }
        }
    }

    private suspend fun resolveRest(session: PlayerSession) {
        val player = session.player ?: return

        // Break stealth if hidden
        StealthUtils.breakStealth(session, sessionManager, "Resting reveals your presence!")

        // Skill check: health + willpower/2 + level/2 + d20 vs difficulty
        val skillDef = skillCatalog.getSkill("REST")
        if (skillDef == null) {
            try { session.send(ServerMessage.SystemMessage("Rest skill not found.")) } catch (_: Exception) {}
            return
        }

        // Cooldown applies regardless of pass/fail
        session.skillCooldowns["REST"] = skillDef.cooldownTicks

        val effStats = session.effectiveStats()
        val result = SkillCheck.check(skillDef, effStats, player.level)

        if (!result.success) {
            try { session.send(ServerMessage.RestUpdate(false, "You fail to settle into a restful state.")) } catch (_: Exception) {}
            return
        }

        // Break meditation if meditating (mutual exclusion)
        MeditationUtils.breakMeditation(session, "You stop meditating to rest.")

        session.isResting = true
        try { session.send(ServerMessage.RestUpdate(true, "You settle into a restful state.")) } catch (_: Exception) {}
    }

    private suspend fun resolveMeditate(session: PlayerSession) {
        val player = session.player ?: return

        // Break stealth if hidden
        StealthUtils.breakStealth(session, sessionManager, "Meditating reveals your presence!")

        // Skill check: WIL + INT/2 + level/2 + d20 vs difficulty
        val skillDef = skillCatalog.getSkill("MEDITATE")
        if (skillDef == null) {
            try { session.send(ServerMessage.SystemMessage("Meditate skill not found.")) } catch (_: Exception) {}
            return
        }

        // Cooldown applies regardless of pass/fail
        session.skillCooldowns["MEDITATE"] = skillDef.cooldownTicks

        val effStats = session.effectiveStats()
        val result = SkillCheck.check(skillDef, effStats, player.level)

        if (!result.success) {
            try { session.send(ServerMessage.MeditateUpdate(false, "You fail to focus your mind.")) } catch (_: Exception) {}
            return
        }

        session.isMeditating = true
        try { session.send(ServerMessage.MeditateUpdate(true, "You enter a meditative state.")) } catch (_: Exception) {}
    }

    private suspend fun resolveTrack(session: PlayerSession, targetId: String?) {
        val roomId = session.currentRoomId ?: return
        val player = session.player ?: return
        val trailMgr = movementTrailManager ?: return

        val trackSkill = skillCatalog.getSkill("TRACK")
        session.skillCooldowns["TRACK"] = trackSkill?.cooldownTicks ?: 4

        // Skill check: willpower + agility/2 + level/2 + d20
        val effStats = session.effectiveStats()
        val roll = (1..GameConfig.Stealth.PERCEPTION_DICE_SIZE).random()
        val check = effStats.willpower + effStats.agility / GameConfig.Stealth.DC_WIL_DIVISOR + player.level / GameConfig.Stealth.DC_LEVEL_DIVISOR + roll

        // Get trails in current room (optionally filtered by target)
        val trails = trailMgr.getTrails(roomId, targetId)

        val trackBaseDifficulty = GameConfig.Trails.TRACK_BASE_DIFFICULTY
        if (trails.isEmpty()) {
            if (check >= trackBaseDifficulty) {
                try { session.send(ServerMessage.TrackResult(success = false, message = "You don't find any tracks here.")) } catch (_: Exception) {}
            } else {
                try { session.send(ServerMessage.TrackResult(success = false, message = "You fail to find any tracks.")) } catch (_: Exception) {}
            }
            checkHiddenExits(session, roomId, check)
            return
        }

        // Use freshest trail
        val freshest = trails.first()
        val penalty = trailMgr.stalenessPenalty(freshest)
        val difficulty = trackBaseDifficulty + penalty

        if (check >= difficulty) {
            val dirName = freshest.direction.name.lowercase()
            try { session.send(ServerMessage.TrackResult(success = true, direction = freshest.direction, targetName = freshest.entityName, message = "You find tracks left by ${freshest.entityName} leading $dirName.")) } catch (_: Exception) {}
        } else {
            try { session.send(ServerMessage.TrackResult(success = false, message = "You find faint tracks but cannot make them out.")) } catch (_: Exception) {}
        }

        checkHiddenExits(session, roomId, check)
    }

    private suspend fun resolveUseItem(session: PlayerSession, itemId: String, pendingNpcKills: MutableList<CombatEvent.NpcKilled> = mutableListOf()) {
        val playerName = session.playerName ?: return
        val player = session.player ?: return

        val item = itemCatalog.getItem(itemId)
        if (item == null || item.type != "consumable") {
            try { session.send(ServerMessage.Error("That item cannot be used.")) } catch (_: Exception) {}
            return
        }

        if (UseEffectProcessor.isHealOnly(item.useEffect) && player.currentHp >= session.effectiveMaxHp()) {
            try { session.send(ServerMessage.SystemMessage("Your health is already full.")) } catch (_: Exception) {}
            return
        }

        val result = UseEffectProcessor.process(item.useEffect, player, item.name, effectiveMaxHp = session.effectiveMaxHp())
        if (result == null) {
            try { session.send(ServerMessage.Error("${item.name} has no usable effect.")) } catch (_: Exception) {}
            return
        }

        // Re-validate ownership at resolution time (item may have been dropped between queue and tick)
        val removed = inventoryRepository.removeItem(playerName, itemId, 1)
        if (!removed) {
            try { session.send(ServerMessage.Error("You no longer have that item.")) } catch (_: Exception) {}
            return
        }

        session.player = result.updatedPlayer
        session.activeEffects.addAll(result.newEffects)

        // Handle cure_dot — remove POISON and DAMAGE effects
        if (result.cureDot) {
            session.activeEffects.removeAll { it.type in setOf(EffectType.POISON, EffectType.DAMAGE) }
        }

        // Handle targetDamage — apply instant damage to combat target
        if (result.targetDamage > 0) {
            val targetId = session.selectedTargetId
            val npc = if (targetId != null) npcManager.getNpcState(targetId) else null
            val roomId = session.currentRoomId
            if (npc != null && roomId != null && npc.currentRoomId == roomId && npc.currentHp > 0) {
                npc.currentHp -= result.targetDamage
                sessionManager.broadcastToRoom(roomId, ServerMessage.SpellEffect(
                    casterName = playerName,
                    targetName = npc.name,
                    spellName = item.name,
                    effectAmount = result.targetDamage,
                    targetNewHp = npc.currentHp.coerceAtLeast(0),
                    targetMaxHp = npc.maxHp,
                    targetId = npc.id
                ))
                if (npc.currentHp <= 0) {
                    pendingNpcKills.add(CombatEvent.NpcKilled(
                        npcId = npc.id,
                        npcName = npc.name,
                        killerName = playerName,
                        roomId = roomId,
                        npcLevel = npc.level,
                        xpReward = npc.xpReward,
                        templateId = npc.templateId
                    ))
                }
            } else {
                // No valid target — refund the item
                inventoryRepository.addItem(playerName, itemId, 1)
                try { session.send(ServerMessage.Error("You have no target for that scroll.")) } catch (_: Exception) {}
                return
            }
        }

        val message = result.messages.joinToString(" ")
        try {
            session.send(ServerMessage.ItemUsed(
                itemName = item.name,
                message = message,
                newHp = result.updatedPlayer.currentHp,
                newMp = result.updatedPlayer.currentMp
            ))
            if (result.newEffects.isNotEmpty() || result.cureDot) {
                session.send(ServerMessage.ActiveEffectsUpdate(session.activeEffects.toList()))
            }
            sendInventoryUpdateForSession(session)
        } catch (_: Exception) {}
    }

    private suspend fun sendInventoryUpdateForSession(session: PlayerSession) {
        val playerName = session.playerName ?: return
        val inventory = inventoryRepository.getInventory(playerName)
        val equipment = inventoryRepository.getEquippedItems(playerName)
        val coins = coinRepository.getCoins(playerName)
        session.send(ServerMessage.InventoryUpdate(inventory, equipment, coins))
    }

    private suspend fun checkHiddenExits(session: PlayerSession, roomId: String, check: Int) {
        val hiddenDefs = worldGraph.getHiddenExitDefs(roomId)
        if (hiddenDefs.isEmpty()) return

        val trackRoll = check + GameConfig.Trails.TRACK_HIDDEN_EXIT_BONUS
        var found = false
        for ((dir, data) in hiddenDefs) {
            if (session.hasDiscoveredExit(roomId, dir)) continue
            if (trackRoll >= data.perceptionDC) {
                session.discoverExit(roomId, dir)
                worldGraph.revealHiddenExit(roomId, dir)
                try { session.send(ServerMessage.SystemMessage("Your tracking skills reveal a hidden passage to the ${dir.name.lowercase()}!")) } catch (_: Exception) {}
                tutorialService?.trySend(session, "tut_hidden_exit")
                found = true
            }
        }
        if (found) {
            val room = worldGraph.getRoom(roomId)
            if (room != null) {
                val filteredRoom = RoomFilter.forPlayer(room, session, worldGraph)
                try { session.send(ServerMessage.RoomInfo(filteredRoom, emptyList(), emptyList())) } catch (_: Exception) {}
            }
        }
    }

    private suspend fun updateMapForPlayersInRoom(roomId: RoomId) {
        for (s in sessionManager.getSessionsInRoom(roomId)) {
            try {
                val mapRooms = MapRoomFilter.enrichForPlayer(
                    worldGraph.getRoomsNear(roomId), s, worldGraph, sessionManager, npcManager
                )
                s.send(ServerMessage.MapData(mapRooms, roomId))
            } catch (_: Exception) {}
        }
    }

    /**
     * Handle an NPC kill: mark dead, broadcast death, roll/drop loot, award XP,
     * and auto-disable attack mode for players with no remaining targets.
     */
    private suspend fun handleNpcKillEvent(event: CombatEvent.NpcKilled) {
        if (!npcManager.markDead(event.npcId)) return
        sessionManager.broadcastToRoom(
            event.roomId,
            ServerMessage.NpcDied(event.npcId, event.npcName, event.killerName, event.roomId)
        )

        // Roll loot and drop on ground (use templateId for spawned copies)
        val lootKey = event.templateId.ifEmpty { event.npcId }
        val lootTable = lootTableCatalog.getLootTable(lootKey)
        val coinDrop = lootTableCatalog.getCoinDrop(lootKey)
        val lootedItems = lootService.rollLoot(lootTable)
        val coins = lootService.rollCoins(coinDrop)

        if (lootedItems.isNotEmpty() || !coins.isEmpty()) {
            if (lootedItems.isNotEmpty()) {
                roomItemManager.addItems(
                    event.roomId,
                    lootedItems.map { GroundItem(it.itemId, it.quantity) }
                )
            }
            roomItemManager.addCoins(event.roomId, coins)

            sessionManager.broadcastToRoom(
                event.roomId,
                ServerMessage.LootDropped(event.npcName, lootedItems, coins)
            )

            val groundItems = roomItemManager.getGroundItems(event.roomId)
            val groundCoins = roomItemManager.getGroundCoins(event.roomId)
            sessionManager.broadcastToRoom(
                event.roomId,
                ServerMessage.RoomItemsUpdate(groundItems, groundCoins)
            )
        }

        // Award XP — party members in the same room share via bonus pool
        if (event.xpReward > 0) {
            val killerSession = sessionManager.getSession(event.killerName)
            val partyMembers = if (partyService != null && partyService.isInParty(event.killerName)) {
                partyService.getMembersInRoom(event.killerName, event.roomId) { name ->
                    sessionManager.getSession(name)?.currentRoomId
                }
            } else {
                listOf(event.killerName)
            }

            val membersInRoom = partyMembers.size
            val partyBaseXp = XpCalculator.partyXpPerMember(event.xpReward, membersInRoom)

            for (memberName in partyMembers) {
                val memberSession = sessionManager.getSession(memberName) ?: continue
                val memberPlayer = memberSession.player ?: continue
                val xpGained = XpCalculator.xpForKill(event.npcLevel, memberPlayer.level, partyBaseXp)
                val newXp = memberPlayer.currentXp + xpGained
                memberSession.player = memberPlayer.copy(currentXp = newXp)
                try {
                    memberSession.send(ServerMessage.XpGained(xpGained, newXp, memberPlayer.xpToNextLevel))
                    if (XpCalculator.isReadyToLevel(newXp, memberPlayer.xpToNextLevel, memberPlayer.level)) {
                        memberSession.send(ServerMessage.SystemMessage("You have enough experience to level up! Visit a trainer."))
                        tutorialService?.trySend(memberSession, "tut_level_up")
                    }
                    playerRepository.savePlayerState(memberSession.player!!)

                    if (tutorialService != null && !memberSession.firstKillDone) {
                        memberSession.firstKillDone = true
                        tutorialService.trySend(memberSession, "tut_first_kill")
                    }
                } catch (_: Exception) { }
            }

        }

        // Set kill flags on the killer (e.g., boss-gated exits).
        // Uses event.killerName directly — no session lookup — so flags persist
        // even if the killer disconnects between the kill and tick resolution.
        val templateData = npcManager.getTemplateData(event.templateId.ifEmpty { event.npcId })
        if (templateData != null && templateData.onKillFlags.isNotEmpty() && playerFlagsRepository != null) {
            for ((flagKey, flagValue) in templateData.onKillFlags) {
                playerFlagsRepository.setFlag(event.killerName, flagKey, flagValue)
            }
        }

        // Auto-disable attack mode for players with no remaining targets
        for (session in sessionManager.getSessionsInRoom(event.roomId)) {
            if (session.attackMode) {
                val remaining = npcManager.getLivingHostileNpcsInRoom(event.roomId)
                if (remaining.isEmpty()) {
                    session.attackMode = false
                    session.selectedTargetId = null
                    session.readiedSpellId = null
                    try {
                        session.send(ServerMessage.AttackModeUpdate(false))
                    } catch (_: Exception) { }
                } else if (session.selectedTargetId == event.npcId) {
                    session.selectedTargetId = null
                }
            }
        }
    }

    /**
     * Resolve a queued CastSpell via SpellCommand.resolve(), then handle kill if target died.
     */
    private suspend fun resolveCastSpell(session: PlayerSession, spellId: String, targetId: String?) {
        val cmd = spellCommand ?: return
        val target = cmd.resolve(session, spellId, targetId)
        if (target != null && target.currentHp <= 0 && target.isAlive) {
            val playerName = session.playerName ?: return
            handleNpcKillEvent(CombatEvent.NpcKilled(
                npcId = target.id,
                npcName = target.name,
                killerName = playerName,
                roomId = target.currentRoomId,
                npcLevel = target.level,
                xpReward = target.xpReward,
                templateId = target.templateId
            ))
        }
    }

    /**
     * Resolve a queued Sneak attempt: skill check, NPC/player perception rolls, hide or fail.
     */
    private suspend fun resolveSneak(session: PlayerSession) {
        val roomId = session.currentRoomId ?: return
        val playerName = session.playerName ?: return
        val player = session.player ?: return

        // Skill check: AGI + WIL/2 + level/2 + d20 vs SNEAK_DIFFICULTY (using buffed stats)
        val stats = session.effectiveStats()
        val roll = (1..GameConfig.Stealth.PERCEPTION_DICE_SIZE).random()
        val check = stats.agility + stats.willpower / GameConfig.Stealth.DC_WIL_DIVISOR + player.level / GameConfig.Stealth.DC_LEVEL_DIVISOR + roll
        val difficulty = GameConfig.Stealth.SNEAK_DIFFICULTY

        val sneakSkill = skillCatalog.getSkill("SNEAK")
        session.skillCooldowns["SNEAK"] = sneakSkill?.cooldownTicks ?: 2

        if (check < difficulty) {
            try { session.send(ServerMessage.StealthUpdate(false, "You fail to find cover!")) } catch (_: Exception) {}
            return
        }

        // Stealth check passed - NPCs in the room get a perception check
        val stealthDc = stats.agility + stats.willpower / GameConfig.Stealth.DC_WIL_DIVISOR + player.level / GameConfig.Stealth.DC_LEVEL_DIVISOR + GameConfig.Stealth.DC_BASE
        val npcsInRoom = npcManager.getLivingNpcsInRoom(roomId)
        var detected = false
        var detectorName = ""

        for (npc in npcsInRoom) {
            val npcRoll = npc.perception + npc.level + (1..GameConfig.Stealth.PERCEPTION_DICE_SIZE).random()
            if (npcRoll >= stealthDc) {
                detected = true
                detectorName = npc.name
                break
            }
        }

        // Non-hidden players in room get perception checks
        if (!detected) {
            for (otherSession in sessionManager.getSessionsInRoom(roomId)) {
                if (otherSession == session || otherSession.isHidden) continue
                val otherPlayer = otherSession.player ?: continue
                val otherStats = otherSession.effectiveStats()
                val bonus = StealthUtils.perceptionBonus(otherPlayer.characterClass, classCatalog)
                val observerRoll = otherStats.willpower + otherStats.intellect / GameConfig.Stealth.PERCEPTION_INT_DIVISOR + otherPlayer.level / GameConfig.Stealth.PERCEPTION_LEVEL_DIVISOR + bonus + (1..GameConfig.Stealth.PERCEPTION_DICE_SIZE).random()
                if (observerRoll >= stealthDc) {
                    detected = true
                    detectorName = otherPlayer.name
                    break
                }
            }
        }

        if (detected) {
            try { session.send(ServerMessage.StealthUpdate(false, "$detectorName notices your attempt to hide!")) } catch (_: Exception) {}
            return
        }

        // Successfully hidden!
        session.isHidden = true
        try { session.send(ServerMessage.StealthUpdate(true, "You slip into the shadows.")) } catch (_: Exception) {}
        // Player vanishes from others' view
        sessionManager.broadcastToRoom(
            roomId,
            ServerMessage.PlayerLeft(playerName, roomId, com.neomud.shared.model.Direction.NORTH),
            exclude = playerName
        )
    }

    private suspend fun resolvePickLock(session: PlayerSession, targetId: String) {
        val roomId = session.currentRoomId ?: return
        val room = worldGraph.getRoom(roomId) ?: return

        session.skillCooldowns["PICK_LOCK"] = GameConfig.Skills.PICK_LOCK_COOLDOWN_TICKS

        val stats = session.effectiveStats()
        val roll = (1..GameConfig.Skills.PICK_LOCK_DICE_SIZE).random()
        val check = stats.agility + stats.intellect / GameConfig.Skills.PICK_LOCK_INT_DIVISOR + roll

        if (targetId.startsWith("feature:")) {
            val featureId = targetId.removePrefix("feature:")
            val interactableDefs = worldGraph.getInteractableDefs(roomId)
            val feat = interactableDefs.find { it.id == featureId }
            if (feat == null) {
                try { session.send(ServerMessage.SystemMessage("You don't see anything like that to pick.")) } catch (_: Exception) {}
                return
            }
            if (check >= feat.difficulty) {
                try { session.send(ServerMessage.SystemMessage("You successfully pick the lock on the ${feat.label}.")) } catch (_: Exception) {}
            } else {
                val failMsg = feat.failureMessage.ifEmpty { "You fail to pick the lock on the ${feat.label}." }
                try { session.send(ServerMessage.SystemMessage(failMsg)) } catch (_: Exception) {}
            }
            return
        }

        if (targetId.startsWith("exit:")) {
            val dirName = targetId.removePrefix("exit:")
            val direction = try { Direction.valueOf(dirName) } catch (_: IllegalArgumentException) {
                try { session.send(ServerMessage.SystemMessage("Invalid direction: $dirName")) } catch (_: Exception) {}
                return
            }
            val difficulty = room.lockedExits[direction]
            if (difficulty == null) {
                try { session.send(ServerMessage.SystemMessage("${direction.lockedExitPhrase.replaceFirstChar { it.uppercase() }} is not locked.")) } catch (_: Exception) {}
                return
            }
            if (check >= difficulty) {
                worldGraph.unlockExit(roomId, direction)
                try { session.send(ServerMessage.SystemMessage("You pick the lock on ${direction.lockedExitPhrase}.")) } catch (_: Exception) {}
                // Refresh room info for all players in the room so they see the unlocked exit
                resendRoomInfoToPlayersInRoom(roomId)
            } else {
                try { session.send(ServerMessage.SystemMessage("You fail to pick the lock.")) } catch (_: Exception) {}
            }
        }
    }

    private suspend fun meditationPhase() {
        for (session in sessionManager.getAllAuthenticatedSessions()) {
            if (!session.isMeditating) continue
            val player = session.player ?: continue

            val effStats = session.effectiveStats()
            val restore = maxOf(effStats.willpower / GameConfig.Meditation.WIL_DIVISOR + GameConfig.Meditation.RESTORE_BASE, 1)
            val newMp = minOf(player.currentMp + restore, player.maxMp)
            val restored = newMp - player.currentMp
            session.player = player.copy(currentMp = newMp)

            try {
                // SpellCastResult updates client MP state and logs the message
                session.send(ServerMessage.SpellCastResult(
                    success = true,
                    spellName = "Meditate",
                    message = "You meditate and restore $restored mana.",
                    newMp = newMp
                ))

                if (newMp >= player.maxMp) {
                    MeditationUtils.breakMeditation(session, "Your mana is fully restored.")
                }

                playerRepository.savePlayerState(session.player!!)
            } catch (_: Exception) { }
        }
    }

    private suspend fun restPhase() {
        for (session in sessionManager.getAllAuthenticatedSessions()) {
            if (!session.isResting) continue
            val player = session.player ?: continue

            val effStats = session.effectiveStats()
            val restore = maxOf(effStats.health / GameConfig.Rest.HEALTH_DIVISOR + GameConfig.Rest.RESTORE_BASE, 1)
            val newHp = minOf(player.currentHp + restore, player.maxHp)
            val restored = newHp - player.currentHp
            session.player = player.copy(currentHp = newHp)

            try {
                session.send(ServerMessage.SpellCastResult(
                    success = true,
                    spellName = "Rest",
                    message = "You rest and restore $restored health.",
                    newMp = player.currentMp,
                    newHp = newHp
                ))

                if (newHp >= player.maxHp) {
                    RestUtils.breakRest(session, "Your health is fully restored.")
                }

                playerRepository.savePlayerState(session.player!!)
            } catch (_: Exception) { }
        }
    }

    /**
     * Periodic scan: non-hidden players in rooms with hidden players roll perception.
     * Observer roll: WIL + INT/2 + level/2 + perceptionBonus + d20
     * vs player stealth DC: AGI + WIL/2 + level/2 + 10
     */
    private suspend fun playerPerceptionPhase() {
        val hiddenByRoom = mutableMapOf<RoomId, MutableList<PlayerSession>>()
        for (session in sessionManager.getAllAuthenticatedSessions()) {
            if (session.isHidden) {
                val roomId = session.currentRoomId ?: continue
                hiddenByRoom.getOrPut(roomId) { mutableListOf() }.add(session)
            }
        }

        for ((roomId, hiddenPlayers) in hiddenByRoom) {
            val observers = sessionManager.getSessionsInRoom(roomId).filter { !it.isHidden }
            for (observer in observers) {
                val observerPlayer = observer.player ?: continue
                val obsStats = observer.effectiveStats()
                val bonus = StealthUtils.perceptionBonus(observerPlayer.characterClass, classCatalog)
                val observerRoll = obsStats.willpower + obsStats.intellect / GameConfig.Stealth.PERCEPTION_INT_DIVISOR + observerPlayer.level / GameConfig.Stealth.PERCEPTION_LEVEL_DIVISOR + bonus + (1..GameConfig.Stealth.PERCEPTION_DICE_SIZE).random()

                for (hiddenSession in hiddenPlayers) {
                    if (!hiddenSession.isHidden) continue
                    val hiddenPlayer = hiddenSession.player ?: continue
                    val hiddenStats = hiddenSession.effectiveStats()
                    val stealthDc = hiddenStats.agility + hiddenStats.willpower / GameConfig.Stealth.DC_WIL_DIVISOR + hiddenPlayer.level / GameConfig.Stealth.DC_LEVEL_DIVISOR + GameConfig.Stealth.DC_BASE

                    if (observerRoll >= stealthDc) {
                        StealthUtils.breakStealth(hiddenSession, sessionManager, "${observerPlayer.name} spots you lurking in the shadows!")
                        try {
                            observer.send(ServerMessage.SystemMessage("You spot ${hiddenPlayer.name} lurking in the shadows!"))
                        } catch (_: Exception) { }
                    }
                }
            }
        }
    }

}
