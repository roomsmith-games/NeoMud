package com.neomud.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import com.neomud.shared.model.Direction
import com.neomud.shared.model.MapRoom
import com.neomud.shared.model.RoomId

@Composable
fun MiniMap(
    rooms: List<MapRoom>,
    playerRoomId: RoomId,
    visitedRoomIds: Set<RoomId> = emptySet(),
    partyMemberRoomIds: Map<Int, RoomId> = emptyMap(),
    fogOfWar: Boolean = false,
    cellSize: Float = 48f,
    roomSize: Float = 36f,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(MapBackground)
    ) {
        if (rooms.isEmpty()) return@Canvas

        val playerRoom = rooms.find { it.id == playerRoomId } ?: return@Canvas
        val centerX = size.width / 2
        val centerY = size.height / 2

        val roomPositions = mutableMapOf<RoomId, Offset>()
        for (room in rooms) {
            val dx = (room.x - playerRoom.x).toFloat()
            val dy = -(room.y - playerRoom.y).toFloat()
            roomPositions[room.id] = Offset(centerX + dx * cellSize, centerY + dy * cellSize)
        }

        val visibleRooms = if (fogOfWar) rooms.filter { it.id in visitedRoomIds } else rooms
        val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)

        for (room in visibleRooms) {
            val from = roomPositions[room.id] ?: continue
            for ((dir, targetId) in room.exits) {
                if (dir == Direction.UP || dir == Direction.DOWN) continue

                val color = exitColor(dir, room)
                val isDashed = isHiddenExit(dir, room)

                if (fogOfWar && targetId !in visitedRoomIds) {
                    val targetPos = roomPositions[targetId]
                    if (targetPos != null) {
                        val mid = Offset(
                            from.x + (targetPos.x - from.x) * 0.5f,
                            from.y + (targetPos.y - from.y) * 0.5f
                        )
                        val stubColor = if (color != ExitColorNormal) color.copy(alpha = 0.5f) else FogStubColor
                        if (isDashed) {
                            drawLine(color = stubColor, start = from, end = mid, strokeWidth = 2f, pathEffect = dashedEffect)
                        } else {
                            drawLine(color = stubColor, start = from, end = mid, strokeWidth = 2f)
                        }
                        drawCircle(color = stubColor.copy(alpha = 0.7f), radius = 4f, center = mid)
                    }
                } else {
                    val to = roomPositions[targetId] ?: continue
                    if (isDashed) {
                        drawLine(color = color, start = from, end = to, strokeWidth = 2f, pathEffect = dashedEffect)
                    } else {
                        drawLine(color = color, start = from, end = to, strokeWidth = 2f)
                    }
                    if (dir in room.lockedExits) {
                        val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
                        drawCircle(color = ExitColorLocked, radius = 3f, center = mid)
                    }
                }
            }
        }

        for (room in visibleRooms) {
            val pos = roomPositions[room.id] ?: continue
            val baseColor = when {
                room.id == playerRoomId -> RoomColorPlayer
                fogOfWar -> zoneColor(room.zoneId)
                room.hasPlayers -> RoomColorPlayers
                room.hasNpcs -> RoomColorNpcs
                else -> RoomColorDefault
            }

            drawRect(
                color = baseColor,
                topLeft = Offset(pos.x - roomSize / 2, pos.y - roomSize / 2),
                size = Size(roomSize, roomSize)
            )

            if (fogOfWar && room.id != playerRoomId) {
                if (room.hasPlayers) {
                    drawCircle(color = RoomColorPlayers, radius = 4f, center = Offset(pos.x + roomSize / 4, pos.y - roomSize / 4))
                }
                if (room.hasNpcs) {
                    drawCircle(color = RoomColorNpcs, radius = 4f, center = Offset(pos.x - roomSize / 4, pos.y - roomSize / 4))
                }
            }

            val triSize = 9f
            val hasNorth = Direction.NORTH in room.exits
            val hasSouth = Direction.SOUTH in room.exits

            if (Direction.UP in room.exits) {
                val upColor = if (Direction.UP in room.lockedExits) ExitColorLocked
                    else if (Direction.UP in room.hiddenExits) ExitColorHidden
                    else UpDownFill
                val offsetY = if (hasNorth) roomSize / 2 + triSize + 3f else roomSize / 2 + 3f
                drawUpTriangle(pos.x, pos.y - offsetY, triSize, upColor, UpDownOutline)
            }

            if (Direction.DOWN in room.exits) {
                val downColor = if (Direction.DOWN in room.lockedExits) ExitColorLocked
                    else if (Direction.DOWN in room.hiddenExits) ExitColorHidden
                    else UpDownFill
                val offsetY = if (hasSouth) roomSize / 2 + triSize + 3f else roomSize / 2 + 3f
                drawDownTriangle(pos.x, pos.y + offsetY, triSize, downColor, UpDownOutline)
            }
        }

        // Party member dots — visible even on fogged rooms
        val dotOffsets = listOf(
            Offset(-roomSize / 4, roomSize / 4),
            Offset(roomSize / 4, roomSize / 4),
            Offset(-roomSize / 4, -roomSize / 4)
        )
        for ((colorIndex, memberRoomId) in partyMemberRoomIds) {
            val pos = roomPositions[memberRoomId] ?: continue
            val color = PartyDotColors[colorIndex % PartyDotColors.size]
            val slotIndex = colorIndex.coerceAtMost(dotOffsets.size - 1)
            val offset = dotOffsets[slotIndex]
            drawCircle(
                color = color,
                radius = 4f,
                center = Offset(pos.x + offset.x, pos.y + offset.y)
            )
        }
    }
}
