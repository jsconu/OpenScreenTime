package org.openscreentime.parent.monitor

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.openscreentime.parent.data.UsageStore
import org.openscreentime.shared.model.shouldIncludeInDigest

/**
 * See #35 - counts the parent's own notifications, overall and by app, while notification
 * tracking is turned on for their self profile. A count only: nothing about a notification's
 * content is kept. Does nothing at all while the toggle is off, even if notification access has
 * been granted.
 */
class NotificationCountListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!AppLimitAccessibilityService.trackNotifications) return
        val notification = sbn.notification
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (!shouldIncludeInDigest(sbn.packageName, packageName, sbn.isOngoing, isGroupSummary, title, text)) return
        val usageStore = UsageStore(this)
        usageStore.cacheAppName(sbn.packageName, appLabelFor(sbn.packageName))
        usageStore.recordNotification(sbn.packageName)
    }

    private fun appLabelFor(packageName: String): String =
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }
}
