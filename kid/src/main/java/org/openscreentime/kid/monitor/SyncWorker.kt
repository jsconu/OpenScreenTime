package org.openscreentime.kid.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.shared.model.AppUsage
import org.openscreentime.shared.model.DailyStats
import org.openscreentime.shared.model.toAppCounts
import org.openscreentime.shared.util.listLaunchableApps

/** Periodically pushes the on-device usage snapshot up to Firestore. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pairingStore = PairingStore(applicationContext)
        val parentUid = pairingStore.parentUid ?: return Result.success()
        val childId = pairingStore.childId ?: return Result.success()

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
            // See #35 - only uploaded while a parent has the matching tracking toggle on, so
            // turning one off also stops any counts already on the device from being sent.
            notificationCount = if (LiveChildState.trackNotifications) usageStore.notificationCount else 0,
            notificationsByApp = if (LiveChildState.trackNotifications) {
                toAppCounts(usageStore.notificationCountsByApp, usageStore.appNames)
            } else {
                emptyList()
            },
            unlockFirstApps = if (LiveChildState.trackUnlocks) {
                toAppCounts(usageStore.firstAppsAfterUnlock, usageStore.appNames)
            } else {
                emptyList()
            },
            // See #41 - only while a parent has website tracking on for this phone.
            websiteCounts = if (LiveChildState.trackWebsites) toAppCounts(usageStore.websiteCounts, emptyMap()) else emptyList()
        )

        val repository = (applicationContext as KidApp).repository
        return try {
            repository.pushDailyStats(parentUid, childId, stats)
            pushInstalledAppsIfChanged(repository, parentUid, childId)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    /**
     * Publishes the phone's launchable apps so the parent app can list them all. Only when the list
     * changed (or hasn't been re-sent for a week), so this is rare, and never fails the usage sync
     * above if it can't go through.
     */
    private suspend fun pushInstalledAppsIfChanged(
        repository: org.openscreentime.shared.repo.FamilyRepository,
        parentUid: String,
        childId: String
    ) {
        val apps = listLaunchableApps(applicationContext).sortedBy { it.packageName }
        if (apps.isEmpty()) return
        val fingerprint = apps.joinToString("|") { it.packageName + "=" + it.label }.hashCode()
        val prefs = applicationContext.getSharedPreferences("installed_apps_sync", Context.MODE_PRIVATE)
        val unchanged = prefs.getInt("fingerprint", 0) == fingerprint && prefs.getString("child", null) == childId
        val stale = System.currentTimeMillis() - prefs.getLong("sentAtMs", 0L) > WEEK_MS
        if (unchanged && !stale) return
        try {
            repository.pushInstalledApps(parentUid, childId, apps.take(MAX_APPS))
            prefs.edit()
                .putInt("fingerprint", fingerprint)
                .putString("child", childId)
                .putLong("sentAtMs", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            // Try again on the next run.
        }
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
        /** Matches the cap in firestore.rules. */
        const val MAX_APPS = 500
    }
}
