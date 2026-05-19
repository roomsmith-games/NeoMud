package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomud.shared.model.PlayerInfo

@Composable
fun PlayerTooltip(
    playerInfo: PlayerInfo,
    onDismiss: () -> Unit,
    onInviteToParty: ((String) -> Unit)? = null,
    onFollow: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 220.dp)
                .clickable(onClick = {}),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xEE1a1a2e))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = playerInfo.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                val race = playerInfo.race
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
                    .replace("_", " ")
                val cls = playerInfo.characterClass
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
                Text(
                    text = "Level ${playerInfo.level} $race $cls",
                    fontSize = 13.sp,
                    color = Color(0xFFAAAAAA)
                )
                if (onInviteToParty != null || onFollow != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onInviteToParty != null) {
                            Text(
                                "Invite",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF55AAFF),
                                modifier = Modifier
                                    .background(Color(0xFF222244), RoundedCornerShape(4.dp))
                                    .clickable { onInviteToParty(playerInfo.name); onDismiss() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (onFollow != null) {
                            Text(
                                "Follow",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF55FF55),
                                modifier = Modifier
                                    .background(Color(0xFF224422), RoundedCornerShape(4.dp))
                                    .clickable { onFollow(playerInfo.name); onDismiss() }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
