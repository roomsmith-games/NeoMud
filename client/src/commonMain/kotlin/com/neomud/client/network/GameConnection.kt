package com.neomud.client.network

import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.ServerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Abstraction over the WebSocket connection for testability. */
interface GameConnection {
    val messages: SharedFlow<ServerMessage>
    val connectionState: StateFlow<ConnectionState>
    val connectionError: StateFlow<String?>

    /**
     * Status of the automatic reconnect loop. Starts at [ReconnectStatus.Idle],
     * transitions to [ReconnectStatus.Reconnecting] when a connection that had
     * reached CONNECTED drops unexpectedly, to [ReconnectStatus.Idle] on success,
     * or to [ReconnectStatus.Failed] when all retry attempts are exhausted.
     */
    val reconnectStatus: StateFlow<ReconnectStatus>

    fun connect(host: String, port: Int, useTls: Boolean, scope: CoroutineScope, path: String = "/game")
    suspend fun send(message: ClientMessage): Boolean
    fun disconnect()

    /**
     * Manually re-trigger reconnect after [ReconnectStatus.Failed]. No-op when
     * there is no saved connection to retry against.
     */
    fun retryReconnect()
}
