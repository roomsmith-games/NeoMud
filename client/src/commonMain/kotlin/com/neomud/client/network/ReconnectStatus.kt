package com.neomud.client.network

/**
 * Visible state of an in-progress reconnect attempt. Drives the
 * "World is reloading…" overlay in the game screen.
 */
sealed class ReconnectStatus {
    /** No reconnect activity — either connected normally, never was, or the user explicitly disconnected. */
    data object Idle : ReconnectStatus()

    /** Currently attempting to reconnect. [attempt] is 1-indexed. */
    data class Reconnecting(val attempt: Int, val maxAttempts: Int) : ReconnectStatus()

    /** All reconnect attempts exhausted without success. UI should offer a manual Retry. */
    data object Failed : ReconnectStatus()
}
