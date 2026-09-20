package org.openscreentime.shared.util

import android.content.Context

/**
 * Today's usage on this phone, kept in SharedPreferences under [prefsName]: "usage" on a child's phone and
 * "self_usage" on the parent's own, so what is saved stays exactly where it was. The rules are all in [DayLedger].
 * The friction-pause record is kept by both but only used where a pause screen exists.
 */
open class DailyUsageStore(context: Context, prefsName: String) :
    DayLedger(SharedPrefsStore(context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)))
