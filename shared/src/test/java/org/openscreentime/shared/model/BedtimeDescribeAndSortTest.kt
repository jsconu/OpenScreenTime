package org.openscreentime.shared.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BedtimeDescribeAndSortTest {

    @Test
    fun `an overnight window says tonight and tomorrow morning`() {
        assertEquals(
            "9:00 PM tonight to 7:00 AM tomorrow morning (10 hours)",
            describeBedtimeWindow(21 * 60, 7 * 60)
        )
    }

    @Test
    fun `an overnight window ending after noon says just tomorrow`() {
        assertEquals("10:00 PM tonight to 1:00 PM tomorrow (15 hours)", describeBedtimeWindow(22 * 60, 13 * 60))
    }

    @Test
    fun `a window that stays within one day says the same day`() {
        assertEquals("1:00 AM to 6:30 AM the same day (5 hours 30 min)", describeBedtimeWindow(60, 6 * 60 + 30))
    }

    @Test
    fun `equal start and end is not a window`() {
        assertEquals("", describeBedtimeWindow(600, 600))
    }

    @Test
    fun `duration counts across midnight`() {
        assertEquals(600, bedtimeDurationMinutes(21 * 60, 7 * 60))
        assertEquals(330, bedtimeDurationMinutes(60, 6 * 60 + 30))
    }

    private val apps = listOf(
        AppUsage("b", "banana", 5_000),
        AppUsage("a", "Apple", 0),
        AppUsage("c", "Cherry", 60_000),
        AppUsage("d", "Date", 0)
    )

    @Test
    fun `usage order puts the most used first and breaks ties alphabetically`() {
        assertEquals(listOf("Cherry", "banana", "Apple", "Date"), sortApps(apps, AppSort.USAGE).map { it.appName })
    }

    @Test
    fun `name order ignores case`() {
        assertEquals(listOf("Apple", "banana", "Cherry", "Date"), sortApps(apps, AppSort.NAME).map { it.appName })
    }
}
