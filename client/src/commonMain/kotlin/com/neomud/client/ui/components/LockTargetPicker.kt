package com.neomud.client.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomud.shared.model.Direction
import com.neomud.shared.model.RoomInteractable
import org.jetbrains.compose.resources.painterResource

private val LockGold = Color(0xFFCCAA33)
private val EnabledColor = Color(0xFFCCAA33)
private val DisabledColor = Color(0xFF444444)
private val ButtonSize = 44.dp
private val SmallButtonSize = 38.dp
private val Gap = 4.dp

@Composable
fun LockTargetPicker(
    lockedExits: Map<Direction, Int>,
    unpickableExits: Set<Direction> = emptySet(),
    lockedInteractables: List<RoomInteractable> = emptyList(),
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val pickableExits = lockedExits.filterKeys { it !in unpickableExits }
    val pickableDirections = pickableExits.keys

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // consume clicks on the card
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Pick Lock",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = LockGold
                )
                Text(
                    text = "Choose a target",
                    fontSize = 12.sp,
                    color = Color(0xFF888888)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Directional grid for locked exits
                if (lockedExits.isNotEmpty()) {
                    // Row 1: NW, N, NE
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Gap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LockDirButton("NW", Direction.NORTHWEST, pickableDirections, lockedExits.keys, onSelect, SmallButtonSize)
                        LockDirButton("\u25B2", Direction.NORTH, pickableDirections, lockedExits.keys, onSelect, ButtonSize)
                        LockDirButton("NE", Direction.NORTHEAST, pickableDirections, lockedExits.keys, onSelect, SmallButtonSize)
                    }

                    Spacer(modifier = Modifier.height(Gap))

                    // Row 2: W, lock icon, E
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Gap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LockDirButton("\u25C0", Direction.WEST, pickableDirections, lockedExits.keys, onSelect, ButtonSize)
                        Box(
                            modifier = Modifier.size(ButtonSize),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(MudIcons.PickLock),
                                contentDescription = "Pick Lock",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        LockDirButton("\u25B6", Direction.EAST, pickableDirections, lockedExits.keys, onSelect, ButtonSize)
                    }

                    Spacer(modifier = Modifier.height(Gap))

                    // Row 3: SW, S, SE
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Gap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LockDirButton("SW", Direction.SOUTHWEST, pickableDirections, lockedExits.keys, onSelect, SmallButtonSize)
                        LockDirButton("\u25BC", Direction.SOUTH, pickableDirections, lockedExits.keys, onSelect, ButtonSize)
                        LockDirButton("SE", Direction.SOUTHEAST, pickableDirections, lockedExits.keys, onSelect, SmallButtonSize)
                    }

                    // UP / DOWN row
                    val hasUp = Direction.UP in lockedExits
                    val hasDown = Direction.DOWN in lockedExits
                    if (hasUp || hasDown) {
                        Spacer(modifier = Modifier.height(Gap))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasUp) {
                                val pickable = Direction.UP in pickableDirections
                                FilledTonalButton(
                                    onClick = { if (pickable) onSelect("exit:UP") },
                                    enabled = pickable,
                                    modifier = Modifier.height(36.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (pickable) EnabledColor.copy(alpha = 0.2f) else DisabledColor,
                                        contentColor = if (pickable) EnabledColor else Color(0xFF666666),
                                        disabledContainerColor = DisabledColor,
                                        disabledContentColor = Color(0xFF666666)
                                    )
                                ) {
                                    Text(
                                        text = if (pickable) "\u2B06 Up" else "\u22A0 Up",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (hasDown) {
                                val pickable = Direction.DOWN in pickableDirections
                                FilledTonalButton(
                                    onClick = { if (pickable) onSelect("exit:DOWN") },
                                    enabled = pickable,
                                    modifier = Modifier.height(36.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = if (pickable) EnabledColor.copy(alpha = 0.2f) else DisabledColor,
                                        contentColor = if (pickable) EnabledColor else Color(0xFF666666),
                                        disabledContainerColor = DisabledColor,
                                        disabledContentColor = Color(0xFF666666)
                                    )
                                ) {
                                    Text(
                                        text = if (pickable) "\u2B07 Down" else "\u22A0 Down",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Locked interactables section
                if (lockedInteractables.isNotEmpty()) {
                    if (lockedExits.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFF333344), thickness = 1.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    for (feat in lockedInteractables) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect("feature:${feat.id}") }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(MudIcons.PickLock),
                                contentDescription = "Locked",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = feat.label,
                                fontSize = 14.sp,
                                color = Color(0xFFCCCCCC)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockDirButton(
    icon: String,
    direction: Direction,
    pickableDirections: Set<Direction>,
    allLockedDirections: Set<Direction>,
    onSelect: (String) -> Unit,
    size: androidx.compose.ui.unit.Dp
) {
    val isLocked = direction in allLockedDirections
    val isPickable = direction in pickableDirections
    // Only show button if the direction actually has a lock
    val visible = isLocked

    if (!visible) {
        // Empty placeholder to preserve grid layout
        Box(modifier = Modifier.size(size))
        return
    }

    FilledTonalButton(
        onClick = { if (isPickable) onSelect("exit:${direction.name}") },
        enabled = isPickable,
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isPickable) EnabledColor.copy(alpha = 0.2f) else DisabledColor,
            contentColor = if (isPickable) EnabledColor else Color(0xFF666666),
            disabledContainerColor = DisabledColor,
            disabledContentColor = Color(0xFF666666)
        )
    ) {
        if (!isPickable) {
            Image(
                painter = painterResource(MudIcons.PickLock),
                contentDescription = "Locked",
                modifier = Modifier.size(if (size == SmallButtonSize) 20.dp else 22.dp)
            )
        } else {
            Text(
                text = icon,
                fontSize = if (size == SmallButtonSize) 14.sp else 16.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
