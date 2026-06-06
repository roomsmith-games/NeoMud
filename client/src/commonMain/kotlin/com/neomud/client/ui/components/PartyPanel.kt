package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.shared.model.PartyMember

@Composable
fun PartyPanel(
    members: List<PartyMember>,
    leaderId: String?,
    playerName: String?,
    playerRoomId: String?,
    onInvite: (String) -> Unit,
    onKick: (String) -> Unit,
    onLeave: () -> Unit,
    onFollow: (String) -> Unit,
    onPromote: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inviteName by remember { mutableStateOf("") }
    var showLeaveConfirmation by remember { mutableStateOf(false) }
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
                    val inDifferentRoom = playerRoomId != null && member.roomId.isNotEmpty() && member.roomId != playerRoomId
                    val nameColor = when {
                        isMe -> Color(0xFF55FF55)
                        inDifferentRoom -> Color(0xFF888888)
                        else -> Color.White
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${member.name}$crown",
                                    color = nameColor,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (inDifferentRoom) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        "(elsewhere)",
                                        color = Color(0xFF666666),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Text(
                                "Lv.${member.level} ${member.characterClass}",
                                color = Color(0xFFAAAAAA),
                                fontSize = 11.sp
                            )
                            if (!isMe) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        "Follow",
                                        color = Color(0xFF55AAFF),
                                        fontSize = 10.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color(0xFF1A2A3A))
                                            .clickable { onFollow(member.name) }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    if (isLeader) {
                                        Text(
                                            "Promote",
                                            color = StoneTheme.metalGold,
                                            fontSize = 10.sp,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(Color(0xFF2A2A1A))
                                                .clickable { onPromote(member.name) }
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${member.currentHp}/${member.maxHp}",
                                    color = Color(0xFF999999),
                                    fontSize = 9.sp
                                )
                                Spacer(Modifier.width(4.dp))
                                HpBar(member.currentHp, member.maxHp)
                            }
                            if (member.maxMp > 0) {
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${member.currentMp}/${member.maxMp}",
                                        color = Color(0xFF999999),
                                        fontSize = 9.sp
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    MpBar(member.currentMp, member.maxMp)
                                }
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
                    .clickable { showLeaveConfirmation = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (showLeaveConfirmation) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(onClick = { showLeaveConfirmation = false }),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .fillMaxWidth(0.8f)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1A1510), Color(0xFF0D0B09))
                            ),
                            RoundedCornerShape(8.dp)
                        )
                        .border(1.dp, Color(0xFFFF5555).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(enabled = false, onClick = {})
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Leave Party?",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF5555)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Are you sure you want to leave the party?",
                        fontSize = 13.sp,
                        color = Color(0xFFCCCCCC),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

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
                            .border(1.dp, Color(0xFFFF5555).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable(onClick = {
                                showLeaveConfirmation = false
                                onLeave()
                                onDismiss()
                            }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Leave", fontSize = 13.sp, color = Color(0xFFFF5555))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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
                            .border(1.dp, Color(0xFF888888).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .clickable(onClick = { showLeaveConfirmation = false }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Cancel", fontSize = 13.sp, color = Color(0xFFCCCCCC))
                    }
                }
            }
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
