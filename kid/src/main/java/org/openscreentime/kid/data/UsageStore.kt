package org.openscreentime.kid.data

import android.content.Context
import org.openscreentime.shared.util.DailyUsageStore

/** Today's usage on this (a child's) phone. */
class UsageStore(context: Context) : DailyUsageStore(context, "usage")
