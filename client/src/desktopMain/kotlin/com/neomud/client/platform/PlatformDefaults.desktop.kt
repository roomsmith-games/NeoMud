package com.neomud.client.platform

actual fun returnToMarketplace() { /* No-op on desktop — world browser handles navigation */ }
actual fun dismissNativeLoadingScreen() { /* No-op on desktop */ }

actual val serverConfig: ServerConfig = ServerConfig(
    defaultHost = System.getProperty("neomud.host", "127.0.0.1"),
    defaultPort = System.getProperty("neomud.port", "8080").toInt(),
    useTls = System.getProperty("neomud.tls", "false").toBoolean(),
    showServerConfig = System.getProperty("neomud.showConfig", "true").toBoolean(),
    platformApiUrl = System.getProperty("neomud.platformApi", "http://localhost:3002/api/v1")
)
