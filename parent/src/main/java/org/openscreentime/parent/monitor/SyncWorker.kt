package org.openscreentime.parent.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.data.SelfProfileStore
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.shared.util.buildDailyStats
import org.openscreentime.shared.util.trackingChoices

/** Periodically pushes the parent's own on-device usage snapshot up to Firestore. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as ParentApp).repository
        val parentUid = repository.currentUid ?: return Result.success()
        val childId = SelfProfileStore(applicationContext).childId ?: return Result.success()

        val stats = buildDailyStats(UsageStore(applicationContext), SelfDeviceState.trackingChoices(), System.currentTimeMillis())

        return try {
            repository.pushDailyStats(parentUid, childId, stats)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
