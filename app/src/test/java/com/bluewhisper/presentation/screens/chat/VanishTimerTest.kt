package com.bluewhisper.presentation.screens.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD W9: vanish-timer countdown — verifies FR-07.2 / FR-07.3.
 *  - Counts down to zero.
 *  - Fires onZero exactly once.
 *  - Survives a no-op start for the same fileId.
 *  - Cancel for the active fileId stops the timer; cancel for a different
 *    fileId is a no-op (so closing the viewer never aborts the wrong file).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VanishTimerTest {

    @Test fun `ticks from 10 to 0`() = runTest {
        val timer = VanishTimer(this, tickMillis = 1_000L, totalSeconds = 10)
        val ticks = mutableListOf<Int>()
        var zeroFired = 0
        timer.start("f1", onTick = { ticks += it }, onZero = { zeroFired++ })

        // First call (the immediate onTick at start) is 10
        assertEquals(10, ticks.first())

        advanceTimeBy(10_000L)
        advanceUntilIdle()

        // We expect the inclusive sequence 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0
        assertEquals(listOf(10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0), ticks)
        assertEquals(1, zeroFired)
        assertNull(timer.activeFileId)
    }

    @Test fun `cancelFor active fileId stops the timer`() = runTest {
        val timer = VanishTimer(this, tickMillis = 1_000L, totalSeconds = 10)
        var zeroFired = 0
        timer.start("f1", onTick = { /* ignore */ }, onZero = { zeroFired++ })
        advanceTimeBy(3_000L)
        timer.cancelFor("f1")
        advanceTimeBy(15_000L)
        advanceUntilIdle()
        assertEquals(0, zeroFired)
        assertNull(timer.activeFileId)
        assertFalse(timer.isRunning())
    }

    @Test fun `cancelFor different fileId leaves timer alone`() = runTest {
        val timer = VanishTimer(this, tickMillis = 1_000L, totalSeconds = 10)
        var zeroFired = 0
        timer.start("f1", onTick = {}, onZero = { zeroFired++ })
        advanceTimeBy(2_000L)
        timer.cancelFor("OTHER")
        advanceTimeBy(10_000L)
        advanceUntilIdle()
        assertEquals(1, zeroFired)
    }

    @Test fun `start for the same active fileId is idempotent`() = runTest {
        val timer = VanishTimer(this, tickMillis = 1_000L, totalSeconds = 10)
        val ticks = mutableListOf<Int>()
        var zeroFired = 0
        timer.start("f1", onTick = { ticks += it }, onZero = { zeroFired++ })
        advanceTimeBy(2_000L)
        // Re-start with same fileId — should NOT reset ticks back to 10.
        timer.start("f1", onTick = { ticks += it }, onZero = { zeroFired++ })
        advanceTimeBy(10_000L)
        advanceUntilIdle()
        // Should still hit 0 exactly once.
        assertEquals(1, zeroFired)
    }

    @Test fun `start for new fileId cancels old`() = runTest {
        val timer = VanishTimer(this, tickMillis = 1_000L, totalSeconds = 10)
        var firstZero = 0
        var secondZero = 0
        timer.start("f1", onTick = {}, onZero = { firstZero++ })
        advanceTimeBy(2_000L)
        timer.start("f2", onTick = {}, onZero = { secondZero++ })
        advanceTimeBy(15_000L)
        advanceUntilIdle()
        assertEquals(0, firstZero)
        assertEquals(1, secondZero)
    }

    @Test fun `cancel hard-resets state`() = runTest {
        val timer = VanishTimer(this, tickMillis = 1_000L, totalSeconds = 10)
        timer.start("f1", onTick = {}, onZero = {})
        timer.cancel()
        assertFalse(timer.isRunning())
        assertNull(timer.activeFileId)
    }
}
