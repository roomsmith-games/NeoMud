package com.neomud.client.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neomud.client.model.platform.WorldDetail
import com.neomud.client.network.ConnectionState
import com.neomud.client.network.PlatformApiClient
import com.neomud.client.network.parseServerEndpoint
import com.neomud.client.platform.PlatformAudioManager
import com.neomud.client.platform.returnToMarketplace
import com.neomud.client.platform.serverConfig
import com.neomud.client.ui.screens.GameScreen
import com.neomud.client.ui.screens.LoginScreen
import com.neomud.client.ui.screens.RegistrationScreen
import com.neomud.client.ui.screens.WorldBrowserScreen
import com.neomud.client.ui.screens.WorldDetailScreen
import com.neomud.client.viewmodel.AuthState
import com.neomud.client.viewmodel.AuthViewModel
import com.neomud.client.viewmodel.GameViewModel
import com.neomud.client.viewmodel.WorldBrowserViewModel
import neomud.client.generated.resources.Res

/**
 * Shared navigation graph for all platforms.
 * Platform entry points provide the AudioManager and CompositionLocals
 * (LocalIsLandscape, LocalSetLayoutPreference) before calling this.
 */
@Composable
fun NeoMudApp(
    authViewModel: AuthViewModel,
    audioManager: PlatformAudioManager
) {
    val navController = rememberNavController()

    DisposableEffect(Unit) {
        onDispose { audioManager.release() }
    }

    val authState by authViewModel.authState.collectAsState()
    val connectionState by authViewModel.connectionState.collectAsState()
    val connectionError by authViewModel.connectionError.collectAsState()
    val availableClasses by authViewModel.availableClasses.collectAsState()
    val availableRaces by authViewModel.availableRaces.collectAsState()

    // Selected world from marketplace — drives connection target
    var selectedWorld by remember { mutableStateOf<WorldDetail?>(null) }

    // World browser ViewModel (shared across browser + detail screens)
    val worldBrowserViewModel = remember {
        WorldBrowserViewModel(PlatformApiClient(serverConfig.platformApiUrl))
    }
    val worlds by worldBrowserViewModel.worlds.collectAsState()
    val worldBrowserLoading by worldBrowserViewModel.isLoading.collectAsState()
    val worldBrowserError by worldBrowserViewModel.error.collectAsState()
    val worldDetail by worldBrowserViewModel.selectedWorld.collectAsState()
    val worldDetailLoading by worldBrowserViewModel.isLoadingDetail.collectAsState()

    // Navigate on auth state changes
    LaunchedEffect(authState) {
        if (authState is AuthState.LoggedIn) {
            navController.navigate("game") {
                popUpTo("login") { inclusive = true }
            }
        }
        if (authState is AuthState.Registered) {
            navController.navigate("login") {
                popUpTo("register") { inclusive = true }
            }
        }
        if (authState is AuthState.GuestCharacterCreation) {
            navController.navigate("guestRegister") {
                popUpTo("login") { inclusive = true }
            }
        }
        // Handle logout: Idle while on game screen → back to world selection
        if (authState is AuthState.Idle) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute == "game") {
                if (serverConfig.skipMarketplace) {
                    // Launched from React marketplace — redirect back to it
                    returnToMarketplace()
                } else {
                    navController.navigate("worldBrowser") {
                        popUpTo("game") { inclusive = true }
                    }
                }
            }
        }
    }

    // Load worlds on first composition
    LaunchedEffect(Unit) {
        worldBrowserViewModel.loadWorlds()
    }

    // Dark background fills edge-to-edge (including safe area zones on iOS)
    // so notch/home indicator areas show dark instead of white
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF080604))) {
    // When launched from the marketplace, skip the world browser and go straight to login
    val startDestination = if (serverConfig.skipMarketplace) "login" else "worldBrowser"

    NavHost(navController = navController, startDestination = startDestination) {

        composable("worldBrowser") {
            // Play intro BGM from embedded resource
            LaunchedEffect(Unit) {
                val introUri = Res.getUri("files/intro_theme.mp3")
                audioManager.playBgmFromUri(introUri, "intro_theme")
            }
            WorldBrowserScreen(
                worlds = worlds,
                isLoading = worldBrowserLoading,
                error = worldBrowserError,
                showDirectConnect = serverConfig.showServerConfig,
                onWorldClick = { slug ->
                    worldBrowserViewModel.loadWorldDetail(slug)
                    navController.navigate("worldDetail")
                },
                onSearch = { query -> worldBrowserViewModel.loadWorlds(query) },
                onRetry = { worldBrowserViewModel.loadWorlds() },
                onDirectConnect = {
                    // Skip marketplace, go straight to manual login (dev mode)
                    navController.navigate("login")
                }
            )
        }

        composable("worldDetail") {
            WorldDetailScreen(
                world = worldDetail,
                isLoading = worldDetailLoading,
                error = worldBrowserError,
                onPlay = { world ->
                    selectedWorld = world
                    worldBrowserViewModel.clearSelection()
                    navController.navigate("login")
                },
                onBack = {
                    worldBrowserViewModel.clearSelection()
                    navController.popBackStack()
                },
                onRetry = {
                    worldBrowserViewModel.clearError()
                    // Re-fetch if we have a slug
                    worldDetail?.slug?.let { worldBrowserViewModel.loadWorldDetail(it) }
                }
            )
        }

        composable("login") {
            // Play BGM on login screen:
            // - WASM (skipMarketplace=true): index.html handles BGM, don't double-play
            // - Native: play world's loading BGM if selected, else intro theme
            if (!serverConfig.skipMarketplace) {
                val worldBgmUrl = selectedWorld?.loadingBgmUrl
                LaunchedEffect(worldBgmUrl) {
                    if (!worldBgmUrl.isNullOrEmpty()) {
                        // World-specific BGM — resolve relative URLs against Platform API
                        val fullUrl = if (worldBgmUrl.startsWith("http")) worldBgmUrl
                            else serverConfig.platformApiUrl.removeSuffix("/api/v1") + worldBgmUrl
                        audioManager.playBgmFromUri(fullUrl, "world_loading")
                    } else {
                        val introUri = Res.getUri("files/intro_theme.mp3")
                        audioManager.playBgmFromUri(introUri, "intro_theme")
                    }
                }
            }

            // If a world was selected from marketplace, auto-connect to its endpoint
            val world = selectedWorld
            val parsedEndpoint = remember(world?.serverEndpoint) {
                world?.serverEndpoint?.let { parseServerEndpoint(it) }
            }

            // Auto-connect when arriving from marketplace with a valid endpoint
            LaunchedEffect(parsedEndpoint) {
                val ep = parsedEndpoint ?: return@LaunchedEffect
                if (connectionState == ConnectionState.DISCONNECTED) {
                    authViewModel.connect(ep.host, ep.port, ep.useTls, ep.path)
                }
            }

            // Auto-connect when launched from marketplace with injected config (skipMarketplace)
            LaunchedEffect(serverConfig.skipMarketplace) {
                if (serverConfig.skipMarketplace && connectionState == ConnectionState.DISCONNECTED) {
                    authViewModel.connect(serverConfig.defaultHost, serverConfig.defaultPort, serverConfig.useTls, serverConfig.serverPath)
                }
            }

            // Auto-guest for marketplace users without platform auth:
            // once connected and catalogs synced (Idle), skip login screen → guest flow
            LaunchedEffect(connectionState, authState) {
                if (serverConfig.skipMarketplace &&
                    serverConfig.platformToken.isEmpty() &&
                    connectionState == ConnectionState.CONNECTED &&
                    authState is AuthState.Idle) {
                    authViewModel.startGuestSession()
                }
            }

            LoginScreen(
                connectionState = connectionState,
                authState = authState,
                connectionError = connectionError,
                onConnect = { host, port ->
                    val ep = parsedEndpoint
                    if (ep != null) {
                        authViewModel.connect(ep.host, ep.port, ep.useTls, ep.path)
                    } else {
                        authViewModel.connect(host, port, serverConfig.useTls, serverConfig.serverPath)
                    }
                },
                onLogin = { characterName -> authViewModel.login(characterName) },
                onNavigateToRegister = {
                    authViewModel.clearError()
                    navController.navigate("register")
                },
                onClearError = { authViewModel.clearError() },
                onPlatformLogin = { authViewModel.platformLogin() },
                onPlayAsGuest = { authViewModel.startGuestSession() },
                onForceLogin = { authViewModel.forceLogin() },
                onCancelForceLogin = { authViewModel.cancelForceLogin() }
            )
        }

        composable("guestRegister") {
            val nameAvailability by authViewModel.nameAvailability.collectAsState()
            RegistrationScreen(
                authState = authState,
                availableClasses = availableClasses,
                availableRaces = availableRaces,
                serverBaseUrl = authViewModel.serverBaseUrl,
                nameAvailability = nameAvailability,
                isGuestMode = true,
                onRegister = { characterName, characterClass, race, gender, allocatedStats ->
                    authViewModel.guestLogin(characterName, characterClass, race, gender, allocatedStats)
                },
                onCheckName = { authViewModel.checkName(it) },
                onClearNameCheck = { authViewModel.clearNameCheck() },
                onBack = {
                    // Auto-guest from the marketplace pops `login` inclusively when it
                    // navigates to `guestRegister`, so the back stack is empty here —
                    // `popBackStack()` returns false and strands the user (NeoMud#300).
                    // Mirror the logout-from-game behavior: bounce back to the
                    // marketplace SPA. The non-skipMarketplace path (worldBrowser →
                    // login → guestRegister) still has frames to pop.
                    authViewModel.clearError()
                    if (serverConfig.skipMarketplace) {
                        returnToMarketplace()
                    } else {
                        navController.popBackStack()
                    }
                },
                onClearError = { authViewModel.clearError() }
            )
        }

        composable("register") {
            val nameAvailability by authViewModel.nameAvailability.collectAsState()
            RegistrationScreen(
                authState = authState,
                availableClasses = availableClasses,
                availableRaces = availableRaces,
                serverBaseUrl = authViewModel.serverBaseUrl,
                nameAvailability = nameAvailability,
                onRegister = { characterName, characterClass, race, gender, allocatedStats ->
                    authViewModel.register(characterName, characterClass, race, gender, allocatedStats)
                },
                onCheckName = { characterName ->
                    authViewModel.checkName(characterName)
                },
                onClearNameCheck = { authViewModel.clearNameCheck() },
                onBack = { navController.popBackStack() },
                onClearError = { authViewModel.clearError() }
            )
        }

        composable("game") {
            val initialPlayer = (authState as? AuthState.LoggedIn)?.player
            val serverBaseUrl = authViewModel.serverBaseUrl
            val initialClasses = authViewModel.availableClasses.value
            val initialItems = authViewModel.availableItems.value
            val initialSpells = authViewModel.availableSpells.value
            val initialSkills = authViewModel.availableSkills.value
            val initialRoomInfo = authViewModel.initialRoomInfo.value
            val initialMapData = authViewModel.initialMapData.value
            val initialTutorials = authViewModel.initialTutorials.value
            val gameViewModel = remember {
                GameViewModel(authViewModel.wsClient, serverBaseUrl, audioManager).also {
                    if (initialPlayer != null) it.setInitialPlayer(initialPlayer)
                    it.setInitialCatalogs(classes = initialClasses, items = initialItems, spells = initialSpells, skills = initialSkills)
                    if (initialRoomInfo != null) it.setInitialRoomInfo(initialRoomInfo)
                    if (initialMapData != null) it.setInitialMapData(initialMapData)
                    initialTutorials.forEach { t -> it.setInitialTutorial(t) }
                    it.startCollecting()
                }
            }
            GameScreen(
                gameViewModel = gameViewModel,
                onLogout = { authViewModel.logout() },
                audioManager = audioManager
            )
        }
    }
    } // end edge-to-edge dark background
}

