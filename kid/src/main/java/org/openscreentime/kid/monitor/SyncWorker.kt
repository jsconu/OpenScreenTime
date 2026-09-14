package org.openscreentime.kid.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.shared.model.AppUsage
import org.openscreentime.shared.model.DailyStats

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
            lastSyncedAtMs = System.currentTimeMillis()
        )

        val repository = (applicationContext as KidApp).repository
        return try {
            repository.pushDailyStats(parentUid, childId, stats)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
