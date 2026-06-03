package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neomud.client.platform.LocalIsLandscape
import com.neomud.shared.model.MapRoom
import com.neomud.shared.model.RoomId

@Composable
fun FloatingMiniMap(
    rooms: List<MapRoom>,
    playerRoomId: RoomId,
    visitedRoomIds: Set<RoomId> = emptySet(),
    partyMemberRoomIds: Map<Int, RoomId> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalIsLandscape.current
    val mapSize = if (isLandscape) 160.dp else 80.dp
    val cellSize = if (isLandscape) 36f else 48f
    val roomSize = if (isLandscape) 26f else 36f

    Box(
        modifier = modifier
            .size(mapSize)
            .alpha(0.6f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC0D1117))
            .border(1.dp, Color(0xFF444444), RoundedCornerShape(8.dp))
    ) {
        MiniMap(
            rooms = rooms,
            playerRoomId = playerRoomId,
            visitedRoomIds = visitedRoomIds,
            partyMemberRoomIds = partyMemberRoomIds,
            fogOfWar = true,
            cellSize = cellSize,
            roomSize = roomSize
        )
    }
}
