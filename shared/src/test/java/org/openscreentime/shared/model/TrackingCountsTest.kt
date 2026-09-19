package org.openscreentime.shared.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingCountsTest {

    private val ignored = setOf("com.android.launcher", "com.android.systemui")

    private fun firstApp(pkg: String, unlockedAt: Long?, now: Long) =
        isFirstAppAfterUnlock(pkg, "org.openscreentime.kid", ignored, unlockedAt, now)

    @Test
    fun `an app opened right after an unlock counts as the first app`() {
        assertTrue(firstApp("com.tiktok", unlockedAt = 1_000, now = 5_000))
    }

    @Test
    fun `an app opened well after the unlock window does not count`() {
        assertFalse(firstApp("com.tiktok", unlockedAt = 1_000, now = 1_000 + UNLOCK_FIRST_APP_WINDOW_MS + 1))
    }

    @Test
    fun `the last moment of the window still counts`() {
        assertTrue(firstApp("com.tiktok", unlockedAt = 1_000, now = 1_000 + UNLOCK_FIRST_APP_WINDOW_MS))
    }

    @Test
    fun `landing on the launcher or system UI is not an app`() {
        assertFalse(firstApp("com.android.launcher", unlockedAt = 1_000, now = 2_000))
        assertFalse(firstApp("com.android.systemui", unlockedAt = 1_000, now = 2_000))
    }

    @Test
    fun `this app itself never counts`() {
        assertFalse(firstApp("org.openscreentime.kid", unlockedAt = 1_000, now = 2_000))
    }

    @Test
    fun `nothing counts without a pending unlock`() {
        assertFalse(firstApp("com.tiktok", unlockedAt = null, now = 2_000))
    }

    @Test
    fun `a clock that went backwards never counts`() {
        assertFalse(firstApp("com.tiktok", unlockedAt = 5_000, now = 1_000))
    }

    @Test
    fun `the unlock window expires after the window length`() {
        assertFalse(hasUnlockWindowExpired(1_000, 1_000 + UNLOCK_FIRST_APP_WINDOW_MS))
        assertTrue(hasUnlockWindowExpired(1_000, 1_000 + UNLOCK_FIRST_APP_WINDOW_MS + 1))
    }

    @Test
    fun `app counts are labeled from the name cache and sorted most-first`() {
        val counts = toAppCounts(
            counts = mapOf("com.a" to 2, "com.b" to 9),
            names = mapOf("com.a" to "App A")
        )
        assertEquals(listOf(AppCount("com.b", "com.b", 9), AppCount("com.a", "App A", 2)), counts)
    }

    // --- DailyStats round trip ---

    @Test
    fun `tracking fields survive a DailyStats map round trip`() {
        val stats = DailyStats(
            date = "2026-09-01",
            unlockCount = 5,
            notificationCount = 12,
            notificationsByApp = listOf(AppCount("com.chat", "Chat", 12)),
            unlockFirstApps = listOf(AppCount("com.tiktok", "TikTok", 3))
        )
        // Firestore hands integers back as Long.
        val asStored = stats.toMap().toMutableMap().apply {
            this["notificationCount"] = 12L
            this["notificationsByApp"] = listOf(mapOf("packageName" to "com.chat", "appName" to "Chat", "count" to 12L))
            this["unlockFirstApps"] = listOf(mapOf("packageName" to "com.tiktok", "appName" to "TikTok", "count" to 3L))
        }
        val parsed = DailyStats.fromMap("2026-09-01", asStored)
        assertEquals(12, parsed.notificationCount)
        assertEquals(stats.notificationsByApp, parsed.notificationsByApp)
        assertEquals(stats.unlockFirstApps, parsed.unlockFirstApps)
    }

    @Test
    fun `an older DailyStats document with no tracking fields parses to empty tracking`() {
        val parsed = DailyStats.fromMap("2026-09-01", mapOf("unlockCount" to 4L))
        assertEquals(0, parsed.notificationCount)
        assertEquals(emptyList<AppCount>(), parsed.notificationsByApp)
        assertEquals(emptyList<AppCount>(), parsed.unlockFirstApps)
    }

    // --- Weekly report aggregation ---

    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun dateDaysAgo(daysAgo: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return format.format(calendar.time)
    }

    @Test
    fun `the weekly report sums unlocks and notifications and merges per-app counts`() {
        val stats = listOf(
            DailyStats(
                date = dateDaysAgo(0), unlockCount = 10, notificationCount = 30,
                notificationsByApp = listOf(AppCount("com.chat", "Chat", 30)),
                unlockFirstApps = listOf(AppCount("com.tiktok", "TikTok", 6))
            ),
            DailyStats(
                date = dateDaysAgo(1), unlockCount = 5, notificationCount = 20,
                notificationsByApp = listOf(AppCount("com.chat", "Chat", 15), AppCount("com.mail", "Mail", 5)),
                unlockFirstApps = listOf(AppCount("com.tiktok", "TikTok", 1), AppCount("com.maps", "Maps", 4))
            )
        )
        val week = computeWeeklyReport(stats, weeks = 1).weeks[0]
        assertEquals(15, week.unlockCount)
        assertEquals(50, week.notificationCount)
        assertEquals(
            listOf(AppCount("com.chat", "Chat", 45), AppCount("com.mail", "Mail", 5)),
            week.notificationsByApp
        )
        assertEquals(
            listOf(AppCount("com.tiktok", "TikTok", 7), AppCount("com.maps", "Maps", 4)),
            week.unlockFirstApps
        )
    }

    @Test
    fun `the daily digest lists only days with data, newest first`() {
        val stats = listOf(
            DailyStats(date = dateDaysAgo(2), unlockCount = 3, notificationCount = 7),
            DailyStats(date = dateDaysAgo(0), unlockCount = 9, notificationCount = 1)
        )
        val daily = computeWeeklyReport(stats, weeks = 1).weeks[0].daily
        assertEquals(
            listOf(DaySummary(dateDaysAgo(0), 9, 1), DaySummary(dateDaysAgo(2), 3, 7)),
            daily
        )
    }
}
