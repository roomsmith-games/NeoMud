package com.neomud.client.platform

/** Server connection configuration, driven by build flavor (Android) or build config (Desktop/iOS). */
data class ServerConfig(
    val defaultHost: String,
    val defaultPort: Int,
    val useTls: Boolean,
    val showServerConfig: Boolean,
    val platformApiUrl: String,
    /** WebSocket path — defaults to /game, overridden by marketplace endpoint (e.g. /worlds/{id}/game). */
    val serverPath: String = "/game",
    /** Platform JWT for auto-authentication in game servers (empty = use password auth). */
    val platformToken: String = "",
    /** When true, skip the world browser and go directly to login (marketplace-launched). */
    val skipMarketplace: Boolean = false,
    /** World metadata from marketplace (only set when skipMarketplace=true). */
    val worldName: String = "",
    val worldVersion: String = "",
    val creatorName: String = "",
    val coverImageUrl: String = "",
    val loadingBgmUrl: String = ""
)

expect val serverConfig: ServerConfig

/** Redirect back to the web marketplace. Only meaningful on WASM; no-op on native platforms. */
expect fun returnToMarketplace()

/** Dismiss the native loading screen. On WASM, fades out the HTML loading overlay.
 *  No-op on native platforms (Android/iOS/Desktop handle their own splash screens). */
expect fun dismissNativeLoadingScreen()

/** Backward-compatible alias — use [serverConfig] directly for new code. */
val defaultServerHost: String get() = serverConfig.defaultHost
