package org.openscreentime.parent.monitor

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.openscreentime.parent.data.NotificationDigestStore
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.shared.model.DigestNotification
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
        if (counting) {
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
    }

    private fun appLabelFor(packageName: String): String =
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }
}
