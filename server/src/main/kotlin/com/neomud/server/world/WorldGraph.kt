package com.neomud.server.world

import com.neomud.shared.model.Direction
import com.neomud.shared.model.MapRoom
import com.neomud.shared.model.Room
import com.neomud.shared.model.RoomId
import com.neomud.shared.model.RoomInteractable

sealed class ExitResetEvent {
    data class Relocked(val roomId: RoomId, val direction: Direction, val difficulty: Int) : ExitResetEvent()
    data class Rehidden(val roomId: RoomId, val direction: Direction) : ExitResetEvent()
}

data class InteractableResetEvent(
    val roomId: RoomId,
    val featureId: String,
    val featureLabel: String,
    val actionType: String
)

class WorldGraph {
    private val rooms = HashMap<RoomId, Room>()

    // Immutable references from zone data
    private val originalLockedExits = HashMap<RoomId, Map<Direction, Int>>()
    private val lockResetDurations = HashMap<RoomId, Map<Direction, Int>>()
    private val hiddenExitDefs = HashMap<RoomId, Map<Direction, HiddenExitData>>()

    // Mutable timer state
    private val lockResetTimers = HashMap<RoomId, MutableMap<Direction, Int>>()
    private val hiddenResetTimers = HashMap<RoomId, MutableMap<Direction, Int>>()

    // Interactable storage
    private val interactableDefs = HashMap<RoomId, List<RoomInteractable>>()
    val usedInteractables = mutableSetOf<String>()           // "roomId:featureId"
    val interactableResetTimers = HashMap<String, Int>()     // "roomId:featureId" -> ticks remaining

    var defaultSpawnRoom: RoomId = ""
        private set

    fun addRoom(room: Room) {
        rooms[room.id] = room
        if (defaultSpawnRoom.isEmpty()) {
            defaultSpawnRoom = room.id
        }
    }

    fun getRoom(roomId: RoomId): Room? = rooms[roomId]

    fun getRoomsNear(centerRoomId: RoomId, radius: Int = 3): List<MapRoom> {
        val center = rooms[centerRoomId] ?: return emptyList()
        val centerZ = center.z
        val visited = mutableSetOf<RoomId>()
        val result = mutableListOf<MapRoom>()
        val queue = ArrayDeque<Pair<RoomId, Int>>()

        queue.add(centerRoomId to 0)
        visited.add(centerRoomId)

        while (queue.isNotEmpty()) {
            val (currentId, depth) = queue.removeFirst()
            val room = rooms[currentId] ?: continue

            result.add(
                MapRoom(
                    id = room.id,
                    name = room.name,
                    x = room.x,
                    y = room.y,
                    z = room.z,
                    exits = room.exits,
                    backgroundImage = room.backgroundImage,
                    zoneId = room.zoneId
                )
            )

            if (depth < radius) {
                for ((dir, targetId) in room.exits) {
                    if (targetId in visited) continue
                    val target = rooms[targetId] ?: continue
                    if (target.z != centerZ) continue
                    visited.add(targetId)
                    queue.add(targetId to depth + 1)
                }
            }
        }

        return result
    }

    fun setDefaultSpawn(roomId: RoomId) {
        if (rooms.containsKey(roomId)) {
            defaultSpawnRoom = roomId
        }
    }

    fun getRoomIdsInZone(zoneId: String): List<RoomId> =
        rooms.values.filter { it.zoneId == zoneId }.map { it.id }

    val roomCount: Int get() = rooms.size

    fun getAllRooms(): List<Room> = rooms.values.toList()

    // --- Lock and hidden exit management ---

    fun setOriginalLockedExits(roomId: RoomId, locks: Map<Direction, Int>) {
        if (locks.isNotEmpty()) originalLockedExits[roomId] = locks
    }

    fun setLockResetDurations(roomId: RoomId, durations: Map<Direction, Int>) {
        if (durations.isNotEmpty()) lockResetDurations[roomId] = durations
    }

    fun setHiddenExitDefs(roomId: RoomId, defs: Map<Direction, HiddenExitData>) {
        if (defs.isNotEmpty()) hiddenExitDefs[roomId] = defs
    }

    fun getHiddenExitDefs(roomId: RoomId): Map<Direction, HiddenExitData> =
        hiddenExitDefs[roomId] ?: emptyMap()

    fun isExitCurrentlyHidden(roomId: RoomId, direction: Direction): Boolean {
        val defs = hiddenExitDefs[roomId] ?: return false
        if (direction !in defs) return false
        // Exit is hidden if there's no active hidden-reset timer counting down
        // (i.e., it hasn't been revealed, or it was re-hidden after timer expired)
        val timer = hiddenResetTimers[roomId]?.get(direction)
        return timer == null
    }

    fun unlockExit(roomId: RoomId, direction: Direction) {
        val room = rooms[roomId] ?: return
        rooms[roomId] = room.copy(lockedExits = room.lockedExits - direction)

        // Start lock reset timer if configured
        val duration = lockResetDurations[roomId]?.get(direction)
            ?: hiddenExitDefs[roomId]?.get(direction)?.lockResetTicks?.takeIf { it > 0 }
        if (duration != null && duration > 0) {
            lockResetTimers.getOrPut(roomId) { mutableMapOf() }[direction] = duration
        }
    }

    fun relockExit(roomId: RoomId, direction: Direction) {
        val difficulty = originalLockedExits[roomId]?.get(direction)
            ?: hiddenExitDefs[roomId]?.get(direction)?.lockDifficulty
            ?: return
        val room = rooms[roomId] ?: return
        rooms[roomId] = room.copy(lockedExits = room.lockedExits + (direction to difficulty))
    }

    fun revealHiddenExit(roomId: RoomId, direction: Direction) {
        val def = hiddenExitDefs[roomId]?.get(direction) ?: return
        if (def.hiddenResetTicks > 0) {
            hiddenResetTimers.getOrPut(roomId) { mutableMapOf() }[direction] = def.hiddenResetTicks
        } else {
            // Permanent reveal — mark with a sentinel so isExitCurrentlyHidden returns false
            hiddenResetTimers.getOrPut(roomId) { mutableMapOf() }[direction] = -1
        }
    }

    fun rehideExit(roomId: RoomId, direction: Direction) {
        hiddenResetTimers[roomId]?.remove(direction)
    }

    // --- Interactable management ---

    fun storeInteractableDefs(roomId: RoomId, defs: List<RoomInteractable>) {
        if (defs.isNotEmpty()) interactableDefs[roomId] = defs
    }

    fun getInteractableDefs(roomId: RoomId): List<RoomInteractable> =
        interactableDefs[roomId] ?: emptyList()

    fun isInteractableUsed(roomId: RoomId, featureId: String): Boolean =
        "$roomId::$featureId" in usedInteractables

    fun markInteractableUsed(roomId: RoomId, featureId: String, resetTicks: Int) {
        val key = "$roomId::$featureId"
        usedInteractables.add(key)
        if (resetTicks > 0) {
            interactableResetTimers[key] = resetTicks
        }
    }

    fun resetInteractable(roomId: RoomId, featureId: String) {
        usedInteractables.remove("$roomId::$featureId")
    }

    fun tickInteractableTimers(): List<InteractableResetEvent> {
        val events = mutableListOf<InteractableResetEvent>()
        val iter = interactableResetTimers.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            val newVal = entry.value - 1
            if (newVal <= 0) {
                iter.remove()
                val key = entry.key
                val sepIdx = key.lastIndexOf("::")
                if (sepIdx > 0) {
                    val roomId = key.substring(0, sepIdx)
                    val featureId = key.substring(sepIdx + 2)
                    val def = getInteractableDefs(roomId).find { it.id == featureId }
                    resetInteractable(roomId, featureId)
                    if (def?.actionType == "EXIT_OPEN") {
                        // Re-lock the exit
                        val dirStr = def.actionData["direction"]
                        if (dirStr != null) {
                            try {
                                relockExit(roomId, Direction.valueOf(dirStr))
                            } catch (_: IllegalArgumentException) { }
                        }
                    }
                    events.add(InteractableResetEvent(
                        roomId = roomId,
                        featureId = featureId,
                        featureLabel = def?.label ?: featureId,
                        actionType = def?.actionType ?: ""
                    ))
                }
            } else {
                entry.setValue(newVal)
            }
        }
        return events
    }

    fun tickResetTimers(): List<ExitResetEvent> {
        val events = mutableListOf<ExitResetEvent>()

        // Tick lock reset timers
        val lockRoomIter = lockResetTimers.entries.iterator()
        while (lockRoomIter.hasNext()) {
            val roomEntry = lockRoomIter.next()
            val roomId = roomEntry.key
            val timers = roomEntry.value
            val dirIter = timers.entries.iterator()
            while (dirIter.hasNext()) {
                val entry = dirIter.next()
                val direction = entry.key
                val newVal = entry.value - 1
                if (newVal <= 0) {
                    dirIter.remove()
                    val difficulty = originalLockedExits[roomId]?.get(direction)
                        ?: hiddenExitDefs[roomId]?.get(direction)?.lockDifficulty
                    if (difficulty != null) {
                        relockExit(roomId, direction)
                        events.add(ExitResetEvent.Relocked(roomId, direction, difficulty))
                    }
                } else {
                    entry.setValue(newVal)
                }
            }
            if (timers.isEmpty()) lockRoomIter.remove()
        }

        // Tick hidden reset timers
        val hiddenRoomIter = hiddenResetTimers.entries.iterator()
        while (hiddenRoomIter.hasNext()) {
            val roomEntry = hiddenRoomIter.next()
            val roomId = roomEntry.key
            val timers = roomEntry.value
            val dirIter = timers.entries.iterator()
            while (dirIter.hasNext()) {
                val entry = dirIter.next()
                val direction = entry.key
                val remaining = entry.value
                if (remaining < 0) continue // permanent reveal sentinel
                val newVal = remaining - 1
                if (newVal <= 0) {
                    dirIter.remove()
                    events.add(ExitResetEvent.Rehidden(roomId, direction))
                } else {
                    entry.setValue(newVal)
                }
            }
            if (timers.isEmpty()) hiddenRoomIter.remove()
        }

        return events
    }
}
