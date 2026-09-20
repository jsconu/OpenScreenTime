package org.openscreentime.parent.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.data.SelfProfileStore
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.shared.model.AppUsage
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.toAppCounts

/** Periodically pushes the parent's own on-device usage snapshot up to Firestore. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as ParentApp).repository
        val parentUid = repository.currentUid ?: return Result.success()
        val childId = SelfProfileStore(applicationContext).childId ?: return Result.success()

        val usageStore = UsageStore(applicationContext)
        val appUsage = usageStore.appUsageMs.map { (pkg, ms) ->
            AppUsage(packageName = pkg, appName = usageStore.appNames[pkg] ?: pkg, foregroundTimeMs = ms)
        }
        val stats = DailyStats(
            date = usageStore.date,
            totalScreenTimeMs = usageStore.totalScreenTimeMs,
            unlockCount = usageStore.unlockCount,
            appUsage = appUsage,
            lastSyncedAtMs = System.currentTimeMillis(),
            // See #35 - empty unless a parent turned the matching tracking toggle on.
            notificationCount = usageStore.notificationCount,
            notificationsByApp = toAppCounts(usageStore.notificationCountsByApp, usageStore.appNames),
            unlockFirstApps = toAppCounts(usageStore.firstAppsAfterUnlock, usageStore.appNames),
            // See #41 - empty unless website tracking is on for the parent's own profile.
            websiteCounts = if (AppLimitAccessibilityService.trackWebsites) toAppCounts(usageStore.websiteCounts, emptyMap()) else emptyList()
        )

        return try {
            repository.pushDailyStats(parentUid, childId, stats)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
