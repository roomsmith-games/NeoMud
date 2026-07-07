package com.neomud.server.telnet

import java.util.concurrent.ConcurrentHashMap

/**
 * Atomic per-IP live-connection counter for [TelnetServer].
 *
 * Reserve and release both go through [ConcurrentHashMap.compute], so the cap check and the
 * increment happen as one atomic step — a plain get-then-increment could admit two concurrent
 * accepts past the cap, and a decrement-then-remove could orphan a still-live counter.
 */
class IpConnectionLimiter(private val maxPerIp: Int) {
    private val counts = ConcurrentHashMap<String, Int>()

    /** Reserve a slot for [ip] if it is under the cap. Returns true when admitted. */
    fun tryReserve(ip: String): Boolean {
        var admitted = false
        counts.compute(ip) { _, current ->
            val c = current ?: 0
            if (c >= maxPerIp) c else { admitted = true; c + 1 }
        }
        return admitted
    }

    /** Release a slot for [ip], dropping the map entry once it reaches zero. */
    fun release(ip: String) {
        counts.compute(ip) { _, current ->
            val c = (current ?: 1) - 1
            if (c <= 0) null else c
        }
    }

    /** Current live-connection count for [ip] (0 when none). */
    fun activeFor(ip: String): Int = counts[ip] ?: 0
}
