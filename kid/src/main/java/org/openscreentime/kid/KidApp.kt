package org.openscreentime.kid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.kid.monitor.AppLimitAccessibilityService
import org.openscreentime.kid.monitor.SyncWorker
import org.openscreentime.kid.ui.BlockOverlayActivity
import org.openscreentime.shared.repo.FamilyRepository
import java.util.concurrent.TimeUnit

class KidApp : Application() {
    val repository by lazy { FamilyRepository() }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            // Must happen before FamilyRepository's lazy init ever touches Firebase.
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
        }
        createNotificationChannels()
        scheduleSync()
        startLimitsListener()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(MONITOR_CHANNEL_ID, "Screen time monitoring", NotificationManager.IMPORTANCE_MIN)
        )
        manager.createNotificationChannel(
            NotificationChannel(WARNING_CHANNEL_ID, "Approaching a limit", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun scheduleSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Keeps the in-memory limits/lock state used by the accessibility service up to date with
     * Firestore. Safe to call more than once (e.g. right after pairing completes, so the
     * listener starts without needing an app restart) - each call just attaches a fresh listener.
     */
    fun startLimitsListener() {
        val pairingStore = PairingStore(this)
        val parentUid = pairingStore.parentUid
        val childId = pairingStore.childId
        if (parentUid == null || childId == null) return

        repository.listenChild(parentUid, childId) { child ->
            AppLimitAccessibilityService.limitsCache = child.appLimits
            AppLimitAccessibilityService.dailyLimitMinutes = child.dailyLimitMinutes

            val wasLocked = AppLimitAccessibilityService.lockedCache
            AppLimitAccessibilityService.lockedCache = child.locked
            if (child.locked && !wasLocked) {
                // Don't wait for the next app switch or tick - interrupt right away.
                startActivity(
                    Intent(this, BlockOverlayActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(BlockOverlayActivity.EXTRA_REASON, "parent_lock")
                )
            }
        }
    }

    companion object {
        const val MONITOR_CHANNEL_ID = "monitor_service"
        const val WARNING_CHANNEL_ID = "limit_warnings"
    }
}
