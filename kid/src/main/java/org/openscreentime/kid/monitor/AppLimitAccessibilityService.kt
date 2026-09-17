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
import org.openscreentime.shared.model.BlockReason
import org.openscreentime.shared.model.EnforcementEvent
import org.openscreentime.shared.model.EnforcementInput
import org.openscreentime.shared.model.StatusTier
import org.openscreentime.shared.model.WARNING_THRESHOLD_MINUTES
import org.openscreentime.shared.model.WarnKind
import org.openscreentime.shared.model.computeStatusTier
import org.openscreentime.shared.model.decideEnforcement
import org.openscreentime.shared.model.isInBedtimeWindow
import org.openscreentime.shared.model.nowMinutesOfDay

/**
 * Watches foreground app changes to (a) attribute time per app and (b) enforce
 * per-app and overall daily limits by launching a full-screen block activity,
 * and (c) warn once when a limit is close. A periodic tick re-checks limits
 * even while the kid stays inside a single app for a long stretch, since
 * foreground-change events alone wouldn't catch that case.
 *
 * The actual decision logic lives in `shared/model/Enforcement.kt` - this class is a
 * thin adapter: it builds a snapshot for decideEnforcement() and acts on the events it
 * returns (launch an overlay Activity, post a notification, record what was shown so
 * it isn't repeated). See the architecture review's Candidate 2.
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
            checkLimits(currentPackage)
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

    private fun buildInput(pkg: String?): EnforcementInput {
        val appLimitMinutes = pkg?.let { LiveChildState.limitsCache[it] }
        return EnforcementInput(
            locked = LiveChildState.lockedCache,
            bedtimeStartMinutes = LiveChildState.bedtimeStartMinutes,
            bedtimeEndMinutes = LiveChildState.bedtimeEndMinutes,
            nowMinutesOfDay = nowMinutesOfDay(),
            dailyLimitMinutes = LiveChildState.dailyLimitMinutes,
            liveTotalScreenTimeMs = usageStore.liveTotalScreenTimeMs,
            dailyWarned = usageStore.dailyWarned,
            foregroundPackage = pkg,
            appLimitMinutes = appLimitMinutes,
            appUsedMs = pkg?.let { usageStore.appUsageMs[it] } ?: 0,
            appWarned = pkg?.let { it in usageStore.warnedApps } ?: false,
            // This app supports the friction-pause feature (#12) - unlike the parent's
            // self-tracking equivalent, which never supplies this.
            appAlreadyPaused = pkg?.let { it in usageStore.pausedApps },
            nowMs = System.currentTimeMillis(),
            temporaryUnlockUntilMs = LiveChildState.temporaryUnlockUntilMs,
            alwaysAllowedPackages = LiveChildState.alwaysAllowedPackages
        )
    }

    private fun checkLimits(pkg: String?) {
        for (event in decideEnforcement(buildInput(pkg))) {
            when (event) {
                is EnforcementEvent.Block -> showBlockOverlay(event.reason)
                is EnforcementEvent.Pause -> {
                    usageStore.markAppPaused(event.packageName)
                    showPauseOverlay(usageStore.appNames[event.packageName] ?: event.packageName)
                }
                is EnforcementEvent.Warn -> handleWarn(event.kind, pkg)
            }
        }
    }

    private fun handleWarn(kind: WarnKind, pkg: String?) {
        when (kind) {
            WarnKind.DAILY -> {
                usageStore.markDailyWarned()
                notifyWarning(
                    "Screen time is almost up",
                    "Less than $WARNING_THRESHOLD_MINUTES minutes left for today."
                )
            }
            WarnKind.APP -> {
                val appPkg = pkg ?: return
                val appName = usageStore.appNames[appPkg] ?: appPkg
                usageStore.markAppWarned(appPkg)
                notifyWarning(
                    "$appName time is almost up",
                    "Less than $WARNING_THRESHOLD_MINUTES minutes left for $appName today."
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

    private fun showBlockOverlay(reason: BlockReason) {
        val overlay = Intent(this, BlockOverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(BlockOverlayActivity.EXTRA_REASON, reason.wireValue)
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
        val isInBedtime = isInBedtimeWindow(
            nowMinutesOfDay(), LiveChildState.bedtimeStartMinutes, LiveChildState.bedtimeEndMinutes
        )
        val tier = computeStatusTier(
            locked = LiveChildState.lockedCache,
            isInBedtime = isInBedtime,
            dailyLimitMinutes = LiveChildState.dailyLimitMinutes,
            liveTotalScreenTimeMs = usageStore.liveTotalScreenTimeMs,
            dailyUnlockGoal = LiveChildState.dailyUnlockGoal,
            unlockCount = usageStore.unlockCount
        )
        val iconRes = when (tier) {
            StatusTier.STOP -> R.drawable.ic_status_stop
            StatusTier.CAUTION -> R.drawable.ic_status_caution
            StatusTier.GOOD -> R.drawable.ic_status_good
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
        private const val WARNING_NOTIFICATION_ID = 1002
    }
}
