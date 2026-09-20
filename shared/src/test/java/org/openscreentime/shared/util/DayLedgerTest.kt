package org.openscreentime.shared.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the day's usage rules: midnight, an open session, and time in excluded apps. No device needed. */
class DayLedgerTest {

    private val minute = 60_000L

    private class Clock(var nowMs: Long = 1_000_000L, var date: String = "2026-09-20")

    private fun ledger(clock: Clock = Clock(), store: KeyValueStore = InMemoryKeyValueStore()) =
        DayLedger(store, nowMs = { clock.nowMs }, todayString = { clock.date })

    @Test
    fun `a session in progress counts toward the total, and folds in when the screen turns off`() {
        val clock = Clock()
        val ledger = ledger(clock)
        ledger.startSession()
        clock.nowMs += 10 * minute
        assertEquals(10 * minute, ledger.liveTotalScreenTimeMs)
        ledger.endSessionAndFlush()
        clock.nowMs += 5 * minute
        assertEquals(10 * minute, ledger.liveTotalScreenTimeMs)
        assertEquals(10 * minute, ledger.totalScreenTimeMs)
    }

    @Test
    fun `sessions add up across the day`() {
        val clock = Clock()
        val ledger = ledger(clock)
        repeat(2) {
            ledger.startSession()
            clock.nowMs += 20 * minute
            ledger.endSessionAndFlush()
            clock.nowMs += 60 * minute
        }
        assertEquals(40 * minute, ledger.liveTotalScreenTimeMs)
    }

    @Test
    fun `time in an excluded app comes off the total but stays in that app's usage`() {
        val clock = Clock()
        val ledger = ledger(clock)
        ledger.startSession()
        clock.nowMs += 30 * minute
        ledger.addForegroundTime("com.example.game", 20 * minute, countsTowardTotal = true)
        ledger.addForegroundTime("com.example.audiobook", 10 * minute, countsTowardTotal = false)
        assertEquals(20 * minute, ledger.liveTotalScreenTimeMs)
        assertEquals(mapOf("com.example.game" to 20 * minute, "com.example.audiobook" to 10 * minute), ledger.appUsageMs)
    }

    @Test
    fun `excluded time in an open session never reduces earlier sessions`() {
        val clock = Clock()
        val ledger = ledger(clock)
        ledger.startSession()
        clock.nowMs += 30 * minute
        ledger.endSessionAndFlush()
        clock.nowMs += 10 * minute
        ledger.startSession()
        clock.nowMs += 10 * minute
        ledger.addForegroundTime("com.example.audiobook", 10 * minute, countsTowardTotal = false)
        assertEquals(30 * minute, ledger.liveTotalScreenTimeMs)
    }

    @Test
    fun `excluded time recorded before any screen time never makes the total negative`() {
        val ledger = ledger()
        ledger.addForegroundTime("com.example.audiobook", 5 * minute, countsTowardTotal = false)
        assertEquals(0L, ledger.liveTotalScreenTimeMs)
    }

    @Test
    fun `nothing is added for zero or negative time`() {
        val ledger = ledger()
        ledger.addAppTime("com.example.game", 0)
        ledger.addAppTime("com.example.game", -5)
        ledger.addExcludedTime(-1)
        ledger.addScreenTime(0)
        assertTrue(ledger.appUsageMs.isEmpty())
        assertEquals(0L, ledger.liveTotalScreenTimeMs)
    }

    @Test
    fun `at midnight the day's numbers reset but app names are kept`() {
        val clock = Clock()
        val ledger = ledger(clock)
        ledger.cacheAppName("com.example.game", "Game")
        ledger.addForegroundTime("com.example.game", 15 * minute, countsTowardTotal = true)
        ledger.addScreenTime(15 * minute)
        ledger.addExcludedTime(3 * minute)
        ledger.incrementUnlockCount()
        ledger.markDailyWarned()
        ledger.markAppWarned("com.example.game")
        ledger.markAppPaused("com.example.game")
        ledger.recordNotification("com.example.game")
        ledger.markUnlockAwaitingFirstApp(123L)
        ledger.addWebsiteCounts(mapOf("example.com" to 3))

        clock.date = "2026-09-21"
        assertEquals("2026-09-21", ledger.date)
        assertEquals(0L, ledger.liveTotalScreenTimeMs)
        assertTrue(ledger.appUsageMs.isEmpty())
        assertEquals(0, ledger.unlockCount)
        assertFalse(ledger.dailyWarned)
        assertTrue(ledger.warnedApps.isEmpty())
        assertTrue(ledger.pausedApps.isEmpty())
        assertEquals(0, ledger.notificationCount)
        assertNull(ledger.unlockAwaitingMs)
        assertTrue(ledger.websiteCounts.isEmpty())
        assertEquals(mapOf("com.example.game" to "Game"), ledger.appNames)
    }

    @Test
    fun `warned and paused apps are remembered within the day`() {
        val ledger = ledger()
        ledger.markAppWarned("a")
        ledger.markAppWarned("b")
        ledger.markAppPaused("a")
        ledger.markDailyWarned()
        assertEquals(setOf("a", "b"), ledger.warnedApps)
        assertEquals(setOf("a"), ledger.pausedApps)
        assertTrue(ledger.dailyWarned)
    }

    @Test
    fun `the first app after an unlock is recorded once and stops the wait`() {
        val ledger = ledger()
        ledger.markUnlockAwaitingFirstApp(500L)
        assertEquals(500L, ledger.unlockAwaitingMs)
        ledger.recordFirstAppAfterUnlock("com.example.social")
        assertNull(ledger.unlockAwaitingMs)
        ledger.markUnlockAwaitingFirstApp(900L)
        ledger.recordFirstAppAfterUnlock("com.example.social")
        assertEquals(mapOf("com.example.social" to 2), ledger.firstAppsAfterUnlock)
        ledger.markUnlockAwaitingFirstApp(950L)
        ledger.clearUnlockAwaiting()
        assertNull(ledger.unlockAwaitingMs)
    }

    @Test
    fun `notifications are counted per app`() {
        val ledger = ledger()
        ledger.recordNotification("a")
        ledger.recordNotification("a")
        ledger.recordNotification("b")
        assertEquals(mapOf("a" to 2, "b" to 1), ledger.notificationCountsByApp)
        assertEquals(3, ledger.notificationCount)
    }

    @Test
    fun `website counts are merged across additions`() {
        val ledger = ledger()
        ledger.addWebsiteCounts(mapOf("example.com" to 2))
        ledger.addWebsiteCounts(mapOf("example.com" to 1, "other.org" to 4))
        ledger.addWebsiteCounts(emptyMap())
        assertEquals(mapOf("example.com" to 3, "other.org" to 4), ledger.websiteCounts)
    }

    @Test
    fun `unlocks are counted`() {
        val ledger = ledger()
        repeat(3) { ledger.incrementUnlockCount() }
        assertEquals(3, ledger.unlockCount)
    }

    @Test
    fun `a corrupt saved map reads as empty rather than crashing`() {
        val store = InMemoryKeyValueStore()
        store.edit {
            putString("date", "2026-09-20")
            putString("appUsage", "not json")
        }
        assertTrue(ledger(store = store).appUsageMs.isEmpty())
    }

    @Test
    fun `the saved keys are the ones already on people's phones`() {
        val store = InMemoryKeyValueStore()
        val ledger = ledger(store = store)
        ledger.addAppTime("a", 1)
        ledger.cacheAppName("a", "A")
        ledger.startSession()
        ledger.markAppWarned("a")
        // Written through the shared seam: read back with the exact saved names.
        assertEquals("2026-09-20", store.getString("date", null))
        assertTrue(store.getString("appUsage", null)!!.contains("\"a\""))
        assertTrue(store.getString("appNames", null)!!.contains("\"A\""))
        assertTrue(store.getLong("sessionStartMs", -1) > 0)
        assertEquals(setOf("a"), store.getStringSet("warnedApps"))
        assertEquals(0L, store.getLong("excludedMs", -1))
    }
}
