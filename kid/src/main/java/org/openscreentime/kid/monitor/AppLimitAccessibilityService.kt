package org.openscreentime.kid.monitor

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openscreentime.kid.KidApp
import org.openscreentime.kid.R
import org.openscreentime.kid.data.PairingStore
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.kid.ui.BlockOverlayActivity
import org.openscreentime.kid.ui.MainActivity
import org.openscreentime.kid.ui.PauseOverlayActivity
import org.openscreentime.shared.model.BlockReason
import org.openscreentime.shared.model.EnforcementEvent
import org.openscreentime.shared.model.EnforcementInput
import org.openscreentime.shared.model.STATUS_NOTIFICATION_TITLE
import org.openscreentime.shared.model.StatusTier
import org.openscreentime.shared.model.WARNING_THRESHOLD_MINUTES
import org.openscreentime.shared.model.WarnKind
import org.openscreentime.shared.model.computeStatusTier
import org.openscreentime.shared.model.decideEnforcement
import org.openscreentime.shared.model.hasUnlockWindowExpired
import org.openscreentime.shared.model.isFirstAppAfterUnlock
import org.openscreentime.shared.model.isInBedtimeWindow
import org.openscreentime.shared.model.nowMinutesOfDay
import org.openscreentime.shared.model.relockDue
import org.openscreentime.shared.model.statusNotificationMessage

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
            checkRelock()
            checkLimits(currentPackage)
            updateStatusNotification()
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        usageStore = UsageStore(applicationContext)
        // Show the right icon straight away instead of waiting for the first tick.
        updateStatusNotification()
        handler.postDelayed(tick, TICK_INTERVAL_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        recordFirstAppIfPending(pkg)
        if (pkg == packageName || pkg == currentPackage) return

        flushCurrent(restart = false)
        currentPackage = pkg
        currentPackageStartMs = System.currentTimeMillis()
        cacheAppLabel(pkg)
        checkLimits(pkg)
        updateStatusNotification()
    }

    /**
     * See #35 - if a parent has unlock tracking on, the unlock receiver left a pending unlock
     * behind; the first real app to come forward within the window is the one recorded. This runs
     * before the "same app as before" early return above, since unlocking straight back into the
     * app that was open when the screen went off is exactly a first-app-after-unlock too.
     */
    private fun recordFirstAppIfPending(pkg: String) {
        val unlockedAt = usageStore.unlockAwaitingMs ?: return
        val now = System.currentTimeMillis()
        if (hasUnlockWindowExpired(unlockedAt, now)) {
            usageStore.clearUnlockAwaiting()
            return
        }
        if (isFirstAppAfterUnlock(pkg, packageName, ignoredFirstAppPackages, unlockedAt, now)) {
            cacheAppLabel(pkg)
            usageStore.recordFirstAppAfterUnlock(pkg)
        }
    }

    /** The home launcher(s) and system UI: arriving there right after an unlock isn't opening an app. */
    private val ignoredFirstAppPackages: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet() + "com.android.systemui"
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

    /** A parent's timed "Parent unlock" has run out - lock again, here and in Firestore. */
    private fun checkRelock() {
        if (!relockDue(LiveChildState.relockAtMs, System.currentTimeMillis())) return
        LiveChildState.relockNow(applicationContext)
        val pairing = PairingStore(applicationContext)
        val parentUid = pairing.parentUid
        val childId = pairing.childId
        if (parentUid != null && childId != null) {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { (application as KidApp).repository.setLocked(parentUid, childId, true) }
            }
        }
        showBlockOverlay(BlockReason.PARENT_LOCK)
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
        StatusNotification.post(this, usageStore)
    }

    override fun onInterrupt() {}

    companion object {
        private const val TICK_INTERVAL_MS = 30_000L
        private const val WARNING_NOTIFICATION_ID = 1002
    }
}
