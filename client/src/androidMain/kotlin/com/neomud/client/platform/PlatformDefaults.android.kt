package com.neomud.client.platform

import com.neomud.client.BuildConfig

actual fun returnToMarketplace() { /* No-op on Android — world browser handles navigation */ }
actual fun dismissNativeLoadingScreen() { /* No-op on Android */ }

actual val serverConfig: ServerConfig = ServerConfig(
    defaultHost = BuildConfig.DEFAULT_HOST,
    defaultPort = BuildConfig.DEFAULT_PORT,
    useTls = BuildConfig.USE_TLS,
    showServerConfig = BuildConfig.SHOW_SERVER_CONFIG,
    platformApiUrl = BuildConfig.PLATFORM_API_URL
)
