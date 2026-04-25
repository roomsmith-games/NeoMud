package com.neomud.server

import com.neomud.server.game.CommandProcessor
import com.neomud.server.game.GameLoop
import com.neomud.server.game.MovementTrailManager
import com.neomud.server.game.TutorialService
import com.neomud.server.game.combat.CombatManager
import com.neomud.server.game.commands.InventoryCommand
import com.neomud.server.game.commands.PickupCommand
import com.neomud.server.game.commands.CraftCommand
import com.neomud.server.game.commands.SpellCommand
import com.neomud.server.game.commands.TrainerCommand
import com.neomud.server.game.commands.VendorCommand
import com.neomud.server.game.crafting.CraftingService
import com.neomud.server.game.inventory.EquipmentService
import com.neomud.server.game.inventory.LootService
import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.persistence.DatabaseFactory
import com.neomud.server.persistence.repository.CoinRepository
import com.neomud.server.persistence.repository.DiscoveryRepository
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.plugins.configureForwardedHeaders
import com.neomud.server.plugins.configureRouting
import com.neomud.server.plugins.configureWebSockets
import com.neomud.server.session.SessionManager
import com.neomud.server.world.NmdBundleDataSource
import com.neomud.server.world.WorldDataSource
import com.neomud.server.world.WorldLoader
import io.ktor.server.application.*
import com.neomud.shared.NeoMudVersion
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.File
import java.net.BindException
import java.net.ServerSocket
import java.util.zip.ZipFile
import com.neomud.server.game.GameConfig
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("NeoMud")

/** Parsed CLI configuration, with environment variable fallbacks. */
data class ServerConfig(
    val port: Int = System.getenv("NEOMUD_PORT")?.toIntOrNull() ?: GameConfig.Server.PORT,
    val worldFile: String = System.getenv("NEOMUD_WORLD") ?: "",
    val dbPath: String = System.getenv("NEOMUD_DB") ?: "neomud.db",
    val admins: Set<String> = System.getenv("NEOMUD_ADMINS")
        ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet()
        ?: emptySet()
)

fun parseArgs(args: Array<String>): ServerConfig {
    var port: Int? = null
    var worldFile: String? = null
    var dbPath: String? = null
    var admins: Set<String>? = null

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--help", "-h" -> {
                printHelp()
                exitProcess(0)
            }
            "--port", "-p" -> {
                i++
                port = args.getOrNull(i)?.toIntOrNull()
                if (port == null) {
                    System.err.println("Error: --port requires a numeric value")
                    exitProcess(1)
                }
            }
            "--world", "-w" -> {
                i++
                worldFile = args.getOrNull(i)
                if (worldFile == null) {
                    System.err.println("Error: --world requires a file path")
                    exitProcess(1)
                }
            }
            "--db" -> {
                i++
                dbPath = args.getOrNull(i)
                if (dbPath == null) {
                    System.err.println("Error: --db requires a file path")
                    exitProcess(1)
                }
            }
            "--admins" -> {
                i++
                val value = args.getOrNull(i)
                if (value == null) {
                    System.err.println("Error: --admins requires a comma-separated list of usernames")
                    exitProcess(1)
                }
                admins = value.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            }
            else -> {
                System.err.println("Unknown argument: ${args[i]}")
                printHelp()
                exitProcess(1)
            }
        }
        i++
    }

    val base = ServerConfig()
    return base.copy(
        port = port ?: base.port,
        worldFile = worldFile ?: base.worldFile,
        dbPath = dbPath ?: base.dbPath,
        admins = admins ?: base.admins
    )
}

fun printHelp() {
    println("""
${NeoMudVersion.DISPLAY}

Usage: java -jar neomud-server.jar [options]

Options:
  --port, -p <port>       Server port (default: ${GameConfig.Server.PORT}, env: NEOMUD_PORT)
  --world, -w <path>      World bundle .nmd file (default: bundled world, env: NEOMUD_WORLD)
  --db <path>             SQLite database path (default: neomud.db, env: NEOMUD_DB)
  --admins <users>        Comma-separated admin usernames (env: NEOMUD_ADMINS)
  --help, -h              Show this help message

Examples:
  java -jar neomud-server.jar
  java -jar neomud-server.jar --port 9090 --admins alice,bob
  java -jar neomud-server.jar --world my-world.nmd --db /data/neomud.db
    """.trimIndent())
}

/**
 * Resolve the world file path. Priority:
 * 1. Explicit path from CLI/env (if non-empty and file exists)
 * 2. "build/worlds/default-world.nmd" (dev mode, Gradle run)
 * 3. Bundled classpath resource extracted to temp file (fat JAR distribution)
 */
fun resolveWorldFile(configPath: String): File {
    // Explicit path provided
    if (configPath.isNotEmpty()) {
        val f = File(configPath)
        if (f.exists()) return f
        logger.warn("Specified world file not found: $configPath — trying defaults")
    }

    // Dev mode: Gradle build output
    val devFile = File("build/worlds/default-world.nmd")
    if (devFile.exists()) return devFile

    // Fat JAR: extract bundled resource to temp file
    val resource = Thread.currentThread().contextClassLoader.getResourceAsStream("bundled/default-world.nmd")
    if (resource != null) {
        val tempFile = File.createTempFile("neomud-world-", ".nmd")
        tempFile.deleteOnExit()
        resource.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        logger.info("Extracted bundled world to: ${tempFile.absolutePath}")
        return tempFile
    }

    // Nothing found
    System.err.println("""
World bundle not found. Provide one via:
  --world <path>     CLI argument
  NEOMUD_WORLD       Environment variable

Or run from the project directory after: ./gradlew packageWorld
    """.trimIndent())
    exitProcess(1)
}

fun printBanner(config: ServerConfig, worldFile: File) {
    val banner = """
    _   _            __  __           _
   | \ | | ___  ___ |  \/  |_   _  __| |
   |  \| |/ _ \/ _ \| |\/| | | | |/ _` |
   | |\  |  __/ (_) | |  | | |_| | (_| |
   |_| \_|\___|\___/|_|  |_|\__,_|\__,_|
    """.trimIndent()
    println(banner)
    println()
    logger.info("${NeoMudVersion.DISPLAY}")
    logger.info("WebSocket:  ws://0.0.0.0:${config.port}/game")
    logger.info("Health:     http://0.0.0.0:${config.port}/health")
    logger.info("World:      ${worldFile.name} (${worldFile.length() / 1024}KB)")
    logger.info("Database:   ${config.dbPath}")
    if (config.admins.isNotEmpty()) {
        logger.info("Admins:     ${config.admins.joinToString(", ")}")
    }
    println()
}

fun checkPortAvailable(port: Int): Boolean {
    return try {
        ServerSocket(port).use { true }
    } catch (e: BindException) {
        false
    }
}

fun main(args: Array<String>) {
    // Initialize Sentry error tracking
    val sentryDsn = System.getenv("SENTRY_DSN")
    if (!sentryDsn.isNullOrBlank()) {
        io.sentry.Sentry.init { options ->
            options.dsn = sentryDsn
            options.environment = System.getenv("NODE_ENV") ?: "development"
            options.tracesSampleRate = 0.2
        }
        logger.info("Sentry error tracking initialized")
    }

    val config = parseArgs(args)

    if (!checkPortAvailable(config.port)) {
        logger.error("Port ${config.port} is already in use. Is another NeoMud server running? Exiting.")
        exitProcess(1)
    }

    val worldFile = resolveWorldFile(config.worldFile)
    printBanner(config, worldFile)

    val server = embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(
            jdbcUrl = "jdbc:sqlite:${config.dbPath}",
            worldFile = worldFile.absolutePath,
            adminUsernamesOverride = config.admins.ifEmpty { null }
        )
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown signal received. Stopping server...")
        server.stop(gracePeriodMillis = 1000, timeoutMillis = 3000)
        logger.info("Server stopped.")
    })

    server.start(wait = true)
}

fun Application.module(jdbcUrl: String = "jdbc:sqlite:neomud.db", worldFile: String = "build/worlds/default-world.nmd", adminUsernamesOverride: Set<String>? = null, platformVerifierOverride: com.neomud.server.auth.PlatformTokenVerifier? = null) {
    // Initialize database
    DatabaseFactory.init(jdbcUrl)

    // Load world from .nmd bundle
    val file = File(worldFile)
    if (!file.exists()) {
        error("World bundle not found at $worldFile. Run './gradlew packageWorld' or set NEOMUD_WORLD to a valid .nmd file.")
    }
    logger.info("Loading world from bundle: $worldFile")
    val dataSource: WorldDataSource = NmdBundleDataSource(ZipFile(file))

    // Load world. WorldLoader hard-throws on common bundle gaps (no
    // spawnRoom, malformed zone JSON, etc.). When this happens inside the
    // Ktor application module, the exception is swallowed by Ktor's
    // startup machinery — the only visible log is the JVM shutdown hook
    // firing "Shutdown signal received". That's useless for diagnosing a
    // bad bundle. Catch, log with stack, rethrow so Ktor still aborts.
    val loadResult = try {
        WorldLoader.load(dataSource)
    } catch (e: Throwable) {
        logger.error("World load failed for bundle $worldFile: ${e.message}", e)
        throw e
    }
    val worldGraph = loadResult.worldGraph
    val classCatalog = loadResult.classCatalog
    val itemCatalog = loadResult.itemCatalog
    val lootTableCatalog = loadResult.lootTableCatalog
    val skillCatalog = loadResult.skillCatalog
    val raceCatalog = loadResult.raceCatalog
    val spellCatalog = loadResult.spellCatalog
    val pcSpriteCatalog = loadResult.pcSpriteCatalog
    val recipeCatalog = loadResult.recipeCatalog
    val npcManager = NpcManager(worldGraph, loadResult.zoneSpawnConfigs, loadResult.roomMaxHostileNpcs)
    npcManager.loadNpcs(loadResult.npcDataList)

    // Clean up orphaned ephemeral guest players from unclean shutdowns
    val playerRepository = PlayerRepository()
    val cleanedGuests = playerRepository.deleteEphemeralPlayers()
    if (cleanedGuests > 0) {
        logger.info("Cleaned up $cleanedGuests orphaned ephemeral guest player(s)")
    }

    // Create dependencies
    val sessionManager = SessionManager()
    val inventoryRepository = InventoryRepository(itemCatalog)
    val coinRepository = CoinRepository()
    val discoveryRepository = DiscoveryRepository()
    val lootService = LootService(itemCatalog)
    val equipmentService = EquipmentService(inventoryRepository, itemCatalog)
    val roomItemManager = RoomItemManager()
    val inventoryCommand = InventoryCommand(inventoryRepository, itemCatalog, coinRepository, worldGraph, sessionManager)
    val movementTrailManager = MovementTrailManager()
    val spellCommand = SpellCommand(spellCatalog, classCatalog, npcManager, sessionManager, playerRepository)
    val combatManager = CombatManager(npcManager, sessionManager, worldGraph, equipmentService, skillCatalog, spellCommand, spellCatalog, movementTrailManager)
    val tutorialService = TutorialService(discoveryRepository, classCatalog)
    val pickupCommand = PickupCommand(roomItemManager, inventoryRepository, coinRepository, itemCatalog, sessionManager, tutorialService)
    val trainerCommand = TrainerCommand(classCatalog, raceCatalog, playerRepository, sessionManager, npcManager, tutorialService)
    val vendorCommand = VendorCommand(npcManager, itemCatalog, inventoryRepository, coinRepository, inventoryCommand, sessionManager, skillCatalog)
    val craftingService = CraftingService(recipeCatalog, itemCatalog, inventoryRepository, coinRepository)
    val craftCommand = CraftCommand(npcManager, craftingService, recipeCatalog, inventoryCommand, sessionManager, inventoryRepository, coinRepository)
    val adminUsernames = adminUsernamesOverride ?: (System.getenv("NEOMUD_ADMINS")
        ?.split(",")
        ?.map { it.trim().lowercase() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        ?: emptySet())
    if (adminUsernames.isNotEmpty()) {
        logger.info("Admin usernames: $adminUsernames")
    }

    val platformVerifier = platformVerifierOverride ?: com.neomud.server.auth.PlatformTokenVerifier(
        jwksUrl = System.getenv("PLATFORM_JWKS_URL"),
        devSecret = System.getenv("PLATFORM_JWT_SECRET")
    )
    if (platformVerifier.isEnabled) {
        logger.info("Platform JWT verification enabled")
    }

    val commandProcessor = CommandProcessor(
        worldGraph, sessionManager, npcManager, playerRepository,
        classCatalog, itemCatalog, skillCatalog, raceCatalog, inventoryCommand, pickupCommand, roomItemManager,
        trainerCommand, spellCommand, spellCatalog, vendorCommand, lootService, lootTableCatalog,
        inventoryRepository, coinRepository, discoveryRepository, craftCommand, adminUsernames, movementTrailManager,
        pcSpriteCatalog, tutorialService, platformVerifier
    )
    val gameLoop = GameLoop(sessionManager, npcManager, combatManager, worldGraph, lootService, lootTableCatalog, roomItemManager, playerRepository, skillCatalog, classCatalog, itemCatalog, inventoryRepository, coinRepository, movementTrailManager, spellCommand, spellCatalog, tutorialService)
    commandProcessor.setGameLoop(gameLoop)

    // Save players and close resources when the application stops
    monitor.subscribe(ApplicationStopped) {
        logger.info("Saving player states...")
        for (session in sessionManager.getAllAuthenticatedSessions()) {
            val playerName = session.playerName ?: continue
            try {
                session.player?.let { playerRepository.savePlayerState(it) }
            } catch (e: Exception) {
                logger.error("Failed to save player $playerName: ${e.message}")
            }
            try {
                discoveryRepository.savePlayerDiscovery(
                    playerName,
                    com.neomud.server.persistence.repository.PlayerDiscoveryData(
                        visitedRooms = session.visitedRooms.toSet(),
                        discoveredHiddenExits = session.discoveredHiddenExits.toSet(),
                        discoveredLockedExits = session.discoveredLockedExits.toSet(),
                        discoveredInteractables = session.discoveredInteractables.toSet(),
                        tutorials = session.seenTutorials.toSet()
                    )
                )
            } catch (e: Exception) {
                logger.error("Failed to save discovery data for $playerName: ${e.message}")
            }
        }
        logger.info("Closing world bundle...")
        dataSource.close()
    }

    // Install plugins
    configureForwardedHeaders()
    configureWebSockets()
    configureRouting(sessionManager, commandProcessor, playerRepository, discoveryRepository, dataSource, loadResult.manifest)

    // Launch game loop
    launch {
        gameLoop.run()
        logger.info("Game loop ended.")
    }
}
