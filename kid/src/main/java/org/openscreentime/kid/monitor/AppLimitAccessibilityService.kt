package org.openscreentime.kid.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import org.openscreentime.kid.data.UsageStore
import org.openscreentime.kid.ui.BlockOverlayActivity

/**
 * Watches foreground app changes to (a) attribute time per app and (b) enforce
 * per-app and overall daily limits by launching a full-screen block activity.
 *
 * Accessibility services are exempt from Android's package-visibility restrictions,
 * so this can resolve any app's label without extra <queries> declarations.
 */
class AppLimitAccessibilityService : AccessibilityService() {

    private lateinit var usageStore: UsageStore

    private var currentPackage: String? = null
    private var currentPackageStartMs: Long = 0L

    override fun onServiceConnected() {
        usageStore = UsageStore(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == currentPackage) return

        flushCurrent()
        currentPackage = pkg
        currentPackageStartMs = System.currentTimeMillis()
        cacheAppLabel(pkg)
        checkLimits(pkg)
    }

    private fun flushCurrent() {
        val pkg = currentPackage ?: return
        usageStore.addAppTime(pkg, System.currentTimeMillis() - currentPackageStartMs)
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

    private fun checkLimits(pkg: String) {
        if (usageStore.totalScreenTimeMs >= dailyLimitMinutes * 60_000L) {
            showBlockOverlay("daily_limit")
            return
        }
        val limitMinutes = limitsCache[pkg] ?: return
        val usedMs = usageStore.appUsageMs[pkg] ?: 0
        if (usedMs >= limitMinutes * 60_000L) {
            showBlockOverlay("app_limit")
        }
    }

    private fun showBlockOverlay(reason: String) {
        val overlay = Intent(this, BlockOverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(BlockOverlayActivity.EXTRA_REASON, reason)
        startActivity(overlay)
    }

    override fun onInterrupt() {}

    companion object {
        /** Updated live from Firestore by [org.openscreentime.kid.KidApp]. */
        @Volatile var limitsCache: Map<String, Int> = emptyMap()
        @Volatile var dailyLimitMinutes: Int = Int.MAX_VALUE
    }
}
