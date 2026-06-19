package com.neomud.server.session

import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.websocket.*

class WebSocketTransport(private val ws: WebSocketSession) : TransportSession {
    override suspend fun sendMessage(message: ServerMessage) {
        ws.send(Frame.Text(MessageSerializer.encodeServerMessage(message)))
    }

    override suspend fun close(reason: String) {
        ws.close(CloseReason(CloseReason.Codes.NORMAL, reason))
    }
}
