package org.openscreentime.parent.monitor

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.openscreentime.parent.data.CalmModePrefs
import org.openscreentime.parent.data.NotificationDigestStore
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.shared.model.AUTHENTICATOR_PACKAGES
import org.openscreentime.shared.model.DigestNotification
import org.openscreentime.shared.model.FOCUS_SYSTEM_PACKAGES
import org.openscreentime.shared.util.resolveEssentialPackages
import org.openscreentime.shared.model.NotificationDeduper
import org.openscreentime.shared.model.shouldIncludeInDigest

/**
 * Two independent, opt-in jobs for the parent's own phone, both local:
 *  - See #35: counts the parent's own notifications, overall and by app, while notification
 *    tracking is on for their self profile. A count only - nothing about content is kept.
 *  - The calm notification list (the parent's twin of the kid app's, see #20): a plain, read-only
 *    list of today's notifications, kept on this phone only, while the parent has switched it on.
 * With both off this does nothing at all, even if notification access has been granted.
 */
class NotificationCountListenerService : NotificationListenerService() {

    private val countDeduper = NotificationDeduper()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val digestStore = NotificationDigestStore(this)
        val counting = AppLimitAccessibilityService.trackNotifications
        if (!counting && !digestStore.optedIn) return
        val notification = sbn.notification
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (!shouldIncludeInDigest(sbn.packageName, packageName, sbn.isOngoing, isGroupSummary, title, text)) return
        val label = appLabelFor(sbn.packageName)
        // An identical re-post of the same notification is not a new notification (see #39).
        if (counting && countDeduper.shouldCount(sbn.key, title, text)) {
            val usageStore = UsageStore(this)
            usageStore.cacheAppName(sbn.packageName, label)
            usageStore.recordNotification(sbn.packageName)
        }
        digestStore.record(
            DigestNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = label,
                title = title.trim(),
                text = text.trim(),
                postedAtMs = sbn.postTime
            )
        )
        // Calm mode: take everything but the essentials out of the shade; the summary stands in for them.
        if (digestStore.optedIn && CalmModePrefs(this).hideOthers && shouldHide(sbn)) {
            cancelNotification(sbn.key)
            CalmSummary.post(this, digestStore.entries.size)
        }
    }

    private var essentialCache: Set<String> = emptySet()
    private var essentialAtMs = 0L

    /** Calls, texts, alarms, sign-in codes and system messages are never hidden. */
    private fun shouldHide(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName
        if (pkg == packageName || sbn.isOngoing) return false
        val now = System.currentTimeMillis()
        if (essentialCache.isEmpty() || now - essentialAtMs > 60_000L) {
            essentialCache = resolveEssentialPackages(this).toSet()
            essentialAtMs = now
        }
        if (pkg in essentialCache || pkg in FOCUS_SYSTEM_PACKAGES || pkg in AUTHENTICATOR_PACKAGES) return false
        return when (sbn.notification.category) {
            Notification.CATEGORY_CALL, Notification.CATEGORY_ALARM, Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_SYSTEM, Notification.CATEGORY_ERROR, Notification.CATEGORY_TRANSPORT -> false
            else -> true
        }
    }

    private fun appLabelFor(packageName: String): String =
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }
}
