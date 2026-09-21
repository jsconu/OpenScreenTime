package org.openscreentime.parent

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.openscreentime.shared.util.CrashNote
import org.openscreentime.parent.monitor.SelfDeviceState
import org.openscreentime.parent.monitor.SyncWorker
import org.openscreentime.parent.ui.BlockOverlayActivity
import org.openscreentime.parent.ui.FocusLauncherActivity
import org.openscreentime.shared.model.BlockReason
import org.openscreentime.shared.repo.FamilyRepository
import java.util.concurrent.TimeUnit

class ParentApp : Application() {
    val repository: FamilyRepository by lazy { Backend.createRepository(this) }

    override fun onCreate() {
        super.onCreate()
        CrashNote.install(this, "OpenScreenTime Parent", BuildConfig.VERSION_NAME)
        // Whatever this flavor's backend needs before anything touches it - nothing, locally.
        Backend.onAppCreate(this)
        createNotificationChannels()
        // So this phone's own limits still apply after a restart, before Firestore has answered.
        SelfDeviceState.restore(this)
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
        manager.createNotificationChannel(
            NotificationChannel(CALM_CHANNEL_ID, "Calm notifications", NotificationManager.IMPORTANCE_LOW)
        )
    }

    /**
     * Keeps the in-memory limits/lock state used by the self-tracking accessibility service
     * up to date with Firestore - the parent-app equivalent of the kid app's
     * startLimitsListener(). Safe to call more than once (e.g. right after starting
     * self-tracking, so the listener starts without needing an app restart).
     */
    fun startSelfTrackingListener() {
        val (parentUid, childId) = Backend.profileIds(this) ?: return

        repository.listenChild(parentUid, childId) { child ->
            val wasLocked = SelfDeviceState.lockedCache
            // Limits, lock, tracking choices and dumb-phone mode, in memory and saved on the phone.
            SelfDeviceState.update(this, child)
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
        const val CALM_CHANNEL_ID = "calm_summary"
    }
}
