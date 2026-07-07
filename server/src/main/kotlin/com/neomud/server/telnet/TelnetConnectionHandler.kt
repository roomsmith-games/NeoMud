package com.neomud.server.telnet

import com.neomud.server.game.CommandProcessor
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.platform.PlatformApiClient
import com.neomud.server.platform.PlatformCredResult
import com.neomud.server.session.PlayerSession
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.RaceCatalog
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.ServerMessage
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

class TelnetConnectionHandler(
    private val socket: Socket,
    private val commandProcessor: CommandProcessor,
    private val playerRepository: PlayerRepository,
    private val classCatalog: ClassCatalog,
    private val raceCatalog: RaceCatalog,
    private val worldName: String? = null,
    private val preAuthSecret: String? = null,
    private val platformApiClient: PlatformApiClient? = null,
) {
    private val logger = LoggerFactory.getLogger(TelnetConnectionHandler::class.java)

    private val state = TelnetSessionState()
    private val renderer = TextRenderer()
    private val parser = CommandParser()
    private val transport = TelnetTransport(socket, state, renderer)

    // Byte read ahead of IacLineReader during pre-auth detection; injected as initialByte.
    private var injectedByte: Byte? = null

    private val iacReader by lazy {
        IacLineReader(
            channel = transport.readChannel,
            initialByte = injectedByte,
            onNaws = { width -> state.terminalWidth = width },
            onNegotiation = { cmd, opt -> handleNegotiation(cmd, opt) },
        )
    }

    suspend fun handle() = coroutineScope {
        val remoteAddr = socket.remoteAddress.toString()
        logger.info("Telnet connection from $remoteAddr")

        val writerJob = launch { transport.runWriterLoop() }
        try {
            // Detect pre-auth header before sending any bytes (100ms window).
            // Router connections send \x00NEOMUD:... immediately; direct clients don't.
            val preAuth = detectPreAuth()

            // IAC negotiation and banner are sent after detection in both paths.
            sendNeg(Telnet.DO, Telnet.SGA)
            sendNeg(Telnet.WILL, Telnet.SGA)
            sendNeg(Telnet.WILL, Telnet.ECHO)
            sendNeg(Telnet.DO, Telnet.NAWS)
            transport.sendRaw(welcomeBanner())

            val session = if (preAuth != null) {
                runPostAuthFlow(preAuth)
            } else {
                runLoginFlow()
            }

            if (session == null) {
                transport.sendRaw("Disconnecting.\r\n")
                return@coroutineScope
            }

            transport.redisplayPrompt()
            runCommandLoop(session)
        } catch (e: Exception) {
            logger.debug("Telnet connection $remoteAddr closed: ${e.message}")
        } finally {
            writerJob.cancel()
            socket.close()
            logger.info("Telnet disconnected: $remoteAddr")
        }
    }

    // ─── Pre-auth header detection ────────────────────────────────────────────

    private suspend fun detectPreAuth(): PreAuthResult? {
        val secret = preAuthSecret ?: return null
        return withTimeoutOrNull(100L) {
            val firstByte = try {
                transport.readChannel.readByte()
            } catch (_: Exception) {
                return@withTimeoutOrNull null
            }
            if (firstByte != 0x00.toByte()) {
                // Not a router connection — buffer the byte for IacLineReader
                injectedByte = firstByte
                return@withTimeoutOrNull null
            }
            // Read rest of header line (raw ASCII, no IAC processing needed)
            val sb = StringBuilder()
            while (true) {
                val b = try { transport.readChannel.readByte() } catch (_: Exception) { break }
                if (b == '\n'.code.toByte()) break
                if (b != '\r'.code.toByte()) sb.append(b.toInt().toChar())
            }
            PreAuthHeader.verify(sb.toString(), secret)
        }
    }

    // ─── Post-auth flow (router path) ─────────────────────────────────────────

    private suspend fun runPostAuthFlow(preAuth: PreAuthResult): PlayerSession? {
        val session = PlayerSession(transport)
        session.platformUserId = preAuth.userId
        if (preAuth.type == "guest") session.platformRole = "GUEST"

        return when (preAuth.type) {
            "guest" -> runGuestFlow(session)
            "user" -> runPlatformUserFlow(session, preAuth.userId, preAuth.characterName)
            else -> null
        }
    }

    private suspend fun runGuestFlow(session: PlayerSession): PlayerSession? {
        transport.sendRaw(
            "\r\n  World: ${worldName ?: "NeoMud"}\r\n\r\n" +
            "Playing as guest. Progress won't be saved.\r\n" +
            "Type 'register' to create an account at neomud.app.\r\n\r\n"
        )
        val guestName = "Guest_${randomHex(8)}"
        val firstClass = classCatalog.getAllClasses().firstOrNull()?.id ?: return null
        val firstRace = raceCatalog.getAllRaces().firstOrNull()?.id ?: return null

        val deferred = CompletableDeferred<ServerMessage>()
        transport.loginDeferred = deferred
        commandProcessor.process(session, ClientMessage.GuestLogin(
            characterName = guestName,
            characterClass = firstClass,
            race = firstRace,
            gender = "neutral",
        ))
        return when (deferred.await()) {
            is ServerMessage.LoginOk -> { transport.loginDeferred = null; session }
            else -> { transport.loginDeferred = null; null }
        }
    }

    private suspend fun runPlatformUserFlow(
        session: PlayerSession,
        userId: String,
        preSelectedCharacter: String? = null,
    ): PlayerSession? {
        // Router pre-selected the character — go straight to login, no prompts.
        if (preSelectedCharacter != null) {
            transport.sendRaw("\r\n  World: ${worldName ?: "NeoMud"}\r\n\r\nLogging in as $preSelectedCharacter...\r\n")
            val deferred = CompletableDeferred<ServerMessage>()
            transport.loginDeferred = deferred
            commandProcessor.process(session, ClientMessage.PlatformLogin(characterName = preSelectedCharacter))
            return when (val result = deferred.await()) {
                is ServerMessage.LoginOk -> { transport.loginDeferred = null; session }
                is ServerMessage.SessionConflict -> {
                    transport.loginDeferred = null
                    transport.sendRaw("\r\n$preSelectedCharacter is already logged in. Kick? (y/n): ")
                    val answer = readLine()?.trim()?.lowercase()
                    if (answer == "y" || answer == "yes") {
                        val forceDeferred = CompletableDeferred<ServerMessage>()
                        transport.loginDeferred = forceDeferred
                        commandProcessor.process(session, ClientMessage.PlatformLogin(characterName = preSelectedCharacter, force = true))
                        val forceResult = forceDeferred.await()
                        transport.loginDeferred = null
                        if (forceResult is ServerMessage.LoginOk) session else null
                    } else null
                }
                else -> { transport.loginDeferred = null; null }
            }
        }

        val characters = playerRepository.findAllByPlatformUserId(userId)
        return when {
            characters.isEmpty() -> runCharacterCreation(session)
            characters.size == 1 -> {
                transport.sendRaw("\r\n  World: ${worldName ?: "NeoMud"}\r\n\r\nLogging in as ${characters[0].name}...\r\n")
                val deferred = CompletableDeferred<ServerMessage>()
                transport.loginDeferred = deferred
                commandProcessor.process(session, ClientMessage.PlatformLogin())
                return when (deferred.await()) {
                    is ServerMessage.LoginOk -> { transport.loginDeferred = null; session }
                    else -> { transport.loginDeferred = null; null }
                }
            }
            else -> runCharacterSelectLoop(session, characters)
        }
    }

    // ─── Character creation wizard ────────────────────────────────────────────

    private suspend fun runCharacterCreation(session: PlayerSession): PlayerSession? {
        transport.sendRaw("\r\n  World: ${worldName ?: "NeoMud"}\r\n\r\nWelcome! Let's create your character.\r\n\r\n")

        // Name
        var characterName: String
        while (true) {
            transport.sendRaw("Character name: ")
            val input = readLine()?.trim() ?: return null
            if (Regex("^[a-zA-Z][a-zA-Z0-9_ ]{1,19}$").matches(input)) {
                characterName = input
                break
            }
            transport.sendRaw("\r\nName must be 2-20 characters, start with a letter, contain only letters, numbers, spaces, or underscores.\r\n\r\n")
        }

        // Class
        val classes = classCatalog.getAllClasses()
        transport.sendRaw("\r\n  Choose your class:\r\n\r\n")
        classes.forEachIndexed { i, cls ->
            transport.sendRaw("    ${i + 1}. ${cls.name.padEnd(14)} ${cls.description}\r\n")
        }
        var className: String
        while (true) {
            transport.sendRaw("\r\nClass (1-${classes.size}): ")
            val input = readLine()?.trim() ?: return null
            val idx = (input.toIntOrNull() ?: 0) - 1
            if (idx in classes.indices) { className = classes[idx].id; break }
            transport.sendRaw("Invalid choice. ")
        }

        // Race
        val races = raceCatalog.getAllRaces()
        transport.sendRaw("\r\n  Choose your race:\r\n\r\n")
        races.forEachIndexed { i, race ->
            transport.sendRaw("    ${i + 1}. ${race.name.padEnd(14)} ${race.description}\r\n")
        }
        var raceName: String
        while (true) {
            transport.sendRaw("\r\nRace (1-${races.size}): ")
            val input = readLine()?.trim() ?: return null
            val idx = (input.toIntOrNull() ?: 0) - 1
            if (idx in races.indices) { raceName = races[idx].id; break }
            transport.sendRaw("Invalid choice. ")
        }

        // Gender
        val genders = listOf("Male" to "male", "Female" to "female", "Non-binary" to "non-binary")
        transport.sendRaw("\r\n  Choose your gender:\r\n\r\n")
        genders.forEachIndexed { i, (label, _) -> transport.sendRaw("    ${i + 1}. $label\r\n") }
        var gender: String
        while (true) {
            transport.sendRaw("\r\nGender (1-${genders.size}): ")
            val input = readLine()?.trim() ?: return null
            val idx = (input.toIntOrNull() ?: 0) - 1
            if (idx in genders.indices) { gender = genders[idx].second; break }
            transport.sendRaw("Invalid choice. ")
        }

        transport.sendRaw("\r\nCreating $characterName...\r\n\r\n")

        val deferred = CompletableDeferred<ServerMessage>()
        transport.loginDeferred = deferred
        commandProcessor.process(session, ClientMessage.PlatformRegister(
            characterName = characterName,
            characterClass = className,
            race = raceName,
            gender = gender,
        ))
        return when (val result = deferred.await()) {
            is ServerMessage.LoginOk -> { transport.loginDeferred = null; session }
            is ServerMessage.AuthError -> {
                transport.loginDeferred = null
                transport.sendRaw("\r\nError: ${result.reason}\r\n\r\n")
                runCharacterCreation(session)  // re-prompt from name step on error (e.g. name taken)
            }
            else -> { transport.loginDeferred = null; null }
        }
    }

    // ─── Character select loop ────────────────────────────────────────────────

    private suspend fun runCharacterSelectLoop(
        session: PlayerSession,
        characters: List<com.neomud.shared.model.Player>,
    ): PlayerSession? {
        transport.sendRaw("\r\n  World: ${worldName ?: "NeoMud"}\r\n\r\nYour characters:\r\n")
        characters.forEachIndexed { i, c -> transport.sendRaw("  ${i + 1}. ${c.name}\r\n") }

        while (true) {
            transport.sendRaw("\r\nSelect a character (1-${characters.size} or name): ")
            val input = readLine()?.trim() ?: return null

            val selected = input.toIntOrNull()?.let { characters.getOrNull(it - 1) }
                ?: characters.firstOrNull { it.name.equals(input, ignoreCase = true) }

            if (selected == null) {
                transport.sendRaw("Invalid choice. Try again.\r\n")
                continue
            }

            val deferred = CompletableDeferred<ServerMessage>()
            transport.loginDeferred = deferred
            commandProcessor.process(session, ClientMessage.PlatformLogin(characterName = selected.name))
            val result = deferred.await()
            transport.loginDeferred = null

            when (result) {
                is ServerMessage.LoginOk -> return session
                is ServerMessage.SessionConflict -> {
                    transport.sendRaw("\r\n${selected.name} is already logged in. Kick? (y/n): ")
                    val answer = readLine()?.trim()?.lowercase()
                    if (answer == "y" || answer == "yes") {
                        val forceDeferred = CompletableDeferred<ServerMessage>()
                        transport.loginDeferred = forceDeferred
                        commandProcessor.process(session, ClientMessage.PlatformLogin(characterName = selected.name, force = true))
                        val forceResult = forceDeferred.await()
                        transport.loginDeferred = null
                        if (forceResult is ServerMessage.LoginOk) return session
                    }
                    // On 'n' or failed force: re-show menu
                }
                is ServerMessage.AuthError -> transport.sendRaw("\r\nError: ${result.reason}\r\n")
                else -> return null
            }
        }
    }

    // ─── Legacy / direct-connection login flow ────────────────────────────────

    private suspend fun runLoginFlow(): PlayerSession? {
        val client = platformApiClient
        return if (client != null) runPlatformDirectLogin(client) else runLegacyLogin()
    }

    private suspend fun runPlatformDirectLogin(client: PlatformApiClient): PlayerSession? {
        transport.sendRaw("Username or email (Enter for guest): ")
        val input = readLine()?.trim() ?: return null

        val session = PlayerSession(transport)

        if (input.isEmpty()) {
            transport.sendRaw("\r\n")
            return when (val r = client.createGuestSession()) {
                is PlatformCredResult.Success -> {
                    session.platformUserId = r.userId
                    session.platformRole = "GUEST"
                    runGuestFlow(session)
                }
                else -> {
                    transport.sendRaw("Error: Authentication service unavailable. Please try again in a moment.\r\n")
                    null
                }
            }
        }

        if (input.contains('@')) {
            // Email flow
            transport.sendRaw("\r\nPassword: ")
            sendNeg(Telnet.WONT, Telnet.ECHO)
            val password = readLine() ?: return null
            sendNeg(Telnet.WILL, Telnet.ECHO)
            transport.sendRaw("\r\nAuthenticating...\r\n")

            var attemptsLeft = MAX_LOGIN_ATTEMPTS
            var currentPassword = password
            while (attemptsLeft > 0) {
                when (val r = client.verifyCredentials(input, currentPassword)) {
                    is PlatformCredResult.Success -> {
                        session.platformUserId = r.userId
                        return runPlatformUserFlow(session, r.userId)
                    }
                    is PlatformCredResult.AuthFailure -> {
                        attemptsLeft--
                        if (attemptsLeft <= 0 || r.code != "INVALID_CREDENTIALS") {
                            transport.sendRaw("Error: ${r.message}\r\nDisconnecting.\r\n")
                            return null
                        }
                        transport.sendRaw("Error: ${r.message} ($attemptsLeft attempt${if (attemptsLeft == 1) "" else "s"} remain)\r\nPassword: ")
                        sendNeg(Telnet.WONT, Telnet.ECHO)
                        currentPassword = readLine() ?: return null
                        sendNeg(Telnet.WILL, Telnet.ECHO)
                        transport.sendRaw("\r\nAuthenticating...\r\n")
                    }
                    is PlatformCredResult.ServiceError -> {
                        transport.sendRaw("Error: ${r.message}\r\nDisconnecting.\r\n")
                        return null
                    }
                }
            }
            return null
        }

        // Not an email — fall through to legacy character-name auth
        transport.sendRaw("\r\nPassword: ")
        sendNeg(Telnet.WONT, Telnet.ECHO)
        val password = readLine() ?: return null
        sendNeg(Telnet.WILL, Telnet.ECHO)
        transport.sendRaw("\r\n")
        return runLegacyLoginAttempts(session, input, password)
    }

    private suspend fun runLegacyLogin(): PlayerSession? {
        transport.sendRaw("Username: ")
        val username = readLine() ?: return null
        if (username.isBlank()) return null

        transport.sendRaw("\r\nPassword: ")
        sendNeg(Telnet.WONT, Telnet.ECHO)
        val password = readLine() ?: return null
        sendNeg(Telnet.WILL, Telnet.ECHO)
        transport.sendRaw("\r\n")

        val session = PlayerSession(transport)
        return runLegacyLoginAttempts(session, username, password)
    }

    private suspend fun runLegacyLoginAttempts(
        session: PlayerSession,
        username: String,
        firstPassword: String,
    ): PlayerSession? {
        repeat(MAX_LOGIN_ATTEMPTS) { attempt ->
            val pwd = if (attempt == 0) firstPassword else {
                transport.sendRaw("Password: ")
                sendNeg(Telnet.WONT, Telnet.ECHO)
                val p = readLine() ?: return null
                sendNeg(Telnet.WILL, Telnet.ECHO)
                transport.sendRaw("\r\n")
                p
            }

            val deferred = CompletableDeferred<ServerMessage>()
            transport.loginDeferred = deferred
            commandProcessor.process(session, ClientMessage.Login(username = username, password = pwd))

            return when (val result = deferred.await()) {
                is ServerMessage.LoginOk -> { transport.loginDeferred = null; session }
                is ServerMessage.SessionConflict -> {
                    transport.loginDeferred = null
                    transport.sendRaw("Force login? (y/n): ")
                    val answer = readLine()?.trim()?.lowercase()
                    if (answer == "y" || answer == "yes") {
                        val forceDeferred = CompletableDeferred<ServerMessage>()
                        transport.loginDeferred = forceDeferred
                        commandProcessor.process(session, ClientMessage.Login(username = username, password = pwd, force = true))
                        transport.loginDeferred = null
                        if (forceDeferred.await() is ServerMessage.LoginOk) session else null
                    } else null
                }
                is ServerMessage.AuthError -> {
                    transport.loginDeferred = null
                    val remaining = MAX_LOGIN_ATTEMPTS - attempt - 1
                    if (remaining > 0) {
                        transport.sendRaw("Error: ${result.reason} ($remaining attempt${if (remaining == 1) "" else "s"} remain)\r\n")
                        return@repeat
                    }
                    null
                }
                else -> { transport.loginDeferred = null; null }
            }
        }
        return null
    }

    // ─── Command loop ─────────────────────────────────────────────────────────

    private suspend fun runCommandLoop(session: PlayerSession) {
        while (true) {
            val line = readLine() ?: break
            if (line.isBlank()) {
                transport.redisplayPrompt()
                continue
            }
            when (val result = parser.parse(line, state)) {
                is ParseResult.Send -> commandProcessor.process(session, result.message)
                is ParseResult.Multi -> result.messages.forEach { commandProcessor.process(session, it) }
                is ParseResult.Local -> handleLocalCommand(result.command)
                is ParseResult.Err -> transport.sendRaw("${result.text}\r\n")
                is ParseResult.Empty -> transport.redisplayPrompt()
            }
        }
    }

    private suspend fun handleLocalCommand(cmd: LocalCommand) {
        val lines: List<String> = when (cmd) {
            is LocalCommand.Help -> parser.helpLines(cmd.topic)
            is LocalCommand.ShowMap -> renderer.render(
                state.mapData ?: return transport.sendRaw("No map data yet.\r\n"), state, useColor = true
            )
            is LocalCommand.ShowAtlas -> renderer.render(
                state.atlasData ?: return transport.sendRaw("No atlas data yet.\r\n"), state, useColor = true
            )
            is LocalCommand.ShowEffects -> renderer.render(
                ServerMessage.ActiveEffectsUpdate(state.activeEffects), state, useColor = true
            )
            is LocalCommand.ShowHp -> listOf(renderer.renderPrompt(state, useColor = true))
            is LocalCommand.Cancel -> {
                val modal = state.modalState
                val msg = when (modal) {
                    is ModalState.RiddleActive -> "You step away from the riddle."
                    is ModalState.ChoiceActive -> "You step back."
                    is ModalState.PlaceItemActive -> "Cancelled."
                    is ModalState.None -> "Nothing to cancel."
                }
                state.modalState = ModalState.None
                listOf(msg)
            }
            is LocalCommand.ShowPartyInfo -> state.partyInfo?.let {
                renderer.render(it, state, useColor = true)
            } ?: listOf("You are not in a party.")
            is LocalCommand.GetAll -> return  // handled via Multi in CommandParser
        }
        for (line in lines) transport.sendRaw("$line\r\n")
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private suspend fun readLine(): String? = iacReader.readLine()

    private suspend fun handleNegotiation(cmd: Byte, option: Byte) {
        when {
            cmd == Telnet.WILL && option == Telnet.NAWS -> {}
            cmd == Telnet.DO -> sendNeg(Telnet.WONT, option)
            cmd == Telnet.WILL -> sendNeg(Telnet.DONT, option)
            else -> {}
        }
    }

    private suspend fun sendNeg(command: Byte, option: Byte) {
        transport.sendBytes(Telnet.negotiationFrame(command, option))
    }

    private fun welcomeBanner(): String {
        val world = if (!worldName.isNullOrBlank()) "  World: $worldName\r\n" else ""
        return "\r\n" +
            "     _   _            __  __           _\r\n" +
            "    | \\ | | ___  ___ |  \\/  |_   _  __| |\r\n" +
            "    |  \\| |/ _ \\/ _ \\| |\\/| | | | |/ _` |\r\n" +
            "    | |\\  |  __/ (_) | |  | | |_| | (_| |\r\n" +
            "    |_| \\_|\\___|\\___|_|  |_|\\__,_|\\__,_|\r\n" +
            "\r\n" +
            world +
            "    Connect. Explore. Survive.\r\n" +
            "    Type 'help' after login for commands.\r\n" +
            "\r\n"
    }

    private fun randomHex(bytes: Int): String =
        java.util.UUID.randomUUID().toString().replace("-", "").take(bytes * 2)

    companion object {
        private const val MAX_LOGIN_ATTEMPTS = 3
    }
}
