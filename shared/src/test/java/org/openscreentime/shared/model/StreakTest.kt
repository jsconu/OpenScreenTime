package org.openscreentime.shared.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {

    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** [daysAgo] = 1 means yesterday, 2 means the day before, etc. */
    private fun dateDaysAgo(daysAgo: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return format.format(calendar.time)
    }

    @Test
    fun `no history is a zero streak, not an error`() {
        assertEquals(0, computeStreak(emptyList(), dailyLimitMinutes = 60, dailyUnlockGoal = null))
    }

    @Test
    fun `consecutive days under the limit count up from yesterday`() {
        val stats = listOf(
            DailyStats(date = dateDaysAgo(1), totalScreenTimeMs = 30 * 60_000L),
            DailyStats(date = dateDaysAgo(2), totalScreenTimeMs = 45 * 60_000L),
            DailyStats(date = dateDaysAgo(3), totalScreenTimeMs = 59 * 60_000L)
        )
        assertEquals(3, computeStreak(stats, dailyLimitMinutes = 60, dailyUnlockGoal = null))
    }

    @Test
    fun `a day over the limit breaks the streak instead of being skipped`() {
        val stats = listOf(
            DailyStats(date = dateDaysAgo(1), totalScreenTimeMs = 30 * 60_000L),
            DailyStats(date = dateDaysAgo(2), totalScreenTimeMs = 90 * 60_000L),
            DailyStats(date = dateDaysAgo(3), totalScreenTimeMs = 10 * 60_000L)
        )
        assertEquals(1, computeStreak(stats, dailyLimitMinutes = 60, dailyUnlockGoal = null))
    }

    @Test
    fun `a missing day (no synced data) also breaks the streak rather than passing by default`() {
        val stats = listOf(
            DailyStats(date = dateDaysAgo(1), totalScreenTimeMs = 30 * 60_000L),
            // dateDaysAgo(2) is missing entirely
            DailyStats(date = dateDaysAgo(3), totalScreenTimeMs = 10 * 60_000L)
        )
        assertEquals(1, computeStreak(stats, dailyLimitMinutes = 60, dailyUnlockGoal = null))
    }

    @Test
    fun `an unlock goal over the limit also breaks the streak even if time was fine`() {
        val stats = listOf(
            DailyStats(date = dateDaysAgo(1), totalScreenTimeMs = 10 * 60_000L, unlockCount = 5)
        )
        assertEquals(
            0,
            computeStreak(stats, dailyLimitMinutes = 60, dailyUnlockGoal = 3)
        )
    }

    @Test
    fun `a null unlock goal never affects the streak`() {
        val stats = listOf(
            DailyStats(date = dateDaysAgo(1), totalScreenTimeMs = 10 * 60_000L, unlockCount = 500)
        )
        assertEquals(
            1,
            computeStreak(stats, dailyLimitMinutes = 60, dailyUnlockGoal = null)
        )
    }
}
