package org.openscreentime.shared.util

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.PowerManager
import android.view.inputmethod.InputMethodManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import org.openscreentime.shared.model.BlockReason
import org.openscreentime.shared.model.EnforcementEvent
import org.openscreentime.shared.model.EnforcementSettings
import org.openscreentime.shared.model.WARNING_THRESHOLD_MINUTES
import org.openscreentime.shared.model.WarnKind
import org.openscreentime.shared.model.buildEnforcementInput
import org.openscreentime.shared.model.decideEnforcement
import org.openscreentime.shared.model.hasUnlockWindowExpired
import org.openscreentime.shared.model.isFirstAppAfterUnlock
import org.openscreentime.shared.model.nowMinutesOfDay
import org.openscreentime.shared.model.relockDue
import org.openscreentime.shared.model.SYSTEM_UI_PACKAGE
import org.openscreentime.shared.model.WindowKind
import org.openscreentime.shared.model.classifyWindow

/**
 * The foreground guard both apps run through Android's accessibility events: it attributes time to the app in
 * front, sends a "dumb phone" app that isn't allowed back home, enforces per-app and daily limits, bedtime and locks
 * with a block screen, and warns once when a limit is close. A periodic tick re-checks limits while someone stays in
 * one app, since foreground-change events alone wouldn't catch that.
 *
 * The decisions are made by `decideEnforcement` (pure, unit-tested); this class holds the rest of the loop once,
 * and the kid app and the parent's own-phone tracking are adapters that supply only what genuinely differs:
 * where the limits live ([settings]), where usage is stored, which overlay and channel to use, how to lock again
 * after a timed unlock, and how the status notification looks. The kid adapter also supplies a friction pause.
 *
 * Accessibility services are exempt from Android's package-visibility restrictions, so this can resolve any app's
 * label without extra `<queries>` declarations.
 */
abstract class BaseAppLimitAccessibilityService : AccessibilityService() {

    protected abstract val settings: EnforcementSettings
    protected abstract fun openUsageStore(): DailyUsageStore

    protected abstract val warningChannelId: String
    protected abstract val warningNotificationId: Int
    protected abstract val warningIconRes: Int

    protected abstract fun showBlockOverlay(reason: BlockReason)

    /** Only the kid app has a friction-pause screen (see #12); elsewhere this is never asked for. */
    protected open val supportsPause: Boolean = false
    protected open fun showPauseOverlay(appName: String) {}

    /** A parent's timed unlock has run out: lock this phone again, here and in Firestore. */
    protected abstract fun relockNow()

    /** Redraws the calm status notification (see #9); updates the existing one, never alerts. */
    protected abstract fun updateStatusNotification()

    protected open fun onConnected() {}

    protected lateinit var usageStore: DailyUsageStore
        private set

    private val focusEnforcer by lazy { FocusEnforcer(this) }
    private val handler = Handler(Looper.getMainLooper())

    private var currentPackage: String? = null
    private var currentPackageStartMs: Long = 0L

    private val power by lazy { getSystemService(Context.POWER_SERVICE) as PowerManager }

    private val tick = object : Runnable {
        override fun run() {
            // The tick keeps running while the phone is locked. Crediting the app that happened to
            // be in front when the screen went off, every thirty seconds all night, is what turned a
            // browser into a fourteen-hour app. If the screen is off, nobody is using anything.
            if (!power.isInteractive) stopCrediting() else flushCurrent(restart = true)
            checkRelock()
            checkLimits(currentPackage)
            updateStatusNotification()
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    /**
     * Screen off and unlock, heard here as well as by the screen-monitor service. That service is
     * the one that counts unlocks; this one only needs to know when to stop and start crediting
     * apps, and hearing it directly means one of the two being killed no longer leaves a session
     * running all night. Both calls below are safe to receive twice.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    stopCrediting()
                    usageStore.endSessionAndFlush()
                }
                Intent.ACTION_USER_PRESENT -> resumeCrediting()
            }
        }
    }

    override fun onServiceConnected() {
        usageStore = openUsageStore()
        onConnected()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        handler.postDelayed(tick, TICK_INTERVAL_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(screenReceiver) }
    }

    /** Folds in the app in front up to now, then credits nobody until something new comes forward. */
    private fun stopCrediting() {
        flushCurrent(restart = false)
        currentPackage = null
    }

    /**
     * Just unlocked. Unlocking straight back into the app that was open does not always produce a
     * window event, so ask which window is in front rather than waiting for one that may not come.
     */
    private fun resumeCrediting() {
        val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: return
        if (kindOf(pkg) != WindowKind.APP) return
        currentPackage = pkg
        currentPackageStartMs = System.currentTimeMillis()
        cacheAppLabel(pkg)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val kind = kindOf(pkg)

        // The keyboard, the notification shade, a volume slider: drawn over the app being used, and
        // not a change of app. Crediting them took time off whatever was underneath - typing a search
        // made the browser stop counting until the keyboard went away.
        if (kind == WindowKind.OVERLAY) return

        settings.foregroundPackage = pkg
        // "Dumb phone" (see #42): an app that isn't allowed goes straight back to the home screen.
        if (focusEnforcer.enforce(pkg)) return
        recordFirstAppIfPending(pkg)
        if (kind == WindowKind.SELF || pkg == currentPackage) return

        flushCurrent(restart = false)
        if (kind == WindowKind.HOME) {
            // On the home screen nobody is in an app. Still screen time, not anyone's app time.
            currentPackage = null
            updateStatusNotification()
            return
        }
        currentPackage = pkg
        currentPackageStartMs = System.currentTimeMillis()
        cacheAppLabel(pkg)
        checkLimits(pkg)
        updateStatusNotification()
    }

    private fun kindOf(pkg: String): WindowKind =
        classifyWindow(pkg, packageName, homePackages, overlayPackages)

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
    private val ignoredFirstAppPackages: Set<String> by lazy { homePackages + SYSTEM_UI_PACKAGE }

    /** Every installed home screen, not only the current default: people switch launchers. */
    private val homePackages: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    /**
     * What draws over apps rather than replacing them. Keyboards are read from the system each
     * time rather than once, since a person can install or switch keyboards while this is running.
     */
    private val overlayPackages: Set<String>
        get() {
            val keyboards = runCatching {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .enabledInputMethodList.map { it.packageName }
            }.getOrDefault(emptyList())
            return keyboards.toSet() + SYSTEM_UI_PACKAGE
        }

    /** Adds elapsed time for the current package. If [restart], keeps tracking it from now. */
    private fun flushCurrent(restart: Boolean) {
        val pkg = currentPackage ?: return
        val now = System.currentTimeMillis()
        // A parent can exclude an app from the overall limit; its time is then taken off today's total.
        usageStore.addForegroundTime(pkg, now - currentPackageStartMs, pkg !in settings.excludedFromTotalPackages)
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

    private fun checkLimits(pkg: String?) {
        val input = buildEnforcementInput(
            settings = settings,
            usage = usageStore,
            packageName = pkg,
            nowMs = System.currentTimeMillis(),
            nowMinutesOfDay = nowMinutesOfDay(),
            supportsPause = supportsPause
        )
        for (event in decideEnforcement(input)) {
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

    private fun checkRelock() {
        if (!relockDue(settings.relockAtMs, System.currentTimeMillis())) return
        relockNow()
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
        val notification = Notification.Builder(this, warningChannelId)
            .setSmallIcon(warningIconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(warningNotificationId, notification)
    }

    override fun onInterrupt() {}

    private companion object {
        const val TICK_INTERVAL_MS = 30_000L
    }
}
