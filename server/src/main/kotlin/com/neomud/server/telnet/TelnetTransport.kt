package com.neomud.server.telnet

import com.neomud.server.session.TransportSession
import com.neomud.shared.protocol.ServerMessage
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TelnetTransport(
    socket: Socket,
    val state: TelnetSessionState,
    private val renderer: TextRenderer,
) : TransportSession {

    private val outChannel = Channel<ServerMessage>(Channel.UNLIMITED)
    val writeChannel: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)
    val readChannel: ByteReadChannel = socket.openReadChannel()

    // Both the writer coroutine and the reader coroutine (telnet negotiation, login prompts) write
    // to the socket. Ktor's ByteWriteChannel is not safe for concurrent writers — an unguarded
    // overlap throws and kills the writer loop — so every write goes through this lock.
    private val writeLock = Mutex()

    private suspend fun writeBytes(bytes: ByteArray) = writeLock.withLock { writeChannel.writeFully(bytes) }
    private suspend fun writeText(text: String) = writeLock.withLock { writeChannel.writeStringUtf8(text) }

    /** Set before login; completed when LoginOk / AuthError / SessionConflict arrives. */
    @Volatile
    var loginDeferred: CompletableDeferred<ServerMessage>? = null

    // Out-of-band protocol negotiation. Set on the reader coroutine (handleNegotiation), read on
    // the writer coroutine — hence @Volatile. The *snapshot* is flushed from the writer loop (not
    // here) so it runs after the login burst has populated `state`, avoiding an empty snapshot.
    @Volatile var gmcpEnabled = false
    @Volatile var msdpEnabled = false
    @Volatile private var gmcpSnapshotPending = false
    @Volatile private var msdpSnapshotPending = false

    override suspend fun sendMessage(message: ServerMessage) {
        outChannel.send(message)
    }

    /** Answer `IAC DO GMCP`: turn on GMCP, send the handshake, and queue a state snapshot. */
    suspend fun enableGmcp() {
        gmcpEnabled = true
        for (frame in Gmcp.handshakeFrames()) writeBytes(frame)
        gmcpSnapshotPending = true
        outChannel.send(ServerMessage.Pong)  // wake the writer loop to flush the snapshot
    }

    /** Answer `IAC DO MSDP`: turn on MSDP and queue a state snapshot. */
    suspend fun enableMsdp() {
        msdpEnabled = true
        msdpSnapshotPending = true
        outChannel.send(ServerMessage.Pong)
    }

    override suspend fun close(reason: String) {
        outChannel.close()
    }

    /** Writes raw bytes directly to the socket (telnet negotiation frames, prompts). */
    suspend fun sendBytes(bytes: ByteArray) = writeBytes(bytes)

    /** Writes a raw string directly to the socket (welcome banner, prompts). */
    suspend fun sendRaw(text: String) = writeText(text)

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
                    writeText("$line\r\n")
                }
            }

            // Out-of-band protocol pushes are invisible to the terminal — emit after any
            // visible text but before re-showing the prompt.
            if (gmcpEnabled) {
                for (frame in Gmcp.framesFor(message, state)) writeBytes(frame)
            }
            if (msdpEnabled) {
                for (frame in Msdp.framesFor(message, state)) writeBytes(frame)
            }

            // A snapshot was requested (client negotiated GMCP/MSDP mid-stream). Flush it here,
            // on the writer coroutine, now that `state` reflects the login/room burst.
            if (gmcpEnabled && gmcpSnapshotPending && state.playerName != null) {
                for (frame in Gmcp.snapshotFrames(state)) writeBytes(frame)
                gmcpSnapshotPending = false
            }
            if (msdpEnabled && msdpSnapshotPending && state.playerName != null) {
                for (frame in Msdp.snapshotFrames(state)) writeBytes(frame)
                msdpSnapshotPending = false
            }

            if (lines.isNotEmpty()) redisplayPrompt()
        }
    }

    /** Overwrites the current input line before printing async output. */
    private suspend fun clearCurrentLine() {
        val spaces = " ".repeat(state.terminalWidth)
        writeText("\r$spaces\r")
    }

    /** Re-displays the prompt after async output so the player sees where to type. */
    internal suspend fun redisplayPrompt() {
        writeText(renderer.renderPrompt(state, useColor = true))
    }
}
