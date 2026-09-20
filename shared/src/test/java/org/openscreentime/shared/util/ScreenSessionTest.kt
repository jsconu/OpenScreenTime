package org.openscreentime.shared.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins how unlocking and the screen turning off shape a day's screen time. */
class ScreenSessionTest {

    private val minute = 60_000L

    private class Clock(var nowMs: Long = 1_000_000L)

    private fun ledger(clock: Clock) =
        DayLedger(InMemoryKeyValueStore(), nowMs = { clock.nowMs }, todayString = { "2026-09-20" })

    @Test
    fun `unlocking starts a session and counts an unlock`() {
        val clock = Clock()
        val ledger = ledger(clock)
        ledger.recordScreenEvent(ScreenEvent.UNLOCKED, trackUnlocks = false, nowMs = clock.nowMs)
        clock.nowMs += 5 * minute
        assertEquals(1, ledger.unlockCount)
        assertEquals(5 * minute, ledger.liveTotalScreenTimeMs)
    }

    @Test
    fun `the screen turning off folds the session into the day`() {
        val clock = Clock()
        val ledger = ledger(clock)
        ledger.recordScreenEvent(ScreenEvent.UNLOCKED, trackUnlocks = false, nowMs = clock.nowMs)
        clock.nowMs += 12 * minute
        ledger.recordScreenEvent(ScreenEvent.SCREEN_OFF, trackUnlocks = false, nowMs = clock.nowMs)
        clock.nowMs += 30 * minute
        assertEquals(12 * minute, ledger.liveTotalScreenTimeMs)
    }

    @Test
    fun `waiting for the first app happens only while unlock tracking is on`() {
        val clock = Clock()
        val off = ledger(clock)
        off.recordScreenEvent(ScreenEvent.UNLOCKED, trackUnlocks = false, nowMs = clock.nowMs)
        assertNull(off.unlockAwaitingMs)

        val on = ledger(clock)
        on.recordScreenEvent(ScreenEvent.UNLOCKED, trackUnlocks = true, nowMs = clock.nowMs)
        assertEquals(clock.nowMs, on.unlockAwaitingMs)
    }

    @Test
    fun `each unlock is counted`() {
        val clock = Clock()
        val ledger = ledger(clock)
        repeat(3) {
            ledger.recordScreenEvent(ScreenEvent.UNLOCKED, trackUnlocks = false, nowMs = clock.nowMs)
            ledger.recordScreenEvent(ScreenEvent.SCREEN_OFF, trackUnlocks = false, nowMs = clock.nowMs)
        }
        assertEquals(3, ledger.unlockCount)
    }

    @Test
    fun `a screen-off with no session open changes nothing`() {
        val clock = Clock()
        val ledger = ledger(clock)
        ledger.recordScreenEvent(ScreenEvent.SCREEN_OFF, trackUnlocks = false, nowMs = clock.nowMs)
        assertEquals(0L, ledger.liveTotalScreenTimeMs)
        assertEquals(0, ledger.unlockCount)
    }
}
