package com.neomud.client.platform

private fun isLocalhost(): Boolean = js("window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'")

// Read injected config from the marketplace's Play.tsx page (XSS-safe data-attr injection).
// Returns empty string / 0 / false if not set (Kotlin/WASM js() doesn't support nullable String).
private fun getInjectedHost(): String = js("(window.__NEOMUD_CONFIG__ && window.__NEOMUD_CONFIG__.serverHost) || ''")
private fun getInjectedPort(): Int = js("(window.__NEOMUD_CONFIG__ && window.__NEOMUD_CONFIG__.serverPort) || 0")
private fun getInjectedTls(): Boolean = js("!!(window.__NEOMUD_CONFIG__ && window.__NEOMUD_CONFIG__.useTls)")
private fun getInjectedSkipMarketplace(): Boolean = js("!!(window.__NEOMUD_CONFIG__ && window.__NEOMUD_CONFIG__.skipMarketplace)")
private fun getInjectedWorldName(): String = js("(window.__NEOMUD_CONFIG__ && window.__NEOMUD_CONFIG__.worldName) || ''")
private fun getInjectedWorldVersion(): String = js("(window.__NEOMUD_CONFIG__ && window.__NEOMUD_CONFIG__.worldVersion) || ''")
private fun getInjectedCreatorName(): String = js("(window.__NEOMUD_CONFIG__ && window.__NEOMUD_CONFIG__.creatorName) || ''")
private fun getInjectedCoverImageUrl(): String = js("(window.__NEOMUD_CONFIG__ && window.__NEOMUD_CONFIG__.coverImageUrl) || ''")
private fun getInjectedLoadingBgmUrl(): String = js("(window.__NEOMUD_CONFIG__ && window.__NEOMUD_CONFIG__.loadingBgmUrl) || ''")
private fun getInjectedServerPath(): String = js("(window.__NEOMUD_CONFIG__ && window.__NEOMUD_CONFIG__.serverPath) || ''")
private fun getInjectedPlatformToken(): String = js("(window.__NEOMUD_CONFIG__ && window.__NEOMUD_CONFIG__.platformToken) || ''")

private fun getHostname(): String = js("window.location.hostname || ''")
private fun jsNavigate(url: String): Unit = js("window.location.href = url")

actual fun returnToMarketplace() {
    jsNavigate("/")
}

actual fun dismissNativeLoadingScreen() {
    js("if (typeof window.__NEOMUD_DISMISS_LOADING__ === 'function') window.__NEOMUD_DISMISS_LOADING__()")
}

actual val serverConfig: ServerConfig = run {
    val injectedHost = getInjectedHost()
    val injectedPort = getInjectedPort()
    val hostname = getHostname()

    // Derive platform API URL from the current hostname
    val platformApi = when {
        hostname == "localhost" || hostname == "127.0.0.1" -> "http://localhost:3002/api/v1"
        hostname.startsWith("stage") -> "https://stage-api.neomud.app/api/v1"
        else -> "https://api.neomud.app/api/v1"
    }

    if (injectedHost.isNotEmpty() && injectedPort > 0) {
        // Launched from the React marketplace — connect to the selected world's server
        val injectedPath = getInjectedServerPath()
        val injectedToken = getInjectedPlatformToken()
        ServerConfig(
            defaultHost = injectedHost,
            defaultPort = injectedPort,
            useTls = getInjectedTls(),
            showServerConfig = false,
            platformApiUrl = platformApi,
            serverPath = if (injectedPath.isNotEmpty()) injectedPath else "/game",
            platformToken = injectedToken,
            skipMarketplace = getInjectedSkipMarketplace(),
            worldName = getInjectedWorldName(),
            worldVersion = getInjectedWorldVersion(),
            creatorName = getInjectedCreatorName(),
            coverImageUrl = getInjectedCoverImageUrl(),
            loadingBgmUrl = getInjectedLoadingBgmUrl()
        )
    } else {
        // Standalone / dev mode — detect from browser location
        val local = isLocalhost()
        ServerConfig(
            defaultHost = if (local) "localhost" else "play.neomud.app",
            defaultPort = if (local) 8080 else 443,
            useTls = !local,
            showServerConfig = true,
            platformApiUrl = platformApi
        )
    }
}
