package org.openscreentime.shared.util

import org.openscreentime.shared.model.AppUsage
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.toAppCounts

/** The parent-controlled tracking choices in force on a phone (see #35 and #41). All off unless a parent turned one on. */
data class TrackingChoices(
    val notifications: Boolean = false,
    val unlocks: Boolean = false,
    val websites: Boolean = false
)

/** The tracking choices this phone currently has. */
fun DeviceProfileState.trackingChoices() = TrackingChoices(
    notifications = trackNotifications,
    unlocks = trackUnlocks,
    websites = trackWebsites
)

/**
 * The one place that decides what a phone may send to the parent's account: today's counted screen time, unlock
 * count and per-app time always, and each optional category (notification counts, first app after unlock, sites
 * looked up) only while its tracking choice is on. So switching a choice off also stops anything already collected
 * from being sent, and both the kid app and the parent's own phone go through here.
 */
fun buildDailyStats(ledger: DayLedger, tracking: TrackingChoices, nowMs: Long): DailyStats {
    val names = ledger.appNames
    return DailyStats(
        date = ledger.date,
        totalScreenTimeMs = ledger.totalScreenTimeMs,
        unlockCount = ledger.unlockCount,
        appUsage = ledger.appUsageMs.map { (pkg, ms) ->
            AppUsage(packageName = pkg, appName = names[pkg] ?: pkg, foregroundTimeMs = ms)
        },
        lastSyncedAtMs = nowMs,
        notificationCount = if (tracking.notifications) ledger.notificationCount else 0,
        notificationsByApp = if (tracking.notifications) toAppCounts(ledger.notificationCountsByApp, names) else emptyList(),
        unlockFirstApps = if (tracking.unlocks) toAppCounts(ledger.firstAppsAfterUnlock, names) else emptyList(),
        // Site names only, so there is no app name to look up.
        websiteCounts = if (tracking.websites) toAppCounts(ledger.websiteCounts, emptyMap()) else emptyList()
    )
}
