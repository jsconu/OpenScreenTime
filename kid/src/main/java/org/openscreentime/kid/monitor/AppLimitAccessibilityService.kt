package org.openscreentime.kid.monitor

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.R
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.kid.ui.BlockOverlayActivity
import org.openscreentime.kid.ui.MainActivity
import org.openscreentime.kid.ui.PauseOverlayActivity

/**
 * Watches foreground app changes to (a) attribute time per app and (b) enforce
 * per-app and overall daily limits by launching a full-screen block activity,
 * and (c) warn once when a limit is close. A periodic tick re-checks limits
 * even while the kid stays inside a single app for a long stretch, since
 * foreground-change events alone wouldn't catch that case.
 *
 * Accessibility services are exempt from Android's package-visibility restrictions,
 * so this can resolve any app's label without extra <queries> declarations.
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
            updateStatusNotification()
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
        updateStatusNotification()
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
            usedMs >= limitMs / 2 && pkg !in usageStore.pausedApps -> {
                usageStore.markAppPaused(pkg)
                showPauseOverlay(appName)
            }
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
        val notification = NotificationCompat.Builder(this, KidApp.WARNING_CHANNEL_ID)
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

    /** See #12 - a brief, dismissible breath, not a block, shown once per app per day. */
    private fun showPauseOverlay(appName: String) {
        val overlay = Intent(this, PauseOverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(PauseOverlayActivity.EXTRA_APP_NAME, appName)
        startActivity(overlay)
    }

    /**
     * Calm status signal (see #9) instead of exact numbers - updates the existing ongoing
     * notification's icon rather than posting a new one, so this never interrupts/alerts,
     * just reflects current state whenever it's glanced at.
     */
    private fun updateStatusNotification() {
        val dailyLimitMs = dailyLimitMinutes * 60_000L
        val timeRatio = if (dailyLimitMs in 1..Long.MAX_VALUE) {
            usageStore.liveTotalScreenTimeMs.toFloat() / dailyLimitMs
        } else 0f
        val unlockGoal = dailyUnlockGoal
        val unlockRatio = if (unlockGoal != null && unlockGoal > 0) {
            usageStore.unlockCount.toFloat() / unlockGoal
        } else 0f
        val ratio = maxOf(timeRatio, unlockRatio)

        val iconRes = when {
            lockedCache || ratio >= 1f -> R.drawable.ic_status_stop
            ratio >= 0.7f -> R.drawable.ic_status_caution
            else -> R.drawable.ic_status_good
        }

        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, KidApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(getString(R.string.monitor_notification_title))
            .setContentText(getString(R.string.monitor_notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        getSystemService(NotificationManager::class.java).notify(ScreenMonitorService.NOTIFICATION_ID, notification)
    }

    override fun onInterrupt() {}

    companion object {
        private const val TICK_INTERVAL_MS = 30_000L
        private const val WARNING_THRESHOLD_MS = 5 * 60_000L
        private const val WARNING_NOTIFICATION_ID = 1002

        /** Updated live from Firestore by [org.openscreentime.kid.KidApp]. */
        @Volatile var limitsCache: Map<String, Int> = emptyMap()
        @Volatile var dailyLimitMinutes: Int = Int.MAX_VALUE
        @Volatile var lockedCache: Boolean = false
        /** Informational only (see #10) - factors into the status icon, never blocks. */
        @Volatile var dailyUnlockGoal: Int? = null
    }
}
