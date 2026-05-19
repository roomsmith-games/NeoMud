package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomud.client.network.ReconnectStatus
import com.neomud.client.ui.theme.StoneTheme

private val BurnishedGold = Color(0xFFCCA855)
private val BoneWhite = Color(0xFFD8CCAA)
private val AshGray = Color(0xFF5A5040)
private val CrimsonError = Color(0xFFCC4444)

@Composable
fun ReconnectOverlay(
    status: ReconnectStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (status is ReconnectStatus.Idle) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .drawBehind {
                    val w = size.width; val h = size.height
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A1510), Color(0xFF100E0B), Color(0xFF080604))
                        )
                    )
                    drawRect(StoneTheme.frameMid, Offset.Zero, Size(w, 3f))
                    drawRect(StoneTheme.frameMid, Offset(0f, h - 3f), Size(w, 3f))
                    drawRect(StoneTheme.frameMid, Offset(0f, 0f), Size(3f, h))
                    drawRect(StoneTheme.frameMid, Offset(w - 3f, 0f), Size(3f, h))
                    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(w, 0f), 1f)
                    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(0f, h), 1f)
                    drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                    drawLine(StoneTheme.innerShadow, Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
                }
                .background(Color.Transparent)
                .padding(horizontal = 36.dp, vertical = 28.dp),
        ) {
            when (status) {
                is ReconnectStatus.Reconnecting -> {
                    CircularProgressIndicator(
                        color = BurnishedGold,
                        trackColor = AshGray.copy(alpha = 0.3f),
                        modifier = Modifier.size(44.dp),
                    )
                    Text(
                        text = "World is restarting…",
                        color = BoneWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Reconnecting (${status.attempt} / ${status.maxAttempts})",
                        color = AshGray,
                        fontSize = 12.sp,
                    )
                }
                is ReconnectStatus.Failed -> {
                    Text(
                        text = "World is unreachable",
                        color = CrimsonError,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "The server may be down for maintenance.\nCheck back shortly.",
                        color = AshGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(StoneTheme.frameLight, StoneTheme.frameDark)
                                ),
                                RoundedCornerShape(6.dp)
                            )
                            .drawBehind {
                                val w = size.width; val h = size.height
                                drawLine(BurnishedGold.copy(alpha = 0.5f), Offset(0f, 0f), Offset(w, 0f), 1f)
                                drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                            }
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Retry",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BurnishedGold
                        )
                    }
                }
                is ReconnectStatus.Idle -> Unit
            }
        }
    }
}
