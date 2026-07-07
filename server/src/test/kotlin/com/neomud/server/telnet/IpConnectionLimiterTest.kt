package com.neomud.server.telnet

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IpConnectionLimiterTest {

    @Test fun admitsUpToCapThenRejects() {
        val limiter = IpConnectionLimiter(maxPerIp = 3)
        assertTrue(limiter.tryReserve("1.2.3.4"))
        assertTrue(limiter.tryReserve("1.2.3.4"))
        assertTrue(limiter.tryReserve("1.2.3.4"))
        assertFalse(limiter.tryReserve("1.2.3.4"), "4th over a cap of 3 must be rejected")
        assertEquals(3, limiter.activeFor("1.2.3.4"))
    }

    @Test fun differentIpsHaveIndependentBudgets() {
        val limiter = IpConnectionLimiter(maxPerIp = 1)
        assertTrue(limiter.tryReserve("a"))
        assertFalse(limiter.tryReserve("a"))
        assertTrue(limiter.tryReserve("b"), "a's cap must not affect b")
    }

    @Test fun releaseFreesASlot() {
        val limiter = IpConnectionLimiter(maxPerIp = 1)
        assertTrue(limiter.tryReserve("ip"))
        assertFalse(limiter.tryReserve("ip"))
        limiter.release("ip")
        assertTrue(limiter.tryReserve("ip"), "a released slot should be reusable")
    }

    @Test fun releaseToZeroRemovesEntry() {
        val limiter = IpConnectionLimiter(maxPerIp = 2)
        limiter.tryReserve("ip")
        limiter.release("ip")
        assertEquals(0, limiter.activeFor("ip"))
    }

    @Test fun releaseBelowZeroStaysAtZero() {
        val limiter = IpConnectionLimiter(maxPerIp = 2)
        limiter.release("ghost")  // never reserved — must not underflow
        assertEquals(0, limiter.activeFor("ghost"))
        assertTrue(limiter.tryReserve("ghost"))
    }

    /** The whole point of the compute-based reserve: N racing threads never exceed the cap. */
    @Test fun concurrentReservationsNeverExceedCap() {
        val cap = 10
        val threads = 100
        val limiter = IpConnectionLimiter(maxPerIp = cap)
        val admitted = AtomicInteger(0)
        val barrier = CyclicBarrier(threads)
        val workers = (1..threads).map {
            Thread {
                barrier.await()  // maximize contention on the same instant
                if (limiter.tryReserve("same-ip")) admitted.incrementAndGet()
            }.apply { start() }
        }
        workers.forEach { it.join() }
        assertEquals(cap, admitted.get(), "exactly cap reservations should be admitted under contention")
        assertEquals(cap, limiter.activeFor("same-ip"))
    }
}
