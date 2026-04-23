package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomud.client.network.ReconnectStatus

/**
 * Translucent fullscreen overlay shown while a previously-established
 * WebSocket connection is being reconnected (e.g. during a Maker
 * "Reload Playtest"). Renders nothing when status is Idle.
 *
 * - Reconnecting: spinner + "World is reloading… reconnecting (N / MAX)".
 * - Failed:      "World is unreachable" + Retry button that calls [onRetry].
 */
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
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1a1a2e))
                .padding(horizontal = 36.dp, vertical = 28.dp),
        ) {
            when (status) {
                is ReconnectStatus.Reconnecting -> {
                    CircularProgressIndicator(
                        color = Color(0xFFd0b060),
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        text = "World is reloading…",
                        color = Color(0xFFe0e0f0),
                        fontSize = 18.sp,
                    )
                    Text(
                        text = "reconnecting (${status.attempt} / ${status.maxAttempts})",
                        color = Color(0xFF9090b0),
                        fontSize = 13.sp,
                    )
                }
                is ReconnectStatus.Failed -> {
                    Text(
                        text = "World is unreachable",
                        color = Color(0xFFffb0b0),
                        fontSize = 18.sp,
                    )
                    Text(
                        text = "Check your connection and try again.",
                        color = Color(0xFF9090b0),
                        fontSize = 13.sp,
                    )
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                }
                is ReconnectStatus.Idle -> Unit // Handled by early return above.
            }
        }
    }
}
