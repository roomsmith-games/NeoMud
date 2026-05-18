package com.neomud.client.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.neomud.client.ui.theme.StoneTheme

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoneTooltip(
    text: String,
    content: @Composable () -> Unit
) {
    if (text.isEmpty()) {
        content()
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                containerColor = StoneTheme.panelBg,
                contentColor = Color(0xFFD8CCAA)
            ) {
                Text(text, fontSize = 12.sp)
            }
        },
        state = rememberTooltipState(),
        content = content
    )
}
