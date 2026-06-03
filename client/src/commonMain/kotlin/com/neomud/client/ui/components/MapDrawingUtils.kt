package com.neomud.client.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.neomud.shared.model.Direction
import com.neomud.shared.model.MapRoom
import kotlin.math.abs

internal val ExitColorNormal = Color(0xFF444444)
internal val ExitColorLocked = Color(0xFFCC8833)
internal val ExitColorHidden = Color(0xFF7755AA)
internal val FogStubColor = Color(0xFF333333)
internal val FogDotColor = Color(0xFF444444)
internal val UpDownFill = Color(0xFFE0E0E0)
internal val UpDownOutline = Color(0xFF111111)
internal val MapBackground = Color(0xFF0D1117)

internal val RoomColorPlayer = Color(0xFF4CAF50)
internal val RoomColorPlayers = Color(0xFF42A5F5)
internal val RoomColorNpcs = Color(0xFFFF9800)
internal val RoomColorDefault = Color(0xFF555555)

internal val PartyDotColors = listOf(
    Color(0xFF66BB6A),
    Color(0xFF42A5F5),
    Color(0xFFFF8A65),
    Color(0xFFAB47BC)
)

internal fun zoneColor(zoneId: String): Color {
    if (zoneId.isEmpty()) return RoomColorDefault
    val hash = zoneId.hashCode()
    val hue = (abs(hash) % 360).toFloat()
    val s = 0.4f
    val l = 0.45f
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((hue / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        hue < 60f  -> Triple(c, x, 0f)
        hue < 120f -> Triple(x, c, 0f)
        hue < 180f -> Triple(0f, c, x)
        hue < 240f -> Triple(0f, x, c)
        hue < 300f -> Triple(x, 0f, c)
        else       -> Triple(c, 0f, x)
    }
    return Color(r1 + m, g1 + m, b1 + m)
}

internal fun exitColor(dir: Direction, room: MapRoom): Color = when {
    dir in room.lockedExits -> ExitColorLocked
    dir in room.hiddenExits -> ExitColorHidden
    else -> ExitColorNormal
}

internal fun isHiddenExit(dir: Direction, room: MapRoom): Boolean = dir in room.hiddenExits

internal fun DrawScope.drawUpTriangle(cx: Float, tipY: Float, size: Float, color: Color, outline: Color) {
    val path = Path().apply {
        moveTo(cx, tipY)
        lineTo(cx - size, tipY + size)
        lineTo(cx + size, tipY + size)
        close()
    }
    drawPath(path, outline, style = Stroke(width = 3f))
    drawPath(path, color)
}

internal fun DrawScope.drawDownTriangle(cx: Float, tipY: Float, size: Float, color: Color, outline: Color) {
    val path = Path().apply {
        moveTo(cx, tipY)
        lineTo(cx - size, tipY - size)
        lineTo(cx + size, tipY - size)
        close()
    }
    drawPath(path, outline, style = Stroke(width = 3f))
    drawPath(path, color)
}
