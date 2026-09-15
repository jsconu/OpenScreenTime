package org.openscreentime.parent.monitor

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.R
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.parent.ui.BlockOverlayActivity

/**
 * Self-tracking equivalent of the kid app's AppLimitAccessibilityService (see #8) -
 * enforces the parent's own limits on their own device, the same way the kid app
 * enforces a child's. Only runs once the parent has opted into self-tracking and
 * granted this device's own accessibility permission.
 */
class AppLimitAccessibilityService : AccessibilityService() {

    private lateinit var usageStore: UsageStore
    private val handler = Handler(Looper.getMainLooper())

    private var currentPackage: String? = null
    private var currentPackageStartMs: Long = 0L

    private val tick = object : Runnable {
        override fun run() {
            flushCurrent(restart = true)
            currentPackage?.let { checkLimits(it) } ?: checkLockOnly()
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        usageStore = UsageStore(applicationContext)
        handler.postDelayed(tick, TICK_INTERVAL_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == currentPackage) return

        flushCurrent(restart = false)
        currentPackage = pkg
        currentPackageStartMs = System.currentTimeMillis()
        cacheAppLabel(pkg)
        checkLimits(pkg)
    }

    /** Adds elapsed time for the current package. If [restart], keeps tracking it from now. */
    private fun flushCurrent(restart: Boolean) {
        val pkg = currentPackage ?: return
        val now = System.currentTimeMillis()
        usageStore.addAppTime(pkg, now - currentPackageStartMs)
        if (restart) currentPackageStartMs = now
    }

    private fun cacheAppLabel(pkg: String) {
        if (usageStore.appNames.containsKey(pkg)) return
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
        usageStore.cacheAppName(pkg, label)
    }

    private fun checkLockOnly() {
        if (lockedCache) showBlockOverlay("parent_lock")
    }

    private fun checkLimits(pkg: String) {
        if (lockedCache) {
            showBlockOverlay("parent_lock")
            return
        }

        val dailyLimitMs = dailyLimitMinutes * 60_000L
        val liveTotal = usageStore.liveTotalScreenTimeMs
        when {
            liveTotal >= dailyLimitMs -> {
                showBlockOverlay("daily_limit")
                return
            }
            dailyLimitMs - liveTotal <= WARNING_THRESHOLD_MS && !usageStore.dailyWarned -> {
                usageStore.markDailyWarned()
                notifyWarning(
                    "Screen time is almost up",
                    "Less than ${WARNING_THRESHOLD_MS / 60_000} minutes left for today."
                )
            }
        }

        val limitMinutes = limitsCache[pkg] ?: return
        val limitMs = limitMinutes * 60_000L
        val usedMs = usageStore.appUsageMs[pkg] ?: 0
        val appName = usageStore.appNames[pkg] ?: pkg
        when {
            usedMs >= limitMs -> showBlockOverlay("app_limit")
            limitMs - usedMs <= WARNING_THRESHOLD_MS && pkg !in usageStore.warnedApps -> {
                usageStore.markAppWarned(pkg)
                notifyWarning(
                    "$appName time is almost up",
                    "Less than ${WARNING_THRESHOLD_MS / 60_000} minutes left for $appName today."
                )
            }
        }
    }

    private fun notifyWarning(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, ParentApp.WARNING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_monitor)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(WARNING_NOTIFICATION_ID, notification)
    }

    private fun showBlockOverlay(reason: String) {
        val overlay = Intent(this, BlockOverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(BlockOverlayActivity.EXTRA_REASON, reason)
        startActivity(overlay)
    }

    override fun onInterrupt() {}

    companion object {
        private const val TICK_INTERVAL_MS = 30_000L
        private const val WARNING_THRESHOLD_MS = 5 * 60_000L
        private const val WARNING_NOTIFICATION_ID = 2002

        /** Updated live from Firestore by [org.openscreentime.parent.ParentApp]. */
        @Volatile var limitsCache: Map<String, Int> = emptyMap()
        @Volatile var dailyLimitMinutes: Int = Int.MAX_VALUE
        @Volatile var lockedCache: Boolean = false
    }
}
