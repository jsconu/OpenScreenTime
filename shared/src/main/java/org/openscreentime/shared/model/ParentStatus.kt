package org.openscreentime.shared.model

/**
 * Same calm, three-tier framing as the status notification icon (#9) - a kid sees their
 * linked parent's own screen-time status the same non-precise way, never raw numbers, so
 * this can't itself become something to compulsively check. See #18.
 */
fun calmParentStatusLabel(profile: ChildProfile, stats: DailyStats): String {
    val limitMs = profile.dailyLimitMinutes * 60_000L
    val timeRatio = if (limitMs > 0) stats.totalScreenTimeMs.toFloat() / limitMs else 0f
    val goal = profile.dailyUnlockGoal
    val unlockRatio = if (goal != null && goal > 0) stats.unlockCount.toFloat() / goal else 0f
    val ratio = maxOf(timeRatio, unlockRatio)
    return when {
        profile.locked || ratio >= 1f -> "Paused for today"
        ratio >= 0.7f -> "Getting close to their goal"
        else -> "On track today"
    }
}
