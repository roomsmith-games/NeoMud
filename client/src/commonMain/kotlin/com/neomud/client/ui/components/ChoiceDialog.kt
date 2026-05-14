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
fun ChoiceDialog(
    prompt: ServerMessage.ChoicePrompt,
    onChoice: (choiceId: String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .testTag("choiceDialog"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .drawBehind {
                    val w = size.width; val h = size.height
                    drawRect(StoneTheme.panelBg)
                    drawRect(StoneTheme.frameMid, size = size)
                    drawRect(
                        StoneTheme.panelBg,
                        topLeft = Offset(3f, 3f),
                        size = Size(w - 6f, h - 6f)
                    )
                    drawLine(StoneTheme.frameLight, Offset(1f, 1f), Offset(w - 1f, 1f), 2f)
                    drawLine(StoneTheme.frameLight, Offset(1f, 1f), Offset(1f, h - 1f), 2f)
                    drawLine(StoneTheme.innerShadow, Offset(1f, h - 2f), Offset(w - 1f, h - 2f), 2f)
                    drawLine(StoneTheme.innerShadow, Offset(w - 2f, 1f), Offset(w - 2f, h - 1f), 2f)
                    drawLine(StoneTheme.metalGold, Offset(12f, 64f), Offset(w - 12f, 64f), 1f)
                }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = prompt.label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCCA855)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = prompt.question,
                fontSize = 14.sp,
                color = Color(0xFFCCCCCC)
            )

            Spacer(modifier = Modifier.height(20.dp))

            prompt.options.forEach { option ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .drawBehind {
                            val w = size.width; val h = size.height
                            drawRect(Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark)))
                            drawLine(StoneTheme.frameLight, Offset(0f, 0.5f), Offset(w, 0.5f), 1f)
                            drawLine(StoneTheme.frameLight, Offset(0.5f, 0f), Offset(0.5f, h), 1f)
                            drawLine(StoneTheme.innerShadow, Offset(0f, h - 0.5f), Offset(w, h - 0.5f), 1f)
                            drawLine(StoneTheme.innerShadow, Offset(w - 0.5f, 0f), Offset(w - 0.5f, h), 1f)
                        }
                        .clickable { onChoice(option.id) }
                        .testTag("choiceOption_${option.id}")
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCCA855)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                    .clickable { onDismiss() }
                    .testTag("choiceCancel")
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCCA855)
                )
            }
        }
    }
}
