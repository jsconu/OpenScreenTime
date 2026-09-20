package org.openscreentime.shared.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins what a phone is allowed to upload, whichever app builds it. */
class DailyStatsBuilderTest {

    private val minute = 60_000L

    private fun ledger(): DayLedger {
        val ledger = DayLedger(InMemoryKeyValueStore(), nowMs = { 5_000_000L }, todayString = { "2026-09-20" })
        ledger.cacheAppName("com.example.game", "Game")
        ledger.addForegroundTime("com.example.game", 20 * minute, countsTowardTotal = true)
        ledger.addForegroundTime("com.example.audiobook", 10 * minute, countsTowardTotal = false)
        ledger.addScreenTime(30 * minute)
        ledger.incrementUnlockCount()
        ledger.incrementUnlockCount()
        ledger.recordNotification("com.example.game")
        ledger.recordNotification("com.example.game")
        ledger.markUnlockAwaitingFirstApp(1L)
        ledger.recordFirstAppAfterUnlock("com.example.game")
        ledger.addWebsiteCounts(mapOf("example.com" to 3))
        return ledger
    }

    @Test
    fun `the basics are always sent`() {
        val stats = buildDailyStats(ledger(), TrackingChoices(), nowMs = 7L)
        assertEquals("2026-09-20", stats.date)
        assertEquals(20 * minute, stats.totalScreenTimeMs)
        assertEquals(2, stats.unlockCount)
        assertEquals(7L, stats.lastSyncedAtMs)
        assertEquals(
            listOf("com.example.game" to "Game", "com.example.audiobook" to "com.example.audiobook"),
            stats.appUsage.map { it.packageName to it.appName }
        )
        assertEquals(listOf(20 * minute, 10 * minute), stats.appUsage.map { it.foregroundTimeMs })
    }

    @Test
    fun `with every tracking choice off nothing optional is sent, even if it was collected`() {
        val stats = buildDailyStats(ledger(), TrackingChoices(), nowMs = 0)
        assertEquals(0, stats.notificationCount)
        assertTrue(stats.notificationsByApp.isEmpty())
        assertTrue(stats.unlockFirstApps.isEmpty())
        assertTrue(stats.websiteCounts.isEmpty())
    }

    @Test
    fun `notification counts go only with the notifications choice`() {
        val stats = buildDailyStats(ledger(), TrackingChoices(notifications = true), nowMs = 0)
        assertEquals(2, stats.notificationCount)
        assertEquals(listOf("Game" to 2), stats.notificationsByApp.map { it.appName to it.count })
        assertTrue(stats.unlockFirstApps.isEmpty())
        assertTrue(stats.websiteCounts.isEmpty())
    }

    @Test
    fun `first apps after unlock go only with the unlocks choice`() {
        val stats = buildDailyStats(ledger(), TrackingChoices(unlocks = true), nowMs = 0)
        assertEquals(listOf("Game" to 1), stats.unlockFirstApps.map { it.appName to it.count })
        assertEquals(0, stats.notificationCount)
        assertTrue(stats.websiteCounts.isEmpty())
    }

    @Test
    fun `sites go only with the websites choice, by site name`() {
        val stats = buildDailyStats(ledger(), TrackingChoices(websites = true), nowMs = 0)
        assertEquals(listOf("example.com" to 3), stats.websiteCounts.map { it.packageName to it.count })
        assertEquals(0, stats.notificationCount)
        assertTrue(stats.unlockFirstApps.isEmpty())
    }

    @Test
    fun `a phone's tracking choices come from its device profile`() {
        val state = DeviceProfileState("test")
        state.trackNotifications = true
        state.trackWebsites = true
        assertEquals(TrackingChoices(notifications = true, unlocks = false, websites = true), state.trackingChoices())
    }
}
