package org.openscreentime.parent.monitor

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import org.openscreentime.parent.ParentApp
import org.openscreentime.parent.R
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.parent.ui.BlockOverlayActivity
import org.openscreentime.parent.ui.MainActivity
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
 * Self-tracking equivalent of the kid app's AppLimitAccessibilityService (see #8) -
 * enforces the parent's own limits on their own device, the same way the kid app
 * enforces a child's. Only runs once the parent has opted into self-tracking and
 * granted this device's own accessibility permission.
 *
 * The actual decision logic lives in `shared/model/Enforcement.kt`, shared with the kid
 * app's equivalent - this class is a thin adapter. Unlike the kid app, it never supplies
 * [EnforcementInput.appAlreadyPaused], so it never receives a friction-pause event (#12
 * has no equivalent here - there's no PauseOverlayActivity in this app). See the
 * architecture review's Candidate 2.
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
        val appLimitMinutes = pkg?.let { limitsCache[it] }
        return EnforcementInput(
            locked = lockedCache,
            bedtimeStartMinutes = bedtimeStartMinutes,
            bedtimeEndMinutes = bedtimeEndMinutes,
            nowMinutesOfDay = nowMinutesOfDay(),
            dailyLimitMinutes = dailyLimitMinutes,
            liveTotalScreenTimeMs = usageStore.liveTotalScreenTimeMs,
            dailyWarned = usageStore.dailyWarned,
            foregroundPackage = pkg,
            appLimitMinutes = appLimitMinutes,
            appUsedMs = pkg?.let { usageStore.appUsageMs[it] } ?: 0,
            appWarned = pkg?.let { it in usageStore.warnedApps } ?: false,
            // appAlreadyPaused intentionally left null - no pause feature on this app.
            alwaysAllowedPackages = alwaysAllowedCache
        )
    }

    private fun checkLimits(pkg: String?) {
        for (event in decideEnforcement(buildInput(pkg))) {
            when (event) {
                is EnforcementEvent.Block -> showBlockOverlay(event.reason)
                is EnforcementEvent.Pause -> Unit // never returned; this app supplies no pause state.
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
        val notification = NotificationCompat.Builder(this, ParentApp.WARNING_CHANNEL_ID)
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

    /**
     * Calm status signal (see #9) instead of exact numbers - updates the existing ongoing
     * notification's icon rather than posting a new one, so this never interrupts/alerts,
     * just reflects current state whenever it's glanced at.
     */
    private fun updateStatusNotification() {
        val isInBedtime = isInBedtimeWindow(nowMinutesOfDay(), bedtimeStartMinutes, bedtimeEndMinutes)
        val tier = computeStatusTier(
            locked = lockedCache,
            isInBedtime = isInBedtime,
            dailyLimitMinutes = dailyLimitMinutes,
            liveTotalScreenTimeMs = usageStore.liveTotalScreenTimeMs,
            dailyUnlockGoal = dailyUnlockGoal,
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
        val notification = NotificationCompat.Builder(this, ParentApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(iconRes)
            // See the kid app's AppLimitAccessibilityService: Android forces every
            // status-bar icon to a flat white silhouette regardless of setColor() -
            // this only reaches the pulled-down notification shade's icon circle.
            .setColor(statusTierColor(tier))
            .setContentTitle(getString(R.string.self_monitor_notification_title))
            .setContentText(getString(R.string.self_monitor_notification_text))
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
        private const val WARNING_NOTIFICATION_ID = 2002

        /** Updated live from Firestore by [org.openscreentime.parent.ParentApp]. */
        @Volatile var limitsCache: Map<String, Int> = emptyMap()
        @Volatile var dailyLimitMinutes: Int = Int.MAX_VALUE
        @Volatile var lockedCache: Boolean = false
        /** Informational only (see #10) - factors into the status icon, never blocks. */
        @Volatile var dailyUnlockGoal: Int? = null
        /** Minutes since local midnight; either null = no bedtime window set. See #15. */
        @Volatile var bedtimeStartMinutes: Int? = null
        @Volatile var bedtimeEndMinutes: Int? = null
        /** See #28 - packages that bypass bedtime and every daily/app-limit check. */
        @Volatile var alwaysAllowedCache: Set<String> = emptySet()
    }
}

/** Matches the three status-icon colors used elsewhere (e.g. the Dashboard's "Granted" text, the lock button). */
private fun statusTierColor(tier: StatusTier): Int = when (tier) {
    StatusTier.GOOD -> Color.parseColor("#2E7D32")
    StatusTier.CAUTION -> Color.parseColor("#F57C00")
    StatusTier.STOP -> Color.parseColor("#B3261E")
}
