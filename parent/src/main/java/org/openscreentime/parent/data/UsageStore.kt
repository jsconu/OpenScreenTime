package org.openscreentime.parent.data

import android.content.Context
import org.openscreentime.shared.util.DailyUsageStore

/** Today's usage on the parent's own phone (self-tracking, see #8). */
class UsageStore(context: Context) : DailyUsageStore(context, "self_usage")
