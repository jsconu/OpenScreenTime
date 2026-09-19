package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTextAndInstalledAppsTest {

    @Test
    fun `each tier has its message`() {
        assertEquals("Great job! You're on track.", statusNotificationMessage(StatusTier.GOOD))
        assertEquals("Slow down", statusNotificationMessage(StatusTier.CAUTION))
        assertEquals("You've reached your overall screen time limit", statusNotificationMessage(StatusTier.STOP))
    }

    @Test
    fun `a lock or bedtime stop does not claim the limit was reached`() {
        assertEquals(
            "Screen time is paused right now",
            statusNotificationMessage(StatusTier.STOP, pausedByLockOrBedtime = true)
        )
        // The pause wording only ever replaces the stop message.
        assertEquals("Slow down", statusNotificationMessage(StatusTier.CAUTION, pausedByLockOrBedtime = true))
    }

    @Test
    fun `the title and location text are what the apps promise`() {
        assertEquals("Screen Time Status", STATUS_NOTIFICATION_TITLE)
        assertTrue(STATUS_ICON_LOCATION_TEXT.contains("top-left"))
    }

    @Test
    fun `installed apps are added with zero time and used apps keep their time`() {
        val merged = mergeUsageWithInstalled(
            usage = listOf(AppUsage("a.used", "Used", 90_000)),
            installed = listOf(InstalledApp("a.used", "Used"), InstalledApp("b.idle", "Idle"), InstalledApp("c.alpha", "Alpha"))
        )
        assertEquals(listOf("a.used", "c.alpha", "b.idle"), merged.map { it.packageName })
        assertEquals(90_000L, merged.first().foregroundTimeMs)
        assertEquals(0L, merged.last().foregroundTimeMs)
    }

    @Test
    fun `an app with usage but no launcher entry still appears`() {
        val merged = mergeUsageWithInstalled(listOf(AppUsage("gone.app", "Gone", 5_000)), emptyList())
        assertEquals(listOf("gone.app"), merged.map { it.packageName })
    }

    @Test
    fun `a blank label falls back to the package name`() {
        val merged = mergeUsageWithInstalled(emptyList(), listOf(InstalledApp("x.y", " ")))
        assertEquals("x.y", merged.single().appName)
    }
}
