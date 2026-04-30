package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.shared.protocol.ServerMessage

@Composable
fun RiddleDialog(
    prompt: ServerMessage.RiddlePrompt,
    onSubmit: (answer: String) -> Unit,
    onDismiss: () -> Unit
) {
    var answer by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .testTag("riddleDialog"),
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

            val hint = prompt.hint
            if (hint != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF888888)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            BasicTextField(
                value = answer,
                onValueChange = { answer = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .padding(12.dp)
                    .testTag("riddleInput"),
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = Color(0xFFD8CCAA)
                ),
                cursorBrush = SolidColor(Color(0xFFCCA855)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (answer.isNotBlank()) onSubmit(answer) }
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (answer.isEmpty()) {
                            Text(
                                text = "Speak your answer...",
                                fontSize = 15.sp,
                                color = Color(0xFF666666)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                // Answer button
                Box(
                    modifier = Modifier
                        .drawBehind {
                            val w = size.width; val h = size.height
                            drawRect(Brush.verticalGradient(
                                if (answer.isNotBlank()) listOf(StoneTheme.frameLight, StoneTheme.frameDark)
                                else listOf(Color(0xFF444444), Color(0xFF333333))
                            ))
                            drawLine(StoneTheme.frameLight, Offset(0f, 0.5f), Offset(w, 0.5f), 1f)
                            drawLine(StoneTheme.frameLight, Offset(0.5f, 0f), Offset(0.5f, h), 1f)
                            drawLine(StoneTheme.innerShadow, Offset(0f, h - 0.5f), Offset(w, h - 0.5f), 1f)
                            drawLine(StoneTheme.innerShadow, Offset(w - 0.5f, 0f), Offset(w - 0.5f, h), 1f)
                        }
                        .clickable(enabled = answer.isNotBlank()) { onSubmit(answer) }
                        .testTag("riddleSubmit")
                        .padding(horizontal = 32.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Answer",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (answer.isNotBlank()) Color(0xFFCCA855) else Color(0xFF666666)
                    )
                }

                // Cancel button
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
                        .testTag("riddleCancel")
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
}
