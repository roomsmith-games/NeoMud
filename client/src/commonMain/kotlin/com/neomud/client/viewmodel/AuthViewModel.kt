package com.neomud.client.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neomud.client.network.ConnectionState
import com.neomud.client.network.GameConnection
import com.neomud.client.platform.PlatformLogger
import com.neomud.client.platform.serverConfig
import com.neomud.client.network.WebSocketClient
import com.neomud.shared.NeoMudVersion
import com.neomud.shared.model.CharacterClassDef
import com.neomud.shared.model.Item
import com.neomud.shared.model.Player
import com.neomud.shared.model.RaceDef
import com.neomud.shared.model.SkillDef
import com.neomud.shared.model.SpellDef
import com.neomud.shared.model.Stats
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.ServerMessage
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    val wsClient: GameConnection = WebSocketClient()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    val connectionState: StateFlow<ConnectionState> = wsClient.connectionState
    val connectionError: StateFlow<String?> = wsClient.connectionError

    private val _availableClasses = MutableStateFlow<List<CharacterClassDef>>(emptyList())
    val availableClasses: StateFlow<List<CharacterClassDef>> = _availableClasses

    private val _availableRaces = MutableStateFlow<List<RaceDef>>(emptyList())
    val availableRaces: StateFlow<List<RaceDef>> = _availableRaces

    private val _availableSpells = MutableStateFlow<List<SpellDef>>(emptyList())
    val availableSpells: StateFlow<List<SpellDef>> = _availableSpells

    private val _availableItems = MutableStateFlow<List<Item>>(emptyList())
    val availableItems: StateFlow<List<Item>> = _availableItems

    private val _availableSkills = MutableStateFlow<List<SkillDef>>(emptyList())
    val availableSkills: StateFlow<List<SkillDef>> = _availableSkills

    private var pendingLoginCharacterName: String? = null
    private var pendingGuestLogin: Boolean = false
    private var pendingForceLogin: ClientMessage.Login? = null
    private var pendingForcePlatformLogin: ClientMessage.PlatformLogin? = null

    private val _initialRoomInfo = MutableStateFlow<ServerMessage.RoomInfo?>(null)
    val initialRoomInfo: StateFlow<ServerMessage.RoomInfo?> = _initialRoomInfo

    private val _initialMapData = MutableStateFlow<ServerMessage.MapData?>(null)
    val initialMapData: StateFlow<ServerMessage.MapData?> = _initialMapData

    /**
     * Captures Tutorial messages arriving between LoginOk and GameViewModel.startCollecting().
     * Queue (not single slot) so multiple sequential tutorials — e.g. per-world intro
     * (#272) followed by `welcome` — both reach the GameViewModel.
     */
    private val _initialTutorials = MutableStateFlow<List<ServerMessage.Tutorial>>(emptyList())
    val initialTutorials: StateFlow<List<ServerMessage.Tutorial>> = _initialTutorials

    private val _serverInfo = MutableStateFlow(ServerInfo())
    val serverInfo: StateFlow<ServerInfo> = _serverInfo

    data class UpdateInfo(val minVersion: String, val currentVersion: String)
    private val _updateRequired = MutableStateFlow<UpdateInfo?>(null)
    val updateRequired: StateFlow<UpdateInfo?> = _updateRequired

    data class NameAvailability(val usernameAvailable: Boolean, val characterNameAvailable: Boolean)
    private val _nameAvailability = MutableStateFlow<NameAvailability?>(null)
    val nameAvailability: StateFlow<NameAvailability?> = _nameAvailability

    private var _serverHost: String = ""
    private var _serverPort: Int = 0
    private var _useTls: Boolean = false
    private var _serverPath: String = "/game"
    val serverBaseUrl: String get() {
        val scheme = if (_useTls) "https" else "http"
        // Strip trailing /game to get the world-scoped base URL for assets
        val pathPrefix = _serverPath.removeSuffix("/game")
        return "$scheme://$_serverHost:$_serverPort$pathPrefix"
    }

    init {
        viewModelScope.launch {
            wsClient.messages.collect { message ->
                try {
                    when (message) {
                        is ServerMessage.LoginOk -> {
                            pendingLoginCharacterName = null
                            _initialRoomInfo.value = null
                            _initialMapData.value = null
                            _initialTutorials.value = emptyList()
                            _authState.value = AuthState.LoggedIn(message.player)
                        }
                        is ServerMessage.RoomInfo -> {
                            // Capture the initial RoomInfo sent right after LoginOk
                            // so GameViewModel can use it before its collector subscribes
                            if (_authState.value is AuthState.LoggedIn) {
                                _initialRoomInfo.value = message
                            }
                        }
                        is ServerMessage.MapData -> {
                            if (_authState.value is AuthState.LoggedIn) {
                                _initialMapData.value = message
                            }
                        }
                        is ServerMessage.Tutorial -> {
                            if (_authState.value is AuthState.LoggedIn) {
                                _initialTutorials.value = _initialTutorials.value + message
                            }
                        }
                        is ServerMessage.RegisterOk -> {
                            if (pendingGuestLogin) {
                                pendingGuestLogin = false
                            } else {
                                val charName = pendingLoginCharacterName
                                if (charName != null) {
                                    wsClient.send(ClientMessage.Login(characterName = charName))
                                    pendingLoginCharacterName = null
                                } else {
                                    _authState.value = AuthState.Registered
                                }
                            }
                        }
                        is ServerMessage.AuthError -> {
                            pendingLoginCharacterName = null
                            _authState.value = AuthState.Error(message.reason)
                        }
                        is ServerMessage.SessionConflict -> {
                            _authState.value = AuthState.SessionConflict(
                                characterName = message.characterName,
                                message = message.message
                            )
                        }
                        is ServerMessage.NameCheckResult -> {
                            _nameAvailability.value = NameAvailability(
                                message.usernameAvailable,
                                message.characterNameAvailable
                            )
                        }
                        is ServerMessage.ClassCatalogSync -> {
                            _availableClasses.value = message.classes
                        }
                        is ServerMessage.RaceCatalogSync -> {
                            _availableRaces.value = message.races
                        }
                        is ServerMessage.SpellCatalogSync -> {
                            _availableSpells.value = message.spells
                        }
                        is ServerMessage.ItemCatalogSync -> {
                            _availableItems.value = message.items
                        }
                        is ServerMessage.SkillCatalogSync -> {
                            _availableSkills.value = message.skills
                        }
                        is ServerMessage.ServerHello -> {
                            _serverInfo.value = ServerInfo(
                                engineVersion = message.engineVersion,
                                protocolVersion = message.protocolVersion,
                                worldName = message.worldName,
                                worldVersion = message.worldVersion
                            )

                            // Check minimum version before proceeding
                            if (message.minClientVersion.isNotEmpty() &&
                                NeoMudVersion.compareVersions(NeoMudVersion.ENGINE_VERSION, message.minClientVersion) < 0) {
                                _updateRequired.value = UpdateInfo(
                                    minVersion = message.minClientVersion,
                                    currentVersion = NeoMudVersion.ENGINE_VERSION
                                )
                                return@collect
                            }

                            val token = serverConfig.platformToken.takeIf { it.isNotEmpty() }
                            wsClient.send(ClientMessage.ClientHello(
                                clientVersion = NeoMudVersion.ENGINE_VERSION,
                                protocolVersion = NeoMudVersion.PROTOCOL_VERSION,
                                platformToken = token
                            ))
                        }
                        is ServerMessage.PlatformAuthOk -> {
                            val charName = message.characterName
                            if (!message.needsCharacterCreation && charName != null) {
                                _authState.value = AuthState.PlatformReady(charName, message.platformUserId)
                            } else if (message.role == "GUEST") {
                                // Anonymous platform session — route to the ephemeral
                                // guest character flow instead of platform-register.
                                // Anon platform users have no Platform DB record so
                                // they can't own a persistent character; the server
                                // also rejects PlatformRegister from this role.
                                _authState.value = AuthState.GuestCharacterCreation
                            } else {
                                _authState.value = AuthState.PlatformNeedsCharacter(message.platformUserId)
                            }
                        }
                        is ServerMessage.ConnectionRejected -> {
                            _updateRequired.value = UpdateInfo(
                                minVersion = message.minClientVersion,
                                currentVersion = NeoMudVersion.ENGINE_VERSION
                            )
                        }
                        else -> { /* handled by GameViewModel */ }
                    }
                } catch (e: Exception) {
                    PlatformLogger.e("AuthViewModel", "Message handling failed for ${message::class.simpleName}", e)
                }
            }
        }
    }

    fun connect(host: String, port: Int, useTls: Boolean = false, path: String = "/game") {
        _serverHost = host
        _serverPort = port
        _useTls = useTls
        // _serverPath stores the BARE path (no query string) — `serverBaseUrl`
        // strips the trailing `/game` from it to compose asset URLs, and any
        // query string here would defeat that strip and pollute every asset
        // URL with the JWT (issue #297). The token-augmented path is what
        // wsClient.connect needs (gameProxy gateway requires ?token=<jwt> on
        // the upgrade or returns 401), but it must NOT leak into _serverPath.
        _serverPath = path
        // Append the platform JWT to the WS URL when present. Logged-in users
        // get a real JWT; anonymous visitors get a guest JWT minted by
        // Play.tsx via /api/v1/auth/anonymous. Empty token → don't append
        // (preserves the legacy direct-connect path used by Android dev).
        val pathWithToken = if (serverConfig.platformToken.isNotEmpty()) {
            val sep = if (path.contains('?')) "&" else "?"
            "$path${sep}token=${serverConfig.platformToken.encodeURLQueryComponent()}"
        } else {
            path
        }
        wsClient.connect(host, port, useTls, viewModelScope, pathWithToken)
    }

    fun login(characterName: String) {
        _authState.value = AuthState.Loading
        val msg = ClientMessage.Login(characterName = characterName)
        pendingForceLogin = msg
        pendingForcePlatformLogin = null
        viewModelScope.launch {
            val sent = wsClient.send(msg)
            if (!sent) {
                pendingForceLogin = null
                _authState.value = AuthState.Error("Not connected to server")
            }
        }
    }

    fun register(characterName: String, characterClass: String, race: String = "", gender: String = "neutral", allocatedStats: Stats = Stats()) {
        _authState.value = AuthState.Loading
        pendingLoginCharacterName = characterName
        viewModelScope.launch {
            val sent = wsClient.send(
                ClientMessage.Register(characterName = characterName, characterClass = characterClass, race = race, gender = gender, allocatedStats = allocatedStats)
            )
            if (!sent) {
                pendingLoginCharacterName = null
                _authState.value = AuthState.Error("Not connected to server")
            }
        }
    }

    fun checkName(characterName: String) {
        _nameAvailability.value = null
        viewModelScope.launch {
            wsClient.send(ClientMessage.CheckName(characterName = characterName))
        }
    }

    fun clearNameCheck() {
        _nameAvailability.value = null
    }

    fun clearError() {
        _authState.value = AuthState.Idle
    }

    /** Auto-login with verified platform session (returning player). */
    fun platformLogin() {
        _authState.value = AuthState.Loading
        val msg = ClientMessage.PlatformLogin()
        pendingForcePlatformLogin = msg
        pendingForceLogin = null
        viewModelScope.launch {
            wsClient.send(msg)
        }
    }

    /** Create character for platform user (first time on this world). */
    fun platformRegister(characterName: String, characterClass: String, race: String, gender: String, allocatedStats: Stats) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            wsClient.send(ClientMessage.PlatformRegister(
                characterName = characterName,
                characterClass = characterClass,
                race = race,
                gender = gender,
                allocatedStats = allocatedStats
            ))
        }
    }

    /** Transition to guest character creation screen. */
    fun startGuestSession() {
        _authState.value = AuthState.GuestCharacterCreation
    }

    /** Create ephemeral guest character and auto-login. */
    fun guestLogin(characterName: String, characterClass: String, race: String, gender: String, allocatedStats: Stats) {
        _authState.value = AuthState.Loading
        pendingGuestLogin = true
        viewModelScope.launch {
            val sent = wsClient.send(ClientMessage.GuestLogin(
                characterName = characterName,
                characterClass = characterClass,
                race = race,
                gender = gender,
                allocatedStats = allocatedStats
            ))
            if (!sent) {
                pendingGuestLogin = false
                _authState.value = AuthState.Error("Not connected to server")
            }
        }
    }

    fun forceLogin() {
        val msg = pendingForceLogin?.copy(force = true)
            ?: pendingForcePlatformLogin?.copy(force = true)
        pendingForceLogin = null
        pendingForcePlatformLogin = null
        if (msg == null) {
            _authState.value = AuthState.Error("Session expired, please try again")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            wsClient.send(msg)
        }
    }

    fun cancelForceLogin() {
        pendingForceLogin = null
        pendingForcePlatformLogin = null
        _authState.value = AuthState.Idle
    }

    fun logout() {
        pendingForceLogin = null
        pendingForcePlatformLogin = null
        wsClient.disconnect()
        _authState.value = AuthState.Idle
        _availableClasses.value = emptyList()
    }

    override fun onCleared() {
        wsClient.disconnect()
    }
}

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data object Registered : AuthState()
    data class LoggedIn(val player: Player) : AuthState()
    data class Error(val message: String) : AuthState()
    /** Server detected an existing session for this character — prompt player to force-displace */
    data class SessionConflict(val characterName: String, val message: String) : AuthState()
    /** Platform token verified, returning player — show "Continue as [character]" */
    data class PlatformReady(val characterName: String, val platformUserId: String) : AuthState()
    /** Platform token verified, no character on this world — show character creation */
    data class PlatformNeedsCharacter(val platformUserId: String) : AuthState()
    /** Guest mode — show character creation without credentials */
    data object GuestCharacterCreation : AuthState()
}

data class ServerInfo(
    val engineVersion: String = "",
    val protocolVersion: Int = 1,
    val worldName: String = "",
    val worldVersion: String = ""
)
