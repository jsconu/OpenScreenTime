package org.openscreentime.parent.data

import android.content.Context

/**
 * Local-only, per-day state for the tip card (see #17) - just whether *today's* tip has
 * been checked off, nothing more. No running count, no history: when the day advances,
 * yesterday's checked value simply stops matching the new day index and the card resets
 * on its own, with nothing stored about days that came and went unacknowledged. Matches
 * the non-punitive rule from #13 - there is no way for this data to ever represent "you
 * missed a day."
 */
class TipsStore(context: Context) {
    private val prefs = context.getSharedPreferences("tips", Context.MODE_PRIVATE)

    var acknowledgedDayIndex: Int?
        get() = if (prefs.contains("acknowledgedDayIndex")) prefs.getInt("acknowledgedDayIndex", 0) else null
        set(value) {
            prefs.edit().apply {
                if (value == null) remove("acknowledgedDayIndex") else putInt("acknowledgedDayIndex", value)
            }.apply()
        }
}
