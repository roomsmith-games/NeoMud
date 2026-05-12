package com.neomud.client.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import com.neomud.client.platform.PlatformAudioManager
import com.neomud.client.ui.theme.StoneTheme
import org.jetbrains.compose.resources.painterResource

// ─────────────────────────────────────────────
// Palette — Stone & Torchlight
// ─────────────────────────────────────────────
private val DeepVoid = Color(0xFF080604)
private val WornLeather = Color(0xFF1A1510)
private val BurnishedGold = Color(0xFFCCA855)
private val TorchAmber = Color(0xFFBBA060)
private val BoneWhite = Color(0xFFD8CCAA)
private val AshGray = Color(0xFF5A5040)
private val CrimsonError = Color(0xFFCC4444)
private val EmptySlotEdge = Color(0xFF2A2218)

// ─────────────────────────────────────────────
// Stone frame drawing
// ─────────────────────────────────────────────
private fun DrawScope.drawStoneFrame(borderPx: Float) {
    val w = size.width; val h = size.height
    drawRect(StoneTheme.frameMid, Offset.Zero, Size(w, borderPx))
    drawRect(StoneTheme.frameMid, Offset(0f, h - borderPx), Size(w, borderPx))
    drawRect(StoneTheme.frameMid, Offset(0f, borderPx), Size(borderPx, h - borderPx * 2))
    drawRect(StoneTheme.frameMid, Offset(w - borderPx, borderPx), Size(borderPx, h - borderPx * 2))
    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(w, 0f), 1f)
    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(0f, h), 1f)
    drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
    drawLine(StoneTheme.innerShadow, Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
    drawLine(StoneTheme.innerShadow, Offset(borderPx, h - borderPx), Offset(w - borderPx, h - borderPx), 1f)
    drawLine(StoneTheme.innerShadow, Offset(w - borderPx, borderPx), Offset(w - borderPx, h - borderPx), 1f)
    drawLine(StoneTheme.runeGlow, Offset(borderPx, borderPx), Offset(w - borderPx, borderPx), 1f)
    drawLine(StoneTheme.runeGlow, Offset(borderPx, borderPx), Offset(borderPx, h - borderPx), 1f)
    val rivetRadius = 3f; val rivetOffset = borderPx / 2f
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(rivetOffset, rivetOffset))
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(w - rivetOffset, rivetOffset))
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(rivetOffset, h - rivetOffset))
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(w - rivetOffset, h - rivetOffset))
}

@Composable
fun SettingsPanel(
    isLandscape: Boolean,
    onSetLayoutPreference: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onClose: () -> Unit,
    audioManager: PlatformAudioManager? = null,
    isGuest: Boolean = false
) {
    var showGuestLogoutWarning by remember { mutableStateOf(false) }
    var masterVolume by remember { mutableFloatStateOf(audioManager?.masterVolume ?: 1f) }
    var sfxVolume by remember { mutableFloatStateOf(audioManager?.sfxVolume ?: 1f) }
    var bgmVolume by remember { mutableFloatStateOf(audioManager?.bgmVolume ?: 0.5f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* consume backdrop touches */ }
            .padding(if (isLandscape) 8.dp else 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val borderPx = 4.dp
        Box(
            modifier = Modifier
                .then(
                    if (isLandscape) Modifier.fillMaxSize()
                    else Modifier.fillMaxWidth()
                )
                .drawBehind { drawStoneFrame(borderPx.toPx()) }
                .padding(borderPx)
                .background(
                    Brush.verticalGradient(
                        listOf(WornLeather, Color(0xFF100E0B), DeepVoid, Color(0xFF100E0B), WornLeather)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(MudIcons.Settings),
                            contentDescription = "Settings",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Settings",
                            color = BurnishedGold,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Stone close button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(StoneTheme.frameLight, StoneTheme.frameDark)
                                ),
                                RoundedCornerShape(3.dp)
                            )
                            .drawBehind {
                                val w = size.width; val h = size.height
                                drawLine(StoneTheme.frameLight.copy(alpha = 0.5f), Offset(0f, 0f), Offset(w, 0f), 1f)
                                drawLine(StoneTheme.frameLight.copy(alpha = 0.5f), Offset(0f, 0f), Offset(0f, h), 1f)
                                drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                                drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
                            }
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        CloseIcon(color = BoneWhite)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Gold ornamental line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, BurnishedGold.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isLandscape) {
                    // Landscape: two-column layout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left column: Layout + Logout
                        Column(modifier = Modifier.weight(1f)) {
                            LayoutSection(isLandscape, onSetLayoutPreference)
                            Spacer(modifier = Modifier.weight(1f))
                            LogoutButton(if (isGuest) {{ showGuestLogoutWarning = true }} else onLogout)
                        }

                        // Vertical divider — gradient fade
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            AshGray.copy(alpha = 0.4f),
                                            AshGray.copy(alpha = 0.4f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Right column: Audio
                        Column(modifier = Modifier.weight(1f)) {
                            if (audioManager != null) {
                                AudioSection(
                                    masterVolume, sfxVolume, bgmVolume,
                                    onMasterChange = { masterVolume = it; audioManager.setVolumes(masterVolume, sfxVolume, bgmVolume) },
                                    onSfxChange = { sfxVolume = it; audioManager.setVolumes(masterVolume, sfxVolume, bgmVolume) },
                                    onBgmChange = { bgmVolume = it; audioManager.setVolumes(masterVolume, sfxVolume, bgmVolume) }
                                )
                            }
                        }
                    }
                } else {
                    // Portrait: single-column vertical stack
                    LayoutSection(isLandscape, onSetLayoutPreference)

                    if (audioManager != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        RunicDivider()
                        Spacer(modifier = Modifier.height(10.dp))
                        AudioSection(
                            masterVolume, sfxVolume, bgmVolume,
                            onMasterChange = { masterVolume = it; audioManager.setVolumes(masterVolume, sfxVolume, bgmVolume) },
                            onSfxChange = { sfxVolume = it; audioManager.setVolumes(masterVolume, sfxVolume, bgmVolume) },
                            onBgmChange = { bgmVolume = it; audioManager.setVolumes(masterVolume, sfxVolume, bgmVolume) }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    RunicDivider()
                    Spacer(modifier = Modifier.height(6.dp))
                    LogoutButton(if (isGuest) {{ showGuestLogoutWarning = true }} else onLogout)
                }
            }
        }
    }

    // Guest logout confirmation dialog — zIndex ensures it renders above the settings panel
    if (showGuestLogoutWarning) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(onClick = { showGuestLogoutWarning = false }),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(0.85f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A1510), Color(0xFF0D0B09))
                        ),
                        RoundedCornerShape(8.dp)
                    )
                    .border(1.dp, CrimsonError.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable(enabled = false, onClick = {})
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Leave Game?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonError
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "You are playing as a guest. All progress will be permanently lost.",
                    fontSize = 13.sp,
                    color = BoneWhite,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Leave button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF3A1515), Color(0xFF2A0A0A))
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .border(1.dp, CrimsonError.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable(onClick = {
                            showGuestLogoutWarning = false
                            onLogout()
                        }),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Leave", fontSize = 13.sp, color = CrimsonError)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Keep Playing button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF2A2218), Color(0xFF1A1510))
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .border(1.dp, AshGray.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable(onClick = { showGuestLogoutWarning = false }),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Keep Playing", fontSize = 13.sp, color = BoneWhite)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Runic divider
// ─────────────────────────────────────────────
@Composable
private fun RunicDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, AshGray.copy(alpha = 0.4f))
                    )
                )
        )
        Text(
            "*",
            fontSize = 10.sp,
            color = AshGray.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(AshGray.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )
    }
}

// ─────────────────────────────────────────────
// Layout toggle — stone selection tabs
// ─────────────────────────────────────────────
@Composable
private fun LayoutSection(
    isLandscape: Boolean,
    onSetLayoutPreference: (Boolean) -> Unit
) {
    Text(
        "Layout",
        color = TorchAmber,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsTab(
            text = "Portrait",
            isSelected = !isLandscape,
            onClick = { onSetLayoutPreference(false) }
        )
        SettingsTab(
            text = "Landscape",
            isSelected = isLandscape,
            onClick = { onSetLayoutPreference(true) }
        )
    }
}

@Composable
private fun SettingsTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected)
        Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameMid))
    else
        Brush.verticalGradient(listOf(StoneTheme.frameDark, Color(0xFF0D0A08)))

    val borderColor = if (isSelected) BurnishedGold else EmptySlotEdge

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .drawBehind {
                val w = size.width; val h = size.height
                if (isSelected) {
                    drawLine(BurnishedGold.copy(alpha = 0.3f), Offset(0f, 0f), Offset(w, 0f), 1f)
                    drawLine(BurnishedGold.copy(alpha = 0.2f), Offset(0f, 0f), Offset(0f, h), 1f)
                }
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (isSelected) BurnishedGold else AshGray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

// ─────────────────────────────────────────────
// Audio section with stone-themed sliders
// ─────────────────────────────────────────────
@Composable
private fun AudioSection(
    masterVolume: Float,
    sfxVolume: Float,
    bgmVolume: Float,
    onMasterChange: (Float) -> Unit,
    onSfxChange: (Float) -> Unit,
    onBgmChange: (Float) -> Unit
) {
    Text(
        "Audio",
        color = TorchAmber,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    VolumeSlider("Master", masterVolume, onMasterChange)
    VolumeSlider("SFX", sfxVolume, onSfxChange)
    VolumeSlider("Music", bgmVolume, onBgmChange)
}

// ─────────────────────────────────────────────
// Logout button — ember/crimson stone beveled
// ─────────────────────────────────────────────
@Composable
private fun LogoutButton(onLogout: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(
                Brush.verticalGradient(listOf(CrimsonError, Color(0xFF882222))),
                RoundedCornerShape(4.dp)
            )
            .drawBehind {
                val w = size.width; val h = size.height
                drawLine(CrimsonError.copy(alpha = 0.5f), Offset(0f, 0f), Offset(w, 0f), 1f)
                drawLine(CrimsonError.copy(alpha = 0.3f), Offset(0f, 0f), Offset(0f, h), 1f)
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
            }
            .clickable(onClick = onLogout),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Logout & Exit",
            color = BoneWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

// ─────────────────────────────────────────────
// Volume slider — stone-themed
// ─────────────────────────────────────────────
@Composable
private fun VolumeSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = AshGray,
            fontSize = 12.sp,
            modifier = Modifier.width(52.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = BurnishedGold,
                activeTrackColor = TorchAmber,
                inactiveTrackColor = StoneTheme.frameDark
            )
        )
        Text(
            "${(value * 100).toInt()}",
            color = BoneWhite,
            fontSize = 12.sp,
            modifier = Modifier.width(32.dp)
        )
    }
}
