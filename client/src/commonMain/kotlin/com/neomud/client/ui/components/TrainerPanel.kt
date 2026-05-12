package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomud.client.ui.theme.MudColors
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ServerMessage

@Composable
fun TrainerPanel(
    trainerInfo: ServerMessage.TrainerInfo,
    onLevelUp: () -> Unit,
    onAllocateStats: (Stats) -> Unit,
    onClose: () -> Unit
) {
    var tentativeStats by remember(trainerInfo) { mutableStateOf(trainerInfo.currentStats) }
    val baseStats = trainerInfo.baseStats

    // Calculate CP used by tentative allocation
    val cpUsed = remember(tentativeStats, baseStats) {
        cpForStatRange(tentativeStats.strength, baseStats.strength) +
                cpForStatRange(tentativeStats.agility, baseStats.agility) +
                cpForStatRange(tentativeStats.intellect, baseStats.intellect) +
                cpForStatRange(tentativeStats.willpower, baseStats.willpower) +
                cpForStatRange(tentativeStats.health, baseStats.health) +
                cpForStatRange(tentativeStats.charm, baseStats.charm)
    }
    val cpRemaining = trainerInfo.totalCpEarned - cpUsed
    val hasChanged = tentativeStats != trainerInfo.currentStats

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* consume clicks */ },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Guildmaster",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700)
                    )
                    TextButton(onClick = onClose) {
                        Text("Close", color = Color(0xFFAAAAAA))
                    }
                }

                HorizontalDivider(color = Color(0xFF333355), modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Level Up section
                    Text(
                        text = "Level Up",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCCCCCC)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (trainerInfo.canLevelUp) {
                        Button(
                            onClick = onLevelUp,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Level Up!", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "You are not ready to level up yet.",
                            fontSize = 13.sp,
                            color = Color(0xFF888888)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFF333355))
                    Spacer(modifier = Modifier.height(16.dp))

                    // CP display
                    if (trainerInfo.totalCpEarned > 0) {
                        Text(
                            text = "CP: $cpUsed / ${trainerInfo.totalCpEarned}  ($cpRemaining remaining)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MudColors.xp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stat training grid
                        Text(
                            text = "Train Stats",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCCCCCC)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cost: 1 CP (0-9 above base), 2 CP (10-19), 3 CP (20+)",
                            fontSize = 11.sp,
                            color = Color(0xFF777777)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val statEntries = listOf(
                            StatEntry("Strength", tentativeStats.strength, baseStats.strength) { v -> tentativeStats = tentativeStats.copy(strength = v) },
                            StatEntry("Agility", tentativeStats.agility, baseStats.agility) { v -> tentativeStats = tentativeStats.copy(agility = v) },
                            StatEntry("Intellect", tentativeStats.intellect, baseStats.intellect) { v -> tentativeStats = tentativeStats.copy(intellect = v) },
                            StatEntry("Willpower", tentativeStats.willpower, baseStats.willpower) { v -> tentativeStats = tentativeStats.copy(willpower = v) },
                            StatEntry("Health", tentativeStats.health, baseStats.health) { v -> tentativeStats = tentativeStats.copy(health = v) },
                            StatEntry("Charm", tentativeStats.charm, baseStats.charm) { v -> tentativeStats = tentativeStats.copy(charm = v) }
                        )

                        for (entry in statEntries) {
                            val costToAdd = costToRaise(entry.current, entry.base)
                            val canAdd = cpRemaining >= costToAdd
                            val canRemove = entry.current > entry.base

                            StatAllocRow(
                                statName = entry.name,
                                currentValue = entry.current,
                                baseValue = entry.base,
                                costToAdd = costToAdd,
                                canAdd = canAdd,
                                canRemove = canRemove,
                                onAdd1 = { entry.onSet(entry.current + 1) },
                                onRemove1 = { entry.onSet(entry.current - 1) },
                                onAdd5 = {
                                    var value = entry.current
                                    var budget = cpRemaining
                                    repeat(5) {
                                        val cost = costToRaise(value, entry.base)
                                        if (budget >= cost) { value++; budget -= cost }
                                    }
                                    entry.onSet(value)
                                },
                                onRemove5 = {
                                    entry.onSet(maxOf(entry.current - 5, entry.base))
                                },
                                onMax = {
                                    var value = entry.current
                                    var budget = cpRemaining
                                    while (true) {
                                        val cost = costToRaise(value, entry.base)
                                        if (budget < cost) break
                                        value++; budget -= cost
                                    }
                                    entry.onSet(value)
                                }
                            )
                        }
                    } else {
                        Text(
                            text = "Level up to earn CP for stat training!",
                            fontSize = 14.sp,
                            color = Color(0xFF888888)
                        )
                    }
                }

                // Bottom buttons (only show when there's CP to train with)
                if (trainerInfo.totalCpEarned > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { tentativeStats = trainerInfo.currentStats },
                            enabled = hasChanged,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFAAAAAA),
                                disabledContentColor = Color(0xFF555555)
                            )
                        ) {
                            Text("Reset")
                        }
                        Button(
                            onClick = { onAllocateStats(tentativeStats) },
                            enabled = hasChanged && cpUsed <= trainerInfo.totalCpEarned,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1565C0),
                                disabledContainerColor = Color(0xFF333333)
                            )
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

private data class StatEntry(
    val name: String,
    val current: Int,
    val base: Int,
    val onSet: (Int) -> Unit
)

private fun costToRaise(currentValue: Int, baseValue: Int): Int {
    val above = currentValue - baseValue
    return when {
        above < 10 -> 1
        above < 20 -> 2
        else -> 3
    }
}

private fun costToLower(currentValue: Int, baseValue: Int): Int {
    val above = currentValue - baseValue - 1
    return when {
        above < 10 -> 1
        above < 20 -> 2
        else -> 3
    }
}

/** Total CP cost from base to current for a single stat. */
private fun cpForStatRange(current: Int, base: Int): Int {
    var cost = 0
    for (i in 0 until (current - base)) {
        cost += when {
            i < 10 -> 1
            i < 20 -> 2
            else -> 3
        }
    }
    return cost
}

@Composable
private fun StatAllocRow(
    statName: String,
    currentValue: Int,
    baseValue: Int,
    costToAdd: Int,
    canAdd: Boolean,
    canRemove: Boolean,
    onAdd1: () -> Unit,
    onRemove1: () -> Unit,
    onAdd5: () -> Unit,
    onRemove5: () -> Unit,
    onMax: () -> Unit
) {
    val above = currentValue - baseValue
    val statColor = when {
        above >= 20 -> Color(0xFFFFD700)
        above >= 10 -> Color(0xFF66BB6A)
        above > 0 -> Color(0xFF42A5F5)
        else -> Color(0xFFCCCCCC)
    }
    val canRemove5 = currentValue - baseValue >= 5

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Stat name + value
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = statName,
                    fontSize = 14.sp,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.width(72.dp)
                )
                Text(
                    text = "$currentValue",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = statColor,
                    modifier = Modifier.width(30.dp)
                )
                Text(
                    text = "(${baseValue})",
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.width(36.dp)
                )
                Text(
                    text = "${costToAdd}cp",
                    fontSize = 11.sp,
                    color = if (canAdd) MudColors.xp else Color(0xFF555555)
                )
            }

            // Button cluster: -5 -1 +1 +5 Max
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallAllocButton("-5", canRemove5, Color(0xFF8B0000), onRemove5)
                SmallAllocButton("-1", canRemove, Color(0xFF8B0000), onRemove1)
                SmallAllocButton("+1", canAdd, Color(0xFF1565C0), onAdd1)
                SmallAllocButton("+5", canAdd, Color(0xFF1565C0), onAdd5)
                SmallAllocButton("Max", canAdd, Color(0xFF2E7D32), onMax)
            }
        }
    }
}

@Composable
private fun SmallAllocButton(
    label: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .height(28.dp)
            .widthIn(min = 34.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = Color(0xFF333333)
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
