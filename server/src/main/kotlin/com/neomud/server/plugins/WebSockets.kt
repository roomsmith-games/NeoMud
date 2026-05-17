package com.neomud.server.plugins

import io.ktor.server.application.*
import io.ktor.server.websocket.*
import kotlin.time.Duration.Companion.seconds

fun Application.configureWebSockets() {
    install(WebSockets) {
        pingPeriodMillis = 15_000
        timeoutMillis = 30_000
        maxFrameSize = 131072 // 128KB — atlas response can reach ~60KB for fully-explored worlds
        masking = false
    }
}
