package org.openscreentime.parent.data

import android.content.Context
import org.openscreentime.shared.model.currentDayIndex

/**
 * Local-only count of how many times *this parent, on this device* has opened a weekly
 * report today - see #30. Never synced to Firestore: this is about nudging one parent's
 * own checking habit, not family state, and it's a global count across every child rather
 * than one per child, since the thing being nudged is "checking reports a lot today," not
 * "checking any single kid's report a lot." Resets automatically once the day index rolls
 * over, the same [currentDayIndex] convention [TipsStore] uses.
 */
class ReportOpenTracker(context: Context) {
    private val prefs = context.getSharedPreferences("report_open_tracker", Context.MODE_PRIVATE)

    /** Opens recorded so far today - zero once the day index has moved on. */
    val todayOpenCount: Int
        get() = if (prefs.getInt(KEY_DAY_INDEX, -1) == currentDayIndex()) prefs.getInt(KEY_COUNT, 0) else 0

    fun recordOpen() {
        prefs.edit()
            .putInt(KEY_DAY_INDEX, currentDayIndex())
            .putInt(KEY_COUNT, todayOpenCount + 1)
            .apply()
    }

    private companion object {
        const val KEY_DAY_INDEX = "dayIndex"
        const val KEY_COUNT = "count"
    }
}
