package com.neomud.server.game.commands

import com.neomud.server.game.GameConfig
import com.neomud.server.game.MeditationUtils
import com.neomud.server.game.RestUtils
import com.neomud.server.game.StealthUtils
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.game.progression.CpAllocator
import com.neomud.server.game.progression.ThresholdBonuses
import com.neomud.server.game.progression.XpCalculator
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.RaceCatalog
import com.neomud.server.world.TrainerConfig
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ServerMessage

class TrainerCommand(
    private val classCatalog: ClassCatalog,
    private val raceCatalog: RaceCatalog,
    private val playerRepository: PlayerRepository,
    private val sessionManager: SessionManager,
    private val npcManager: NpcManager,
    private val tutorialService: com.neomud.server.game.TutorialService? = null
) {
    suspend fun handleInteract(session: PlayerSession) {
        val roomId = session.currentRoomId ?: return
        val player = session.player ?: return

        MeditationUtils.breakMeditation(session, "You stop meditating.")
        RestUtils.breakRest(session, "You stop resting.")
        StealthUtils.breakStealth(session, sessionManager, "Interacting with a trainer reveals your presence!")

        val trainer = npcManager.getTrainerInRoom(roomId)
        if (trainer == null) {
            session.send(ServerMessage.SystemMessage("There is no trainer here."))
            return
        }

        val baseStats = playerRepository.getBaseStats(player.name) ?: player.stats
        val canLevelUp = XpCalculator.isReadyToLevel(player.currentXp, player.xpToNextLevel, player.level)

        session.send(ServerMessage.TrainerInfo(
            canLevelUp = canLevelUp,
            unspentCp = player.unspentCp,
            totalCpEarned = player.totalCpEarned,
            baseStats = baseStats,
            currentStats = player.stats,
            interactSound = trainer.interactSound,
            exitSound = trainer.exitSound
        ))

        // tut_cp_spend: player has unspent CP when interacting with trainer
        if (player.unspentCp > 0 && tutorialService != null) {
            tutorialService.trySend(session, "tut_cp_spend")
        }
    }

    suspend fun handleLevelUp(session: PlayerSession) {
        val roomId = session.currentRoomId ?: return
        val player = session.player ?: return

        val trainer = npcManager.getTrainerInRoom(roomId)
        if (trainer == null) {
            session.send(ServerMessage.SystemMessage("There is no trainer here."))
            return
        }

        if (!XpCalculator.isReadyToLevel(player.currentXp, player.xpToNextLevel, player.level)) {
            session.send(ServerMessage.SystemMessage("You are not ready to level up yet."))
            return
        }

        if (player.level >= GameConfig.Progression.MAX_LEVEL) {
            session.send(ServerMessage.SystemMessage("You have reached the maximum level."))
            return
        }

        // Per-trainer tier cap (null trainerConfig = no cap, treats as global MAX_LEVEL).
        val cap = trainer.trainerConfig
        if (cap != null && player.level >= cap.maxLevel) {
            session.send(ServerMessage.SystemMessage(buildLevelCapMessage(trainer.name, cap)))
            tutorialService?.trySend(session, "tut_trainer_cap")
            return
        }

        val classDef = classCatalog.getClass(player.characterClass) ?: run {
            session.send(ServerMessage.SystemMessage("Class not found."))
            return
        }

        val raceDef = raceCatalog.getRace(player.race)

        val newLevel = player.level + 1
        val hpRoll = (classDef.hpPerLevelMin..classDef.hpPerLevelMax).random()
        val mpRoll = if (classDef.mpPerLevelMax > 0)
            (classDef.mpPerLevelMin..classDef.mpPerLevelMax).random() else 0

        val thresholds = ThresholdBonuses.compute(player.stats)
        val newMaxHp = player.maxHp + hpRoll + (if (newLevel == 2) thresholds.hpBonus else 0) // Only apply HP threshold bonus once at first level-up
        val newMaxMp = player.maxMp + mpRoll + (if (newLevel == 2) thresholds.mpBonus else 0)

        val cpGained = XpCalculator.cpForLevel(newLevel)
        val newUnspentCp = player.unspentCp + cpGained
        val newTotalCpEarned = player.totalCpEarned + cpGained

        // Calculate XP needed for next level
        val raceXpMod = raceDef?.xpModifier ?: 1.0
        val classXpMod = classDef.xpModifier
        val newXpToNext = XpCalculator.adjustedXpForLevel(newLevel, raceXpMod, classXpMod)

        val updatedPlayer = player.copy(
            level = newLevel,
            maxHp = newMaxHp,
            currentHp = newMaxHp, // Full heal on level up
            maxMp = newMaxMp,
            currentMp = newMaxMp,
            xpToNextLevel = newXpToNext,
            unspentCp = newUnspentCp,
            totalCpEarned = newTotalCpEarned
        )
        session.player = updatedPlayer

        try {
            playerRepository.savePlayerState(updatedPlayer)
        } catch (_: Exception) { }

        session.send(ServerMessage.LevelUp(
            newLevel = newLevel,
            hpRoll = hpRoll,
            newMaxHp = newMaxHp,
            mpRoll = mpRoll,
            newMaxMp = newMaxMp,
            cpGained = cpGained,
            totalUnspentCp = newUnspentCp,
            xpToNextLevel = newXpToNext
        ))

        // Broadcast to room
        sessionManager.broadcastToRoom(
            roomId,
            ServerMessage.SystemMessage("${player.name} has reached level $newLevel!"),
            exclude = player.name
        )
    }

    suspend fun handleTrainStat(session: PlayerSession, stat: String, points: Int) {
        val clampedPoints = points.coerceIn(1, 50)
        val roomId = session.currentRoomId ?: return
        val player = session.player ?: return

        val trainer = npcManager.getTrainerInRoom(roomId)
        if (trainer == null) {
            session.send(ServerMessage.SystemMessage("There is no trainer here."))
            return
        }

        val baseStats = playerRepository.getBaseStats(player.name) ?: return

        val (currentValue, baseValue) = when (stat.lowercase()) {
            "strength" -> player.stats.strength to baseStats.strength
            "agility" -> player.stats.agility to baseStats.agility
            "intellect" -> player.stats.intellect to baseStats.intellect
            "willpower" -> player.stats.willpower to baseStats.willpower
            "health" -> player.stats.health to baseStats.health
            "charm" -> player.stats.charm to baseStats.charm
            else -> {
                session.send(ServerMessage.SystemMessage("Unknown stat: $stat"))
                return
            }
        }

        // Per-trainer stat cap. Block the allocation if the resulting value would exceed the cap.
        val cap = trainer.trainerConfig
        if (cap != null && currentValue >= cap.maxStatValue) {
            session.send(ServerMessage.SystemMessage(buildStatCapMessage(stat, trainer.name, cap)))
            tutorialService?.trySend(session, "tut_trainer_cap")
            return
        }
        val effectivePoints = if (cap != null) {
            (cap.maxStatValue - currentValue).coerceAtMost(clampedPoints)
        } else {
            clampedPoints
        }

        val result = CpAllocator.allocate(currentValue, baseValue, player.unspentCp, effectivePoints)
        if (result == null) {
            session.send(ServerMessage.SystemMessage("Not enough CP to train $stat."))
            return
        }

        val newStats = when (stat.lowercase()) {
            "strength" -> player.stats.copy(strength = result.newValue)
            "agility" -> player.stats.copy(agility = result.newValue)
            "intellect" -> player.stats.copy(intellect = result.newValue)
            "willpower" -> player.stats.copy(willpower = result.newValue)
            "health" -> player.stats.copy(health = result.newValue)
            "charm" -> player.stats.copy(charm = result.newValue)
            else -> player.stats
        }

        // Recalculate threshold-based maxHp/maxMp
        val oldThresholds = ThresholdBonuses.compute(player.stats)
        val newThresholds = ThresholdBonuses.compute(newStats)
        val hpDelta = newThresholds.hpBonus - oldThresholds.hpBonus
        val mpDelta = newThresholds.mpBonus - oldThresholds.mpBonus

        val updatedPlayer = player.copy(
            stats = newStats,
            unspentCp = result.remainingCp,
            maxHp = player.maxHp + hpDelta,
            currentHp = (player.currentHp + hpDelta).coerceAtMost(player.maxHp + hpDelta),
            maxMp = player.maxMp + mpDelta,
            currentMp = (player.currentMp + mpDelta).coerceAtMost(player.maxMp + mpDelta)
        )
        session.player = updatedPlayer

        try {
            playerRepository.savePlayerState(updatedPlayer)
        } catch (_: Exception) { }

        session.send(ServerMessage.StatTrained(
            stat = stat,
            newValue = result.newValue,
            cpSpent = result.totalCpSpent,
            remainingCp = result.remainingCp,
            currentHp = updatedPlayer.currentHp,
            maxHp = updatedPlayer.maxHp,
            currentMp = updatedPlayer.currentMp,
            maxMp = updatedPlayer.maxMp
        ))
    }

    suspend fun handleAllocateTrainedStats(session: PlayerSession, newStats: Stats) {
        val roomId = session.currentRoomId ?: return
        val player = session.player ?: return

        val trainer = npcManager.getTrainerInRoom(roomId)
        if (trainer == null) {
            session.send(ServerMessage.SystemMessage("There is no trainer here."))
            return
        }

        val baseStats = playerRepository.getBaseStats(player.name) ?: return

        // Validate no stat goes below base
        if (newStats.strength < baseStats.strength ||
            newStats.agility < baseStats.agility ||
            newStats.intellect < baseStats.intellect ||
            newStats.willpower < baseStats.willpower ||
            newStats.health < baseStats.health ||
            newStats.charm < baseStats.charm
        ) {
            session.send(ServerMessage.SystemMessage("Stats cannot go below base values."))
            return
        }

        // Per-trainer stat cap applies uniformly to every stat.
        val cap = trainer.trainerConfig
        if (cap != null) {
            val cappedStat = listOf(
                "strength" to newStats.strength,
                "agility" to newStats.agility,
                "intellect" to newStats.intellect,
                "willpower" to newStats.willpower,
                "health" to newStats.health,
                "charm" to newStats.charm
            ).firstOrNull { it.second > cap.maxStatValue }
            if (cappedStat != null) {
                session.send(ServerMessage.SystemMessage(buildStatCapMessage(cappedStat.first, trainer.name, cap)))
                tutorialService?.trySend(session, "tut_trainer_cap")
                return
            }
        }

        val cpUsed = CpAllocator.totalCpUsed(newStats, baseStats)
        if (cpUsed > player.totalCpEarned) {
            session.send(ServerMessage.SystemMessage("Not enough CP for that allocation."))
            return
        }

        // Compute threshold bonus deltas
        val oldThresholds = ThresholdBonuses.compute(player.stats)
        val newThresholds = ThresholdBonuses.compute(newStats)
        val hpDelta = newThresholds.hpBonus - oldThresholds.hpBonus
        val mpDelta = newThresholds.mpBonus - oldThresholds.mpBonus

        val updatedPlayer = player.copy(
            stats = newStats,
            unspentCp = player.totalCpEarned - cpUsed,
            maxHp = player.maxHp + hpDelta,
            currentHp = (player.currentHp + hpDelta).coerceAtMost(player.maxHp + hpDelta),
            maxMp = player.maxMp + mpDelta,
            currentMp = (player.currentMp + mpDelta).coerceAtMost(player.maxMp + mpDelta)
        )
        session.player = updatedPlayer

        try {
            playerRepository.savePlayerState(updatedPlayer)
        } catch (_: Exception) { }

        // Send fresh trainer info
        val canLevelUp = XpCalculator.isReadyToLevel(updatedPlayer.currentXp, updatedPlayer.xpToNextLevel, updatedPlayer.level)
        session.send(ServerMessage.TrainerInfo(
            canLevelUp = canLevelUp,
            unspentCp = updatedPlayer.unspentCp,
            totalCpEarned = updatedPlayer.totalCpEarned,
            baseStats = baseStats,
            currentStats = updatedPlayer.stats,
            interactSound = trainer.interactSound,
            exitSound = trainer.exitSound
        ))
    }

    private fun buildLevelCapMessage(trainerName: String, cap: TrainerConfig): String {
        val base = "$trainerName can only train you to level ${cap.maxLevel}."
        return base + nextTierHint(cap)
    }

    private fun buildStatCapMessage(stat: String, trainerName: String, cap: TrainerConfig): String {
        val base = "$trainerName cannot train your $stat past ${cap.maxStatValue}."
        return base + nextTierHint(cap)
    }

    private fun nextTierHint(cap: TrainerConfig): String {
        val name = cap.nextTierTrainerName
        val hint = cap.nextTierLocationHint
        return when {
            name != null && hint != null -> " Seek $name in $hint to train further."
            name != null -> " Seek $name to train further."
            hint != null -> " Seek a higher-tier trainer in $hint."
            else -> ""
        }
    }
}
