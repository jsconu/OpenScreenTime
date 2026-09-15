package org.openscreentime.shared.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Consecutive recently-completed days (not including today, which isn't over
 * yet) that stayed under the currently-configured daily limit and unlock goal
 * (see #13). This is v1: it checks each past day against *today's* configured
 * limit/goal rather than reconstructing what was configured on that day, so a
 * recent limit change can make the streak read slightly generously or
 * conservatively for a few days - an acceptable simplification per the issue.
 *
 * A day with no synced data at all doesn't count as "under the limit" (we
 * can't confirm it), so it breaks the streak rather than being skipped.
 *
 * This is display-only positive reinforcement: it only ever counts up from
 * zero. There is deliberately no equivalent "days over limit" or "broken
 * streak" computation anywhere - see the design-principle check on #13.
 */
fun computeStreak(
    recentStats: List<DailyStats>,
    dailyLimitMinutes: Int,
    dailyUnlockGoal: Int?
): Int {
    val byDate = recentStats.associateBy { it.date }
    val limitMs = dailyLimitMinutes * 60_000L
    val calendar = Calendar.getInstance()
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    var streak = 0
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    while (true) {
        val date = format.format(calendar.time)
        val stats = byDate[date] ?: break
        val underTime = stats.totalScreenTimeMs <= limitMs
        val underUnlocks = dailyUnlockGoal == null || stats.unlockCount <= dailyUnlockGoal
        if (!underTime || !underUnlocks) break
        streak++
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}
