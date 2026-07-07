package com.neomud.server.telnet

import com.neomud.server.game.CommandProcessor
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.platform.PlatformApiClient
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.RaceCatalog
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

class TelnetServer(
    private val port: Int,
    private val commandProcessor: CommandProcessor,
    private val playerRepository: PlayerRepository,
    private val classCatalog: ClassCatalog,
    private val raceCatalog: RaceCatalog,
    private val worldName: String? = null,
    private val preAuthSecret: String? = null,
    private val platformApiClient: PlatformApiClient? = null,
    private val maxConnections: Int = MAX_CONNECTIONS_DEFAULT,
    private val maxPerIp: Int = MAX_PER_IP_DEFAULT,
) {
    private val logger = LoggerFactory.getLogger(TelnetServer::class.java)
    private val activeConnections = AtomicInteger(0)
    private val ipLimiter = IpConnectionLimiter(maxPerIp)

    suspend fun start() = coroutineScope {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val serverSocket = aSocket(selectorManager).tcp().bind("0.0.0.0", port)
        logger.info("Telnet server listening on port $port")

        while (true) {
            val socket = serverSocket.accept()
            val remoteIp = (socket.remoteAddress as? InetSocketAddress)?.hostname ?: "unknown"

            // Global cap: increment-then-check so two concurrent accepts can't both slip past.
            if (activeConnections.incrementAndGet() > maxConnections) {
                activeConnections.decrementAndGet()
                logger.warn("Telnet: connection limit reached, rejecting $remoteIp")
                try {
                    socket.openWriteChannel(autoFlush = true)
                        .writeStringUtf8("Server full. Try again later.\r\n")
                } catch (_: Exception) {}
                socket.close()
                continue
            }

            if (!ipLimiter.tryReserve(remoteIp)) {
                activeConnections.decrementAndGet()
                logger.warn("Telnet: per-IP limit reached for $remoteIp")
                try {
                    socket.openWriteChannel(autoFlush = true)
                        .writeStringUtf8("Too many connections from your address.\r\n")
                } catch (_: Exception) {}
                socket.close()
                continue
            }

            launch(Dispatchers.IO) {
                try {
                    TelnetConnectionHandler(
                        socket = socket,
                        commandProcessor = commandProcessor,
                        playerRepository = playerRepository,
                        classCatalog = classCatalog,
                        raceCatalog = raceCatalog,
                        worldName = worldName,
                        preAuthSecret = preAuthSecret,
                        platformApiClient = platformApiClient,
                    ).handle()
                } catch (e: Exception) {
                    logger.debug("Telnet handler error for $remoteIp: ${e.message}")
                } finally {
                    activeConnections.decrementAndGet()
                    ipLimiter.release(remoteIp)
                    try { socket.close() } catch (_: Exception) {}
                }
            }
        }
    }

    companion object {
        const val DEFAULT_PORT = 4000
        private const val MAX_CONNECTIONS_DEFAULT = 100
        private const val MAX_PER_IP_DEFAULT = 5
    }
}
