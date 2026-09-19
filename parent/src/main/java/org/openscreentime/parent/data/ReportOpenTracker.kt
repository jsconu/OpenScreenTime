package org.openscreentime.parent.data

import android.content.Context
import org.openscreentime.shared.model.todayDateString

/**
 * Local-only count of how many times *this parent, on this device* has opened a weekly
 * report today - see #30. Never synced to Firestore: this is about nudging one parent's
 * own checking habit, not family state, and it's a global count across every child rather
 * than one per child, since the thing being nudged is "checking reports a lot today," not
 * "checking any single kid's report a lot." Resets at the parent's local midnight (keyed by
 * [todayDateString], like the rest of the report) - not UTC midnight, which would reset the
 * count mid-evening for anyone west of Greenwich.
 */
class ReportOpenTracker(context: Context) {
    private val prefs = context.getSharedPreferences("report_open_tracker", Context.MODE_PRIVATE)

    /** Opens recorded so far today - zero once the local date has moved on. */
    val todayOpenCount: Int
        get() = if (prefs.getString(KEY_DATE, null) == todayDateString()) prefs.getInt(KEY_COUNT, 0) else 0

    fun recordOpen() {
        prefs.edit()
            .putString(KEY_DATE, todayDateString())
            .putInt(KEY_COUNT, todayOpenCount + 1)
            .apply()
    }

    private companion object {
        const val KEY_DATE = "date"
        const val KEY_COUNT = "count"
    }
}
