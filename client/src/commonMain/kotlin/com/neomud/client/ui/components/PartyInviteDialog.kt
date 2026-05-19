package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.shared.protocol.ServerMessage

@Composable
fun PartyInviteDialog(
    invite: ServerMessage.PartyInviteReceived,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var secondsLeft by remember { mutableStateOf(60) }

    LaunchedEffect(invite) {
        secondsLeft = 60
        while (secondsLeft > 0) {
            kotlinx.coroutines.delay(1000)
            secondsLeft--
        }
        onDecline()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDecline() }
            .testTag("partyInviteDialog"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .drawBehind {
                    val w = size.width; val h = size.height
                    drawRect(StoneTheme.panelBg)
                    drawRect(StoneTheme.frameMid, size = size)
                    drawRect(StoneTheme.panelBg, topLeft = Offset(3f, 3f), size = Size(w - 6f, h - 6f))
                    drawLine(StoneTheme.frameLight, Offset(1f, 1f), Offset(w - 1f, 1f), 2f)
                    drawLine(StoneTheme.frameLight, Offset(1f, 1f), Offset(1f, h - 1f), 2f)
                    drawLine(StoneTheme.innerShadow, Offset(1f, h - 2f), Offset(w - 1f, h - 2f), 2f)
                    drawLine(StoneTheme.innerShadow, Offset(w - 2f, 1f), Offset(w - 2f, h - 1f), 2f)
                    drawLine(StoneTheme.metalGold, Offset(12f, 52f), Offset(w - 12f, 52f), 1f)
                }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Party Invite",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCCA855)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "${invite.inviterName} invites you to join their party.",
                fontSize = 14.sp,
                color = Color(0xFFCCCCCC)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Party size: ${invite.partySize}/4",
                fontSize = 12.sp,
                color = Color(0xFF999999)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Expires in ${secondsLeft}s",
                fontSize = 11.sp,
                color = Color(0xFF888888)
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                Box(
                    modifier = Modifier
                        .drawBehind {
                            val w = size.width; val h = size.height
                            drawRect(Brush.verticalGradient(listOf(Color(0xFF2A4A2A), Color(0xFF1A3A1A))))
                            drawLine(StoneTheme.frameLight, Offset(0f, 0.5f), Offset(w, 0.5f), 1f)
                            drawLine(StoneTheme.frameLight, Offset(0.5f, 0f), Offset(0.5f, h), 1f)
                            drawLine(StoneTheme.innerShadow, Offset(0f, h - 0.5f), Offset(w, h - 0.5f), 1f)
                            drawLine(StoneTheme.innerShadow, Offset(w - 0.5f, 0f), Offset(w - 0.5f, h), 1f)
                        }
                        .clickable { onAccept() }
                        .testTag("partyInviteAccept")
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Accept", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF55FF55))
                }
                Box(
                    modifier = Modifier
                        .drawBehind {
                            val w = size.width; val h = size.height
                            drawRect(Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark)))
                            drawLine(StoneTheme.frameLight, Offset(0f, 0.5f), Offset(w, 0.5f), 1f)
                            drawLine(StoneTheme.frameLight, Offset(0.5f, 0f), Offset(0.5f, h), 1f)
                            drawLine(StoneTheme.innerShadow, Offset(0f, h - 0.5f), Offset(w, h - 0.5f), 1f)
                            drawLine(StoneTheme.innerShadow, Offset(w - 0.5f, 0f), Offset(w - 0.5f, h), 1f)
                        }
                        .clickable { onDecline() }
                        .testTag("partyInviteDecline")
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Decline", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCCA855))
                }
            }
        }
    }
}
