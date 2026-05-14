package com.neomud.server.game.npc

import com.neomud.server.game.GameConfig
import com.neomud.server.game.MovementTrailManager
import com.neomud.server.game.npc.behavior.BehaviorNode
import com.neomud.server.game.npc.behavior.IdleBehavior
import com.neomud.server.game.npc.behavior.NpcAction
import com.neomud.server.game.npc.behavior.PatrolBehavior
import com.neomud.server.game.npc.behavior.PursuitBehavior
import com.neomud.server.game.npc.behavior.WanderBehavior
import com.neomud.server.session.SessionManager
import com.neomud.server.world.BossPhaseData
import com.neomud.server.world.NpcData
import com.neomud.server.world.SpawnConfig
import com.neomud.server.world.TrainerConfig
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.ActiveEffect
import com.neomud.shared.model.Direction
import com.neomud.shared.model.Npc
import com.neomud.shared.model.RoomId

data class NpcState(
    val id: String,
    val name: String,
    val description: String,
    var currentRoomId: RoomId,
    var behavior: BehaviorNode,
    val hostile: Boolean = false,
    val maxHp: Int = 0,
    var currentHp: Int = 0,
    var damage: Int = 0,
    val level: Int = 1,
    val perception: Int = 0,
    val xpReward: Long = 0,
    val behaviorType: String = "idle",
    val zoneId: String = "",
    val startRoomId: RoomId = "",
    val templateId: String = "",
    val vendorItems: List<String> = emptyList(),
    val crafterRecipes: List<String> = emptyList(),
    var accuracy: Int = 0,
    var defense: Int = 0,
    var evasion: Int = 0,
    val agility: Int = 10,
    val attackSound: String = "",
    val missSound: String = "",
    val deathSound: String = "",
    val interactSound: String = "",
    val exitSound: String = "",
    val trainerConfig: TrainerConfig? = null,
    val dialogueScript: String = "",
    val grantItemId: String = "",
    val grantItemFlag: String = "",
    val repeatDialogueScript: String = "",
    val phases: List<BossPhaseData> = emptyList(),
    var currentPhase: Int = 0,
    var spriteOverride: String = ""
) {
    /** Active spell effects on this NPC (DoT, HoT, etc.). */
    val activeEffects: MutableList<ActiveEffect> = mutableListOf()
    /** Set by [NpcManager.markDead] to prevent double-processing kills. */
    var deathProcessed: Boolean = false
    /** When > 0, NPC skips attack ticks (decremented each combat tick). */
    var stunTicks: Int = 0
    /** When > 0, newly spawned NPC waits before attacking (decremented each combat tick). */
    var spawnGraceTicks: Int = 0
    val isAlive: Boolean get() = !deathProcessed && (maxHp == 0 || currentHp > 0)
    /** Stores pre-pursuit behavior for restoration when pursuit ends. */
    var originalBehavior: BehaviorNode? = null
    /** Tracks all players who have engaged this NPC in combat. */
    val engagedPlayerIds: MutableSet<String> = mutableSetOf()
}

data class NpcEvent(
    val npcName: String,
    val fromRoomId: RoomId?,
    val toRoomId: RoomId?,
    val direction: Direction?,
    val npcId: String = "",
    val hostile: Boolean = false,
    val currentHp: Int = 0,
    val maxHp: Int = 0,
    val templateId: String = "",
    val attackSound: String = "",
    val missSound: String = "",
    val deathSound: String = "",
    val interactSound: String = "",
    val exitSound: String = ""
)

class NpcManager(
    private val worldGraph: WorldGraph,
    private val zoneSpawnConfigs: Map<String, SpawnConfig> = emptyMap(),
    private val roomMaxHostileNpcs: Map<RoomId, Int> = emptyMap()
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(NpcManager::class.java)
    private val npcs = mutableListOf<NpcState>()
    private val zoneTemplates = mutableMapOf<String, List<Pair<NpcData, String>>>()
    private val allTemplates = mutableMapOf<String, Pair<NpcData, String>>()
    private val zoneSpawnTimers = mutableMapOf<String, Int>()
    private var nextSpawnIndex = 1L

    fun loadNpcs(npcDataList: List<Pair<NpcData, String>>) {
        // Store hostile templates per zone for continuous spawning
        zoneTemplates.putAll(
            npcDataList
                .filter { it.first.hostile }
                .groupBy { it.second }
        )

        // Store all templates by ID for admin spawn
        for ((data, zoneId) in npcDataList) {
            allTemplates[data.id] = Pair(data, zoneId)
        }

        for ((data, zoneId) in npcDataList) {
            npcs.add(createNpcState(data, zoneId, data.id))
        }
    }

    private fun createNpcState(data: NpcData, zoneId: String, instanceId: String): NpcState {
        val behavior: BehaviorNode = when (data.behaviorType) {
            "patrol" -> PatrolBehavior(data.patrolRoute)
            "wander" -> WanderBehavior()
            else -> IdleBehavior()
        }

        return NpcState(
            id = instanceId,
            name = data.name,
            description = data.description,
            currentRoomId = data.startRoomId,
            behavior = behavior,
            hostile = data.hostile,
            maxHp = data.maxHp,
            currentHp = data.maxHp,
            damage = data.damage,
            level = data.level,
            perception = data.perception,
            xpReward = data.xpReward,
            behaviorType = data.behaviorType,
            zoneId = zoneId,
            startRoomId = data.startRoomId,
            templateId = data.id,
            vendorItems = data.vendorItems,
            crafterRecipes = data.crafterRecipes,
            accuracy = data.accuracy,
            defense = data.defense,
            evasion = data.evasion,
            agility = data.agility,
            attackSound = data.attackSound,
            missSound = data.missSound,
            deathSound = data.deathSound,
            interactSound = data.interactSound,
            exitSound = data.exitSound,
            trainerConfig = data.trainerConfig,
            dialogueScript = data.dialogueScript,
            grantItemId = data.grantItemId,
            grantItemFlag = data.grantItemFlag,
            repeatDialogueScript = data.repeatDialogueScript,
            phases = data.phases
        )
    }

    private fun aliveHostileNpcsInRoom(roomId: RoomId): Int =
        npcs.count { it.currentRoomId == roomId && it.isAlive && it.hostile }

    private fun aliveNpcsInRoom(roomId: RoomId): Int =
        npcs.count { it.currentRoomId == roomId && it.isAlive }

    private fun aliveNpcsInZone(zoneId: String): Int =
        npcs.count { it.zoneId == zoneId && it.isAlive }

    /** Returns the effective hostile NPC cap for a room: room override > zone default > null (unlimited). */
    private fun effectiveMaxPerRoom(roomId: RoomId, zoneId: String): Int? =
        roomMaxHostileNpcs[roomId] ?: zoneSpawnConfigs[zoneId]?.maxPerRoom

    /**
     * @param roomsWithVisiblePlayers rooms containing non-hidden, alive players past grace period.
     *        Hostile NPCs in these rooms skip wander/patrol to stay and fight.
     */
    fun tick(roomsWithVisiblePlayers: Set<RoomId> = emptySet()): List<NpcEvent> {
        val events = mutableListOf<NpcEvent>()

        val friendlyNpcRooms = npcs.filter { it.isAlive && !it.hostile }.map { it.currentRoomId }.toSet()

        // 1. Process living NPC behaviors
        for (npc in npcs) {
            if (!npc.isAlive) continue

            // Hostile NPCs that detect a player (or are stunned) stay put — skip all movement behaviors
            // Exception: sanctuary rooms suppress combat, so NPCs should not stay to fight there
            val inSanctuary = npc.hostile && worldGraph.getRoom(npc.currentRoomId)?.effects?.any { it.type == "SANCTUARY" } == true
            if (npc.hostile && !inSanctuary && (npc.currentRoomId in roomsWithVisiblePlayers || npc.stunTicks > 0)) {
                continue
            }

            val canMoveTo: (RoomId) -> Boolean = { targetRoomId ->
                val maxPerRoom = effectiveMaxPerRoom(targetRoomId, npc.zoneId)
                val roomOk = maxPerRoom == null || aliveHostileNpcsInRoom(targetRoomId) < maxPerRoom
                val sanctuaryOk = !npc.hostile || worldGraph.getRoom(targetRoomId)?.effects?.none { it.type == "SANCTUARY" } != false
                val noFriendlyNpcs = !npc.hostile || targetRoomId !in friendlyNpcRooms
                roomOk && sanctuaryOk && noFriendlyNpcs
            }

            when (val action = npc.behavior.tick(npc, worldGraph, canMoveTo)) {
                is NpcAction.MoveTo -> {
                    val oldRoom = npc.currentRoomId
                    val newRoom = action.targetRoomId

                    val room = worldGraph.getRoom(oldRoom)
                    val direction = room?.exits?.entries?.find { it.value == newRoom }?.key

                    npc.currentRoomId = newRoom

                    events.add(
                        NpcEvent(
                            npcName = npc.name,
                            fromRoomId = oldRoom,
                            toRoomId = newRoom,
                            direction = direction,
                            npcId = npc.id,
                            hostile = npc.hostile,
                            currentHp = npc.currentHp,
                            maxHp = npc.maxHp,
                            templateId = npc.templateId,
                            attackSound = npc.attackSound,
                            missSound = npc.missSound,
                            deathSound = npc.deathSound,
                            interactSound = npc.interactSound,
                            exitSound = npc.exitSound
                        )
                    )
                }
                is NpcAction.None -> { /* do nothing */ }
            }

            // Check if pursuit behavior has ended and restore original
            val behavior = npc.behavior
            if (behavior is PursuitBehavior && behavior.pursuitEnded) {
                endPursuit(npc.id)
            }
        }

        // 2. Clean up dead NPCs (remove from list) — reset zone spawn timers to
        //    prevent instant respawns when the spawn timer was about to fire
        val deadNpcs = npcs.filter { !it.isAlive }
        for (dead in deadNpcs) {
            zoneSpawnTimers[dead.zoneId] = 0
        }
        npcs.removeAll { !it.isAlive }

        // 3. Zone spawn timers — spawn new NPC copies up to maxEntities
        for ((zoneId, config) in zoneSpawnConfigs) {
            if (config.rateTicks == 0 || config.maxEntities == 0) continue

            val timer = zoneSpawnTimers.getOrPut(zoneId) { 0 } + 1
            zoneSpawnTimers[zoneId] = timer

            if (timer < config.rateTicks) continue
            zoneSpawnTimers[zoneId] = 0

            if (aliveNpcsInZone(zoneId) >= config.maxEntities) continue

            val templates = zoneTemplates[zoneId] ?: continue
            if (templates.isEmpty()) continue

            // Pick a random template and try to spawn at a valid room
            val (template, _) = templates.random()
            val candidates = template.spawnPoints.ifEmpty { listOf(template.startRoomId) }
            val spawnRoom = candidates.filter { roomId ->
                val max = effectiveMaxPerRoom(roomId, zoneId)
                max == null || aliveHostileNpcsInRoom(roomId) < max
            }.randomOrNull() ?: continue

            val instanceId = "${template.id}#${nextSpawnIndex++}"
            val spawned = createNpcState(template, zoneId, instanceId)
            spawned.currentRoomId = spawnRoom
            if (spawned.hostile) {
                spawned.spawnGraceTicks = GameConfig.Combat.NPC_SPAWN_GRACE_TICKS
            }
            npcs.add(spawned)
            logger.info("Spawned ${spawned.name} ($instanceId) at $spawnRoom")

            events.add(
                NpcEvent(
                    npcName = spawned.name,
                    fromRoomId = null,
                    toRoomId = spawnRoom,
                    direction = null,
                    npcId = spawned.id,
                    hostile = spawned.hostile,
                    currentHp = spawned.currentHp,
                    maxHp = spawned.maxHp,
                    templateId = spawned.templateId,
                    attackSound = spawned.attackSound,
                    missSound = spawned.missSound,
                    deathSound = spawned.deathSound,
                    interactSound = spawned.interactSound,
                    exitSound = spawned.exitSound
                )
            )
        }

        return events
    }

    fun getNpcsInRoom(roomId: RoomId): List<Npc> {
        val all = npcs.filter { it.currentRoomId == roomId }
        val alive = all.filter { it.isAlive }
        if (all.isNotEmpty()) {
            logger.info("getNpcsInRoom($roomId): ${all.size} total, ${alive.size} alive: ${all.map { "${it.name}(hp=${it.currentHp}/${it.maxHp},alive=${it.isAlive})" }}")
        }
        return alive.map { npcState ->
            Npc(
                id = npcState.id,
                name = npcState.name,
                description = npcState.description,
                currentRoomId = npcState.currentRoomId,
                behaviorType = npcState.behaviorType,
                hostile = npcState.hostile,
                currentHp = npcState.currentHp,
                maxHp = npcState.maxHp,
                attackSound = npcState.attackSound,
                missSound = npcState.missSound,
                deathSound = npcState.deathSound,
                interactSound = npcState.interactSound,
                exitSound = npcState.exitSound,
                activeEffects = npcState.activeEffects.toList(),
                spriteOverride = npcState.spriteOverride
            )
        }
    }

    fun getLivingHostileNpcsInRoom(roomId: RoomId): List<NpcState> =
        npcs.filter { it.currentRoomId == roomId && it.hostile && it.currentHp > 0 }

    /** Returns non-hostile NPCs with combat stats (guards) that are alive in the given room. */
    fun getLivingGuardNpcsInRoom(roomId: RoomId): List<NpcState> =
        npcs.filter { it.currentRoomId == roomId && !it.hostile && it.maxHp > 0 && it.damage > 0 && it.isAlive && it.currentHp > 0 }

    /** Returns all rooms that currently contain living guard NPCs. */
    fun getRoomsWithGuards(): Map<RoomId, List<NpcState>> =
        npcs.filter { !it.hostile && it.maxHp > 0 && it.damage > 0 && it.isAlive && it.currentHp > 0 }
            .groupBy { it.currentRoomId }

    fun getLivingNpcsInRoom(roomId: RoomId): List<NpcState> =
        npcs.filter { it.currentRoomId == roomId && it.currentHp > 0 }

    fun getNpcState(npcId: String): NpcState? =
        npcs.find { it.id == npcId && it.isAlive }

    @Synchronized
    fun markDead(npcId: String): Boolean {
        val npc = npcs.find { it.id == npcId } ?: return false
        if (npc.deathProcessed) return false
        npc.deathProcessed = true
        npc.currentHp = 0
        npc.activeEffects.clear()
        return true
    }

    fun getLivingNpcsWithEffects(): List<NpcState> =
        npcs.filter { it.isAlive && it.activeEffects.isNotEmpty() }

    fun getTrainerInRoom(roomId: RoomId): NpcState? =
        npcs.find { it.currentRoomId == roomId && it.behaviorType == "trainer" && it.isAlive }

    fun getVendorInRoom(roomId: RoomId): NpcState? =
        npcs.find { it.currentRoomId == roomId && it.behaviorType == "vendor" && it.isAlive }

    fun getCrafterInRoom(roomId: RoomId): NpcState? =
        npcs.find { it.currentRoomId == roomId && it.behaviorType == "crafter" && it.isAlive }

    fun spawnAdminNpc(templateId: String, roomId: RoomId): NpcState? {
        val (data, zoneId) = allTemplates[templateId] ?: return null
        val instanceId = "${data.id}#${nextSpawnIndex++}"
        val spawned = createNpcState(data, zoneId, instanceId)
        spawned.currentRoomId = roomId
        npcs.add(spawned)
        logger.info("Admin spawned ${spawned.name} ($instanceId) at $roomId")
        return spawned
    }

    /**
     * Moves an NPC to a new room and returns an [NpcEvent] for broadcasting.
     */
    fun moveNpc(npcId: String, toRoomId: RoomId): NpcEvent? {
        val npc = npcs.find { it.id == npcId && it.isAlive } ?: return null
        val oldRoom = npc.currentRoomId
        val room = worldGraph.getRoom(oldRoom)
        val direction = room?.exits?.entries?.find { it.value == toRoomId }?.key
        npc.currentRoomId = toRoomId
        return NpcEvent(
            npcName = npc.name,
            fromRoomId = oldRoom,
            toRoomId = toRoomId,
            direction = direction,
            npcId = npc.id,
            hostile = npc.hostile,
            currentHp = npc.currentHp,
            maxHp = npc.maxHp,
            templateId = npc.templateId,
            attackSound = npc.attackSound,
            missSound = npc.missSound,
            deathSound = npc.deathSound,
            interactSound = npc.interactSound,
            exitSound = npc.exitSound
        )
    }

    /**
     * Switches an NPC to pursuit behavior targeting the given player.
     * Does nothing if the NPC is already pursuing someone.
     */
    fun engagePursuit(
        npcId: String,
        targetPlayerId: String,
        trailManager: MovementTrailManager,
        sessionManager: SessionManager
    ) {
        val npc = npcs.find { it.id == npcId && it.isAlive } ?: return
        if (npc.originalBehavior != null) return // already pursuing
        if (npc.behaviorType !in listOf("wander", "patrol")) return
        npc.originalBehavior = npc.behavior
        npc.behavior = PursuitBehavior(
            targetPlayerId = targetPlayerId,
            trailManager = trailManager,
            sessionManager = sessionManager
        )
        logger.info("${npc.name} ($npcId) begins pursuing $targetPlayerId")
    }

    /**
     * Ends pursuit for an NPC, restoring its original behavior.
     */
    fun endPursuit(npcId: String) {
        val npc = npcs.find { it.id == npcId } ?: return
        val original = npc.originalBehavior ?: return
        npc.behavior = original
        npc.originalBehavior = null
        npc.engagedPlayerIds.clear()
        logger.info("${npc.name} ($npcId) ends pursuit, reverting to ${npc.behaviorType}")
    }
}
