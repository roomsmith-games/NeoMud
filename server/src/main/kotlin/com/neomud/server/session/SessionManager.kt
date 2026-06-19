package com.neomud.server.session

import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.shared.model.Direction
import com.neomud.shared.model.PlayerInfo
import com.neomud.shared.model.RoomId
import com.neomud.shared.protocol.ServerMessage
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class SessionManager {
    private val logger = LoggerFactory.getLogger(SessionManager::class.java)
    private val sessions = ConcurrentHashMap<String, PlayerSession>()
    /** Maps login username (lowercase) → character name for duplicate login detection */
    private val usernameToCharacter = ConcurrentHashMap<String, String>()

    /**
     * Most recent moment at which this server saw meaningful client activity:
     * session add, session remove, or an inbound WebSocket frame. Read by the
     * platform orchestrator's idle reaper via `/internal/status` to decide
     * whether an ephemeral container is still in use.
     */
    private val lastActivityAt = AtomicReference<Instant>(Instant.now())

    /**
     * Record that client activity just happened. Called from the WebSocket
     * frame ingest loop on every Frame.Text, and internally on session
     * add/remove.
     */
    fun touchActivity() {
        lastActivityAt.set(Instant.now())
    }

    /** Most recent activity timestamp. Exposed for status reporting. */
    fun getLastActivityAt(): Instant = lastActivityAt.get()

    /** Number of currently tracked player sessions. */
    fun activeSessionCount(): Int = sessions.size

    /**
     * Adds a session for the given player. If [username] is provided, atomically checks
     * that the username is not already logged in. Returns false if the username is already
     * mapped to another session (duplicate login race).
     */
    fun addSession(playerName: String, session: PlayerSession, username: String? = null): Boolean {
        if (username != null) {
            val prev = usernameToCharacter.putIfAbsent(username.lowercase(), playerName)
            if (prev != null && prev != playerName) {
                return false // Another session already claimed this username
            }
        }
        sessions[playerName] = session
        touchActivity()
        return true
    }

    fun removeSession(playerName: String) {
        sessions.remove(playerName)
        // Clean up username mapping
        usernameToCharacter.entries.removeIf { it.value == playerName }
        touchActivity()
    }

    /**
     * Displace an existing session for the given login username. Sends a
     * [ServerMessage.SessionDisplaced] to the old session, closes its WebSocket,
     * removes it from tracking, and broadcasts a disconnect to the room.
     * The caller can then proceed with a fresh login on the new connection.
     */
    suspend fun displaceSession(username: String) {
        val characterName = usernameToCharacter[username.lowercase()] ?: return
        val oldSession = sessions[characterName] ?: return

        logger.info("Displacing session for $characterName (username=$username)")

        try {
            oldSession.send(ServerMessage.SessionDisplaced())
        } catch (_: Exception) {}

        val roomId = oldSession.currentRoomId

        // Remove from tracking before closing WebSocket so the finally block
        // in Routing.kt sees playerName is already gone and skips duplicate cleanup
        removeSession(characterName)

        if (roomId != null) {
            broadcastToRoom(roomId, ServerMessage.PlayerLeft(characterName, roomId, Direction.NORTH))
            broadcastToRoom(roomId, ServerMessage.SystemMessage("$characterName has disconnected."))
        }

        try {
            oldSession.transport.close("Session displaced by new login")
        } catch (_: Exception) {}
    }

    fun getSession(playerName: String): PlayerSession? = sessions[playerName]

    fun isLoggedIn(playerName: String): Boolean = sessions.containsKey(playerName)

    /** Check if a login username already has an active session */
    fun isUsernameLoggedIn(username: String): Boolean =
        usernameToCharacter.containsKey(username.lowercase())

    fun getSessionsInRoom(roomId: RoomId): List<PlayerSession> =
        sessions.values.filter { it.currentRoomId == roomId }

    fun getPlayerNamesInRoom(roomId: RoomId): List<String> =
        sessions.values
            .filter { it.currentRoomId == roomId && it.playerName != null }
            .map { it.playerName!! }

    fun getVisiblePlayerNamesInRoom(roomId: RoomId): List<String> =
        sessions.values
            .filter { it.currentRoomId == roomId && it.playerName != null && !it.isHidden }
            .map { it.playerName!! }

    fun getVisiblePlayerInfosInRoom(roomId: RoomId): List<PlayerInfo> =
        sessions.values
            .filter { it.currentRoomId == roomId && it.playerName != null && !it.isHidden }
            .mapNotNull { session ->
                val player = session.player ?: return@mapNotNull null
                PlayerInfo(
                    name = player.name,
                    characterClass = player.characterClass,
                    race = player.race,
                    gender = player.gender,
                    level = player.level,
                    spriteUrl = PlayerRepository.pcSpriteRelativePath(player.race, player.gender, player.characterClass)
                )
            }

    fun getAllAuthenticatedSessions(): List<PlayerSession> =
        sessions.values.filter { it.isAuthenticated }

    suspend fun broadcastToAll(message: ServerMessage) {
        sessions.values.forEach { session ->
            try {
                session.send(message)
            } catch (_: Exception) {
                // Session might be closing
            }
        }
    }

    suspend fun broadcastToRoom(roomId: RoomId, message: ServerMessage, exclude: String? = null) {
        sessions.values
            .filter { it.currentRoomId == roomId && it.playerName != exclude }
            .forEach { session ->
                try {
                    session.send(message)
                } catch (_: Exception) {
                    // Session might be closing
                }
            }
    }

    suspend fun broadcastToParty(memberNames: List<String>, message: ServerMessage, exclude: String? = null) {
        for (name in memberNames) {
            if (name == exclude) continue
            try {
                sessions[name]?.send(message)
            } catch (_: Exception) {}
        }
    }
}
