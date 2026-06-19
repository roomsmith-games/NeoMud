package com.neomud.server.session

import com.neomud.shared.protocol.ServerMessage

interface TransportSession {
    suspend fun sendMessage(message: ServerMessage)
    suspend fun close(reason: String = "")
}
