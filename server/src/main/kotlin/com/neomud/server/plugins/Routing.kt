package com.neomud.server.plugins

import com.neomud.server.game.CommandProcessor
import com.neomud.server.game.GameConfig
import com.neomud.server.persistence.repository.DiscoveryRepository
import com.neomud.server.persistence.repository.PlayerDiscoveryData
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.WorldDataSource
import com.neomud.server.world.WorldManifest
import com.neomud.shared.NeoMudVersion
import com.neomud.shared.protocol.MessageSerializer
import com.neomud.shared.protocol.ServerMessage
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val logger = LoggerFactory.getLogger("Routing")

/** Safely quote a string for JSON output. */
private fun quoteJson(s: String): String {
    val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    return "\"$escaped\""
}

/** Per-IP active WebSocket connection counter */
private val connectionsPerIp = ConcurrentHashMap<String, AtomicInteger>()

private val extensionToContentType = mapOf(
    "webp" to ContentType.Image.Any,
    "png" to ContentType.Image.PNG,
    "jpg" to ContentType.Image.JPEG,
    "jpeg" to ContentType.Image.JPEG,
    "ogg" to ContentType.parse("audio/ogg"),
    "mp3" to ContentType.parse("audio/mpeg"),
    "wav" to ContentType.parse("audio/wav"),
    "json" to ContentType.Application.Json
)

fun Application.configureRouting(
    sessionManager: SessionManager,
    commandProcessor: CommandProcessor,
    playerRepository: PlayerRepository,
    discoveryRepository: DiscoveryRepository,
    dataSource: WorldDataSource,
    worldManifest: WorldManifest? = null,
    partyService: com.neomud.server.game.party.PartyService? = null
) {
    routing {
        get("/assets/{path...}") {
            val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
            val fullPath = "assets/$path"

            // CORS for web client (in production, same-origin via Caddy — this is for local dev)
            call.response.header("Access-Control-Allow-Origin", "*")

            // Reject path traversal attempts
            if (".." in path) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val bytes = dataSource.readBytes(fullPath)
            if (bytes == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val ext = path.substringAfterLast('.', "").lowercase()
            val contentType = extensionToContentType[ext] ?: ContentType.Application.OctetStream

            // ETag based on content hash so clients refetch when assets change
            val etag = "\"${bytes.size}-${bytes.contentHashCode()}\""
            val ifNoneMatch = call.request.headers["If-None-Match"]
            if (ifNoneMatch == etag) {
                call.respond(HttpStatusCode.NotModified)
                return@get
            }
            call.response.header("ETag", etag)
            call.response.header("Cache-Control", "public, max-age=3600, stale-while-revalidate=86400")

            call.respondBytes(bytes, contentType)
        }

        get("/health") {
            call.respondText("OK", ContentType.Text.Plain)
        }

        // Internal status for the platform's idle reaper. Reports active
        // session count and last-activity timestamp. Deliberately no auth —
        // this endpoint is only reachable over the internal Docker network
        // (production) or localhost (dev), and leaks nothing secret.
        get("/internal/status") {
            val activeConnections = sessionManager.activeSessionCount()
            val lastActivityAt = sessionManager.getLastActivityAt().toString()
            val json = """{"activeConnections":$activeConnections,"lastActivityAt":${quoteJson(lastActivityAt)}}"""
            call.respondText(json, ContentType.Application.Json)
        }

        get("/api/character") {
            // Auth: verify shared secret from Platform
            val secret = call.request.headers["X-Platform-Secret"]
            val expected = System.getenv("PLATFORM_API_SECRET")
            if (expected.isNullOrBlank() || secret != expected) {
                call.respondText("""{"error":"Unauthorized"}""", ContentType.Application.Json, HttpStatusCode.Forbidden)
                return@get
            }

            val platformUserId = call.request.queryParameters["platformUserId"]
            if (platformUserId.isNullOrBlank()) {
                call.respondText("""{"error":"platformUserId required"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                return@get
            }

            val player = playerRepository.findByPlatformUserId(platformUserId)
            if (player == null) {
                call.respondText("""{"error":"No character found"}""", ContentType.Application.Json, HttpStatusCode.NotFound)
                return@get
            }

            val json = """{"name":${quoteJson(player.name)},"characterClass":${quoteJson(player.characterClass)},"race":${quoteJson(player.race)},"level":${player.level},"gender":${quoteJson(player.gender)}}"""
            call.respondText(json, ContentType.Application.Json)
        }

        webSocket("/game") {
            val remoteIp = call.request.origin.remoteHost
            val ipCounter = connectionsPerIp.computeIfAbsent(remoteIp) { AtomicInteger(0) }
            val currentCount = ipCounter.incrementAndGet()

            if (currentCount > GameConfig.Security.MAX_CONNECTIONS_PER_IP) {
                ipCounter.decrementAndGet()
                logger.warn("Connection limit exceeded for IP: $remoteIp ($currentCount connections)")
                close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Too many connections"))
                return@webSocket
            }

            val session = PlayerSession(this)
            session.remoteIp = remoteIp
            logger.info("New WebSocket connection from $remoteIp")

            try {
                // Send server hello with version info
                session.send(ServerMessage.ServerHello(
                    engineVersion = NeoMudVersion.ENGINE_VERSION,
                    protocolVersion = NeoMudVersion.PROTOCOL_VERSION,
                    worldName = worldManifest?.name ?: "",
                    worldVersion = worldManifest?.version ?: "",
                    minClientVersion = NeoMudVersion.MIN_CLIENT_VERSION
                ))

                // Send catalog data before auth so registration screen can populate
                commandProcessor.sendCatalogSync(session)

                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        sessionManager.touchActivity()
                        val text = frame.readText()
                        try {
                            val message = MessageSerializer.decodeClientMessage(text)
                            if (!session.tryConsumeMessage()) {
                                session.send(ServerMessage.Error("Too many commands, slow down."))
                                continue
                            }
                            commandProcessor.process(session, message)
                        } catch (e: Exception) {
                            logger.error("Failed to process message (${text.length} chars): ${e.message}")
                            session.send(ServerMessage.Error("Invalid message format"))
                        }
                    }
                }
            } catch (e: Exception) {
                logger.info("WebSocket error: ${e.message}")
            } finally {
                // Decrement IP connection counter
                val counter = connectionsPerIp[remoteIp]
                if (counter != null && counter.decrementAndGet() <= 0) {
                    connectionsPerIp.remove(remoteIp)
                }

                val playerName = session.playerName
                if (playerName != null && sessionManager.isLoggedIn(playerName)) {
                    val roomId = session.currentRoomId

                    if (session.isGuest) {
                        // Ephemeral guest — delete all data instead of saving
                        try {
                            playerRepository.deletePlayer(playerName)
                            logger.info("Deleted ephemeral guest data for $playerName")
                        } catch (e: Exception) {
                            logger.error("Failed to clean up guest data for $playerName: ${e.message}")
                        }
                    } else {
                        // Persistent player — save state
                        val player = session.player
                        if (player != null) {
                            try {
                                playerRepository.savePlayerState(player)
                            } catch (e: Exception) {
                                logger.error("Failed to save player state on disconnect: ${e.message}")
                            }
                            try {
                                discoveryRepository.savePlayerDiscovery(
                                    playerName,
                                    PlayerDiscoveryData(
                                        visitedRooms = session.visitedRooms.toSet(),
                                        discoveredHiddenExits = session.discoveredHiddenExits.toSet(),
                                        discoveredLockedExits = session.discoveredLockedExits.toSet(),
                                        discoveredInteractables = session.discoveredInteractables.toSet(),
                                        tutorials = session.seenTutorials.toSet()
                                    )
                                )
                            } catch (e: Exception) {
                                logger.error("Failed to save discovery data on disconnect: ${e.message}")
                            }
                        }
                    }

                    // Notify party members immediately; keep grace period for reconnect
                    val partyResult = partyService?.markDisconnected(playerName)
                    if (partyResult != null) {
                        when (partyResult) {
                            is com.neomud.server.game.party.PartyService.DisconnectResult.GracePeriod -> {
                                for (name in partyResult.otherMembers) {
                                    sessionManager.getSession(name)?.send(
                                        ServerMessage.PartyMemberLeft(playerName, "disconnected")
                                    )
                                }
                                if (partyResult.newLeader != null) {
                                    for (name in partyResult.otherMembers) {
                                        sessionManager.getSession(name)?.send(
                                            ServerMessage.PartyLeaderChanged(partyResult.newLeader, partyResult.newLeader)
                                        )
                                        sessionManager.getSession(name)?.send(
                                            ServerMessage.SystemMessage("${partyResult.newLeader} is now the party leader.")
                                        )
                                    }
                                }
                            }
                            is com.neomud.server.game.party.PartyService.DisconnectResult.Disbanded -> {
                                for (name in partyResult.otherMembers) {
                                    sessionManager.getSession(name)?.send(
                                        ServerMessage.PartyDisbanded("$playerName disconnected — party too small to continue.")
                                    )
                                }
                            }
                            is com.neomud.server.game.party.PartyService.DisconnectResult.NotInParty -> {}
                        }
                    }

                    sessionManager.removeSession(playerName)
                    if (roomId != null) {
                        sessionManager.broadcastToRoom(
                            roomId,
                            ServerMessage.PlayerLeft(playerName, roomId, com.neomud.shared.model.Direction.NORTH)
                        )
                        sessionManager.broadcastToRoom(
                            roomId,
                            ServerMessage.SystemMessage("$playerName has disconnected.")
                        )
                    }
                    logger.info("Player $playerName disconnected${if (session.isGuest) " (guest)" else ""}")
                }
            }
        }
    }
}
