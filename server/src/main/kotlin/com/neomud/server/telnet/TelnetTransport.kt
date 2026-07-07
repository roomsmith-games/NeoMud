package com.neomud.server.telnet

import com.neomud.server.session.TransportSession
import com.neomud.shared.protocol.ServerMessage
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

class TelnetTransport(
    socket: Socket,
    val state: TelnetSessionState,
    private val renderer: TextRenderer,
) : TransportSession {

    private val outChannel = Channel<ServerMessage>(Channel.UNLIMITED)
    val writeChannel: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
    val readChannel: ByteReadChannel = socket.openReadChannel()

    /** Set before login; completed when LoginOk / AuthError / SessionConflict arrives. */
    @Volatile
    var loginDeferred: CompletableDeferred<ServerMessage>? = null

    override suspend fun sendMessage(message: ServerMessage) {
        outChannel.send(message)
    }

    override suspend fun close(reason: String) {
        outChannel.close()
    }

    /** Writes raw bytes directly to the socket (telnet negotiation frames, prompts). */
    suspend fun sendBytes(bytes: ByteArray) {
        writeChannel.writeFully(bytes)
    }

    /** Writes a raw string directly to the socket (welcome banner, prompts). */
    suspend fun sendRaw(text: String) {
        writeChannel.writeStringUtf8(text)
    }

    /**
     * Main writer coroutine. Reads ServerMessages from the channel, updates cached state,
     * renders them to text lines, and writes to the socket. Overwrites any partially-typed
     * input before async output to avoid visual corruption.
     */
    suspend fun runWriterLoop() {
        for (message in outChannel) {
            // Signal the login flow if it's waiting
            val deferred = loginDeferred
            if (deferred != null && !deferred.isCompleted) {
                if (message is ServerMessage.LoginOk ||
                    message is ServerMessage.PlatformAuthOk ||
                    message is ServerMessage.AuthError ||
                    message is ServerMessage.SessionConflict
                ) {
                    deferred.complete(message)
                }
            }

            state.update(message)
            val lines = renderer.render(message, state, useColor = true)
            if (lines.isNotEmpty()) {
                clearCurrentLine()
                for (line in lines) {
                    writeChannel.writeStringUtf8("$line\r\n")
                }
            }

            // Out-of-band protocol pushes are invisible to the terminal — emit after any
            // visible text but before re-showing the prompt.
            if (state.gmcpEnabled) {
                for (frame in Gmcp.framesFor(message, state)) writeChannel.writeFully(frame)
            }
            if (state.msdpEnabled) {
                for (frame in Msdp.framesFor(message, state)) writeChannel.writeFully(frame)
            }

            if (lines.isNotEmpty()) redisplayPrompt()
        }
    }

    /** Overwrites the current input line before printing async output. */
    private suspend fun clearCurrentLine() {
        val spaces = " ".repeat(state.terminalWidth)
        writeChannel.writeStringUtf8("\r$spaces\r")
    }

    /** Re-displays the prompt after async output so the player sees where to type. */
    internal suspend fun redisplayPrompt() {
        writeChannel.writeStringUtf8(renderer.renderPrompt(state, useColor = true))
    }
}
