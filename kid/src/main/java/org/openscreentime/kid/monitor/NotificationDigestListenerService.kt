package org.openscreentime.kid.monitor

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.openscreentime.kid.data.NotificationDigestStore
import org.openscreentime.shared.model.DigestNotification
import org.openscreentime.shared.model.shouldIncludeInDigest

/**
 * Optional, off-by-default listener for the kid-app notification digest (see #20).
 * Records locally only; [onNotificationRemoved] is intentionally a no-op so the day's
 * list stays a digest of what arrived, not a live shade.
 */
class NotificationDigestListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val store = NotificationDigestStore(this)
        if (!store.optedIn) return

        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (!shouldIncludeInDigest(
                packageName = sbn.packageName,
                ownPackageName = packageName,
                isOngoing = sbn.isOngoing,
                isGroupSummary = isGroupSummary,
                title = title,
                text = text
            )
        ) {
            return
        }

        store.record(
            DigestNotification(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = appLabelFor(sbn.packageName),
                title = title.trim(),
                text = text.trim(),
                postedAtMs = sbn.postTime
            )
        )
    }

    private fun appLabelFor(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }
}
