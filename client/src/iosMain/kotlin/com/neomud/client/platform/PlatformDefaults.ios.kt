package com.neomud.client.platform

import platform.Foundation.NSBundle

actual fun returnToMarketplace() { /* No-op on iOS — world browser handles navigation */ }
actual fun dismissNativeLoadingScreen() { /* No-op on iOS */ }

actual val serverConfig: ServerConfig = ServerConfig(
    defaultHost = (NSBundle.mainBundle.objectForInfoDictionaryKey("NeoMudDefaultHost") as? String) ?: "127.0.0.1",
    defaultPort = (NSBundle.mainBundle.objectForInfoDictionaryKey("NeoMudDefaultPort") as? String)?.toIntOrNull() ?: 8080,
    useTls = (NSBundle.mainBundle.objectForInfoDictionaryKey("NeoMudUseTLS") as? String) == "true",
    showServerConfig = (NSBundle.mainBundle.objectForInfoDictionaryKey("NeoMudShowConfig") as? String ?: "true") == "true",
    platformApiUrl = (NSBundle.mainBundle.objectForInfoDictionaryKey("NeoMudPlatformApiUrl") as? String) ?: "http://localhost:3002/api/v1"
)
