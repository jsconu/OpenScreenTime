package org.openscreentime.kid.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.Backend
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.shared.util.buildDailyStats
import org.openscreentime.shared.util.listLaunchableApps
import org.openscreentime.shared.util.trackingChoices

/** Periodically saves the on-device usage snapshot: to Firestore in a cloud build, to this phone's own
 *  day history in a local one (see LocalFamilyRepository). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val (parentUid, childId) = Backend.profileIds(applicationContext) ?: return Result.success()

        val stats = buildDailyStats(UsageStore(applicationContext), LiveChildState.trackingChoices(), System.currentTimeMillis())

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
