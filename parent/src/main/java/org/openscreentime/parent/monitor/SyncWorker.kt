package org.openscreentime.parent.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.Backend
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.shared.util.buildDailyStats
import org.openscreentime.shared.util.trackingChoices

/** Periodically saves the parent's own on-device usage snapshot: to Firestore in a cloud build, to this
 *  phone's own day history in a local one (see LocalFamilyRepository). */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = (applicationContext as ParentApp).repository
        val (parentUid, childId) = Backend.profileIds(applicationContext) ?: return Result.success()

        val stats = buildDailyStats(UsageStore(applicationContext), SelfDeviceState.trackingChoices(), System.currentTimeMillis())

        return try {
            repository.pushDailyStats(parentUid, childId, stats)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
