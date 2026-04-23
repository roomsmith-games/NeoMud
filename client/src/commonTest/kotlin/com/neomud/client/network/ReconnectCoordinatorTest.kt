package com.neomud.client.network

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectCoordinatorTest {

    @Test
    fun `succeeds on first attempt and transitions idle, reconnecting, idle`() = runTest {
        val coord = ReconnectCoordinator(maxAttempts = 5, intervalMs = 1000)
        assertEquals(ReconnectStatus.Idle, coord.status.value)

        coord.launch(this) { true }
        runCurrent()

        // Status flips to Reconnecting(1, 5) before the first delay.
        assertEquals(ReconnectStatus.Reconnecting(1, 5), coord.status.value)

        advanceTimeBy(1000)
        runCurrent()
        // Attempt returned true — back to Idle.
        assertEquals(ReconnectStatus.Idle, coord.status.value)
    }

    @Test
    fun `succeeds on third attempt`() = runTest {
        val coord = ReconnectCoordinator(maxAttempts = 10, intervalMs = 1000)
        var calls = 0
        coord.launch(this) {
            calls++
            calls == 3
        }
        runCurrent()

        assertEquals(ReconnectStatus.Reconnecting(1, 10), coord.status.value)
        advanceTimeBy(1000); runCurrent() // attempt 1 fails
        assertEquals(ReconnectStatus.Reconnecting(2, 10), coord.status.value)
        advanceTimeBy(1000); runCurrent() // attempt 2 fails
        assertEquals(ReconnectStatus.Reconnecting(3, 10), coord.status.value)
        advanceTimeBy(1000); runCurrent() // attempt 3 succeeds
        assertEquals(ReconnectStatus.Idle, coord.status.value)
        assertEquals(3, calls)
    }

    @Test
    fun `fails after exhausting maxAttempts and emits Failed`() = runTest {
        val coord = ReconnectCoordinator(maxAttempts = 3, intervalMs = 1000)
        coord.launch(this) { false }
        advanceUntilIdle()
        assertEquals(ReconnectStatus.Failed, coord.status.value)
    }

    @Test
    fun `exceptions in attempt are treated as failures and the loop continues`() = runTest {
        val coord = ReconnectCoordinator(maxAttempts = 3, intervalMs = 500)
        var calls = 0
        coord.launch(this) {
            calls++
            if (calls < 3) throw RuntimeException("connect failed")
            true
        }
        advanceUntilIdle()
        assertEquals(ReconnectStatus.Idle, coord.status.value)
        assertEquals(3, calls)
    }

    @Test
    fun `cancel mid-loop resets to Idle and stops further attempts`() = runTest {
        val coord = ReconnectCoordinator(maxAttempts = 10, intervalMs = 1000)
        var calls = 0
        coord.launch(this) {
            calls++
            false
        }
        // Let attempts 1 and 2 fire.
        advanceTimeBy(2500); runCurrent()
        assertIs<ReconnectStatus.Reconnecting>(coord.status.value)
        coord.cancel()
        assertEquals(ReconnectStatus.Idle, coord.status.value)
        advanceUntilIdle()
        // No more calls after cancel.
        val finalCount = calls
        advanceTimeBy(10_000); runCurrent()
        assertEquals(finalCount, calls)
    }

    @Test
    fun `a new launch() cancels any in-flight loop`() = runTest {
        val coord = ReconnectCoordinator(maxAttempts = 10, intervalMs = 1000)
        var firstCalls = 0
        var secondCalls = 0

        coord.launch(this) { firstCalls++; false }
        advanceTimeBy(2500); runCurrent()
        val firstCountAtSwitch = firstCalls

        coord.launch(this) { secondCalls++; true }
        advanceTimeBy(1000); runCurrent()
        assertEquals(ReconnectStatus.Idle, coord.status.value)
        assertEquals(1, secondCalls)
        // First loop must not have fired again after it was replaced.
        advanceTimeBy(5000); runCurrent()
        assertEquals(firstCountAtSwitch, firstCalls)
    }
}
