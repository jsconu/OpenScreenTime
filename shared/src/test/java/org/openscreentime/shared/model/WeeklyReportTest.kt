package org.openscreentime.shared.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WeeklyReportTest {

    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** [daysAgo] = 0 means today, 1 means yesterday, etc. */
    private fun dateDaysAgo(daysAgo: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
        return format.format(calendar.time)
    }

    @Test
    fun `no history still produces the requested number of empty weeks`() {
        val report = computeWeeklyReport(emptyList(), weeks = 4)
        assertEquals(4, report.weeks.size)
        assertEquals(0L, report.weeks[0].totalScreenTimeMs)
        assertEquals(emptyList<AppWeekUsage>(), report.weeks[0].byApp)
    }

    @Test
    fun `the current week includes today, not just completed days`() {
        val stats = listOf(DailyStats(date = dateDaysAgo(0), totalScreenTimeMs = 30 * 60_000L))
        val report = computeWeeklyReport(stats, weeks = 4)
        assertEquals(30 * 60_000L, report.weeks[0].totalScreenTimeMs)
    }

    @Test
    fun `a week sums every day within its rolling 7-day window`() {
        val stats = (0..6).map { DailyStats(date = dateDaysAgo(it), totalScreenTimeMs = 10 * 60_000L) }
        val report = computeWeeklyReport(stats, weeks = 4)
        assertEquals(70 * 60_000L, report.weeks[0].totalScreenTimeMs)
        assertEquals(10 * 60_000L, report.weeks[0].averageDailyScreenTimeMs)
    }

    @Test
    fun `a day just outside the 7-day window falls into the next week instead`() {
        val stats = listOf(
            DailyStats(date = dateDaysAgo(6), totalScreenTimeMs = 10 * 60_000L), // last day of week 0
            DailyStats(date = dateDaysAgo(7), totalScreenTimeMs = 20 * 60_000L) // first day of week 1
        )
        val report = computeWeeklyReport(stats, weeks = 4)
        assertEquals(10 * 60_000L, report.weeks[0].totalScreenTimeMs)
        assertEquals(20 * 60_000L, report.weeks[1].totalScreenTimeMs)
    }

    @Test
    fun `weeks are ordered most-recent-first`() {
        val stats = listOf(
            DailyStats(date = dateDaysAgo(0), totalScreenTimeMs = 5 * 60_000L),
            DailyStats(date = dateDaysAgo(7), totalScreenTimeMs = 15 * 60_000L),
            DailyStats(date = dateDaysAgo(14), totalScreenTimeMs = 25 * 60_000L)
        )
        val report = computeWeeklyReport(stats, weeks = 4)
        assertEquals(dateDaysAgo(0), report.weeks[0].weekEndDate)
        assertEquals(dateDaysAgo(6), report.weeks[0].weekStartDate)
        assertEquals(5 * 60_000L, report.weeks[0].totalScreenTimeMs)
        assertEquals(15 * 60_000L, report.weeks[1].totalScreenTimeMs)
        assertEquals(25 * 60_000L, report.weeks[2].totalScreenTimeMs)
    }

    @Test
    fun `a missing day within a week contributes zero rather than breaking the week`() {
        val stats = listOf(
            DailyStats(date = dateDaysAgo(0), totalScreenTimeMs = 10 * 60_000L)
            // every other day in this week is missing entirely
        )
        val report = computeWeeklyReport(stats, weeks = 1)
        assertEquals(10 * 60_000L, report.weeks[0].totalScreenTimeMs)
    }

    @Test
    fun `per-app usage sums across every day in the week, keyed by package`() {
        val stats = listOf(
            DailyStats(
                date = dateDaysAgo(0),
                appUsage = listOf(AppUsage("com.a", "App A", 10 * 60_000L), AppUsage("com.b", "App B", 5 * 60_000L))
            ),
            DailyStats(
                date = dateDaysAgo(1),
                appUsage = listOf(AppUsage("com.a", "App A", 20 * 60_000L))
            )
        )
        val report = computeWeeklyReport(stats, weeks = 1)
        assertEquals(
            listOf(AppWeekUsage("com.a", "App A", 30 * 60_000L), AppWeekUsage("com.b", "App B", 5 * 60_000L)),
            report.weeks[0].byApp
        )
    }

    @Test
    fun `by-app entries are sorted by total time descending`() {
        val stats = listOf(
            DailyStats(
                date = dateDaysAgo(0),
                appUsage = listOf(
                    AppUsage("com.small", "Small", 5 * 60_000L),
                    AppUsage("com.big", "Big", 50 * 60_000L),
                    AppUsage("com.medium", "Medium", 20 * 60_000L)
                )
            )
        )
        val report = computeWeeklyReport(stats, weeks = 1)
        assertEquals(listOf("com.big", "com.medium", "com.small"), report.weeks[0].byApp.map { it.packageName })
    }
}
