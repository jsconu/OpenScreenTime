package org.openscreentime.kid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.kid.monitor.AppLimitAccessibilityService
import org.openscreentime.kid.monitor.SyncWorker
import org.openscreentime.shared.repo.FamilyRepository
import java.util.concurrent.TimeUnit

class KidApp : Application() {
    val repository by lazy { FamilyRepository() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleSync()
        listenForLimitChanges()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            MONITOR_CHANNEL_ID,
            "Screen time monitoring",
            NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun scheduleSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Keeps the in-memory limits used by the accessibility service up to date with Firestore. */
    private fun listenForLimitChanges() {
        val pairingStore = PairingStore(this)
        val parentUid = pairingStore.parentUid
        val childId = pairingStore.childId
        if (parentUid == null || childId == null) return

        repository.listenChild(parentUid, childId) { child ->
            AppLimitAccessibilityService.limitsCache = child.appLimits
            AppLimitAccessibilityService.dailyLimitMinutes = child.dailyLimitMinutes
        }
    }

    companion object {
        const val MONITOR_CHANNEL_ID = "monitor_service"
    }
}
