package org.openscreentime.kid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.openscreentime.kid.nearby.NearbySyncRunner
import org.openscreentime.shared.util.CrashNote
import org.openscreentime.kid.monitor.LiveChildState
import org.openscreentime.kid.ui.FocusLauncherActivity
import org.openscreentime.kid.monitor.SyncWorker
import org.openscreentime.kid.ui.BlockOverlayActivity
import org.openscreentime.shared.model.BlockReason
import org.openscreentime.shared.repo.FamilyRepository
import java.util.concurrent.TimeUnit

class KidApp : Application() {
    val repository: FamilyRepository by lazy { Backend.createRepository(this) }

    /** Listens for the linked parent's phone, so "Sync now" works from their end too. */
    private var nearbyListening: java.io.Closeable? = null

    override fun onCreate() {
        super.onCreate()
        CrashNote.install(this, "OpenScreenTime Kid", BuildConfig.VERSION_NAME)
        // Whatever this flavor's backend needs before anything touches it - nothing, locally.
        Backend.onAppCreate(this)
        // Before anything that enforces or syncs can run - see LiveChildState.
        LiveChildState.restore(this)
        createNotificationChannels()
        scheduleSync()
        startLimitsListener()
        startNearbyHost()
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
     * Keeps [LiveChildState] - the in-memory limits/lock state AppLimitAccessibilityService
     * and DnsSinkholeVpnService both read - up to date with wherever this build keeps the profile:
     * Firestore in a cloud build, this phone's own storage in a local one. Safe to call more than
     * once (e.g. right after pairing completes, so the listener starts without needing an app
     * restart) - each call just attaches a fresh listener.
     */
    fun startLimitsListener() {
        val (parentUid, childId) = Backend.profileIds(this) ?: return

        repository.listenChild(parentUid, childId) { child ->
            val wasLocked = LiveChildState.lockedCache
            LiveChildState.update(this, child)
            // "Dumb phone" (see #42): keep this phone's copy of the choice, and the home-screen option, in step.
            if (child.locked && !wasLocked) {
                // Don't wait for the next app switch or tick - interrupt right away.
                startActivity(
                    Intent(this, BlockOverlayActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(BlockOverlayActivity.EXTRA_REASON, BlockReason.PARENT_LOCK.wireValue)
                )
            }
        }
    }

    /** Safe to call again after linking; a second call replaces the first listener. */
    fun startNearbyHost() {
        if (!Backend.IS_LOCAL) return
        nearbyListening?.let { runCatching { it.close() } }
        nearbyListening = NearbySyncRunner(this).host(repository)
    }

    companion object {
        const val MONITOR_CHANNEL_ID = "monitor_service"
        const val WARNING_CHANNEL_ID = "limit_warnings"
    }
}
