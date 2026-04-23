package com.neomud.client.network

import com.neomud.client.platform.PlatformLogger
import com.neomud.client.platform.createPlatformHttpClient
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class WebSocketClient(
    private val reconnectCoordinator: ReconnectCoordinator = ReconnectCoordinator(
        maxAttempts = 30,
        intervalMs = 1000,
    ),
) : GameConnection {
    private val client = createPlatformHttpClient()

    private var session: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null

    private val _messages = MutableSharedFlow<ServerMessage>(extraBufferCapacity = 64)
    override val messages: SharedFlow<ServerMessage> = _messages

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _connectionError = MutableStateFlow<String?>(null)
    override val connectionError: StateFlow<String?> = _connectionError

    override val reconnectStatus: StateFlow<ReconnectStatus> = reconnectCoordinator.status

    /** Last-used connection params, retained so the reconnect loop can retry them. */
    private data class ConnectParams(val host: String, val port: Int, val useTls: Boolean, val scope: CoroutineScope, val path: String)
    private var lastParams: ConnectParams? = null

    /** True once a session has reached CONNECTED at least once since the last connect() call. */
    private var wasConnected: Boolean = false

    /** True when the user called disconnect(); disables auto-reconnect on close. */
    private var explicitDisconnect: Boolean = false

    override fun connect(host: String, port: Int, useTls: Boolean, scope: CoroutineScope, path: String) {
        connectionJob?.cancel()
        explicitDisconnect = false
        wasConnected = false
        lastParams = ConnectParams(host, port, useTls, scope, path)
        // Cancel any in-flight reconnect loop — this is a fresh connect
        // attempt initiated by the caller, not a recovery.
        reconnectCoordinator.cancel()
        connectionJob = scope.launch(Dispatchers.Default) {
            runConnection()
        }
    }

    private suspend fun runConnection() {
        _connectionState.value = ConnectionState.CONNECTING
        _connectionError.value = null
        val params = lastParams ?: return
        try {
            if (params.useTls) {
                client.wss(host = params.host, port = params.port, path = params.path) {
                    handleSession(this)
                }
            } else {
                client.webSocket(host = params.host, port = params.port, path = params.path) {
                    handleSession(this)
                }
            }
        } catch (_: CancellationException) {
            // Normal disconnection – not an error
        } catch (e: Exception) {
            _connectionError.value = e.message ?: "Connection failed"
        } finally {
            session = null
            _connectionState.value = ConnectionState.DISCONNECTED
            // Auto-reconnect only when this close was UNEXPECTED — the
            // session had reached CONNECTED and the user didn't click
            // disconnect. First-connect failures (wasConnected=false)
            // fall through to the normal error UI as before.
            if (wasConnected && !explicitDisconnect) {
                launchReconnect(params.scope)
            }
        }
    }

    private fun launchReconnect(scope: CoroutineScope) {
        reconnectCoordinator.launch(scope) {
            runConnection()
            // runConnection returns when the socket closes. Success = the
            // session reached CONNECTED at some point during this run,
            // which flips wasConnected. If it did, this attempt is done
            // (either the user will disconnect or another unexpected
            // close will kick off a fresh reconnect loop).
            val connected = wasConnected
            // Reset for the next attempt so we measure each one fresh.
            if (!connected) wasConnected = false
            connected
        }
    }

    private suspend fun handleSession(wsSession: DefaultClientWebSocketSession) {
        session = wsSession
        wasConnected = true
        _connectionState.value = ConnectionState.CONNECTED

        for (frame in wsSession.incoming) {
            if (frame is Frame.Text) {
                val text = frame.readText()
                try {
                    val message = MessageSerializer.decodeServerMessage(text)
                    _messages.emit(message)
                } catch (e: Exception) {
                    PlatformLogger.w("WebSocketClient", "Failed to decode message: ${text.take(200)}", e)
                }
            }
        }
    }

    override suspend fun send(message: ClientMessage): Boolean {
        val s = session ?: run {
            PlatformLogger.w("WebSocketClient", "send() called with no active session for ${message::class.simpleName}")
            return false
        }
        val text = MessageSerializer.encodeClientMessage(message)
        return try {
            s.send(Frame.Text(text))
            true
        } catch (e: Exception) {
            PlatformLogger.e("WebSocketClient", "send() failed for ${message::class.simpleName}", e)
            false
        }
    }

    override fun disconnect() {
        explicitDisconnect = true
        reconnectCoordinator.cancel()
        connectionJob?.cancel()
        connectionJob = null
    }

    override fun retryReconnect() {
        val params = lastParams ?: return
        // Treat the Retry button as "you were connected, try again" —
        // fire the reconnect loop fresh from attempt 1.
        wasConnected = true
        launchReconnect(params.scope)
    }
}
