package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.shared.model.PartyMember

@Composable
fun PartyPanel(
    members: List<PartyMember>,
    leaderId: String?,
    playerName: String?,
    onInvite: (String) -> Unit,
    onKick: (String) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit
) {
    var inviteName by remember { mutableStateOf("") }
    val isLeader = playerName == leaderId

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .testTag("partyPanel"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .drawBehind {
                    val w = size.width; val h = size.height
                    drawRect(StoneTheme.panelBg)
                    drawRect(StoneTheme.frameMid, size = size)
                    drawRect(StoneTheme.frameDark, topLeft = Offset(3f, 3f), size = Size(w - 6f, h - 6f))
                    drawRect(StoneTheme.panelBg, topLeft = Offset(6f, 6f), size = Size(w - 12f, h - 12f))
                }
                .padding(16.dp)
        ) {
            Text("Party", color = StoneTheme.metalGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                for (member in members) {
                    val isMe = member.name == playerName
                    val crown = if (member.isLeader) " ♕" else ""
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${member.name}$crown",
                                color = if (isMe) Color(0xFF55FF55) else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Lv.${member.level} ${member.characterClass}",
                                color = Color(0xFFAAAAAA),
                                fontSize = 11.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            HpBar(member.currentHp, member.maxHp)
                            if (member.maxMp > 0) {
                                Spacer(Modifier.height(2.dp))
                                MpBar(member.currentMp, member.maxMp)
                            }
                        }
                        if (isLeader && !isMe) {
                            Text(
                                " X",
                                color = Color(0xFFFF5555),
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { onKick(member.name) }
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isLeader && members.size < 4) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicTextField(
                        value = inviteName,
                        onValueChange = { inviteName = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inviteName.isNotBlank()) {
                                onInvite(inviteName.trim())
                                inviteName = ""
                            }
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        decorationBox = { inner ->
                            if (inviteName.isEmpty()) {
                                Text("Invite player...", color = Color(0xFF666666), fontSize = 13.sp)
                            }
                            inner()
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Invite",
                        color = StoneTheme.metalGold,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF333333))
                            .clickable {
                                if (inviteName.isNotBlank()) {
                                    onInvite(inviteName.trim())
                                    inviteName = ""
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                "Leave Party",
                color = Color(0xFFFF5555),
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF331111))
                    .clickable { onLeave(); onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun HpBar(current: Int, max: Int) {
    val pct = if (max > 0) current.toFloat() / max else 0f
    val color = when {
        pct > 0.6f -> Color(0xFF55FF55)
        pct > 0.3f -> Color(0xFFFFFF55)
        else -> Color(0xFFFF5555)
    }
    Box(
        modifier = Modifier.width(60.dp).height(8.dp)
            .background(Color(0xFF1A1A1A), RoundedCornerShape(2.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxHeight().fillMaxWidth(pct)
                .background(color, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun MpBar(current: Int, max: Int) {
    val pct = if (max > 0) current.toFloat() / max else 0f
    Box(
        modifier = Modifier.width(60.dp).height(6.dp)
            .background(Color(0xFF1A1A1A), RoundedCornerShape(2.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxHeight().fillMaxWidth(pct)
                .background(Color(0xFF5555FF), RoundedCornerShape(2.dp))
        )
    }
}
