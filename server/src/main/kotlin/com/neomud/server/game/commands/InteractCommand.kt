package com.neomud.server.game.commands

import com.neomud.server.game.EffectApplicator
import com.neomud.server.game.GameConfig
import com.neomud.server.game.MapRoomFilter
import com.neomud.server.game.MeditationUtils
import com.neomud.server.game.RestUtils
import com.neomud.server.game.RoomFilter
import com.neomud.server.game.StealthUtils
import com.neomud.server.game.inventory.LootService
import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.persistence.repository.PlayerFlagsRepository
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.LootTableCatalog
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.ActiveEffect
import com.neomud.shared.model.Direction
import com.neomud.shared.model.EffectType
import com.neomud.shared.model.GroundItem
import com.neomud.shared.protocol.ServerMessage
import org.slf4j.LoggerFactory

class InteractCommand(
    private val worldGraph: WorldGraph,
    private val sessionManager: SessionManager,
    private val npcManager: NpcManager,
    private val roomItemManager: RoomItemManager,
    private val lootService: LootService,
    private val lootTableCatalog: LootTableCatalog,
    private val playerRepository: PlayerRepository? = null,
    private val inventoryRepository: InventoryRepository? = null,
    private val inventoryCommand: InventoryCommand? = null,
    private val playerFlagsRepository: PlayerFlagsRepository? = null
) {
    private val logger = LoggerFactory.getLogger(InteractCommand::class.java)

    suspend fun execute(session: PlayerSession, featureId: String) {
        val roomId = session.currentRoomId ?: return
        val player = session.player ?: return
        val playerName = session.playerName ?: return

        // Interacting breaks meditation, rest, and stealth
        MeditationUtils.breakMeditation(session, "You stop meditating.")
        RestUtils.breakRest(session, "You stop resting.")
        StealthUtils.breakStealth(session, sessionManager, "Interacting reveals your presence!")

        // Find the interactable definition
        val defs = worldGraph.getInteractableDefs(roomId)
        val feat = defs.find { it.id == featureId }
        if (feat == null) {
            session.send(ServerMessage.InteractResult(false, featureId, "You don't see anything like that here."))
            return
        }

        // Check visibility (hidden interactable not yet discovered)
        if (feat.perceptionDC > 0 && !session.hasDiscoveredInteractable(roomId, feat.id)) {
            session.send(ServerMessage.InteractResult(false, featureId, "You don't see anything like that here."))
            return
        }

        // Check global used state
        if (worldGraph.isInteractableUsed(roomId, featureId)) {
            session.send(ServerMessage.InteractResult(false, feat.label, "It doesn't seem to do anything more."))
            return
        }

        // Check per-player cooldown
        val cooldownKey = "$roomId::$featureId"
        val cooldown = session.interactableCooldowns[cooldownKey]
        if (cooldown != null && cooldown > 0) {
            session.send(ServerMessage.InteractResult(false, feat.label, "It's not ready yet."))
            return
        }

        // Difficulty check (if configured)
        if (feat.difficulty > 0 && feat.difficultyCheck.isNotEmpty()) {
            val effStats = session.effectiveStats()
            val statValue = when (feat.difficultyCheck) {
                "STRENGTH" -> effStats.strength
                "AGILITY" -> effStats.agility
                "INTELLECT" -> effStats.intellect
                "WILLPOWER" -> effStats.willpower
                else -> 0
            }
            val roll = statValue + player.level / 2 + (1..20).random()
            if (roll < feat.difficulty) {
                val failMsg = feat.failureMessage.ifEmpty { "You failed to use the ${feat.label}." }
                session.send(ServerMessage.InteractResult(false, feat.label, failMsg, feat.sound))
                // Still apply cooldown on failure so players can't spam attempts
                if (feat.cooldownTicks > 0) {
                    session.interactableCooldowns[cooldownKey] = feat.cooldownTicks
                }
                return
            }
        }

        // Execute action
        val success = when (feat.actionType) {
            "EXIT_OPEN" -> executeExitOpen(session, roomId, feat.actionData)
            "TREASURE_DROP" -> executeTreasureDrop(session, roomId, feat.actionData)
            "MONSTER_SPAWN" -> executeMonsterSpawn(session, roomId, feat.actionData)
            "ROOM_EFFECT" -> executeRoomEffect(session, feat.actionData)
            "TELEPORT" -> executeTeleport(session, roomId, feat.actionData)
            "DAMAGE_TRAP" -> executeDamageTrap(session, feat)
            "PLACE_ITEM" -> { sendPlaceItemPrompt(session, feat); return }
            "PUZZLE_STEP" -> executePuzzleStep(session, roomId, feat)
            else -> {
                session.send(ServerMessage.InteractResult(false, feat.label, "Nothing happens."))
                false
            }
        }

        if (!success) return

        // Mark globally used and start reset timer
        worldGraph.markInteractableUsed(roomId, featureId, feat.resetTicks)

        // Set per-player cooldown
        if (feat.cooldownTicks > 0) {
            session.interactableCooldowns[cooldownKey] = feat.cooldownTicks
        }

        // Send interact result to the player
        session.send(ServerMessage.InteractResult(
            success = true,
            featureName = feat.label,
            message = feat.description,
            sound = feat.sound
        ))
    }

    private suspend fun executeExitOpen(session: PlayerSession, roomId: String, actionData: Map<String, String>): Boolean {
        val dirStr = actionData["direction"]
        if (dirStr == null) {
            session.send(ServerMessage.InteractResult(false, "", "The mechanism seems broken."))
            return false
        }
        val direction = try {
            Direction.valueOf(dirStr)
        } catch (_: IllegalArgumentException) {
            session.send(ServerMessage.InteractResult(false, "", "The mechanism seems broken."))
            return false
        }

        worldGraph.unlockExit(roomId, direction)

        // Broadcast and refresh room info for all players in the room
        sessionManager.broadcastToRoom(roomId,
            ServerMessage.SystemMessage("You hear a click as the ${direction.name.lowercase()} door unlocks."))
        resendRoomInfoToPlayersInRoom(roomId)
        return true
    }

    private suspend fun executeTreasureDrop(session: PlayerSession, roomId: String, actionData: Map<String, String>): Boolean {
        val lootTableId = actionData["lootTableId"]
        if (lootTableId == null) {
            session.send(ServerMessage.InteractResult(false, "", "Nothing happens."))
            return false
        }

        val lootTable = lootTableCatalog.getLootTable(lootTableId)
        val coinDrop = lootTableCatalog.getCoinDrop(lootTableId)
        val lootedItems = lootService.rollLoot(lootTable)
        val coins = lootService.rollCoins(coinDrop)

        if (lootedItems.isNotEmpty()) {
            roomItemManager.addItems(roomId, lootedItems.map { GroundItem(it.itemId, it.quantity) })
        }
        if (!coins.isEmpty()) {
            roomItemManager.addCoins(roomId, coins)
        }

        if (lootedItems.isNotEmpty() || !coins.isEmpty()) {
            val groundItems = roomItemManager.getGroundItems(roomId)
            val groundCoins = roomItemManager.getGroundCoins(roomId)
            sessionManager.broadcastToRoom(roomId, ServerMessage.RoomItemsUpdate(groundItems, groundCoins))
        }

        return true
    }

    private suspend fun executeMonsterSpawn(session: PlayerSession, roomId: String, actionData: Map<String, String>): Boolean {
        val npcId = actionData["npcId"]
        if (npcId == null) {
            session.send(ServerMessage.InteractResult(false, "", "Nothing happens."))
            return false
        }
        val count = actionData["count"]?.toIntOrNull() ?: 1

        for (i in 0 until count) {
            val spawned = npcManager.spawnAdminNpc(npcId, roomId)
            if (spawned != null) {
                sessionManager.broadcastToRoom(roomId,
                    ServerMessage.NpcEntered(
                        npcName = spawned.name,
                        roomId = roomId,
                        npcId = spawned.id,
                        hostile = spawned.hostile,
                        currentHp = spawned.currentHp,
                        maxHp = spawned.maxHp,
                        spawned = true,
                        templateId = spawned.templateId,
                        attackSound = spawned.attackSound,
                        missSound = spawned.missSound,
                        deathSound = spawned.deathSound,
                        interactSound = spawned.interactSound,
                        exitSound = spawned.exitSound
                    ))
            }
        }

        return true
    }

    private suspend fun executeRoomEffect(session: PlayerSession, actionData: Map<String, String>): Boolean {
        val effectType = actionData["effectType"] ?: return false
        val value = actionData["value"]?.toIntOrNull() ?: 0
        val durationTicks = actionData["durationTicks"]?.toIntOrNull() ?: 0
        val message = actionData["message"] ?: ""

        val player = session.player ?: return false

        if (durationTicks > 0) {
            // Apply as active effect (buff/debuff over time)
            val eType = try { EffectType.valueOf(effectType) } catch (_: IllegalArgumentException) { return false }
            val activeEffect = ActiveEffect(
                name = message.ifEmpty { effectType },
                type = eType,
                magnitude = value,
                remainingTicks = durationTicks
            )
            session.activeEffects.add(activeEffect)
            session.send(ServerMessage.ActiveEffectsUpdate(session.activeEffects.toList()))
        } else {
            // Instant effect
            val result = EffectApplicator.applyEffect(effectType, value, message, player, effectiveMaxHp = session.effectiveMaxHp())
            if (result != null) {
                session.player = player.copy(currentHp = result.newHp, currentMp = result.newMp)
                session.send(ServerMessage.EffectTick(effectType, result.message, result.newHp, newMp = result.newMp))
            }
        }

        return true
    }

    /**
     * Player-triggered DAMAGE_TRAP — e.g. a chest that hurts when opened.
     * Shares damage/save math with TrapManager via TrapResolver. Does NOT
     * mark `trippedTraps` (that's an ON_ENTER concept); the existing
     * `markInteractableUsed` flow handles cooldown/reset for player-triggered
     * traps.
     */
    private suspend fun executeDamageTrap(
        session: com.neomud.server.session.PlayerSession,
        feat: com.neomud.shared.model.RoomInteractable
    ): Boolean {
        val player = session.player ?: return false
        val data = feat.actionData

        val baseDamage = data["damage"]?.toIntOrNull() ?: 0
        val saveStat = data["saveStat"] ?: ""
        val saveDC = data["saveDC"]?.toIntOrNull() ?: 0
        val saveType = when (data["saveType"]?.uppercase()) {
            "DODGE" -> com.neomud.server.game.trap.TrapResolver.SaveType.DODGE
            "RESIST" -> com.neomud.server.game.trap.TrapResolver.SaveType.RESIST
            else -> com.neomud.server.game.trap.TrapResolver.SaveType.NONE
        }

        val outcome = com.neomud.server.game.trap.TrapResolver.resolveSave(
            stats = session.effectiveStats(),
            level = player.level,
            saveStat = saveStat,
            saveDC = saveDC,
            saveType = saveType
        )
        val damage = com.neomud.server.game.trap.TrapResolver.computeDamage(baseDamage, outcome)

        val flavor = when (outcome) {
            com.neomud.server.game.trap.TrapResolver.SaveOutcome.AVOID ->
                data["dodgeMessage"] ?: "You narrowly avoid the ${feat.label}!"
            com.neomud.server.game.trap.TrapResolver.SaveOutcome.HALF ->
                data["resistMessage"] ?: "You partially resist the ${feat.label}. (-$damage HP)"
            com.neomud.server.game.trap.TrapResolver.SaveOutcome.FAIL ->
                data["damageMessage"] ?: "${feat.label} hits you! (-$damage HP)"
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
                session.send(ServerMessage.EffectTick("DAMAGE", effect.message, effect.newHp, newMp = effect.newMp))
            }
        } else {
            session.send(ServerMessage.SystemMessage(flavor))
        }
        return true
    }

    private suspend fun executeTeleport(session: PlayerSession, roomId: String, actionData: Map<String, String>): Boolean {
        val targetRoomId = actionData["targetRoomId"]
        if (targetRoomId == null) {
            session.send(ServerMessage.InteractResult(false, "", "Nothing happens."))
            return false
        }

        val targetRoom = worldGraph.getRoom(targetRoomId)
        if (targetRoom == null) {
            session.send(ServerMessage.InteractResult(false, "", "The destination seems to have vanished."))
            return false
        }

        val playerName = session.playerName ?: return false
        val teleportMessage = actionData["message"] ?: "You are teleported!"

        // Broadcast leave from current room
        sessionManager.broadcastToRoom(roomId,
            ServerMessage.PlayerLeft(playerName, roomId, Direction.NORTH),
            exclude = playerName)

        // Update session
        session.currentRoomId = targetRoomId
        session.player = session.player?.copy(currentRoomId = targetRoomId)
        session.visitedRooms.add(targetRoomId)
        session.combatGraceTicks = GameConfig.Combat.GRACE_TICKS

        // Persist position
        val playerState = session.player
        if (playerState != null) {
            try {
                playerRepository?.savePlayerState(playerState)
            } catch (_: Exception) {
                // Fire-and-forget
            }
        }

        // Cancel attack mode
        if (session.attackMode) {
            session.attackMode = false
            session.selectedTargetId = null
            session.send(ServerMessage.AttackModeUpdate(false))
        }

        // Broadcast enter to new room
        sessionManager.broadcastToRoom(targetRoomId,
            ServerMessage.PlayerEntered(playerName, targetRoomId, session.toPlayerInfo()),
            exclude = playerName)

        // Send new room info to the player
        val filteredRoom = RoomFilter.forPlayer(targetRoom, session, worldGraph)
        val playersInRoom = sessionManager.getVisiblePlayerInfosInRoom(targetRoomId)
            .filter { it.name != playerName }
        val npcsInRoom = npcManager.getNpcsInRoom(targetRoomId)
        session.send(ServerMessage.RoomInfo(filteredRoom, playersInRoom, npcsInRoom))

        val mapRooms = MapRoomFilter.enrichForPlayer(
            worldGraph.getRoomsNear(targetRoomId), session, worldGraph, sessionManager, npcManager
        )
        session.send(ServerMessage.MapData(mapRooms, targetRoomId))

        // Send ground items for new room
        val groundItems = roomItemManager.getGroundItems(targetRoomId)
        val groundCoins = roomItemManager.getGroundCoins(targetRoomId)
        session.send(ServerMessage.RoomItemsUpdate(groundItems, groundCoins))

        // Send teleport message
        session.send(ServerMessage.SystemMessage(teleportMessage))

        return true
    }

    private suspend fun resendRoomInfoToPlayersInRoom(roomId: String) {
        val room = worldGraph.getRoom(roomId) ?: return
        for (s in sessionManager.getSessionsInRoom(roomId)) {
            val name = s.playerName ?: continue
            val filteredRoom = RoomFilter.forPlayer(room, s, worldGraph)
            val playersInRoom = sessionManager.getVisiblePlayerInfosInRoom(roomId)
                .filter { it.name != name }
            val npcsInRoom = npcManager.getNpcsInRoom(roomId)
            try {
                s.send(ServerMessage.RoomInfo(filteredRoom, playersInRoom, npcsInRoom))
                val mapRooms = MapRoomFilter.enrichForPlayer(
                    worldGraph.getRoomsNear(roomId), s, worldGraph, sessionManager, npcManager
                )
                s.send(ServerMessage.MapData(mapRooms, roomId))
            } catch (_: Exception) { }
        }
    }

    // ─── PLACE_ITEM ─────────────────────────────────────────────────
    // Two-phase: tap interactable → server sends PlaceItemPrompt;
    // client picks an item → ClientMessage.PlaceItem → handlePlaceItem.

    private suspend fun sendPlaceItemPrompt(session: PlayerSession, feat: com.neomud.shared.model.RoomInteractable) {
        val accepted = (feat.actionData["acceptedItems"] ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val prompt = feat.actionData["promptText"]?.takeIf { it.isNotBlank() } ?: "Place an item here."
        session.send(ServerMessage.PlaceItemPrompt(
            featureId = feat.id,
            label = feat.label,
            prompt = prompt,
            acceptedItems = accepted
        ))
    }

    /**
     * Handles [com.neomud.shared.protocol.ClientMessage.PlaceItem] — the player's response
     * to a PlaceItemPrompt. Validates the item is accepted, validates the player owns it,
     * optionally consumes it, and fires the puzzle's success exit-open.
     */
    suspend fun handlePlaceItem(session: PlayerSession, featureId: String, itemId: String) {
        val roomId = session.currentRoomId ?: return
        val playerName = session.playerName ?: return
        val feat = worldGraph.getInteractableDefs(roomId).find { it.id == featureId }
        if (feat == null || feat.actionType != "PLACE_ITEM") {
            session.send(ServerMessage.SystemMessage("There is nowhere to place that here."))
            return
        }
        // Visibility + cooldown checks.
        if (worldGraph.isInteractableUsed(roomId, featureId)) {
            session.send(ServerMessage.InteractResult(false, feat.label, "It doesn't seem to do anything more."))
            return
        }
        val accepted = (feat.actionData["acceptedItems"] ?: "").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (itemId !in accepted) {
            val msg = feat.actionData["failureMessage"]?.takeIf { it.isNotBlank() }
                ?: "Nothing happens — that's not what fits here."
            session.send(ServerMessage.InteractResult(false, feat.label, msg))
            return
        }
        // Player must actually have the item.
        val inv = inventoryRepository ?: run {
            session.send(ServerMessage.InteractResult(false, feat.label, "You can't place anything here right now."))
            return
        }
        val owned = inv.getInventory(playerName).any { it.itemId == itemId && !it.equipped }
        if (!owned) {
            session.send(ServerMessage.InteractResult(false, feat.label, "You don't have that on you."))
            return
        }
        // Optional consume.
        val consume = (feat.actionData["consumeItem"] ?: "true").equals("true", ignoreCase = true)
        if (consume) {
            try { inv.removeItem(playerName, itemId, 1) } catch (e: Exception) {
                logger.warn("PLACE_ITEM consume failed for $playerName / $itemId: ${e.message}")
            }
            inventoryCommand?.sendInventoryUpdate(session)
        }
        // Fire success: open the configured exit, broadcast room refresh.
        val msg = feat.actionData["successMessage"]?.takeIf { it.isNotBlank() } ?: "It fits. Something shifts."
        val openedExit = openSuccessExit(session, roomId, feat.actionData)
        worldGraph.markInteractableUsed(roomId, featureId, feat.resetTicks)
        session.send(ServerMessage.InteractResult(success = true, featureName = feat.label, message = msg, sound = feat.sound))
        if (openedExit) resendRoomInfoToPlayersInRoom(roomId)
    }

    // ─── PUZZLE_STEP ────────────────────────────────────────────────
    // Player progresses through a sequence of interactables. State per character
    // in PlayerFlagsTable (flagKey="puzzle:{groupId}:step", value="N"). Wrong order
    // resets to 0 and (optionally) fires a punishment message.

    private suspend fun executePuzzleStep(session: PlayerSession, roomId: String, feat: com.neomud.shared.model.RoomInteractable): Boolean {
        val playerName = session.playerName ?: return false
        val flags = playerFlagsRepository ?: run {
            session.send(ServerMessage.InteractResult(false, feat.label, "Nothing happens."))
            return false
        }
        val groupId = feat.actionData["puzzleGroupId"]?.takeIf { it.isNotBlank() } ?: run {
            session.send(ServerMessage.InteractResult(false, feat.label, "Nothing happens."))
            return false
        }
        val expectedIndex = feat.actionData["puzzleStepIndex"]?.toIntOrNull() ?: return false
        val totalSteps = feat.actionData["puzzleTotalSteps"]?.toIntOrNull() ?: return false

        val flagKey = "puzzle:$groupId:step"
        val currentStep = flags.getFlag(playerName, flagKey)?.toIntOrNull() ?: 0

        return if (currentStep == expectedIndex) {
            val nextStep = currentStep + 1
            if (nextStep >= totalSteps) {
                // Puzzle solved!
                val msg = feat.actionData["successMessage"]?.takeIf { it.isNotBlank() } ?: "The puzzle yields. Something opens."
                val openedExit = openSuccessExit(session, roomId, feat.actionData)
                session.send(ServerMessage.InteractResult(true, feat.label, msg, feat.sound))
                if (openedExit) resendRoomInfoToPlayersInRoom(roomId)
                // Note: we don't reset the puzzle flag — solved-once-per-character semantics.
                true
            } else {
                flags.setFlag(playerName, flagKey, nextStep.toString())
                val msg = feat.actionData["advanceMessage"]?.takeIf { it.isNotBlank() } ?: "Something gives. You're on the right track."
                session.send(ServerMessage.InteractResult(true, feat.label, msg, feat.sound))
                false  // don't fire the global cooldown / mark-used path; player can revisit other steps
            }
        } else {
            flags.setFlag(playerName, flagKey, "0")
            val msg = feat.actionData["resetMessage"]?.takeIf { it.isNotBlank() } ?: "The mechanism resets with a low click. The sequence has been broken."
            session.send(ServerMessage.InteractResult(false, feat.label, msg, feat.sound))
            false
        }
    }

    /**
     * Shared success-action helper for PLACE_ITEM and PUZZLE_STEP. Reads
     * `successDirection` from actionData and unlocks that exit if present.
     * Returns true if an exit was opened (caller should refresh room info).
     */
    private fun openSuccessExit(session: PlayerSession, roomId: String, actionData: Map<String, String>): Boolean {
        val dirStr = actionData["successDirection"]?.takeIf { it.isNotBlank() } ?: return false
        val direction = try { Direction.valueOf(dirStr) } catch (_: IllegalArgumentException) { return false }
        worldGraph.unlockExit(roomId, direction)
        return true
    }

}
