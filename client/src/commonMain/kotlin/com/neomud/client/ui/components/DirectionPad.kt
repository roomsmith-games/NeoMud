package com.neomud.client.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.shared.model.Direction

// Stone & Torchlight palette for direction states
private val EnabledGradientTop = Color(0xFF4A4030)    // warm stone, lighter
private val EnabledGradientBot = Color(0xFF1A1510)    // frameDark
private val DisabledGradientTop = Color(0xFF1A1510)   // barely visible
private val DisabledGradientBot = Color(0xFF0D0A08)   // innerShadow
private val LockedGradientTop = Color(0xFF6B5020)     // amber stone
private val LockedGradientBot = Color(0xFF3A2810)     // dark amber
private val TrackedGradientTop = Color(0xFF2A5530)    // verdant stone
private val TrackedGradientBot = Color(0xFF0D2210)    // dark green

private val EnabledText = Color(0xFFD8CCAA)           // BoneWhite
private val DisabledText = Color(0xFF3A3228)           // frameMid, very dim
private val LockedText = Color(0xFFCCA855)             // BurnishedGold
private val TrackedText = Color(0xFF44CC55)            // VerdantUpgrade
private val LookText = Color(0xFFBBA060)               // TorchAmber

private val BevelLight = Color(0xFF5A5040)             // frameLight
private val BevelShadow = Color(0xFF0D0A08)            // innerShadow
private val LockedBevel = Color(0xFFAA8844)            // metalGold
private val TrackedBevel = Color(0xFF44CC55)            // VerdantUpgrade

private val BUTTON_SIZE = 36.dp
private val SMALL_BUTTON_SIZE = 30.dp
private val STAIR_WIDTH = 30.dp
private val STAIR_HEIGHT = 22.dp
private val H_GAP = 4.dp
private val V_GAP = 3.dp

@Composable
fun DirectionPad(
    availableExits: Set<Direction>,
    onMove: (Direction) -> Unit,
    onLook: () -> Unit,
    modifier: Modifier = Modifier,
    lockedExits: Set<Direction> = emptySet(),
    trackedDirection: Direction? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(V_GAP)
    ) {
        // Row 1: NW, N, NE
        Row(
            horizontalArrangement = Arrangement.spacedBy(H_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StoneTooltip("Move Northwest") {
            StoneDPadButton(
                text = "NW",
                enabled = Direction.NORTHWEST in availableExits,
                locked = Direction.NORTHWEST in lockedExits,
                tracked = trackedDirection == Direction.NORTHWEST,
                onClick = { onMove(Direction.NORTHWEST) },
                size = SMALL_BUTTON_SIZE
            )
            }
            StoneTooltip("Move North") {
            StoneDPadButton(
                text = "N",
                enabled = Direction.NORTH in availableExits,
                locked = Direction.NORTH in lockedExits,
                tracked = trackedDirection == Direction.NORTH,
                onClick = { onMove(Direction.NORTH) },
                isCardinal = true
            )
            }
            StoneTooltip("Move Northeast") {
            StoneDPadButton(
                text = "NE",
                enabled = Direction.NORTHEAST in availableExits,
                locked = Direction.NORTHEAST in lockedExits,
                tracked = trackedDirection == Direction.NORTHEAST,
                onClick = { onMove(Direction.NORTHEAST) },
                size = SMALL_BUTTON_SIZE
            )
            }
        }

        // Row 2: W, LOOK, E
        Row(
            horizontalArrangement = Arrangement.spacedBy(H_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StoneTooltip("Move West") {
            StoneDPadButton(
                text = "W",
                enabled = Direction.WEST in availableExits,
                locked = Direction.WEST in lockedExits,
                tracked = trackedDirection == Direction.WEST,
                onClick = { onMove(Direction.WEST) },
                isCardinal = true
            )
            }
            StoneTooltip("Look Around") {
            StoneDPadButton(
                text = "",
                enabled = true,
                onClick = onLook,
                isLook = true,
                icon = { LookIcon(color = LookText) }
            )
            }
            StoneTooltip("Move East") {
            StoneDPadButton(
                text = "E",
                enabled = Direction.EAST in availableExits,
                locked = Direction.EAST in lockedExits,
                tracked = trackedDirection == Direction.EAST,
                onClick = { onMove(Direction.EAST) },
                isCardinal = true
            )
            }
        }

        // Row 3: SW, S, SE
        Row(
            horizontalArrangement = Arrangement.spacedBy(H_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StoneTooltip("Move Southwest") {
            StoneDPadButton(
                text = "SW",
                enabled = Direction.SOUTHWEST in availableExits,
                locked = Direction.SOUTHWEST in lockedExits,
                tracked = trackedDirection == Direction.SOUTHWEST,
                onClick = { onMove(Direction.SOUTHWEST) },
                size = SMALL_BUTTON_SIZE
            )
            }
            StoneTooltip("Move South") {
            StoneDPadButton(
                text = "S",
                enabled = Direction.SOUTH in availableExits,
                locked = Direction.SOUTH in lockedExits,
                tracked = trackedDirection == Direction.SOUTH,
                onClick = { onMove(Direction.SOUTH) },
                isCardinal = true
            )
            }
            StoneTooltip("Move Southeast") {
            StoneDPadButton(
                text = "SE",
                enabled = Direction.SOUTHEAST in availableExits,
                locked = Direction.SOUTHEAST in lockedExits,
                tracked = trackedDirection == Direction.SOUTHEAST,
                onClick = { onMove(Direction.SOUTHEAST) },
                size = SMALL_BUTTON_SIZE
            )
            }
        }

        // Row 4: UP and DOWN — compact stone stair buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(H_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StoneTooltip("Go Up") {
            StoneStairButton(
                label = "UP",
                enabled = Direction.UP in availableExits,
                locked = Direction.UP in lockedExits,
                tracked = trackedDirection == Direction.UP,
                onClick = { onMove(Direction.UP) }
            )
            }
            Spacer(modifier = Modifier.width(SMALL_BUTTON_SIZE - STAIR_WIDTH + H_GAP))
            StoneTooltip("Go Down") {
            StoneStairButton(
                label = "DN",
                enabled = Direction.DOWN in availableExits,
                locked = Direction.DOWN in lockedExits,
                tracked = trackedDirection == Direction.DOWN,
                onClick = { onMove(Direction.DOWN) }
            )
            }
        }
    }
}

@Composable
private fun StoneDPadButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    isLook: Boolean = false,
    isCardinal: Boolean = false,
    locked: Boolean = false,
    tracked: Boolean = false,
    size: Dp = BUTTON_SIZE,
    icon: @Composable (() -> Unit)? = null
) {
    val gradientTop = when {
        isLook -> StoneTheme.frameLight
        tracked -> TrackedGradientTop
        locked -> LockedGradientTop
        enabled -> EnabledGradientTop
        else -> DisabledGradientTop
    }
    val gradientBot = when {
        isLook -> StoneTheme.frameDark
        tracked -> TrackedGradientBot
        locked -> LockedGradientBot
        enabled -> EnabledGradientBot
        else -> DisabledGradientBot
    }
    val textColor = when {
        isLook -> LookText
        tracked -> TrackedText
        locked -> LockedText
        enabled -> EnabledText
        else -> DisabledText
    }
    val bevelHighlight = when {
        tracked -> TrackedBevel
        locked -> LockedBevel
        else -> BevelLight
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .drawBehind {
                drawStoneDPad(gradientTop, gradientBot, bevelHighlight, enabled || isLook)
            }
            .then(if (isLook) Modifier.testTag("look_button") else Modifier)
            .then(if (enabled || isLook) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        if (icon != null) {
            icon()
        } else {
            Text(
                text = text,
                fontSize = if (isCardinal) 13.sp else 12.sp,
                fontWeight = if (isCardinal) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun StoneStairButton(
    label: String,
    enabled: Boolean,
    locked: Boolean = false,
    tracked: Boolean = false,
    onClick: () -> Unit
) {
    val gradientTop = when {
        tracked -> TrackedGradientTop
        locked -> LockedGradientTop
        enabled -> EnabledGradientTop
        else -> DisabledGradientTop
    }
    val gradientBot = when {
        tracked -> TrackedGradientBot
        locked -> LockedGradientBot
        enabled -> EnabledGradientBot
        else -> DisabledGradientBot
    }
    val textColor = when {
        tracked -> TrackedText
        locked -> LockedText
        enabled -> EnabledText
        else -> DisabledText
    }
    val bevelHighlight = when {
        tracked -> TrackedBevel
        locked -> LockedBevel
        else -> BevelLight
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(STAIR_WIDTH)
            .height(STAIR_HEIGHT)
            .drawBehind {
                drawStoneStair(gradientTop, gradientBot, bevelHighlight, enabled)
            }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

private fun DrawScope.drawStoneDPad(
    gradientTop: Color,
    gradientBot: Color,
    bevelHighlight: Color,
    active: Boolean
) {
    val w = size.width
    val h = size.height

    // Stone body gradient
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(gradientTop, gradientBot)),
        cornerRadius = CornerRadius(6f, 6f),
        size = Size(w, h)
    )

    // Border: 1px frameMid
    drawRoundRect(
        color = StoneTheme.frameMid,
        cornerRadius = CornerRadius(6f, 6f),
        size = Size(w, h),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
    )

    if (active) {
        // Top-left bevel highlight
        drawLine(bevelHighlight.copy(alpha = 0.6f), Offset(2f, 1f), Offset(w - 2f, 1f), strokeWidth = 1f)
        drawLine(bevelHighlight.copy(alpha = 0.4f), Offset(1f, 2f), Offset(1f, h - 2f), strokeWidth = 1f)

        // Bottom-right shadow
        drawLine(BevelShadow, Offset(2f, h - 1f), Offset(w - 2f, h - 1f), strokeWidth = 1f)
        drawLine(BevelShadow, Offset(w - 1f, 2f), Offset(w - 1f, h - 2f), strokeWidth = 1f)
    }
}

private fun DrawScope.drawStoneStair(
    gradientTop: Color,
    gradientBot: Color,
    bevelHighlight: Color,
    active: Boolean
) {
    val w = size.width
    val h = size.height

    // Stone body gradient
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(gradientTop, gradientBot)),
        cornerRadius = CornerRadius(4f, 4f),
        size = Size(w, h)
    )

    // Border
    drawRoundRect(
        color = StoneTheme.frameMid,
        cornerRadius = CornerRadius(4f, 4f),
        size = Size(w, h),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
    )

    if (active) {
        // Top bevel
        drawLine(bevelHighlight.copy(alpha = 0.5f), Offset(2f, 1f), Offset(w - 2f, 1f), strokeWidth = 1f)
        // Bottom shadow
        drawLine(BevelShadow, Offset(2f, h - 1f), Offset(w - 2f, h - 1f), strokeWidth = 1f)
    }
}
