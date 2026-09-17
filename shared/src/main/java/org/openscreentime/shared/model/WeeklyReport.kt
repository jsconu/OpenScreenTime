package org.openscreentime.shared.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** One app's total usage within a single [WeekSummary]. */
data class AppWeekUsage(
    val packageName: String,
    val appName: String,
    val totalMs: Long
)

/**
 * One rolling 7-day bucket - [weekStartDate] is the oldest day in it, [weekEndDate] the
 * newest (today itself, for the most recent bucket, even though today isn't over yet -
 * the reporting screen this feeds is meant to answer "how's this week going," the same
 * question Apple's and Android's own screen-time reports answer, not just "how did last
 * week go"). [byApp] is sorted by [AppWeekUsage.totalMs] descending.
 */
data class WeekSummary(
    val weekStartDate: String,
    val weekEndDate: String,
    val totalScreenTimeMs: Long,
    val averageDailyScreenTimeMs: Long,
    val byApp: List<AppWeekUsage>
)

/** Most-recent-first: `weeks.first()` is the current rolling week. */
data class WeeklyReport(val weeks: List<WeekSummary>)

private const val DAYS_PER_WEEK = 7

/**
 * See #29 - a parent-facing reporting screen modeled loosely on Apple's and Android's own
 * screen-time reports: rolling 7-day buckets rather than calendar weeks (so "this week"
 * always means "the last 7 days," not a partial week early on), bounded to [weeks] buckets
 * rather than a caller-chosen or unbounded range - a fixed, short lookback is deliberate
 * (see the design discussion on #29): enough to see a real trend, not an archive to pore
 * over. [dailyStats] should include today's (possibly still-live) entry if the caller wants
 * the current week's total to reflect today-so-far, the same way the calm status icon does;
 * a day missing from [dailyStats] simply contributes zero, it doesn't break anything the
 * way a missing day breaks [computeStreak].
 */
fun computeWeeklyReport(dailyStats: List<DailyStats>, weeks: Int = 4): WeeklyReport {
    val byDate = dailyStats.associateBy { it.date }
    val calendar = Calendar.getInstance()
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    val weekSummaries = (0 until weeks).map {
        var weekTotalMs = 0L
        val appTotals = LinkedHashMap<String, AppWeekUsage>()
        var weekEndDate = ""
        var weekStartDate = ""
        for (dayOffset in 0 until DAYS_PER_WEEK) {
            val date = format.format(calendar.time)
            if (dayOffset == 0) weekEndDate = date
            weekStartDate = date
            byDate[date]?.let { stats ->
                weekTotalMs += stats.totalScreenTimeMs
                for (app in stats.appUsage) {
                    val existing = appTotals[app.packageName]
                    appTotals[app.packageName] = AppWeekUsage(
                        packageName = app.packageName,
                        appName = app.appName,
                        totalMs = (existing?.totalMs ?: 0L) + app.foregroundTimeMs
                    )
                }
            }
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }
        WeekSummary(
            weekStartDate = weekStartDate,
            weekEndDate = weekEndDate,
            totalScreenTimeMs = weekTotalMs,
            averageDailyScreenTimeMs = weekTotalMs / DAYS_PER_WEEK,
            byApp = appTotals.values.sortedByDescending { it.totalMs }
        )
    }
    return WeeklyReport(weekSummaries)
}
