package com.neomud.client.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.client.viewmodel.LogEntry

// Warm log palette — leather-tinted dark, not code-editor blue
private val LogBgTop = Color(0xFF12100C)     // warm near-black (leather tint)
private val LogBgMid = Color(0xFF0D0B08)     // textPanelBg, warm
private val LogBgBot = Color(0xFF100E0A)     // slightly lighter at bottom

// Scrollbar colors matching stone palette
private val ScrollTrack = Color(0xFF1A1510)  // frameDark — subtle track
private val ScrollThumb = Color(0xFF5A5040)  // frameLight — visible thumb
private val ScrollThumbAlpha = 0.7f

@Composable
fun GameLog(
    entries: List<LogEntry>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    val lastEntryId = entries.lastOrNull()?.id ?: -1L
    LaunchedEffect(lastEntryId) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.size - 1)
        }
    }

    val scrollbarWidth = 4.dp

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Warm vertical gradient background instead of flat blue-black
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(LogBgTop, LogBgMid, LogBgBot)
                    )
                )
                // Subtle inner shadow at top edge (depth cue — text scrolls "under" the frame)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            StoneTheme.innerShadow.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 8.dp.toPx()
                    )
                )
            }
            .padding(8.dp)
            .drawWithContent {
                drawContent()
                val info = listState.layoutInfo
                val totalItems = info.totalItemsCount
                if (totalItems > 0 && info.visibleItemsInfo.isNotEmpty()) {
                    val viewportHeight = info.viewportSize.height.toFloat()
                    val firstVisible = info.visibleItemsInfo.first()
                    val scrollFraction = firstVisible.index.toFloat() / totalItems
                    val visibleFraction = (info.visibleItemsInfo.size.toFloat() / totalItems)
                        .coerceAtMost(1f)
                    val thumbHeight = (visibleFraction * viewportHeight).coerceAtLeast(16.dp.toPx())
                    val trackHeight = viewportHeight
                    val thumbTop = scrollFraction * (trackHeight - thumbHeight)
                    val barW = scrollbarWidth.toPx()
                    val barX = size.width - barW

                    // Track — warm stone dark
                    drawRect(
                        color = ScrollTrack,
                        topLeft = Offset(barX, 0f),
                        size = Size(barW, trackHeight)
                    )
                    // Thumb — stone light, semi-transparent
                    drawRect(
                        color = ScrollThumb.copy(alpha = ScrollThumbAlpha),
                        topLeft = Offset(barX, thumbTop),
                        size = Size(barW, thumbHeight)
                    )
                }
            }
    ) {
        items(entries, key = { it.id }) { entry ->
            Text(
                text = buildAnnotatedString {
                    for (span in entry.spans) {
                        withStyle(SpanStyle(color = span.color)) {
                            append(span.text)
                        }
                    }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 1.dp)
            )
        }
    }
}
