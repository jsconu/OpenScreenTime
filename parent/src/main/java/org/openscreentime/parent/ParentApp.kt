package org.openscreentime.parent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import org.openscreentime.parent.data.SelfProfileStore
import org.openscreentime.parent.monitor.AppLimitAccessibilityService
import org.openscreentime.parent.monitor.SyncWorker
import org.openscreentime.parent.ui.BlockOverlayActivity
import org.openscreentime.shared.model.BlockReason
import org.openscreentime.shared.repo.FamilyRepository
import java.util.concurrent.TimeUnit

class ParentApp : Application() {
    val repository by lazy { FamilyRepository() }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            // Must happen before FamilyRepository's lazy init ever touches Firebase.
            FirebaseFirestore.getInstance().useEmulator("10.0.2.2", 8080)
            FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
        }
        // Never report CI/E2E-emulator crashes to the real Crashlytics dashboard (see #21) -
        // same flag that already points Firebase itself at the local emulator suite.
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.USE_FIREBASE_EMULATOR)
        createNotificationChannels()
        scheduleSelfSync()
        startSelfTrackingListener()
    }

    private fun scheduleSelfSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "self_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
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

    /**
     * Keeps the in-memory limits/lock state used by the self-tracking accessibility service
     * up to date with Firestore - the parent-app equivalent of the kid app's
     * startLimitsListener(). Safe to call more than once (e.g. right after starting
     * self-tracking, so the listener starts without needing an app restart).
     */
    fun startSelfTrackingListener() {
        val childId = SelfProfileStore(this).childId ?: return
        val parentUid = repository.currentUid ?: return

        repository.listenChild(parentUid, childId) { child ->
            AppLimitAccessibilityService.limitsCache = child.appLimits
            AppLimitAccessibilityService.dailyLimitMinutes = child.dailyLimitMinutes
            AppLimitAccessibilityService.dailyUnlockGoal = child.dailyUnlockGoal
            AppLimitAccessibilityService.bedtimeStartMinutes = child.bedtimeStartMinutes
            AppLimitAccessibilityService.bedtimeEndMinutes = child.bedtimeEndMinutes

            val wasLocked = AppLimitAccessibilityService.lockedCache
            AppLimitAccessibilityService.lockedCache = child.locked
            if (child.locked && !wasLocked) {
                startActivity(
                    Intent(this, BlockOverlayActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(BlockOverlayActivity.EXTRA_REASON, BlockReason.PARENT_LOCK.wireValue)
                )
            }
        }
    }

    companion object {
        const val MONITOR_CHANNEL_ID = "self_monitor_service"
        const val WARNING_CHANNEL_ID = "self_limit_warnings"
    }
}
