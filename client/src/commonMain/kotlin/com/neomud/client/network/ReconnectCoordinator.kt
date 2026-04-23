package com.neomud.client.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Runs a bounded reconnect loop and publishes its state so the UI can
 * render the "World is reloading…" overlay and Retry button. Pure
 * coordination logic — doesn't know anything about WebSockets; the
 * caller supplies an `attempt` lambda that returns true on success.
 *
 * Designed to be unit-testable with virtual time.
 */
class ReconnectCoordinator(
    private val maxAttempts: Int = 30,
    private val intervalMs: Long = 1000,
) {
    private val _status = MutableStateFlow<ReconnectStatus>(ReconnectStatus.Idle)
    val status: StateFlow<ReconnectStatus> = _status

    private var job: Job? = null

    /**
     * Run the reconnect loop. Cancels any in-flight loop first.
     * Invokes [attempt] up to [maxAttempts] times with [intervalMs]
     * between attempts. Transitions status to Reconnecting before each
     * attempt; to Idle when one succeeds; to Failed when all attempts
     * have been exhausted.
     *
     * Exceptions thrown by [attempt] are treated as failures of that
     * attempt (loop continues). CancellationException is re-thrown so
     * scope cancellation propagates normally.
     */
    fun launch(scope: CoroutineScope, attempt: suspend () -> Boolean) {
        job?.cancel()
        job = scope.launch {
            try {
                for (i in 1..maxAttempts) {
                    _status.value = ReconnectStatus.Reconnecting(i, maxAttempts)
                    delay(intervalMs)
                    val success = try {
                        attempt()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        false
                    }
                    if (success) {
                        _status.value = ReconnectStatus.Idle
                        return@launch
                    }
                }
                _status.value = ReconnectStatus.Failed
            } catch (e: CancellationException) {
                // Cancellation leaves status unchanged — a subsequent
                // cancel() call sets it to Idle, and a subsequent
                // launch() overwrites it.
                throw e
            }
        }
    }

    /** Stop any in-flight reconnect loop and reset to Idle. */
    fun cancel() {
        job?.cancel()
        job = null
        _status.value = ReconnectStatus.Idle
    }
}
