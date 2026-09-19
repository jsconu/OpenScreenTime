package org.openscreentime.kid.monitor

import org.openscreentime.kid.data.UsageStore
import org.openscreentime.shared.model.StatusTier
import org.openscreentime.shared.model.computeStatusTier
import org.openscreentime.shared.model.isInBedtimeWindow
import org.openscreentime.shared.model.nowMinutesOfDay

/** This device's calm status right now, and whether a stop is really a lock or bedtime. */
data class CurrentStatus(val tier: StatusTier, val pausedByLockOrBedtime: Boolean)

/**
 * The single place the kid's calm status is worked out. The status-bar icon (set by
 * [AppLimitAccessibilityService]) and the "My screen time" tile on the home screen both call this,
 * so the two can never disagree about thumbs up, open hand or stop.
 */
fun currentStatus(usageStore: UsageStore): CurrentStatus {
    val isInBedtime = isInBedtimeWindow(
        nowMinutesOfDay(), LiveChildState.bedtimeStartMinutes, LiveChildState.bedtimeEndMinutes
    )
    val tier = computeStatusTier(
        locked = LiveChildState.lockedCache,
        isInBedtime = isInBedtime,
        dailyLimitMinutes = LiveChildState.dailyLimitMinutes,
        liveTotalScreenTimeMs = usageStore.liveTotalScreenTimeMs,
        dailyUnlockGoal = LiveChildState.dailyUnlockGoal,
        unlockCount = usageStore.unlockCount
    )
    return CurrentStatus(tier, pausedByLockOrBedtime = LiveChildState.lockedCache || isInBedtime)
}
