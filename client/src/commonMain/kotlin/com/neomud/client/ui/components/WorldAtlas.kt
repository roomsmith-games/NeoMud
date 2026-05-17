package com.neomud.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.shared.model.Direction
import com.neomud.shared.model.MapRoom
import com.neomud.shared.model.RoomId

private val AtlasBg = Color(0xFF080604)
private val TabActive = Color(0xFF2A2218)
private val TabInactive = Color(0xFF100E0B)
private val TabText = Color(0xFFCCA855)
private val TabTextInactive = Color(0xFF5A5040)
private val HeaderGold = Color(0xFFCCA855)

@Composable
fun WorldAtlas(
    rooms: List<MapRoom>,
    playerRoomId: RoomId,
    visitedRoomIds: Set<RoomId>,
    zoneNames: Map<String, String>,
    onClose: () -> Unit
) {
    val playerRoom = rooms.find { it.id == playerRoomId }
    val availableZLevels = rooms.filter { it.id in visitedRoomIds }
        .map { it.z }.distinct().sorted()
    var selectedZ by remember { mutableIntStateOf(playerRoom?.z ?: 0) }

    var scale by remember { mutableFloatStateOf(0.6f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val filteredRooms = rooms.filter { it.z == selectedZ }
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.93f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .background(AtlasBg, RoundedCornerShape(8.dp))
                .drawBehind {
                    drawRect(StoneTheme.frameMid, Offset.Zero, Size(size.width, 3f))
                    drawRect(StoneTheme.frameMid, Offset(0f, size.height - 3f), Size(size.width, 3f))
                    drawRect(StoneTheme.frameMid, Offset.Zero, Size(3f, size.height))
                    drawRect(StoneTheme.frameMid, Offset(size.width - 3f, 0f), Size(3f, size.height))
                }
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("World Atlas", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = HeaderGold)
                Text(
                    "X",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF888888),
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(horizontal = 8.dp)
                )
            }

            // Z-level tabs
            if (availableZLevels.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (z in availableZLevels) {
                        val active = z == selectedZ
                        val label = when {
                            z > 0 -> "L+$z"
                            z == 0 -> "Surface"
                            else -> "L$z"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (active) TabActive else TabInactive)
                                .clickable { selectedZ = z }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(label, fontSize = 12.sp, color = if (active) TabText else TabTextInactive)
                        }
                    }
                }
            }

            // Canvas with pan/zoom
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.25f, 3f)
                            offset += pan
                        }
                    }
            ) {
                if (filteredRooms.isEmpty()) return@Canvas

                val cellSize = 48f * scale
                val roomSize = 36f * scale

                val anchorRoom = playerRoom?.takeIf { it.z == selectedZ }
                    ?: filteredRooms.firstOrNull { it.id in visitedRoomIds }
                    ?: filteredRooms.first()
                val anchorX = anchorRoom.x
                val anchorY = anchorRoom.y

                val centerX = size.width / 2 + offset.x
                val centerY = size.height / 2 + offset.y

                val roomPositions = mutableMapOf<RoomId, Offset>()
                for (room in filteredRooms) {
                    val dx = (room.x - anchorX).toFloat()
                    val dy = -(room.y - anchorY).toFloat()
                    roomPositions[room.id] = Offset(centerX + dx * cellSize, centerY + dy * cellSize)
                }

                val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(6f * scale, 4f * scale), 0f)

                // Draw exits
                val visibleRooms = filteredRooms.filter { it.id in visitedRoomIds }
                for (room in visibleRooms) {
                    val from = roomPositions[room.id] ?: continue
                    for ((dir, targetId) in room.exits) {
                        if (dir == Direction.UP || dir == Direction.DOWN) continue
                        val color = exitColor(dir, room)
                        val isDashed = isHiddenExit(dir, room)

                        if (targetId !in visitedRoomIds) {
                            val targetPos = roomPositions[targetId]
                            if (targetPos != null) {
                                val mid = Offset(
                                    from.x + (targetPos.x - from.x) * 0.5f,
                                    from.y + (targetPos.y - from.y) * 0.5f
                                )
                                val stubColor = if (color != ExitColorNormal) color.copy(alpha = 0.5f) else FogStubColor
                                if (isDashed) {
                                    drawLine(stubColor, from, mid, 2f * scale, pathEffect = dashedEffect)
                                } else {
                                    drawLine(stubColor, from, mid, 2f * scale)
                                }
                                drawCircle(stubColor.copy(alpha = 0.7f), 4f * scale, mid)
                            }
                        } else {
                            val to = roomPositions[targetId] ?: continue
                            if (isDashed) {
                                drawLine(color, from, to, 2f * scale, pathEffect = dashedEffect)
                            } else {
                                drawLine(color, from, to, 2f * scale)
                            }
                            if (dir in room.lockedExits) {
                                val mid = Offset((from.x + to.x) / 2f, (from.y + to.y) / 2f)
                                drawCircle(ExitColorLocked, 3f * scale, mid)
                            }
                        }
                    }
                }

                // Draw rooms
                for (room in visibleRooms) {
                    val pos = roomPositions[room.id] ?: continue
                    val baseColor = when {
                        room.id == playerRoomId -> RoomColorPlayer
                        else -> zoneColor(room.zoneId)
                    }
                    drawRect(
                        color = baseColor,
                        topLeft = Offset(pos.x - roomSize / 2, pos.y - roomSize / 2),
                        size = Size(roomSize, roomSize)
                    )

                    if (room.hasPlayers && room.id != playerRoomId) {
                        drawCircle(RoomColorPlayers, 4f * scale, Offset(pos.x + roomSize / 4, pos.y - roomSize / 4))
                    }
                    if (room.hasNpcs) {
                        drawCircle(RoomColorNpcs, 4f * scale, Offset(pos.x - roomSize / 4, pos.y - roomSize / 4))
                    }

                    val triSize = 9f * scale
                    val hasNorth = Direction.NORTH in room.exits
                    val hasSouth = Direction.SOUTH in room.exits
                    if (Direction.UP in room.exits) {
                        val upColor = if (Direction.UP in room.lockedExits) ExitColorLocked
                            else if (Direction.UP in room.hiddenExits) ExitColorHidden
                            else UpDownFill
                        val oY = if (hasNorth) roomSize / 2 + triSize + 3f * scale else roomSize / 2 + 3f * scale
                        drawUpTriangle(pos.x, pos.y - oY, triSize, upColor, UpDownOutline)
                    }
                    if (Direction.DOWN in room.exits) {
                        val downColor = if (Direction.DOWN in room.lockedExits) ExitColorLocked
                            else if (Direction.DOWN in room.hiddenExits) ExitColorHidden
                            else UpDownFill
                        val oY = if (hasSouth) roomSize / 2 + triSize + 3f * scale else roomSize / 2 + 3f * scale
                        drawDownTriangle(pos.x, pos.y + oY, triSize, downColor, UpDownOutline)
                    }
                }

                // Draw zone labels at centroids
                if (scale >= 0.4f) {
                    val zoneCentroids = visibleRooms.groupBy { it.zoneId }
                    for ((zoneId, zoneRooms) in zoneCentroids) {
                        val name = zoneNames[zoneId] ?: continue
                        val cx = zoneRooms.map { roomPositions[it.id]?.x ?: 0f }.average().toFloat()
                        val cy = zoneRooms.map { roomPositions[it.id]?.y ?: 0f }.average().toFloat()
                        val style = TextStyle(
                            color = zoneColor(zoneId).copy(alpha = 0.6f),
                            fontSize = (10f * scale).sp,
                            fontWeight = FontWeight.Medium
                        )
                        val measured = textMeasurer.measure(name, style)
                        drawText(
                            measured,
                            topLeft = Offset(cx - measured.size.width / 2f, cy + roomSize / 2 + 6f * scale)
                        )
                    }
                }
            }
        }
    }
}
